package com.example.localqwen.runtime

import com.example.localqwen.modelruntime.ModelRuntimeType

/**
 * محول (Adapter) تمهيدي لمحرك Qwen الحالي.
 * 
 * ملاحظة هامة: هذا الكلاس عبارة عن Stub (هيكل شكلي) لتجهيز البنية التحتية متعددة المحركات.
 * لا يستخدم حالياً في تدفق المحادثة الفعلي ولا يرتبط بمحرك الاستدلال النشط.
 */
class QwenRuntimeAdapter : ModelRuntimeInterface {

    override val runtimeId: String = "qwen-default"
    override val displayName: String = "Qwen Default Runtime"
    override val runtimeType: ModelRuntimeType = ModelRuntimeType.LITERT

    override suspend fun initialize(modelPath: String): RuntimeStatus {
        return if (modelPath.isNotBlank()) {
            RuntimeStatus.Ready
        } else {
            RuntimeStatus.Failed(RuntimeError.ModelFileMissing(modelPath))
        }
    }

    override suspend fun generate(
        request: RuntimeGenerationRequest,
        onChunk: suspend (RuntimeGenerationChunk) -> Unit
    ): RuntimeGenerationResult {
        // هذا المحول غير متصل بمحرك التوليد الحقيقي بعد
        return RuntimeGenerationResult(
            fullText = "",
            metrics = RuntimeMetrics(timeToFirstTokenMs = null),
            error = RuntimeError.GenerationFailed("QwenRuntimeAdapter is a stub and is not connected to the active runtime yet.")
        )
    }

    override suspend fun stopGeneration() {
        // No-op في هذه المرحلة
    }

    override suspend fun release() {
        // No-op في هذه المرحلة
    }

    override fun isAvailable(): Boolean {
        return true
    }
}
