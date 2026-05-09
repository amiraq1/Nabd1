package com.example.localqwen.attachments

/**
 * Static helper that provides formatted messages related to PDF processing.
 * Extracted from MainActivity to reduce its size without changing any behavior.
 */
object PdfMessageFormatter {

    /** Message added to chat when a PDF is enqueued for background processing. */
    fun pdfQueuedMessage(title: String): String =
        "تمت إضافة ملف PDF للمعالجة في الخلفية..."

    /** Status text shown while PDF processing is running (no page info available). */
    fun pdfRunningStatus(): String =
        "جاري تحليل ملف PDF في الخلفية..."

    /** Status text shown while PDF processing is running with page progress. */
    fun pdfProgressStatus(page: Int, total: Int): String =
        "جاري استخراج النص من الصفحة $page من $total..."

    /** Chat message added when PDF processing succeeds. */
    fun pdfSuccessMessage(title: String, charCount: Int): String =
        "تم تحليل ملف PDF وحفظه في مكتبة المستندات: $title\nعدد الأحرف المستخرجة: $charCount"

    /** Chat message added when PDF processing fails. */
    fun pdfFailureMessage(error: String): String =
        "تعذر تحليل ملف PDF: $error"

    /** Chat message added when PDF processing is cancelled. */
    fun pdfCancelledMessage(): String =
        "تم إلغاء تحليل ملف PDF"

    /** Placeholder message when no text could be extracted (used by worker failure output). */
    fun pdfNoTextMessage(): String =
        "لم يتم العثور على نص واضح في ملف PDF"

    /** Error message when PDF is too large to process. */
    fun pdfTooLargeMessage(): String =
        "تعذر تحليل الملف بسبب حجمه الكبير. جرّب ملفًا أصغر."

    /** Default document title used when a PDF filename cannot be determined. */
    fun defaultPdfTitle(): String =
        "ملف PDF"
}
