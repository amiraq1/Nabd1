package com.example.localqwen.model

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.util.Log
import com.example.localqwen.engine.NabdInferenceEngine
import com.example.localqwen.engine.InferenceEngineFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

sealed class ModelLoadingState {
    data object Idle : ModelLoadingState()
    data class Loading(val progress: Float) : ModelLoadingState()
    data object Ready : ModelLoadingState()
    data object Unloading : ModelLoadingState()
    data class Error(val message: String) : ModelLoadingState()
}

@Singleton
class ModelManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val engineFactory: InferenceEngineFactory
) : ComponentCallbacks2 {

    private val _modelLoadingState = MutableStateFlow<ModelLoadingState>(ModelLoadingState.Idle)
    val modelLoadingState: StateFlow<ModelLoadingState> = _modelLoadingState.asStateFlow()

    private var activeEngine: NabdInferenceEngine? = null
    @Volatile private var currentModelId: String? = null
    private val modelLock = Mutex()

    fun getActiveModelId(): String? = currentModelId
    fun setActiveModelId(id: String?) { currentModelId = id }

    fun getDefaultTextModelId(): String? = SUPPORTED_MODELS.firstOrNull()?.id

    init {
        context.registerComponentCallbacks(this)
    }

    data class SupportedModel(
        val id: String,
        val displayName: String,
        val fileName: String,
        val description: String
    )

    companion object {
        private const val TAG = "ModelManager"
        const val MIN_MODEL_SIZE_BYTES = 50_000_000L

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

    suspend fun loadModel(modelId: String, backend: String = "cpu") = withContext(Dispatchers.IO) {
        modelLock.withLock {
            if (currentModelId == modelId && activeEngine?.isReady() == true) {
                Log.d(TAG, "MODEL_ALREADY_LOADED: Model $modelId is already active")
                _modelLoadingState.value = ModelLoadingState.Ready
                return@withLock
            }

            val model = getModelById(modelId) ?: run {
                _modelLoadingState.value = ModelLoadingState.Error("النموذج غير موجود")
                return@withLock
            }

            try {
                _modelLoadingState.value = ModelLoadingState.Loading(0.1f)
                unloadInternal()

                _modelLoadingState.value = ModelLoadingState.Loading(0.3f)
                val engine = engineFactory.getEngine()
                
                _modelLoadingState.value = ModelLoadingState.Loading(0.6f)
                engine.load(modelPath(model), context.cacheDir.absolutePath, backend)
                
                activeEngine = engine
                currentModelId = modelId
                _modelLoadingState.value = ModelLoadingState.Ready
                Log.d(TAG, "ENGINE_CREATED: Model $modelId loaded successfully on $backend")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load model $modelId", e)
                _modelLoadingState.value = ModelLoadingState.Error(e.message ?: "فشل تحميل النموذج")
            }
        }
    }

    suspend fun unloadModel() = withContext(Dispatchers.IO) {
        modelLock.withLock {
            unloadInternal()
        }
    }

    private suspend fun unloadInternal() {
        if (activeEngine != null) {
            _modelLoadingState.value = ModelLoadingState.Unloading
            try {
                activeEngine?.closeSuspend()
                Log.d(TAG, "ENGINE_DESTROYED: Engine closed")
            } catch (e: Exception) {
                Log.w(TAG, "Error closing engine", e)
            } finally {
                activeEngine = null
                currentModelId = null
                _modelLoadingState.value = ModelLoadingState.Idle
                Log.d(TAG, "Model unloaded")
            }
        }
    }

    fun getEngine(): NabdInferenceEngine? = activeEngine
    fun getLoadedModelId(): String? = currentModelId

    fun getModelById(modelId: String): SupportedModel? {
        if (modelId == VISION_MODEL.id) return VISION_MODEL
        return SUPPORTED_MODELS.firstOrNull { it.id == modelId }
    }

    private fun modelDir(model: SupportedModel): File {
        return File(context.filesDir, "models/${model.id}").apply {
            if (!exists()) mkdirs()
        }
    }

    fun modelFile(model: SupportedModel): File = File(modelDir(model), "model.litertlm")
    fun tempModelFile(model: SupportedModel): File = File(modelDir(model), "model.litertlm.tmp")
    fun modelPath(model: SupportedModel): String = modelFile(model).absolutePath

    fun isModelImported(modelId: String): Boolean {
        val model = getModelById(modelId) ?: return false
        val file = modelFile(model)
        return file.exists() && file.isFile && file.length() >= 10_000_000
    }

    fun isModelReady(model: SupportedModel): Boolean {
        val file = modelFile(model)
        return file.exists() && file.isFile && file.length() >= MIN_MODEL_SIZE_BYTES
    }

    fun isModelFileExtensionValid(fileName: String?): Boolean {
        return fileName?.endsWith(".litertlm", ignoreCase = true) == true ||
               fileName?.endsWith(".task", ignoreCase = true) == true
    }

    fun deleteModel(model: SupportedModel): Boolean = modelDir(model).deleteRecursively()

    override fun onTrimMemory(level: Int) {
        Log.d(TAG, "onTrimMemory level: $level")
        CoroutineScope(Dispatchers.IO).launch {
            when (level) {
                ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE -> {
                    modelLock.withLock {
                        activeEngine?.resetConversation()
                        Log.d(TAG, "Trim: KV Cache cleared")
                    }
                }
                ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL,
                ComponentCallbacks2.TRIM_MEMORY_BACKGROUND -> {
                    unloadModel()
                    Log.d(TAG, "Trim: Model unloaded")
                }
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {}
    override fun onLowMemory() {
        onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL)
    }
}
