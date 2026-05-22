package com.hwb.aianswerer.api

import com.google.gson.annotations.SerializedName

/**
 * OpenAI 兼容 /v1/embeddings 接口的数据模型。
 *
 * 复用与 ChatCompletion 相同的 Gson + OkHttp 链路；
 * SiliconFlow / 智谱 / OpenAI 官方均兼容此协议。
 */
data class EmbeddingRequest(
    val model: String,
    val input: List<String>,
    @SerializedName("encoding_format")
    val encodingFormat: String = "float"
)

data class EmbeddingResponse(
    val data: List<EmbeddingData> = emptyList(),
    val model: String = "",
    val usage: EmbeddingUsage? = null
)

data class EmbeddingData(
    /** 服务端固定返回 "embedding"，保留字段名兼容性 */
    @SerializedName("object")
    val objectType: String = "embedding",
    val embedding: List<Float> = emptyList(),
    val index: Int = 0
)

data class EmbeddingUsage(
    @SerializedName("prompt_tokens")
    val promptTokens: Int = 0,
    @SerializedName("total_tokens")
    val totalTokens: Int = 0
)

/**
 * Embedding 失败自定义异常，便于 UI 区分 HTTP 状态。
 */
class EmbeddingException(
    val code: Int,
    message: String
) : RuntimeException(message)
