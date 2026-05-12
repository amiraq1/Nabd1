package com.example.localqwen

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.os.Build
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.content.res.ColorStateList
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.localqwen.attachments.PdfMessageFormatter
import com.example.localqwen.chat.ChatAdapter
import com.example.localqwen.chat.ChatMessage
import com.example.localqwen.chat.ChatSession
import com.example.localqwen.chat.ChatSessionStore
import com.example.localqwen.chat.Role
import com.example.localqwen.diagnostics.BetaReportBuilder
import com.example.localqwen.document.DocumentExtractionProcessor
import com.example.localqwen.document.DocumentMessageFormatter
import com.example.localqwen.document.PdfSettings
import com.example.localqwen.document.DocumentStore
import com.example.localqwen.document.DocumentToolIntent
import com.example.localqwen.document.DocumentToolRequest
import com.example.localqwen.document.DocumentToolRouter
import com.example.localqwen.document.LocalDocument
import com.example.localqwen.memory.MemoryExtractor
import com.example.localqwen.memory.MemoryPromptBuilder
import com.example.localqwen.memory.MemoryStore
import com.example.localqwen.rag.EmbeddingBackend
import com.example.localqwen.rag.EmbeddingEngine
import com.example.localqwen.model.ModelManager
import com.example.localqwen.model.ModelManager.SupportedModel
import com.example.localqwen.prompt.NabdSystemPrompt
import com.example.localqwen.rag.EmbeddingModelManager
import com.example.localqwen.rag.EmbeddingStore
import com.example.localqwen.diagnostics.LiteRtDiagnosticsData
import com.example.localqwen.diagnostics.LiteRtDiagnosticsFormatter
import com.example.localqwen.diagnostics.LocalModelDiagnosticsFormatter
import com.example.localqwen.rag.TextChunker
import com.example.localqwen.rag.RagMode
import com.example.localqwen.rag.RetrievedChunk
import com.example.localqwen.rag.SemanticRetriever
import com.example.localqwen.tools.PhoneToolIntent
import com.example.localqwen.tools.PhoneToolManager
import com.example.localqwen.tools.PhoneToolResult
import com.example.localqwen.tools.PhoneToolRouter
import com.example.localqwen.ui.NabdErrorMessages
import com.example.localqwen.viewmodel.ChatViewModel
import com.example.localqwen.viewmodel.ModelViewModel
import com.example.localqwen.viewmodel.ModelState
import com.example.localqwen.viewmodel.StatusEvent
import com.example.localqwen.work.PdfProcessingWorker
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import androidx.activity.viewModels
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class MainActivity : AppCompatActivity() {
    // --- ViewModels ---
    private val chatViewModel: ChatViewModel by viewModels()
    private val modelViewModel: ModelViewModel by viewModels()

    private lateinit var statusView: TextView
    private lateinit var rvChatMessages: RecyclerView
    private lateinit var chatAdapter: ChatAdapter
    private lateinit var typingIndicatorView: TextView
    private lateinit var inputView: EditText
    private lateinit var attachButton: Button
    private lateinit var optionsButton: Button
    private lateinit var sendButton: Button
    private lateinit var sendProgressBar: ProgressBar
    private lateinit var modelManager: ModelManager
    private lateinit var documentStore: DocumentStore
    private lateinit var chatSessionStore: ChatSessionStore
    private lateinit var phoneToolManager: PhoneToolManager
    private lateinit var savedPlacesStore: com.example.localqwen.tools.SavedPlacesStore
    private lateinit var embeddingModelManager: EmbeddingModelManager
    private lateinit var embeddingEngine: EmbeddingEngine
    private lateinit var embeddingStore: EmbeddingStore
    private lateinit var semanticRetriever: SemanticRetriever
    private lateinit var memoryStore: MemoryStore

    private val pickModelRequestCode = 200
    private val pickImageRequestCode = 201
    private val pickPdfRequestCode = 202
    private val settingsRequestCode = 203
    private val pickEmbeddingModelRequestCode = 204
    private val pickVisionModelRequestCode = 205
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val chatMessages = mutableListOf<ChatMessage>()
    private val supportedModels = ModelManager.SUPPORTED_MODELS
    private val preferences by lazy { getSharedPreferences("nabd_prefs", MODE_PRIVATE) }
    private val workManager by lazy { WorkManager.getInstance(applicationContext) }
    private val modelLock = Any()
    private val handledPdfWorkIds = mutableSetOf<UUID>()
    private var backgroundTasksDialog: AlertDialog? = null
    private var localModelManagerDialog: AlertDialog? = null
    private var localModelManagerContentContainer: LinearLayout? = null
    private var localModelManagerQuickTestJob: Job? = null
    private var localModelManagerLastQuickTestMessage = "لم يتم تشغيل أي اختبار بعد."
    private var backgroundTasksPdfContainer: LinearLayout? = null
    private var backgroundTasksSemanticContainer: LinearLayout? = null
    private var backgroundTasksEmptyStateView: TextView? = null
    private var backgroundTasksLatestPdfWorkInfos: List<WorkInfo> = emptyList()
    private var backgroundTasksPdfWorkLiveData: LiveData<List<WorkInfo>>? = null
    private var backgroundTasksPdfObserver: Observer<List<WorkInfo>>? = null

    private var selectedModel: SupportedModel = supportedModels.first()
    private var loadedModelId: String? = null
    private var lastAssistantResponse: String = ""
    private var activeAssistantMessageIndex: Int = -1
    private var saveSessionJob: Job? = null
    private var lastGenerationStartedAtElapsedMs: Long? = null
    private var lastGenerationFinishedAtElapsedMs: Long? = null
    private var lastResponseCharCount: Int = 0
    private var lastFirstTokenLatencyMs: Long? = null
    private var lastGenerationDurationMs: Long? = null

    private var textInferenceEngine: com.example.localqwen.engine.NabdInferenceEngine? = null

    private var isLoadingModel = false
    private var isGenerating = false
    private var isProcessingFile = false
    private var isPreparingDocumentContext = false
    private var semanticIndexingStatus = SEMANTIC_INDEX_STATUS_IDLE
    private var semanticIndexingDocumentTitle: String? = null
    private var semanticIndexingStartedAt: Long? = null
    private var semanticIndexingLastMessage = "لا توجد عمليات فهرسة حالية."
    private var lastRagOperation: LastRagOperationResult? = null
    private var localEngineLastErrorMessage: String? = null
    private var sendLoadingAnimator: ObjectAnimator? = null
    private var typingPulseAnimator: ObjectAnimator? = null
    private var pendingAttachDialogRunnable: Runnable? = null
    private val microInteractionInterpolator = AccelerateDecelerateInterpolator()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        migrateDataIfNeeded()
        val db = com.example.localqwen.data.NabdDatabase.getInstance(this)
        modelManager = ModelManager(this)
        documentStore = DocumentStore(preferences, db)
        chatSessionStore = ChatSessionStore(preferences, db)
        phoneToolManager = PhoneToolManager(this)
        savedPlacesStore = com.example.localqwen.tools.SavedPlacesStore(this)
        embeddingModelManager = EmbeddingModelManager(this)
        embeddingEngine = EmbeddingEngine(
            context = this,
            embeddingModelManager = embeddingModelManager,
            backendSelector = ::currentEmbeddingBackend
        )
        embeddingStore = EmbeddingStore(this)
        semanticRetriever = SemanticRetriever(embeddingEngine, embeddingStore)
        memoryStore = MemoryStore(this)
        chatAdapter = ChatAdapter(this)

        statusView = findViewById(R.id.statusTextView)
        rvChatMessages = findViewById(R.id.rvChatMessages)
        typingIndicatorView = findViewById(R.id.tvTypingIndicator)
        inputView = findViewById(R.id.etMessage)
        attachButton = findViewById(R.id.btnAttachFile)
        optionsButton = findViewById(R.id.optionsButton)
        sendButton = findViewById(R.id.btnSend)
        sendProgressBar = findViewById(R.id.sendProgressBar)

        rvChatMessages.layoutManager = LinearLayoutManager(this)
        rvChatMessages.adapter = chatAdapter
        rvChatMessages.itemAnimator = null

        selectedModel = supportedModels.find {
            it.id == preferences.getString(KEY_SELECTED_MODEL_ID, null)
        } ?: supportedModels.first()

        migrateOldHistoryIfNeeded()
        loadActiveSession()

        optionsButton.setOnClickListener { showOptionsBottomSheet() }
        attachButton.setOnClickListener {
            if (!attachButton.isEnabled) return@setOnClickListener
            animateButtonPress(attachButton)
            pendingAttachDialogRunnable?.let(attachButton::removeCallbacks)
            pendingAttachDialogRunnable = Runnable {
                pendingAttachDialogRunnable = null
                if (!isFinishing && !isDestroyed) {
                    showAttachmentTypeDialog()
                }
            }
            pendingAttachDialogRunnable?.let {
                attachButton.postDelayed(it, ATTACH_BUTTON_PRESS_DURATION_MS)
            }
        }
        sendButton.setOnClickListener { sendPrompt() }

        inputView.addTextChangedListener(SimpleTextWatcher { updateButtons() })
        updateButtons()
        showOnboardingIfNeeded()
    }

    override fun onDestroy() {
        backgroundTasksDialog?.dismiss()
        clearBackgroundTasksDialogRefs()
        detachBackgroundTasksObserver()
        localModelManagerDialog?.dismiss()
        clearLocalModelManagerDialogRefs()
        localModelManagerQuickTestJob?.cancel()
        localModelManagerQuickTestJob = null
        pendingAttachDialogRunnable?.let(attachButton::removeCallbacks)
        pendingAttachDialogRunnable = null
        attachButton.animate().cancel()
        attachButton.scaleX = 1f
        attachButton.scaleY = 1f
        stopSendLoadingAnimation()
        stopTypingPulse()
        saveSessionJob?.cancel()
        embeddingEngine.close()
        closeModelResources()
        scope.cancel()
        super.onDestroy()
    }

    private fun migrateOldHistoryIfNeeded() {
        if (preferences.getString("chat_sessions_json", null) != null) return
        val oldJson = preferences.getString(KEY_CHAT_MESSAGES_JSON, null)
        if (!oldJson.isNullOrBlank()) {
            val session = ChatSession(
                title = "محادثة سابقة",
                messagesJson = oldJson,
                lastAssistantResponse = preferences.getString(KEY_CHAT_HISTORY_TEXT, "") ?: ""
            )
            scope.launch { chatSessionStore.saveSession(session) }
            chatSessionStore.setActiveSessionId(session.id)
        }
    }

    private fun migrateDataIfNeeded() {
        val isMigrated = preferences.getBoolean("room_migration_done", false)
        if (isMigrated) return

        kotlinx.coroutines.runBlocking {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val db = com.example.localqwen.data.NabdDatabase.getInstance(this@MainActivity)
                    
                    // Migrate Documents
                    val docsJson = preferences.getString("local_documents_json", null)
                    if (!docsJson.isNullOrBlank()) {
                        val arr = org.json.JSONArray(docsJson)
                        for (i in 0 until arr.length()) {
                            val obj = arr.getJSONObject(i)
                            db.localDocumentDao().insertOrUpdate(
                                com.example.localqwen.data.LocalDocumentEntity(
                                    obj.optString("id"),
                                    obj.optString("title"),
                                    obj.optString("type"),
                                    obj.optString("extractedText"),
                                    obj.optLong("createdAt", System.currentTimeMillis())
                                )
                            )
                        }
                    }

                    // Migrate Sessions
                    val sessionsJson = preferences.getString("chat_sessions_json", null)
                    if (!sessionsJson.isNullOrBlank()) {
                        val arr = org.json.JSONArray(sessionsJson)
                        for (i in 0 until arr.length()) {
                            val obj = arr.getJSONObject(i)
                            db.chatSessionDao().insertOrUpdate(
                                com.example.localqwen.data.ChatSessionEntity(
                                    obj.getString("id"),
                                    obj.getString("title"),
                                    obj.getLong("createdAt"),
                                    obj.getLong("updatedAt"),
                                    obj.optString("messagesJson", "[]"),
                                    obj.optString("lastAssistantResponse", ""),
                                    obj.optString("selectedDocumentId", null).takeIf { it != "null" },
                                    obj.optString("documentAnswerLength", "short")
                                )
                            )
                        }
                    }

                    preferences.edit().putBoolean("room_migration_done", true).commit()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun loadActiveSession() {
        scope.launch {
            val session = chatSessionStore.getActiveOrCreateSession()
            chatMessages.clear()
            chatMessages.addAll(loadChatMessages(session.messagesJson))
            lastAssistantResponse = cleanForDisplay(session.lastAssistantResponse, preserveMarkdown = true)
            activeAssistantMessageIndex = chatMessages.indexOfLast { it.role == Role.ASSISTANT }
            session.selectedDocumentId?.let(documentStore::setSelectedDocumentId) ?: documentStore.clearSelectedDocumentId()
            renderChatHistory()
            setStatusInfo(currentStatus())
        }
    }

    private fun loadChatMessages(rawMessages: String): MutableList<ChatMessage> {
        parseLegacyJsonMessages(rawMessages)?.let { return it }
        return parseMessagesTextToChatMessages(rawMessages)
    }

    private fun parseLegacyJsonMessages(rawMessages: String): MutableList<ChatMessage>? {
        if (rawMessages.isBlank()) return mutableListOf()

        return runCatching {
            val historyJson = JSONArray(rawMessages)
            MutableList(historyJson.length()) { index ->
                val item = historyJson.getJSONObject(index)
                val role = runCatching {
                    Role.valueOf(item.optString("role", Role.ASSISTANT.name))
                }.getOrDefault(Role.ASSISTANT)
                ChatMessage(
                    role = role,
                    text = normalizeMessageText(role, item.optString("text", "")),
                    timestamp = item.optLong("timestamp", System.currentTimeMillis())
                )
            }
        }.getOrNull()
    }

    private fun parseMessagesTextToChatMessages(messagesText: String): MutableList<ChatMessage> {
        val normalized = messagesText.trim()
        if (normalized.isBlank()) return mutableListOf()

        return runCatching {
            val messages = mutableListOf<ChatMessage>()
            val headerRegex = Regex("^(أنت|نبض|النظام):\\s*(.*)$")
            var currentRole: Role? = null
            val buffer = mutableListOf<String>()

            fun flushCurrent() {
                val role = currentRole ?: return
                val text = normalizeMessageText(role, buffer.joinToString("\n").trim())
                messages.add(
                    ChatMessage(
                        role = role,
                        text = text,
                        timestamp = System.currentTimeMillis()
                    )
                )
                buffer.clear()
            }

            normalized.lines().forEach { line ->
                val match = headerRegex.matchEntire(line.trim())
                if (match != null) {
                    flushCurrent()
                    currentRole = when (match.groupValues[1]) {
                        "أنت" -> Role.USER
                        "نبض" -> Role.ASSISTANT
                        else -> Role.SYSTEM
                    }
                    val inlineText = match.groupValues[2].trim()
                    if (inlineText.isNotEmpty()) {
                        buffer.add(inlineText)
                    }
                } else {
                    buffer.add(line)
                }
            }

            flushCurrent()

            if (messages.isEmpty()) {
                mutableListOf(
                    ChatMessage(
                        role = Role.SYSTEM,
                        text = cleanForDisplay(normalized),
                        timestamp = System.currentTimeMillis()
                    )
                )
            } else {
                messages
            }
        }.getOrElse {
            mutableListOf(
                ChatMessage(
                    role = Role.SYSTEM,
                    text = cleanForDisplay(normalized),
                    timestamp = System.currentTimeMillis()
                )
            )
        }
    }

    private fun buildMessagesTextFromChatMessages(messages: List<ChatMessage>): String {
        return messages.joinToString("\n\n") { message ->
            val label = when (message.role) {
                Role.USER -> "أنت"
                Role.ASSISTANT -> "نبض"
                Role.SYSTEM -> "النظام"
            }
            "$label:\n${message.text.trim()}"
        }.trim()
    }

    private fun startNewChat() {
        chatMessages.clear()
        lastAssistantResponse = ""
        activeAssistantMessageIndex = -1
        chatAdapter.clearMessages()
        documentStore.clearSelectedDocumentId()
        scope.launch { chatSessionStore.createNewSession() }
        setStatusInfo("تم إنشاء محادثة جديدة")
        updateButtons()
    }

    private fun switchSession(id: String) {
        chatSessionStore.setActiveSessionId(id)
        loadActiveSession()
        updateButtons()
    }

    private fun currentStatus(): String {
        return when {
            textInferenceEngine?.isReady() == true && loadedModelId == selectedModel.id -> "${selectedModel.displayName} — جاهز"
            modelManager.isModelReady(selectedModel) -> "${selectedModel.displayName} — غير مشغّل"
            else -> "${selectedModel.displayName} — غير مستورد"
        }
    }

    private fun currentModelStatusLabel(): String {
        return when {
            isLoadingModel -> "جاري التشغيل"
            textInferenceEngine?.isReady() == true && loadedModelId == selectedModel.id -> "مشغّل"
            modelManager.isModelReady(selectedModel) -> "غير مشغّل"
            else -> "غير مستورد"
        }
    }

    private fun updateButtons() {
        val hasInput = inputView.text?.toString()?.trim()?.isNotEmpty() == true
        val busy = isGenerating || isProcessingFile || isPreparingDocumentContext
        val blocked = busy || isLoadingModel

        optionsButton.isEnabled = !blocked
        attachButton.isEnabled = !blocked
        inputView.isEnabled = !blocked
        sendButton.isEnabled = hasInput && !blocked

        sendButton.alpha = if (isGenerating || sendButton.isEnabled) 1.0f else 0.45f
        attachButton.alpha = if (attachButton.isEnabled) 1.0f else 0.5f
        attachButton.text = "+"
        if (isGenerating) {
            startSendLoadingAnimation()
            startTypingPulse()
        } else {
            stopSendLoadingAnimation()
            stopTypingPulse()
        }
        sendProgressBar.visibility = if (blocked && !isGenerating) View.VISIBLE else View.GONE
    }

    private fun animateButtonPress(view: View) {
        view.animate().cancel()
        view.scaleX = 1f
        view.scaleY = 1f
        view.animate()
            .scaleX(0.92f)
            .scaleY(0.92f)
            .setDuration(ATTACH_BUTTON_PRESS_DURATION_MS / 2)
            .setInterpolator(microInteractionInterpolator)
            .withEndAction {
                view.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(ATTACH_BUTTON_PRESS_DURATION_MS / 2)
                    .setInterpolator(microInteractionInterpolator)
                    .start()
            }
            .start()
    }

    private fun startSendLoadingAnimation() {
        sendButton.text = "…"
        if (sendLoadingAnimator?.isRunning == true) return

        sendLoadingAnimator?.cancel()
        sendButton.scaleX = 1f
        sendButton.scaleY = 1f
        sendLoadingAnimator = ObjectAnimator.ofPropertyValuesHolder(
            sendButton,
            PropertyValuesHolder.ofFloat(View.SCALE_X, 0.96f, 1.04f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.96f, 1.04f)
        ).apply {
            duration = SEND_BUTTON_PULSE_DURATION_MS
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = microInteractionInterpolator
            start()
        }
    }

    private fun stopSendLoadingAnimation() {
        sendLoadingAnimator?.cancel()
        sendLoadingAnimator = null
        sendButton.scaleX = 1f
        sendButton.scaleY = 1f
        sendButton.text = "↑"
    }

    private fun startTypingPulse() {
        typingIndicatorView.text = "نبض يكتب..."
        typingIndicatorView.visibility = View.VISIBLE
        if (typingPulseAnimator?.isRunning == true) return

        typingPulseAnimator?.cancel()
        typingIndicatorView.alpha = 1f
        typingPulseAnimator = ObjectAnimator.ofFloat(
            typingIndicatorView,
            View.ALPHA,
            0.45f,
            1f
        ).apply {
            duration = TYPING_PULSE_DURATION_MS
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = microInteractionInterpolator
            start()
        }
    }

    private fun stopTypingPulse() {
        typingPulseAnimator?.cancel()
        typingPulseAnimator = null
        typingIndicatorView.alpha = 1f
        typingIndicatorView.visibility = View.GONE
    }

    private fun setStatusInfo(message: String) {
        statusView.text = message
        statusView.setTextColor(ContextCompat.getColor(this, R.color.nabd_text_secondary))
    }

    private fun setStatusSuccess(message: String) {
        statusView.text = message
        statusView.setTextColor(ContextCompat.getColor(this, R.color.nabd_success))
    }

    private fun setStatusError(message: String) {
        statusView.text = message
        statusView.setTextColor(ContextCompat.getColor(this, R.color.nabd_error))
    }

    private suspend fun getSelectedDocument(): LocalDocument? {
        return documentStore.getDocument(documentStore.getSelectedDocumentId())
    }

    private fun currentDocumentAnswerLength(): String {
        return preferences.getString("document_answer_length", "short") ?: "short"
    }

    private fun currentRagMode(): RagMode {
        return runCatching {
            RagMode.valueOf(
                preferences.getString(KEY_RAG_SEARCH_MODE, RagMode.AUTO.name)?.uppercase(Locale.US)
                    ?: RagMode.AUTO.name
            )
        }.getOrDefault(RagMode.AUTO)
    }

    private fun currentEmbeddingBackend(): EmbeddingBackend {
        return runCatching {
            EmbeddingBackend.valueOf(
                preferences.getString(KEY_EMBEDDING_BACKEND, EmbeddingBackend.AUTO.name)
                    ?.uppercase(Locale.US)
                    ?: EmbeddingBackend.AUTO.name
            )
        }.getOrDefault(EmbeddingBackend.AUTO)
    }

    private fun documentAnswerLengthLabel(value: String): String {
        return when (value) {
            "medium" -> "متوسط"
            "long" -> "مفصل"
            else -> "مختصر"
        }
    }

    private fun ragModeLabel(mode: RagMode): String {
        return when (mode) {
            RagMode.KEYWORD -> "بحث نصي"
            RagMode.SEMANTIC -> "بحث دلالي"
            RagMode.AUTO -> "تلقائي"
        }
    }

    private fun embeddingBackendLabel(backend: EmbeddingBackend): String {
        return when (backend) {
            EmbeddingBackend.MEDIAPIPE -> "MediaPipe"
            EmbeddingBackend.TFLITE -> "TensorFlow Lite"
            EmbeddingBackend.AUTO -> "تلقائي"
        }
    }

    private fun updateDocumentAnswerLength(value: String) {
        preferences.edit().putString("document_answer_length", value).apply()
        scope.launch {
            val session = chatSessionStore.getActiveOrCreateSession()
            session.documentAnswerLength = value
            session.updatedAt = System.currentTimeMillis()
            chatSessionStore.saveSession(session)
        }
        setStatusSuccess("تم ضبط طول إجابة المستند: ${documentAnswerLengthLabel(value)}")
    }

    private fun updateRagMode(value: String) {
        val mode = runCatching { RagMode.valueOf(value.uppercase(Locale.US)) }.getOrDefault(RagMode.AUTO)
        preferences.edit().putString(KEY_RAG_SEARCH_MODE, mode.name).apply()
        setStatusSuccess("تم ضبط وضع البحث: ${ragModeLabel(mode)}")
    }

    private fun updateEmbeddingBackend(value: String) {
        val backend = runCatching {
            EmbeddingBackend.valueOf(value.uppercase(Locale.US))
        }.getOrDefault(EmbeddingBackend.AUTO)
        preferences.edit().putString(KEY_EMBEDDING_BACKEND, backend.name).apply()
        embeddingEngine.close()
        setStatusSuccess("تم ضبط محرك التضمين: ${embeddingBackendLabel(backend)}")
    }

    private fun updatePdfPageLimit(limit: Int) {
        PdfSettings.setPdfPageLimit(this, limit)
        setStatusSuccess("تم تحديث حد صفحات PDF")
    }

    private fun embeddingModelSettingsStatus(): String {
        if (!embeddingModelManager.isEmbeddingModelReady()) {
            return "غير مستورد"
        }

        val sizeBytes = embeddingModelManager.modelSizeBytes()
        return if (sizeBytes > 0L) {
            "مستورد - ${formatStorageSize(sizeBytes)}"
        } else {
            "مستورد"
        }
    }

    private fun modelImportStatus(model: SupportedModel): String {
        val imported = modelManager.isModelImported(model.id)
        if (!imported) return "غير مستورد"
        val sizeBytes = modelManager.modelSizeBytes(model.id)
        return if (sizeBytes > 0L) {
            "مستورد - ${formatStorageSize(sizeBytes)}"
        } else {
            "مستورد"
        }
    }

    private fun currentDocumentAnswerLengthInstruction(): String {
        return when (currentDocumentAnswerLength()) {
            "medium" -> "اجعل الإجابة متوسطة الطول مع بعض التفصيل."
            "long" -> "اجعل الإجابة مفصلة ومنظمة مع شرح أوضح."
            else -> "اجعل الإجابة مختصرة ومباشرة."
        }
    }

    private fun copyToClipboard(label: String, text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    }

    private fun buildConversationPlainText(): String {
        return buildMessagesTextFromChatMessages(chatMessages)
    }

    private fun copyFullConversation() {
        val text = buildConversationPlainText()
        if (text.isBlank()) {
            setStatusError("لا توجد محادثة لنسخها")
            Toast.makeText(this, "لا توجد محادثة لنسخها", Toast.LENGTH_SHORT).show()
            return
        }
        copyToClipboard("محادثة نبض", text)
        setStatusSuccess("تم نسخ المحادثة")
        Toast.makeText(this, "تم نسخ المحادثة", Toast.LENGTH_SHORT).show()
    }

    private fun copyLastAssistantResponse() {
        val text = lastAssistantResponse.trim()
        if (text.isBlank()) {
            setStatusError("لا يوجد رد لنسخه")
            Toast.makeText(this, "لا يوجد رد لنسخه", Toast.LENGTH_SHORT).show()
            return
        }
        copyToClipboard("آخر رد", text)
        setStatusSuccess("تم نسخ آخر رد")
        Toast.makeText(this, "تم نسخ آخر رد", Toast.LENGTH_SHORT).show()
    }

    private fun copyBetaReportToClipboard() {
        scope.launch {
            val report = withContext(Dispatchers.IO) {
                val packageInfo = runCatching { packageManager.getPackageInfo(packageName, 0) }.getOrNull()
                val versionName = packageInfo?.versionName ?: "غير معروف"
                val versionCode = packageInfo?.let {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        it.longVersionCode.toString()
                    } else {
                        @Suppress("DEPRECATION")
                        it.versionCode.toString()
                    }
                } ?: "غير معروف"

                val deviceInfo = phoneToolManager.getDeviceInfo().content
                val storageInfo = phoneToolManager.getStorageInfo().content
                val batteryInfo = phoneToolManager.getBatteryStatus().content

                val modelImported = modelManager.isModelImported(selectedModel.id)
                val modelSizeBytes = modelManager.modelSizeBytes(selectedModel.id)
                val modelSizeLabel = if (modelSizeBytes > 0L) formatStorageSize(modelSizeBytes) else "غير متاح"
                val modelState = currentModelStatusLabel()
                val engineReady = textInferenceEngine?.isReady() == true && loadedModelId == selectedModel.id

                val ragMode = currentRagMode()
                val embeddingBackend = currentEmbeddingBackend()
                val embeddingImported = embeddingModelManager.isEmbeddingModelReady()
                val embeddingSizeBytes = embeddingModelManager.modelSizeBytes()
                val embeddingSizeLabel = if (embeddingSizeBytes > 0L) formatStorageSize(embeddingSizeBytes) else "غير متاح"
                val selectedDocumentId = documentStore.getSelectedDocumentId()
                val selectedDocument = documentStore.getDocument(selectedDocumentId)
                val indexInfo = selectedDocumentId?.let { embeddingStore.getIndexInfo(it) }

                val chatSessionsCount = chatSessionStore.getAllSessions().size
                val documentsCount = countSavedDocuments()
                val embeddingIndexesCount = embeddingStore.countIndexes()

                BetaReportBuilder(
                    reportDateTime = formatDateTime(System.currentTimeMillis()),
                    versionName = versionName,
                    versionCode = versionCode,
                    deviceInfo = deviceInfo,
                    storageInfo = storageInfo,
                    batteryInfo = batteryInfo,
                    selectedModelId = selectedModel.id,
                    selectedModelDisplayName = selectedModel.displayName,
                    modelImported = modelImported,
                    modelSizeLabel = modelSizeLabel,
                    modelState = modelState,
                    engineReady = engineReady,
                    lastFirstTokenLatencyMs = lastFirstTokenLatencyMs,
                    lastGenerationDurationMs = lastGenerationDurationMs,
                    lastResponseCharCount = lastResponseCharCount,
                    ragModeName = ragMode.name.lowercase(Locale.US),
                    embeddingBackendName = embeddingBackend.name.lowercase(Locale.US),
                    embeddingImported = embeddingImported,
                    embeddingSizeLabel = embeddingSizeLabel,
                    embeddingModelPath = "files/${EmbeddingModelManager.EMBEDDING_MODEL_RELATIVE_PATH}",
                    selectedDocumentTitle = selectedDocument?.title,
                    indexInfo = indexInfo,
                    chatSessionsCount = chatSessionsCount,
                    documentsCount = documentsCount,
                    embeddingIndexesCount = embeddingIndexesCount
                ).build()
            }
            copyToClipboard("Nabd Beta Report", report)
            setStatusSuccess("تم نسخ تقرير بيتا")
            Toast.makeText(this@MainActivity, "تم نسخ تقرير بيتا", Toast.LENGTH_SHORT).show()
            MaterialAlertDialogBuilder(this@MainActivity)
                .setTitle("تم نسخ التقرير")
                .setMessage("تم نسخ تقرير بيتا إلى الحافظة.")
                .setPositiveButton("إغلاق", null)
                .show()
        }
    }

    private suspend fun countSavedDocuments(): Int {
        val raw = preferences.getString("local_documents_json", null).orEmpty()
        if (raw.isBlank()) return 0
        return runCatching { org.json.JSONArray(raw).length() }.getOrElse { documentStore.getDocuments().size }
    }

    private fun yesNo(value: Boolean): String {
        return if (value) "نعم" else "لا"
    }

    private fun getDisplayName(uri: Uri): String? {
        return runCatching {
            contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use {
                if (it.moveToFirst()) {
                    it.getString(it.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
                } else {
                    null
                }
            }
        }.getOrNull()
    }

    private fun showExtractionModeDialog(title: String, type: String, text: String) {
        val modes = DocumentExtractionProcessor.ExtractionMode.values()
        val labels = modes.map { it.label }.toTypedArray()

        MaterialAlertDialogBuilder(this)
            .setTitle("اختر طريقة حفظ النص")
            .setItems(labels) { _, which ->
                val selectedMode = modes[which]
                processExtractedTextWithMode(title, type, text, selectedMode)
            }
            .show()
    }

    private fun processExtractedTextWithMode(
        baseTitle: String,
        type: String,
        text: String,
        mode: DocumentExtractionProcessor.ExtractionMode
    ) {
        val finalTitle = "$baseTitle ${mode.suffix}"

        when (mode) {
            DocumentExtractionProcessor.ExtractionMode.RAW -> {
                saveExtractedDocument(finalTitle, type, text)
                setStatusSuccess("تم حفظ النص الخام")
            }
            DocumentExtractionProcessor.ExtractionMode.REDACTED -> {
                val redactedByRegex = DocumentExtractionProcessor.redactSensitiveInfo(text)
                if (textInferenceEngine?.isReady() == true) {
                    performLlmExtraction(finalTitle, type, DocumentExtractionProcessor.getRedactionPrompt(redactedByRegex), "جاري تنقيح النص...")
                } else {
                    saveExtractedDocument(finalTitle, type, redactedByRegex)
                    setStatusSuccess("تم التنقيح باستخدام القواعد الأساسية (شغّل نبض لتنقيح أعمق)")
                }
            }
            DocumentExtractionProcessor.ExtractionMode.MARKDOWN -> {
                if (textInferenceEngine?.isReady() == true) {
                    performLlmExtraction(finalTitle, type, DocumentExtractionProcessor.getMarkdownPrompt(text), "جاري تحويل النص إلى Markdown...")
                } else {
                    setStatusError("شغّل نبض أولًا لاستخدام نمط Markdown")
                }
            }
            DocumentExtractionProcessor.ExtractionMode.STRUCTURED -> {
                if (textInferenceEngine?.isReady() == true) {
                    performLlmExtraction(finalTitle, type, DocumentExtractionProcessor.getStructuredDataPrompt(text), "جاري استخراج البيانات المنظمة...")
                } else {
                    setStatusError("شغّل نبض أولًا لاستخدام نمط البيانات المنظمة")
                }
            }
        }
    }

    private fun performLlmExtraction(finalTitle: String, type: String, prompt: String, statusMessage: String) {
        isProcessingFile = true
        updateButtons()
        setStatusInfo(statusMessage)

        scope.launch {
            try {
                val result = StringBuilder()
                withContext(Dispatchers.IO) {
                    textInferenceEngine?.generate(prompt)?.collect { chunk ->
                        result.append(chunk)
                    }
                }
                val processedText = cleanForDisplay(result.toString(), preserveMarkdown = true)
                if (processedText.isNotEmpty()) {
                    saveExtractedDocument(finalTitle, type, processedText)
                    setStatusSuccess("اكتمل استخراج البيانات بنجاح")
                } else {
                    setStatusError("فشل استخراج البيانات من النموذج")
                }
            } catch (e: Exception) {
                setStatusError("حدث خطأ أثناء معالجة النموذج: ${e.message}")
            } finally {
                isProcessingFile = false
                updateButtons()
            }
        }
    }

    private fun saveExtractedDocument(title: String, type: String, text: String) {
        if (text.isBlank()) return
        scope.launch {
            val document = LocalDocument(
                id = UUID.randomUUID().toString(),
                title = title,
                type = type,
                extractedText = text,
                createdAt = System.currentTimeMillis()
            )
            documentStore.saveDocument(document)
        }
    }

    private fun chunkText(text: String, max: Int = DOCUMENT_CHUNK_SIZE): List<String> {
        val normalized = text.trim()
        if (normalized.isEmpty()) return emptyList()

        val chunks = mutableListOf<String>()
        val current = StringBuilder()

        normalized.split(Regex("\\n\\s*\\n"))
            .filter { it.isNotBlank() }
            .forEach { paragraph ->
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

        if (current.isNotEmpty()) {
            chunks.add(current.toString().trim())
        }
        return chunks
    }

    private fun retrieveKeywordDocumentChunks(
        document: LocalDocument,
        query: String
    ): List<RetrievedChunk> {
        val words = query.lowercase()
            .split(Regex("\\s+"))
            .filter { it.length >= 2 }
            .toSet()

        val scored = chunkText(document.extractedText.take(DOCUMENT_TEXT_LIMIT))
            .mapIndexed { index, chunk ->
                val loweredChunk = chunk.lowercase()
                RetrievedChunk(
                    documentId = document.id,
                    documentTitle = document.title,
                    chunkIndex = index,
                    text = chunk,
                    score = words.sumOf { word -> if (loweredChunk.contains(word)) 1 else 0 }
                )
            }
            .sortedByDescending { it.score }

        if (scored.isEmpty()) return emptyList()
        if (words.isEmpty()) return scored.take(MAX_RETRIEVED_CHUNKS)

        return scored.filter { it.score > 0 }
            .take(MAX_RETRIEVED_CHUNKS)
            .ifEmpty { scored.take(MAX_RETRIEVED_CHUNKS) }
    }

    private suspend fun retrieveDocumentChunks(query: String): DocumentRetrievalOutcome {
        val ragMode = currentRagMode()
        val document = getSelectedDocument() ?: return DocumentRetrievalOutcome(
            generationStatus = if (ragMode == RagMode.AUTO) {
                DEFAULT_GENERATION_STATUS
            } else {
                "اختر مستندًا من المكتبة أولًا."
            }
        )
        val keywordResults = { retrieveKeywordDocumentChunks(document, query) }

        return when (ragMode) {
            RagMode.KEYWORD -> {
                DocumentRetrievalOutcome(
                    chunks = keywordResults(),
                    generationStatus = KEYWORD_GENERATION_STATUS
                )
            }
            RagMode.AUTO -> {
                val canUseSemantic = embeddingEngine.isReady() && embeddingStore.hasIndex(document.id)
                if (canUseSemantic) {
                    val semanticResults = semanticRetriever.retrieveSemantic(document.id, query)
                        .map { it.copy(documentTitle = document.title) }
                    if (semanticResults.isNotEmpty()) {
                        DocumentRetrievalOutcome(
                            chunks = semanticResults,
                            generationStatus = SEMANTIC_GENERATION_STATUS
                        )
                    } else {
                        DocumentRetrievalOutcome(
                            chunks = keywordResults(),
                            generationStatus = KEYWORD_GENERATION_STATUS
                        )
                    }
                } else {
                    DocumentRetrievalOutcome(
                        chunks = keywordResults(),
                        generationStatus = KEYWORD_GENERATION_STATUS
                    )
                }
            }
            RagMode.SEMANTIC -> {
                if (!embeddingEngine.isReady() || !embeddingStore.hasIndex(document.id)) {
                    return DocumentRetrievalOutcome(
                        chunks = keywordResults(),
                        generationStatus = "البحث الدلالي غير جاهز، تم استخدام البحث النصي مؤقتًا."
                    )
                }

                val semanticResults = semanticRetriever.retrieveSemantic(document.id, query)
                    .map { it.copy(documentTitle = document.title) }
                if (semanticResults.isNotEmpty()) {
                    DocumentRetrievalOutcome(
                        chunks = semanticResults,
                        generationStatus = SEMANTIC_GENERATION_STATUS
                    )
                } else {
                    val failureReason = semanticRetriever.lastFailureReason()
                        ?: embeddingEngine.lastFailureReason()
                    DocumentRetrievalOutcome(
                        chunks = keywordResults(),
                        generationStatus = semanticFallbackGenerationStatus(failureReason)
                    )
                }
            }
        }
    }

    private fun semanticFallbackGenerationStatus(reason: String?): String {
        return if (reason.isNullOrBlank()) {
            SEMANTIC_FALLBACK_GENERATION_STATUS
        } else {
            DocumentMessageFormatter.semanticFallbackStatusWithReason(reason)
        }
    }

    private suspend fun buildDocumentContext(query: String): DocumentContextResult {
        val outcome = retrieveDocumentChunks(query)
        if (outcome.chunks.isEmpty()) {
            lastRagOperation = LastRagOperationResult(
                query = query,
                chunks = emptyList(),
                rejectedCount = semanticRetriever.lastRejectedCount(),
                fullContextSentToModel = ""
            )
            return DocumentContextResult(
                context = null,
                generationStatus = outcome.generationStatus
            )
        }

        val builder = StringBuilder()
        builder.append("استخدم المعلومات التالية من المستندات للإجابة:\n\n")
        outcome.chunks.forEachIndexed { index, chunk ->
            val block = "المصدر [${index + 1}]: ${chunk.documentTitle}\nالنص: ${chunk.text.safeTruncate(DOCUMENT_CHUNK_SIZE)}"
            if (builder.length + block.length + 4 <= MAX_DOCUMENT_CONTEXT_CHARS) {
                if (index > 0) builder.append("\n---\n")
                builder.append(block)
            }
        }
        val context = builder.toString().takeIf { it.isNotBlank() }

        lastRagOperation = LastRagOperationResult(
            query = query,
            chunks = outcome.chunks,
            rejectedCount = semanticRetriever.lastRejectedCount(),
            fullContextSentToModel = context ?: ""
        )

        return DocumentContextResult(
            context = context,
            generationStatus = if (context != null) outcome.generationStatus else DEFAULT_GENERATION_STATUS
        )
    }

    private fun handleMemoryCommand(input: String): Boolean {
        val normalized = input.trim()
        if (normalized.isEmpty()) return false

        when {
            normalized == "ماذا تتذكر عني؟" || normalized == "ماذا تتذكر عني" -> {
                inputView.setText("")
                addChatMessage(ChatMessage(role = Role.USER, text = input))
                addChatMessage(ChatMessage(role = Role.ASSISTANT, text = buildMemoryListText()))
                saveActiveSessionDebounced(immediate = true)
                return true
            }
            normalized == "امسح ذاكرة نبض" -> {
                inputView.setText("")
                addChatMessage(ChatMessage(role = Role.USER, text = input))
                confirmClearMemory(showChatMessage = true)
                return true
            }
            normalized == "عطّل الذاكرة" || normalized == "عطل الذاكرة" -> {
                inputView.setText("")
                addChatMessage(ChatMessage(role = Role.USER, text = input))
                memoryStore.setMemoryEnabled(false)
                val message = "تم تعطيل ذاكرة نبض."
                addChatMessage(ChatMessage(role = Role.ASSISTANT, text = message))
                setStatusSuccess(message)
                saveActiveSessionDebounced(immediate = true)
                return true
            }
            normalized == "فعّل الذاكرة" || normalized == "فعل الذاكرة" -> {
                inputView.setText("")
                addChatMessage(ChatMessage(role = Role.USER, text = input))
                memoryStore.setMemoryEnabled(true)
                val message = "تم تفعيل ذاكرة نبض."
                addChatMessage(ChatMessage(role = Role.ASSISTANT, text = message))
                setStatusSuccess(message)
                saveActiveSessionDebounced(immediate = true)
                return true
            }
        }

        val extracted = MemoryExtractor.extractMemoryCommand(input) ?: return false
        inputView.setText("")
        addChatMessage(ChatMessage(role = Role.USER, text = input))

        if (!memoryStore.isMemoryEnabled()) {
            val message = "ذاكرة نبض معطلة. يمكنك تفعيلها من الإعدادات."
            addChatMessage(ChatMessage(role = Role.ASSISTANT, text = message))
            setStatusError(message)
            saveActiveSessionDebounced(immediate = true)
            return true
        }

        validateMemoryInput(
            text = extracted,
            sensitiveMessage = "لا يمكن حفظ معلومات حساسة في ذاكرة نبض."
        )?.let { message ->
            addChatMessage(ChatMessage(role = Role.ASSISTANT, text = message))
            setStatusError(message)
            saveActiveSessionDebounced(immediate = true)
            return true
        }

        memoryStore.addMemory(
            text = extracted,
            category = inferMemoryCategory(extracted)
        )
        addChatMessage(ChatMessage(role = Role.ASSISTANT, text = "تم حفظ ذلك في ذاكرة نبض."))
        setStatusSuccess("تم تحديث ذاكرة نبض.")
        saveActiveSessionDebounced(immediate = true)
        return true
    }

    private fun validateMemoryInput(
        text: String,
        sensitiveMessage: String
    ): String? {
        val normalized = text.trim()
        if (normalized.isEmpty()) return "لا يمكن حفظ ذاكرة فارغة."
        if (normalized.length > MAX_MEMORY_ITEM_CHARS) return "المعلومة طويلة جدًا. اكتبها بشكل أقصر."
        if (containsSensitiveMemory(normalized)) return sensitiveMessage
        return null
    }

    private fun buildMemoryListText(): String {
        val memories = memoryStore.getAllMemories()
        if (memories.isEmpty()) return "لا توجد معلومات محفوظة في ذاكرة نبض."
        return buildString {
            appendLine("هذه المعلومات المحفوظة في ذاكرة نبض:")
            memories.forEach { memory ->
                appendLine("- ${memory.text}")
            }
        }.trim()
    }

    private fun inferMemoryCategory(text: String): String {
        val normalized = text.lowercase(Locale.getDefault())
        return when {
            normalized.contains("أفضل") || normalized.contains("افضل") || normalized.contains("أحب") || normalized.contains("احب") || normalized.contains("لا أحب") || normalized.contains("لا احب") -> {
                MemoryStore.CATEGORY_PREFERENCE
            }
            normalized.contains("اسمي") || normalized.contains("اسم المستخدم") || normalized.contains("عمري") || normalized.contains("مدينتي") || normalized.contains("أعمل") || normalized.contains("اعمل") -> {
                MemoryStore.CATEGORY_PROFILE
            }
            normalized.contains("مشروعي") || normalized.contains("مشروع") || normalized.contains("التطبيق") || normalized.contains("العمل") -> {
                MemoryStore.CATEGORY_PROJECT
            }
            else -> MemoryStore.CATEGORY_GENERAL
        }
    }

    private fun containsSensitiveMemory(text: String): Boolean {
        val normalized = text.lowercase(Locale.getDefault())
        return SENSITIVE_MEMORY_KEYWORDS.any { keyword ->
            normalized.contains(keyword)
        }
    }

    private fun buildMemoryContextForPrompt(): String {
        if (!memoryStore.isMemoryEnabled()) return ""
        return MemoryPromptBuilder.buildMemoryContext(memoryStore.getAllMemories())
    }

    private fun memoryCategoryLabel(category: String): String {
        return when (category) {
            MemoryStore.CATEGORY_PREFERENCE -> "تفضيل"
            MemoryStore.CATEGORY_PROFILE -> "ملف شخصي"
            MemoryStore.CATEGORY_PROJECT -> "مشروع"
            else -> "عام"
        }
    }

    private fun showMemoryDialog() {
        val memories = memoryStore.getAllMemories()
        var memoryDialog: AlertDialog? = null
        val density = resources.displayMetrics.density
        val outerPadding = (16 * density).toInt()
        val cardPadding = (12 * density).toInt()
        val cardSpacing = (10 * density).toInt()

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(outerPadding, outerPadding, outerPadding, outerPadding)
        }

        val privacyNote = TextView(this).apply {
            text = "تُحفظ الذاكرة محليًا على جهازك فقط. لا تحفظ معلومات حساسة مثل كلمات المرور أو الأرقام السرية."
            setTextColor(Color.parseColor("#A0A0A0"))
            textSize = 12f
            setLineSpacing(0f, 1.2f)
            textDirection = View.TEXT_DIRECTION_LOCALE
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
        }
        container.addView(privacyNote)

        container.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (12 * density).toInt()
            )
        })

        if (memories.isEmpty()) {
            container.addView(
                TextView(this).apply {
                    text = "ذاكرة نبض فارغة."
                    setTextColor(Color.WHITE)
                    textSize = 14f
                    textDirection = View.TEXT_DIRECTION_LOCALE
                    textAlignment = View.TEXT_ALIGNMENT_VIEW_START
                }
            )
        } else {
            memories.forEach { memory ->
                val card = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutDirection = View.LAYOUT_DIRECTION_RTL
                    setBackgroundColor(Color.parseColor("#242424"))
                    setPadding(cardPadding, cardPadding, cardPadding, cardPadding)
                }

                val topRow = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutDirection = View.LAYOUT_DIRECTION_RTL
                }

                val editButton = TextView(this).apply {
                    text = "تعديل"
                    setTextColor(Color.parseColor("#FF7000"))
                    textSize = 13f
                    textDirection = View.TEXT_DIRECTION_LOCALE
                    textAlignment = View.TEXT_ALIGNMENT_CENTER
                    setPadding((10 * density).toInt(), (4 * density).toInt(), (10 * density).toInt(), (4 * density).toInt())
                    setOnClickListener {
                        val editInput = EditText(this@MainActivity).apply {
                            setText(memory.text)
                            setSelection(memory.text.length)
                            setTextColor(Color.WHITE)
                            setHintTextColor(Color.parseColor("#A0A0A0"))
                            background = ContextCompat.getDrawable(this@MainActivity, R.drawable.bg_input)
                            minLines = 3
                            maxLines = 6
                            gravity = android.view.Gravity.TOP or android.view.Gravity.START
                            textDirection = View.TEXT_DIRECTION_LOCALE
                            layoutDirection = View.LAYOUT_DIRECTION_RTL
                            setPadding((12 * density).toInt(), (12 * density).toInt(), (12 * density).toInt(), (12 * density).toInt())
                        }

                        val editContainer = LinearLayout(this@MainActivity).apply {
                            orientation = LinearLayout.VERTICAL
                            layoutDirection = View.LAYOUT_DIRECTION_RTL
                            setBackgroundColor(Color.parseColor("#171717"))
                            setPadding((16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt(), (8 * density).toInt())
                            addView(
                                editInput,
                                LinearLayout.LayoutParams(
                                    LinearLayout.LayoutParams.MATCH_PARENT,
                                    LinearLayout.LayoutParams.WRAP_CONTENT
                                )
                            )
                        }

                        MaterialAlertDialogBuilder(this@MainActivity)
                            .setTitle("تعديل عنصر الذاكرة")
                            .setView(editContainer)
                            .setNegativeButton("إلغاء", null)
                            .setPositiveButton("حفظ") { _, _ ->
                                val updatedText = editInput.text?.toString()?.trim().orEmpty()
                                validateMemoryInput(
                                    text = updatedText,
                                    sensitiveMessage = "لا يمكن حفظ معلومات حساسة في الذاكرة."
                                )?.let { message ->
                                    Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
                                    return@setPositiveButton
                                }

                                val updated = memoryStore.updateMemory(memory.id, updatedText)
                                if (updated) {
                                    Toast.makeText(this@MainActivity, "تم تحديث عنصر الذاكرة", Toast.LENGTH_SHORT).show()
                                    memoryDialog?.dismiss()
                                    showMemoryDialog()
                                }
                            }
                            .show()
                    }
                }

                val deleteButton = TextView(this).apply {
                    text = "حذف"
                    setTextColor(Color.parseColor("#EF4444"))
                    textSize = 13f
                    textDirection = View.TEXT_DIRECTION_LOCALE
                    textAlignment = View.TEXT_ALIGNMENT_CENTER
                    setPadding((10 * density).toInt(), (4 * density).toInt(), (10 * density).toInt(), (4 * density).toInt())
                    setOnClickListener {
                        MaterialAlertDialogBuilder(this@MainActivity)
                            .setTitle("تأكيد الحذف")
                            .setMessage("هل تريد حذف هذا العنصر من ذاكرة نبض؟")
                            .setNegativeButton("إلغاء", null)
                            .setPositiveButton("حذف") { _, _ ->
                                memoryStore.deleteMemory(memory.id)
                                Toast.makeText(this@MainActivity, "تم حذف عنصر الذاكرة", Toast.LENGTH_SHORT).show()
                                memoryDialog?.dismiss()
                                showMemoryDialog()
                            }
                            .show()
                    }
                }

                val textView = TextView(this).apply {
                    text = memory.text
                    setTextColor(Color.WHITE)
                    textSize = 14f
                    setLineSpacing(0f, 1.2f)
                    textDirection = View.TEXT_DIRECTION_LOCALE
                    textAlignment = View.TEXT_ALIGNMENT_VIEW_START
                }

                topRow.addView(
                    deleteButton,
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                )
                topRow.addView(
                    editButton,
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                )
                topRow.addView(
                    textView,
                    LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f
                    )
                )

                val secondaryText = TextView(this).apply {
                    val dateLabel = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()).format(Date(memory.updatedAt))
                    text = "${memoryCategoryLabel(memory.category)} • $dateLabel"
                    setTextColor(Color.parseColor("#A0A0A0"))
                    textSize = 12f
                    textDirection = View.TEXT_DIRECTION_LOCALE
                    textAlignment = View.TEXT_ALIGNMENT_VIEW_START
                }

                card.addView(topRow)
                card.addView(View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        (6 * density).toInt()
                    )
                })
                card.addView(secondaryText)

                container.addView(
                    card,
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        bottomMargin = cardSpacing
                    }
                )
            }
        }

        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#171717"))
            addView(container)
        }

        memoryDialog = MaterialAlertDialogBuilder(this)
            .setTitle("ذاكرة نبض")
            .setView(scroll)
            .setPositiveButton("إغلاق", null)
            .show()
    }

    private fun confirmClearMemory(showChatMessage: Boolean = false) {
        MaterialAlertDialogBuilder(this)
            .setTitle("مسح ذاكرة نبض؟")
            .setMessage("سيتم حذف كل عناصر ذاكرة نبض من هذا الجهاز.")
            .setPositiveButton("مسح") { _, _ ->
                memoryStore.clearAll()
                if (showChatMessage) {
                    addChatMessage(ChatMessage(role = Role.ASSISTANT, text = "تم مسح ذاكرة نبض."))
                    saveActiveSessionDebounced(immediate = true)
                }
                setStatusSuccess("تم مسح ذاكرة نبض.")
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun sendPrompt() {
        if (isGenerating || isProcessingFile || isLoadingModel || isPreparingDocumentContext) return
        val input = inputView.text.toString().trim()
        if (input.isEmpty()) return
        if (handleMemoryCommand(input)) return

        val mapIntent = com.example.localqwen.tools.MapToolRouter.detectMapIntent(input)
        if (mapIntent != null) {
            handleMapToolIntent(input, mapIntent)
            return
        }

        val toolIntents = PhoneToolRouter.detectToolIntents(input)
        if (toolIntents.isNotEmpty()) {
            showPhoneToolConfirmation(input, toolIntents)
            return
        }

        val documentToolRequests = DocumentToolRouter.detectDocumentToolRequests(input)
        if (documentToolRequests.isNotEmpty()) {
            showDocumentToolConfirmation(input, documentToolRequests)
            return
        }

        if (textInferenceEngine?.isReady() != true || loadedModelId != selectedModel.id) {
            setStatusError(NabdErrorMessages.engineNotReady())
            return
        }

        inputView.setText("")
        addChatMessage(ChatMessage(role = Role.USER, text = input))
        val assistantMessage = ChatMessage(role = Role.ASSISTANT, text = "")
        addChatMessage(assistantMessage)

        isPreparingDocumentContext = true
        updateButtons()

        scope.launch {
            val hasDoc = getSelectedDocument() != null
            setStatusInfo(
                if (hasDoc) {
                    "جاري تجهيز سياق المستند..."
                } else {
                    DEFAULT_GENERATION_STATUS
                }
            )

            val contextResult = try {
                withContext(Dispatchers.IO) { buildDocumentContext(input) }
            } catch (_: Exception) {
                DocumentContextResult(
                    context = null,
                    generationStatus = DEFAULT_GENERATION_STATUS
                )
            } finally {
                isPreparingDocumentContext = false
            }

            withContext(Dispatchers.Main) {
                updateButtons()
                setStatusInfo(contextResult.generationStatus)

                val prompt = if (contextResult.context != null) {
                    NabdSystemPrompt.documentPrompt(
                        userInput = input,
                        contextChunks = contextResult.context,
                        answerLengthInstruction = currentDocumentAnswerLengthInstruction()
                    )
                } else {
                    NabdSystemPrompt.normalChatPrompt(
                        userInput = input,
                        memoryContext = buildMemoryContextForPrompt()
                    )
                }

                startAssistantGeneration(
                    prompt = prompt,
                    assistant = assistantMessage,
                    status = contextResult.generationStatus,
                    autoTitle = chatMessages.count { it.role == Role.USER } == 1,
                    firstUserMessage = input
                )
            }
        }
    }

    private fun addChatMessage(message: ChatMessage) {
        val normalizedMessage = if (message.text.isBlank()) {
            message
        } else {
            message.copy(text = normalizeMessageText(message.role, message.text))
        }
        chatMessages.add(normalizedMessage)
        if (normalizedMessage.role == Role.ASSISTANT && normalizedMessage.text.isNotBlank()) {
            lastAssistantResponse = normalizedMessage.text
        }
        chatAdapter.addMessage(normalizedMessage)
        if (normalizedMessage.role == Role.ASSISTANT) {
            activeAssistantMessageIndex = chatMessages.lastIndex
        }
        scrollChatToBottom()
    }

    private fun updateAssistantMessage(
        text: String,
        forceScroll: Boolean = false,
        preserveMarkdown: Boolean = true,
        renderMarkdown: Boolean = true
    ) {
        if (activeAssistantMessageIndex !in chatMessages.indices) return
        val cleanedText = cleanForDisplay(text, preserveMarkdown = preserveMarkdown)
        chatMessages[activeAssistantMessageIndex] = chatMessages[activeAssistantMessageIndex].copy(text = cleanedText)
        chatAdapter.updateLastAssistantMessage(cleanedText, renderMarkdown = renderMarkdown)
        if (forceScroll) {
            scrollChatToBottom()
        }
    }

    private fun renderChatHistory() {
        chatAdapter.submitMessages(chatMessages.map { it.copy() })
        scrollChatToBottom()
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

        val normalizedLines = withoutThinking
            .lines()
            .map { line ->
                if (preserveMarkdown) {
                    line.trimEnd()
                } else {
                    line
                        .replace("**", "")
                        .replace("__", "")
                        .replace("*", "")
                        .replace("`", "")
                        .replace(Regex("^\\s*#{1,4}\\s*"), "")
                        .replace(Regex("^\\s*[\\*-]\\s+"), "• ")
                        .replace(Regex("^\\s*•\\s+"), "• ")
                        .replace(Regex("^\\s*\\d+\\.\\s+"), "• ")
                        .replace(Regex("[ \\t]+"), " ")
                        .trimEnd()
                }
            }
            .joinToString("\n")

        return normalizedLines
            .replace(Regex("\n\\s*\n(?:\\s*\n)+"), "\n\n")
            .replace(Regex("[ \\t]+\n"), "\n")
            .trim()
    }

    private fun scrollChatToBottom() {
        val lastIndex = chatAdapter.itemCount - 1
        if (lastIndex >= 0) {
            rvChatMessages.post { rvChatMessages.scrollToPosition(lastIndex) }
        }
    }

    private fun startAssistantGeneration(
        prompt: String,
        assistant: ChatMessage,
        status: String,
        autoTitle: Boolean = false,
        firstUserMessage: String? = null,
        preserveMarkdownOutput: Boolean = true,
        renderMarkdownOutput: Boolean = true
    ) {
        val engine = textInferenceEngine
        if (engine == null || !engine.isReady()) {
            setStatusError(NabdErrorMessages.modelLoadFailed())
            return
        }

        isGenerating = true
        val generationStartedAt = SystemClock.elapsedRealtime()
        lastGenerationStartedAtElapsedMs = generationStartedAt
        lastGenerationFinishedAtElapsedMs = null
        lastFirstTokenLatencyMs = null
        lastGenerationDurationMs = null
        updateButtons()
        setStatusInfo(status)
        chatAdapter.markLastAssistantStreaming()

        scope.launch {
            try {
                val output = java.lang.StringBuilder()
                var lastRenderedRawLength = 0
                var firstChunkCaptured = false
                withContext(Dispatchers.IO) {
                    engine.generate(prompt).collect { chunk ->
                        if (!firstChunkCaptured) {
                            firstChunkCaptured = true
                            lastFirstTokenLatencyMs = SystemClock.elapsedRealtime() - generationStartedAt
                        }
                        output.append(chunk)
                        val shouldRefresh =
                            output.length <= 256 || output.length - lastRenderedRawLength >= STREAM_UPDATE_MIN_CHARS
                        if (shouldRefresh) {
                            val snapshot = output.toString()
                            lastRenderedRawLength = output.length
                            withContext(Dispatchers.Main) {
                                val cleanedSnapshot = cleanForDisplay(
                                    snapshot,
                                    preserveMarkdown = preserveMarkdownOutput
                                )
                                updateAssistantMessage(
                                    text = cleanedSnapshot,
                                    preserveMarkdown = preserveMarkdownOutput,
                                    renderMarkdown = false
                                )
                            }
                        }
                    }
                }

                val finalText = cleanForDisplay(
                    output.toString(),
                    preserveMarkdown = preserveMarkdownOutput
                ).ifBlank { "(فارغ)" }
                lastAssistantResponse = finalText
                lastResponseCharCount = finalText.length
                val generationFinishedAt = SystemClock.elapsedRealtime()
                lastGenerationFinishedAtElapsedMs = generationFinishedAt
                lastGenerationDurationMs = generationFinishedAt - generationStartedAt
                updateAssistantMessage(
                    text = finalText,
                    forceScroll = true,
                    preserveMarkdown = preserveMarkdownOutput,
                    renderMarkdown = renderMarkdownOutput
                )
                saveActiveSessionDebounced(
                    autoTitle = autoTitle,
                    firstUserMessage = firstUserMessage,
                    immediate = true
                )
                setStatusSuccess("جاهز")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: OutOfMemoryError) {
                val generationFinishedAt = SystemClock.elapsedRealtime()
                lastGenerationFinishedAtElapsedMs = generationFinishedAt
                lastGenerationDurationMs = generationFinishedAt - generationStartedAt
                saveActiveSessionDebounced(
                    autoTitle = autoTitle,
                    firstUserMessage = firstUserMessage,
                    immediate = true
                )
                setStatusError(NabdErrorMessages.generationFailed())
            } catch (_: Exception) {
                val generationFinishedAt = SystemClock.elapsedRealtime()
                lastGenerationFinishedAtElapsedMs = generationFinishedAt
                lastGenerationDurationMs = generationFinishedAt - generationStartedAt
                saveActiveSessionDebounced(
                    autoTitle = autoTitle,
                    firstUserMessage = firstUserMessage,
                    immediate = true
                )
                setStatusError(NabdErrorMessages.generationFailed())
            } finally {
                if (lastGenerationFinishedAtElapsedMs == null) {
                    val generationFinishedAt = SystemClock.elapsedRealtime()
                    lastGenerationFinishedAtElapsedMs = generationFinishedAt
                    lastGenerationDurationMs = generationFinishedAt - generationStartedAt
                }
                isGenerating = false
                updateButtons()
            }
        }
    }

    private fun saveActiveSessionDebounced(
        autoTitle: Boolean = false,
        firstUserMessage: String? = null,
        immediate: Boolean = false
    ) {
        saveSessionJob?.cancel()

        val snapshotMessages = chatMessages.map { it.copy() }
        val snapshotAssistantResponse = lastAssistantResponse
        val selectedDocumentId = documentStore.getSelectedDocumentId()

        saveSessionJob = scope.launch {
            if (!immediate) delay(250)
            val messagesText = withContext(Dispatchers.Default) {
                buildMessagesTextFromChatMessages(snapshotMessages)
            }

            withContext(Dispatchers.IO) {
                chatSessionStore.updateActiveSession(
                    messagesJson = messagesText,
                    lastAssistantResponse = snapshotAssistantResponse,
                    selectedDocumentId = selectedDocumentId,
                    documentAnswerLength = currentDocumentAnswerLength(),
                    autoTitle = autoTitle,
                    firstUserMessage = firstUserMessage
                )
            }
        }
    }

    private fun loadModel() {
        if (!modelManager.isModelReady(selectedModel)) {
            setStatusError("النموذج غير موجود")
            return
        }
        if (isLoadingModel) return
        if (loadedModelId == selectedModel.id && textInferenceEngine?.isReady() == true) {
            setStatusSuccess("تم تشغيل نبض")
            return
        }

        isLoadingModel = true
        updateButtons()
        setStatusInfo("جاري تشغيل نبض...")

        scope.launch {
            try {
                val requestModel = selectedModel
                
                val newEngine = com.example.localqwen.engine.LiteRtLmInferenceEngine()
                newEngine.load(modelManager.modelPath(requestModel), cacheDir.absolutePath)
                
                textInferenceEngine = newEngine
                loadedModelId = requestModel.id
                localEngineLastErrorMessage = null
                setStatusSuccess("تم تشغيل نبض")
            } catch (oom: OutOfMemoryError) {
                closeModelResources()
                localEngineLastErrorMessage = oom.message ?: "نفاد الذاكرة أثناء تحميل النموذج."
                setStatusError(NabdErrorMessages.modelLoadFailed())
            } catch (exception: Exception) {
                closeModelResources()
                localEngineLastErrorMessage = exception.message ?: "فشل تحميل النموذج."
                setStatusError(NabdErrorMessages.modelLoadFailed())
            } finally {
                isLoadingModel = false
                updateButtons()
                renderLocalModelManagerDialog()
            }
        }
    }

    private fun unloadModel(postSuccessMessage: String? = null) {
        if (isLoadingModel) return
        isLoadingModel = true
        updateButtons()
        setStatusInfo("جاري إيقاف نبض...")

        scope.launch {
            try {
                withContext(Dispatchers.IO) { closeModelResources() }
                if (postSuccessMessage.isNullOrBlank()) {
                    setStatusInfo("تم إيقاف نبض")
                } else {
                    setStatusSuccess(postSuccessMessage)
                }
            } finally {
                isLoadingModel = false
                updateButtons()
            }
        }
    }

    private fun closeModelResources() {
        synchronized(modelLock) {
            closeModelResourcesLocked()
        }
    }

    private fun closeModelResourcesLocked() {
        runCatching { kotlinx.coroutines.runBlocking { textInferenceEngine?.unload() } }
        textInferenceEngine = null
        loadedModelId = null
    }

    private fun showOptionsBottomSheet() {
        val sheet = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_options, null)
        val dialog = BottomSheetDialog(this)
        dialog.setContentView(sheet)

        val statusChip = sheet.findViewById<TextView>(R.id.tvStatusChip)
        val currentModelNameView = sheet.findViewById<TextView>(R.id.tvCurrentModelName)
        
        currentModelNameView.text = when (selectedModel.id) {
            MODEL_ID_E2B -> "Gemma E2B"
            MODEL_ID_E4B -> "Gemma E4B"
            else -> selectedModel.displayName
        }
        
        val isModelActive = textInferenceEngine?.isReady() == true && loadedModelId == selectedModel.id
        val isModelLoading = isLoadingModel
        
        statusChip.text = when {
            isModelLoading -> "جاري التشغيل"
            isModelActive -> "مشغّل"
            else -> "غير مشغّل"
        }

        val mainContainer = sheet.findViewById<LinearLayout>(R.id.sectionMainOptions)

        // 1. Toggle Nabd
        addOptionRow(
            mainContainer,
            if (isModelActive) R.drawable.ic_stop else R.drawable.ic_play,
            if (isModelActive) "إيقاف نبض" else "تشغيل نبض",
            iconColor = ContextCompat.getColor(this, if (isModelActive) R.color.nabd_error else R.color.icon_accent_tint)
        ) {
            dialog.dismiss()
            if (isModelActive) unloadModel() else loadModel()
        }

        // 2. Add File
        addOptionRow(
            mainContainer,
            R.drawable.ic_add,
            "إضافة ملف"
        ) {
            dialog.dismiss()
            showAttachmentTypeDialog()
        }

        // 3. New Chat
        addOptionRow(
            mainContainer,
            R.drawable.ic_history,
            "محادثة جديدة"
        ) {
            dialog.dismiss()
            startNewChat()
        }

        // 4. History
        addOptionRow(
            mainContainer,
            R.drawable.ic_history,
            "سجل المحادثات"
        ) {
            dialog.dismiss()
            showChatHistoryDialog()
        }

        // 5. Settings
        addOptionRow(
            mainContainer,
            R.drawable.ic_settings,
            "الإعدادات"
        ) {
            dialog.dismiss()
            openSettingsPage()
        }

        dialog.show()
    }

    private fun openSettingsPage() {
        scope.launch {
            val session = chatSessionStore.getActiveOrCreateSession()
            val doc = getSelectedDocument()
            val intent = SettingsActivity.createIntent(
                context = this@MainActivity,
                modelDescription = selectedModel.displayName,
                modelStatus = currentModelStatusLabel(),
                modelE2bStatus = modelImportStatus(modelById(MODEL_ID_E2B)),
                modelE4bStatus = modelImportStatus(modelById(MODEL_ID_E4B)),
                documentAnswerLength = currentDocumentAnswerLength(),
                ragSearchMode = currentRagMode().name.lowercase(Locale.US),
                embeddingBackend = currentEmbeddingBackend().name.lowercase(Locale.US),
                embeddingModelStatus = embeddingModelSettingsStatus(),
                embeddingIndexCount = embeddingStore.countIndexes(),
                selectedDocumentTitle = doc?.title,
                sessionTitle = session.title,
                appVersion = packageManager.getPackageInfo(packageName, 0).versionName ?: "1.0"
            )
            startActivityForResult(intent, settingsRequestCode)
        }
    }

    private fun showToolsCenter() {
        val items = arrayOf("البطارية", "الجهاز", "المكتبة", "السجل", "فتح الخريطة", "الأماكن المحفوظة")
        MaterialAlertDialogBuilder(this)
            .setTitle("مركز الأدوات")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> appendToolResultToChat(phoneToolManager.getBatteryStatus())
                    1 -> appendToolResultToChat(phoneToolManager.getDeviceInfo())
                    2 -> showDocumentLibraryDialog()
                    3 -> showChatHistoryDialog()
                    4 -> promptForMapSearch()
                    5 -> showSavedPlacesDialog()
                }
            }
            .show()
    }

    private fun promptForMapSearch() {
        val input = EditText(this).apply {
            hint = "ابحث عن مكان..."
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#A0A0A0"))
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("فتح الخريطة")
            .setView(input)
            .setPositiveButton("بحث") { _, _ ->
                val query = input.text.toString().trim()
                if (query.isNotEmpty()) {
                    openMapIntent(query)
                }
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun handleMapToolIntent(input: String, intent: com.example.localqwen.tools.MapToolIntent) {
        inputView.setText("")
        addChatMessage(ChatMessage(role = Role.USER, text = input))

        when (intent) {
            is com.example.localqwen.tools.MapToolIntent.SearchMap -> {
                val suggestion = "أستطيع فتح تطبيق الخرائط للبحث عن:\n${intent.query}\nسيتم فتح تطبيق الخرائط على جهازك."
                addChatMessage(ChatMessage(role = Role.ASSISTANT, text = suggestion))
                MaterialAlertDialogBuilder(this)
                    .setTitle("فتح الخريطة؟")
                    .setMessage(suggestion)
                    .setPositiveButton("فتح الخريطة") { _, _ -> openMapIntent(intent.query) }
                    .setNegativeButton("إلغاء", null)
                    .show()
            }
            is com.example.localqwen.tools.MapToolIntent.RouteMap -> {
                val suggestion = "أستطيع فتح تطبيق الخرائط لعرض المسار من ${intent.origin} إلى ${intent.destination}.\nسيتم فتح تطبيق الخرائط على جهازك."
                addChatMessage(ChatMessage(role = Role.ASSISTANT, text = suggestion))
                MaterialAlertDialogBuilder(this)
                    .setTitle("عرض المسار؟")
                    .setMessage(suggestion)
                    .setPositiveButton("فتح الخريطة") { _, _ -> openRouteIntent(intent.origin, intent.destination) }
                    .setNegativeButton("إلغاء", null)
                    .show()
            }
            is com.example.localqwen.tools.MapToolIntent.SavePlace -> {
                savedPlacesStore.savePlace(com.example.localqwen.tools.SavedPlace(intent.name, intent.query))
                addChatMessage(ChatMessage(role = Role.ASSISTANT, text = "تم حفظ المكان '${intent.name}' بنجاح."))
            }
            is com.example.localqwen.tools.MapToolIntent.OpenSavedPlace -> {
                val place = savedPlacesStore.getPlace(intent.name)
                if (place != null) {
                    val suggestion = "أستطيع فتح تطبيق الخرائط للبحث عن المكان المحفوظ '${place.name}' (${place.query}).\nسيتم فتح تطبيق الخرائط على جهازك."
                    addChatMessage(ChatMessage(role = Role.ASSISTANT, text = suggestion))
                    MaterialAlertDialogBuilder(this)
                        .setTitle("فتح الخريطة؟")
                        .setMessage(suggestion)
                        .setPositiveButton("فتح الخريطة") { _, _ -> openMapIntent(place.query) }
                        .setNegativeButton("إلغاء", null)
                        .show()
                } else {
                    addChatMessage(ChatMessage(role = Role.ASSISTANT, text = "عذراً، المكان '${intent.name}' غير محفوظ لديك."))
                }
            }
            is com.example.localqwen.tools.MapToolIntent.DeleteSavedPlace -> {
                savedPlacesStore.deletePlace(intent.name)
                addChatMessage(ChatMessage(role = Role.ASSISTANT, text = "تم حذف المكان '${intent.name}' من الأماكن المحفوظة."))
            }
            is com.example.localqwen.tools.MapToolIntent.ListSavedPlaces -> {
                val places = savedPlacesStore.getPlaces()
                if (places.isEmpty()) {
                    addChatMessage(ChatMessage(role = Role.ASSISTANT, text = "قائمة الأماكن المحفوظة فارغة."))
                } else {
                    val list = places.joinToString("\n") { "- ${it.name}: ${it.query}" }
                    addChatMessage(ChatMessage(role = Role.ASSISTANT, text = "الأماكن المحفوظة لديك:\n$list"))
                }
            }
        }
    }

    private fun showSavedPlacesDialog() {
        val places = savedPlacesStore.getPlaces()
        if (places.isEmpty()) {
            Toast.makeText(this, "لا توجد أماكن محفوظة", Toast.LENGTH_SHORT).show()
            return
        }
        val items = places.map { p: com.example.localqwen.tools.SavedPlace -> "${p.name} (${p.query})" }.toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle("الأماكن المحفوظة")
            .setItems(items) { _, which ->
                openMapIntent(places[which].query)
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun openMapIntent(query: String) {
        val encodedQuery = android.net.Uri.encode(query)
        val uri = android.net.Uri.parse("geo:0,0?q=$encodedQuery")
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, uri)
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "لا يوجد تطبيق خرائط متاح.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openRouteIntent(origin: String, destination: String) {
        val encodedOrigin = android.net.Uri.encode(origin)
        val encodedDest = android.net.Uri.encode(destination)
        val uri = android.net.Uri.parse("https://www.google.com/maps/dir/?api=1&origin=$encodedOrigin&destination=$encodedDest")
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, uri)
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "لا يوجد تطبيق خرائط متاح.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun addOptionRow(
        container: LinearLayout,
        iconRes: Int,
        title: String,
        subtitle: String? = null,
        titleColor: Int = ContextCompat.getColor(this, R.color.nabd_on_surface),
        iconColor: Int = ContextCompat.getColor(this, R.color.nabd_text_secondary),
        enabled: Boolean = true,
        onClick: () -> Unit
    ) {
        val row = LayoutInflater.from(this).inflate(R.layout.item_option_row, container, false)
        row.findViewById<ImageView>(R.id.ivOptionIcon).apply {
            setImageResource(iconRes)
            imageTintList = ColorStateList.valueOf(iconColor)
            alpha = if (enabled) 1.0f else 0.45f
        }
        row.findViewById<TextView>(R.id.tvOptionTitle).apply {
            text = title
            setTextColor(titleColor)
            alpha = if (enabled) 1.0f else 0.45f
        }
        row.findViewById<TextView>(R.id.tvOptionSubtitle).apply {
            if (subtitle.isNullOrBlank()) {
                visibility = View.GONE
            } else {
                visibility = View.VISIBLE
                text = subtitle
                alpha = if (enabled) 1.0f else 0.45f
            }
        }
        row.findViewById<ImageView>(R.id.ivOptionChevron).alpha = if (enabled) 0.5f else 0.25f
        row.alpha = if (enabled) 1.0f else 0.45f
        row.isEnabled = enabled
        row.setOnClickListener { if (enabled) onClick() }
        container.addView(row)
    }

    private fun modelById(modelId: String): SupportedModel {
        return supportedModels.firstOrNull { it.id == modelId } ?: supportedModels.first()
    }

    private fun modelDescription(model: SupportedModel): String {
        return model.displayName
    }

    private fun showMainModelManagementDialog(model: SupportedModel) {
        if (!modelManager.isModelImported(model.id)) {
            beginModelImportFor(model)
            return
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(model.displayName)
            .setItems(
                arrayOf(
                    "اختيار هذا النموذج",
                    "إعادة الاستيراد",
                    "حذف النموذج",
                    "إلغاء"
                )
            ) { dialog, which ->
                when (which) {
                    0 -> selectMainModelFromSettings(model)
                    1 -> beginModelImportFor(model)
                    2 -> confirmDeleteMainModel(model)
                    else -> dialog.dismiss()
                }
            }
            .show()
    }

    private fun showModelSelectionDialog() {
        val selectedIndex = supportedModels.indexOfFirst { it.id == selectedModel.id }.coerceAtLeast(0)
        val labels = supportedModels.map { modelDescription(it) }.toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle("اختيار النموذج")
            .setSingleChoiceItems(labels, selectedIndex) { dialog, which ->
                selectModel(which)
                dialog.dismiss()
            }
            .setNegativeButton("إغلاق", null)
            .show()
    }

    private fun showAboutDialog() {
        val packageInfo = packageManager.getPackageInfo(packageName, 0)
        val versionName = packageInfo.versionName ?: "غير متاح"
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode.toString()
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toString()
        }

        val density = resources.displayMetrics.density
        val outerPadding = (20 * density).toInt()
        val sectionSpacing = (14 * density).toInt()
        val titleColor = Color.parseColor("#FF7000")
        val bodyColor = Color.WHITE

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(outerPadding, outerPadding, outerPadding, outerPadding)
        }

        fun addSection(title: String, body: String) {
            val titleView = TextView(this).apply {
                text = title
                setTextColor(titleColor)
                textSize = 15f
                textDirection = View.TEXT_DIRECTION_LOCALE
                textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            }
            val bodyView = TextView(this).apply {
                text = body
                setTextColor(bodyColor)
                textSize = 14f
                setLineSpacing(0f, 1.25f)
                textDirection = View.TEXT_DIRECTION_LOCALE
                textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            }
            content.addView(titleView)
            content.addView(bodyView)
            content.addView(View(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    sectionSpacing
                )
            })
        }

        addSection("نبض", "مساعد ذكاء اصطناعي محلي يعمل على Android.")
        addSection(
            "الفكرة",
            "تشغيل نماذج ذكاء اصطناعي محلية داخل الجهاز، مع دعم المحادثة، المستندات، الصور، الذاكرة، وأدوات آمنة بإذن المستخدم."
        )
        addSection(
            "الخصوصية",
            "يعالج نبض المحادثات والمستندات والصور محليًا قدر الإمكان. لا يتم رفع بياناتك إلى خادم خارجي من داخل التطبيق."
        )
        addSection(
            "النماذج",
            "النماذج غير مدمجة داخل التطبيق بسبب الحجم والترخيص. يجب استيراد ملفات .litertlm يدويًا."
        )
        addSection("المطور", "عمار محمد التميمي")
        addSection("الإصدار", "versionName: $versionName\nversionCode: $versionCode")
        addSection(
            "ملاحظة",
            "نبض ما زال في مرحلة Beta، وقد يختلف الأداء حسب الجهاز والنموذج المستخدم."
        )

        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#171717"))
            addView(content)
        }

        val copyInfo = """
            اسم التطبيق: نبض
            الإصدار: $versionName ($versionCode)
            المطور: عمار محمد التميمي
            النماذج غير مدمجة داخل التطبيق ويجب استيراد ملفات .litertlm يدويًا.
            التطبيق في مرحلة Beta.
        """.trimIndent()

        MaterialAlertDialogBuilder(this)
            .setTitle("حول نبض")
            .setView(scroll)
            .setPositiveButton("إغلاق", null)
            .setNeutralButton("نسخ معلومات التطبيق") { _, _ ->
                copyToClipboard("Nabd App Info", copyInfo)
                Toast.makeText(this, "تم نسخ معلومات التطبيق", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun showOnboardingIfNeeded() {
        if (preferences.getBoolean(KEY_ONBOARDING_SEEN, false)) return
        showOnboardingDialog()
    }

    private fun showOnboardingDialog() {
        val density = resources.displayMetrics.density
        val outerPadding = (20 * density).toInt()
        val sectionSpacing = (12 * density).toInt()
        val titleColor = Color.parseColor("#FF7000")
        val bodyColor = Color.WHITE

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(outerPadding, outerPadding, outerPadding, outerPadding)
        }

        fun addSection(title: String, body: String) {
            val titleView = TextView(this).apply {
                text = title
                setTextColor(titleColor)
                textSize = 15f
                textDirection = View.TEXT_DIRECTION_LOCALE
                textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            }
            val bodyView = TextView(this).apply {
                text = body
                setTextColor(bodyColor)
                textSize = 14f
                setLineSpacing(0f, 1.25f)
                textDirection = View.TEXT_DIRECTION_LOCALE
                textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            }
            content.addView(titleView)
            content.addView(bodyView)
            content.addView(View(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    sectionSpacing
                )
            })
        }

        addSection(
            "نبض",
            "نبض مساعد ذكاء اصطناعي محلي يعمل داخل جهازك."
        )
        addSection(
            "للبدء",
            "1. استورد نموذج Gemma بصيغة .litertlm من الإعدادات.\n" +
                "2. اضغط تشغيل نبض.\n" +
                "3. أضف PDF أو صورة إذا أردت تحليل مستند.\n" +
                "4. استخدم الذاكرة بقولك: تذكر أن..."
        )
        addSection(
            "ملاحظة",
            "النماذج غير مدمجة داخل التطبيق بسبب الحجم والترخيص."
        )
        addSection(
            "الخصوصية",
            "المحادثات والمستندات والصور تُعالج محليًا قدر الإمكان."
        )

        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#171717"))
            addView(content)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("مرحبًا بك في نبض")
            .setView(scroll)
            .setPositiveButton("فهمت") { _, _ ->
                preferences.edit().putBoolean(KEY_ONBOARDING_SEEN, true).apply()
            }
            .setNeutralButton("افتح الإعدادات") { _, _ ->
                preferences.edit().putBoolean(KEY_ONBOARDING_SEEN, true).apply()
                openSettingsPage()
            }
            .setCancelable(false)
            .show()
    }

    private fun showHelpDialog() {
        val density = resources.displayMetrics.density
        val outerPadding = (20 * density).toInt()
        val sectionSpacing = (14 * density).toInt()
        val titleColor = Color.parseColor("#FF7000")
        val bodyColor = Color.WHITE
        val secondaryColor = Color.parseColor("#A0A0A0")

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(outerPadding, outerPadding, outerPadding, outerPadding)
        }

        fun addSection(title: String, body: String) {
            val titleView = TextView(this).apply {
                text = title
                setTextColor(titleColor)
                textSize = 15f
                textDirection = View.TEXT_DIRECTION_LOCALE
                textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            }
            val bodyView = TextView(this).apply {
                text = body
                setTextColor(bodyColor)
                textSize = 14f
                setLineSpacing(0f, 1.25f)
                textDirection = View.TEXT_DIRECTION_LOCALE
                textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            }

            content.addView(titleView)
            content.addView(bodyView)
            content.addView(View(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    sectionSpacing
                )
            })
        }

        addSection(
            "1. تشغيل نبض",
            "لاستخدام المحادثة، استورد نموذج Gemma بصيغة .litertlm ثم اضغط تشغيل نبض."
        )
        addSection(
            "2. استيراد النماذج",
            "النماذج غير مدمجة داخل التطبيق بسبب الحجم والترخيص. يمكنك استيراد Gemma للمحادثة و FastVLM للرؤية من الإعدادات."
        )
        addSection(
            "3. المستندات و PDF",
            "يمكنك إضافة PDF أو صورة، ثم سؤال نبض عن المحتوى. يدعم PDF أرقام الصفحات مثل [الصفحة 1]."
        )
        addSection(
            "4. حد صفحات PDF",
            "إذا كان جهازك ضعيفًا، اختر حد صفحات أقل من إعدادات المستندات والبحث."
        )
        addSection(
            "5. Ask Image",
            "بدون نموذج رؤية، يستخدم نبض OCR لتحليل النص داخل الصورة. مع FastVLM يمكنه فهم الصورة بصريًا عند توفر النموذج."
        )
        addSection(
            "6. البحث في المستندات",
            "البحث النصي يعمل مباشرة. البحث الدلالي يحتاج نموذج تضمين وفهرسة المستند أولًا."
        )
        addSection(
            "7. ذاكرة نبض",
            "الذاكرة اختيارية ومحلية. يحفظ نبض فقط ما تطلب منه حفظه بصراحة مثل: تذكر أن اسمي عمار."
        )
        addSection(
            "8. الخريطة",
            "أداة الخريطة تفتح تطبيق خرائط خارجي بعد موافقتك، ولا يطلب نبض صلاحية GPS."
        )
        addSection(
            "9. الخصوصية",
            "نبض يعمل محليًا قدر الإمكان. لا يتم رفع المحادثات أو المستندات أو الصور إلى خادم خارجي من داخل التطبيق."
        )
        addSection(
            "10. عند حدوث مشكلة",
            "استخدم نسخ تقرير بيتا من الإعدادات وأرسله للمطور بدون مشاركة بيانات خاصة."
        )

        val noteView = TextView(this).apply {
            text = "شرح سريع لاستخدام التطبيق"
            setTextColor(secondaryColor)
            textSize = 13f
            textDirection = View.TEXT_DIRECTION_LOCALE
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
        }
        content.addView(noteView, 0)
        content.addView(
            View(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    (10 * density).toInt()
                )
            },
            1
        )

        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#171717"))
            addView(content)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("مساعدة نبض")
            .setView(scroll)
            .setPositiveButton("إغلاق", null)
            .show()
    }

    private fun showModelImportHelpDialog() {
        val density = resources.displayMetrics.density
        val outerPadding = (20 * density).toInt()
        val sectionSpacing = (14 * density).toInt()
        val titleColor = Color.parseColor("#FF7000")
        val bodyColor = Color.WHITE

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(outerPadding, outerPadding, outerPadding, outerPadding)
        }

        fun addSection(title: String, body: String) {
            val titleView = TextView(this).apply {
                text = title
                setTextColor(titleColor)
                textSize = 15f
                textDirection = View.TEXT_DIRECTION_LOCALE
                textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            }
            val bodyView = TextView(this).apply {
                text = body
                setTextColor(bodyColor)
                textSize = 14f
                setLineSpacing(0f, 1.25f)
                textDirection = View.TEXT_DIRECTION_LOCALE
                textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            }
            content.addView(titleView)
            content.addView(bodyView)
            content.addView(View(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    sectionSpacing
                )
            })
        }

        addSection(
            "1. ما هو ملف .litertlm؟",
            "هو ملف النموذج المحلي الذي يستخدمه نبض لتشغيل الذكاء الاصطناعي داخل جهازك."
        )
        addSection(
            "2. أي نموذج أختار؟",
            "ابدأ بـ Gemma E2B إذا كان جهازك متوسطًا أو ضعيفًا. استخدم Gemma E4B إذا كان جهازك أقوى ولديك ذاكرة كافية."
        )
        addSection(
            "3. نموذج الرؤية FastVLM",
            "FastVLM اختياري ويُستخدم لفهم الصور بصريًا في Ask Image. إذا لم تستورده، يستخدم نبض OCR لتحليل النص داخل الصورة."
        )
        addSection(
            "4. نموذج التضمين",
            "نموذج التضمين اختياري ويُستخدم للبحث الدلالي داخل المستندات. إذا لم يكن جاهزًا، يستخدم نبض البحث النصي."
        )
        addSection(
            "5. لماذا النماذج غير مدمجة؟",
            "بسبب الحجم والترخيص. يجب على المستخدم استيراد النماذج يدويًا."
        )
        addSection(
            "6. إذا فشل الاستيراد",
            "تأكد أن الملف بصيغة .litertlm، وأن لديك مساحة كافية، ثم حاول مرة أخرى."
        )

        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#171717"))
            addView(content)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("دليل استيراد النماذج")
            .setView(scroll)
            .setPositiveButton("إغلاق", null)
            .show()
    }

    private fun showReadinessCheckDialog() {
        val selectedModelLabel = when (selectedModel.id) {
            MODEL_ID_E2B -> "Gemma E2B"
            MODEL_ID_E4B -> "Gemma E4B"
            else -> selectedModel.displayName
        }

        val chatModelReady = modelManager.isModelImported(selectedModel.id)
        val visionReady = modelManager.isModelImported(ModelManager.VISION_MODEL.id)
        val embeddingReady = embeddingModelManager.isEmbeddingModelReady()
        val freeBytes = filesDir.usableSpace
        val freeGb = freeBytes / (1024f * 1024f * 1024f)
        val freeSpaceLine = String.format(Locale.US, "المساحة الحرة: %.1f GB", freeGb)
        val isLowSpace = freeGb < 2f

        val content = buildString {
            appendLine(if (chatModelReady) "✅ نموذج المحادثة مستورد" else "⚠️ نموذج المحادثة غير مستورد")
            appendLine("النموذج المحدد: $selectedModelLabel")
            appendLine(if (visionReady) "✅ نموذج الرؤية مستورد" else "اختياري: نموذج الرؤية غير مستورد")
            appendLine(if (embeddingReady) "✅ نموذج التضمين مستورد" else "اختياري: نموذج التضمين غير مستورد")
            appendLine(freeSpaceLine)
            if (isLowSpace) {
                appendLine("⚠️ المساحة الحرة منخفضة")
            }
            appendLine()
            append("يمكن تشغيل نبض إذا كان نموذج المحادثة مستوردًا. باقي النماذج اختيارية.")
        }

        val messageView = TextView(this).apply {
            text = content
            setTextColor(Color.WHITE)
            textSize = 14f
            setLineSpacing(0f, 1.25f)
            textDirection = View.TEXT_DIRECTION_LOCALE
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(24, 12, 24, 8)
        }

        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#171717"))
            addView(messageView)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("فحص جاهزية نبض")
            .setView(scroll)
            .setPositiveButton("إغلاق", null)
            .show()
    }

    private fun showFeedbackDialog() {
        val density = resources.displayMetrics.density
        val padding = (20 * density).toInt()

        val input = EditText(this).apply {
            hint = "اكتب ملاحظتك هنا..."
            setHintTextColor(Color.parseColor("#A0A0A0"))
            setTextColor(Color.WHITE)
            background = ContextCompat.getDrawable(this@MainActivity, R.drawable.bg_input)
            minLines = 5
            maxLines = 10
            gravity = android.view.Gravity.TOP or android.view.Gravity.START
            textDirection = View.TEXT_DIRECTION_LOCALE
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(28, 28, 28, 28)
        }

        val privacyNote = TextView(this).apply {
            text = "لا تكتب كلمات مرور أو معلومات حساسة. سيتم تجهيز النص للمشاركة يدويًا فقط."
            setTextColor(Color.parseColor("#A0A0A0"))
            textSize = 12f
            textDirection = View.TEXT_DIRECTION_LOCALE
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            setLineSpacing(0f, 1.2f)
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setBackgroundColor(Color.parseColor("#171717"))
            setPadding(padding, padding, padding, padding)
            addView(
                input,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )
            addView(
                privacyNote,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = (10 * density).toInt()
                }
            )
        }

        fun buildFeedbackText(note: String): String {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            val versionName = packageInfo.versionName ?: "غير متاح"
            val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode.toString()
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toString()
            }
            val date = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()).format(Date())
            val device = "${Build.MANUFACTURER} ${Build.MODEL}"
            val androidInfo = "Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})"

            return """
                ملاحظة مستخدم - نبض
                التاريخ: $date
                الإصدار: $versionName ($versionCode)
                الجهاز: $device
                النظام: $androidInfo
                الملاحظة:
                $note
            """.trimIndent()
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("إرسال ملاحظة للمطور")
            .setView(content)
            .setNegativeButton("إلغاء", null)
            .setNeutralButton("نسخ الملاحظة") { _, _ ->
                val note = input.text?.toString()?.trim().orEmpty()
                if (note.isBlank()) {
                    Toast.makeText(this, "اكتب الملاحظة أولًا.", Toast.LENGTH_SHORT).show()
                    return@setNeutralButton
                }
                val formatted = buildFeedbackText(note)
                copyToClipboard("Nabd User Feedback", formatted)
                Toast.makeText(this, "تم نسخ الملاحظة", Toast.LENGTH_SHORT).show()
            }
            .setPositiveButton("مشاركة") { _, _ ->
                val note = input.text?.toString()?.trim().orEmpty()
                if (note.isBlank()) {
                    Toast.makeText(this, "اكتب الملاحظة أولًا.", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val formatted = buildFeedbackText(note)
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, formatted)
                }
                startActivity(Intent.createChooser(shareIntent, "مشاركة الملاحظة"))
            }
            .show()
    }

    private fun showWhatsNewDialog() {
        val density = resources.displayMetrics.density
        val outerPadding = (20 * density).toInt()
        val titleColor = Color.parseColor("#FF7000")
        val bodyColor = Color.WHITE
        val secondaryColor = Color.parseColor("#A0A0A0")

        val changesText = """
            v0.1.2 Beta

            - تبسيط واجهة الخيارات والإعدادات.
            - إصلاح مشكلة Room Migration عند التحديث.
            - تحسين معالجة PDF مع دعم حد صفحات قابل للتخصيص.
            - إضافة أرقام الصفحات إلى نصوص PDF.
            - إصلاح اختيار النموذج من الإعدادات.
            - تحسين استرداد المستند المحدد في RAG.
            - إضافة ذاكرة نبض المحلية الاختيارية.
            - إضافة مساعدة داخل التطبيق ودليل استيراد النماذج.
            - تحسين تقرير بيتا وإرسال الملاحظات.
            - تحسينات استقرار وتجربة مستخدم.

            ملاحظة:
            نبض ما زال في مرحلة Beta، وقد يختلف الأداء حسب الجهاز والنموذج.
        """.trimIndent()

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(outerPadding, outerPadding, outerPadding, outerPadding)
        }

        val versionTitle = TextView(this).apply {
            text = "v0.1.2 Beta"
            setTextColor(titleColor)
            textSize = 16f
            textDirection = View.TEXT_DIRECTION_LOCALE
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
        }

        val bodyView = TextView(this).apply {
            text = changesText
            setTextColor(bodyColor)
            textSize = 14f
            setLineSpacing(0f, 1.25f)
            textDirection = View.TEXT_DIRECTION_LOCALE
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
        }

        val noteView = TextView(this).apply {
            text = "آخر تحسينات النسخة التجريبية"
            setTextColor(secondaryColor)
            textSize = 12f
            textDirection = View.TEXT_DIRECTION_LOCALE
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
        }

        content.addView(noteView)
        content.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (8 * density).toInt()
            )
        })
        content.addView(versionTitle)
        content.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (10 * density).toInt()
            )
        })
        content.addView(bodyView)

        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#171717"))
            addView(content)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("ما الجديد في نبض")
            .setView(scroll)
            .setPositiveButton("إغلاق", null)
            .setNeutralButton("نسخ التغييرات") { _, _ ->
                copyToClipboard("Nabd What's New", changesText)
                Toast.makeText(this, "تم نسخ التغييرات", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun showLiteRtDiagnosticsDialog() {
        val diagnostics = buildLiteRtDiagnosticsData()
        MaterialAlertDialogBuilder(this)
            .setTitle("تشخيص نموذج الذكاء")
            .setView(buildLiteRtDiagnosticsView(diagnostics))
            .setPositiveButton("إغلاق", null)
            .setNeutralButton("نسخ التشخيص") { _, _ ->
                copyToClipboard("LiteRT-LM Diagnostics", LiteRtDiagnosticsFormatter.buildReport(diagnostics))
                Toast.makeText(this, "تم نسخ التشخيص", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun buildLiteRtDiagnosticsData(): LiteRtDiagnosticsData {
        val modelFile = modelManager.getModelFile(selectedModel.id)
        val modelSizeBytes = modelManager.modelSizeBytes(selectedModel.id)
        val engineReady = textInferenceEngine?.isReady() == true && loadedModelId == selectedModel.id
        val backendLabel = "CPU"

        return LiteRtDiagnosticsData(
            currentModelLabel = when (selectedModel.id) {
                MODEL_ID_E2B -> "Gemma E2B"
                MODEL_ID_E4B -> "Gemma E4B"
                else -> selectedModel.displayName
            },
            modelStatusLabel = currentModelStatusLabel(),
            importedModelSizeLabel = if (modelSizeBytes > 0L) formatStorageSize(modelSizeBytes) else "غير متاح",
            modelFileLabel = if (modelFile != null && modelFile.exists()) {
                "files/models/${selectedModel.id}/${modelFile.name}"
            } else {
                selectedModel.fileName
            },
            engineStatusLabel = if (engineReady) "جاهز" else "غير جاهز",
            lastResponseLatencyLabel = lastFirstTokenLatencyMs?.let { "${it}ms" } ?: "غير متاح بعد",
            lastGenerationDurationLabel = lastGenerationDurationMs?.let { "${it}ms" } ?: "غير متاح بعد",
            lastResponseCharCountLabel = if (lastResponseCharCount > 0) {
                "$lastResponseCharCount"
            } else {
                "غير متاح بعد"
            },
            backendLabel = backendLabel
        )
    }

    private fun buildLiteRtDiagnosticsView(data: LiteRtDiagnosticsData): View {
        val density = resources.displayMetrics.density
        val outerPadding = (20 * density).toInt()
        val bottomSpacing = (14 * density).toInt()

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(outerPadding, outerPadding / 2, outerPadding, outerPadding / 2)
        }

        appendDiagnosticsField(content, "النموذج الحالي", data.currentModelLabel, Color.WHITE, bottomSpacing)
        appendDiagnosticsField(
            content,
            "حالة النموذج",
            data.modelStatusLabel,
            when (data.modelStatusLabel) {
                "مشغّل" -> Color.parseColor("#10B981")
                "جاري التشغيل" -> Color.parseColor("#FF7000")
                else -> Color.parseColor("#EF4444")
            },
            bottomSpacing
        )
        appendDiagnosticsField(content, "حجم النموذج المستورد", data.importedModelSizeLabel, Color.WHITE, bottomSpacing)
        appendDiagnosticsField(content, "ملف النموذج", data.modelFileLabel, Color.parseColor("#A0A0A0"), bottomSpacing)
        appendDiagnosticsField(
            content,
            "Engine",
            data.engineStatusLabel,
            if (data.engineStatusLabel == "جاهز") Color.parseColor("#10B981") else Color.parseColor("#EF4444"),
            bottomSpacing
        )
        appendDiagnosticsField(content, "آخر زمن استجابة", data.lastResponseLatencyLabel, Color.WHITE, bottomSpacing)
        appendDiagnosticsField(content, "آخر مدة توليد", data.lastGenerationDurationLabel, Color.WHITE, bottomSpacing)
        appendDiagnosticsField(content, "آخر عدد أحرف الرد", data.lastResponseCharCountLabel, Color.WHITE, bottomSpacing)
        appendDiagnosticsField(content, "Backend", data.backendLabel, Color.parseColor("#FF7000"), bottomSpacing)
        appendDiagnosticsField(
            content,
            "ملاحظة",
            "يعتمد الأداء على الجهاز والذاكرة وحجم النموذج.",
            Color.parseColor("#A0A0A0"),
            0
        )

        return ScrollView(this).apply {
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            isFillViewport = true
            addView(
                content,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }
    }

    private fun showLocalModelManagerDialog() {
        localModelManagerDialog?.dismiss()
        localModelManagerQuickTestJob?.cancel()
        localModelManagerQuickTestJob = null

        val density = resources.displayMetrics.density
        val outerPadding = (20 * density).toInt()

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(outerPadding, outerPadding / 2, outerPadding, outerPadding / 2)
        }

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("إدارة النماذج المحلية")
            .setView(
                ScrollView(this).apply {
                    layoutDirection = View.LAYOUT_DIRECTION_RTL
                    isFillViewport = true
                    addView(
                        content,
                        ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                    )
                }
            )
            .setPositiveButton("إغلاق", null)
            .setNeutralButton("تحديث", null)
            .create()

        localModelManagerDialog = dialog
        localModelManagerContentContainer = content

        dialog.setOnShowListener {
            dialog.getButton(DialogInterface.BUTTON_NEUTRAL)?.setOnClickListener {
                renderLocalModelManagerDialog()
            }
            renderLocalModelManagerDialog()
        }
        dialog.setOnDismissListener {
            if (localModelManagerDialog === dialog) {
                localModelManagerQuickTestJob?.cancel()
                localModelManagerQuickTestJob = null
                clearLocalModelManagerDialogRefs()
            }
        }
        dialog.show()
        renderLocalModelManagerDialog()
    }

    private fun clearLocalModelManagerDialogRefs() {
        localModelManagerDialog = null
        localModelManagerContentContainer = null
    }

    private fun renderLocalModelManagerDialog() {
        val container = localModelManagerContentContainer ?: return
        container.removeAllViews()

        val density = resources.displayMetrics.density
        val sectionSpacing = (18 * density).toInt()
        val itemSpacing = (12 * density).toInt()
        val accentColor = Color.parseColor("#FF7000")
        val secondaryColor = Color.parseColor("#A0A0A0")

        val modelStatus = localModelStatusLabel()
        val modelStatusColor = when (modelStatus) {
            "جاهز" -> Color.parseColor("#10B981")
            "خطأ" -> Color.parseColor("#EF4444")
            else -> secondaryColor
        }

        container.addView(
            TextView(this).apply {
                text = "النموذج الحالي"
                setTextColor(accentColor)
                textSize = 15f
                textDirection = View.TEXT_DIRECTION_LOCALE
                textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            }
        )
        appendDiagnosticsField(
            container = container,
            title = "الاسم",
            value = localModelDisplayName(selectedModel),
            valueColor = Color.WHITE,
            bottomMargin = itemSpacing
        )
        appendDiagnosticsField(
            container = container,
            title = "الحالة",
            value = modelStatus,
            valueColor = modelStatusColor,
            bottomMargin = itemSpacing
        )
        appendDiagnosticsField(
            container = container,
            title = "الحجم",
            value = modelManager.modelSizeBytes(selectedModel.id).takeIf { it > 0L }?.let(::formatStorageSize)
                ?: "غير معروف",
            valueColor = Color.WHITE,
            bottomMargin = itemSpacing
        )
        appendDiagnosticsField(
            container = container,
            title = "مسار التخزين",
            value = safeModelStoragePath(selectedModel),
            valueColor = secondaryColor,
            bottomMargin = sectionSpacing
        )

        val engineState = localEngineStateLabel()
        val engineStateColor = when (engineState) {
            "جاهز" -> Color.parseColor("#10B981")
            "فشل التحميل" -> Color.parseColor("#EF4444")
            "يتم التحميل" -> Color.parseColor("#FF7000")
            else -> secondaryColor
        }

        container.addView(
            TextView(this).apply {
                text = "حالة المحرك المحلي"
                setTextColor(accentColor)
                textSize = 15f
                textDirection = View.TEXT_DIRECTION_LOCALE
                textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            }
        )
        appendDiagnosticsField(
            container = container,
            title = "حالة المحرك",
            value = engineState,
            valueColor = engineStateColor,
            bottomMargin = itemSpacing
        )
        appendDiagnosticsField(
            container = container,
            title = "آخر خطأ",
            value = localEngineLastErrorMessage?.safeTruncate(200) ?: "-",
            valueColor = if (localEngineLastErrorMessage.isNullOrBlank()) secondaryColor else Color.parseColor("#EF4444"),
            bottomMargin = sectionSpacing
        )

        container.addView(
            TextView(this).apply {
                text = "اختبارات سريعة"
                setTextColor(accentColor)
                textSize = 15f
                textDirection = View.TEXT_DIRECTION_LOCALE
                textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            }
        )

        val canRunInferenceTests = !isLoadingModel && !isGenerating && loadedModelId == selectedModel.id &&
            textInferenceEngine?.isReady() == true
        val isQuickTestRunning = localModelManagerQuickTestJob?.isActive == true

        val shortTestButton = Button(this).apply {
            text = "اختبار توليد قصير"
            isEnabled = canRunInferenceTests && !isQuickTestRunning
            alpha = if (isEnabled) 1f else 0.6f
            setOnClickListener { startLocalModelQuickTest(LocalModelQuickTestType.SHORT_GENERATION) }
        }
        container.addView(
            shortTestButton,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = (8 * density).toInt() }
        )

        val memoryTestButton = Button(this).apply {
            text = "اختبار الذاكرة"
            isEnabled = !isQuickTestRunning
            alpha = if (isEnabled) 1f else 0.6f
            setOnClickListener { startLocalModelQuickTest(LocalModelQuickTestType.MEMORY) }
        }
        container.addView(
            memoryTestButton,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = (8 * density).toInt() }
        )

        val speedTestButton = Button(this).apply {
            text = "اختبار السرعة"
            isEnabled = canRunInferenceTests && !isQuickTestRunning
            alpha = if (isEnabled) 1f else 0.6f
            setOnClickListener { startLocalModelQuickTest(LocalModelQuickTestType.SPEED) }
        }
        container.addView(
            speedTestButton,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = (8 * density).toInt() }
        )

        appendDiagnosticsField(
            container = container,
            title = "نتيجة الاختبار",
            value = localModelManagerLastQuickTestMessage,
            valueColor = when {
                localModelManagerLastQuickTestMessage.startsWith("فشل") -> Color.parseColor("#EF4444")
                localModelManagerLastQuickTestMessage.startsWith("جارٍ") -> Color.parseColor("#FF7000")
                else -> Color.WHITE
            },
            bottomMargin = 0
        )
    }

    private fun localModelDisplayName(model: SupportedModel): String {
        return when (model.id) {
            MODEL_ID_E2B -> "Gemma E2B"
            MODEL_ID_E4B -> "Gemma E4B"
            else -> model.displayName
        }
    }

    private fun localModelStatusLabel(): String {
        if (textInferenceEngine?.isReady() == true && loadedModelId == selectedModel.id) return "جاهز"
        if (localEngineLastErrorMessage != null && !isLoadingModel) return "خطأ"
        return "غير محمّل"
    }

    private fun localEngineStateLabel(): String {
        return when {
            isLoadingModel -> "يتم التحميل"
            textInferenceEngine?.isReady() == true && loadedModelId == selectedModel.id -> "جاهز"
            !localEngineLastErrorMessage.isNullOrBlank() -> "فشل التحميل"
            else -> "غير محمّل"
        }
    }

    private fun safeModelStoragePath(model: SupportedModel): String {
        val fileName = modelManager.getModelFile(model.id)?.name ?: model.fileName
        return "files/models/${model.id}/$fileName"
    }

    private fun startLocalModelQuickTest(testType: LocalModelQuickTestType) {
        if (localModelManagerQuickTestJob?.isActive == true) return

        localModelManagerLastQuickTestMessage = "جارٍ تنفيذ ${testType.label}..."
        renderLocalModelManagerDialog()

        localModelManagerQuickTestJob = scope.launch {
            val resultMessage = try {
                when (testType) {
                    LocalModelQuickTestType.SHORT_GENERATION -> runLocalModelShortGenerationTest()
                    LocalModelQuickTestType.MEMORY -> runLocalModelMemoryTest()
                    LocalModelQuickTestType.SPEED -> runLocalModelSpeedTest()
                }
            } catch (cancelled: CancellationException) {
                "تم إلغاء ${testType.label}."
            } catch (oom: OutOfMemoryError) {
                "فشل ${testType.label}: ${oom.message ?: "نفاد الذاكرة"}"
            } catch (exception: Exception) {
                "فشل ${testType.label}: ${exception.message ?: "خطأ غير متوقع"}"
            }

            localModelManagerLastQuickTestMessage = resultMessage
            localModelManagerQuickTestJob = null
            renderLocalModelManagerDialog()
        }
        renderLocalModelManagerDialog()
    }

    private suspend fun runLocalModelShortGenerationTest(): String {
        val probe = runLocalModelInferenceProbe(LOCAL_MODEL_SHORT_TEST_PROMPT)
        return LocalModelDiagnosticsFormatter.formatGenerationTest(
            success = probe.success,
            firstTokenLatencyMs = probe.firstTokenLatencyMs,
            totalDurationMs = probe.totalDurationMs,
            responseCharCount = probe.responseCharCount,
            errorMessage = probe.errorMessage
        )
    }

    private suspend fun runLocalModelSpeedTest(): String {
        val probe = runLocalModelInferenceProbe(LOCAL_MODEL_SPEED_TEST_PROMPT)
        return LocalModelDiagnosticsFormatter.formatSpeedTest(
            success = probe.success,
            firstTokenLatencyMs = probe.firstTokenLatencyMs,
            totalDurationMs = probe.totalDurationMs,
            responseCharCount = probe.responseCharCount,
            errorMessage = probe.errorMessage
        )
    }

    private fun runLocalModelMemoryTest(): String {
        val runtime = Runtime.getRuntime()
        val used = runtime.totalMemory() - runtime.freeMemory()
        val max = runtime.maxMemory()
        val freeInsideHeap = runtime.freeMemory()
        return LocalModelDiagnosticsFormatter.formatMemoryTest(
            usedBytes = used,
            freeInsideHeapBytes = freeInsideHeap,
            maxHeapBytes = max
        )
    }

    private suspend fun runLocalModelInferenceProbe(prompt: String): LocalModelProbeResult {
        if (isLoadingModel) {
            return LocalModelProbeResult(success = false, errorMessage = "المحرك المحلي قيد التحميل.")
        }
        if (isGenerating) {
            return LocalModelProbeResult(success = false, errorMessage = "يوجد توليد جارٍ. أعد الاختبار بعد الانتهاء.")
        }

        val engine = textInferenceEngine
        if (engine == null || !engine.isReady() || loadedModelId != selectedModel.id) {
            return LocalModelProbeResult(success = false, errorMessage = "النموذج الحالي غير جاهز للتشغيل.")
        }

        return withContext(Dispatchers.IO) {
            val startedAt = SystemClock.elapsedRealtime()
            var firstTokenLatencyMs: Long? = null
            val output = StringBuilder()

            try {
                engine.generate(prompt.safeTruncate(PROMPT_TEXT_LIMIT)).collect { chunk ->
                    if (firstTokenLatencyMs == null) {
                        firstTokenLatencyMs = SystemClock.elapsedRealtime() - startedAt
                    }
                    if (output.length < LOCAL_MODEL_TEST_MAX_OUTPUT_CHARS) {
                        output.append(chunk)
                    }
                }
                val finishedAt = SystemClock.elapsedRealtime()
                LocalModelProbeResult(
                    success = true,
                    firstTokenLatencyMs = firstTokenLatencyMs,
                    totalDurationMs = finishedAt - startedAt,
                    responseCharCount = cleanForDisplay(output.toString(), preserveMarkdown = false).length
                )
            } catch (oom: OutOfMemoryError) {
                LocalModelProbeResult(success = false, errorMessage = oom.message ?: "نفاد الذاكرة.")
            } catch (exception: Exception) {
                LocalModelProbeResult(success = false, errorMessage = exception.message ?: "فشل الاختبار.")
            }
        }
    }

    private fun showLastRagOperationDetailsDialog(operation: LastRagOperationResult) {
        val density = resources.displayMetrics.density
        val padding = (16 * density).toInt()

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
        }

        val addText = { label: String, text: String, isBold: Boolean ->
            content.addView(TextView(this).apply {
                this.text = label
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.nabd_accent))
                if (isBold) setTypeface(null, android.graphics.Typeface.BOLD)
                setPadding(0, (10 * density).toInt(), 0, 0)
            })
            content.addView(TextView(this).apply {
                this.text = text
                setTextColor(Color.WHITE)
                setPadding(0, 0, 0, (10 * density).toInt())
            })
        }

        addText("السؤال:", operation.query, true)
        addText("المقاطع المسترجعة:", "${operation.chunks.size} مقاطع", true)
        
        operation.chunks.forEachIndexed { index, chunk ->
            addText("مقطع [${index + 1}] (التشابه: ${chunk.similarity}):", chunk.text.safeTruncate(150) + "...", false)
        }

        addText("المقاطع المرفوضة (أقل من العتبة):", "${operation.rejectedCount}", true)
        addText("النص النهائي المرسل للنموذج:", operation.fullContextSentToModel.safeTruncate(300) + "...", true)

        val scrollView = ScrollView(this).apply { addView(content) }

        MaterialAlertDialogBuilder(this)
            .setTitle("تفاصيل آخر استرجاع")
            .setView(scrollView)
            .setPositiveButton("إغلاق", null)
            .setNeutralButton("نسخ السياق الكامل") { _, _ ->
                copyToClipboard("RAG Context", operation.fullContextSentToModel)
                Toast.makeText(this, "تم نسخ السياق", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun showRagDiagnosticsDialog() {
        scope.launch {
            val selectedDocument = getSelectedDocument()
            val diagnostics = withContext(Dispatchers.IO) {
                buildRagDiagnosticsData(selectedDocument)
            }

            MaterialAlertDialogBuilder(this@MainActivity)
               .setTitle("تشخيص البحث الدلالي")
               .setView(buildRagDiagnosticsView(diagnostics))
               .setPositiveButton("إغلاق", null)
               .apply {
                   if (diagnostics.canBuildIndex) {
                       setNeutralButton("إنشاء فهرس") { _, _ ->
                           confirmBuildSelectedDocumentSemanticIndex()
                       }
                   }
                   if (diagnostics.lastOperation != null) {
                       setNegativeButton("آخر استرجاع") { _, _ ->
                           showLastRagOperationDetailsDialog(diagnostics.lastOperation)
                       }
                   }
               }
               .show()

        }
    }

    private fun buildRagDiagnosticsData(selectedDocument: LocalDocument?): RagDiagnosticsData {
        val modelReady = embeddingModelManager.isEmbeddingModelReady()
        val indexInfo = selectedDocument?.id?.let(embeddingStore::getIndexInfo)
        val lastState = when {
            !modelReady -> "استورد نموذج تضمين أولًا."
            selectedDocument == null -> "اختر مستندًا من مكتبة المستندات."
            indexInfo == null || indexInfo.chunkCount <= 0 -> "أنشئ فهرسًا دلاليًا للمستند."
            else -> "البحث الدلالي جاهز لهذا المستند."
        }

        return RagDiagnosticsData(
            ragModeLabel = ragModeLabel(currentRagMode()),
            embeddingBackendLabel = embeddingBackendLabel(currentEmbeddingBackend()),
            embeddingModelImported = modelReady,
            embeddingModelDisplayPath = "files/${EmbeddingModelManager.EMBEDDING_MODEL_RELATIVE_PATH}",
            selectedDocumentTitle = selectedDocument?.title ?: DocumentMessageFormatter.noSelectedDocumentMessage(),
            indexInfo = indexInfo,
            lastState = lastState,
            canBuildIndex = modelReady && selectedDocument != null
        )
    }

    private fun buildRagDiagnosticsView(data: RagDiagnosticsData): View {
        val density = resources.displayMetrics.density
        val outerPadding = (20 * density).toInt()
        val bottomSpacing = (14 * density).toInt()

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(outerPadding, outerPadding / 2, outerPadding, outerPadding / 2)
        }

        appendDiagnosticsField(
            content,
            "وضع البحث في المستندات",
            data.ragModeLabel,
            Color.WHITE,
            bottomSpacing
        )
        appendDiagnosticsField(
            content,
            "محرك التضمين",
            data.embeddingBackendLabel,
            Color.parseColor("#FF7000"),
            bottomSpacing
        )
        appendDiagnosticsField(
            content,
            "نموذج التضمين",
            buildString {
                append(if (data.embeddingModelImported) "مستورد" else "غير مستورد")
                append("\n")
                append(data.embeddingModelDisplayPath)
            },
            if (data.embeddingModelImported) Color.parseColor("#10B981") else Color.parseColor("#EF4444"),
            bottomSpacing
        )
        appendDiagnosticsField(
            content,
            "المستند المحدد",
            data.selectedDocumentTitle,
            Color.WHITE,
            bottomSpacing
        )

        val indexValue = when {
            data.selectedDocumentTitle == DocumentMessageFormatter.noSelectedDocumentMessage() -> "اختر مستندًا أولًا."
            data.indexInfo == null -> "غير موجود"
            else -> {
                buildString {
                    append("موجود")
                    append("\nعدد المقاطع: ${data.indexInfo.chunkCount}")
                    append("\nتاريخ إنشاء الفهرس: ${formatDateTime(data.indexInfo.createdAt)}")
                }
            }
        }
        appendDiagnosticsField(
            content,
            "الفهرس الدلالي للمستند",
            indexValue,
            if (data.indexInfo != null) Color.parseColor("#10B981") else Color.parseColor("#EF4444"),
            bottomSpacing
        )
        appendDiagnosticsField(
            content,
            "آخر حالة",
            data.lastState,
            if (data.lastState == "البحث الدلالي جاهز لهذا المستند.") {
                Color.parseColor("#10B981")
            } else {
                Color.parseColor("#FF7000")
            },
            0
        )

        return ScrollView(this).apply {
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            isFillViewport = true
            addView(
                content,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }
    }

    private fun showBackgroundTasksDialog() {
        backgroundTasksDialog?.dismiss()
        detachBackgroundTasksObserver()

        val density = resources.displayMetrics.density
        val outerPadding = (20 * density).toInt()
        val sectionBottomSpacing = (16 * density).toInt()

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(outerPadding, outerPadding / 2, outerPadding, outerPadding / 2)
        }

        val pdfSectionTitle = TextView(this).apply {
            text = "معالجة ملفات PDF"
            setTextColor(Color.parseColor("#FF7000"))
            textSize = 15f
            textDirection = View.TEXT_DIRECTION_LOCALE
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
        }
        val pdfContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (8 * density).toInt()
                bottomMargin = sectionBottomSpacing
            }
        }

        val semanticSectionTitle = TextView(this).apply {
            text = "الفهرسة الدلالية"
            setTextColor(Color.parseColor("#FF7000"))
            textSize = 15f
            textDirection = View.TEXT_DIRECTION_LOCALE
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
        }
        val semanticContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (8 * density).toInt()
            }
        }

        val emptyStateView = TextView(this).apply {
            text = "لا توجد مهام خلفية حاليًا."
            setTextColor(Color.parseColor("#A0A0A0"))
            textSize = 14f
            textDirection = View.TEXT_DIRECTION_LOCALE
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            visibility = View.GONE
            setPadding(0, (12 * density).toInt(), 0, 0)
        }

        content.addView(pdfSectionTitle)
        content.addView(pdfContainer)
        content.addView(semanticSectionTitle)
        content.addView(semanticContainer)
        content.addView(emptyStateView)

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("مهام الخلفية")
            .setView(
                ScrollView(this).apply {
                    layoutDirection = View.LAYOUT_DIRECTION_RTL
                    isFillViewport = true
                    addView(
                        content,
                        ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                    )
                }
            )
            .setPositiveButton("إغلاق", null)
            .setNeutralButton("تحديث", null)
            .setNegativeButton("إلغاء مهام PDF", null)
            .create()

        backgroundTasksDialog = dialog
        backgroundTasksPdfContainer = pdfContainer
        backgroundTasksSemanticContainer = semanticContainer
        backgroundTasksEmptyStateView = emptyStateView

        dialog.setOnShowListener {
            dialog.getButton(DialogInterface.BUTTON_NEUTRAL)?.setOnClickListener {
                refreshBackgroundTasksPdfList()
            }
            dialog.getButton(DialogInterface.BUTTON_NEGATIVE)?.setOnClickListener {
                workManager.cancelAllWorkByTag(PDF_PROCESSING_TAG)
                Toast.makeText(this, "تم طلب إلغاء مهام PDF", Toast.LENGTH_SHORT).show()
                refreshBackgroundTasksPdfList()
            }
            renderBackgroundTasksDialog()
            refreshBackgroundTasksPdfList()
        }
        dialog.setOnDismissListener {
            if (backgroundTasksDialog === dialog) {
                clearBackgroundTasksDialogRefs()
                detachBackgroundTasksObserver()
            }
        }
        dialog.show()

        observeBackgroundPdfTasks()
        renderBackgroundTasksDialog()
    }

    private fun observeBackgroundPdfTasks() {
        detachBackgroundTasksObserver()
        val liveData = workManager.getWorkInfosByTagLiveData(PDF_PROCESSING_TAG)
        val observer = Observer<List<WorkInfo>> { workInfos ->
            backgroundTasksLatestPdfWorkInfos = workInfos
            renderBackgroundTasksDialog()
        }
        backgroundTasksPdfWorkLiveData = liveData
        backgroundTasksPdfObserver = observer
        liveData.observe(this, observer)
    }

    private fun detachBackgroundTasksObserver() {
        val liveData = backgroundTasksPdfWorkLiveData
        val observer = backgroundTasksPdfObserver
        if (liveData != null && observer != null) {
            liveData.removeObserver(observer)
        }
        backgroundTasksPdfWorkLiveData = null
        backgroundTasksPdfObserver = null
    }

    private fun clearBackgroundTasksDialogRefs() {
        backgroundTasksDialog = null
        backgroundTasksPdfContainer = null
        backgroundTasksSemanticContainer = null
        backgroundTasksEmptyStateView = null
    }

    private fun refreshBackgroundTasksPdfList() {
        scope.launch {
            val workInfos = runCatching {
                withContext(Dispatchers.IO) {
                    workManager.getWorkInfosByTag(PDF_PROCESSING_TAG).get()
                }
            }.getOrDefault(emptyList())
            backgroundTasksLatestPdfWorkInfos = workInfos
            renderBackgroundTasksDialog()
        }
    }

    private fun renderBackgroundTasksDialog() {
        val pdfContainer = backgroundTasksPdfContainer ?: return
        val semanticContainer = backgroundTasksSemanticContainer ?: return
        val emptyStateView = backgroundTasksEmptyStateView ?: return

        renderPdfTasksSection(pdfContainer, backgroundTasksLatestPdfWorkInfos)
        renderSemanticIndexingSection(semanticContainer)

        val hasRunningPdf = hasActivePdfTasks(backgroundTasksLatestPdfWorkInfos)
        backgroundTasksDialog?.getButton(DialogInterface.BUTTON_NEGATIVE)?.visibility =
            if (hasRunningPdf) View.VISIBLE else View.GONE

        val showEmptyState = backgroundTasksLatestPdfWorkInfos.isEmpty() &&
            semanticIndexingStatus == SEMANTIC_INDEX_STATUS_IDLE
        emptyStateView.visibility = if (showEmptyState) View.VISIBLE else View.GONE
    }

    private fun renderPdfTasksSection(container: LinearLayout, workInfos: List<WorkInfo>) {
        container.removeAllViews()
        if (workInfos.isEmpty()) {
            appendDiagnosticsField(
                container = container,
                title = "معالجة ملفات PDF",
                value = "لا توجد مهام PDF حاليًا.",
                valueColor = Color.parseColor("#A0A0A0"),
                bottomMargin = 0
            )
            return
        }

        val sortedInfos = workInfos.sortedWith(
            compareBy<WorkInfo>({ pdfTaskStatePriority(it.state) }, { it.id.toString() })
        ).take(BACKGROUND_TASKS_MAX_PDF_ITEMS)

        val density = resources.displayMetrics.density
        val itemSpacing = (12 * density).toInt()
        sortedInfos.forEachIndexed { index, workInfo ->
            val title = workInfo.outputData.getString(PdfProcessingWorker.KEY_PDF_TITLE)
                ?: workInfo.progress.getString(PdfProcessingWorker.KEY_PDF_TITLE)
                ?: PdfMessageFormatter.defaultPdfTitle()
            val value = buildPdfTaskDetails(workInfo)
            appendDiagnosticsField(
                container = container,
                title = "${index + 1}. $title",
                value = value,
                valueColor = pdfWorkStateColor(workInfo.state),
                bottomMargin = itemSpacing
            )
        }
    }

    private fun buildPdfTaskDetails(workInfo: WorkInfo): String {
        val page = workInfo.progress.getInt(PdfProcessingWorker.KEY_PROGRESS_PAGE, 0)
        val total = workInfo.progress.getInt(PdfProcessingWorker.KEY_PROGRESS_TOTAL, 0)
        val extractedChars = workInfo.outputData.getInt(PdfProcessingWorker.KEY_EXTRACTED_CHARS, 0)
        val errorMessage = workInfo.outputData.getString(PdfProcessingWorker.KEY_ERROR_MESSAGE)

        return buildString {
            appendLine("الحالة: ${pdfWorkStateLabel(workInfo.state)}")
            if (page > 0 && total > 0) {
                appendLine("التقدم: الصفحة $page من $total")
            }
            if (workInfo.state == WorkInfo.State.SUCCEEDED) {
                append("الأحرف المستخرجة: $extractedChars")
            } else if (workInfo.state == WorkInfo.State.FAILED && !errorMessage.isNullOrBlank()) {
                append("الخطأ: $errorMessage")
            } else if (workInfo.state == WorkInfo.State.CANCELLED) {
                append("تم إلغاء المهمة.")
            }
        }.trim()
    }

    private fun renderSemanticIndexingSection(container: LinearLayout) {
        container.removeAllViews()
        val statusLabel = semanticStatusLabel(semanticIndexingStatus)
        val startedAt = semanticIndexingStartedAt?.let(::formatDateTime) ?: "-"
        val documentTitle = semanticIndexingDocumentTitle ?: "-"
        val details = buildString {
            appendLine("الحالة: $statusLabel")
            appendLine("المستند: $documentTitle")
            appendLine("وقت البدء: $startedAt")
            append("آخر رسالة: ${semanticIndexingLastMessage.ifBlank { "-" }}")
        }
        appendDiagnosticsField(
            container = container,
            title = "الفهرسة الدلالية",
            value = details,
            valueColor = semanticStatusColor(semanticIndexingStatus),
            bottomMargin = 0
        )
    }

    private fun hasActivePdfTasks(workInfos: List<WorkInfo>): Boolean {
        return workInfos.any {
            it.state == WorkInfo.State.ENQUEUED ||
                it.state == WorkInfo.State.RUNNING ||
                it.state == WorkInfo.State.BLOCKED
        }
    }

    private fun pdfTaskStatePriority(state: WorkInfo.State): Int {
        return when (state) {
            WorkInfo.State.RUNNING -> 0
            WorkInfo.State.ENQUEUED -> 1
            WorkInfo.State.BLOCKED -> 2
            WorkInfo.State.FAILED -> 3
            WorkInfo.State.CANCELLED -> 4
            WorkInfo.State.SUCCEEDED -> 5
        }
    }

    private fun pdfWorkStateLabel(state: WorkInfo.State): String {
        return when (state) {
            WorkInfo.State.RUNNING -> "RUNNING • جاري"
            WorkInfo.State.SUCCEEDED -> "SUCCEEDED • مكتمل"
            WorkInfo.State.FAILED -> "FAILED • فشل"
            WorkInfo.State.CANCELLED -> "CANCELLED • ملغى"
            WorkInfo.State.ENQUEUED -> "ENQUEUED • بانتظار التنفيذ"
            WorkInfo.State.BLOCKED -> "BLOCKED • بانتظار التبعيات"
        }
    }

    private fun pdfWorkStateColor(state: WorkInfo.State): Int {
        return when (state) {
            WorkInfo.State.SUCCEEDED -> Color.parseColor("#10B981")
            WorkInfo.State.FAILED, WorkInfo.State.CANCELLED -> Color.parseColor("#EF4444")
            else -> Color.parseColor("#FF7000")
        }
    }

    private fun semanticStatusLabel(status: String): String {
        return when (status) {
            SEMANTIC_INDEX_STATUS_RUNNING -> "جاري"
            SEMANTIC_INDEX_STATUS_SUCCESS -> "مكتمل"
            SEMANTIC_INDEX_STATUS_FAILED -> "فشل"
            else -> "IDLE • لا توجد مهمة"
        }
    }

    private fun semanticStatusColor(status: String): Int {
        return when (status) {
            SEMANTIC_INDEX_STATUS_RUNNING -> Color.parseColor("#FF7000")
            SEMANTIC_INDEX_STATUS_SUCCESS -> Color.parseColor("#10B981")
            SEMANTIC_INDEX_STATUS_FAILED -> Color.parseColor("#EF4444")
            else -> Color.parseColor("#A0A0A0")
        }
    }

    private fun appendDiagnosticsField(
        container: LinearLayout,
        title: String,
        value: String,
        valueColor: Int,
        bottomMargin: Int
    ) {
        val wrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }

        wrapper.addView(
            TextView(this).apply {
                text = title
                setTextColor(Color.parseColor("#A0A0A0"))
                textSize = 13f
                textDirection = View.TEXT_DIRECTION_LOCALE
                textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            }
        )
        wrapper.addView(
            TextView(this).apply {
                text = value
                setTextColor(valueColor)
                textSize = 15f
                setLineSpacing(0f, 1.2f)
                textDirection = View.TEXT_DIRECTION_LOCALE
                textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            }
        )

        container.addView(
            wrapper,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                this.bottomMargin = bottomMargin
            }
        )
    }

    private fun handleSettingsAction(data: Intent?) {
        val action = data?.getStringExtra(SettingsActivity.EXTRA_ACTION) ?: return
        when (action) {
            SettingsActivity.ACTION_SELECT_MODEL -> showModelSelectionDialog()
            SettingsActivity.ACTION_IMPORT_MODEL -> openFilePicker()
            SettingsActivity.ACTION_MANAGE_MODEL_E2B -> showMainModelManagementDialog(modelById(MODEL_ID_E2B))
            SettingsActivity.ACTION_MANAGE_MODEL_E4B -> showMainModelManagementDialog(modelById(MODEL_ID_E4B))
            SettingsActivity.ACTION_IMPORT_VISION_MODEL -> openVisionModelPicker()
            SettingsActivity.ACTION_DELETE_VISION_MODEL -> confirmDeleteVisionModel()
            SettingsActivity.ACTION_READINESS_CHECK -> showReadinessCheckDialog()
            SettingsActivity.ACTION_MODEL_IMPORT_HELP -> showModelImportHelpDialog()
            SettingsActivity.ACTION_LITERT_DIAGNOSTICS -> showLiteRtDiagnosticsDialog()
            SettingsActivity.ACTION_IMPORT_EMBEDDING_MODEL -> openEmbeddingModelPicker()
            SettingsActivity.ACTION_DELETE_EMBEDDING_MODEL -> confirmDeleteEmbeddingModel()
            SettingsActivity.ACTION_DELETE_EMBEDDING_INDEXES -> confirmDeleteEmbeddingIndexes()
            SettingsActivity.ACTION_BUILD_DOCUMENT_SEMANTIC_INDEX -> confirmBuildSelectedDocumentSemanticIndex()
            SettingsActivity.ACTION_RAG_DIAGNOSTICS -> showRagDiagnosticsDialog()
            SettingsActivity.ACTION_BACKGROUND_TASKS -> showBackgroundTasksDialog()
            SettingsActivity.ACTION_LOCAL_MODEL_MANAGER -> showLocalModelManagerDialog()
            SettingsActivity.ACTION_OPEN_CHAT_HISTORY -> showChatHistoryDialog()
            SettingsActivity.ACTION_OPEN_DOCUMENT_LIBRARY -> showDocumentLibraryDialog()
            SettingsActivity.ACTION_SET_DOCUMENT_ANSWER_LENGTH -> {
                val value = data.getStringExtra(SettingsActivity.EXTRA_VALUE) ?: return
                updateDocumentAnswerLength(value)
                saveActiveSessionDebounced(immediate = true)
            }
            SettingsActivity.ACTION_SET_RAG_SEARCH_MODE -> {
                val value = data.getStringExtra(SettingsActivity.EXTRA_VALUE) ?: return
                updateRagMode(value)
            }
            SettingsActivity.ACTION_SET_EMBEDDING_BACKEND -> {
                val value = data.getStringExtra(SettingsActivity.EXTRA_VALUE) ?: return
                updateEmbeddingBackend(value)
            }
            SettingsActivity.ACTION_SET_PDF_PAGE_LIMIT -> {
                val value = data.getStringExtra(SettingsActivity.EXTRA_VALUE)?.toIntOrNull() ?: return
                updatePdfPageLimit(value)
            }
            SettingsActivity.ACTION_TOGGLE_MEMORY -> {
                val enabled = !memoryStore.isMemoryEnabled()
                memoryStore.setMemoryEnabled(enabled)
                setStatusSuccess(if (enabled) "تم تفعيل ذاكرة نبض." else "تم تعطيل ذاكرة نبض.")
            }
            SettingsActivity.ACTION_SHOW_MEMORY -> showMemoryDialog()
            SettingsActivity.ACTION_CLEAR_MEMORY -> confirmClearMemory()
            SettingsActivity.ACTION_WHATS_NEW -> showWhatsNewDialog()
            SettingsActivity.ACTION_SEND_FEEDBACK -> showFeedbackDialog()
            SettingsActivity.ACTION_HELP -> showHelpDialog()
            SettingsActivity.ACTION_COPY_BETA_REPORT -> copyBetaReportToClipboard()
            SettingsActivity.ACTION_CLEAR_SELECTED_DOCUMENT -> {
                documentStore.clearSelectedDocumentId()
                saveActiveSessionDebounced(immediate = true)
                setStatusSuccess(DocumentMessageFormatter.documentClearedMessage())
            }
            SettingsActivity.ACTION_CLEAR_CHAT -> confirmClearChat()
            SettingsActivity.ACTION_COPY_CHAT -> copyFullConversation()
            SettingsActivity.ACTION_COPY_LAST_RESPONSE -> copyLastAssistantResponse()
            SettingsActivity.ACTION_ABOUT -> showAboutDialog()
        }
    }

    private fun showPhoneToolConfirmation(input: String, intents: List<PhoneToolIntent>) {
        inputView.setText("")
        addChatMessage(ChatMessage(role = Role.USER, text = input))
        val suggestion = "أستطيع تنفيذ أدوات الهاتف:\n" + intents.joinToString("\n") {
            "- ${getPhoneToolLabel(it)}"
        }
        addChatMessage(ChatMessage(role = Role.ASSISTANT, text = suggestion))
        MaterialAlertDialogBuilder(this)
            .setTitle("تنفيذ؟")
            .setPositiveButton("تنفيذ") { _, _ -> executePhoneTools(intents) }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun getPhoneToolLabel(intent: PhoneToolIntent) = when (intent) {
        PhoneToolIntent.BATTERY -> "البطارية"
        PhoneToolIntent.DEVICE_INFO -> "الجهاز"
        PhoneToolIntent.STORAGE -> "التخزين"
        PhoneToolIntent.INSTALLED_APPS -> "التطبيقات"
    }

    private fun executePhoneTools(intents: List<PhoneToolIntent>) {
        val result = intents.joinToString("\n\n") { intent ->
            val toolResult = when (intent) {
                PhoneToolIntent.BATTERY -> phoneToolManager.getBatteryStatus()
                PhoneToolIntent.DEVICE_INFO -> phoneToolManager.getDeviceInfo()
                PhoneToolIntent.STORAGE -> phoneToolManager.getStorageInfo()
                PhoneToolIntent.INSTALLED_APPS -> phoneToolManager.getInstalledAppsSummary()
            }
            "[${toolResult.title}]\n${toolResult.content}"
        }
        addChatMessage(ChatMessage(role = Role.ASSISTANT, text = result))
        saveActiveSessionDebounced(immediate = true)
    }

    private fun showDocumentToolConfirmation(input: String, requests: List<DocumentToolRequest>) {
        inputView.setText("")
        addChatMessage(ChatMessage(role = Role.USER, text = input))
        val suggestion = "أستطيع استخدام أدوات المستندات:\n" + requests.joinToString("\n") {
            "- ${getDocToolLabel(it)}"
        }
        addChatMessage(ChatMessage(role = Role.ASSISTANT, text = suggestion))
        MaterialAlertDialogBuilder(this)
            .setTitle("تنفيذ؟")
            .setPositiveButton("تنفيذ") { _, _ -> executeDocumentTools(requests) }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun getDocToolLabel(request: DocumentToolRequest) = when (request.intent) {
        DocumentToolIntent.SEARCH_DOCUMENTS -> "البحث عن: ${request.query}"
        DocumentToolIntent.SHOW_DOCUMENT_LIBRARY -> "المكتبة"
        DocumentToolIntent.CLEAR_SELECTED_DOCUMENT -> "إلغاء الاختيار"
        DocumentToolIntent.CURRENT_DOCUMENT_SUMMARY -> "التلخيص"
    }

    private fun executeDocumentTools(requests: List<DocumentToolRequest>) {
        scope.launch {
            requests.forEach { request ->
                when (request.intent) {
                    DocumentToolIntent.SEARCH_DOCUMENTS -> {
                        val result = performLocalDocumentSearch(request.query.orEmpty())
                        addChatMessage(ChatMessage(role = Role.ASSISTANT, text = result))
                    }

                    DocumentToolIntent.SHOW_DOCUMENT_LIBRARY -> showDocumentLibraryDialog()
                    DocumentToolIntent.CLEAR_SELECTED_DOCUMENT -> {
                        documentStore.clearSelectedDocumentId()
                        addChatMessage(ChatMessage(role = Role.ASSISTANT, text = DocumentMessageFormatter.documentClearedMessage()))
                        setStatusInfo(currentStatus())
                    }

                    DocumentToolIntent.CURRENT_DOCUMENT_SUMMARY -> {
                        val summary = performCurrentDocumentSummary()
                        addChatMessage(ChatMessage(role = Role.ASSISTANT, text = summary))
                    }
                }
            }
            saveActiveSessionDebounced(immediate = true)
        }
    }

    private suspend fun performLocalDocumentSearch(query: String): String {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isEmpty()) return "يرجى تحديد عبارة البحث."

        val results = documentStore.getDocuments()
            .asSequence()
            .mapNotNull { document ->
                val titleMatch = document.title.contains(normalizedQuery, ignoreCase = true)
                val textIndex = document.extractedText.indexOf(normalizedQuery, ignoreCase = true)
                if (!titleMatch && textIndex == -1) return@mapNotNull null

                val excerpt = if (textIndex >= 0) {
                    val start = (textIndex - 80).coerceAtLeast(0)
                    val end = (textIndex + normalizedQuery.length + 170)
                        .coerceAtMost(document.extractedText.length)
                    document.extractedText.substring(start, end)
                        .replace(Regex("\\s+"), " ")
                        .trim()
                        .safeTruncate(DOCUMENT_SEARCH_EXCERPT_CHARS)
                } else {
                    "مطابقة في عنوان المستند"
                }

                "${document.title} (${document.type})\n$excerpt"
            }
            .take(MAX_DOCUMENT_SEARCH_RESULTS)
            .toList()

        return if (results.isEmpty()) {
            "لا توجد نتائج لـ $normalizedQuery"
        } else {
            "نتائج البحث:\n" + results.joinToString("\n\n")
        }
    }

    private suspend fun performCurrentDocumentSummary(): String {
        val document = getSelectedDocument() ?: return DocumentMessageFormatter.noSelectedDocumentMessage()
        val excerpt = document.extractedText
            .replace(Regex("\\s+"), " ")
            .trim()
            .safeTruncate(DOCUMENT_SEARCH_EXCERPT_CHARS)
        return "ملخص المستند: ${document.title}\n$excerpt"
    }

    private fun appendToolResultToChat(result: PhoneToolResult) {
        addChatMessage(ChatMessage(role = Role.ASSISTANT, text = "[${result.title}]\n${result.content}"))
        saveActiveSessionDebounced(immediate = true)
    }

    private fun clearChat() {
        chatMessages.clear()
        lastAssistantResponse = ""
        activeAssistantMessageIndex = -1
        chatAdapter.clearMessages()
        saveActiveSessionDebounced(immediate = true)
        setStatusInfo("تم المسح")
    }

    private fun confirmClearChat() {
        MaterialAlertDialogBuilder(this)
            .setTitle("مسح المحادثة؟")
            .setPositiveButton("مسح") { _, _ -> clearChat() }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun showAttachmentTypeDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(com.example.localqwen.attachments.AttachmentOptionsHelper.attachmentDialogTitle())
            .setItems(com.example.localqwen.attachments.AttachmentOptionsHelper.mainAttachmentOptions()) { _, which ->
                if (which == 0) openImagePicker() else openPdfPicker()
            }
            .show()
    }

    private fun openImagePicker() {
        startActivityForResult(
            Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "image/*"
            },
            pickImageRequestCode
        )
    }

    private fun openPdfPicker() {
        startActivityForResult(
            Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/pdf"
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            },
            pickPdfRequestCode
        )
    }

    private fun openFilePicker() {
        startActivityForResult(
            Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
            },
            pickModelRequestCode
        )
    }

    private fun openVisionModelPicker() {
        startActivityForResult(
            Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
            },
            pickVisionModelRequestCode
        )
    }

    private fun confirmDeleteVisionModel() {
        MaterialAlertDialogBuilder(this)
            .setTitle("حذف نموذج الرؤية؟")
            .setMessage("هل أنت متأكد من حذف نموذج الرؤية المحلي؟ سيتم الرجوع إلى البحث النصي OCR.")
            .setPositiveButton("حذف") { _, _ ->
                modelManager.deleteModel(ModelManager.VISION_MODEL)
                setStatusSuccess("تم حذف نموذج الرؤية")
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun openEmbeddingModelPicker() {
        startActivityForResult(
            Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
            },
            pickEmbeddingModelRequestCode
        )
    }

    private fun showDocumentLibraryDialog() {
        scope.launch {
            val documents = withContext(Dispatchers.IO) { documentStore.getDocuments() }
            if (documents.isEmpty()) {
                setStatusError(DocumentMessageFormatter.emptyLibraryMessage())
                return@launch
            }

            val labels = documents.map { document ->
                "${document.title} • ${document.type} • ${formatDate(document.createdAt)} • ${formatDocumentSize(document.extractedText.length)}"
            }.toTypedArray()

            MaterialAlertDialogBuilder(this@MainActivity)
                .setTitle("المكتبة")
                .setItems(labels) { _, which ->
                    documentStore.setSelectedDocumentId(documents[which].id)
                    saveActiveSessionDebounced(immediate = true)
                    setStatusSuccess(DocumentMessageFormatter.documentSelectedMessage(documents[which].title))
                }
                .show()
        }
    }

    private fun showChatHistoryDialog() {
        scope.launch {
            val sessions = withContext(Dispatchers.IO) { chatSessionStore.getAllSessions().toMutableList() }
            if (sessions.isEmpty()) {
                Toast.makeText(this@MainActivity, "لا يوجد سجل", Toast.LENGTH_SHORT).show()
                return@launch
            }

            val context = this@MainActivity
            val layout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(40, 40, 40, 40)
                setBackgroundColor(Color.parseColor("#202020"))
            }

            val searchInput = EditText(context).apply {
                hint = "ابحث في المحادثات..."
                setHintTextColor(Color.parseColor("#A0A0A0"))
                setTextColor(Color.WHITE)
                background = ContextCompat.getDrawable(context, R.drawable.bg_input)
                setPadding(30, 30, 30, 30)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 0, 0, 40) }
            }
            layout.addView(searchInput)

            val scrollView = ScrollView(context).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
            }
            val resultsContainer = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
            }
            scrollView.addView(resultsContainer)
            layout.addView(scrollView)

            val noResultsText = TextView(context).apply {
                text = "لا توجد نتائج مطابقة"
                setTextColor(Color.parseColor("#A0A0A0"))
                gravity = android.view.Gravity.CENTER
                visibility = View.GONE
                setPadding(0, 40, 0, 40)
            }
            layout.addView(noResultsText)

            val dialog = MaterialAlertDialogBuilder(context)
                .setView(layout)
                .show()
                
            dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.parseColor("#202020")))
            dialog.window?.setLayout(LinearLayout.LayoutParams.MATCH_PARENT, (context.resources.displayMetrics.heightPixels * 0.8).toInt())

            fun renderSessions(query: String) {
                resultsContainer.removeAllViews()
                val q = query.trim().lowercase(Locale.getDefault()).replace(Regex("\\s+"), " ")
                var matchCount = 0

                val currentSessionId = chatSessionStore.getActiveSessionId()

                for (session in sessions) {
                    val messagesText = java.lang.StringBuilder()
                    try {
                        val arr = JSONArray(session.messagesJson)
                        for (i in 0 until arr.length()) {
                            messagesText.append(arr.getJSONObject(i).optString("text", "")).append(" ")
                        }
                    } catch (e: Exception) {}
                    val allText = messagesText.toString()
                    val searchableText = (session.title + " " + allText + " " + session.lastAssistantResponse).lowercase(Locale.getDefault()).replace(Regex("\\s+"), " ")

                    if (q.isEmpty() || searchableText.contains(q)) {
                        matchCount++
                        val row = LinearLayout(context).apply {
                            orientation = LinearLayout.VERTICAL
                            setPadding(30, 30, 30, 30)
                            setBackgroundColor(Color.parseColor("#242424"))
                            layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            ).apply { setMargins(0, 0, 0, 20) }
                            
                            var isPressed = false
                            setOnTouchListener { v, event ->
                                when (event.action) {
                                    android.view.MotionEvent.ACTION_DOWN -> { v.setBackgroundColor(Color.parseColor("#333333")); isPressed = true }
                                    android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> { v.setBackgroundColor(Color.parseColor("#242424")); isPressed = false }
                                }
                                false
                            }

                            setOnClickListener {
                                dialog.dismiss()
                                switchSession(session.id)
                            }
                            setOnLongClickListener {
                                val options = arrayOf("فتح", "إعادة تسمية", "حذف", "نسخ المحادثة")
                                MaterialAlertDialogBuilder(context)
                                    .setItems(options) { _, which ->
                                        when (which) {
                                            0 -> { dialog.dismiss(); switchSession(session.id) }
                                            1 -> renameSessionPrompt(session) { renderSessions(searchInput.text.toString()) }
                                            2 -> deleteSessionPrompt(session) {
                                                sessions.removeAll { it.id == session.id }
                                                if(sessions.isEmpty()) dialog.dismiss() else renderSessions(searchInput.text.toString())
                                            }
                                            3 -> copySessionText(session)
                                        }
                                    }.show()
                                true
                            }
                        }

                        val titleLayout = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
                        val titleView = TextView(context).apply {
                            text = session.title
                            setTextColor(Color.WHITE)
                            textSize = 16f
                            setTypeface(null, android.graphics.Typeface.BOLD)
                            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                        }
                        titleLayout.addView(titleView)

                        if (session.id == currentSessionId) {
                            val currentLabel = TextView(context).apply {
                                text = "الحالية"
                                setTextColor(Color.parseColor("#FF7000"))
                                textSize = 12f
                                setPadding(10, 0, 10, 0)
                            }
                            titleLayout.addView(currentLabel)
                        }
                        row.addView(titleLayout)
                        
                        val dateView = TextView(context).apply {
                            text = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(session.updatedAt))
                            setTextColor(Color.parseColor("#A0A0A0"))
                            textSize = 12f
                            setPadding(0, 5, 0, 10)
                        }
                        row.addView(dateView)

                        var preview = ""
                        if (q.isNotEmpty() && allText.lowercase(Locale.getDefault()).contains(q)) {
                            val idx = allText.lowercase(Locale.getDefault()).indexOf(q)
                            val start = Math.max(0, idx - 40)
                            val end = Math.min(allText.length, idx + q.length + 40)
                            preview = "..." + allText.substring(start, end).replace("\n", " ") + "..."
                        } else if (allText.isNotBlank()) {
                            preview = allText.take(80).replace("\n", " ") + if (allText.length > 80) "..." else ""
                        }

                        if (preview.isNotEmpty()) {
                            val previewView = TextView(context).apply {
                                text = preview
                                setTextColor(Color.parseColor("#A0A0A0"))
                                textSize = 14f
                                maxLines = 2
                                ellipsize = android.text.TextUtils.TruncateAt.END
                            }
                            row.addView(previewView)
                        }

                        resultsContainer.addView(row)
                    }
                }
                noResultsText.visibility = if (matchCount == 0) View.VISIBLE else View.GONE
            }

            searchInput.addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: android.text.Editable?) {
                    renderSessions(s?.toString() ?: "")
                }
            })

            renderSessions("")
        }
    }

    private fun renameSessionPrompt(session: ChatSession, onRenamed: () -> Unit) {
        val input = EditText(this).apply {
            setText(session.title)
            setTextColor(Color.WHITE)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("إعادة تسمية")
            .setView(input)
            .setPositiveButton("حفظ") { _, _ ->
                val newTitle = input.text.toString().trim()
                if (newTitle.isNotEmpty()) {
                    scope.launch { chatSessionStore.renameSession(session.id, newTitle) }
                    session.title = newTitle
                    onRenamed()
                }
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun deleteSessionPrompt(session: ChatSession, onDeleted: () -> Unit) {
        MaterialAlertDialogBuilder(this)
            .setTitle("حذف المحادثة؟")
            .setMessage("هل أنت متأكد من حذف هذه المحادثة؟")
            .setPositiveButton("حذف") { _, _ ->
                scope.launch {
                    chatSessionStore.deleteSession(session.id)
                    if (chatSessionStore.getActiveSessionId() == session.id) {
                        chatSessionStore.setActiveSessionId(null)
                        val sessions = chatSessionStore.getAllSessions()
                        if (sessions.isNotEmpty()) {
                            switchSession(sessions.first().id)
                        } else {
                            chatMessages.clear()
                            chatAdapter.submitMessages(emptyList())
                        }
                    }
                    onDeleted()
                }
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun copySessionText(session: ChatSession) {
        val sb = java.lang.StringBuilder()
        try {
            val arr = JSONArray(session.messagesJson)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val role = obj.optString("role", "")
                val text = obj.optString("text", "")
                val name = if (role == "user") "أنت" else if (role == "assistant") "الذكاء الاصطناعي" else "النظام"
                sb.append("$name: $text\n\n")
            }
        } catch (e: Exception) {}
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Chat Session", sb.toString().trim())
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "تم نسخ المحادثة", Toast.LENGTH_SHORT).show()
    }

    private fun selectModel(position: Int) {
        val model = supportedModels[position]
        if (selectedModel.id == model.id) return
        if (!modelManager.isModelImported(model.id)) {
            setStatusError("تعذر اختيار النموذج. تأكد من استيراده أولًا.")
            return
        }

        selectedModel = model
        preferences.edit().putString(KEY_SELECTED_MODEL_ID, model.id).apply()

        if (loadedModelId != null && loadedModelId != model.id) {
            unloadModel()
        } else {
            setStatusInfo(currentStatus())
        }
        updateButtons()
    }

    private fun selectMainModelFromSettings(model: SupportedModel) {
        val previousModelId = selectedModel.id
        selectedModel = model
        preferences.edit().putString(KEY_SELECTED_MODEL_ID, model.id).apply()

        if (loadedModelId != null && loadedModelId != model.id) {
            unloadModel("تم اختيار النموذج. شغّل نبض لاستخدامه.")
        } else if (previousModelId != model.id) {
            setStatusSuccess("تم اختيار النموذج. شغّل نبض لاستخدامه.")
        } else {
            setStatusInfo(currentStatus())
        }
        updateButtons()
    }

    private fun beginModelImportFor(model: SupportedModel) {
        selectedModel = model
        preferences.edit().putString(KEY_SELECTED_MODEL_ID, model.id).apply()
        setStatusInfo("اختر ملف النموذج لـ ${model.displayName}")
        openFilePicker()
    }

    private fun confirmDeleteMainModel(model: SupportedModel) {
        MaterialAlertDialogBuilder(this)
            .setTitle("حذف النموذج؟")
            .setMessage("سيتم حذف ملف النموذج من تخزين التطبيق. يمكنك استيراده مرة أخرى لاحقًا.")
            .setPositiveButton("حذف") { _, _ ->
                deleteMainModel(model)
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun deleteMainModel(model: SupportedModel) {
        scope.launch {
            isLoadingModel = true
            updateButtons()
            setStatusInfo("جاري حذف النموذج...")

            try {
                if (loadedModelId == model.id) {
                    withContext(Dispatchers.IO) { closeModelResources() }
                }
                val deleted = withContext(Dispatchers.IO) {
                    modelManager.deleteModel(model)
                }
                if (deleted) {
                    setStatusSuccess("تم حذف النموذج")
                } else {
                    setStatusError("تعذر حذف النموذج")
                }
            } catch (_: Exception) {
                setStatusError("تعذر حذف النموذج")
            } finally {
                isLoadingModel = false
                updateButtons()
            }
        }
    }

    private suspend fun importModelFile(uri: Uri, isVision: Boolean = false) {
        val targetModel = if (isVision) ModelManager.VISION_MODEL else selectedModel
        val targetFile = modelManager.modelFile(targetModel)
        val tempFile = modelManager.tempModelFile(targetModel)
        
        try {
            modelManager.cleanTempFiles()
            
            contentResolver.openInputStream(uri)?.buffered()?.use { input ->
                tempFile.outputStream().buffered().use { output ->
                    input.copyTo(output, DEFAULT_COPY_BUFFER_SIZE)
                }
            } ?: throw IOException("تعذر فتح ملف النموذج")
            
            // تحقق من سلامة الملف المؤقت بعد النسخ
            if (tempFile.length() < ModelManager.MIN_MODEL_SIZE_BYTES) {
                tempFile.delete()
                throw IOException("ملف النموذج صغير جداً أو تالف")
            }
            
            // النقل الذري (Atomic Move) عبر إعادة التسمية
            if (targetFile.exists()) targetFile.delete()
            if (!tempFile.renameTo(targetFile)) {
                throw IOException("تعذر تثبيت ملف النموذج النهائي")
            }
        } catch (e: Exception) {
            if (tempFile.exists()) tempFile.delete()
            throw e
        }
    }

    private fun getFileName(uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
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

    private fun importVisionModelFromUri(uri: Uri) {
        val fileName = getFileName(uri)
        if (!modelManager.isModelFileExtensionValid(fileName)) {
            setStatusError("خطأ: يجب أن يكون الملف بصيغة .litertlm")
            return
        }

        scope.launch {
            isLoadingModel = true
            updateButtons()
            setStatusInfo("جاري استيراد نموذج الرؤية...")
            try {
                withContext(Dispatchers.IO) { importModelFile(uri, isVision = true) }
                setStatusSuccess("تم استيراد نموذج الرؤية بنجاح")
                delay(1500)
                setStatusInfo(currentStatus())
            } catch (e: Exception) {
                setStatusError("فشل الاستيراد: ${e.localizedMessage ?: "خطأ غير معروف"}")
            } finally {
                isLoadingModel = false
                updateButtons()
            }
        }
    }

    private fun importModelFromUri(uri: Uri) {
        val fileName = getFileName(uri)
        if (!modelManager.isModelFileExtensionValid(fileName)) {
            setStatusError("خطأ: يجب أن يكون الملف بصيغة .litertlm")
            return
        }

        scope.launch {
            isLoadingModel = true
            updateButtons()
            setStatusInfo("جاري استيراد النموذج...")
            try {
                withContext(Dispatchers.IO) { importModelFile(uri) }
                setStatusSuccess("تم استيراد النموذج بنجاح")
                delay(1500)
                setStatusInfo(currentStatus())
            } catch (e: Exception) {
                setStatusError("فشل الاستيراد: ${e.localizedMessage ?: "خطأ غير معروف"}")
            } finally {
                isLoadingModel = false
                updateButtons()
            }
        }
    }

    private fun importEmbeddingModelFromUri(uri: Uri) {
        scope.launch {
            isProcessingFile = true
            updateButtons()
            setStatusInfo("جاري استيراد نموذج التضمين...")
            try {
                withContext(Dispatchers.IO) { embeddingModelManager.importEmbeddingModel(uri) }
                embeddingEngine.close()
                setStatusSuccess("تم استيراد نموذج التضمين")
            } catch (_: Exception) {
                setStatusError("فشل استيراد نموذج التضمين")
            } finally {
                isProcessingFile = false
                updateButtons()
            }
        }
    }

    private fun confirmDeleteEmbeddingModel() {
        if (!embeddingModelManager.isEmbeddingModelReady()) {
            setStatusError("نموذج التضمين غير مستورد.")
            return
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("حذف نموذج التضمين؟")
            .setMessage(
                "سيتم حذف نموذج التضمين المحلي. سيبقى البحث النصي يعمل، لكن البحث الدلالي يحتاج استيراد نموذج جديد."
            )
            .setPositiveButton("حذف") { _, _ ->
                deleteEmbeddingModel()
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun deleteEmbeddingModel() {
        scope.launch {
            isProcessingFile = true
            updateButtons()
            setStatusInfo("جاري حذف نموذج التضمين...")
            try {
                val deleted = withContext(Dispatchers.IO) {
                    embeddingModelManager.deleteEmbeddingModel()
                }
                embeddingEngine.close()
                if (deleted) {
                    setStatusSuccess("تم حذف نموذج التضمين")
                } else {
                    setStatusError("تعذر حذف نموذج التضمين")
                }
            } catch (_: Exception) {
                embeddingEngine.close()
                setStatusError("تعذر حذف نموذج التضمين")
            } finally {
                isProcessingFile = false
                updateButtons()
            }
        }
    }

    private fun confirmDeleteEmbeddingIndexes() {
        if (embeddingStore.countIndexes() <= 0) {
            setStatusError("لا توجد فهارس دلالية للحذف.")
            return
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("حذف الفهارس الدلالية؟")
            .setMessage("سيتم حذف فهارس البحث الدلالي للمستندات. يمكنك إنشاؤها من جديد لاحقًا.")
            .setPositiveButton("حذف") { _, _ ->
                deleteEmbeddingIndexes()
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun deleteEmbeddingIndexes() {
        scope.launch {
            isProcessingFile = true
            updateButtons()
            setStatusInfo("جاري حذف الفهارس الدلالية...")
            try {
                val deleted = withContext(Dispatchers.IO) {
                    embeddingStore.deleteAllIndexes()
                }
                if (deleted) {
                    setStatusSuccess("تم حذف الفهارس الدلالية")
                } else {
                    setStatusError("تعذر حذف الفهارس الدلالية")
                }
            } catch (_: Exception) {
                setStatusError("تعذر حذف الفهارس الدلالية")
            } finally {
                isProcessingFile = false
                updateButtons()
            }
        }
    }

    private fun confirmBuildSelectedDocumentSemanticIndex() {
        scope.launch {
            val document = getSelectedDocument()
            if (document == null) {
                setStatusError("اختر مستندًا أولًا.")
                return@launch
            }
            if (!embeddingModelManager.isEmbeddingModelReady()) {
                setStatusError("استورد نموذج التضمين أولًا.")
                return@launch
            }

            withContext(Dispatchers.Main) {
                MaterialAlertDialogBuilder(this@MainActivity)
                    .setTitle("إنشاء فهرس دلالي")
                    .setMessage("سيتم إنشاء فهرس دلالي محلي لهذا المستند. قد يستغرق بعض الوقت.")
                    .setPositiveButton("بدء") { _, _ ->
                        buildSemanticIndex(document)
                    }
                    .setNegativeButton("إلغاء", null)
                    .show()
            }
        }
    }

    private fun buildSemanticIndex(document: LocalDocument) {
        if (isGenerating || isLoadingModel || isProcessingFile || isPreparingDocumentContext) return

        scope.launch {
            isProcessingFile = true
            semanticIndexingStatus = SEMANTIC_INDEX_STATUS_RUNNING
            semanticIndexingDocumentTitle = document.title
            semanticIndexingStartedAt = System.currentTimeMillis()
            semanticIndexingLastMessage = "جاري فهرسة المستند دلاليًا..."
            renderBackgroundTasksDialog()
            updateButtons()
            setStatusInfo("جاري فهرسة المستند دلاليًا...")

            try {
                withContext(Dispatchers.IO) {
                    val chunks = TextChunker.chunkText(
                        text = document.extractedText.take(DOCUMENT_TEXT_LIMIT),
                        maxChars = 800,
                        overlapChars = 120
                    ).take(MAX_SEMANTIC_INDEX_CHUNKS)

                    if (chunks.isEmpty()) {
                        throw IllegalStateException("لا يوجد نص كافٍ لفهرسة هذا المستند.")
                    }

                    val indexedChunks = chunks.mapIndexed { index, chunk ->
                        EmbeddingStore.IndexedChunk(
                            chunkIndex = index,
                            text = chunk,
                            vector = embeddingEngine.embed(chunk)
                        )
                    }.filter { it.vector.isNotEmpty() }

                    if (indexedChunks.isEmpty()) {
                        throw IllegalStateException("تعذر إنشاء متجهات صالحة لهذا المستند.")
                    }

                    embeddingStore.saveIndex(document.id, indexedChunks)
                }
                semanticIndexingStatus = SEMANTIC_INDEX_STATUS_SUCCESS
                semanticIndexingLastMessage = "تم إنشاء الفهرس الدلالي."
                renderBackgroundTasksDialog()
                setStatusSuccess("تم إنشاء الفهرس الدلالي")
            } catch (error: EmbeddingEngine.UnsupportedEmbeddingModelException) {
                semanticIndexingStatus = SEMANTIC_INDEX_STATUS_FAILED
                semanticIndexingLastMessage = error.message ?: "تعذر إنشاء الفهرس الدلالي."
                renderBackgroundTasksDialog()
                setStatusError(error.message ?: "تعذر إنشاء الفهرس الدلالي.")
            } catch (error: IllegalStateException) {
                semanticIndexingStatus = SEMANTIC_INDEX_STATUS_FAILED
                semanticIndexingLastMessage = error.message ?: "تعذر إنشاء الفهرس الدلالي."
                renderBackgroundTasksDialog()
                setStatusError(error.message ?: "تعذر إنشاء الفهرس الدلالي.")
            } catch (_: OutOfMemoryError) {
                semanticIndexingStatus = SEMANTIC_INDEX_STATUS_FAILED
                semanticIndexingLastMessage = "تعذر إنشاء الفهرس الدلالي بسبب حجم المستند."
                renderBackgroundTasksDialog()
                setStatusError("تعذر إنشاء الفهرس الدلالي بسبب حجم المستند.")
            } catch (_: Exception) {
                semanticIndexingStatus = SEMANTIC_INDEX_STATUS_FAILED
                semanticIndexingLastMessage = "تعذر إنشاء الفهرس الدلالي الآن."
                renderBackgroundTasksDialog()
                setStatusError("تعذر إنشاء الفهرس الدلالي الآن.")
            } finally {
                isProcessingFile = false
                updateButtons()
            }
        }
    }

    private fun processImageUri(uri: Uri) {
        if (textInferenceEngine?.isReady() != true) {
            setStatusError("شغل نبض أولاً")
            return
        }

        val options = com.example.localqwen.attachments.AttachmentOptionsHelper.imageActionOptions()
        MaterialAlertDialogBuilder(this)
            .setTitle(com.example.localqwen.attachments.AttachmentOptionsHelper.imageActionDialogTitle())
            .setItems(options) { _, which ->
                when (which) {
                    0 -> executeImageOcrAnalysis(uri)
                    1 -> executeImageAskFlow(uri)
                }
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun executeImageOcrAnalysis(uri: Uri) {
        isProcessingFile = true
        updateButtons()
        setStatusInfo("استخراج النص...")

        scope.launch {
            try {
                val extractedText = withContext(Dispatchers.IO) {
                    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                    try {
                        val result = recognizer.process(
                            InputImage.fromFilePath(this@MainActivity, uri)
                        ).awaitTask()
                        result.text.trim()
                    } finally {
                        recognizer.close()
                    }
                }

                if (extractedText.isNotEmpty()) {
                    val baseTitle = getDisplayName(uri) ?: "صورة"
                    showExtractionModeDialog(baseTitle, "image", extractedText)
                    
                    addChatMessage(
                        ChatMessage(role = Role.USER, text = "تحليل صورة: $baseTitle")
                    )
                    val assistant = ChatMessage(role = Role.ASSISTANT, text = "")
                    addChatMessage(assistant)
                    startAssistantGeneration(
                        NabdSystemPrompt.imageAnalysisPrompt(
                            extractedText.safeTruncate(PROMPT_TEXT_LIMIT)
                        ),
                        assistant,
                        "جاري التحليل..."
                    )
                } else {
                    setStatusError("لم يتم العثور على نص واضح.")
                }
            } catch (_: OutOfMemoryError) {
                setStatusError(NabdErrorMessages.fileTooLarge())
            } catch (_: Exception) {
                setStatusError(NabdErrorMessages.fileTooLarge())
            } finally {
                isProcessingFile = false
                updateButtons()
            }
        }
    }

    private fun executeImageAskFlow(uri: Uri) {
        if (modelManager.isModelImported(ModelManager.VISION_MODEL.id)) {
            executeImageAskVisionFlow(uri)
        } else {
            executeImageAskOcrFlow(uri)
        }
    }

    private fun executeImageAskVisionFlow(uri: Uri) {
        val input = EditText(this@MainActivity).apply {
            hint = "اكتب سؤالك عن الصورة..."
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#A0A0A0"))
        }
        MaterialAlertDialogBuilder(this@MainActivity)
            .setTitle("اسأل عن الصورة")
            .setView(input)
            .setPositiveButton("إرسال") { _, _ ->
                val question = input.text.toString().trim()
                if (question.isNotEmpty()) {
                    isProcessingFile = true
                    updateButtons()
                    setStatusInfo("جاري تحليل الصورة بنموذج الرؤية...")
                    addChatMessage(ChatMessage(role = Role.USER, text = "سؤال عن صورة: $question"))
                    val assistant = ChatMessage(role = Role.ASSISTANT, text = "")
                    addChatMessage(assistant)
                    chatAdapter.markLastAssistantStreaming()
                    scope.launch {
                        try {
                            val tempFile = java.io.File(cacheDir, "vision_temp_image.jpg")
                            withContext(Dispatchers.IO) {
                                contentResolver.openInputStream(uri)?.use { inputStream ->
                                    tempFile.outputStream().use { outputStream ->
                                        inputStream.copyTo(outputStream)
                                    }
                                }
                            }
                            
                            val visionFile = modelManager.getModelFile(ModelManager.VISION_MODEL.id) ?: throw Exception("Vision model not found")
                            val visionEngine = com.example.localqwen.engine.LiteRtLmInferenceEngine()
                            try {
                                visionEngine.load(visionFile.absolutePath, cacheDir.absolutePath)
                                val promptText = "${NabdSystemPrompt.baseIdentityPrompt()}\nأجب بالعربية بوضوح واختصار. أنت ترى هذه الصورة. إذا لم تتأكد من الفهم، قل ذلك بصراحة.\n\nسؤال: $question"
                                
                                val output = StringBuilder()
                                var lastRenderedRawLength = 0
                                withContext(Dispatchers.IO) {
                                    visionEngine.generateVision(tempFile.absolutePath, promptText).collect { chunk ->
                                        output.append(chunk)
                                        val shouldRefresh = output.length <= 256 || output.length - lastRenderedRawLength >= STREAM_UPDATE_MIN_CHARS
                                        if (shouldRefresh) {
                                            val snapshot = output.toString()
                                            lastRenderedRawLength = output.length
                                            withContext(Dispatchers.Main) {
                                                val cleanedSnapshot = cleanForDisplay(snapshot, preserveMarkdown = true)
                                                updateAssistantMessage(cleanedSnapshot, preserveMarkdown = true, renderMarkdown = false)
                                            }
                                        }
                                    }
                                }
                                val finalText = cleanForDisplay(output.toString(), preserveMarkdown = true).ifBlank { "(فارغ)" }
                                lastAssistantResponse = finalText
                                updateAssistantMessage(finalText, forceScroll = true, preserveMarkdown = true, renderMarkdown = true)
                                setStatusSuccess("تم التحليل بنموذج الرؤية.")
                            } catch (e: Exception) {
                                e.printStackTrace()
                                setStatusError("تم استخدام OCR بدل نموذج الرؤية.")
                                withContext(Dispatchers.Main) {
                                    chatMessages.removeLast()
                                    chatMessages.removeLast()
                                    chatAdapter.submitMessages(chatMessages.toList())
                                    executeImageAskOcrFlow(uri)
                                }
                            } finally {
                                withContext(Dispatchers.IO) {
                                    visionEngine.unload()
                                    tempFile.delete()
                                }
                            }
                        } catch (e: Exception) {
                            setStatusError("فشل معالجة الصورة.")
                        } finally {
                            isProcessingFile = false
                            updateButtons()
                        }
                    }
                }
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun executeImageAskOcrFlow(uri: Uri) {
        isProcessingFile = true
        updateButtons()
        setStatusInfo("استخراج النص...")

        scope.launch {
            try {
                val extractedText = withContext(Dispatchers.IO) {
                    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                    try {
                        val result = recognizer.process(
                            InputImage.fromFilePath(this@MainActivity, uri)
                        ).awaitTask()
                        result.text.trim()
                    } finally {
                        recognizer.close()
                    }
                }

                if (extractedText.isNotEmpty()) {
                    val baseTitle = getDisplayName(uri) ?: "صورة"
                    showExtractionModeDialog(baseTitle, "image", extractedText)
                    setStatusSuccess("تم استخراج النص.")
                    
                    val input = EditText(this@MainActivity).apply {
                        hint = "اكتب سؤالك عن النص الموجود في الصورة..."
                        setTextColor(Color.WHITE)
                        setHintTextColor(Color.parseColor("#A0A0A0"))
                    }
                    MaterialAlertDialogBuilder(this@MainActivity)
                        .setTitle("اسأل عن الصورة")
                        .setView(input)
                        .setPositiveButton("إرسال") { _, _ ->
                            val question = input.text.toString().trim()
                            if (question.isNotEmpty()) {
                                addChatMessage(ChatMessage(role = Role.USER, text = "سؤال عن صورة: $question"))
                                val assistant = ChatMessage(role = Role.ASSISTANT, text = "")
                                addChatMessage(assistant)
                                startAssistantGeneration(
                                    NabdSystemPrompt.askImagePrompt(
                                        question = question,
                                        extractedText = extractedText.safeTruncate(PROMPT_TEXT_LIMIT)
                                    ),
                                    assistant,
                                    "جاري الإجابة..."
                                )
                            }
                        }
                        .setNegativeButton("إلغاء", null)
                        .show()
                } else {
                setStatusError(NabdErrorMessages.visionModelMissing())
                    MaterialAlertDialogBuilder(this@MainActivity)
                        .setTitle("تنبيه")
                        .setMessage(NabdErrorMessages.visionModelMissing())
                        .setPositiveButton("حسناً", null)
                        .show()
                }
            } catch (_: OutOfMemoryError) {
                setStatusError(NabdErrorMessages.fileTooLarge())
            } catch (_: Exception) {
                setStatusError(NabdErrorMessages.fileTooLarge())
            } finally {
                isProcessingFile = false
                updateButtons()
            }
        }
    }

    private fun processPdfUri(uri: Uri) {
        val title = getDisplayName(uri) ?: PdfMessageFormatter.defaultPdfTitle()
        val pageLimit = PdfSettings.getPdfPageLimit(this)
        val request = OneTimeWorkRequestBuilder<PdfProcessingWorker>()
            .addTag(PDF_PROCESSING_TAG)
            .setInputData(
                workDataOf(
                    PdfProcessingWorker.KEY_PDF_URI to uri.toString(),
                    PdfProcessingWorker.KEY_PDF_TITLE to title,
                    PdfSettings.KEY_PDF_PAGE_LIMIT to pageLimit
                )
            )
            .build()

        addChatMessage(ChatMessage(role = Role.SYSTEM, text = PdfMessageFormatter.pdfQueuedMessage(title)))
        saveActiveSessionDebounced(immediate = true)
        setStatusInfo(PdfMessageFormatter.pdfRunningStatus())

        workManager.enqueue(request)
        observePdfProcessing(request.id)
    }

    private fun observePdfProcessing(workId: UUID) {
        workManager.getWorkInfoByIdLiveData(workId).observe(this) { workInfo ->
            if (workInfo == null) return@observe

            when (workInfo.state) {
                WorkInfo.State.RUNNING -> {
                    val page = workInfo.progress.getInt(PdfProcessingWorker.KEY_PROGRESS_PAGE, 0)
                    val total = workInfo.progress.getInt(PdfProcessingWorker.KEY_PROGRESS_TOTAL, 0)
                    if (page > 0 && total > 0) {
                        setStatusInfo(PdfMessageFormatter.pdfProgressStatus(page, total))
                    } else {
                        setStatusInfo(PdfMessageFormatter.pdfRunningStatus())
                    }
                }

                WorkInfo.State.SUCCEEDED -> {
                    if (!handledPdfWorkIds.add(workId)) return@observe
                    val title = workInfo.outputData.getString(PdfProcessingWorker.KEY_PDF_TITLE)
                        ?: PdfMessageFormatter.defaultPdfTitle()
                    val extractedChars = workInfo.outputData.getInt(PdfProcessingWorker.KEY_EXTRACTED_CHARS, 0)
                    
                    // Get text from DocumentStore for the newly created doc, OR we need to pass it back
                    // Actually, the worker already saved it. This is a bit redundant but for consistency:
                    scope.launch {
                        val docId = workInfo.outputData.getString(PdfProcessingWorker.KEY_DOCUMENT_ID)
                        val doc = docId?.let { documentStore.getDocument(it) }
                        if (doc != null) {
                            withContext(Dispatchers.Main) {
                                showExtractionModeDialog(title, "pdf", doc.extractedText)
                            }
                        }
                    }

                    addChatMessage(
                        ChatMessage(
                            role = Role.SYSTEM,
                            text = PdfMessageFormatter.pdfSuccessMessage(title, extractedChars)
                        )
                    )
                    saveActiveSessionDebounced(immediate = true)
                    setStatusSuccess("اكتمل تحليل ملف PDF")
                }

                WorkInfo.State.FAILED -> {
                    if (!handledPdfWorkIds.add(workId)) return@observe
                    val error = workInfo.outputData.getString(PdfProcessingWorker.KEY_ERROR_MESSAGE)
                        ?: "تعذر تحليل ملف PDF"
                    addChatMessage(ChatMessage(role = Role.SYSTEM, text = PdfMessageFormatter.pdfFailureMessage(error)))
                    saveActiveSessionDebounced(immediate = true)
                    setStatusError(error)
                }

                WorkInfo.State.CANCELLED -> {
                    if (!handledPdfWorkIds.add(workId)) return@observe
                    addChatMessage(ChatMessage(role = Role.SYSTEM, text = PdfMessageFormatter.pdfCancelledMessage()))
                    saveActiveSessionDebounced(immediate = true)
                    setStatusError(PdfMessageFormatter.pdfCancelledMessage())
                }

                else -> Unit
            }
        }
    }

    private fun tryPersistPdfReadPermission(uri: Uri) {
        runCatching {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
    }

    private fun formatDate(timestamp: Long): String {
        return SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(Date(timestamp))
    }

    private fun formatDateTime(timestamp: Long): String {
        return SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()).format(Date(timestamp))
    }

    private fun formatDocumentSize(chars: Int): String {
        return when {
            chars >= 1_000_000 -> String.format(Locale.US, "%.1fM", chars / 1_000_000f)
            chars >= 1_000 -> String.format(Locale.US, "%.1fK", chars / 1_000f)
            else -> ""
        }
    }

    private fun formatStorageSize(bytes: Long): String {
        return when {
            bytes >= 1024L * 1024L * 1024L -> String.format(Locale.US, "%.1f GB", bytes / (1024f * 1024f * 1024f))
            bytes >= 1024L * 1024L -> String.format(Locale.US, "%.1f MB", bytes / (1024f * 1024f))
            bytes >= 1024L -> String.format(Locale.US, "%.1f KB", bytes / 1024f)
            else -> " B"
        }
    }

    private suspend fun <T> com.google.android.gms.tasks.Task<T>.awaitTask(): T =
        suspendCancellableCoroutine { cont ->
            addOnSuccessListener { cont.resume(it) }
            addOnFailureListener { cont.resumeWithException(it) }
        }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        if (requestCode == settingsRequestCode) {
            handleSettingsAction(data)
            return
        }
        val uri = data?.data ?: return
        when (requestCode) {
            pickModelRequestCode -> importModelFromUri(uri)
            pickImageRequestCode -> processImageUri(uri)
            pickVisionModelRequestCode -> importVisionModelFromUri(uri)
            pickEmbeddingModelRequestCode -> importEmbeddingModelFromUri(uri)
            pickPdfRequestCode -> {
                tryPersistPdfReadPermission(uri)
                processPdfUri(uri)
            }
        }
    }

    companion object {
        private const val MODEL_ID_E2B = "gemma_e2b"
        private const val MODEL_ID_E4B = "gemma_e4b"
        private const val KEY_CHAT_HISTORY_TEXT = "chat_history_text"
        private const val KEY_CHAT_MESSAGES_JSON = "chat_messages_json"
        private const val KEY_SELECTED_MODEL_ID = "selected_model_id"
        private const val KEY_ONBOARDING_SEEN = "onboarding_seen"
        private const val KEY_RAG_SEARCH_MODE = "rag_search_mode"
        private const val KEY_EMBEDDING_BACKEND = "embedding_backend"
        private const val MAX_DOCUMENT_CONTEXT_CHARS = 5_000
        private const val MAX_RETRIEVED_CHUNKS = 6
        private const val DOCUMENT_CHUNK_SIZE = 1_200
        private const val DOCUMENT_TEXT_LIMIT = 200_000
        private const val PROMPT_TEXT_LIMIT = 6_000
        private const val STREAM_UPDATE_MIN_CHARS = 48
        private const val MAX_SEMANTIC_INDEX_CHUNKS = 300
        private const val MAX_DOCUMENT_SEARCH_RESULTS = 5
        private const val DOCUMENT_SEARCH_EXCERPT_CHARS = 250
        private const val DEFAULT_COPY_BUFFER_SIZE = 8_192
        private const val PDF_PROCESSING_TAG = "pdf_processing"
        private const val MAX_MEMORY_ITEM_CHARS = 300
        private const val DEFAULT_GENERATION_STATUS = "جاري التوليد..."
        private val SENSITIVE_MEMORY_KEYWORDS = listOf(
            "password",
            "كلمة المرور",
            "الرقم السري",
            "token",
            "api key",
            "بطاقة",
            "حساب بنكي"
        )
        private val KEYWORD_GENERATION_STATUS = DocumentMessageFormatter.keywordSearchUsedStatus()
        private val SEMANTIC_GENERATION_STATUS = DocumentMessageFormatter.semanticSearchUsedStatus()
        private const val BACKGROUND_TASKS_MAX_PDF_ITEMS = 8
        private const val SEMANTIC_INDEX_STATUS_IDLE = "idle"
        private const val SEMANTIC_INDEX_STATUS_RUNNING = "running"
        private const val SEMANTIC_INDEX_STATUS_SUCCESS = "success"
        private const val SEMANTIC_INDEX_STATUS_FAILED = "failed"
        private const val ATTACH_BUTTON_PRESS_DURATION_MS = 120L
        private const val SEND_BUTTON_PULSE_DURATION_MS = 420L
        private const val TYPING_PULSE_DURATION_MS = 680L
        private const val LOCAL_MODEL_TEST_MAX_OUTPUT_CHARS = 240
        private const val LOCAL_MODEL_SHORT_TEST_PROMPT =
            "اكتب جملة عربية قصيرة جدًا من 4 إلى 6 كلمات فقط."
        private const val LOCAL_MODEL_SPEED_TEST_PROMPT =
            "اكتب قائمة من 1 إلى 10 بالأرقام فقط في سطر واحد."
        private val SEMANTIC_FALLBACK_GENERATION_STATUS =
            DocumentMessageFormatter.semanticFallbackStatus()
    }
}

private data class RagDiagnosticsData(
    val ragModeLabel: String,
    val embeddingBackendLabel: String,
    val embeddingModelImported: Boolean,
    val embeddingModelDisplayPath: String,
    val selectedDocumentTitle: String,
    val indexInfo: EmbeddingStore.EmbeddingIndexInfo?,
    val lastState: String,
    val canBuildIndex: Boolean,
    val lastOperation: LastRagOperationResult? = null
)

private data class LastRagOperationResult(
    val query: String,
    val chunks: List<RetrievedChunk>,
    val rejectedCount: Int,
    val fullContextSentToModel: String
)

private data class LocalModelProbeResult(
    val success: Boolean,
    val firstTokenLatencyMs: Long? = null,
    val totalDurationMs: Long? = null,
    val responseCharCount: Int? = null,
    val errorMessage: String? = null
)

private enum class LocalModelQuickTestType(val label: String) {
    SHORT_GENERATION("اختبار التوليد القصير"),
    MEMORY("اختبار الذاكرة"),
    SPEED("اختبار السرعة")
}

private data class DocumentRetrievalOutcome(
    val chunks: List<RetrievedChunk> = emptyList(),
    val generationStatus: String = "جاري التوليد..."
)

private data class DocumentContextResult(
    val context: String?,
    val generationStatus: String
)

private fun String.safeTruncate(maxChars: Int): String {
    return if (length <= maxChars) this else take(maxChars)
}

private class SimpleTextWatcher(
    private val onTextChangedAction: () -> Unit
) : android.text.TextWatcher {
    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
        onTextChangedAction()
    }
    override fun afterTextChanged(s: android.text.Editable?) = Unit
}
