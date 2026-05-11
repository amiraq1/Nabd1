package com.example.localqwen.memory

object MemoryPromptBuilder {
    fun buildMemoryContext(memories: List<MemoryItem>): String {
        if (memories.isEmpty()) return ""

        val lines = memories.map { memory -> "- ${memory.text}" }
        return buildString {
            appendLine("معلومات محفوظة عن المستخدم:")
            lines.forEach { appendLine(it) }
            append("استخدم هذه المعلومات إذا كانت مفيدة، ولا تذكر أنك تستخدم الذاكرة إلا إذا سُئلت.")
        }
    }
}
