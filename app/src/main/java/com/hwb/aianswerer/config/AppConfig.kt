package com.hwb.aianswerer.config

import android.content.Context
import com.hwb.aianswerer.BuildConfig
import com.tencent.mmkv.MMKV

/**
 * 应用配置管理类
 * 负责保存和读取用户的API配置、语言设置等
 * 使用MMKV作为底层存储，提供高性能的key-value数据持久化
 */
object AppConfig {

    // MMKV存储键名
    private const val KEY_API_URL = "api_url"
    private const val KEY_API_KEY = "api_key"
    private const val KEY_MODEL_NAME = "model_name"
    private const val KEY_LANGUAGE = "language"
    private const val KEY_AUTO_SUBMIT = "auto_submit"
    private const val KEY_AUTO_COPY = "auto_copy"
    private const val KEY_QUESTION_TYPES = "question_types"
    private const val KEY_QUESTION_SCOPE = "question_scope"
    private const val KEY_IS_FIRST_LAUNCH = "is_first_launch"
    private const val KEY_CROP_MODE = "crop_mode"
    private const val KEY_SHOW_ANSWER_CARD_QUESTION = "show_answer_card_question"
    private const val KEY_SHOW_ANSWER_CARD_OPTIONS = "show_answer_card_options"

    // 语言代码常量
    const val LANGUAGE_ZH = "zh"
    const val LANGUAGE_EN = "en"

    // 截图识别模式常量
    const val CROP_MODE_FULL = "full"           // 全屏
    const val CROP_MODE_EACH = "each"           // 部分识别（每次）
    const val CROP_MODE_ONCE = "once"           // 部分识别（单次）

    private lateinit var mmkv: MMKV

    /**
     * 初始化MMKV
     * 应该在Application.onCreate()中调用
     */
    fun init(context: Context) {
        MMKV.initialize(context)
        mmkv = MMKV.defaultMMKV()
    }

    // ========== API配置相关 ==========

    /**
     * 保存API URL
     */
    fun saveApiUrl(url: String) {
        mmkv.encode(KEY_API_URL, url)
    }

    /**
     * 获取API URL
     * @return API URL，优先返回BuildConfig配置，其次返回用户设置值，最后返回默认值
     */
    fun getApiUrl(): String {
        return mmkv.decodeString(KEY_API_URL, BuildConfig.API_URL) ?: ""
    }

    /**
     * 保存API Key
     */
    fun saveApiKey(key: String) {
        mmkv.encode(KEY_API_KEY, key)
    }

    /**
     * 获取API Key
     * @return API Key，优先返回BuildConfig配置，其次返回用户设置值，最后返回空值
     */
    fun getApiKey(): String {
        return mmkv.decodeString(KEY_API_KEY, BuildConfig.API_KEY) ?: ""
    }

    /**
     * 保存模型名称
     */
    fun saveModelName(model: String) {
        mmkv.encode(KEY_MODEL_NAME, model)
    }

    /**
     * 获取模型名称
     * @return 模型名称，优先返回BuildConfig配置，其次返回用户设置值，最后返回默认值
     */
    fun getModelName(): String {
        return mmkv.decodeString(KEY_MODEL_NAME, BuildConfig.API_MODEL) ?: ""
    }

    /**
     * 验证API配置是否完整
     * @return true表示配置完整，false表示缺少必要配置
     */
    fun isApiConfigValid(
        url: String = getApiUrl(),
        key: String = getApiKey(),
        model: String = getModelName()
    ): Boolean {

        return url.isNotBlank() && key.isNotBlank() && model.isNotBlank() && url.startsWith("http")
    }

    // ========== 语言设置相关 ==========

    /**
     * 保存语言设置
     * @param languageCode 语言代码 (zh/en)
     */
    fun saveLanguage(languageCode: String) {
        mmkv.encode(KEY_LANGUAGE, languageCode)
    }

    /**
     * 获取当前设置的语言
     * @return 语言代码，默认为中文
     */
    fun getLanguage(): String {
        return mmkv.decodeString(KEY_LANGUAGE, LANGUAGE_ZH) ?: LANGUAGE_ZH
    }

    // ========== 应用设置相关 ==========

    /**
     * 保存自动提交设置
     * @param enabled 是否启用自动提交（识别后直接获取答案，不显示确认对话框）
     */
    fun saveAutoSubmit(enabled: Boolean) {
        mmkv.encode(KEY_AUTO_SUBMIT, enabled)
    }

    /**
     * 获取自动提交设置
     * @return 是否启用自动提交，默认为false
     */
    fun getAutoSubmit(): Boolean {
        return mmkv.decodeBool(KEY_AUTO_SUBMIT, true)
    }

    /**
     * 保存自动复制到剪贴板设置
     * @param enabled 是否启用自动复制（生成答案后自动复制到剪贴板）
     */
    fun saveAutoCopy(enabled: Boolean) {
        mmkv.encode(KEY_AUTO_COPY, enabled)
    }

    /**
     * 获取自动复制到剪贴板设置
     * @return 是否启用自动复制，默认为true（提升用户体验）
     */
    fun getAutoCopy(): Boolean {
        return mmkv.decodeBool(KEY_AUTO_COPY, false)
    }

    // ========== 答题设置相关 ==========

    /**
     * 保存题型设置
     * @param types 题型集合（如：单选题、多选题等）
     */
    fun saveQuestionTypes(types: Set<String>) {
        val typesString = types.joinToString(",")
        mmkv.encode(KEY_QUESTION_TYPES, typesString)
    }

    /**
     * 获取题型设置
     * @return 题型集合，默认为单选题
     */
    fun getQuestionTypes(): Set<String> {
        val typesString = mmkv.decodeString(KEY_QUESTION_TYPES, "单选题") ?: "单选题"
        return if (typesString.isBlank()) {
            setOf("单选题")
        } else {
            typesString.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        }
    }

    /**
     * 保存题目内容范围
     * @param scope 题目内容范围描述
     */
    fun saveQuestionScope(scope: String) {
        mmkv.encode(KEY_QUESTION_SCOPE, scope)
    }

    /**
     * 获取题目内容范围
     * @return 题目内容范围，默认为空字符串（不限制）
     */
    fun getQuestionScope(): String {
        return mmkv.decodeString(KEY_QUESTION_SCOPE, "") ?: ""
    }

    // ========== 答题卡片显示控制相关 ==========
    /**
     * 保存答题卡片是否显示题目设置
     * @param show 是否显示题目
     */
    fun saveShowAnswerCardQuestion(show: Boolean) {
        mmkv.encode(KEY_SHOW_ANSWER_CARD_QUESTION, show)
    }

    /**
     * 获取答题卡片是否显示题目设置
     * @return 是否显示题目，默认为true
     */
    fun getShowAnswerCardQuestion(): Boolean {
        return mmkv.decodeBool(KEY_SHOW_ANSWER_CARD_QUESTION, true)
    }

    /**
     * 保存答题卡片是否显示选项设置
     * @param show 是否显示选项
     */
    fun saveShowAnswerCardOptions(show: Boolean) {
        mmkv.encode(KEY_SHOW_ANSWER_CARD_OPTIONS, show)
    }

    /**
     * 获取答题卡片是否显示选项设置
     * @return 是否显示选项，默认为true
     */
    fun getShowAnswerCardOptions(): Boolean {
        return mmkv.decodeBool(KEY_SHOW_ANSWER_CARD_OPTIONS, true)
    }

    // ========== 截图识别模式相关 ==========

    /**
     * 保存截图识别模式
     * @param mode 识别模式（CROP_MODE_FULL/CROP_MODE_EACH/CROP_MODE_ONCE）
     */
    fun saveCropMode(mode: String) {
        mmkv.encode(KEY_CROP_MODE, mode)
    }

    /**
     * 获取截图识别模式
     * @return 识别模式，默认为全屏模式
     */
    fun getCropMode(): String {
        return mmkv.decodeString(KEY_CROP_MODE, CROP_MODE_FULL) ?: CROP_MODE_FULL
    }

    // ========== 首次启动相关 ==========

    /**
     * 检查是否为首次启动
     * @return true表示首次启动，false表示已启动过
     */
    fun isFirstLaunch(): Boolean {
        return mmkv.decodeBool(KEY_IS_FIRST_LAUNCH, true)
    }

    /**
     * 标记首次启动完成
     */
    fun setFirstLaunchComplete() {
        mmkv.encode(KEY_IS_FIRST_LAUNCH, false)
    }

    // ========== RAG 知识库相关（Phase 1-3 stub；UI 写入 Phase 4 接入） ==========
    // 这一组 getter 在本波（Phase 1-3）只提供默认值，使 EmbeddingClient /
    // RagOrchestrator 可以直接编译运行；Phase 4 会补 setter + UI 绑定。

    private const val KEY_RAG_ENABLED = "rag_enabled"
    private const val KEY_RAG_TOP_K = "rag_top_k"
    private const val KEY_RAG_THRESHOLD = "rag_threshold"
    private const val KEY_RAG_MAX_CONTEXT = "rag_max_context"
    private const val KEY_EMBED_URL = "embed_url"
    private const val KEY_EMBED_MODEL = "embed_model"
    private const val KEY_EMBED_KEY = "embed_key"
    private const val KEY_EMBED_USE_CHAT_KEY = "embed_use_chat_key"

    /** RAG 总开关；默认开。 */
    fun getRagEnabled(): Boolean = mmkv.decodeBool(KEY_RAG_ENABLED, true)

    /** 检索 topK；默认 3。 */
    fun getRagTopK(): Int = mmkv.decodeInt(KEY_RAG_TOP_K, 3).coerceAtLeast(1)

    /** 相似度阈值（加权后分数）；< 阈值视为未命中，走通用兜底。 */
    fun getRagThreshold(): Float = mmkv.decodeFloat(KEY_RAG_THRESHOLD, 0.5f)

    /** 拼入 prompt 的最大字符数，防止上下文超长。 */
    fun getRagMaxContext(): Int = mmkv.decodeInt(KEY_RAG_MAX_CONTEXT, 1500).coerceAtLeast(200)

    /** Embedding API URL（OpenAI 兼容 /v1/embeddings）。 */
    fun getEmbedUrl(): String =
        mmkv.decodeString(KEY_EMBED_URL, "https://api.siliconflow.cn/v1/embeddings")
            ?: "https://api.siliconflow.cn/v1/embeddings"

    /** Embedding 模型名。 */
    fun getEmbedModel(): String =
        mmkv.decodeString(KEY_EMBED_MODEL, "BAAI/bge-m3") ?: "BAAI/bge-m3"

    /**
     * Embedding API Key：
     * - 若用户勾选「沿用 chat key」且独立 key 为空 → fallback getApiKey()
     * - 否则使用独立 key（可为空，调用方需自行校验）
     */
    fun getEmbedKey(): String {
        val useChat = getEmbedUseChatKey()
        val ownKey = mmkv.decodeString(KEY_EMBED_KEY, "") ?: ""
        return if (useChat && ownKey.isBlank()) getApiKey() else ownKey
    }

    /** 是否沿用 chat 的 API Key；默认 true（多数用户同服务商）。 */
    fun getEmbedUseChatKey(): Boolean = mmkv.decodeBool(KEY_EMBED_USE_CHAT_KEY, true)

    // ---------- RAG setter（Phase 4 接入 UI） ----------

    /** RAG 总开关 */
    fun saveRagEnabled(enabled: Boolean) {
        mmkv.encode(KEY_RAG_ENABLED, enabled)
    }

    /** 检索 topK，最小 1 */
    fun saveRagTopK(topK: Int) {
        mmkv.encode(KEY_RAG_TOP_K, topK.coerceAtLeast(1))
    }

    /** 相似度阈值（加权后） */
    fun saveRagThreshold(threshold: Float) {
        mmkv.encode(KEY_RAG_THRESHOLD, threshold)
    }

    /** 拼入 prompt 的最大字符数，最小 200 */
    fun saveRagMaxContext(maxChars: Int) {
        mmkv.encode(KEY_RAG_MAX_CONTEXT, maxChars.coerceAtLeast(200))
    }

    /** Embedding API URL */
    fun saveEmbedUrl(url: String) {
        mmkv.encode(KEY_EMBED_URL, url)
    }

    /** Embedding 模型名 */
    fun saveEmbedModel(model: String) {
        mmkv.encode(KEY_EMBED_MODEL, model)
    }

    /** Embedding API Key（独立 key；为空且 useChatKey=true 时回落 chat key） */
    fun saveEmbedKey(key: String) {
        mmkv.encode(KEY_EMBED_KEY, key)
    }

    /** 是否沿用 chat 的 API Key */
    fun saveEmbedUseChatKey(useChat: Boolean) {
        mmkv.encode(KEY_EMBED_USE_CHAT_KEY, useChat)
    }

    /**
     * 读取 Embedding 独立 key 的原始值（不做 chat key 回落）。
     * 用于设置页回显——避免把 chat key 暴露给用户误以为已写入。
     */
    fun getEmbedKeyRaw(): String {
        return mmkv.decodeString(KEY_EMBED_KEY, "") ?: ""
    }
}

