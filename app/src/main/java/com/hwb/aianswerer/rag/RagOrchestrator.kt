package com.hwb.aianswerer.rag

import android.content.Context
import android.util.Log
import com.hwb.aianswerer.api.EmbeddingClient
import com.hwb.aianswerer.config.AppConfig

/**
 * RAG 入口编排器
 *
 * 单一职责：把"查询文本"转换为可以直接拼到 LLM prompt 的"参考资料"字符串。
 *
 * 流程：
 * 1. 检查 RAG 开关；关闭 → 返回 ""
 * 2. embed 查询文本
 * 3. RetrievalEngine.search 取 topK
 * 4. 按顺序拼 markdown 「## 参考资料 / [KB名] chunk / --- 分隔」
 * 5. 截断至 maxContext 字符
 *
 * 失败处理：任何异常吞掉返回 ""，记录 logcat。RAG 是增强而非阻塞，
 * 失败时主链路必须照常走通用 AI 兜底。
 */
object RagOrchestrator {

    private const val TAG = "RagOrchestrator"

    /**
     * 取查询的检索上下文。
     *
     * @param queryText 用户查询（OCR 后的题目）
     * @param ctx Application Context；用于 RetrievalEngine 懒初始化
     * @return 拼好的 markdown 上下文；零命中 / 异常 / 关闭 → 返回 ""
     */
    suspend fun retrieveContext(queryText: String, ctx: Context): String {
        if (!AppConfig.getRagEnabled()) return ""
        if (queryText.isBlank()) return ""

        return try {
            // 首次调用 lazy 加载缓存
            if (RetrievalEngine.size() == 0) {
                RetrievalEngine.refresh(ctx)
            }

            val client = EmbeddingClient.getInstance()
            val vectors = client.embed(listOf(queryText), batchSize = 1)
            if (vectors.isEmpty() || vectors[0].isEmpty()) return ""

            val topK = AppConfig.getRagTopK()
            val threshold = AppConfig.getRagThreshold()
            val maxContext = AppConfig.getRagMaxContext()

            val hits = RetrievalEngine.search(vectors[0], topK, threshold)
            if (hits.isEmpty()) return ""

            buildContext(hits, maxContext)
        } catch (e: Exception) {
            Log.w(TAG, "retrieveContext failed: ${e.message}", e)
            ""
        }
    }

    /**
     * 拼接 markdown 上下文，按 maxContext 字符上限截断。
     * 格式：
     *
     *   ## 参考资料
     *
     *   [KB名]
     *   chunk 文本...
     *
     *   ---
     *
     *   [KB名]
     *   chunk 文本...
     */
    private fun buildContext(hits: List<ScoredChunk>, maxContext: Int): String {
        val sb = StringBuilder()
        sb.append("## 参考资料\n\n")
        for ((idx, h) in hits.withIndex()) {
            val segment = StringBuilder()
                .append('[').append(h.kbName).append(']').append('\n')
                .append(h.chunk.text.trim()).append('\n')
            if (idx != hits.lastIndex) segment.append("\n---\n\n")

            if (sb.length + segment.length > maxContext) {
                // 截断当前段以满足上限
                val remain = (maxContext - sb.length).coerceAtLeast(0)
                if (remain > 0) sb.append(segment.substring(0, minOf(remain, segment.length)))
                break
            }
            sb.append(segment)
        }
        return sb.toString().trimEnd()
    }
}
