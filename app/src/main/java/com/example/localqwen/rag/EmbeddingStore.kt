package com.example.localqwen.rag

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class EmbeddingStore(context: Context) {
    private val embeddingsRoot = File(context.filesDir, EMBEDDINGS_DIR_NAME).apply { mkdirs() }

    fun saveIndex(
        documentId: String,
        chunksWithVectors: List<IndexedChunk>,
        createdAt: Long = System.currentTimeMillis()
    ) {
        val file = indexFile(documentId)
        file.parentFile?.mkdirs()

        val payload = JSONObject()
            .put("documentId", documentId)
            .put("createdAt", createdAt)
            .put(
                "chunks",
                JSONArray().apply {
                    chunksWithVectors.forEach { chunk ->
                        put(
                            JSONObject()
                                .put("chunkIndex", chunk.chunkIndex)
                                .put("text", chunk.text)
                                .put(
                                    "vector",
                                    JSONArray().apply {
                                        chunk.vector.forEach { value -> put(value.toDouble()) }
                                    }
                                )
                        )
                    }
                }
            )

        file.writeText(payload.toString())
    }

    fun loadIndex(documentId: String): StoredEmbeddingIndex? {
        val file = indexFile(documentId)
        if (!file.exists() || !file.isFile || file.length() <= 0L) return null

        return runCatching {
            val json = JSONObject(file.readText())
            val chunksJson = json.optJSONArray("chunks") ?: JSONArray()
            val chunks = buildList {
                for (index in 0 until chunksJson.length()) {
                    val item = chunksJson.optJSONObject(index) ?: continue
                    val text = item.optString("text").trim()
                    val vectorJson = item.optJSONArray("vector") ?: continue
                    val vector = FloatArray(vectorJson.length())
                    for (vectorIndex in 0 until vectorJson.length()) {
                        vector[vectorIndex] = vectorJson.optDouble(vectorIndex).toFloat()
                    }
                    if (text.isNotBlank() && vector.isNotEmpty()) {
                        add(
                            IndexedChunk(
                                chunkIndex = item.optInt("chunkIndex", index),
                                text = text,
                                vector = vector
                            )
                        )
                    }
                }
            }

            StoredEmbeddingIndex(
                documentId = json.optString("documentId", documentId),
                createdAt = json.optLong("createdAt", file.lastModified()),
                chunks = chunks
            )
        }.getOrNull()
    }

    fun getIndexInfo(documentId: String): EmbeddingIndexInfo? {
        val file = indexFile(documentId)
        if (!file.exists() || !file.isFile || file.length() <= 0L) return null

        return runCatching {
            val json = JSONObject(file.readText())
            val chunksJson = json.optJSONArray("chunks") ?: JSONArray()
            EmbeddingIndexInfo(
                documentId = json.optString("documentId", documentId),
                chunkCount = chunksJson.length(),
                createdAt = json.optLong("createdAt", file.lastModified())
            )
        }.getOrNull()
    }

    fun hasIndex(documentId: String): Boolean {
        val file = indexFile(documentId)
        return file.exists() && file.isFile && file.length() > 0L
    }

    fun deleteIndex(documentId: String) {
        indexFile(documentId).delete()
        legacyIndexDirectory(documentId).deleteRecursively()
    }

    fun deleteAllIndexes(): Boolean {
        val deleted = !embeddingsRoot.exists() || embeddingsRoot.deleteRecursively()
        embeddingsRoot.mkdirs()
        return deleted && embeddingsRoot.exists()
    }

    fun countIndexes(): Int {
        return embeddingsRoot.listFiles()
            ?.count { it.isFile && it.extension.equals("json", ignoreCase = true) }
            ?: 0
    }

    private fun indexFile(documentId: String): File {
        return File(embeddingsRoot, "${safeDocumentId(documentId)}.json")
    }

    private fun legacyIndexDirectory(documentId: String): File {
        return File(embeddingsRoot, safeDocumentId(documentId))
    }

    private fun safeDocumentId(documentId: String): String {
        return documentId.ifBlank { "unknown" }
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
    }

    data class IndexedChunk(
        val chunkIndex: Int,
        val text: String,
        val vector: FloatArray
    )

    data class StoredEmbeddingIndex(
        val documentId: String,
        val createdAt: Long,
        val chunks: List<IndexedChunk>
    )

    data class EmbeddingIndexInfo(
        val documentId: String,
        val chunkCount: Int,
        val createdAt: Long
    )

    companion object {
        const val EMBEDDINGS_DIR_NAME = "embeddings"
    }
}
