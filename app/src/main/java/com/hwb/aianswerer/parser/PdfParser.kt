package com.hwb.aianswerer.parser

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.hwb.aianswerer.TextRecognitionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * PDF 解析器（OCR 全页模式）
 *
 * 实现方式：
 * 1. PdfRenderer 按目标 DPI 渲染每页为 Bitmap
 * 2. 调用 ML Kit 中文识别器抽文本
 * 3. 汇总后返回；进度通过 onProgress(page, total) 回调
 *
 * 注意：PdfRenderer 要求 ParcelFileDescriptor 必须是 seekable file，
 * 因此 content uri 需先落盘到 cacheDir 临时文件。
 */
class PdfParser(
    private val targetDpi: Int = 600,
    private val recognizer: TextRecognitionManager = TextRecognitionManager.getInstance()
) : DocumentParser {

    override suspend fun parse(
        uri: Uri,
        ctx: Context,
        onProgress: ((Int, Int) -> Unit)?
    ): ParseResult = withContext(Dispatchers.IO) {
        val tempFile = copyToCache(uri, ctx)
        val pfd = ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(pfd)
        val builder = StringBuilder()
        val total = renderer.pageCount
        try {
            for (i in 0 until total) {
                val page = renderer.openPage(i)
                val scale = targetDpi.toFloat() / 72f   // PDF point 单位 = 1/72 inch
                val widthPx = (page.width * scale).toInt().coerceAtLeast(1)
                val heightPx = (page.height * scale).toInt().coerceAtLeast(1)
                val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(Color.WHITE)  // PdfRenderer 透明背景 OCR 会糊，提前刷白
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()

                val text = recognizer.recognizeText(bitmap).getOrElse { "" }
                if (text.isNotBlank()) {
                    builder.append(text).append("\n\n")
                }
                bitmap.recycle()
                onProgress?.invoke(i + 1, total)
            }
        } finally {
            renderer.close()
            pfd.close()
            // 临时文件用完即清理，避免占用 cache
            runCatching { tempFile.delete() }
        }
        ParseResult(plainText = builder.toString().trim(), pageCount = total)
    }

    private fun copyToCache(uri: Uri, ctx: Context): File {
        val out = File(ctx.cacheDir, "rag_pdf_${System.currentTimeMillis()}.pdf")
        ctx.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(out).use { output ->
                input.copyTo(output)
            }
        } ?: throw IllegalStateException("无法打开 PDF 文件: $uri")
        return out
    }
}
