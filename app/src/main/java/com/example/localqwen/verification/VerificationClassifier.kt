package com.example.localqwen.verification

/**
 * مصنف ذكي (قائم على القواعد) لتحليل نية المستخدم وتحديد مستوى التحقق المطلوب.
 */
object VerificationClassifier {

    private val level2Keywords = listOf(
        // الأخبار والوقت الحالي
        "أخبار", "آخر خبر", "اليوم", "الآن", "حالي", "الجديد", "today", "current", "latest", "now", "news",
        // الأسعار والعملات
        "سعر", "كم سعر", "الدولار", "الذهب", "النفط", "الأسهم", "price", "currency", "dollar", "gold", "stock",
        // المسؤولين
        "الرئيس", "رئيس الوزراء", "المدير", "CEO", "president", "prime minister",
        // الصحة
        "علاج", "دواء", "تشخيص", "أعراض", "مرض", "سكري", "ضغط", "medical", "doctor", "medicine", "health",
        // القانون
        "قانون", "محكمة", "دعوى", "عقوبة", "سجن", "محامي", "legal", "law", "court",
        // المال والاستثمار
        "استثمر", "أستثمر", "سهم", "تداول", "ربح", "أموالي", "تسلا", "invest", "trading", "profit",
        // أحداث مستقبلية أو نتائج حديثة
        "نتيجة مباراة", "من فاز", "ترتيب الدوري", "2030"
    )

    private val level1Keywords = listOf(
        "بطولة", "بطولات", "تاريخ", "عام", "سنة", "موسم", "نتائج قديمة",
        "ريال مدريد", "برشلونة", "كأس العالم", "الدوري", "دوري أبطال", 
        "champion", "season", "history", "cup", "league", "world cup"
    )

    /**
     * يقوم بتصنيف رسالة المستخدم وإرجاع قرار التحقق المناسب.
     */
    fun classify(userMessage: String): VerificationDecision {
        val lowerMessage = userMessage.lowercase()

        // التحقق من المستوى الثاني (الأكثر حساسية أو حداثة)
        if (containsAny(lowerMessage, level2Keywords)) {
            val isProfessional = containsAny(lowerMessage, listOf("علاج", "دواء", "قانون", "استثمر", "أستثمر", "medical", "legal", "invest"))
            
            return VerificationDecision(
                level = VerificationLevel.LEVEL_2_RECENT_OR_SENSITIVE,
                sourceRequirement = if (isProfessional) SourceRequirement.PROFESSIONAL_ADVICE else SourceRequirement.RECENT_SOURCE,
                answerState = if (isProfessional) VerifiedAnswerState.SAFETY_RESTRICTED else VerifiedAnswerState.NEEDS_RECENT_CHECK,
                shouldAnswerDirectly = false,
                shouldAddCaution = true,
                shouldAskForSource = true,
                reason = "Question asks about recent, changing, or sensitive professional information."
            )
        }

        // التحقق من المستوى الأول (سياقي/تاريخي)
        if (containsAny(lowerMessage, level1Keywords) || containsYear(lowerMessage)) {
            return VerificationDecision(
                level = VerificationLevel.LEVEL_1_CONTEXTUAL_CAUTION,
                sourceRequirement = SourceRequirement.STATIC_KNOWLEDGE,
                answerState = VerifiedAnswerState.PARTIALLY_VERIFIED,
                shouldAnswerDirectly = true,
                shouldAddCaution = true,
                shouldAskForSource = false,
                reason = "Question involves sports, history, or specific dates and may require contextual caution."
            )
        }

        // الافتراضي: المستوى صفر
        return VerificationDecision(
            level = VerificationLevel.LEVEL_0_DIRECT,
            sourceRequirement = SourceRequirement.NONE,
            answerState = VerifiedAnswerState.VERIFIED,
            shouldAnswerDirectly = true,
            shouldAddCaution = false,
            shouldAskForSource = false,
            reason = "Question is general or creative and can be answered directly."
        )
    }

    private fun containsAny(text: String, keywords: List<String>): Boolean {
        return keywords.any { text.contains(it, ignoreCase = true) }
    }

    private fun containsYear(text: String): Boolean {
        // فحص بسيط لوجود سنة (4 أرقام متتالية)
        val regex = Regex("\\b(19|20)\\d{2}\\b")
        return regex.containsMatchIn(text)
    }
}
