package com.example.localqwen.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.provider.OpenableColumns
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Observer
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.localqwen.chat.ChatMessage
import com.example.localqwen.chat.ChatSession
import com.example.localqwen.chat.ChatSessionStore
import com.example.localqwen.chat.Role
import com.example.localqwen.data.NabdDatabase
import com.example.localqwen.document.DocumentStore
import com.example.localqwen.document.LocalDocument
import com.example.localqwen.engine.NabdInferenceEngine
import com.example.localqwen.prompt.NabdSystemPrompt
import com.example.localqwen.verification.VerificationClassifier
import com.example.localqwen.verification.VerificationPromptBuilder
import com.example.localqwen.rag.EmbeddingEngine
import com.example.localqwen.rag.EmbeddingStore
import com.example.localqwen.rag.RagMode
import com.example.localqwen.rag.RetrievedChunk
import com.example.localqwen.rag.SemanticRetriever
import com.example.localqwen.rag.TextChunker
import com.example.localqwen.engine.GemmaImageAnalyzer
import com.example.localqwen.work.PdfProcessingWorker
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import com.example.localqwen.data.SecurePreferences
import com.example.localqwen.diagnostics.NabdDiagnosticLogger

import com.example.localqwen.utils.ContextEngineer

import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class ChatState {
    IDLE,
    PREPARING_CONTEXT,
    GENERATING,
    ERROR
}

@Immutable
data class ChatUiState(
    val chatHistory: List<ChatMessage> = emptyList(),
    val state: ChatState = ChatState.IDLE,
    val isProcessingDocument: Boolean = false,
    val statusEvent: StatusEvent? = null,
    val lastErrorReportFile: File? = null
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    application: Application,
    private val modelManager: com.example.localqwen.model.ModelManager,
    private val imageAnalyzerProvider: dagger.Lazy<GemmaImageAnalyzer>
) : AndroidViewModel(application) {

    private var imageAnalyzer: GemmaImageAnalyzer? = null
    
    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    val lastErrorReportFile: File? get() = _uiState.value.lastErrorReportFile

    fun clearErrorReport() {
        _uiState.update { it.copy(lastErrorReportFile = null) }
    }

    fun triggerTestReport() {
        val context = getApplication<Application>()
        val reportFile = NabdDiagnosticLogger.writeGemmaErrorReport(
            context,
            NabdDiagnosticLogger.GemmaErrorContext(
                stage = "manual_test",
                modelPath = "test_path/model.litertlm",
                tempImagePath = "test_path/temp.jpg",
                promptLength = 10,
                exception = Exception("هذا تقرير اختبار يدوي للتأكد من نظام التشخيص")
            ),
            isTest = true
        )
        _uiState.update { it.copy(lastErrorReportFile = reportFile) }
    }

    fun analyzeImageWithGemma(uri: Uri, question: String) {
        val context = getApplication<Application>()
        _uiState.update { 
            it.copy(
                state = ChatState.GENERATING,
                statusEvent = StatusEvent.Info("جاري تحليل الصورة..."),
                lastErrorReportFile = null
            )
        }
        
        // Add the user message immediately
        addChatMessage(ChatMessage(role = Role.USER, text = "[صورة مرفقة]\n$question"))
        val assistantMessage = ChatMessage(role = Role.ASSISTANT, text = "")
        addChatMessage(assistantMessage)
        
        viewModelScope.launch {
            var modelPath = "unknown"
            try {
                if (imageAnalyzer == null) {
                    imageAnalyzer = imageAnalyzerProvider.get()
                    // Ensure the model path is valid. 
                    val model = modelManager.getModelById("gemma_3n_e2b_it")
                    if (model == null || !modelManager.isModelImported(model.id)) {
                        _uiState.update { it.copy(statusEvent = StatusEvent.Error("النموذج المطلوب غير متوفر."), state = ChatState.IDLE) }
                        return@launch
                    }
                    modelPath = modelManager.modelPath(model)
                    imageAnalyzer?.loadModel(modelPath)
                }
                
                val output = StringBuilder()
                withContext(Dispatchers.IO) {
                    var lastUpdate = SystemClock.elapsedRealtime()
                    imageAnalyzer?.analyzeImage(uri, question)?.collect { chunk ->
                        output.append(chunk)
                        val now = SystemClock.elapsedRealtime()
                        if (now - lastUpdate > 32) { // 32ms buffering (approx 30fps)
                            withContext(Dispatchers.Main) {
                                updateAssistantMessageInternal(cleanForDisplay(output.toString()), renderMarkdown = false)
                            }
                            lastUpdate = now
                        }
                    }
                }
                
                lastAssistantResponse = cleanForDisplay(output.toString(), preserveMarkdown = true)
                updateAssistantMessageInternal(lastAssistantResponse, renderMarkdown = true)
                saveActiveSessionDebounced()
                _uiState.update { it.copy(statusEvent = StatusEvent.Success("اكتمل التحليل"), state = ChatState.IDLE) }
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Image analysis failed", e)
                
                // Generate technical report
                val reportFile = NabdDiagnosticLogger.writeGemmaErrorReport(
                    context,
                    NabdDiagnosticLogger.GemmaErrorContext(
                        stage = imageAnalyzer?.currentStage ?: "init",
                        modelPath = modelPath,
                        tempImagePath = imageAnalyzer?.lastTempImagePath,
                        promptLength = question.length,
                        exception = e
                    )
                )
                _uiState.update { it.copy(lastErrorReportFile = reportFile) }
                
                val errorMsg = if (reportFile != null) 
                    "تعذر تشغيل النموذج. تم إنشاء ملف تشخيص يمكنك مشاركته للمساعدة في الإصلاح." 
                    else "حدث خطأ أثناء تحليل الصورة."
                
                _uiState.update { it.copy(statusEvent = StatusEvent.Error(errorMsg), state = ChatState.ERROR) }
                updateAssistantMessageInternal("⚠️ $errorMsg", renderMarkdown = true)
            } finally {
                imageAnalyzer?.unload()
                imageAnalyzer = null
            }
        }
    }


    private val preferences = SecurePreferences.get(application)
    private val db = NabdDatabase.getInstance(application)
    val chatSessionStore = ChatSessionStore(preferences, db)
    val documentStore = DocumentStore(preferences, db)

    private val chatMessages = mutableListOf<ChatMessage>()
    var lastAssistantResponse: String = ""
        private set
    var activeAssistantMessageIndex: Int = -1
        private set

    // Performance metrics
    var lastFirstTokenLatencyMs: Long? = null
        private set
    var lastGenerationDurationMs: Long? = null
        private set
    var lastResponseCharCount: Int = 0
        private set

    private var saveSessionJob: Job? = null
    private var generationJob: Job? = null

    fun loadActiveSession() {
        viewModelScope.launch {
            val session = chatSessionStore.getActiveOrCreateSession()
            chatMessages.clear()
            chatMessages.addAll(loadChatMessages(session.messagesJson))
            lastAssistantResponse = cleanForDisplay(session.lastAssistantResponse, preserveMarkdown = true)
            activeAssistantMessageIndex = chatMessages.indexOfLast { it.role == Role.ASSISTANT }
            session.selectedDocumentId?.let(documentStore::setSelectedDocumentId)
                ?: documentStore.clearSelectedDocumentId()
            _uiState.update { 
                it.copy(
                    chatHistory = chatMessages.toList()
                )
            }
        }
    }

    fun startNewChat() {
        viewModelScope.launch {
            chatMessages.clear()
            lastAssistantResponse = ""
            activeAssistantMessageIndex = -1
            documentStore.clearSelectedDocumentId()
            chatSessionStore.createNewSession()
            _uiState.update { 
                it.copy(
                    chatHistory = emptyList(),
                    statusEvent = StatusEvent.Info("تم إنشاء محادثة جديدة")
                )
            }
        }
    }

    fun switchSession(id: String) {
        viewModelScope.launch {
            chatSessionStore.setActiveSessionId(id)
            loadActiveSession()
        }
    }

    fun addSystemMessage(text: String) {
        addChatMessage(ChatMessage(role = Role.SYSTEM, text = text))
        saveActiveSessionDebounced()
    }

    fun importDocument(uri: Uri) {
        val context = getApplication<Application>()
        val fileName = getFileName(context, uri) ?: "مستند غير معروف"
        val mimeType = context.contentResolver.getType(uri) ?: ""
        
        _uiState.update { 
            it.copy(
                isProcessingDocument = true,
                statusEvent = StatusEvent.Info("جاري معالجة المستند...")
            )
        }

        viewModelScope.launch {
            try {
                // 1. Safe Copy to internal cache using UriFileResolver
                val localPath = com.example.localqwen.utils.UriFileResolver.copyUriToCache(getApplication<Application>(), uri, "doc_")
                val localFile = java.io.File(localPath)

                val localUri = Uri.fromFile(localFile)

                // 2. Identify file type by MIME or extension
                val isPdf = mimeType == "application/pdf" || fileName.endsWith(".pdf", ignoreCase = true)
                val isImage = mimeType.startsWith("image/") || 
                             listOf(".jpg", ".jpeg", ".png", ".webp").any { fileName.endsWith(it, ignoreCase = true) }

                when {
                    isPdf -> {
                        enqueuePdfProcessing(localUri, fileName)
                    }
                    isImage -> {
                        processImageOcr(localFile, fileName)
                        _uiState.update { it.copy(isProcessingDocument = false) }
                    }
                    else -> {
                        // Fallback: Try reading as plain text
                        val text = withContext(Dispatchers.IO) {
                            try { localFile.readText() } catch (_: Exception) { "" }
                        }
                        if (text.isNotBlank()) {
                            saveTextDocument(fileName, text)
                        } else {
                            _uiState.update { it.copy(statusEvent = StatusEvent.Error("صيغة الملف غير مدعومة أو الملف فارغ.")) }
                        }
                        _uiState.update { it.copy(isProcessingDocument = false) }
                    }
                }
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Error importing document", e)
                _uiState.update { 
                    it.copy(
                        statusEvent = StatusEvent.Error("حدث خطأ أثناء معالجة المستند."),
                        isProcessingDocument = false
                    )
                }
            }
        }
    }

    private suspend fun processImageOcr(file: File, title: String) {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val image = InputImage.fromFilePath(getApplication(), Uri.fromFile(file))
            val result = recognizer.process(image).awaitTask()
            val text = result.text.trim()
            
            if (text.isNotBlank()) {
                saveTextDocument("صورة - $title", text, type = "image_ocr")
                _uiState.update { it.copy(statusEvent = StatusEvent.Success("تم استخراج النص من الصورة، يمكنك الآن سؤالي عنها!")) }
            } else {
                _uiState.update { it.copy(statusEvent = StatusEvent.Error("لم أجد نصاً واضحاً داخل الصورة.")) }
            }
        } catch (e: Exception) {
            Log.e("ChatViewModel", "OCR failed", e)
            _uiState.update { it.copy(statusEvent = StatusEvent.Error("فشل استخراج النص من الصورة.")) }
        } finally {
            recognizer.close()
        }
    }

    private suspend fun saveTextDocument(title: String, text: String, type: String = "txt") {
        val document = LocalDocument(
            id = UUID.randomUUID().toString(),
            title = title,
            type = type,
            extractedText = text,
            createdAt = System.currentTimeMillis()
        )
        documentStore.saveDocument(document)
        documentStore.setSelectedDocumentId(document.id)
        if (type == "txt") {
            _uiState.update { it.copy(statusEvent = StatusEvent.Success("تمت إضافة المستند بنجاح: $title")) }
        }
    }

    private suspend fun <T> com.google.android.gms.tasks.Task<T>.awaitTask(): T =
        suspendCancellableCoroutine { cont ->
            addOnSuccessListener { cont.resume(it) }
            addOnFailureListener { cont.resumeWithException(it) }
        }

    private fun enqueuePdfProcessing(uri: Uri, title: String) {
        val context = getApplication<Application>()
        val data = workDataOf(
            PdfProcessingWorker.KEY_PDF_URI to uri.toString(),
            PdfProcessingWorker.KEY_PDF_TITLE to title
        )
        val request = OneTimeWorkRequestBuilder<PdfProcessingWorker>()
            .setInputData(data)
            .build()

        WorkManager.getInstance(context).enqueue(request)
        
        val workInfoLiveData = WorkManager.getInstance(context).getWorkInfoByIdLiveData(request.id)
        val observer = object : Observer<WorkInfo?> {
            override fun onChanged(value: WorkInfo?) {
                if (value == null) return
                if (value.state.isFinished) {
                    _uiState.update { it.copy(isProcessingDocument = false) }
                    if (value.state == WorkInfo.State.SUCCEEDED) {
                        val docId = value.outputData.getString(PdfProcessingWorker.KEY_DOCUMENT_ID)
                        docId?.let { documentStore.setSelectedDocumentId(it) }
                        _uiState.update { it.copy(statusEvent = StatusEvent.Success("تم تجهيز مستند PDF بنجاح، يمكنك الآن سؤالي عنه!")) }
                    } else {
                        val error = value.outputData.getString(PdfProcessingWorker.KEY_ERROR_MESSAGE) ?: "خطأ غير معروف"
                        _uiState.update { it.copy(statusEvent = StatusEvent.Error("عذراً، لم أتمكن من معالجة ملف PDF: $error")) }
                    }
                    workInfoLiveData.removeObserver(this)
                }
            }
        }
        workInfoLiveData.observeForever(observer)
    }

    private fun getFileName(context: Context, uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index != -1) result = cursor.getString(index)
                }
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/') ?: -1
            if (cut != -1) result = result?.substring(cut + 1)
        }
        return result
    }

    private fun readTextFromUri(context: Context, uri: Uri): String {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                input.bufferedReader().use { it.readText() }
            } ?: ""
        } catch (_: Exception) {
            ""
        }
    }

    private fun buildChatHistoryPrompt(history: List<ChatMessage>): String {
        return history.joinToString("\n") { msg ->
            val role = if (msg.role == Role.USER) "أنت" else "نبض"
            val text = if (msg.text.length > 300) msg.text.take(300) + "..." else msg.text
            "$role: $text"
        }
    }

    fun sendMessage(
        input: String,
        engine: NabdInferenceEngine?,
        embeddingEngine: EmbeddingEngine?,
        embeddingStore: EmbeddingStore?,
        semanticRetriever: SemanticRetriever?,
        ragMode: RagMode,
        documentAnswerLengthInstruction: String,
        memoryContext: String = "",
        responseMode: String = "balanced"
    ) {
        if (input.isBlank()) return
        if (_uiState.value.state != ChatState.IDLE) return

        val userMessage = ChatMessage(role = Role.USER, text = input.trim())
        addChatMessage(userMessage)

        val assistantMessage = ChatMessage(role = Role.ASSISTANT, text = "")
        addChatMessage(assistantMessage)

        _uiState.update { it.copy(state = ChatState.PREPARING_CONTEXT) }

        val hasSelectedDocument = documentStore.getSelectedDocumentId() != null
        _uiState.update { 
            it.copy(
                statusEvent = if (hasSelectedDocument) {
                    StatusEvent.Info("جاري تجهيز سياق المستند...")
                } else {
                    StatusEvent.Info("جاري التوليد...")
                }
            )
        }

        generationJob = viewModelScope.launch {
            try {
                // KV cache preserved across turns for maximum context efficiency
                val selectedDocument = getSelectedDocumentAsync()
                val contextResult = try {
                    withContext(Dispatchers.IO) {
                        buildDocumentContext(
                            query = input,
                            document = selectedDocument,
                            ragMode = ragMode,
                            embeddingEngine = embeddingEngine,
                            embeddingStore = embeddingStore,
                            semanticRetriever = semanticRetriever
                        )
                    }
                } catch (_: Exception) {
                    DocumentContextResult(context = null, generationStatus = "جاري التوليد...")
                }

                // 2. Context Engineering: Optimize chat history within token limits
                val baseIdentity = NabdSystemPrompt.baseIdentityPrompt(responseMode)
                val engineeredHistory = ContextEngineer.engineeringContext(
                    systemPrompt = baseIdentity,
                    fullHistory = chatMessages.dropLast(2), // Exclude the new user/assistant pair
                    latestUserMessage = input
                )
                var historyContext = buildChatHistoryPrompt(engineeredHistory)

                // Verification Engine Integration
                val verificationDecision = VerificationClassifier.classify(input)
                val verificationInstruction = if (VerificationPromptBuilder.shouldInjectInstruction(verificationDecision)) {
                    VerificationPromptBuilder.buildInstruction(verificationDecision)
                } else null
                
                Log.d("ChatContext", "Original history size: ${chatMessages.size - 2}")
                Log.d("ChatContext", "Engineered history size: ${engineeredHistory.size}")

                // Store verification decision in the assistant message for UI
                withContext(Dispatchers.Main) {
                    if (activeAssistantMessageIndex != -1 && activeAssistantMessageIndex < chatMessages.size) {
                        chatMessages[activeAssistantMessageIndex] = chatMessages[activeAssistantMessageIndex].copy(
                            verificationLevel = verificationDecision.level,
                            sourceRequirement = verificationDecision.sourceRequirement
                        )
                        _uiState.update { it.copy(chatHistory = chatMessages.toList()) }
                    }
                }

                var prompt = if (contextResult.context != null) {
                    NabdSystemPrompt.documentPrompt(
                        userInput = input,
                        contextChunks = contextResult.context,
                        answerLengthInstruction = documentAnswerLengthInstruction,
                        historyContext = historyContext,
                        responseMode = responseMode,
                        verificationInstruction = verificationInstruction
                    )
                } else {
                    NabdSystemPrompt.normalChatPrompt(
                        userInput = input, 
                        historyContext = historyContext, 
                        memoryContext = memoryContext,
                        responseMode = responseMode,
                        verificationInstruction = verificationInstruction
                    )
                }

                // 3. Prompt Isolation Test: If the prompt is still huge, fallback to isolated input
                // Estimation: 1024 tokens is roughly 3500-4000 characters for mixed content
                if (prompt.length > 4000) {
                    Log.w("NabdSafety", "Final prompt exceeds safety character limit. Isolating current message.")
                    prompt = NabdSystemPrompt.normalChatPrompt(
                        userInput = input,
                        historyContext = "", // Wipe history for this turn
                        memoryContext = "", 
                        responseMode = responseMode,
                        verificationInstruction = verificationInstruction
                    )
                }

                startGeneration(
                    engine = engine,
                    prompt = prompt,
                    status = contextResult.generationStatus,
                    autoTitle = chatMessages.count { it.role == Role.USER } == 1,
                    firstUserMessage = input
                )
            } catch (e: Exception) {
                // Check specifically for Status Code 3 or related buffer errors
                if (e.message?.contains("Status Code: 3") == true || e.message?.contains("too long") == true) {
                    Log.e("NabdCritical", "Context Overflow detected! Retrying with absolute isolation.")
                    try {
                        withContext(Dispatchers.IO) { engine?.resetConversation() }
                        val isolatedPrompt = NabdSystemPrompt.normalChatPrompt(
                            userInput = input,
                            historyContext = "",
                            memoryContext = "",
                            responseMode = responseMode
                        )
                        startGeneration(engine, isolatedPrompt, "إعادة محاولة (عزل السياق)...", false, input)
                    } catch (retryError: Exception) {
                        _uiState.update { it.copy(statusEvent = StatusEvent.Error("خطأ حرج في الذاكرة: ${retryError.message}"), state = ChatState.ERROR) }
                    }
                } else if (e !is kotlinx.coroutines.CancellationException) {
                    _uiState.update { it.copy(statusEvent = StatusEvent.Error("حدث خطأ أثناء التجهيز: ${e.message}"), state = ChatState.ERROR) }
                } else {
                    finishStoppedGeneration()
                }
            }
        }
    }

    fun stopGeneration() {
        val wasBusy = _uiState.value.state == ChatState.GENERATING || _uiState.value.state == ChatState.PREPARING_CONTEXT
        generationJob?.cancel()
        if (wasBusy) {
            _uiState.update { it.copy(state = ChatState.IDLE) }
            finishStoppedGeneration()
        }
    }

    private suspend fun startGeneration(
        engine: NabdInferenceEngine?,
        prompt: String,
        status: String,
        autoTitle: Boolean,
        firstUserMessage: String?
    ) {
        if (engine == null || !engine.isReady()) {
            val message = "تعذر تشغيل النموذج. جرّب نموذجًا أخف أو أعد استيراده."
            updateAssistantMessageInternal(message, renderMarkdown = true)
            lastAssistantResponse = message
            saveActiveSessionDebounced(autoTitle = autoTitle, firstUserMessage = firstUserMessage, immediate = true)
            _uiState.update { it.copy(statusEvent = StatusEvent.Error(message), state = ChatState.ERROR) }
            return
        }

        val generationStartedAt = SystemClock.elapsedRealtime()
        lastFirstTokenLatencyMs = null
        lastGenerationDurationMs = null

        _uiState.update { 
            it.copy(
                state = ChatState.GENERATING,
                statusEvent = StatusEvent.Info(status)
            )
        }

        try {
            val output = StringBuilder()
            var firstChunkCaptured = false
            var tokenCount = 0

            withContext(Dispatchers.IO) {
                var lastUpdate = SystemClock.elapsedRealtime()
                engine.generate(prompt).collect { chunk ->
                    if (chunk.isNotEmpty()) {
                        tokenCount++
                        if (!firstChunkCaptured) {
                            firstChunkCaptured = true
                            lastFirstTokenLatencyMs = SystemClock.elapsedRealtime() - generationStartedAt
                        }

                        output.append(chunk)
                        
                        val now = SystemClock.elapsedRealtime()
                        if (now - lastUpdate > 32) { // Buffer tokens for 32ms (~30 FPS max updates)
                            // Perform heavy string processing on IO thread before switching to Main
                            val cleaned = cleanForDisplay(output.toString(), preserveMarkdown = false)
                            withContext(Dispatchers.Main) {
                                updateAssistantMessageInternal(cleaned, renderMarkdown = false)
                            }
                            lastUpdate = now
                        }
                    }
                }
            }

            val finalRaw = output.toString().trim()
            
            if (finalRaw.startsWith("{") && finalRaw.endsWith("}")) {
                try {
                    val jsonObj = org.json.JSONObject(finalRaw)
                    if (jsonObj.has("tool") && jsonObj.getString("tool") == "phone") {
                        val intent = jsonObj.optString("intent", "")
                        val phoneManager = com.example.localqwen.tools.PhoneToolManager(getApplication())
                        val result = when(intent) {
                            "battery" -> phoneManager.getBatteryStatus().content
                            "device_info" -> phoneManager.getDeviceInfo().content
                            "storage" -> phoneManager.getStorageInfo().content
                            "installed_apps" -> phoneManager.getInstalledAppsCount().content
                            else -> "إجراء غير معروف: $intent"
                        }
                        
                        val systemResponse = "🔧 **نتيجة أداة النظام:**\n$result"
                        withContext(Dispatchers.Main) {
                            updateAssistantMessageInternal(systemResponse, renderMarkdown = true)
                        }
                        
                        lastAssistantResponse = systemResponse
                        lastResponseCharCount = systemResponse.length
                        lastGenerationDurationMs = SystemClock.elapsedRealtime() - generationStartedAt
                        saveActiveSessionDebounced(autoTitle = autoTitle, firstUserMessage = firstUserMessage, immediate = true)
                        _uiState.update { it.copy(statusEvent = StatusEvent.Success("تم التنفيذ بنجاح"), state = ChatState.IDLE) }
                        return
                    }
                } catch (e: Exception) {
                    android.util.Log.e("ChatViewModel", "Failed to parse tool JSON", e)
                }
            }
            
            val finalText = cleanForDisplay(finalRaw, preserveMarkdown = true).ifBlank { 
                if (finalRaw.isNotBlank()) finalRaw.trim() else "(لم يتم توليد رد)"
            }
            
            lastGenerationDurationMs = SystemClock.elapsedRealtime() - generationStartedAt
            
            val elapsedMs = lastGenerationDurationMs ?: 0L
            val ttftMs = lastFirstTokenLatencyMs ?: 0L
            
            val decodeTimeMs = maxOf(1L, elapsedMs - ttftMs)
            val decodeSeconds = decodeTimeMs / 1000.0
            
            val calculatedTps = if (decodeSeconds > 0.0 && tokenCount > 0) tokenCount / decodeSeconds else 0.0
            
            android.util.Log.d("ChatTPS", "tokens=$tokenCount totalMs=$elapsedMs ttftMs=$ttftMs decodeMs=$decodeTimeMs decodeTps=$calculatedTps")

            withContext(Dispatchers.Main) {
                updateAssistantMessageInternal(finalText, renderMarkdown = true, tps = if (calculatedTps > 0) calculatedTps else null)
            }
            
            lastAssistantResponse = finalText
            lastResponseCharCount = finalText.length

            saveActiveSessionDebounced(autoTitle = autoTitle, firstUserMessage = firstUserMessage, immediate = true)
            _uiState.update { it.copy(statusEvent = StatusEvent.Success("جاهز"), state = ChatState.IDLE) }
        } catch (cancelled: CancellationException) {
            Log.i("NabdInference", "Generation cancelled by user")
            withContext(Dispatchers.Main) {
                finishStoppedGeneration()
            }
        } catch (e: Exception) {
            Log.e("NabdInference", "Generation failed", e)
            lastGenerationDurationMs = SystemClock.elapsedRealtime() - generationStartedAt
            saveActiveSessionDebounced(autoTitle = autoTitle, firstUserMessage = firstUserMessage, immediate = true)
            _uiState.update { it.copy(statusEvent = StatusEvent.Error("حدث خطأ أثناء توليد الرد: ${e.message}"), state = ChatState.ERROR) }
        }
    }

    private fun finishStoppedGeneration() {
        val currentText = chatMessages.getOrNull(activeAssistantMessageIndex)?.text.orEmpty().trim()
        if (currentText.isBlank() && activeAssistantMessageIndex in chatMessages.indices) {
            val message = "تم إيقاف التوليد."
            updateAssistantMessageInternal(message, renderMarkdown = true)
            lastAssistantResponse = message
        } else if (currentText.isNotBlank()) {
            lastAssistantResponse = currentText
        }
        _uiState.update { 
            it.copy(
                state = ChatState.IDLE,
                statusEvent = StatusEvent.Info("تم إيقاف التوليد")
            )
        }
        saveActiveSessionDebounced(immediate = true)
    }

    private fun addChatMessage(message: ChatMessage) {
        val normalized = if (message.text.isBlank()) message
        else message.copy(text = normalizeMessageText(message.role, message.text))

        chatMessages.add(normalized)
        if (normalized.role == Role.ASSISTANT && normalized.text.isNotBlank()) {
            lastAssistantResponse = normalized.text
        }
        if (normalized.role == Role.ASSISTANT) {
            activeAssistantMessageIndex = chatMessages.lastIndex
        }
        _uiState.update { it.copy(chatHistory = chatMessages.toList()) }
    }

    private fun updateAssistantMessageInternal(text: String, renderMarkdown: Boolean, tps: Double? = null) {
        if (activeAssistantMessageIndex !in chatMessages.indices) return
        chatMessages[activeAssistantMessageIndex] = chatMessages[activeAssistantMessageIndex].copy(
            text = text,
            tps = tps ?: chatMessages[activeAssistantMessageIndex].tps
        )
        // Note: isStreaming is implicit now based on ChatState.GENERATING, we just update history
        _uiState.update { 
            it.copy(
                chatHistory = chatMessages.toList()
            )
        }
    }

    fun saveActiveSessionDebounced(
        autoTitle: Boolean = false,
        firstUserMessage: String? = null,
        immediate: Boolean = false
    ) {
        saveSessionJob?.cancel()
        val snapshotMessages = chatMessages.toList()
        val snapshotResponse = lastAssistantResponse
        val selectedDocId = documentStore.getSelectedDocumentId()

        saveSessionJob = viewModelScope.launch {
            if (!immediate) delay(250)
            val messagesText = withContext(Dispatchers.Default) {
                buildMessagesText(snapshotMessages)
            }
            chatSessionStore.updateActiveSession(
                messagesJson = messagesText,
                lastAssistantResponse = snapshotResponse,
                selectedDocumentId = selectedDocId,
                documentAnswerLength = currentDocumentAnswerLength(),
                autoTitle = autoTitle,
                firstUserMessage = firstUserMessage
            )
        }
    }

    suspend fun getSelectedDocumentAsync(): LocalDocument? {
        return documentStore.getDocument(documentStore.getSelectedDocumentId())
    }

    fun currentDocumentAnswerLength(): String {
        return preferences.getString("document_answer_length", "short") ?: "short"
    }

    fun setDocumentAnswerLength(value: String) {
        val safeValue = when (value.lowercase()) {
            "medium", "long" -> value.lowercase()
            else -> "short"
        }
        preferences.edit().putString("document_answer_length", safeValue).apply()
        saveActiveSessionDebounced(immediate = true)
    }

    fun clearSelectedDocument() {
        documentStore.clearSelectedDocumentId()
        saveActiveSessionDebounced(immediate = true)
        _uiState.update { it.copy(statusEvent = StatusEvent.Info("تم إلغاء تحديد المستند")) }
    }

    fun getCurrentMessages(): List<ChatMessage> = chatMessages.toList()

    // --- Private helpers ---

    private fun buildDocumentContext(
        query: String,
        document: LocalDocument?,
        ragMode: RagMode,
        embeddingEngine: EmbeddingEngine?,
        embeddingStore: EmbeddingStore?,
        semanticRetriever: SemanticRetriever?
    ): DocumentContextResult {
        if (document == null) return DocumentContextResult(null, "جاري التوليد...")

        val keywordResults = { retrieveKeywordChunks(document, query) }

        val chunks = when (ragMode) {
            RagMode.KEYWORD -> keywordResults()
            RagMode.AUTO -> {
                val canSemantic = embeddingEngine?.isReady() == true
                        && embeddingStore?.hasIndex(document.id) == true
                if (canSemantic) {
                    val semantic = semanticRetriever?.retrieveSemantic(document.id, query)
                        ?.map { it.copy(documentTitle = document.title) }
                        ?: emptyList()
                    semantic.ifEmpty { keywordResults() }
                } else {
                    keywordResults()
                }
            }
            RagMode.SEMANTIC -> {
                val semantic = semanticRetriever?.retrieveSemantic(document.id, query)
                    ?.map { it.copy(documentTitle = document.title) }
                    ?: emptyList()
                semantic.ifEmpty { keywordResults() }
            }
        }

        if (chunks.isEmpty()) return DocumentContextResult(null, "جاري التوليد...")

        val contextText = chunks.joinToString("\n---\n") { it.text }
        return DocumentContextResult(context = contextText, generationStatus = "جاري التوليد...")
    }

    private fun retrieveKeywordChunks(document: LocalDocument, query: String): List<RetrievedChunk> {
        val words = query.lowercase().split(Regex("\\s+")).filter { it.length >= 2 }.toSet()
        val textChunks = chunkText(document.extractedText.take(DOCUMENT_TEXT_LIMIT))

        val scored = textChunks.mapIndexed { index, chunk ->
            val lowered = chunk.lowercase()
            RetrievedChunk(
                documentId = document.id,
                documentTitle = document.title,
                chunkIndex = index,
                text = chunk,
                score = words.sumOf { word -> if (lowered.contains(word)) 1 else 0 as Int }
            )
        }.sortedByDescending { it.score }

        if (scored.isEmpty()) return emptyList()
        if (words.isEmpty()) return scored.take(MAX_RETRIEVED_CHUNKS)
        return scored.filter { it.score > 0 }.take(MAX_RETRIEVED_CHUNKS).ifEmpty { scored.take(MAX_RETRIEVED_CHUNKS) }
    }

    private fun chunkText(text: String, max: Int = DOCUMENT_CHUNK_SIZE): List<String> {
        val normalized = text.trim()
        if (normalized.isEmpty()) return emptyList()

        val chunks = mutableListOf<String>()
        val current = StringBuilder()

        normalized.split(Regex("\\n\\s*\\n")).filter { it.isNotBlank() }.forEach { paragraph ->
            if (paragraph.length > max) {
                if (current.isNotEmpty()) {
                    chunks.add(current.toString().trim())
                    current.clear()
                }
                var start = 0
                while (start < paragraph.length) {
                    val end = (start + max).coerceAtMost(paragraph.length)
                    chunks.add(paragraph.substring(start, end).trim())
                    start = end
                }
            } else {
                if (current.length + paragraph.length + 2 > max && current.isNotEmpty()) {
                    chunks.add(current.toString().trim())
                    current.clear()
                }
                if (current.isNotEmpty()) current.append("\n\n")
                current.append(paragraph)
            }
        }
        if (current.isNotEmpty()) chunks.add(current.toString().trim())
        return chunks
    }

    private fun loadChatMessages(rawMessages: String): List<ChatMessage> {
        parseLegacyJsonMessages(rawMessages)?.let { return it }
        return parseMessagesText(rawMessages)
    }

    private fun parseLegacyJsonMessages(raw: String): List<ChatMessage>? {
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val arr = org.json.JSONArray(raw)
            List(arr.length()) { i ->
                val obj = arr.getJSONObject(i)
                val role = runCatching { Role.valueOf(obj.optString("role", Role.ASSISTANT.name)) }
                    .getOrDefault(Role.ASSISTANT)
                ChatMessage(
                    role = role,
                    text = normalizeMessageText(role, obj.optString("text", "")),
                    timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                )
            }
        }.getOrNull()
    }

    private fun parseMessagesText(messagesText: String): List<ChatMessage> {
        val normalized = messagesText.trim()
        if (normalized.isBlank()) return emptyList()

        return runCatching {
            val messages = mutableListOf<ChatMessage>()
            val headerRegex = Regex("^(أنت|نبض|النظام):\\s*(.*)$")
            var currentRole: Role? = null
            val buffer = mutableListOf<String>()

            fun flush() {
                val role = currentRole ?: return
                messages.add(ChatMessage(role = role, text = normalizeMessageText(role, buffer.joinToString("\n").trim())))
                buffer.clear()
            }

            normalized.lines().forEach { line ->
                val match = headerRegex.matchEntire(line.trim())
                if (match != null) {
                    flush()
                    currentRole = when (match.groupValues[1]) {
                        "أنت" -> Role.USER
                        "نبض" -> Role.ASSISTANT
                        else -> Role.SYSTEM
                    }
                    val inline = match.groupValues[2].trim()
                    if (inline.isNotEmpty()) buffer.add(inline)
                } else {
                    buffer.add(line)
                }
            }
            flush()
            messages.ifEmpty { listOf(ChatMessage(role = Role.SYSTEM, text = cleanForDisplay(normalized))) }
        }.getOrElse {
            listOf(ChatMessage(role = Role.SYSTEM, text = cleanForDisplay(normalized)))
        }
    }

    private fun buildMessagesText(messages: List<ChatMessage>): String {
        return messages.joinToString("\n\n") { msg ->
            val label = when (msg.role) {
                Role.USER -> "أنت"
                Role.ASSISTANT -> "نبض"
                Role.SYSTEM -> "النظام"
            }
            "$label:\n${msg.text.trim()}"
        }.trim()
    }

    private fun normalizeMessageText(role: Role, text: String): String {
        return when (role) {
            Role.USER -> text.trim()
            Role.ASSISTANT -> cleanForDisplay(text, preserveMarkdown = true)
            Role.SYSTEM -> cleanForDisplay(text)
        }
    }

    private fun cleanForDisplay(text: String, preserveMarkdown: Boolean = false): String {
        if (text.isBlank()) return text.trim()

        val withoutThinking = text
            .replace(Regex("(?is)<think>.*?</think>"), "")
            .replace("<think>", "")
            .replace("</think>", "")
            .replace(Regex("<\\|[^>]+\\|>"), "")

        val normalizedLines = withoutThinking.lines().map { line ->
            if (preserveMarkdown) {
                line.trimEnd()
            } else {
                line.replace("**", "").replace("__", "").replace("*", "").replace("`", "")
                    .replace(Regex("^\\s*#{1,4}\\s*"), "")
                    .replace(Regex("^\\s*[\\*-]\\s+"), "• ")
                    .replace(Regex("^\\s*•\\s+"), "• ")
                    .replace(Regex("^\\s*\\d+\\.\\s+"), "• ")
                    .replace(Regex("[ \\t]+"), " ")
                    .trimEnd()
            }
        }.joinToString("\n")

        return normalizedLines
            .replace(Regex("\n\\s*\n(?:\\s*\n)+"), "\n\n")
            .replace(Regex("[ \\t]+\n"), "\n")
            .trim()
    }

    override fun onCleared() {
        super.onCleared()
        saveSessionJob?.cancel()
        generationJob?.cancel()
        imageAnalyzer?.close()
    }

    companion object {
        private const val STREAM_UPDATE_MIN_CHARS = 24
        private const val DOCUMENT_TEXT_LIMIT = 100_000
        private const val DOCUMENT_CHUNK_SIZE = 500
        private const val MAX_RETRIEVED_CHUNKS = 2
    }
}

data class DocumentContextResult(
    val context: String?,
    val generationStatus: String
)

sealed class StatusEvent {
    data class Info(val message: String) : StatusEvent()
    data class Success(val message: String) : StatusEvent()
    data class Error(val message: String) : StatusEvent()
}

data class StreamingUpdate(
    val index: Int,
    val isStreaming: Boolean
)
