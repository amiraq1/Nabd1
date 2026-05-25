package com.example.localqwen.modelruntime

/**
 * يحدد نوع محرك التشغيل المطلوب للنموذج.
 */
enum class ModelRuntimeType {
    /** محرك LiteRT الرسمي من جوجل. */
    LITERT,
    /** محرك تشغيل ملفات GGUF (مثل llama.cpp). */
    GGUF,
    /** محرك تشغيل خاص بأجهزة Apple Silicon (مستقبلي). */
    MLX,
    /** نوع غير معروف أو غير مدعوم حالياً. */
    UNKNOWN
}

/**
 * يحدد حالة النموذج داخل نظام تجارب التشغيل.
 */
enum class ModelCandidateStatus {
    /** النموذج المستقر والافتراضي الحالي. */
    STABLE_DEFAULT,
    /** نموذج تجريبي قيد الاختبار والتقييم. */
    EXPERIMENTAL,
    /** نموذج تم رفضه لعدم استيفاء المعايير. */
    REJECTED,
    /** نموذج جديد بانتظار بدء عملية الاختبار. */
    NEEDS_TESTING
}

/**
 * يمثل نموذجاً مرشحاً للعمل داخل تطبيق نبض.
 */
data class ModelCandidate(
    val id: String,
    val displayName: String,
    val runtimeType: ModelRuntimeType,
    val fileExtension: String,
    val status: ModelCandidateStatus,
    val notes: String
)

/**
 * يمثل نتائج اختبارات الأداء (Benchmarks) للنموذج على الجهاز.
 */
data class ModelBenchmarkResult(
    val modelId: String,
    val timeToFirstTokenMs: Long?,
    val tokensPerSecond: Double?,
    val peakMemoryMb: Long?,
    val modelSizeMb: Long?,
    val isStableOnAndroid: Boolean,
    val notes: String
)

/**
 * يمثل نتائج اختبارات الجودة (Quality) والموثوقية للردود.
 */
data class ModelQualityResult(
    val modelId: String,
    val arabicQualityScore: Double,
    val safetyScore: Double,
    val hallucinationResistanceScore: Double,
    val programmingScore: Double,
    val verificationComplianceScore: Double,
    val averageScore: Double,
    val notes: String
)

/**
 * يمثل تجربة كاملة لتقييم نموذج معين واتخاذ قرار بشأنه.
 */
data class ModelRuntimeExperiment(
    val candidate: ModelCandidate,
    val benchmarkResult: ModelBenchmarkResult?,
    val qualityResult: ModelQualityResult?,
    val canBecomeDefault: Boolean
)

/**
 * يتحقق مما إذا كانت نتائج الجودة تستوفي الحد الأدنى للاعتماد (4.5/5).
 */
fun ModelQualityResult.meetsQualityThreshold(): Boolean {
    return averageScore >= 4.5 && 
           safetyScore >= 4.5 && 
           hallucinationResistanceScore >= 4.5 && 
           verificationComplianceScore >= 4.5
}

/**
 * يتحقق مما إذا كانت نتائج الأداء تضمن استقرار التطبيق وكفاءته.
 */
fun ModelBenchmarkResult.meetsStabilityThreshold(): Boolean {
    return isStableOnAndroid && 
           timeToFirstTokenMs != null && 
           tokensPerSecond != null && 
           peakMemoryMb != null
}

/**
 * يحدد ما إذا كان النموذج مؤهلاً ليصبح النموذج الافتراضي بناءً على نتائج التجربة.
 */
fun ModelRuntimeExperiment.isEligibleForDefault(): Boolean {
    val qResult = qualityResult ?: return false
    val bResult = benchmarkResult ?: return false
    
    return candidate.status != ModelCandidateStatus.REJECTED &&
           (candidate.status != ModelCandidateStatus.EXPERIMENTAL || canBecomeDefault) &&
           qResult.meetsQualityThreshold() &&
           bResult.meetsStabilityThreshold() &&
           canBecomeDefault
}

/**
 * قائمة المرشحين المبدئيين للنماذج داخل نبض.
 */
object DefaultModelCandidates {
    val Qwen = ModelCandidate(
        id = "qwen-default",
        displayName = "Qwen Default",
        runtimeType = ModelRuntimeType.LITERT,
        fileExtension = ".litertlm",
        status = ModelCandidateStatus.STABLE_DEFAULT,
        notes = "Stable default model currently used by Nabd."
    )

    val MiniCPM = ModelCandidate(
        id = "minicpm-experimental",
        displayName = "MiniCPM Experimental",
        runtimeType = ModelRuntimeType.UNKNOWN,
        fileExtension = ".gguf",
        status = ModelCandidateStatus.EXPERIMENTAL,
        notes = "Experimental lightweight model candidate. Not a default replacement."
    )
}
