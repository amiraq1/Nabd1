package com.example.localqwen.diagnostics

import java.util.Locale

/**
 * Formats Local Model Manager quick-test results into display strings.
 */
object LocalModelDiagnosticsFormatter {

    fun formatGenerationTest(
        success: Boolean,
        firstTokenLatencyMs: Long?,
        totalDurationMs: Long?,
        responseCharCount: Int?,
        errorMessage: String?
    ): String {
        if (!success) return "فشل اختبار التوليد القصير: ${errorMessage ?: "تعذر إكمال الاختبار"}"
        return buildString {
            append("نجح اختبار التوليد القصير")
            firstTokenLatencyMs?.let { append(" • أول رمز: ${it}ms") }
            totalDurationMs?.let { append(" • المدة: ${it}ms") }
            responseCharCount?.let { append(" • الأحرف: $it") }
        }
    }

    fun formatSpeedTest(
        success: Boolean,
        firstTokenLatencyMs: Long?,
        totalDurationMs: Long?,
        responseCharCount: Int?,
        errorMessage: String?
    ): String {
        if (!success) return "فشل اختبار السرعة: ${errorMessage ?: "تعذر إكمال الاختبار"}"
        val duration = totalDurationMs ?: 0L
        val chars = responseCharCount ?: 0
        val charsPerSecond = if (duration > 0L) (chars * 1000f) / duration else 0f
        return buildString {
            append("نجح اختبار السرعة")
            firstTokenLatencyMs?.let { append(" • أول رمز: ${it}ms") }
            append(" • المدة: ${duration}ms")
            append(" • معدل الأحرف/ث: ${String.format(Locale.US, "%.1f", charsPerSecond)}")
        }
    }

    fun formatMemoryTest(
        usedBytes: Long,
        freeInsideHeapBytes: Long,
        maxHeapBytes: Long
    ): String {
        return buildString {
            append("نجح اختبار الذاكرة")
            append(" • مستخدم: ${formatStorageSize(usedBytes)}")
            append(" • متاح داخل heap: ${formatStorageSize(freeInsideHeapBytes)}")
            append(" • حد heap: ${formatStorageSize(maxHeapBytes)}")
        }
    }

    private fun formatStorageSize(bytes: Long): String {
        return when {
            bytes >= 1024L * 1024L * 1024L -> String.format(Locale.US, "%.1f GB", bytes / (1024f * 1024f * 1024f))
            bytes >= 1024L * 1024L -> String.format(Locale.US, "%.1f MB", bytes / (1024f * 1024f))
            bytes >= 1024L -> String.format(Locale.US, "%.1f KB", bytes / 1024f)
            else -> "$bytes B"
        }
    }
}
