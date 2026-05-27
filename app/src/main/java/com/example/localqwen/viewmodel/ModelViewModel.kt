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
import com.example.localqwen.engine.LiteRtLmInferenceEngine
import com.example.localqwen.engine.NabdInferenceEngine
import com.example.localqwen.document.LocalDocument
import com.example.localqwen.model.ModelManager
import com.example.localqwen.model.ModelManager.SupportedModel
import com.example.localqwen.rag.EmbeddingBackend
import com.example.localqwen.rag.EmbeddingEngine
import com.example.localqwen.rag.EmbeddingModelManager
import com.example.localqwen.rag.EmbeddingStore
import com.example.localqwen.rag.RagMode
import com.example.localqwen.rag.SemanticRetriever
import com.example.localqwen.rag.TextChunker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import com.example.localqwen.data.SecurePreferences

class ModelViewModel(application: Application) : AndroidViewModel(application) {

    private val preferences = SecurePreferences.get(application)
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

    var textInferenceEngine: NabdInferenceEngine? = null
        private set

    var loadedModelId: String? = null
        private set

    var localEngineLastErrorMessage: String? = null
        private set

    private val _performanceState = MutableLiveData<PerformanceState>(PerformanceState())
    val performanceState: LiveData<PerformanceState> = _performanceState

    private var performanceMonitorJob: kotlinx.coroutines.Job? = null

    init {
        refreshModelState()
        startPerformanceMonitoring()
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

    fun addTpsRecord(tps: Float) {
        if (tps <= 0) return
        _performanceState.value = _performanceState.value?.let { current ->
            val newHistory = (current.tpsHistory + tps).takeLast(20)
            current.copy(lastTps = tps, tpsHistory = newHistory)
        }
    }

    private fun getRamUsage(): Float {
        return try {
            val activityManager = getApplication<Application>().getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memoryInfo = ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memoryInfo)
            val usedMem = memoryInfo.totalMem - memoryInfo.availMem
            (usedMem.toFloat() / memoryInfo.totalMem.toFloat())
        } catch (_: Exception) { 0f }
    }

    fun runBenchmark() {
        val engine = textInferenceEngine
        if (engine == null || !engine.isReady()) {
            _statusEvent.value = StatusEvent.Error("يجب تشغيل النموذج أولاً لبدء الفحص.")
            return
        }

        viewModelScope.launch {
            _performanceState.value = _performanceState.value?.copy(isBenchmarking = true, benchmarkResult = null)
            val currentBackend = _inferenceBackend.value ?: "cpu"
            _statusEvent.value = StatusEvent.Info("جاري فحص جهد ($currentBackend)...")

            val startTime = SystemClock.elapsedRealtime()
            var firstTokenTime: Long = 0
            var tokenCount = 0
            val testPrompt = "Write a one-sentence greeting."

            try {
                withContext(Dispatchers.IO) {
                    engine.generate(testPrompt).collect { _ ->
                        if (tokenCount == 0) {
                            firstTokenTime = SystemClock.elapsedRealtime()
                        }
                        tokenCount++
                        if (tokenCount >= 30) {
                            throw BenchmarkFinishedException()
                        }
                    }
                }
            } catch (_: BenchmarkFinishedException) {
                // Normal exit
            } catch (e: Exception) {
                Log.e("Benchmark", "Error during benchmark", e)
            }

            val endTime = SystemClock.elapsedRealtime()
            val ttft = if (firstTokenTime > 0) firstTokenTime - startTime else 0L
            val durationAfterFirst = if (endTime > firstTokenTime && firstTokenTime > 0) (endTime - firstTokenTime) / 1000f else 0f
            val tps = if (durationAfterFirst > 0) (tokenCount - 1) / durationAfterFirst else 0f
            
            val result = "[$currentBackend] TTFT: ${ttft}ms | TPS: ${"%.1f".format(tps)}"
            Log.d("NabdPerformance", "Benchmark Result: $result")
            
            _performanceState.value = _performanceState.value?.copy(
                isBenchmarking = false,
                benchmarkResult = result,
                lastTps = tps
            )
            _statusEvent.value = StatusEvent.Success("اكتمل الفحص: $result")
        }
    }

    private class BenchmarkFinishedException : Exception()

    fun selectModel(model: SupportedModel) {
        _selectedModel.value = model
        preferences.edit().putString(KEY_SELECTED_MODEL_ID, model.id).apply()
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

    fun importModel(model: SupportedModel, uri: android.net.Uri) {
        viewModelScope.launch {
            Log.d("ModelViewModel", "Starting import for model: ${model.id}")
            Log.d("ModelViewModel", "Selected URI: $uri")
            _statusEvent.value = StatusEvent.Info("جاري استيراد ${model.displayName}...")
            try {
                val success = withContext(Dispatchers.IO) {
                    val targetFile = modelManager.modelFile(model)
                    val tempFile = modelManager.tempModelFile(model)
                    Log.d("ModelViewModel", "Target path: ${targetFile.absolutePath}")
                    Log.d("ModelViewModel", "Temp path: ${tempFile.absolutePath}")

                    tempFile.parentFile?.mkdirs()
                    if (tempFile.exists()) tempFile.delete()

                    getApplication<Application>().contentResolver.openInputStream(uri)?.use { input ->
                        tempFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    } ?: error("تعذر فتح ملف النموذج المختار")

                    Log.d("ModelViewModel", "Copy to temp finished. Temp size: ${tempFile.length()} bytes")

                    if (tempFile.length() < ModelManager.MIN_MODEL_SIZE_BYTES) {
                        val size = tempFile.length()
                        tempFile.delete()
                        error("ملف النموذج صغير جدًا ($size bytes). تأكد من اكتمال التحميل.")
                    }

                    if (targetFile.exists()) {
                        Log.d("ModelViewModel", "Deleting existing target file")
                        if (!targetFile.delete()) {
                            tempFile.delete()
                            error("تعذر استبدال ملف النموذج السابق")
                        }
                    }

                    if (!tempFile.renameTo(targetFile)) {
                        Log.d("ModelViewModel", "Rename failed, falling back to copy")
                        tempFile.copyTo(targetFile, overwrite = true)
                        tempFile.delete()
                    }

                    Log.d("ModelViewModel", "Model import successful. Final file exists: ${targetFile.exists()}, size: ${targetFile.length()}")
                    true
                }
                if (success) {
                    refreshModelState()
                    _statusEvent.value = StatusEvent.Success("تم استيراد ${model.displayName} بنجاح")
                }
            } catch (e: Exception) {
                Log.e("ModelViewModel", "Failed to import model: ${model.id}", e)
                _statusEvent.value = StatusEvent.Error("فشل استيراد النموذج: ${e.message}")
            }
        }
    }

    fun setupModel(uri: Uri) {
        val model = _selectedModel.value ?: return
        viewModelScope.launch {
            _setupState.value = ModelSetupState.Validating
            _statusEvent.value = StatusEvent.Info("جاري التحقق من ملف النموذج...")
            
            try {
                // 1. Validation
                val fileName = getFileName(uri)
                if (fileName != null && !modelManager.isModelFileExtensionValid(fileName)) {
                    _setupState.value = ModelSetupState.Error("الملف المختار ليس نموذجاً مدعوماً. اختر ملفاً بصيغة .litertlm", "Invalid extension: $fileName")
                    return@launch
                }

                // 2. Copying
                _setupState.value = ModelSetupState.Copying
                _statusEvent.value = StatusEvent.Info("جاري حفظ النموذج محلياً...")
                
                val success = withContext(Dispatchers.IO) {
                    val targetFile = modelManager.modelFile(model)
                    val tempFile = modelManager.tempModelFile(model)
                    
                    tempFile.parentFile?.mkdirs()
                    if (tempFile.exists()) tempFile.delete()

                    getApplication<Application>().contentResolver.openInputStream(uri)?.use { input ->
                        tempFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    } ?: error("تعذر فتح ملف النموذج المختار")

                    if (tempFile.length() < ModelManager.MIN_MODEL_SIZE_BYTES) {
                        tempFile.delete()
                        error("ملف النموذج صغير جداً. تأكد من اكتمال التحميل.")
                    }

                    if (targetFile.exists() && !targetFile.delete()) {
                        tempFile.delete()
                        error("تعذر استبدال ملف النموذج السابق")
                    }

                    if (!tempFile.renameTo(targetFile)) {
                        tempFile.copyTo(targetFile, overwrite = true)
                        tempFile.delete()
                    }
                    true
                }

                if (success) {
                    refreshModelState()
                    
                    // 3. Loading
                    _setupState.value = ModelSetupState.Loading
                    _statusEvent.value = StatusEvent.Info("جاري تشغيل النموذج تلقائياً...")
                    
                    loadModelInternal(model)
                }
            } catch (e: Exception) {
                Log.e("ModelViewModel", "Setup failed", e)
                _setupState.value = ModelSetupState.Error("فشل إعداد النموذج: ${e.message}", e.stackTraceToString())
                _statusEvent.value = StatusEvent.Error("فشل إعداد النموذج")
            }
        }
    }

    private suspend fun loadModelInternal(model: SupportedModel) {
        try {
            withContext(Dispatchers.IO) {
                textInferenceEngine?.unload()
            }
            textInferenceEngine = null
            
            val newEngine = LiteRtLmInferenceEngine()
            val backend = _inferenceBackend.value ?: "cpu"
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    newEngine.load(
                        modelManager.modelPath(model),
                        getApplication<Application>().cacheDir.absolutePath,
                        backend
                    )
                }
            }

            if (result.isSuccess) {
                textInferenceEngine = newEngine
                loadedModelId = model.id
                localEngineLastErrorMessage = null
                _modelState.value = ModelState.Ready
                _setupState.value = ModelSetupState.Ready(model.displayName)
                _statusEvent.value = StatusEvent.Success("تم إعداد وتشغيل ${model.displayName}")
            } else {
                val error = result.exceptionOrNull()
                localEngineLastErrorMessage = error?.message
                val userMsg = "تعذر تشغيل النموذج. قد يكون الملف غير متوافق أو ذاكرة الجهاز غير كافية."
                _setupState.value = ModelSetupState.Error(userMsg, error?.stackTraceToString())
                _modelState.value = ModelState.Error(userMsg)
            }
        } catch (e: Exception) {
            _setupState.value = ModelSetupState.Error("خطأ غير متوقع: ${e.message}", e.stackTraceToString())
        }
    }

    private fun getFileName(uri: Uri): String? {
        return try {
            getApplication<Application>().contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                cursor.moveToFirst()
                cursor.getString(nameIndex)
            }
        } catch (_: Exception) { null }
    }

    fun resetSetupState() {
        _setupState.value = ModelSetupState.Idle
    }

    fun loadModel() {
        val model = _selectedModel.value ?: return
        if (!modelManager.isModelReady(model)) {
            _modelState.value = ModelState.NotImported
            _statusEvent.value = StatusEvent.Error("ملف النموذج غير موجود. يرجى استيراده أولاً.")
            return
        }
        
        if (_modelState.value is ModelState.Loading) return
        
        // If already loaded, don't reload
        if (loadedModelId == model.id && textInferenceEngine?.isReady() == true) {
            _modelState.value = ModelState.Ready
            _statusEvent.value = StatusEvent.Success("نبض مشغّل وجاهز")
            return
        }

        _modelState.value = ModelState.Loading
        _statusEvent.value = StatusEvent.Info("جاري تشغيل نبض...")

        viewModelScope.launch {
            try {
                // Release old resources first
                withContext(Dispatchers.IO) {
                    textInferenceEngine?.unload()
                }
                textInferenceEngine = null
                
                val newEngine = LiteRtLmInferenceEngine()
                val backend = _inferenceBackend.value ?: "cpu"
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        newEngine.load(
                            modelManager.modelPath(model),
                            getApplication<Application>().cacheDir.absolutePath,
                            backend
                        )
                    }
                }

                if (result.isSuccess) {
                    textInferenceEngine = newEngine
                    loadedModelId = model.id
                    localEngineLastErrorMessage = null
                    _modelState.value = ModelState.Ready
                    _statusEvent.value = StatusEvent.Success("تم تشغيل نبض")
                } else {
                    val error = result.exceptionOrNull()
                    localEngineLastErrorMessage = error?.message
                    val userMessage = when (error) {
                        is OutOfMemoryError -> "ذاكرة الجهاز غير كافية لتشغيل هذا النموذج."
                        else -> "حدث خطأ أثناء تحميل النموذج: ${error?.message ?: "خطأ غير معروف"}"
                    }
                    _modelState.value = ModelState.Error(userMessage)
                    _statusEvent.value = StatusEvent.Error(userMessage)
                }
            } catch (e: Exception) {
                localEngineLastErrorMessage = e.message
                _modelState.value = ModelState.Error("خطأ غير متوقع: ${e.message}")
                _statusEvent.value = StatusEvent.Error("فشل تشغيل نبض")
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
            _statusEvent.value = StatusEvent.Info("تم إيقاف تشغيل النموذج")
        }
    }

    fun isEngineReady(): Boolean {
        return textInferenceEngine?.isReady() == true
    }

    fun refreshModelState() {
        val model = _selectedModel.value ?: return
        
        // Debug logging for all supported models
        Log.d("ModelViewModel", "--- Model State Refresh ---")
        ModelManager.SUPPORTED_MODELS.forEach { m ->
            val file = modelManager.modelFile(m)
            Log.d("ModelViewModel", "Model: ${m.id} (${m.displayName})")
            Log.d("ModelViewModel", "  Path: ${file.absolutePath}")
            Log.d("ModelViewModel", "  Exists: ${file.exists()}")
            Log.d("ModelViewModel", "  Size: ${file.length()} bytes")
        }
        Log.d("ModelViewModel", "---------------------------")

        if (modelManager.isModelImported(model.id)) {
            if (isLoaded(model)) {
                _modelState.value = ModelState.Ready
            } else {
                _modelState.value = ModelState.Idle
            }
        } else {
            _modelState.value = ModelState.NotImported
        }
    }

    fun modelImportStatus(model: SupportedModel): String {
        return if (modelManager.isModelImported(model.id)) "مستورد" else "غير مستورد"
    }

    private fun resolveSelectedModel(): SupportedModel {
        val id = preferences.getString(KEY_SELECTED_MODEL_ID, null)
        return ModelManager.SUPPORTED_MODELS.find { it.id == id } ?: ModelManager.SUPPORTED_MODELS[0]
    }

    private fun isLoaded(model: SupportedModel): Boolean {
        if (textInferenceEngine == null) return false
        return textInferenceEngine?.isReady() == true && loadedModelId == model.id
    }

    fun currentModelStatusLabel(): String {
        return when (_modelState.value) {
            is ModelState.Loading -> "جاري التشغيل"
            is ModelState.Ready -> "جاهز"
            is ModelState.Idle -> "غير مشغّل"
            is ModelState.NotImported -> "غير مستورد"
            is ModelState.Error -> "خطأ"
            else -> "غير معروف"
        }
    }

    fun currentRagMode(): RagMode {
        val modeStr = preferences.getString(KEY_RAG_SEARCH_MODE, RagMode.AUTO.name)
        return try { RagMode.valueOf(modeStr!!) } catch (_: Exception) { RagMode.AUTO }
    }

    fun currentEmbeddingBackend(): EmbeddingBackend {
        val backendStr = preferences.getString(KEY_EMBEDDING_BACKEND, EmbeddingBackend.AUTO.name)
        return try { EmbeddingBackend.valueOf(backendStr!!) } catch (_: Exception) { EmbeddingBackend.AUTO }
    }

    fun embeddingModelStatus(): String {
        return if (embeddingModelManager.isEmbeddingModelReady()) "مستورد" else "غير مستورد"
    }

    fun importEmbeddingModel(uri: Uri) {
        viewModelScope.launch {
            _statusEvent.value = StatusEvent.Info("جاري استيراد نموذج التضمين...")
            try {
                withContext(Dispatchers.IO) {
                    embeddingEngine.close()
                    embeddingModelManager.importEmbeddingModel(uri)
                }
                _statusEvent.value = StatusEvent.Success("تم استيراد نموذج التضمين")
            } catch (error: Exception) {
                _statusEvent.value = StatusEvent.Error("فشل استيراد نموذج التضمين: ${error.message}")
            }
        }
    }

    fun deleteEmbeddingModel() {
        viewModelScope.launch {
            val deleted = withContext(Dispatchers.IO) {
                embeddingEngine.close()
                embeddingModelManager.deleteEmbeddingModel()
            }
            _statusEvent.value = if (deleted) {
                StatusEvent.Success("تم حذف نموذج التضمين")
            } else {
                StatusEvent.Error("تعذر حذف نموذج التضمين")
            }
        }
    }

    fun deleteEmbeddingIndexes() {
        viewModelScope.launch {
            val deleted = withContext(Dispatchers.IO) {
                embeddingStore.deleteAllIndexes()
            }
            _statusEvent.value = if (deleted) {
                StatusEvent.Success("تم حذف الفهارس الدلالية")
            } else {
                StatusEvent.Error("تعذر حذف الفهارس الدلالية")
            }
        }
    }

    fun setRagMode(value: String) {
        val mode = when (value.lowercase(Locale.ROOT)) {
            "keyword" -> RagMode.KEYWORD
            "semantic" -> RagMode.SEMANTIC
            else -> RagMode.AUTO
        }
        preferences.edit().putString(KEY_RAG_SEARCH_MODE, mode.name).apply()
        _statusEvent.value = StatusEvent.Success("تم تحديث وضع البحث")
    }

    fun setEmbeddingBackend(value: String) {
        val backend = when (value.lowercase(Locale.ROOT)) {
            "mediapipe" -> EmbeddingBackend.MEDIAPIPE
            "tflite" -> EmbeddingBackend.TFLITE
            else -> EmbeddingBackend.AUTO
        }
        embeddingEngine.close()
        preferences.edit().putString(KEY_EMBEDDING_BACKEND, backend.name).apply()
        _statusEvent.value = StatusEvent.Success("تم تحديث محرك التضمين")
    }

    fun buildSemanticIndex(document: LocalDocument?) {
        if (document == null) {
            _statusEvent.value = StatusEvent.Error("اختر مستندًا أولاً لبناء الفهرس الدلالي.")
            return
        }
        if (!embeddingEngine.isReady()) {
            _statusEvent.value = StatusEvent.Error("استورد نموذج التضمين أولاً.")
            return
        }

        viewModelScope.launch {
            _statusEvent.value = StatusEvent.Info("جاري بناء الفهرس الدلالي...")
            try {
                val indexedChunks = withContext(Dispatchers.Default) {
                    val startTime = SystemClock.elapsedRealtime()
                    val chunks = TextChunker.chunkText(document.extractedText)
                    val results = chunks.mapIndexed { index, chunk ->
                        EmbeddingStore.IndexedChunk(
                            chunkIndex = index,
                            text = chunk,
                            vector = embeddingEngine.embed(chunk)
                        )
                    }
                    val duration = SystemClock.elapsedRealtime() - startTime
                    Log.d("NabdPerformance", "Embedding Indexing: ${chunks.size} chunks in ${duration}ms (${"%.1f".format(duration.toFloat() / chunks.size)}ms/chunk)")
                    results
                }
                withContext(Dispatchers.IO) {
                    embeddingStore.saveIndex(document.id, indexedChunks)
                }
                _statusEvent.value = StatusEvent.Success("تم بناء الفهرس الدلالي")
            } catch (error: Exception) {
                _statusEvent.value = StatusEvent.Error("تعذر بناء الفهرس الدلالي: ${error.message}")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        performanceMonitorJob?.cancel()
        embeddingEngine.close()
        viewModelScope.launch(Dispatchers.IO) {
            textInferenceEngine?.unload()
        }
    }

    companion object {
        private const val KEY_SELECTED_MODEL_ID = "selected_model_id"
        private const val KEY_RAG_SEARCH_MODE = "rag_search_mode"
        private const val KEY_EMBEDDING_BACKEND = "embedding_backend"
        private const val KEY_RESPONSE_MODE = "response_mode"
        private const val KEY_INFERENCE_BACKEND = "inference_backend"
    }
}

data class PerformanceState(
    val ramUsagePercent: Float = 0f,
    val isBenchmarking: Boolean = false,
    val benchmarkResult: String? = null,
    val lastTps: Float = 0f,
    val tpsHistory: List<Float> = emptyList(),
    val ramHistory: List<Float> = emptyList()
)

sealed class ModelState {
    data object NotImported : ModelState()
    data object Idle : ModelState()
    data object Loading : ModelState()
    data object Ready : ModelState()
    data class Error(val message: String) : ModelState()
}

sealed class ModelSetupState {
    data object Idle : ModelSetupState()
    data object Validating : ModelSetupState()
    data object Copying : ModelSetupState()
    data object Loading : ModelSetupState()
    data class Ready(val modelName: String) : ModelSetupState()
    data class Error(val userMessage: String, val technicalMessage: String?) : ModelSetupState()
}
