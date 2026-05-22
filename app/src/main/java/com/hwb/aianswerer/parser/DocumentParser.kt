package com.hwb.aianswerer.parser

import android.content.Context
import android.net.Uri

/**
 * 文档解析器统一接口。
 *
 * 所有解析器为 suspend 函数，内部应自行切换到 Dispatchers.IO；
 * onProgress 用于 PDF 等多页文档汇报阶段进度（page, total），其他实现可忽略。
 */
interface DocumentParser {

    /**
     * 解析给定 uri 指向的文档。
     *
     * @param uri 文档 URI（content:// 或 file://）
     * @param ctx 用于打开 ContentResolver
     * @param onProgress 进度回调：(已完成页, 总页)；非分页文档解析完成后回调 (1,1)
     * @return ParseResult
     * @throws Exception 解析失败原样抛出（OOM/格式错误等），由调用方包装 UI 提示
     */
    suspend fun parse(
        uri: Uri,
        ctx: Context,
        onProgress: ((current: Int, total: Int) -> Unit)? = null
    ): ParseResult
}
