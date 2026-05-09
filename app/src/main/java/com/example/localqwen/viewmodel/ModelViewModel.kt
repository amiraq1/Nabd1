package com.example.localqwen.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.localqwen.engine.LiteRtLmInferenceEngine
import com.example.localqwen.engine.NabdInferenceEngine
import com.example.localqwen.model.ModelManager
import com.example.localqwen.model.ModelManager.SupportedModel
import com.example.localqwen.rag.EmbeddingBackend
import com.example.localqwen.rag.EmbeddingEngine
import com.example.localqwen.rag.EmbeddingModelManager
import com.example.localqwen.rag.EmbeddingStore
import com.example.localqwen.rag.RagMode
import com.example.localqwen.rag.SemanticRetriever
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class ModelViewModel(application: Application) : AndroidViewModel(application) {

    private val preferences = application.getSharedPreferences("nabd_prefs", 0)
    val modelManager = ModelManager(application)
    val embeddingModelManager = EmbeddingModelManager(application)
    val embeddingStore = EmbeddingStore(application)

    val embeddingEngine: EmbeddingEngine = EmbeddingEngine(
        context = application,
        embeddingModelManager = embeddingModelManager,
        backendSelector = ::currentEmbeddingBackend
    )

    val semanticRetriever = SemanticRetriever(embeddingEngine, embeddingStore)

    private val _modelState = MutableLiveData<ModelState>(ModelState.NotImported)
    val modelState: LiveData<ModelState> = _modelState

    private val _selectedModel = MutableLiveData<SupportedModel>(resolveSelectedModel())
    val selectedModel: LiveData<SupportedModel> = _selectedModel

    private val _statusEvent = MutableLiveData<StatusEvent>()
    val statusEvent: LiveData<StatusEvent> = _statusEvent

    var textInferenceEngine: NabdInferenceEngine? = null
        private set

    var loadedModelId: String? = null
        private set

    var localEngineLastErrorMessage: String? = null
        private set

    init {
        refreshModelState()
    }

    fun selectModel(model: SupportedModel) {
        _selectedModel.value = model
        preferences.edit().putString(KEY_SELECTED_MODEL_ID, model.id).apply()
        refreshModelState()
    }

    fun loadModel() {
        val model = _selectedModel.value ?: return
        if (!modelManager.isModelReady(model)) {
            _statusEvent.value = StatusEvent.Error("النموذج غير موجود")
            return
        }
        if (_modelState.value is ModelState.Loading) return
        if (loadedModelId == model.id && textInferenceEngine?.isReady() == true) {
            _statusEvent.value = StatusEvent.Success("تم تشغيل نبض")
            return
        }

        _modelState.value = ModelState.Loading
        _statusEvent.value = StatusEvent.Info("جاري تشغيل نبض...")

        viewModelScope.launch {
            try {
                val newEngine = LiteRtLmInferenceEngine()
                withContext(Dispatchers.IO) {
                    newEngine.load(
                        modelManager.modelPath(model),
                        getApplication<Application>().cacheDir.absolutePath
                    )
                }

                textInferenceEngine = newEngine
                loadedModelId = model.id
                localEngineLastErrorMessage = null
                _modelState.value = ModelState.Ready
                _statusEvent.value = StatusEvent.Success("تم تشغيل نبض")
            } catch (e: Exception) {
                localEngineLastErrorMessage = e.message
                _modelState.value = ModelState.Error(e.message ?: "خطأ غير معروف")
                _statusEvent.value = StatusEvent.Error("تعذر تشغيل النموذج: ${e.message ?: "خطأ"}")
            }
        }
    }

    fun unloadModel() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                textInferenceEngine?.unload()
            }
            textInferenceEngine = null
            loadedModelId = null
            _modelState.value = ModelState.Idle
            _statusEvent.value = StatusEvent.Info("تم إيقاف النموذج")
        }
    }

    fun isEngineReady(): Boolean {
        val model = _selectedModel.value ?: return false
        return textInferenceEngine?.isReady() == true && loadedModelId == model.id
    }

    fun currentModelStatusLabel(): String {
        return when (_modelState.value) {
            is ModelState.Loading -> "جاري التشغيل"
            is ModelState.Ready -> "مشغّل"
            is ModelState.Idle -> "غير مشغّل"
            is ModelState.Error -> "خطأ"
            is ModelState.NotImported -> "غير مستورد"
            else -> "غير معروف"
        }
    }

    fun currentRagMode(): RagMode {
        return runCatching {
            RagMode.valueOf(
                preferences.getString(KEY_RAG_SEARCH_MODE, RagMode.AUTO.name)
                    ?.uppercase(Locale.US) ?: RagMode.AUTO.name
            )
        }.getOrDefault(RagMode.AUTO)
    }

    fun currentEmbeddingBackend(): EmbeddingBackend {
        return runCatching {
            EmbeddingBackend.valueOf(
                preferences.getString(KEY_EMBEDDING_BACKEND, EmbeddingBackend.AUTO.name)
                    ?.uppercase(Locale.US) ?: EmbeddingBackend.AUTO.name
            )
        }.getOrDefault(EmbeddingBackend.AUTO)
    }

    fun updateRagMode(value: String) {
        val mode = runCatching { RagMode.valueOf(value.uppercase(Locale.US)) }.getOrDefault(RagMode.AUTO)
        preferences.edit().putString(KEY_RAG_SEARCH_MODE, mode.name).apply()
    }

    fun updateEmbeddingBackend(value: String) {
        val backend = runCatching { EmbeddingBackend.valueOf(value.uppercase(Locale.US)) }
            .getOrDefault(EmbeddingBackend.AUTO)
        preferences.edit().putString(KEY_EMBEDDING_BACKEND, backend.name).apply()
        embeddingEngine.close()
    }

    fun modelImportStatus(model: SupportedModel): String {
        val imported = modelManager.isModelImported(model.id)
        if (!imported) return "غير مستورد"
        val sizeBytes = modelManager.modelSizeBytes(model.id)
        return if (sizeBytes > 0L) "مستورد - ${formatStorageSize(sizeBytes)}" else "مستورد"
    }

    fun embeddingModelStatus(): String {
        if (!embeddingModelManager.isEmbeddingModelReady()) return "غير مستورد"
        val sizeBytes = embeddingModelManager.modelSizeBytes()
        return if (sizeBytes > 0L) "مستورد - ${formatStorageSize(sizeBytes)}" else "مستورد"
    }

    private fun refreshModelState() {
        val model = _selectedModel.value ?: return
        _modelState.value = when {
            textInferenceEngine?.isReady() == true && loadedModelId == model.id -> ModelState.Ready
            modelManager.isModelReady(model) -> ModelState.Idle
            else -> ModelState.NotImported
        }
    }

    private fun resolveSelectedModel(): SupportedModel {
        val savedId = preferences.getString(KEY_SELECTED_MODEL_ID, null)
        return ModelManager.SUPPORTED_MODELS.find { it.id == savedId } ?: ModelManager.SUPPORTED_MODELS.first()
    }

    private fun formatStorageSize(bytes: Long): String {
        return when {
            bytes >= 1_073_741_824 -> String.format(Locale.US, "%.1f GB", bytes / 1_073_741_824.0)
            bytes >= 1_048_576 -> String.format(Locale.US, "%.1f MB", bytes / 1_048_576.0)
            bytes >= 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
            else -> "$bytes B"
        }
    }

    override fun onCleared() {
        super.onCleared()
        embeddingEngine.close()
        viewModelScope.launch(Dispatchers.IO) {
            textInferenceEngine?.unload()
        }
    }

    companion object {
        private const val KEY_SELECTED_MODEL_ID = "selected_model_id"
        private const val KEY_RAG_SEARCH_MODE = "rag_search_mode"
        private const val KEY_EMBEDDING_BACKEND = "embedding_backend"
    }
}

sealed class ModelState {
    data object NotImported : ModelState()
    data object Idle : ModelState()
    data object Loading : ModelState()
    data object Ready : ModelState()
    data class Error(val message: String) : ModelState()
}
