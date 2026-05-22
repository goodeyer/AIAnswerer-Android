package com.hwb.aianswerer.parser

/**
 * 文档解析器工厂
 *
 * 根据文件扩展名分派对应 Parser；不支持的类型返回 null（UI 提示用户）。
 *
 * 支持扩展名：txt / md / pdf / docx / xlsx
 */
object DocumentParserFactory {

    /**
     * @param fileName 含扩展名的文件名（不区分大小写）
     * @return DocumentParser 或 null（不支持）
     */
    fun create(fileName: String): DocumentParser? {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "txt" -> TxtParser()
            "md", "markdown" -> MdParser()
            "pdf" -> PdfParser()
            "docx" -> DocxParser()
            "xlsx" -> XlsxParser()
            else -> null
        }
    }

    /**
     * 提供给 UI 列文件选择 mime filter 用。
     */
    val supportedExtensions: List<String> = listOf("txt", "md", "pdf", "docx", "xlsx")
}
