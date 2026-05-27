package com.example.localqwen.verification

/**
 * يحدد مستوى التحقق المطلوب بناءً على تصنيف نوع سؤال المستخدم.
 */
enum class VerificationLevel {
    /** إجابة مباشرة بدون قيود سياقية خاصة. */
    LEVEL_0_DIRECT,
    /** تتطلب حذراً سياقياً وتوضيح احتمالات الالتباس (مثل التواريخ والبطولات القديمة). */
    LEVEL_1_CONTEXTUAL_CAUTION,
    /** تتطلب تحققاً حديثاً أو حذراً شديداً (مثل الأخبار، الأسعار، المواضيع المهنية الحساسة). */
    LEVEL_2_RECENT_OR_SENSITIVE
}

/**
 * يحدد نوع المصدر أو الإدخال المطلوب لدعم الإجابة بشكل موثوق.
 */
enum class SourceRequirement {
    /** لا يتطلب مصادر خارجية. */
    NONE,
    /** يكفي الاعتماد على المعرفة الثابتة للنموذج. */
    STATIC_KNOWLEDGE,
    /** يتطلب مصدراً إخبارياً أو تحديثاً من الإنترنت. */
    RECENT_SOURCE,
    /** يعتمد على الوثائق التي قدمها المستخدم (نظام RAG). */
    USER_PROVIDED_SOURCE,
    /** يتطلب استشارة مهنية من مختص (طبيب، محامي، إلخ). */
    PROFESSIONAL_ADVICE
}

/**
 * يمثل الحالة النهائية لدرجة التحقق من الإجابة قبل عرضها للمستخدم.
 */
enum class VerifiedAnswerState {
    /** معلومة مؤكدة وموثقة تماماً. */
    VERIFIED,
    /** مؤكدة جزئياً أو تحتمل هوامش خطأ بسيطة. */
    PARTIALLY_VERIFIED,
    /** غير مؤكدة؛ يتم عرضها مع تنبيه صريح. */
    UNVERIFIED,
    /** تتطلب فحصاً حديثاً لم يتم إجراؤه بعد. */
    NEEDS_RECENT_CHECK,
    /** مقيدة أو محجوبة لأسباب تتعلق بسلامة المحتوى. */
    SAFETY_RESTRICTED
}

/**
 * يمثل القرار النهائي الذي يتخذه محرك التحقق حول كيفية معالجة رد المساعد.
 */
data class VerificationDecision(
    val level: VerificationLevel,
    val sourceRequirement: SourceRequirement,
    val answerState: VerifiedAnswerState,
    val shouldAnswerDirectly: Boolean,
    val shouldAddCaution: Boolean,
    val shouldAskForSource: Boolean,
    val reason: String
)

/**
 * دالة مساعدة لتحديد ما إذا كان مستوى التحقق يتطلب إضافة تنبيهات حذر للمستخدم.
 */
fun VerificationLevel.requiresCaution(): Boolean = when (this) {
    VerificationLevel.LEVEL_0_DIRECT -> false
    VerificationLevel.LEVEL_1_CONTEXTUAL_CAUTION -> true
    VerificationLevel.LEVEL_2_RECENT_OR_SENSITIVE -> true
}

/**
 * دالة مساعدة لتحديد ما إذا كان متطلب المصدر يستدعي إدخالاً خارجياً (إنترنت أو مستندات).
 */
fun SourceRequirement.requiresExternalInput(): Boolean = when (this) {
    SourceRequirement.NONE, SourceRequirement.STATIC_KNOWLEDGE -> false
    SourceRequirement.RECENT_SOURCE, 
    SourceRequirement.USER_PROVIDED_SOURCE, 
    SourceRequirement.PROFESSIONAL_ADVICE -> true
}
