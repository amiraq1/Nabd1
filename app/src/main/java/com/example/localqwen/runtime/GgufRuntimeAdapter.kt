package com.example.localqwen.runtime

import com.example.localqwen.modelruntime.ModelRuntimeType

/**
 * محول (Adapter) شكلي لمحرك GGUF التجريبي.
 * 
 * ملاحظة هامة: هذا الكلاس عبارة عن Stub (هيكل شكلي) وغير مفعل حالياً.
 * مخصص فقط لتجهيز البنية التحتية المستقبلية لـ GGUF داخل واجهة المطور.
 * لا يحتوي على أي ربط فعلي بمكتبات llama.cpp أو JNI.
 */
class GgufRuntimeAdapter : ModelRuntimeInterface {

    override val runtimeId: String = "gguf-experimental"
    override val displayName: String = "GGUF Experimental Runtime"
    override val runtimeType: ModelRuntimeType = ModelRuntimeType.GGUF

    override suspend fun initialize(modelPath: String): RuntimeStatus {
        if (modelPath.isBlank()) {
            return RuntimeStatus.Failed(RuntimeError.ModelFileMissing(modelPath))
        }

        if (!modelPath.lowercase().endsWith(".gguf")) {
            val ext = modelPath.substringAfterLast(".", "")
            return RuntimeStatus.Failed(RuntimeError.UnsupportedFormat(ext))
        }

        // بما أن مكتبات Native غير موجودة بعد، نرجع خطأ فقدان المكتبة دائماً
        return RuntimeStatus.Failed(
            RuntimeError.NativeLibraryMissing("llama.cpp native backend is not bundled yet")
        )
    }

    override suspend fun generate(
        request: RuntimeGenerationRequest,
        onChunk: suspend (RuntimeGenerationChunk) -> Unit
    ): RuntimeGenerationResult {
        return RuntimeGenerationResult(
            fullText = "",
            metrics = RuntimeMetrics(timeToFirstTokenMs = null),
            error = RuntimeError.GenerationFailed("GGUF runtime is a stub and native backend is not implemented yet.")
        )
    }

    override suspend fun stopGeneration() {
        // No-op
    }

    override suspend fun release() {
        // No-op
    }

    override fun isAvailable(): Boolean {
        // المحرك غير متاح حالياً لعدم وجود ملفات الـ Native (.so)
        return false
    }
}
