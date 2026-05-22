package com.hwb.aianswerer.parser

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Markdown (.md) 解析器
 *
 * 复用 TxtParser 读字节，然后剥离常见 Markdown 标记，保留纯文本以利 Embedding。
 *
 * 移除规则（按顺序执行）：
 * 1. 代码块 ``` ... ``` 整段去除（避免代码污染语义向量）
 * 2. 行内 `code` 去掉反引号
 * 3. 图片 `![alt](url)` → alt
 * 4. 链接 `[text](url)` → text
 * 5. 标题前导 `#`、列表前导 `- * +`、引用 `>`、强调 `* _` 移除
 * 6. 多余空行合并
 */
class MdParser(
    private val txtParser: TxtParser = TxtParser()
) : DocumentParser {

    override suspend fun parse(
        uri: Uri,
        ctx: Context,
        onProgress: ((Int, Int) -> Unit)?
    ): ParseResult = withContext(Dispatchers.IO) {
        val raw = txtParser.parse(uri, ctx, null).plainText
        val cleaned = strip(raw)
        onProgress?.invoke(1, 1)
        ParseResult(plainText = cleaned, pageCount = 1)
    }

    private fun strip(src: String): String {
        var s = src
        // 1) 代码块（含语言标识）
        s = Regex("```[\\s\\S]*?```").replace(s, " ")
        // 2) 行内 code
        s = Regex("`([^`]+)`").replace(s, "$1")
        // 3) 图片
        s = Regex("!\\[([^\\]]*)]\\([^)]*\\)").replace(s, "$1")
        // 4) 链接
        s = Regex("\\[([^\\]]+)]\\([^)]*\\)").replace(s, "$1")
        // 5) 行级前缀与强调标记
        val sb = StringBuilder()
        for (line in s.split('\n')) {
            var l = line
            // 行首 #、>、- *、+、数字列表
            l = l.replace(Regex("^\\s{0,3}#{1,6}\\s+"), "")
            l = l.replace(Regex("^\\s{0,3}>\\s?"), "")
            l = l.replace(Regex("^\\s{0,3}([*+\\-])\\s+"), "")
            l = l.replace(Regex("^\\s{0,3}\\d+\\.\\s+"), "")
            // 强调
            l = l.replace(Regex("\\*\\*(.+?)\\*\\*"), "$1")
            l = l.replace(Regex("__(.+?)__"), "$1")
            l = l.replace(Regex("\\*(.+?)\\*"), "$1")
            l = l.replace(Regex("_(.+?)_"), "$1")
            // 水平线
            l = l.replace(Regex("^\\s*[-*_]{3,}\\s*$"), "")
            sb.append(l).append('\n')
        }
        // 6) 合并多余空行
        return sb.toString().replace(Regex("\\n{3,}"), "\n\n").trim()
    }
}
