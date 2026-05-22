package com.hwb.aianswerer.parser

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

/**
 * .docx 解析器（零依赖，基于 ZIP + 正则）
 *
 * docx 本质是 ZIP 包，正文位于 `word/document.xml`，文本节点形如：
 *   <w:t xml:space="preserve">Hello</w:t>
 * 段落由 `</w:p>` 分隔。本实现：
 * 1. ZipInputStream 找到 document.xml
 * 2. 先在 </w:p> 处插入换行
 * 3. 用正则 `<w:t[^>]*>(.+?)</w:t>` 抽取所有文本节点内容
 * 4. XML 实体解码（&amp; &lt; &gt; &quot; &apos;）
 *
 * 已知局限：不解析表格嵌套结构（仅按 <w:t> 顺序拼接），不处理图片/批注。
 */
class DocxParser : DocumentParser {

    override suspend fun parse(
        uri: Uri,
        ctx: Context,
        onProgress: ((Int, Int) -> Unit)?
    ): ParseResult = withContext(Dispatchers.IO) {
        val xml = ctx.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input).use { zip ->
                var entry = zip.nextEntry
                var found: ByteArray? = null
                while (entry != null) {
                    if (entry.name == "word/document.xml") {
                        val out = ByteArrayOutputStream()
                        zip.copyTo(out)
                        found = out.toByteArray()
                        break
                    }
                    entry = zip.nextEntry
                }
                found
            }
        } ?: throw IllegalStateException("docx 内未找到 word/document.xml")

        val xmlStr = String(xml, Charsets.UTF_8)
        // 段落断行：先把 </w:p> 换成换行占位符，再抽 <w:t>
        val withLineBreaks = xmlStr.replace("</w:p>", "</w:p>\n")
        val texts = Regex("<w:t[^>]*>([\\s\\S]*?)</w:t>")
            .findAll(withLineBreaks)
            .map { it.groupValues[1] }
            .toList()

        // 注意：正则按 </w:t> 拆分会丢失段落信息，这里改用「逐段提取」
        val sb = StringBuilder()
        for (paragraph in withLineBreaks.split('\n')) {
            val joined = Regex("<w:t[^>]*>([\\s\\S]*?)</w:t>")
                .findAll(paragraph)
                .joinToString("") { it.groupValues[1] }
            if (joined.isNotBlank()) {
                sb.append(decodeEntities(joined)).append('\n')
            }
        }

        // 若按段落抽不到任何东西（异常 docx），退回扁平拼接
        val text = if (sb.isBlank() && texts.isNotEmpty()) {
            decodeEntities(texts.joinToString("\n"))
        } else {
            sb.toString().trim()
        }

        onProgress?.invoke(1, 1)
        ParseResult(plainText = text, pageCount = 1)
    }

    private fun decodeEntities(s: String): String = s
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace("&amp;", "&")
}
