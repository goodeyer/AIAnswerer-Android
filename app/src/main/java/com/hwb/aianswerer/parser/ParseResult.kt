package com.hwb.aianswerer.parser

/**
 * 文档解析结果。
 *
 * @property plainText 抽取后的纯文本（不含格式标记），用于送入 TextChunker。
 * @property pageCount 页数（PDF 有意义，其他类型缺省为 1），用于 UI 进度展示。
 */
data class ParseResult(
    val plainText: String,
    val pageCount: Int = 1
)
