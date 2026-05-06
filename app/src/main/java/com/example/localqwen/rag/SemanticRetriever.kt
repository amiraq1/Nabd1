package com.example.localqwen.rag

data class RetrievedChunk(
    val documentId: String,
    val documentTitle: String,
    val chunkIndex: Int,
    val text: String,
    val score: Int
)

class SemanticRetriever(
    private val embeddingModelManager: EmbeddingModelManager,
    private val embeddingStore: EmbeddingStore
) {
    fun retrieveSemantic(documentId: String?, query: String): List<RetrievedChunk> {
        if (documentId.isNullOrBlank()) return emptyList()
        if (query.isBlank()) return emptyList()
        if (!embeddingModelManager.isEmbeddingModelReady()) return emptyList()
        if (!embeddingStore.hasIndex(documentId)) return emptyList()

        // Phase 1 only prepares semantic infrastructure. Vector generation and search come later.
        return emptyList()
    }
}
