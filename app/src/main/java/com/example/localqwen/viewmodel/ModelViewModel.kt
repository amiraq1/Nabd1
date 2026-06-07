package com.example.localqwen.viewmodel

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.localqwen.engine.NabdInferenceEngine
import com.example.localqwen.document.LocalDocument
import com.example.localqwen.model.ModelManager
import com.example.localqwen.model.ModelManager.SupportedModel
import com.example.localqwen.model.ModelManager.Companion.SUPPORTED_MODELS
import com.example.localqwen.model.ModelManager.Companion.MIN_MODEL_SIZE_BYTES
import com.example.localqwen.rag.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import com.example.localqwen.data.SecurePreferences
import com.example.localqwen.model.ModelLoadingState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class ModelViewModel @Inject constructor(
    application: Application,
    private val mManager: ModelManager
) : AndroidViewModel(application) {

    val modelManager: ModelManager get() = mManager

    private val preferences = SecurePreferences.get(application)
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

    val modelLoadingState: StateFlow<ModelLoadingState> = mManager.modelLoadingState

    private val _setupState = MutableLiveData<ModelSetupState>(ModelSetupState.Idle)
    val setupState: LiveData<ModelSetupState> = _setupState

    private val _selectedModel = MutableLiveData<SupportedModel>(resolveSelectedModel())
    val selectedModel: LiveData<SupportedModel> = _selectedModel

    private val _statusEvent = MutableLiveData<StatusEvent>()
    val statusEvent: LiveData<StatusEvent> = _statusEvent

    private val _isGenerating = MutableLiveData<Boolean>(false)
    val isGenerating: LiveData<Boolean> = _isGenerating

    private val _responseMode = MutableLiveData<String>(preferences.getString(KEY_RESPONSE_MODE, "balanced") ?: "balanced")
    val responseMode: LiveData<String> = _responseMode

    private val _inferenceBackend = MutableLiveData<String>(preferences.getString(KEY_INFERENCE_BACKEND, "cpu") ?: "cpu")
    val inferenceBackend: LiveData<String> = _inferenceBackend

    val textInferenceEngine: NabdInferenceEngine? get() = mManager.getEngine()
    val loadedModelId: String? get() = mManager.getLoadedModelId()

    var localEngineLastErrorMessage: String? = null
        private set

    private val _performanceState = MutableLiveData<PerformanceState>(PerformanceState())
    val performanceState: LiveData<PerformanceState> = _performanceState

    private var performanceMonitorJob: kotlinx.coroutines.Job? = null

    init {
        viewModelScope.launch {
            mManager.modelLoadingState.collect { loadingState ->
                updateModelState(loadingState)
            }
        }
        refreshModelState()
        startPerformanceMonitoring()
    }

    private fun updateModelState(loadingState: ModelLoadingState) {
        _modelState.value = when (loadingState) {
            is ModelLoadingState.Idle -> ModelState.Idle
            is ModelLoadingState.Loading -> ModelState.Loading(loadingState.progress)
            is ModelLoadingState.Ready -> ModelState.Ready
            is ModelLoadingState.Unloading -> ModelState.Loading()
            is ModelLoadingState.Error -> ModelState.Error(loadingState.message)
        }
    }

    private fun startPerformanceMonitoring() {
        performanceMonitorJob?.cancel()
        performanceMonitorJob = viewModelScope.launch {
            while (true) {
                val ram = getRamUsage()
                _performanceState.value = _performanceState.value?.let { current ->
                    val newHistory = (current.ramHistory + ram).takeLast(20)
                    current.copy(ramUsagePercent = ram, ramHistory = newHistory)
                }
                kotlinx.coroutines.delay(2000)
            }
        }
    }

    private fun getRamUsage(): Float {
        return try {
            val activityManager = getApplication<Application>().getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memoryInfo = ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memoryInfo)
            val available = memoryInfo.availMem.toFloat()
            val total = memoryInfo.totalMem.toFloat()
            (total - available) / total
        } catch (e: Exception) {
            0f
        }
    }

    fun refreshModelState() {
        val model = _selectedModel.value ?: return
        if (mManager.isModelReady(model)) {
            if (loadedModelId == model.id && isEngineReady()) {
                _modelState.value = ModelState.Ready
            } else {
                _modelState.value = ModelState.Idle
            }
        } else {
            _modelState.value = ModelState.NotImported
        }
    }

    fun selectModel(model: SupportedModel) {
        _selectedModel.value = model
        preferences.edit().putString(KEY_SELECTED_MODEL_ID, model.id).apply()
        refreshModelState()
    }

    fun loadModel() {
        val model = _selectedModel.value ?: return
        if (!mManager.isModelReady(model)) {
            _modelState.value = ModelState.NotImported
            _statusEvent.value = StatusEvent.Error("ملف النموذج غير موجود. يرجى استيراده أولاً.")
            return
        }
        
        if (_modelState.value is ModelState.Loading) return
        
        viewModelScope.launch {
            val backend = _inferenceBackend.value ?: "cpu"
            mManager.loadModel(model.id, backend)
        }
    }

    fun unloadModel() {
        viewModelScope.launch {
            mManager.unloadModel()
        }
    }

    fun isEngineReady(): Boolean {
        return textInferenceEngine?.isReady() == true
    }

    fun setupModel(uri: Uri) {
        val model = _selectedModel.value ?: return
        _setupState.value = ModelSetupState.Loading
        viewModelScope.launch {
            try {
                val success = withContext(Dispatchers.IO) {
                    val targetFile = mManager.modelFile(model)
                    getApplication<Application>().contentResolver.openInputStream(uri)?.use { input ->
                        targetFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    true
                }

                if (success && mManager.isModelReady(model)) {
                    loadModelInternal(model)
                } else {
                    _setupState.value = ModelSetupState.Error("فشل نسخ ملف النموذج أو الملف تالف.", null)
                }
            } catch (e: Exception) {
                _setupState.value = ModelSetupState.Error("خطأ أثناء الإعداد: ${e.message}", e.stackTraceToString())
            }
        }
    }

    private suspend fun loadModelInternal(model: SupportedModel) {
        val backend = _inferenceBackend.value ?: "cpu"
        mManager.loadModel(model.id, backend)
        
        val finalState = mManager.modelLoadingState.first { state ->
            state is ModelLoadingState.Ready || state is ModelLoadingState.Error
        }
        
        if (finalState is ModelLoadingState.Ready) {
            _setupState.value = ModelSetupState.Ready(model.displayName)
        } else if (finalState is ModelLoadingState.Error) {
            _setupState.value = ModelSetupState.Error(finalState.message, null)
        }
    }

    fun resetSetupState() {
        _setupState.value = ModelSetupState.Idle
        refreshModelState()
    }

    fun setResponseMode(mode: String) {
        _responseMode.value = mode
        preferences.edit().putString(KEY_RESPONSE_MODE, mode).apply()
    }

    fun setInferenceBackend(backend: String) {
        _inferenceBackend.value = backend
        preferences.edit().putString(KEY_INFERENCE_BACKEND, backend).apply()
    }

    fun addTpsRecord(tps: Float) {
        if (tps <= 0) return
        _performanceState.value = _performanceState.value?.let { current ->
            val newHistory = (current.tpsHistory + tps).takeLast(20)
            current.copy(lastTps = tps, tpsHistory = newHistory)
        }
    }

    fun clearTpsHistory() {
        _performanceState.value = _performanceState.value?.copy(
            lastTps = 0f,
            tpsHistory = emptyList()
        )
    }

    fun runBenchmark() {
        val engine = textInferenceEngine ?: return
        if (!engine.isReady()) return

        _performanceState.value = _performanceState.value?.copy(isBenchmarking = true, benchmarkResult = null)
        
        viewModelScope.launch {
            val startTime = SystemClock.elapsedRealtime()
            var tokens = 0
            try {
                engine.generate("Benchmark test.").collect {
                    tokens++
                }
                val duration = (SystemClock.elapsedRealtime() - startTime) / 1000f
                val tps = if (duration > 0) tokens / duration else 0f
                _performanceState.value = _performanceState.value?.copy(
                    isBenchmarking = false,
                    benchmarkResult = "النتيجة: ${"%.1f".format(tps)} توكن/ثانية"
                )
            } catch (e: Exception) {
                _performanceState.value = _performanceState.value?.copy(
                    isBenchmarking = false,
                    benchmarkResult = "فشل الفحص: ${e.message}"
                )
            }
        }
    }

    fun modelImportStatus(model: SupportedModel): String {
        return if (mManager.isModelImported(model.id)) "مستورد" else "غير مستورد"
    }

    fun currentModelStatusLabel(): String {
        return when (modelState.value) {
            is ModelState.Ready -> "جاهز"
            is ModelState.Loading -> "جاري التحميل..."
            is ModelState.Idle -> "مستورد (غير محمل)"
            else -> "غير متوفر"
        }
    }

    fun currentRagMode(): RagMode {
        val modeStr = preferences.getString(KEY_RAG_SEARCH_MODE, "auto") ?: "auto"
        return try { RagMode.valueOf(modeStr.uppercase()) } catch (_: Exception) { RagMode.AUTO }
    }

    fun setRagMode(mode: String) {
        preferences.edit().putString(KEY_RAG_SEARCH_MODE, mode).apply()
    }

    fun currentEmbeddingBackend(): EmbeddingBackend {
        val bStr = preferences.getString(KEY_EMBEDDING_BACKEND, "auto") ?: "auto"
        return try { EmbeddingBackend.valueOf(bStr.uppercase()) } catch (_: Exception) { EmbeddingBackend.AUTO }
    }

    fun setEmbeddingBackend(backend: String) {
        preferences.edit().putString(KEY_EMBEDDING_BACKEND, backend).apply()
    }

    fun embeddingModelStatus(): String {
        return if (embeddingModelManager.isEmbeddingModelReady()) "جاهز" else "غير مستورد"
    }

    fun deleteEmbeddingModel() {
        embeddingModelManager.deleteEmbeddingModel()
    }

    fun deleteEmbeddingIndexes() {
        embeddingStore.deleteAllIndexes()
    }

    suspend fun buildSemanticIndex(document: LocalDocument?) {
        if (document == null) return
        _statusEvent.value = StatusEvent.Info("جاري بناء الفهرس الدلالي...")
        try {
            val chunks = TextChunker.chunkText(document.extractedText)
            val indexedChunks = chunks.mapIndexed { index, text ->
                EmbeddingStore.IndexedChunk(
                    chunkIndex = index,
                    text = text,
                    vector = embeddingEngine.embed(text)
                )
            }
            embeddingStore.saveIndex(document.id, indexedChunks)
            _statusEvent.value = StatusEvent.Success("تم بناء الفهرس الدلالي")
        } catch (error: Exception) {
            _statusEvent.value = StatusEvent.Error("تعذر بناء الفهرس الدلالي: ${error.message}")
        }
    }

    fun onAppBackgrounded() {
        viewModelScope.launch {
            mManager.unloadModel()
            Log.d("ModelVM", "App in background, model unloaded to save RAM")
        }
    }

    fun onAppForegrounded() {
    }

    private fun resolveSelectedModel(): SupportedModel {
        val id = preferences.getString(KEY_SELECTED_MODEL_ID, SUPPORTED_MODELS[1].id)
        return mManager.getModelById(id ?: SUPPORTED_MODELS[1].id) ?: SUPPORTED_MODELS[1]
    }

    override fun onCleared() {
        super.onCleared()
        performanceMonitorJob?.cancel()
        embeddingEngine.close()
    }

    companion object {
        private const val KEY_SELECTED_MODEL_ID = "selected_model_id"
        private const val KEY_RAG_SEARCH_MODE = "rag_search_mode"
        private const val KEY_EMBEDDING_BACKEND = "embedding_backend"
        private const val KEY_RESPONSE_MODE = "response_mode"
        private const val KEY_INFERENCE_BACKEND = "inference_backend"
    }
}

sealed class ModelState {
    data object NotImported : ModelState()
    data object Idle : ModelState()
    data class Loading(val progress: Float? = null) : ModelState()
    data object Ready : ModelState()
    data class Error(val message: String) : ModelState()
}

sealed class ModelSetupState {
    data object Idle : ModelSetupState()
    data object Loading : ModelSetupState()
    data object Validating : ModelSetupState()
    data object Copying : ModelSetupState()
    data class Ready(val modelName: String) : ModelSetupState()
    data class Error(val userMessage: String, val technicalMessage: String?) : ModelSetupState()
}

data class PerformanceState(
    val ramUsagePercent: Float = 0f,
    val ramHistory: List<Float> = emptyList(),
    val lastTps: Float = 0f,
    val tpsHistory: List<Float> = emptyList(),
    val isBenchmarking: Boolean = false,
    val benchmarkResult: String? = null
)
