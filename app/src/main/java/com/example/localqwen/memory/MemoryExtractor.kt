package com.example.localqwen.memory

object MemoryExtractor {
    private val triggers = listOf(
        "تذكر أن",
        "احفظ أن",
        "خلّي بذاكرتك أن",
        "لا تنسى أن"
    )

    fun extractMemoryCommand(userInput: String): String? {
        val normalizedInput = userInput.trim()
        if (normalizedInput.isEmpty()) return null

        val trigger = triggers.firstOrNull { normalizedInput.contains(it) } ?: return null
        val startIndex = normalizedInput.indexOf(trigger) + trigger.length
        if (startIndex <= 0 || startIndex > normalizedInput.length) return null

        val candidate = normalizedInput.substring(startIndex)
            .trim()
            .trimStart('،', ',', ':', '-', ' ')
            .trim()

        if (candidate.isEmpty()) return null
        return normalizeMemoryText(candidate)
    }

    private fun normalizeMemoryText(text: String): String {
        val cleaned = text.replace(Regex("\\s+"), " ").trim()

        return when {
            cleaned.startsWith("اسمي ") -> "اسم المستخدم ${cleaned.removePrefix("اسمي ").trim()}"
            cleaned.startsWith("أنا اسمي ") -> "اسم المستخدم ${cleaned.removePrefix("أنا اسمي ").trim()}"
            cleaned.startsWith("انا اسمي ") -> "اسم المستخدم ${cleaned.removePrefix("انا اسمي ").trim()}"
            cleaned.startsWith("عمري ") -> "عمر المستخدم ${cleaned.removePrefix("عمري ").trim()}"
            else -> cleaned
        }
    }
}
