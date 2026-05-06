package com.example.localqwen.rag

import android.content.Context
import java.io.File

class EmbeddingStore(context: Context) {
    private val embeddingsRoot = File(context.filesDir, EMBEDDINGS_DIR_NAME).apply { mkdirs() }

    fun hasIndex(documentId: String): Boolean {
        val dir = indexDirectory(documentId)
        return dir.exists() && ((dir.isDirectory && dir.list()?.isNotEmpty() == true) || dir.isFile)
    }

    fun deleteIndex(documentId: String) {
        indexDirectory(documentId).deleteRecursively()
    }

    private fun indexDirectory(documentId: String): File {
        val safeName = documentId.ifBlank { "unknown" }
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
        return File(embeddingsRoot, safeName)
    }

    companion object {
        const val EMBEDDINGS_DIR_NAME = "embeddings"
    }
}
