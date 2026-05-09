package com.example.localqwen.rag

import kotlin.math.roundToInt
import kotlin.math.sqrt

data class RetrievedChunk(
    val documentId: String,
    val documentTitle: String,
    val chunkIndex: Int,
    val text: String,
    val score: Int,
    val similarity: Float = 0f // للتشخيص
)

class SemanticRetriever(
    private val embeddingEngine: EmbeddingEngine,
    private val embeddingStore: EmbeddingStore
) {
    @Volatile
    private var lastFailureReason: String? = null

    @Volatile
    private var lastRejectedCount: Int = 0

    fun retrieveSemantic(documentId: String?, query: String): List<RetrievedChunk> {
        lastFailureReason = null
        lastRejectedCount = 0

        if (documentId.isNullOrBlank()) return emptyList()
        if (query.isBlank()) return emptyList()
        if (!embeddingEngine.isReady()) return emptyList()

        val index = embeddingStore.loadIndex(documentId)
        if (index == null || index.chunks.isEmpty()) return emptyList()

        return try {
            val queryVector = embeddingEngine.embed(query)
            if (queryVector.isEmpty()) return emptyList()

            val scoredChunks = index.chunks.mapNotNull { chunk ->
                val similarity = cosineSimilarity(queryVector, chunk.vector) ?: return@mapNotNull null
                if (!similarity.isFinite()) return@mapNotNull null
                ScoredChunk(chunk, similarity)
            }.sortedByDescending { it.similarity }

            if (scoredChunks.isEmpty()) {
                emptyList()
            } else {
                val accepted = scoredChunks.filter { it.similarity >= DEFAULT_RAG_SIMILARITY_THRESHOLD }
                lastRejectedCount = scoredChunks.size - accepted.size
                
                accepted
                    .take(DEFAULT_RAG_TOP_K)
                    .map { scored ->
                        RetrievedChunk(
                            documentId = index.documentId,
                            documentTitle = "",
                            chunkIndex = scored.chunk.chunkIndex,
                            text = scored.chunk.text,
                            score = (scored.similarity * SCORE_SCALE).roundToInt(),
                            similarity = scored.similarity
                        )
                    }
            }
        } catch (error: EmbeddingEngine.UnsupportedEmbeddingModelException) {
            lastFailureReason = error.message
            emptyList()
        } catch (_: Exception) {
            lastFailureReason = "تعذر استخدام البحث الدلالي الآن."
            emptyList()
        }
    }

    fun lastFailureReason(): String? = lastFailureReason

    fun lastRejectedCount(): Int = lastRejectedCount

    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float? {
        if (a.size != b.size || a.isEmpty()) {
            lastFailureReason = "أبعاد الفهرس الدلالي لا تطابق النموذج الحالي."
            return null
        }

        var dot = 0.0
        var normA = 0.0
        var normB = 0.0

        for (index in a.indices) {
            val aValue = a[index].toDouble()
            val bValue = b[index].toDouble()
            dot += aValue * bValue
            normA += aValue * aValue
            normB += bValue * bValue
        }

        if (normA <= 0.0 || normB <= 0.0) return null

        return (dot / (sqrt(normA) * sqrt(normB))).toFloat()
    }

    private data class ScoredChunk(
        val chunk: EmbeddingStore.IndexedChunk,
        val similarity: Float
    )

    companion object {
        // عدد المقاطع المسترجعة (K) - 6 مقاطع توفر سياقاً جيداً لمعظم النماذج
        const val DEFAULT_RAG_TOP_K = 6
        
        // الحد الأدنى للتشابه - 0.35 تضمن جودة المقتطفات مع استبعاد المحتوى غير ذي الصلة
        const val DEFAULT_RAG_SIMILARITY_THRESHOLD = 0.35f
        
        private const val SCORE_SCALE = 1000f
    }
}
