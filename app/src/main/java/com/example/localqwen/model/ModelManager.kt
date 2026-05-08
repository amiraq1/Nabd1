package com.example.localqwen.model

import android.content.Context
import java.io.File

class ModelManager(private val context: Context) {
    data class SupportedModel(
        val id: String,
        val displayName: String,
        val fileName: String,
        val description: String
    )

    companion object {
        val VISION_MODEL = SupportedModel(
            id = "fastvlm_0_5b",
            displayName = "FastVLM 0.5B",
            fileName = "fastvlm.litertlm",
            description = "نموذج رؤية اختياري لتحليل الصور"
        )

        val SUPPORTED_MODELS = listOf(
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

    fun getModelFile(modelId: String): File? {
        val model = getModelById(modelId) ?: return null
        return modelFile(model)
    }

    fun modelPath(model: SupportedModel): String = modelFile(model).absolutePath

    fun isModelReady(model: SupportedModel): Boolean {
        val file = modelFile(model)
        return file.exists() && file.length() > 10_000_000
    }

    fun isModelImported(modelId: String): Boolean {
        val file = getModelFile(modelId) ?: return false
        return file.exists() && file.isFile && file.length() > 0L
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
}
