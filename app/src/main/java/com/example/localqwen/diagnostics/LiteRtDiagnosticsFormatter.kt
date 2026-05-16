package com.example.localqwen.diagnostics

/**
 * Data holder for LiteRT diagnostics values.
 */
data class LiteRtDiagnosticsData(
    val currentModelLabel: String,
    val modelStatusLabel: String,
    val importedModelSizeLabel: String,
    val modelFileLabel: String,
    val engineStatusLabel: String,
    val lastResponseLatencyLabel: String,
    val lastGenerationDurationLabel: String,
    val lastResponseCharCountLabel: String,
    val backendLabel: String
)

/**
 * Formats LiteRT diagnostics data into a plain-text report string.
 */
object LiteRtDiagnosticsFormatter {

    fun buildReport(data: LiteRtDiagnosticsData): String {
        val sanitizedModelFile = data.modelFileLabel.substringAfterLast("/")
        return buildString {
            appendLine("تشخيص نموذج الذكاء")
            appendLine("النموذج الحالي: ${data.currentModelLabel}")
            appendLine("حالة النموذج: ${data.modelStatusLabel}")
            appendLine("حجم النموذج المستورد: ${data.importedModelSizeLabel}")
            appendLine("ملف النموذج: $sanitizedModelFile") // Sanitized: Hide full path
            appendLine("Engine: ${data.engineStatusLabel}")
            appendLine("آخر زمن استجابة: ${data.lastResponseLatencyLabel}")
            appendLine("آخر مدة توليد: ${data.lastGenerationDurationLabel}")
            appendLine("آخر عدد أحرف الرد: ${data.lastResponseCharCountLabel}")
            appendLine("Backend: ${data.backendLabel}")
            append("ملاحظة: يعتمد الأداء على الجهاز والذاكرة وحجم النموذج.")
        }
    }
}
