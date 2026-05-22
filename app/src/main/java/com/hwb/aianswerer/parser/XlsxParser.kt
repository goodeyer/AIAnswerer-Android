package com.hwb.aianswerer.parser

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

/**
 * .xlsx 解析器（零依赖）
 *
 * Excel 2007+ 同样是 ZIP 包：
 * - `xl/sharedStrings.xml`：去重字符串池，<si><t>...</t></si>
 * - `xl/worksheets/sheet*.xml`：每个 cell <c r="A1" t="s"><v>idx</v></c>
 *   - t="s" → v 是 sharedStrings 索引
 *   - 否则 → v 是数值/inline 字符串本身
 *
 * 输出按 row 换行、cell 用 \t 拼接，保留可读性。表格头与首列共同提供语义上下文。
 */
class XlsxParser : DocumentParser {

    override suspend fun parse(
        uri: Uri,
        ctx: Context,
        onProgress: ((Int, Int) -> Unit)?
    ): ParseResult = withContext(Dispatchers.IO) {
        // 一次性读取所有目标 entry（流不可回溯，需缓存）
        val sheetXmls = mutableListOf<Pair<String, String>>()  // name -> xml
        var sharedStringsXml: String? = null

        ctx.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val name = entry.name
                    if (name == "xl/sharedStrings.xml") {
                        sharedStringsXml = readAsText(zip)
                    } else if (name.startsWith("xl/worksheets/sheet") && name.endsWith(".xml")) {
                        sheetXmls.add(name to readAsText(zip))
                    }
                    entry = zip.nextEntry
                }
            }
        } ?: throw IllegalStateException("无法打开 xlsx: $uri")

        val sharedStrings = sharedStringsXml?.let { parseSharedStrings(it) } ?: emptyList()

        val sb = StringBuilder()
        // 按 sheet 名排序，保证 sheet1, sheet2... 顺序稳定
        sheetXmls.sortedBy { it.first }.forEach { (name, xml) ->
            sb.append("# ").append(name.substringAfterLast('/')).append('\n')
            sb.append(parseSheet(xml, sharedStrings)).append("\n\n")
        }

        onProgress?.invoke(1, 1)
        ParseResult(plainText = sb.toString().trim(), pageCount = 1)
    }

    private fun readAsText(zip: ZipInputStream): String {
        val out = ByteArrayOutputStream()
        zip.copyTo(out)
        return out.toString(Charsets.UTF_8.name())
    }

    /**
     * 解析 sharedStrings.xml；每个 <si> 元素汇总其内部所有 <t> 文本（处理富文本 rPr）
     */
    private fun parseSharedStrings(xml: String): List<String> {
        val list = mutableListOf<String>()
        val siRegex = Regex("<si>([\\s\\S]*?)</si>")
        val tRegex = Regex("<t[^>]*>([\\s\\S]*?)</t>")
        for (si in siRegex.findAll(xml)) {
            val inner = si.groupValues[1]
            val text = tRegex.findAll(inner)
                .joinToString("") { decodeEntities(it.groupValues[1]) }
            list.add(text)
        }
        return list
    }

    /**
     * 解析单 sheet：按 <row> 切分，cell 内 t="s" 走 sharedStrings 否则取 <v>/inline。
     */
    private fun parseSheet(xml: String, sharedStrings: List<String>): String {
        val sb = StringBuilder()
        val rowRegex = Regex("<row[^>]*>([\\s\\S]*?)</row>")
        val cellRegex = Regex("<c[^>]*?(?:\\s+t=\"([^\"]+)\")?[^>]*>([\\s\\S]*?)</c>")
        val vRegex = Regex("<v>([\\s\\S]*?)</v>")
        val tInlineRegex = Regex("<t[^>]*>([\\s\\S]*?)</t>")

        for (rowMatch in rowRegex.findAll(xml)) {
            val rowXml = rowMatch.groupValues[1]
            val cells = mutableListOf<String>()
            for (cellMatch in cellRegex.findAll(rowXml)) {
                val type = cellMatch.groupValues[1]   // "s" / "str" / "inlineStr" / "" / "b" / "n"
                val body = cellMatch.groupValues[2]
                val value: String = when (type) {
                    "s" -> {
                        val idx = vRegex.find(body)?.groupValues?.get(1)?.toIntOrNull()
                        if (idx != null && idx in sharedStrings.indices) sharedStrings[idx] else ""
                    }
                    "inlineStr" -> {
                        tInlineRegex.findAll(body).joinToString("") {
                            decodeEntities(it.groupValues[1])
                        }
                    }
                    else -> {
                        // 数字 / 布尔 / 公式结果等，直接取 <v>
                        val v = vRegex.find(body)?.groupValues?.get(1) ?: ""
                        decodeEntities(v)
                    }
                }
                if (value.isNotEmpty()) cells.add(value)
            }
            if (cells.isNotEmpty()) {
                sb.append(cells.joinToString("\t")).append('\n')
            }
        }
        return sb.toString()
    }

    private fun decodeEntities(s: String): String = s
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace("&amp;", "&")
}
