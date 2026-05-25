package com.example.localqwen.runtime

import com.example.localqwen.modelruntime.ModelRuntimeType

/**
 * الواجهة الأساسية لأي محرك تشغيل نماذج داخل نبض.
 */
interface ModelRuntimeInterface {
    val runtimeId: String
    val displayName: String
    val runtimeType: ModelRuntimeType

    /** تهيئة المحرك وتحميل النموذج من المسار المحدد. */
    suspend fun initialize(modelPath: String): RuntimeStatus

    /** توليد النص بشكل تدفقي (Streaming). */
    suspend fun generate(
        request: RuntimeGenerationRequest,
        onChunk: suspend (RuntimeGenerationChunk) -> Unit
    ): RuntimeGenerationResult

    /** إيقاف عملية التوليد الحالية فوراً. */
    suspend fun stopGeneration()

    /** تحرير الموارد وإغلاق المحرك. */
    suspend fun release()

    /** التحقق مما إذا كان المحرك متاحاً للعمل على الجهاز الحالي. */
    fun isAvailable(): Boolean
}

/** البيانات المطلوبة لعملية التوليد. */
data class RuntimeGenerationRequest(
    val prompt: String,
    val systemPrompt: String? = null,
    val maxTokens: Int = 512,
    val temperature: Float = 0.7f,
    val topP: Float = 0.9f,
    val stream: Boolean = true
)

/** وحدة النص المتدفقة من المحرك. */
data class RuntimeGenerationChunk(
    val text: String,
    val tokenIndex: Int? = null,
    val isFinal: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

/** النتيجة النهائية لعملية التوليد. */
data class RuntimeGenerationResult(
    val fullText: String,
    val metrics: RuntimeMetrics,
    val error: RuntimeError? = null
)

/** مؤشرات أداء المحرك. */
data class RuntimeMetrics(
    val timeToFirstTokenMs: Long?,
    val totalGenerationTimeMs: Long? = null,
    val tokensGenerated: Int? = null,
    val tokensPerSecond: Double? = null,
    val peakMemoryMb: Long? = null
)

/** حالات محرك التشغيل خلال دورة حياته. */
sealed class RuntimeStatus {
    data object NotInitialized : RuntimeStatus()
    data object Initializing : RuntimeStatus()
    data object Ready : RuntimeStatus()
    data object Generating : RuntimeStatus()
    data class Failed(val error: RuntimeError) : RuntimeStatus()
}

/** تعريف أخطاء محركات التشغيل لتسهيل التشخيص. */
sealed class RuntimeError {
    data class ModelFileMissing(val path: String) : RuntimeError()
    data class UnsupportedFormat(val extension: String) : RuntimeError()
    data class NativeLibraryMissing(val libraryName: String) : RuntimeError()
    data class OutOfMemory(val requiredMb: Long?) : RuntimeError()
    data class GenerationFailed(val message: String) : RuntimeError()
    data class Unknown(val message: String) : RuntimeError()
}

/** دالة مساعدة للتحقق من نجاح عملية التوليد. */
fun RuntimeGenerationResult.isSuccess(): Boolean = error == null

/** دالة مساعدة لاسترداد سرعة التوليد أو القيمة الصفرية. */
fun RuntimeMetrics.tokensPerSecondOrZero(): Double = tokensPerSecond ?: 0.0

/** دالة مساعدة للتحقق مما إذا كان المحرك جاهزاً. */
fun RuntimeStatus.isReady(): Boolean = this is RuntimeStatus.Ready

/** دالة مساعدة للتحقق مما إذا كان المحرك قد فشل. */
fun RuntimeStatus.isFailed(): Boolean = this is RuntimeStatus.Failed
