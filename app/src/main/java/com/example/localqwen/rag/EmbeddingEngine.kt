package com.example.localqwen.rag

import android.content.Context
import com.google.mediapipe.tasks.text.textembedder.TextEmbedder
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.Locale

class EmbeddingEngine(
    context: Context,
    private val embeddingModelManager: EmbeddingModelManager,
    private val backendSelector: () -> EmbeddingBackend
) {
    private val appContext = context.applicationContext
    private val lock = Any()

    @Volatile
    private var mediaPipeEngine: MediaPipeEmbeddingBackend? = null

    @Volatile
    private var tfliteEngine: TfliteEmbeddingBackend? = null

    @Volatile
    private var lastFailureReason: String? = null

    @Volatile
    private var lastBackendUsed: EmbeddingBackend? = null

    fun isReady(): Boolean = embeddingModelManager.isEmbeddingModelReady()

    fun lastFailureReason(): String? = lastFailureReason

    fun lastBackendUsed(): EmbeddingBackend? = lastBackendUsed

    @Throws(UnsupportedEmbeddingModelException::class)
    fun embed(text: String): FloatArray {
        val normalized = text.trim()
        if (normalized.isBlank()) {
            throw UnsupportedEmbeddingModelException(MODEL_INCOMPATIBLE_MESSAGE)
        }
        if (!embeddingModelManager.isEmbeddingModelReady()) {
            throw UnsupportedEmbeddingModelException(MODEL_NOT_IMPORTED_MESSAGE)
        }

        lastFailureReason = null
        lastBackendUsed = null

        val candidates = when (backendSelector()) {
            EmbeddingBackend.MEDIAPIPE -> listOf(EmbeddingBackend.MEDIAPIPE)
            EmbeddingBackend.TFLITE -> listOf(EmbeddingBackend.TFLITE)
            EmbeddingBackend.AUTO -> listOf(EmbeddingBackend.MEDIAPIPE, EmbeddingBackend.TFLITE)
        }

        val errors = mutableListOf<String>()
        candidates.forEach { backend ->
            try {
                val vector = backendFor(backend).embed(normalized)
                if (vector.isNotEmpty()) {
                    lastBackendUsed = backend
                    return vector
                }
            } catch (error: UnsupportedEmbeddingModelException) {
                errors += error.message.orEmpty()
            } catch (_: Exception) {
                errors += when (backend) {
                    EmbeddingBackend.MEDIAPIPE -> MEDIAPIPE_INCOMPATIBLE_MESSAGE
                    EmbeddingBackend.TFLITE -> MODEL_INCOMPATIBLE_MESSAGE
                    EmbeddingBackend.AUTO -> MODEL_INCOMPATIBLE_MESSAGE
                }
            }
        }

        lastFailureReason = errors.firstOrNull { it.isNotBlank() } ?: MODEL_INCOMPATIBLE_MESSAGE
        throw UnsupportedEmbeddingModelException(lastFailureReason ?: MODEL_INCOMPATIBLE_MESSAGE)
    }

    fun close() {
        synchronized(lock) {
            mediaPipeEngine?.close()
            mediaPipeEngine = null
            tfliteEngine?.close()
            tfliteEngine = null
            lastFailureReason = null
            lastBackendUsed = null
        }
    }

    private fun backendFor(backend: EmbeddingBackend): BackendEngine {
        synchronized(lock) {
            return when (backend) {
                EmbeddingBackend.MEDIAPIPE -> {
                    mediaPipeEngine ?: MediaPipeEmbeddingBackend(
                        context = appContext,
                        embeddingModelManager = embeddingModelManager
                    ).also { mediaPipeEngine = it }
                }
                EmbeddingBackend.TFLITE -> {
                    tfliteEngine ?: TfliteEmbeddingBackend(
                        embeddingModelManager = embeddingModelManager
                    ).also { tfliteEngine = it }
                }
                EmbeddingBackend.AUTO -> error("AUTO is resolved before backend lookup")
            }
        }
    }

    class UnsupportedEmbeddingModelException(message: String) : IllegalStateException(message)

    companion object {
        const val MODEL_NOT_IMPORTED_MESSAGE = "استورد نموذج التضمين أولًا."
        const val MODEL_INCOMPATIBLE_MESSAGE = "نموذج التضمين غير متوافق مع هذا الإصدار."
        const val TOKENIZER_UNSUPPORTED_MESSAGE = "نموذج التضمين يحتاج tokenizer غير مدعوم حاليًا."
        const val MEDIAPIPE_INCOMPATIBLE_MESSAGE = "نموذج التضمين غير متوافق مع MediaPipe."
    }
}

private interface BackendEngine {
    fun embed(text: String): FloatArray
    fun close()
}

private class MediaPipeEmbeddingBackend(
    private val context: Context,
    private val embeddingModelManager: EmbeddingModelManager
) : BackendEngine {
    @Volatile
    private var textEmbedder: TextEmbedder? = null

    @Volatile
    private var loadedModelSignature: String? = null

    private val lock = Any()

    override fun embed(text: String): FloatArray {
        val result = synchronized(lock) {
            ensureTextEmbedderLocked().embed(text)
        }
        val embedding = result.embeddingResult().embeddings().firstOrNull()
            ?: throw EmbeddingEngine.UnsupportedEmbeddingModelException(
                EmbeddingEngine.MEDIAPIPE_INCOMPATIBLE_MESSAGE
            )
        val vector = embedding.floatEmbedding()
        if (vector.isEmpty()) {
            throw EmbeddingEngine.UnsupportedEmbeddingModelException(
                EmbeddingEngine.MEDIAPIPE_INCOMPATIBLE_MESSAGE
            )
        }
        return vector
    }

    override fun close() {
        synchronized(lock) {
            textEmbedder?.close()
            textEmbedder = null
            loadedModelSignature = null
        }
    }

    private fun ensureTextEmbedderLocked(): TextEmbedder {
        if (!embeddingModelManager.isEmbeddingModelReady()) {
            throw EmbeddingEngine.UnsupportedEmbeddingModelException(
                EmbeddingEngine.MODEL_NOT_IMPORTED_MESSAGE
            )
        }

        val modelFile = File(embeddingModelManager.embeddingModelPath())
        val signature = "${modelFile.absolutePath}:${modelFile.length()}:${modelFile.lastModified()}"
        val current = textEmbedder
        if (current != null && loadedModelSignature == signature) {
            return current
        }

        textEmbedder?.close()
        val created = try {
            TextEmbedder.createFromFile(context, modelFile)
        } catch (error: Exception) {
            throw EmbeddingEngine.UnsupportedEmbeddingModelException(
                translateMediaPipeError(error.message)
            )
        }

        textEmbedder = created
        loadedModelSignature = signature
        return created
    }

    private fun translateMediaPipeError(message: String?): String {
        val normalized = message.orEmpty().lowercase(Locale.US)
        return when {
            "token" in normalized ||
                "sentencepiece" in normalized ||
                "wordpiece" in normalized ||
                "vocab" in normalized -> EmbeddingEngine.TOKENIZER_UNSUPPORTED_MESSAGE

            "metadata" in normalized ||
                "int32 input tensor" in normalized ||
                "text models with int32 input tensors" in normalized ->
                "نموذج التضمين يحتاج metadata متوافقة مع MediaPipe."

            else -> EmbeddingEngine.MEDIAPIPE_INCOMPATIBLE_MESSAGE
        }
    }
}

private class TfliteEmbeddingBackend(
    private val embeddingModelManager: EmbeddingModelManager
) : BackendEngine {
    @Volatile
    private var interpreter: Interpreter? = null

    @Volatile
    private var loadedModelSignature: String? = null

    private val lock = Any()

    override fun embed(text: String): FloatArray {
        val normalized = text.trim()
        if (normalized.isBlank()) {
            throw EmbeddingEngine.UnsupportedEmbeddingModelException(
                EmbeddingEngine.MODEL_INCOMPATIBLE_MESSAGE
            )
        }

        val localInterpreter = ensureInterpreter()
        val inputTensor = localInterpreter.getInputTensor(0)
        val input = createInputPayload(inputTensor.shape(), inputTensor.dataType(), normalized)
        val outputIndex = findFloatOutputIndex(localInterpreter)
        val outputTensor = localInterpreter.getOutputTensor(outputIndex)
        val outputShape = outputTensor.shape()
        val elementCount = outputShape.fold(1) { acc, size ->
            if (size <= 0) {
                throw EmbeddingEngine.UnsupportedEmbeddingModelException(
                    EmbeddingEngine.MODEL_INCOMPATIBLE_MESSAGE
                )
            }
            acc * size
        }
        if (elementCount <= 0) {
            throw EmbeddingEngine.UnsupportedEmbeddingModelException(
                EmbeddingEngine.MODEL_INCOMPATIBLE_MESSAGE
            )
        }

        val outputBuffer = ByteBuffer.allocateDirect(elementCount * FLOAT_BYTES)
            .order(ByteOrder.nativeOrder())

        synchronized(lock) {
            val activeInterpreter = ensureInterpreterLocked()
            outputBuffer.rewind()
            activeInterpreter.runForMultipleInputsOutputs(
                arrayOf(input),
                mutableMapOf<Int, Any>(outputIndex to outputBuffer)
            )
        }

        outputBuffer.rewind()
        val vector = FloatArray(elementCount)
        outputBuffer.asFloatBuffer().get(vector)
        return vector
    }

    override fun close() {
        synchronized(lock) {
            interpreter?.close()
            interpreter = null
            loadedModelSignature = null
        }
    }

    private fun ensureInterpreter(): Interpreter {
        synchronized(lock) {
            return ensureInterpreterLocked()
        }
    }

    private fun ensureInterpreterLocked(): Interpreter {
        if (!embeddingModelManager.isEmbeddingModelReady()) {
            throw EmbeddingEngine.UnsupportedEmbeddingModelException(
                EmbeddingEngine.MODEL_NOT_IMPORTED_MESSAGE
            )
        }

        val modelFile = File(embeddingModelManager.embeddingModelPath())
        val signature = "${modelFile.absolutePath}:${modelFile.length()}:${modelFile.lastModified()}"
        val currentInterpreter = interpreter
        if (currentInterpreter != null && loadedModelSignature == signature) {
            return currentInterpreter
        }

        interpreter?.close()
        val buffer = mapModelFile(modelFile)
        val options = Interpreter.Options().apply {
            setNumThreads(DEFAULT_NUM_THREADS)
        }
        val createdInterpreter = Interpreter(buffer, options)
        createdInterpreter.allocateTensors()
        validateModel(createdInterpreter)

        interpreter = createdInterpreter
        loadedModelSignature = signature
        return createdInterpreter
    }

    private fun validateModel(interpreter: Interpreter) {
        if (interpreter.inputTensorCount != 1) {
            throw EmbeddingEngine.UnsupportedEmbeddingModelException(
                EmbeddingEngine.TOKENIZER_UNSUPPORTED_MESSAGE
            )
        }

        val inputTensor = interpreter.getInputTensor(0)
        if (inputTensor.dataType() != DataType.STRING) {
            throw EmbeddingEngine.UnsupportedEmbeddingModelException(
                EmbeddingEngine.TOKENIZER_UNSUPPORTED_MESSAGE
            )
        }

        val shape = inputTensor.shape()
        if (shape.isNotEmpty() && !(shape.size == 1 && shape[0] == 1)) {
            throw EmbeddingEngine.UnsupportedEmbeddingModelException(
                EmbeddingEngine.MODEL_INCOMPATIBLE_MESSAGE
            )
        }

        findFloatOutputIndex(interpreter)
    }

    private fun createInputPayload(shape: IntArray, dataType: DataType, text: String): Any {
        if (dataType != DataType.STRING) {
            throw EmbeddingEngine.UnsupportedEmbeddingModelException(
                EmbeddingEngine.TOKENIZER_UNSUPPORTED_MESSAGE
            )
        }

        return when {
            shape.isEmpty() -> text
            shape.size == 1 && shape[0] == 1 -> arrayOf(text)
            else -> throw EmbeddingEngine.UnsupportedEmbeddingModelException(
                EmbeddingEngine.MODEL_INCOMPATIBLE_MESSAGE
            )
        }
    }

    private fun findFloatOutputIndex(interpreter: Interpreter): Int {
        for (index in 0 until interpreter.outputTensorCount) {
            val outputTensor = interpreter.getOutputTensor(index)
            if (outputTensor.dataType() == DataType.FLOAT32) {
                return index
            }
        }
        throw EmbeddingEngine.UnsupportedEmbeddingModelException(
            EmbeddingEngine.MODEL_INCOMPATIBLE_MESSAGE
        )
    }

    private fun mapModelFile(file: File): MappedByteBuffer {
        FileInputStream(file).channel.use { channel ->
            return channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size())
        }
    }

    companion object {
        private const val DEFAULT_NUM_THREADS = 2
        private const val FLOAT_BYTES = 4
    }
}
