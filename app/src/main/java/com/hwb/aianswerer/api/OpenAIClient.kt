package com.hwb.aianswerer.api

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.hwb.aianswerer.Constants
import com.hwb.aianswerer.MyApplication
import com.hwb.aianswerer.R
import com.hwb.aianswerer.config.AppConfig
import com.hwb.aianswerer.models.AIAnswer
import com.hwb.aianswerer.models.ChatMessage
import com.hwb.aianswerer.models.ChatRequest
import com.hwb.aianswerer.models.ChatResponse
import com.hwb.aianswerer.models.ResponseFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

/**
 * OpenAI API客户端
 *
 * 负责与OpenAI格式的API进行通信
 * 支持动态配置，从AppConfig读取最新的API设置
 */
class OpenAIClient {

    private val gson = Gson()

    private val client: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /**
     * 分析题目并获取答案
     *
     * 动态从AppConfig读取最新的API配置
     *
     * @param recognizedText OCR识别的文本
     * @param questionTypes 题型集合（如：单选题、多选题等）
     * @param questionScope 题目内容范围
     * @return AI解析的答案，包装在Result中
     */
    suspend fun analyzeQuestion(
        recognizedText: String,
        questionTypes: Set<String> = emptySet(),
        questionScope: String = "",
        ragContext: String = ""
    ): Result<AIAnswer> = withContext(Dispatchers.IO) {
        try {
            // 从配置中读取最新的API设置
            val apiUrl = AppConfig.getApiUrl()
            val apiKey = AppConfig.getApiKey()
            val modelName = AppConfig.getModelName()

            // 验证配置有效性
            if (!AppConfig.isApiConfigValid()) {
                return@withContext Result.failure(
                    Exception(MyApplication.getString(R.string.error_api_config_invalid))
                )
            }

            // 构建请求，使用动态系统提示词
            val systemPrompt = Constants.buildSystemPrompt(questionTypes, questionScope, ragContext)
            val messages = listOf(
                ChatMessage(role = "system", content = systemPrompt),
                ChatMessage(
                    role = "user",
                    content = MyApplication.getString(
                        R.string.system_prompt_user_message,
                        recognizedText
                    )
                )
            )

            val chatRequest = ChatRequest(
                model = modelName,
                messages = messages,
                temperature = 0.3,  // 较低的温度以获得更确定的答案
                responseFormat = ResponseFormat(type = "json_object")
            )

            val requestBody = gson.toJson(chatRequest)
                .toRequestBody("application/json; charset=utf-8".toMediaType())

            val request = Request.Builder()
                .url(apiUrl)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(requestBody)
                .build()

            // 发送请求
            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    Exception(
                        MyApplication.getString(
                            R.string.error_api_request_failed,
                            response.code,
                            response.message
                        )
                    )
                )
            }

            val responseBody = response.body?.string()
                ?: return@withContext Result.failure(
                    Exception(
                        MyApplication.getString(R.string.error_empty_response)
                    )
                )

            // 解析响应
            val chatResponse = gson.fromJson(responseBody, ChatResponse::class.java)
            val answerContent = chatResponse.choices.firstOrNull()?.message?.content
                ?: return@withContext Result.failure(
                    Exception(
                        MyApplication.getString(R.string.error_no_answer_content)
                    )
                )

            // 解析AI返回的JSON答案
            val aiAnswer = try {
                gson.fromJson(extractJsonPayload(answerContent), AIAnswer::class.java)
            } catch (e: JsonSyntaxException) {
                // 如果解析失败，尝试提取文本作为答案
                AIAnswer(
                    question = MyApplication.getString(R.string.error_parse_question_failed),
                    questionType = MyApplication.getString(R.string.question_type_essay),
                    answer = answerContent
                )
            }

            Result.success(aiAnswer)

        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 提取 content 中的首个 JSON 负载（兼容 ```json 代码块 或 纯文本里含 JSON）.
     * 返回去壳后的纯 JSON 字符串（对象或数组）。
     */
    fun extractJsonPayload(content: String): String {
        val s = content.trim()

        // 1) 优先匹配 Markdown 代码块 ```...```，含可选语言标记
        val fenceRegex = Regex("(?s)```\\s*([a-zA-Z0-9_-]+)?\\s*(\\{.*?\\}|\\[.*?\\])\\s*```")
        fenceRegex.find(s)?.let { m ->
            return m.groupValues[2].trim()
        }

        // 2) 无代码块：从首个 '{' 或 '[' 开始，做括号配对提取
        val start = sequenceOf(s.indexOf('{'), s.indexOf('['))
            .filter { it >= 0 }
            .minOrNull() ?: return s // 找不到就原样返回（让 Gson 去判断）

        val openChar = s[start]
        val closeChar = if (openChar == '{') '}' else ']'

        var depth = 0
        var inString = false
        var escape = false
        var end = -1

        for (i in start until s.length) {
            val c = s[i]
            if (inString) {
                if (escape) {
                    escape = false
                } else {
                    if (c == '\\') escape = true
                    else if (c == '"') inString = false
                }
            } else {
                if (c == '"') inString = true
                else if (c == openChar) depth++
                else if (c == closeChar) {
                    depth--
                    if (depth == 0) {
                        end = i
                        break
                    }
                }
            }
        }
        if (end != -1) {
            return s.substring(start, end + 1).trim()
        }

        // 3) 兜底：返回原文（可能是已是纯 JSON）
        return s
    }

    /**
     * 测试API连接，支持传入未保存的配置参数
     */
    suspend fun testConnection(
        apiUrl: String,
        apiKey: String,
        modelName: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            // 验证配置有效性
            if (!AppConfig.isApiConfigValid(apiUrl, apiKey, modelName)) {
                return@withContext Result.failure(
                    Exception(MyApplication.getString(R.string.error_api_config_incomplete))
                )
            }

            // 构建最简单的测试请求
            val messages = listOf(
                ChatMessage(role = "user", content = "hello")
            )

            val chatRequest = ChatRequest(
                model = modelName,
                messages = messages,
            )

            val requestBody = gson.toJson(chatRequest)
                .toRequestBody("application/json; charset=utf-8".toMediaType())

            val request = Request.Builder()
                .url(apiUrl)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(requestBody)
                .build()

            // 发送请求
            val response = client.newCall(request).execute()

            // 检查响应状态
            if (!response.isSuccessful) {
                val errorMessage = when (response.code) {
                    401 -> R.string.error_api_key_invalid
                    403 -> R.string.error_api_forbidden
                    404 -> R.string.error_api_not_found
                    429 -> R.string.error_api_rate_limited
                    500, 502, 503 -> R.string.error_api_server_error
                    else -> null
                }?.let { MyApplication.getString(it) }
                    ?: MyApplication.getString(
                        R.string.error_http_status_generic,
                        response.code,
                        response.message
                    )
                return@withContext Result.failure(Exception(errorMessage))
            }

            // 验证响应体存在
            val responseBody = response.body?.string()
            if (responseBody.isNullOrBlank()) {
                return@withContext Result.failure(
                    Exception(MyApplication.getString(R.string.error_api_empty_response))
                )
            }

            // 尝试解析响应以验证格式正确
            try {
                val chatResponse = gson.fromJson(responseBody, ChatResponse::class.java)
                if (chatResponse.choices.isEmpty()) {
                    return@withContext Result.failure(
                        Exception(MyApplication.getString(R.string.error_api_response_invalid))
                    )
                }
            } catch (e: JsonSyntaxException) {
                return@withContext Result.failure(
                    Exception(MyApplication.getString(R.string.error_api_response_error))
                )
            }

            // 测试成功
            Result.success(MyApplication.getString(R.string.toast_connection_success))

        } catch (e: java.net.UnknownHostException) {
            Result.failure(Exception(MyApplication.getString(R.string.error_api_unknown_host)))
        } catch (e: java.net.SocketTimeoutException) {
            Result.failure(Exception(MyApplication.getString(R.string.error_api_timeout)))
        } catch (e: javax.net.ssl.SSLException) {
            Result.failure(Exception(MyApplication.getString(R.string.error_api_ssl)))
        } catch (e: Exception) {
            val unknownError = MyApplication.getString(R.string.error_unknown)
            Result.failure(
                Exception(
                    MyApplication.getString(
                        R.string.error_connection_test_failed,
                        e.message ?: unknownError
                    )
                )
            )
        }
    }

    companion object {
        @Volatile
        private var instance: OpenAIClient? = null

        fun getInstance(): OpenAIClient {
            return instance ?: synchronized(this) {
                instance ?: OpenAIClient().also { instance = it }
            }
        }
    }
}

