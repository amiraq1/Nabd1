package com.example.localqwen.verification

/**
 * مكوّن مسؤول عن تحويل قرارات التحقق إلى تعليمات نصية (Instructions)
 * يتم دمجها في الـ System Prompt لتوجيه النموذج بشكل أدق.
 */
object VerificationPromptBuilder {

    /**
     * يبني التعليمات النصية المناسبة بناءً على قرار التحقق.
     */
    fun buildInstruction(decision: VerificationDecision): String {
        return when (decision.level) {
            VerificationLevel.LEVEL_0_DIRECT -> """
                تعليمات التحقق:
                - السؤال عام ولا يحتاج تحققاً خارجياً.
                - أجب مباشرة وبوضوح.
                - لا تضف تحذيرات غير ضرورية.
            """.trimIndent()

            VerificationLevel.LEVEL_1_CONTEXTUAL_CAUTION -> """
                تعليمات التحقق:
                - السؤال يحتوي معلومات تاريخية أو رياضية أو رقمية قد يحدث فيها التباس.
                - أجب بحذر ودقة.
                - فرّق بين السنة التقويمية والموسم أو السياق الزمني عند الحاجة.
                - لا تنسب بطولة أو نتيجة أو رقماً دون وضوح.
                - إذا وجدت احتمال التباس، اذكره بوضوح للمستخدم.
            """.trimIndent()

            VerificationLevel.LEVEL_2_RECENT_OR_SENSITIVE -> """
                تعليمات التحقق:
                - السؤال يتضمن معلومات حديثة أو حساسة أو قابلة للتغير.
                - لا تجزم بالمعلومة دون مصدر حديث أو تحقق موثوق.
                - إذا لم يتوفر مصدر، صرّح بوضوح بأن المعلومة تحتاج إلى تحقق حديث.
                - في الأسئلة الطبية أو القانونية أو المالية، قدم معلومات عامة فقط ولا تقدم قراراً حاسماً.
                - عند الحاجة، وجّه المستخدم إلى مختص (طبيب، محامي، مستشار مالي).
            """.trimIndent()
        }
    }

    /**
     * يرجع وسماً (Label) قصيراً مفيداً لسجلات المطورين أو للتصحيح.
     */
    fun buildShortLabel(decision: VerificationDecision): String {
        return when (decision.level) {
            VerificationLevel.LEVEL_0_DIRECT -> "Direct"
            VerificationLevel.LEVEL_1_CONTEXTUAL_CAUTION -> "Contextual caution"
            VerificationLevel.LEVEL_2_RECENT_OR_SENSITIVE -> "Recent or sensitive"
        }
    }

    /**
     * يحدد ما إذا كان يجب حقن هذه التعليمات في الـ Prompt الفعلي.
     * يفضل إرجاع false للمستوى 0 لتوفير مساحة السياق (Context Window).
     */
    fun shouldInjectInstruction(decision: VerificationDecision): Boolean {
        return decision.level != VerificationLevel.LEVEL_0_DIRECT
    }
}
