package com.hwb.aianswerer.api

import android.util.Log
import com.google.gson.Gson
import com.hwb.aianswerer.config.AppConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

/**
 * Embedding HTTP 客户端（OpenAI 兼容 /v1/embeddings 协议）
 *
 * 设计要点：
 * - 单例（仿 OpenAIClient.getInstance）
 * - 复用 OkHttp + Gson + HttpLoggingInterceptor 模式，便于 logcat 调试
 * - 批量请求：默认 batch=16，避免单请求过大触发上游 413
 * - URL / Model / Key 全部从 AppConfig 动态拉，无须重启
 * - 失败抛 EmbeddingException(code, msg)，由调用方决定重试 / UI 提示
 */
class EmbeddingClient private constructor() {

    private val gson = Gson()

    private val client: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .addInterceptor(logging)
            .build()
    }

    /**
     * 批量获取向量。
     *
     * @param texts 待编码文本
     * @param batchSize 每个 HTTP 请求最多打包多少条；默认 16
     * @return 与输入顺序对齐的 FloatArray 列表
     * @throws EmbeddingException
     */
    suspend fun embed(
        texts: List<String>,
        batchSize: Int = 16
    ): List<FloatArray> = withContext(Dispatchers.IO) {
        if (texts.isEmpty()) return@withContext emptyList()

        val url = AppConfig.getEmbedUrl()
        val key = AppConfig.getEmbedKey()
        val model = AppConfig.getEmbedModel()

        if (url.isBlank() || model.isBlank()) {
            throw EmbeddingException(-1, "Embedding URL / Model 未配置")
        }
        if (key.isBlank()) {
            throw EmbeddingException(-1, "Embedding API Key 为空")
        }

        val out = ArrayList<FloatArray>(texts.size)
        for (start in texts.indices step batchSize) {
            val end = minOf(start + batchSize, texts.size)
            val batch = texts.subList(start, end)
            out.addAll(requestBatch(url, key, model, batch))
        }
        out
    }

    /**
     * 测试连接：用单条 "hello" 请求验证 URL / Key / Model 是否可用。
     */
    suspend fun testConnection(): TestResult = withContext(Dispatchers.IO) {
        try {
            val vectors = embed(listOf("hello"), batchSize = 1)
            if (vectors.isEmpty() || vectors[0].isEmpty()) {
                TestResult(false, "返回向量为空")
            } else {
                TestResult(true, "连接成功，维度=${vectors[0].size}")
            }
        } catch (e: EmbeddingException) {
            TestResult(false, "HTTP ${e.code}: ${e.message}")
        } catch (e: Exception) {
            TestResult(false, e.message ?: "未知错误")
        }
    }

    private fun requestBatch(
        url: String,
        key: String,
        model: String,
        batch: List<String>
    ): List<FloatArray> {
        val body = gson.toJson(EmbeddingRequest(model = model, input = batch))
            .toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $key")
            .addHeader("Content-Type", "application/json")
            .post(body)
            .build()

        client.newCall(request).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                Log.w(TAG, "embed HTTP ${resp.code}: ${raw.take(300)}")
                throw EmbeddingException(resp.code, "Embedding 请求失败: ${resp.message}")
            }
            val parsed = try {
                gson.fromJson(raw, EmbeddingResponse::class.java)
            } catch (e: Exception) {
                throw EmbeddingException(-2, "Embedding 响应解析失败: ${e.message}")
            }
            if (parsed.data.isEmpty()) {
                throw EmbeddingException(-3, "Embedding 响应 data 为空")
            }
            // 服务端可能乱序返回，按 index 排序后输出
            return parsed.data.sortedBy { it.index }.map { d ->
                FloatArray(d.embedding.size) { d.embedding[it] }
            }
        }
    }

    data class TestResult(val success: Boolean, val message: String)

    companion object {
        private const val TAG = "EmbeddingClient"

        @Volatile
        private var instance: EmbeddingClient? = null

        fun getInstance(): EmbeddingClient {
            return instance ?: synchronized(this) {
                instance ?: EmbeddingClient().also { instance = it }
            }
        }
    }
}
