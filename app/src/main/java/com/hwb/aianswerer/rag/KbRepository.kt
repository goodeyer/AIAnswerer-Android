package com.hwb.aianswerer.rag

import android.content.Context
import android.net.Uri
import android.util.Log
import com.hwb.aianswerer.api.EmbeddingClient
import com.hwb.aianswerer.db.AppDatabase
import com.hwb.aianswerer.db.Converters
import com.hwb.aianswerer.db.entities.KbChunk
import com.hwb.aianswerer.db.entities.KbDocument
import com.hwb.aianswerer.db.entities.KnowledgeBase
import com.hwb.aianswerer.parser.DocumentParserFactory
import com.hwb.aianswerer.parser.TextChunker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * KbRepository
 *
 * 知识库业务封装层：统一 DAO + Parser + Embedding + RetrievalEngine 刷新。
 * UI 仅依赖该类，避免在 Compose 内直接拼接 DB / 网络逻辑。
 *
 * 设计：
 * - 所有方法均挂起函数，I/O 切到 Dispatchers.IO。
 * - 返回 sealed [ImportResult] 而非抛异常，UI 层易处理。
 * - importDocument 通过 [ProgressCallback] 上报进度（解析 / 切片 / 向量化 / 落库）。
 */
object KbRepository {

    private const val TAG = "KbRepository"

    // ---------- KB CRUD ----------

    suspend fun listKnowledgeBases(ctx: Context): List<KbWithStats> = withContext(Dispatchers.IO) {
        val db = AppDatabase.getInstance(ctx)
        val kbs = db.knowledgeBaseDao().getAll()
        kbs.map { kb ->
            val docCount = db.kbDocumentDao().countByKbId(kb.id)
            val chunkCount = db.kbChunkDao().countByKbId(kb.id)
            KbWithStats(kb, docCount, chunkCount)
        }
    }

    suspend fun createKnowledgeBase(
        ctx: Context,
        name: String,
        priority: Int = 50,
        embedModel: String = "BAAI/bge-m3"
    ): Result<Long> = withContext(Dispatchers.IO) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return@withContext Result.failure(IllegalArgumentException("name blank"))
        val db = AppDatabase.getInstance(ctx)
        try {
            val id = db.knowledgeBaseDao().insert(
                KnowledgeBase(
                    name = trimmed,
                    priority = priority.coerceIn(0, 100),
                    embedModel = embedModel,
                    enabled = true
                )
            )
            Result.success(id)
        } catch (e: Exception) {
            // unique index 冲突等
            Result.failure(e)
        }
    }

    suspend fun deleteKnowledgeBase(ctx: Context, kbId: Long) = withContext(Dispatchers.IO) {
        val db = AppDatabase.getInstance(ctx)
        db.knowledgeBaseDao().deleteById(kbId) // CASCADE 删除 doc / chunk
        // 让检索引擎下次首调用时重建
        RetrievalEngine.invalidate()
    }

    suspend fun setKbEnabled(ctx: Context, kbId: Long, enabled: Boolean) = withContext(Dispatchers.IO) {
        AppDatabase.getInstance(ctx).knowledgeBaseDao().setEnabled(kbId, enabled)
        RetrievalEngine.invalidate()
    }

    suspend fun setKbPriority(ctx: Context, kbId: Long, priority: Int) = withContext(Dispatchers.IO) {
        AppDatabase.getInstance(ctx).knowledgeBaseDao().setPriority(kbId, priority.coerceIn(0, 100))
    }

    // ---------- 文档查询 ----------

    suspend fun listDocuments(ctx: Context, kbId: Long): List<KbDocument> = withContext(Dispatchers.IO) {
        AppDatabase.getInstance(ctx).kbDocumentDao().getByKbId(kbId)
    }

    /**
     * 删除单个文档（CASCADE 删除其 chunk）。
     */
    suspend fun deleteDocument(ctx: Context, docId: Long) = withContext(Dispatchers.IO) {
        AppDatabase.getInstance(ctx).kbDocumentDao().deleteById(docId)
        RetrievalEngine.invalidate()
    }

    // ---------- 导入 ----------

    /**
     * 导入单个文档。
     *
     * 流程：parse → chunk → embed → 入库（事务）→ invalidate RetrievalEngine。
     *
     * 注意：任何阶段失败立即返回 Failure，已写入的部分不会回滚（KbDocument 仅在向量
     * 成功落库后才 insert），所以数据库不会出现"半截文档"。
     */
    suspend fun importDocument(
        ctx: Context,
        kbId: Long,
        uri: Uri,
        fileName: String,
        sizeBytes: Long,
        progress: ProgressCallback = ProgressCallback.NOOP
    ): ImportResult = withContext(Dispatchers.IO) {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        if (ext !in DocumentParserFactory.supportedExtensions) {
            return@withContext ImportResult.Unsupported(ext)
        }
        val parser = DocumentParserFactory.create(fileName)
            ?: return@withContext ImportResult.Unsupported(ext)

        try {
            // 1. 解析（DocumentParser 直接接 uri+ctx，不需要 InputStream）
            progress.onStep(ImportStep.PARSE, 0, 0)
            val parsed = parser.parse(uri, ctx) { cur, tot ->
                progress.onStep(ImportStep.PARSE, cur, tot)
            }
            val plainText = parsed.plainText
            if (plainText.isBlank()) return@withContext ImportResult.Failure("文档无可抽取文本")

            // 2. 切片
            progress.onStep(ImportStep.CHUNK, 0, 0)
            val chunks = TextChunker.chunk(plainText)
            if (chunks.isEmpty()) return@withContext ImportResult.Failure("切片为空")

            // 3. 向量化（分批，进度回调）
            val client = EmbeddingClient.getInstance()
            val total = chunks.size
            val vectors = ArrayList<FloatArray>(total)
            val batch = 16
            var done = 0
            while (done < total) {
                val end = minOf(done + batch, total)
                val slice = chunks.subList(done, end)
                val embs = client.embed(slice, batchSize = batch)
                if (embs.size != slice.size) {
                    return@withContext ImportResult.Failure("向量返回数量与请求不一致：${embs.size}/${slice.size}")
                }
                vectors.addAll(embs)
                done = end
                progress.onStep(ImportStep.EMBED, done, total)
            }

            // 4. 落库
            progress.onStep(ImportStep.SAVE, total, total)
            val db = AppDatabase.getInstance(ctx)
            val docId = db.kbDocumentDao().insert(
                KbDocument(
                    kbId = kbId,
                    fileName = fileName,
                    fileType = ext,
                    sizeBytes = sizeBytes,
                    chunkCount = total
                )
            )
            val rows = ArrayList<KbChunk>(total)
            for (i in 0 until total) {
                val bytes = Converters.floatArrayToBytes(vectors[i])
                    ?: return@withContext ImportResult.Failure("向量序列化失败 idx=$i")
                rows.add(
                    KbChunk(
                        kbId = kbId,
                        docId = docId,
                        text = chunks[i],
                        embedding = bytes,
                        orderIdx = i
                    )
                )
            }
            db.kbChunkDao().insertAll(rows)

            // 5. 让检索引擎下次首调用时刷新
            RetrievalEngine.invalidate()

            ImportResult.Success(docId, total)
        } catch (e: Exception) {
            Log.e(TAG, "importDocument failed: ${e.message}", e)
            ImportResult.Failure(e.message ?: e.javaClass.simpleName)
        }
    }

    // ---------- 数据类 ----------

    data class KbWithStats(
        val kb: KnowledgeBase,
        val docCount: Int,
        val chunkCount: Int
    )

    enum class ImportStep { PARSE, CHUNK, EMBED, SAVE }

    fun interface ProgressCallback {
        fun onStep(step: ImportStep, done: Int, total: Int)

        companion object {
            val NOOP = ProgressCallback { _, _, _ -> }
        }
    }

    sealed class ImportResult {
        data class Success(val docId: Long, val chunkCount: Int) : ImportResult()
        data class Unsupported(val ext: String) : ImportResult()
        data class Failure(val message: String) : ImportResult()
    }
}
