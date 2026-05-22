package com.hwb.aianswerer.parser

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset

/**
 * 纯文本（.txt）解析器
 *
 * 编码探测策略：优先 UTF-8，遇到非法字节则回退 GBK（中文系统常见）。
 * 不做 BOM 解析（Java 的 InputStreamReader 会忽略 BOM，留几个字符不影响向量召回）。
 */
class TxtParser : DocumentParser {

    override suspend fun parse(
        uri: Uri,
        ctx: Context,
        onProgress: ((Int, Int) -> Unit)?
    ): ParseResult = withContext(Dispatchers.IO) {
        val bytes = ctx.contentResolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArrayOutputStream()
            val tmp = ByteArray(8192)
            while (true) {
                val n = input.read(tmp)
                if (n <= 0) break
                buffer.write(tmp, 0, n)
            }
            buffer.toByteArray()
        } ?: throw IllegalStateException("无法打开文件: $uri")

        val text = try {
            decode(bytes, Charsets.UTF_8)
        } catch (_: Exception) {
            decode(bytes, Charset.forName("GBK"))
        }

        onProgress?.invoke(1, 1)
        ParseResult(plainText = text, pageCount = 1)
    }

    /**
     * 严格解码：报告非法字节时抛 MalformedInputException。
     */
    private fun decode(bytes: ByteArray, charset: Charset): String {
        val decoder = charset.newDecoder()
            .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
            .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
        return decoder.decode(java.nio.ByteBuffer.wrap(bytes)).toString()
    }
}
