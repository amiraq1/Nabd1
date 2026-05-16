package com.example.localqwen.rag

import com.example.localqwen.document.DocumentMessageFormatter
import com.example.localqwen.document.LocalDocument
import java.util.Locale

data class LastRagOperationResult(
    val query: String,
    val chunks: List<RetrievedChunk>,
    val rejectedCount: Int,
    val fullContextSentToModel: String
)

data class DocumentRetrievalOutcome(
    val chunks: List<RetrievedChunk> = emptyList(),
    val generationStatus: String
)

data class DocumentContextResult(
    val context: String?,
    val generationStatus: String
)

class RagManager(
    private val embeddingEngine: EmbeddingEngine,
    private val embeddingStore: EmbeddingStore,
    private val semanticRetriever: SemanticRetriever,
    private val getRagMode: () -> RagMode
) {
    var lastRagOperation: LastRagOperationResult? = null
        private set

    companion object {
        const val MAX_DOCUMENT_CONTEXT_CHARS = 5_000
        const val MAX_RETRIEVED_CHUNKS = 6
        const val DOCUMENT_CHUNK_SIZE = 1_200
        const val DOCUMENT_TEXT_LIMIT = 200_000
        
        const val DEFAULT_GENERATION_STATUS = "جاري التوليد..."
        const val KEYWORD_GENERATION_STATUS = "تم استخدام البحث النصي • جاري التوليد..."
        const val SEMANTIC_GENERATION_STATUS = "تم استخدام البحث الدلالي • جاري التوليد..."
        const val SEMANTIC_FALLBACK_GENERATION_STATUS = "لم يتم العثور على نتائج دلالية دقيقة، تم استخدام البحث النصي."
    }

    fun chunkText(text: String, max: Int = DOCUMENT_CHUNK_SIZE): List<String> {
        val normalized = text.trim()
        if (normalized.isEmpty()) return emptyList()

        val chunks = mutableListOf<String>()
        val current = StringBuilder()

        normalized.split(Regex("\\n\\s*\\n"))
            .filter { it.isNotBlank() }
            .forEach { paragraph ->
                if (paragraph.length > max) {
                    if (current.isNotEmpty()) {
                        chunks.add(current.toString().trim())
                        current.clear()
                    }
                    var start = 0
                    while (start < paragraph.length) {
                        val end = (start + max).coerceAtMost(paragraph.length)
                        chunks.add(paragraph.substring(start, end).trim())
                        start = end
                    }
                } else {
                    if (current.length + paragraph.length + 2 > max && current.isNotEmpty()) {
                        chunks.add(current.toString().trim())
                        current.clear()
                    }
                    if (current.isNotEmpty()) current.append("\n\n")
                    current.append(paragraph)
                }
            }

        if (current.isNotEmpty()) {
            chunks.add(current.toString().trim())
        }
        return chunks
    }

    private fun retrieveKeywordDocumentChunks(
        document: LocalDocument,
        query: String
    ): List<RetrievedChunk> {
        val words = query.lowercase()
            .split(Regex("\\s+"))
            .filter { it.length >= 2 }
            .toSet()

        val scored = chunkText(document.extractedText.take(DOCUMENT_TEXT_LIMIT))
            .mapIndexed { index, chunk ->
                val loweredChunk = chunk.lowercase()
                RetrievedChunk(
                    documentId = document.id,
                    documentTitle = document.title,
                    chunkIndex = index,
                    text = chunk,
                    score = words.sumOf { word -> if (loweredChunk.contains(word)) 1 else 0 }
                )
            }
            .sortedByDescending { it.score }

        if (scored.isEmpty()) return emptyList()
        if (words.isEmpty()) return scored.take(MAX_RETRIEVED_CHUNKS)

        return scored.filter { it.score > 0 }
            .take(MAX_RETRIEVED_CHUNKS)
            .ifEmpty { scored.take(MAX_RETRIEVED_CHUNKS) }
    }

    suspend fun retrieveDocumentChunks(document: LocalDocument?, query: String): DocumentRetrievalOutcome {
        val ragMode = getRagMode()
        if (document == null) return DocumentRetrievalOutcome(
            generationStatus = if (ragMode == RagMode.AUTO) {
                DEFAULT_GENERATION_STATUS
            } else {
                "اختر مستندًا من المكتبة أولًا."
            }
        )
        
        if (document.extractedText.isBlank()) {
            return DocumentRetrievalOutcome(
                generationStatus = DocumentMessageFormatter.insufficientDocumentAnswerMessage()
            )
        }
        
        val keywordResults = { retrieveKeywordDocumentChunks(document, query) }

        return when (ragMode) {
            RagMode.KEYWORD -> {
                DocumentRetrievalOutcome(
                    chunks = sanitizeRetrievedChunks(keywordResults()),
                    generationStatus = KEYWORD_GENERATION_STATUS
                )
            }
            RagMode.AUTO -> {
                val canUseSemantic = embeddingEngine.isReady() && embeddingStore.hasIndex(document.id)
                if (canUseSemantic) {
                    val semanticResults = sanitizeRetrievedChunks(
                        semanticRetriever.retrieveSemantic(document.id, query)
                            .map { it.copy(documentTitle = document.title) }
                    )
                    if (semanticResults.isNotEmpty()) {
                        DocumentRetrievalOutcome(
                            chunks = semanticResults,
                            generationStatus = SEMANTIC_GENERATION_STATUS
                        )
                    } else {
                        DocumentRetrievalOutcome(
                            chunks = sanitizeRetrievedChunks(keywordResults()),
                            generationStatus = KEYWORD_GENERATION_STATUS
                        )
                    }
                } else {
                    DocumentRetrievalOutcome(
                        chunks = sanitizeRetrievedChunks(keywordResults()),
                        generationStatus = KEYWORD_GENERATION_STATUS
                    )
                }
            }
            RagMode.SEMANTIC -> {
                if (!embeddingEngine.isReady() || !embeddingStore.hasIndex(document.id)) {
                    return DocumentRetrievalOutcome(
                        chunks = sanitizeRetrievedChunks(keywordResults()),
                        generationStatus = "البحث الدلالي غير جاهز، تم استخدام البحث النصي مؤقتًا."
                    )
                }

                val semanticResults = sanitizeRetrievedChunks(
                    semanticRetriever.retrieveSemantic(document.id, query)
                        .map { it.copy(documentTitle = document.title) }
                )
                if (semanticResults.isNotEmpty()) {
                    DocumentRetrievalOutcome(
                        chunks = semanticResults,
                        generationStatus = SEMANTIC_GENERATION_STATUS
                    )
                } else {
                    val failureReason = semanticRetriever.lastFailureReason()
                        ?: embeddingEngine.lastFailureReason()
                    DocumentRetrievalOutcome(
                        chunks = sanitizeRetrievedChunks(keywordResults()),
                        generationStatus = semanticFallbackGenerationStatus(failureReason)
                    )
                }
            }
        }
    }

    private fun sanitizeRetrievedChunks(chunks: List<RetrievedChunk>): List<RetrievedChunk> {
        return chunks
            .asSequence()
            .filter { it.text.isNotBlank() }
            .distinctBy { "${it.documentId}:${it.chunkIndex}:${it.text.trim()}" }
            .toList()
    }

    private fun semanticFallbackGenerationStatus(reason: String?): String {
        return if (reason.isNullOrBlank()) {
            SEMANTIC_FALLBACK_GENERATION_STATUS
        } else {
            DocumentMessageFormatter.semanticFallbackStatusWithReason(reason)
        }
    }

    suspend fun buildDocumentContext(document: LocalDocument?, query: String): DocumentContextResult {
        val outcome = retrieveDocumentChunks(document, query)
        if (outcome.chunks.isEmpty()) {
            lastRagOperation = LastRagOperationResult(
                query = query,
                chunks = emptyList(),
                rejectedCount = semanticRetriever.lastRejectedCount(),
                fullContextSentToModel = ""
            )
            return DocumentContextResult(
                context = null,
                generationStatus = outcome.generationStatus
            )
        }

        val includedBlocks = mutableListOf<String>()
        var totalLength = "استخدم المعلومات التالية من المستندات للإجابة:\n\n".length
        outcome.chunks.forEachIndexed { index, chunk ->
            val block = "المصدر [${index + 1}]: ${chunk.documentTitle}\nالنص: ${chunk.text.safeTruncate(DOCUMENT_CHUNK_SIZE)}"
            val separatorLength = if (includedBlocks.isEmpty()) 0 else 5
            if (totalLength + separatorLength + block.length <= MAX_DOCUMENT_CONTEXT_CHARS) {
                includedBlocks.add(block)
                totalLength += separatorLength + block.length
            }
        }
        val context = includedBlocks
            .takeIf { it.isNotEmpty() }
            ?.joinToString(
                separator = "\n---\n",
                prefix = "استخدم المعلومات التالية من المستندات للإجابة:\n\n"
            )

        lastRagOperation = LastRagOperationResult(
            query = query,
            chunks = outcome.chunks,
            rejectedCount = semanticRetriever.lastRejectedCount(),
            fullContextSentToModel = context ?: ""
        )

        return DocumentContextResult(
            context = context,
            generationStatus = if (context != null) outcome.generationStatus else DEFAULT_GENERATION_STATUS
        )
    }
}

// Helper extension function
private fun String.safeTruncate(max: Int): String {
    return if (this.length <= max) this else this.substring(0, max)
}
