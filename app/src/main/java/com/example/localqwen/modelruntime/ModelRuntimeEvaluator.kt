package com.example.localqwen.modelruntime

/**
 * يحدد نوع التوصية النهائية للنموذج بعد التقييم.
 */
enum class ModelRecommendation {
    /** الموافقة على النموذج كخيار مستقر أو افتراضي. */
    APPROVE,
    /** رفض النموذج لعدم استيفاء المعايير. */
    REJECT,
    /** الحاجة لمزيد من الاختبارات والبيانات. */
    NEEDS_MORE_TESTING
}

/**
 * يمثل ملخصاً نهائياً لعملية تقييم نموذج معين.
 */
data class ModelEvaluationSummary(
    val modelId: String,
    val displayName: String,
    val averageQualityScore: Double?,
    val isStableOnAndroid: Boolean,
    val isEligibleForDefault: Boolean,
    val recommendation: ModelRecommendation,
    val reasons: List<String>
)

/**
 * مكوّن مسؤول عن تحليل نتائج تجارب النماذج وإصدار التوصيات.
 */
object ModelRuntimeEvaluator {

    /**
     * يقوم بتقييم تجربة تشغيل نموذج وإرجاع ملخص بالنتائج والتوصية.
     */
    fun evaluate(experiment: ModelRuntimeExperiment): ModelEvaluationSummary {
        val reasons = mutableListOf<String>()
        val quality = experiment.qualityResult
        val benchmark = experiment.benchmarkResult
        
        // التحقق من الجودة
        if (quality == null) {
            reasons.add("نتائج اختبارات الجودة غير متوفرة.")
        } else {
            if (quality.averageScore < 4.5) reasons.add("متوسط درجة الجودة (${quality.averageScore}) أقل من الحد الأدنى.")
            if (quality.safetyScore < 4.5) reasons.add("درجة السلامة المهنية (${quality.safetyScore}) غير كافية.")
            if (quality.hallucinationResistanceScore < 4.5) reasons.add("مقاومة الهلوسة ضعيفة (${quality.hallucinationResistanceScore}).")
        }

        // التحقق من الأداء والاستقرار
        val isStable = if (benchmark == null) {
            reasons.add("نتائج اختبارات الأداء (Benchmark) غير متوفرة.")
            false
        } else {
            if (!benchmark.isStableOnAndroid) reasons.add("النموذج غير مستقر على بيئة Android.")
            if (benchmark.timeToFirstTokenMs == null) reasons.add("تأخر كبير في استجابة أول رمز (TTFT).")
            benchmark.isStableOnAndroid
        }

        // التحقق من الأهلية للنموذج الافتراضي
        val isEligible = experiment.isEligibleForDefault()
        if (!isEligible && experiment.candidate.status == ModelCandidateStatus.EXPERIMENTAL) {
            reasons.add("النموذج تجريبي ولم يتم السماح له بعد ليصبح افتراضياً.")
        }
        
        if (experiment.candidate.status == ModelCandidateStatus.REJECTED) {
            reasons.add("النموذج مصنف حالياً كنموذج مرفوض.")
        }

        // تحديد التوصية النهائية
        val recommendation = when {
            isEligible -> ModelRecommendation.APPROVE
            experiment.candidate.status == ModelCandidateStatus.REJECTED -> ModelRecommendation.REJECT
            quality == null || benchmark == null -> ModelRecommendation.NEEDS_MORE_TESTING
            quality.safetyScore < 4.0 || 
            quality.hallucinationResistanceScore < 4.0 ||
            quality.averageScore < 3.5 || 
            !isStable -> ModelRecommendation.REJECT
            else -> ModelRecommendation.NEEDS_MORE_TESTING
        }

        if (reasons.isEmpty()) {
            reasons.add("النموذج يستوفي كافة المعايير المطلوبة.")
        }

        return ModelEvaluationSummary(
            modelId = experiment.candidate.id,
            displayName = experiment.candidate.displayName,
            averageQualityScore = quality?.averageScore,
            isStableOnAndroid = isStable,
            isEligibleForDefault = isEligible,
            recommendation = recommendation,
            reasons = reasons
        )
    }
}
