package com.example.localqwen.work

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.localqwen.document.DocumentStore
import com.example.localqwen.document.LocalDocument
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class PdfProcessingWorker(
    appContext: android.content.Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val uriString = inputData.getString(KEY_PDF_URI)
            ?: return failureResult("تعذر الوصول إلى ملف PDF المحدد.")
        val title = inputData.getString(KEY_PDF_TITLE)
            ?.takeIf { it.isNotBlank() }
            ?: DEFAULT_PDF_TITLE
        val uri = Uri.parse(uriString)

        return try {
            val extractedText = extractPdfText(uri, title)
            if (extractedText.isBlank()) {
                return failureResult("لم يتم العثور على نص واضح في ملف PDF")
            }

            val document = LocalDocument(
                id = UUID.randomUUID().toString(),
                title = title,
                type = "pdf",
                extractedText = extractedText,
                createdAt = System.currentTimeMillis()
            )
            DocumentStore(applicationContext).saveDocument(document)

            Result.success(
                workDataOf(
                    KEY_DOCUMENT_ID to document.id,
                    KEY_PDF_TITLE to document.title,
                    KEY_EXTRACTED_CHARS to extractedText.length
                )
            )
        } catch (_: PdfTooLargeException) {
            failureResult("تعذر تحليل الملف بسبب حجمه الكبير. جرّب ملفًا أصغر.")
        } catch (_: OutOfMemoryError) {
            failureResult("تعذر تحليل الملف بسبب حجمه الكبير. جرّب ملفًا أصغر.")
        } catch (_: SecurityException) {
            failureResult("تعذر الوصول إلى ملف PDF المحدد.")
        } catch (_: Exception) {
            failureResult("تعذر تحليل ملف PDF")
        }
    }

    private suspend fun extractPdfText(uri: Uri, title: String): String {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            applicationContext.contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
                val renderer = PdfRenderer(descriptor)
                try {
                    val pageCount = minOf(renderer.pageCount, MAX_PDF_PAGES)
                    val text = StringBuilder()

                    for (index in 0 until pageCount) {
                        setProgress(
                            workDataOf(
                                KEY_PDF_TITLE to title,
                                KEY_PROGRESS_PAGE to (index + 1),
                                KEY_PROGRESS_TOTAL to pageCount
                            )
                        )

                        val page = renderer.openPage(index)
                        try {
                            val bitmap = createSafePdfBitmap(page)
                            try {
                                val result = recognizer.process(InputImage.fromBitmap(bitmap, 0)).awaitTask()
                                val pageText = result.text.trim()
                                if (pageText.isNotEmpty()) {
                                    if (text.isNotEmpty()) text.append("\n\n")
                                    text.append(pageText)
                                }
                            } finally {
                                if (!bitmap.isRecycled) {
                                    bitmap.recycle()
                                }
                            }
                        } finally {
                            page.close()
                        }
                    }

                    return text.toString().trim()
                } finally {
                    renderer.close()
                }
            } ?: return ""
        } catch (_: OutOfMemoryError) {
            throw PdfTooLargeException()
        } finally {
            recognizer.close()
        }
    }

    private fun createSafePdfBitmap(page: PdfRenderer.Page): Bitmap {
        val width = page.width.coerceAtLeast(1)
        val height = page.height.coerceAtLeast(1)
        val scaledWidth = (width * PDF_RENDER_SCALE).toInt().coerceAtLeast(1)
        val scaledHeight = (height * PDF_RENDER_SCALE).toInt().coerceAtLeast(1)
        val ratio = minOf(
            1f,
            MAX_PDF_BITMAP_DIMENSION.toFloat() / scaledWidth,
            MAX_PDF_BITMAP_DIMENSION.toFloat() / scaledHeight
        )
        val targetWidth = (scaledWidth * ratio).toInt().coerceAtLeast(1)
        val targetHeight = (scaledHeight * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.WHITE)
            page.render(this, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        }
    }

    private fun failureResult(message: String): Result {
        return Result.failure(workDataOf(KEY_ERROR_MESSAGE to message))
    }

    private suspend fun <T> com.google.android.gms.tasks.Task<T>.awaitTask(): T =
        suspendCancellableCoroutine { cont ->
            addOnSuccessListener { cont.resume(it) }
            addOnFailureListener { cont.resumeWithException(it) }
        }

    private class PdfTooLargeException : Exception()

    companion object {
        const val KEY_PDF_URI = "pdf_uri"
        const val KEY_PDF_TITLE = "pdf_title"
        const val KEY_DOCUMENT_ID = "document_id"
        const val KEY_EXTRACTED_CHARS = "extracted_chars"
        const val KEY_ERROR_MESSAGE = "error_message"
        const val KEY_PROGRESS_PAGE = "progress_page"
        const val KEY_PROGRESS_TOTAL = "progress_total"

        private const val DEFAULT_PDF_TITLE = "ملف PDF"
        private const val MAX_PDF_PAGES = 3
        private const val MAX_PDF_BITMAP_DIMENSION = 2048
        private const val PDF_RENDER_SCALE = 1.2f
    }
}
