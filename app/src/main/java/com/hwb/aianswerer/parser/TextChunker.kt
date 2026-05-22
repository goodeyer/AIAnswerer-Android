package com.hwb.aianswerer.parser

/**
 * 文本切片器（chunker）
 *
 * 策略：
 * 1. 优先按段落（`\n\n` 双换行）切分，保留语义边界。
 * 2. 单段超过 chunkSize 的，再退化为按字符滑窗（chunkSize, overlap）。
 * 3. 多个短段落按贪心拼接，直至接近 chunkSize 即落盘。
 * 4. 去除全空白片段。
 *
 * chunkSize/overlap 单位均为字符（适配中文，1 字符 ≈ 1 token）。
 */
object TextChunker {

    fun chunk(
        text: String,
        chunkSize: Int = 400,
        overlap: Int = 50
    ): List<String> {
        require(chunkSize > 0) { "chunkSize must > 0" }
        require(overlap in 0 until chunkSize) { "overlap must in [0, chunkSize)" }

        val normalized = text.replace("\r\n", "\n").trim()
        if (normalized.isEmpty()) return emptyList()

        // 1) 按段落切分
        val paragraphs = normalized.split(Regex("\\n{2,}"))
            .map { it.trim() }
            .filter { it.isNotBlank() }

        val result = mutableListOf<String>()
        val buffer = StringBuilder()

        for (p in paragraphs) {
            if (p.length > chunkSize) {
                // 先把 buffer 内已积累的段落落盘
                if (buffer.isNotBlank()) {
                    result.add(buffer.toString().trim())
                    buffer.clear()
                }
                // 长段落滑窗
                result.addAll(slidingWindow(p, chunkSize, overlap))
            } else if (buffer.length + p.length + 2 <= chunkSize) {
                if (buffer.isNotEmpty()) buffer.append("\n\n")
                buffer.append(p)
            } else {
                // 当前段塞不下，先落盘 buffer，再起新 buffer 放当前段
                if (buffer.isNotBlank()) {
                    result.add(buffer.toString().trim())
                    buffer.clear()
                }
                buffer.append(p)
            }
        }
        if (buffer.isNotBlank()) {
            result.add(buffer.toString().trim())
        }
        return result.filter { it.isNotBlank() }
    }

    /**
     * 字符级滑窗：步长 = chunkSize - overlap，避免上下文断裂。
     */
    private fun slidingWindow(text: String, chunkSize: Int, overlap: Int): List<String> {
        val step = (chunkSize - overlap).coerceAtLeast(1)
        val out = mutableListOf<String>()
        var i = 0
        while (i < text.length) {
            val end = (i + chunkSize).coerceAtMost(text.length)
            val piece = text.substring(i, end).trim()
            if (piece.isNotBlank()) out.add(piece)
            if (end >= text.length) break
            i += step
        }
        return out
    }
}
