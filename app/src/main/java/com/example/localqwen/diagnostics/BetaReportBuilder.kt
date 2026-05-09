package com.example.localqwen.diagnostics

import com.example.localqwen.rag.EmbeddingStore

class BetaReportBuilder(
    private val reportDateTime: String,
    private val versionName: String,
    private val versionCode: String,
    private val deviceInfo: String,
    private val storageInfo: String,
    private val batteryInfo: String,
    private val selectedModelId: String,
    private val selectedModelDisplayName: String,
    private val modelImported: Boolean,
    private val modelSizeLabel: String,
    private val modelState: String,
    private val engineReady: Boolean,
    private val lastFirstTokenLatencyMs: Long?,
    private val lastGenerationDurationMs: Long?,
    private val lastResponseCharCount: Int,
    private val ragModeName: String,
    private val embeddingBackendName: String,
    private val embeddingImported: Boolean,
    private val embeddingSizeLabel: String,
    private val embeddingModelPath: String,
    private val selectedDocumentTitle: String?,
    private val indexInfo: EmbeddingStore.EmbeddingIndexInfo?,
    private val chatSessionsCount: Int,
    private val documentsCount: Int,
    private val embeddingIndexesCount: Int
) {
    fun build(): String {
        return buildString {
            appendLine("تقرير بيتا - نبض")
            appendLine("التاريخ: $reportDateTime")
            appendLine("الإصدار: $versionName")
            appendLine("رقم الإصدار: $versionCode")
            appendLine("Build type: غير متاح")
            appendLine()
            appendLine("معلومات الجهاز:")
            appendLine(deviceInfo)
            appendLine(storageInfo)
            appendLine(batteryInfo)
            appendLine()
            appendLine("معلومات النموذج:")
            appendLine(
                "النموذج المحدد: ${
                    when (selectedModelId) {
                        "gemma_e2b" -> "Gemma E2B"
                        "gemma_e4b" -> "Gemma E4B"
                        else -> selectedModelDisplayName
                    }
                }"
            )
            appendLine("النموذج مستورد: ${yesNo(modelImported)}")
            appendLine("حجم النموذج: $modelSizeLabel")
            appendLine("حالة النموذج: $modelState")
            appendLine("Engine: ${if (engineReady) "جاهز" else "غير جاهز"}")
            appendLine("Backend: CPU")
            appendLine("آخر زمن أول رد: ${lastFirstTokenLatencyMs?.let { "${it}ms" } ?: "غير متاح بعد"}")
            appendLine("آخر مدة توليد: ${lastGenerationDurationMs?.let { "${it}ms" } ?: "غير متاح بعد"}")
            appendLine("آخر عدد أحرف الرد: ${if (lastResponseCharCount > 0) lastResponseCharCount else "غير متاح بعد"}")
            appendLine()
            appendLine("معلومات RAG:")
            appendLine("وضع RAG: $ragModeName")
            appendLine("محرك التضمين: $embeddingBackendName")
            appendLine("نموذج التضمين مستورد: ${yesNo(embeddingImported)}")
            appendLine("حجم نموذج التضمين: $embeddingSizeLabel")
            appendLine("نموذج التضمين: $embeddingModelPath")
            appendLine("مستند محدد: ${yesNo(selectedDocumentTitle != null)}")
            if (selectedDocumentTitle != null) {
                appendLine("عنوان المستند المحدد: $selectedDocumentTitle")
            }
            appendLine("الفهرس الدلالي للمستند المحدد: ${yesNo(indexInfo != null)}")
            appendLine("عدد المقاطع المفهرسة: ${indexInfo?.chunkCount ?: 0}")
            appendLine()
            appendLine("إحصاءات التخزين المحلي:")
            appendLine("عدد المحادثات: $chatSessionsCount")
            appendLine("عدد المستندات المحفوظة: $documentsCount")
            appendLine("عدد الفهارس الدلالية: $embeddingIndexesCount")
            appendLine()
            append("هذا التقرير لا يحتوي على نصوص المحادثات أو محتوى المستندات.")
        }
    }

    private fun yesNo(value: Boolean): String {
        return if (value) "نعم" else "لا"
    }
}
