package com.example.localqwen.model

import android.content.Context
import java.io.File
import java.security.MessageDigest

class ModelManager(private val context: Context) {
    data class SupportedModel(
        val id: String,
        val displayName: String,
        val fileName: String,
        val description: String
    )

    companion object {
        const val MIN_MODEL_SIZE_BYTES = 50_000_000L // 50MB فحص تقريبي أولي لا يغني عن تحقق المحرك

        val VISION_MODEL = SupportedModel(
            id = "fastvlm_0_5b",
            displayName = "FastVLM 0.5B",
            fileName = "fastvlm.litertlm",
            description = "نموذج رؤية اختياري لتحليل الصور"
        )

        val SUPPORTED_MODELS = listOf(
            SupportedModel(
                id = "gemma_3n_e2b_it",
                displayName = "Gemma 3 Multimodal",
                fileName = "gemma-3n-E2B-it-int4.litertlm",
                description = "نموذج رؤية ومحادثة متقدم"
            ),
            SupportedModel(
                id = "gemma_e2b",
                displayName = "Gemma E2B — سريع ومتوازن",
                fileName = "gemma-e2b.litertlm",
                description = "مناسب للمحادثة اليومية والردود السريعة"
            ),
            SupportedModel(
                id = "gemma_e4b",
                displayName = "Gemma E4B — للمهام الثقيلة",
                fileName = "gemma-e4b.litertlm",
                description = "مناسب للأسئلة المعقدة، التحليل، والمهام الثقيلة"
            )
        )
    }

    private val legacyModelFile: File
        get() = File(context.filesDir, "models/qwen3-0.6b/model.litertlm")

    private fun modelDir(model: SupportedModel): File {
        return File(context.filesDir, "models/${model.id}").apply {
            if (!exists()) mkdirs()
        }
    }

    fun getModelById(modelId: String): SupportedModel? {
        if (modelId == VISION_MODEL.id) return VISION_MODEL
        return SUPPORTED_MODELS.firstOrNull { it.id == modelId }
    }

    fun modelFile(model: SupportedModel): File {
        val targetFile = File(modelDir(model), "model.litertlm")
        if (model.id == "gemma_e2b" && !targetFile.exists() && legacyModelFile.exists()) {
            legacyModelFile.copyTo(targetFile, overwrite = false)
        }
        return targetFile
    }

    fun tempModelFile(model: SupportedModel): File {
        return File(modelDir(model), "model.litertlm.tmp")
    }

    fun cleanTempFiles() {
        try {
            val modelsDir = File(context.filesDir, "models")
            if (modelsDir.exists()) {
                modelsDir.walkTopDown().forEach { file ->
                    if (file.isFile && file.name.endsWith(".tmp")) {
                        file.delete()
                    }
                }
            }
        } catch (_: Exception) { }
    }

    fun getModelFile(modelId: String): File? {
        val model = getModelById(modelId) ?: return null
        return modelFile(model)
    }

    fun modelPath(model: SupportedModel): String = modelFile(model).absolutePath

    fun isModelFileExtensionValid(fileName: String?): Boolean {
        return fileName?.endsWith(".litertlm", ignoreCase = true) == true
    }

    fun isModelReady(model: SupportedModel): Boolean {
        val file = modelFile(model)
        // التحقق من وجود الملف النهائي فقط وبحجم معقول
        return file.exists() && file.isFile && file.length() >= MIN_MODEL_SIZE_BYTES
    }

    fun isModelImported(modelId: String): Boolean {
        val file = getModelFile(modelId) ?: return false
        return file.exists() && file.isFile && file.length() >= 10_000_000
    }

    fun modelSizeBytes(modelId: String): Long {
        val file = getModelFile(modelId) ?: return 0L
        return if (file.exists() && file.isFile) file.length() else 0L
    }

    fun deleteModel(model: SupportedModel): Boolean {
        return modelDir(model).deleteRecursively()
    }

    fun deleteModel(modelId: String): Boolean {
        val model = getModelById(modelId) ?: return false
        return deleteModel(model)
    }

    /**
     * Calculates the SHA-256 fingerprint of an imported model file.
     * Used to verify model integrity and prevent tampering.
     */
    fun verifyModelIntegrity(file: File): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            "error_calculating_hash"
        }
    }
}
