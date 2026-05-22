package com.hwb.aianswerer.rag

import android.content.Context
import android.util.Log
import com.hwb.aianswerer.db.AppDatabase
import com.hwb.aianswerer.db.Converters
import com.hwb.aianswerer.db.entities.KbChunk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.math.sqrt

/**
 * 内存级向量检索引擎（单例）
 *
 * 当前规模假设：< 数万 chunk，全量驻留内存即可，余弦相似度暴力扫描。
 * 如未来上量，可平滑替换为 ANN（Faiss/HNSWlib）而不影响调用方接口。
 *
 * 缓存结构：List<Entry>，Entry 持有 chunk + 解码后的 FloatArray + kb 元数据，
 * 避免每次检索都从 ByteArray 解码 / 查 kb 表。
 *
 * 线程：refresh 与 search 串行（Mutex），确保导入/删除完成前不读半状态。
 */
object RetrievalEngine {

    private const val TAG = "RetrievalEngine"

    private data class Entry(
        val chunk: KbChunk,
        val vector: FloatArray,
        val norm: Float,
        val kbName: String,
        val kbPriority: Int,
        val kbEnabled: Boolean
    )

    private val mutex = Mutex()

    @Volatile
    private var cache: List<Entry> = emptyList()

    /**
     * 从数据库重建内存缓存。
     *
     * 调用时机：
     * - MyApplication.onCreate 之后首次检索前（lazy 触发）
     * - 用户导入 / 删除 文档 后
     * - KB 启用状态 / 优先级 修改后
     */
    suspend fun refresh(ctx: Context) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val db = AppDatabase.getInstance(ctx)
            val kbs = db.knowledgeBaseDao().getAll().associateBy { it.id }
            val chunks = db.kbChunkDao().getAll()
            val list = ArrayList<Entry>(chunks.size)
            for (c in chunks) {
                val kb = kbs[c.kbId] ?: continue
                val vec = Converters.bytesToFloatArray(c.embedding) ?: continue
                list.add(
                    Entry(
                        chunk = c,
                        vector = vec,
                        norm = l2Norm(vec),
                        kbName = kb.name,
                        kbPriority = kb.priority,
                        kbEnabled = kb.enabled
                    )
                )
            }
            cache = list
            Log.i(TAG, "refresh: loaded ${list.size} chunks from ${kbs.size} kbs")
        }
    }

    /**
     * 余弦相似度 + 优先级加权 检索 topK。
     *
     * @param queryEmbedding 查询向量
     * @param topK 返回前多少条
     * @param threshold 加权后分数阈值；低于阈值的结果会被过滤
     * @return 按分数降序的 ScoredChunk 列表（≤ topK，可能为空）
     */
    suspend fun search(
        queryEmbedding: FloatArray,
        topK: Int,
        threshold: Float
    ): List<ScoredChunk> = withContext(Dispatchers.Default) {
        if (queryEmbedding.isEmpty() || cache.isEmpty() || topK <= 0) return@withContext emptyList()
        val qNorm = l2Norm(queryEmbedding)
        if (qNorm == 0f) return@withContext emptyList()

        // 取 cache 引用快照，避免遍历期间被 refresh 替换
        val snapshot = cache
        val scored = ArrayList<ScoredChunk>(snapshot.size)
        for (e in snapshot) {
            if (!e.kbEnabled) continue
            if (e.vector.size != queryEmbedding.size) continue  // 维度不匹配（混用模型）跳过
            if (e.norm == 0f) continue
            val cos = dot(queryEmbedding, e.vector) / (qNorm * e.norm)
            val weighted = cos * (e.kbPriority / 100f)
            if (weighted >= threshold) {
                scored.add(ScoredChunk(e.chunk, weighted, e.kbName))
            }
        }
        scored.sortByDescending { it.score }
        if (scored.size > topK) scored.subList(0, topK) else scored
    }

    /** 当前缓存条数（UI/调试用）。 */
    fun size(): Int = cache.size

    /**
     * 让缓存失效，下次 [retrieveContext]/[search] 调用前会触发 [refresh]。
     *
     * 调用时机：导入 / 删除文档 / 启用切换 后。
     * 实现采用置空 cache + 让 RagOrchestrator 检测 size()==0 主动 refresh。
     */
    fun invalidate() {
        cache = emptyList()
    }

    private fun dot(a: FloatArray, b: FloatArray): Float {
        var s = 0f
        for (i in a.indices) s += a[i] * b[i]
        return s
    }

    private fun l2Norm(v: FloatArray): Float {
        var s = 0f
        for (x in v) s += x * x
        return sqrt(s)
    }
}
