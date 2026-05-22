package com.hwb.aianswerer.rag

import com.hwb.aianswerer.db.entities.KbChunk

/**
 * 检索结果包装。
 *
 * @property chunk 命中的原始 chunk
 * @property score 加权后的最终得分 = cosine × (kbPriority/100)
 * @property kbName 所属知识库名称（拼 prompt 时显示来源）
 */
data class ScoredChunk(
    val chunk: KbChunk,
    val score: Float,
    val kbName: String
)
