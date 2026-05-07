package com.example.localqwen.rag

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.IOException

class EmbeddingModelManager(private val context: Context) {

    fun isEmbeddingModelReady(): Boolean {
        val file = embeddingModelFile()
        return file.exists() && file.isFile && file.length() > 0L
    }

    fun embeddingModelPath(): String {
        return embeddingModelFile().absolutePath
    }

    fun modelSizeBytes(): Long {
        val file = embeddingModelFile()
        return if (file.exists() && file.isFile) file.length() else 0L
    }

    fun modelLastModified(): Long {
        val file = embeddingModelFile()
        return if (file.exists() && file.isFile) file.lastModified() else 0L
    }

    @Throws(IOException::class)
    fun importEmbeddingModel(uri: Uri) {
        val target = embeddingModelFile()
        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, "${target.name}.tmp")

        context.contentResolver.openInputStream(uri)?.use { input ->
            temp.outputStream().buffered().use { output ->
                input.copyTo(output, DEFAULT_COPY_BUFFER_SIZE)
            }
        } ?: throw IOException("Unable to open embedding model stream")

        if (!temp.renameTo(target)) {
            temp.copyTo(target, overwrite = true)
            temp.delete()
        }
    }

    fun deleteEmbeddingModel(): Boolean {
        val target = embeddingModelFile()
        val temp = File(target.parentFile, "${target.name}.tmp")
        if (temp.exists()) {
            temp.delete()
        }
        return !target.exists() || target.delete()
    }

    private fun embeddingModelFile(): File {
        return File(context.filesDir, EMBEDDING_MODEL_RELATIVE_PATH)
    }

    companion object {
        const val EMBEDDING_MODEL_RELATIVE_PATH = "models/embedder/model.tflite"
        private const val DEFAULT_COPY_BUFFER_SIZE = 8_192
    }
}
