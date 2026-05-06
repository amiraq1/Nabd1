package com.example.localqwen.rag

object TextChunker {
    fun chunkText(
        text: String,
        maxChars: Int = 800,
        overlapChars: Int = 120
    ): List<String> {
        val normalized = text
            .replace(Regex("[\\t\\x0B\\f\\r ]+"), " ")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
        if (normalized.isEmpty()) return emptyList()

        val safeMaxChars = maxChars.coerceAtLeast(200)
        val safeOverlap = overlapChars.coerceIn(0, safeMaxChars / 2)
        val chunks = mutableListOf<String>()
        var start = 0

        while (start < normalized.length && chunks.size < MAX_CHUNKS) {
            val idealEnd = (start + safeMaxChars).coerceAtMost(normalized.length)
            var end = idealEnd

            if (idealEnd < normalized.length) {
                val whitespaceBreak = normalized.lastIndexOfAny(
                    charArrayOf(' ', '\n'),
                    startIndex = idealEnd - 1
                )
                if (whitespaceBreak > start + safeMaxChars / 2) {
                    end = whitespaceBreak
                }
            }

            val chunk = normalized.substring(start, end).trim()
            if (chunk.isNotEmpty()) {
                chunks.add(chunk)
            }

            if (end >= normalized.length) break

            start = (end - safeOverlap).coerceAtLeast(start + 1)
            while (start < normalized.length && normalized[start].isWhitespace()) {
                start++
            }
        }

        return chunks
    }

    private const val MAX_CHUNKS = 256
}
