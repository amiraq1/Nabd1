package com.example.localqwen

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.provider.OpenableColumns
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.localqwen.chat.ChatMessage
import com.example.localqwen.chat.ChatSession
import com.example.localqwen.chat.ChatSessionStore
import com.example.localqwen.chat.Role
import com.example.localqwen.document.DocumentStore
import com.example.localqwen.document.DocumentToolIntent
import com.example.localqwen.document.DocumentToolRequest
import com.example.localqwen.document.DocumentToolRouter
import com.example.localqwen.document.LocalDocument
import com.example.localqwen.model.ModelManager
import com.example.localqwen.model.ModelManager.SupportedModel
import com.example.localqwen.tools.PhoneToolIntent
import com.example.localqwen.tools.PhoneToolManager
import com.example.localqwen.tools.PhoneToolResult
import com.example.localqwen.tools.PhoneToolRouter
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
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
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class MainActivity : AppCompatActivity() {
    private lateinit var statusView: TextView
    private lateinit var chatContainer: LinearLayout
    private lateinit var chatScrollView: ScrollView
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

    private val pickModelRequestCode = 200
    private val pickImageRequestCode = 201
    private val pickPdfRequestCode = 202
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val chatMessages = mutableListOf<ChatMessage>()
    private val supportedModels = ModelManager.SUPPORTED_MODELS
    private val preferences by lazy { getSharedPreferences("nabd_prefs", MODE_PRIVATE) }
    private val modelLock = Any()

    private var selectedModel: SupportedModel = supportedModels.first()
    private var loadedModelId: String? = null
    private var lastAssistantResponse: String = ""
    private var activeAssistantMessageIndex: Int = -1
    private var activeAssistantTextView: TextView? = null
    private var lastAutoScrollAt = 0L
    private var saveSessionJob: Job? = null

    @Volatile
    private var engine: Engine? = null

    @Volatile
    private var conversation: Conversation? = null

    private var isLoadingModel = false
    private var isGenerating = false
    private var isProcessingFile = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        modelManager = ModelManager(this)
        documentStore = DocumentStore(preferences)
        chatSessionStore = ChatSessionStore(preferences)
        phoneToolManager = PhoneToolManager(this)

        statusView = findViewById(R.id.statusTextView)
        chatContainer = findViewById(R.id.chatContainer)
        chatScrollView = findViewById(R.id.chatScrollView)
        typingIndicatorView = findViewById(R.id.tvTypingIndicator)
        inputView = findViewById(R.id.etMessage)
        attachButton = findViewById(R.id.btnAttachFile)
        optionsButton = findViewById(R.id.optionsButton)
        sendButton = findViewById(R.id.btnSend)
        sendProgressBar = findViewById(R.id.sendProgressBar)

        selectedModel = supportedModels.find {
            it.id == preferences.getString(KEY_SELECTED_MODEL_ID, null)
        } ?: supportedModels.first()

        migrateOldHistoryIfNeeded()
        loadActiveSession()
        setStatusInfo(currentStatus())
        renderChatHistory()

        optionsButton.setOnClickListener { showOptionsBottomSheet() }
        attachButton.setOnClickListener { showAttachmentTypeDialog() }
        sendButton.setOnClickListener { sendPrompt() }

        inputView.addTextChangedListener(SimpleTextWatcher { updateButtons() })
        updateButtons()
    }

    override fun onDestroy() {
        saveSessionJob?.cancel()
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
            chatSessionStore.saveSession(session)
            chatSessionStore.setActiveSessionId(session.id)
        }
    }

    private fun loadActiveSession() {
        val session = chatSessionStore.getActiveOrCreateSession()
        chatMessages.clear()
        runCatching {
            val historyJson = JSONArray(session.messagesJson)
            for (index in 0 until historyJson.length()) {
                val item = historyJson.getJSONObject(index)
                val role = runCatching {
                    Role.valueOf(item.optString("role", Role.ASSISTANT.name))
                }.getOrDefault(Role.ASSISTANT)
                chatMessages.add(
                    ChatMessage(
                        role = role,
                        text = item.optString("text", ""),
                        timestamp = item.optLong("timestamp", System.currentTimeMillis())
                    )
                )
            }
        }
        lastAssistantResponse = session.lastAssistantResponse
        activeAssistantMessageIndex = chatMessages.indexOfLast { it.role == Role.ASSISTANT }
        session.selectedDocumentId?.let(documentStore::setSelectedDocumentId) ?: documentStore.clearSelectedDocumentId()
    }

    private fun startNewChat() {
        chatMessages.clear()
        lastAssistantResponse = ""
        activeAssistantMessageIndex = -1
        activeAssistantTextView = null
        documentStore.clearSelectedDocumentId()
        chatSessionStore.createNewSession()
        renderChatHistory()
        setStatusInfo("تم إنشاء محادثة جديدة")
        updateButtons()
    }

    private fun switchSession(id: String) {
        chatSessionStore.setActiveSessionId(id)
        loadActiveSession()
        renderChatHistory()
        setStatusInfo(currentStatus())
        updateButtons()
    }

    private fun currentStatus(): String {
        val session = chatSessionStore.getActiveOrCreateSession()
        val base = when {
            engine != null && loadedModelId == selectedModel.id -> "جاهز • ${selectedModel.displayName}"
            modelManager.isModelReady(selectedModel) -> "غير مشغّل • ${selectedModel.displayName}"
            else -> "غير مستورد • ${selectedModel.displayName}"
        }
        return "المحادثة: ${session.title}\n$base"
    }

    private fun updateButtons() {
        val hasInput = inputView.text?.toString()?.trim()?.isNotEmpty() == true
        val busy = isGenerating || isProcessingFile
        val blocked = busy || isLoadingModel

        optionsButton.isEnabled = !blocked
        attachButton.isEnabled = !blocked
        inputView.isEnabled = !blocked
        sendButton.isEnabled = hasInput && !blocked

        sendButton.alpha = if (sendButton.isEnabled) 1.0f else 0.45f
        attachButton.alpha = if (attachButton.isEnabled) 1.0f else 0.5f
        sendButton.text = if (isGenerating) "…" else "↑"
        attachButton.text = "+"
        typingIndicatorView.visibility = if (isGenerating) View.VISIBLE else View.GONE
        sendProgressBar.visibility = if (blocked) View.VISIBLE else View.GONE
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

    private fun getSelectedDocument(): LocalDocument? {
        return documentStore.getDocument(documentStore.getSelectedDocumentId())
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

    private fun saveExtractedDocument(title: String, type: String, text: String) {
        if (text.isBlank()) return
        val document = LocalDocument(
            id = UUID.randomUUID().toString(),
            title = title,
            type = type,
            extractedText = text,
            createdAt = System.currentTimeMillis()
        )
        documentStore.saveDocument(document)
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

    data class RetrievedChunk(
        val documentId: String,
        val documentTitle: String,
        val chunkIndex: Int,
        val text: String,
        val score: Int
    )

    private fun retrieveDocumentChunks(query: String): List<RetrievedChunk> {
        val document = getSelectedDocument() ?: return emptyList()
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

    private fun buildDocumentContext(query: String): String? {
        val chunks = retrieveDocumentChunks(query)
        if (chunks.isEmpty()) return null

        val builder = StringBuilder()
        chunks.forEachIndexed { index, chunk ->
            val block = "[مقتطف ${index + 1} من ${chunk.documentTitle}]\n${chunk.text.safeTruncate(DOCUMENT_CHUNK_SIZE)}"
            if (builder.length + block.length + 2 <= MAX_DOCUMENT_CONTEXT_CHARS) {
                if (builder.isNotEmpty()) builder.append("\n\n")
                builder.append(block)
            }
        }
        return builder.toString().takeIf { it.isNotBlank() }
    }

    private fun sendPrompt() {
        if (isGenerating || isProcessingFile || isLoadingModel) return
        val input = inputView.text.toString().trim()
        if (input.isEmpty()) return

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

        if (engine == null || conversation == null || loadedModelId != selectedModel.id) {
            setStatusError("يرجى تشغيل نبض أولًا")
            return
        }

        val context = buildDocumentContext(input)
        val prompt = if (context != null) {
            "أنت نبض، أجب بناءً على السياق.\n\n$context\n\nالسؤال: $input"
        } else {
            "أنت نبض، أجب بالعربية.\n\nالسؤال: $input"
        }

        inputView.setText("")
        addChatMessage(ChatMessage(role = Role.USER, text = input))
        val assistantMessage = ChatMessage(role = Role.ASSISTANT, text = "")
        addChatMessage(assistantMessage)
        saveActiveSessionDebounced(
            autoTitle = chatMessages.count { it.role == Role.USER } == 1,
            firstUserMessage = input,
            immediate = true
        )
        startAssistantGeneration(prompt, assistantMessage, "جاري التوليد...")
    }

    private fun addChatMessage(message: ChatMessage) {
        chatMessages.add(message)
        val view = createMessageView(message)
        chatContainer.addView(view)
        if (message.role == Role.ASSISTANT) {
            activeAssistantMessageIndex = chatMessages.lastIndex
            activeAssistantTextView = view as? TextView
        }
        scheduleAutoScroll(force = true)
    }

    private fun updateAssistantMessage(text: String, forceScroll: Boolean = false) {
        if (activeAssistantMessageIndex !in chatMessages.indices) return
        chatMessages[activeAssistantMessageIndex].text = text
        activeAssistantTextView?.text = formatAssistantMessage(text)
        scheduleAutoScroll(force = forceScroll)
    }

    private fun renderChatHistory() {
        chatContainer.removeAllViews()
        activeAssistantTextView = null
        chatMessages.forEachIndexed { index, message ->
            val view = createMessageView(message)
            chatContainer.addView(view)
            if (index == activeAssistantMessageIndex && message.role == Role.ASSISTANT) {
                activeAssistantTextView = view as? TextView
            }
        }
        scheduleAutoScroll(force = true)
    }

    private fun createMessageView(message: ChatMessage): View {
        return when (message.role) {
            Role.USER -> createUserMessageView(displayTextForChat(message.text))
            Role.ASSISTANT -> createAssistantMessageView(displayTextForChat(message.text))
            Role.SYSTEM -> createSystemMessageView(displayTextForChat(message.text))
        }
    }

    private fun createUserMessageView(text: String) = TextView(this).apply {
        this.text = text
        setTextColor(Color.WHITE)
        setPadding(32, 16, 32, 16)
        background = ContextCompat.getDrawable(context, R.drawable.bg_bubble_user)
        setTextIsSelectable(true)
        layoutParams = LinearLayout.LayoutParams(-2, -2).apply {
            gravity = Gravity.END
            topMargin = 8
            bottomMargin = 8
        }
    }

    private fun createAssistantMessageView(message: String) = TextView(this).apply {
        text = formatAssistantMessage(message)
        setTextColor(ContextCompat.getColor(context, R.color.nabd_text))
        setTextIsSelectable(true)
        setPadding(16, 16, 16, 16)
    }

    private fun createSystemMessageView(text: String) = TextView(this).apply {
        this.text = text
        setTextColor(Color.GRAY)
        gravity = Gravity.CENTER
        setPadding(16, 8, 16, 8)
    }

    private fun formatAssistantMessage(message: String): String {
        return "نبض:\n${displayTextForChat(message)}"
    }

    private fun displayTextForChat(text: String): String {
        return if (text.length <= MAX_CHAT_DISPLAY_CHARS) {
            text
        } else {
            text.take(MAX_CHAT_DISPLAY_CHARS) + "\n\n(تم اختصار العرض لطول المحادثة)"
        }
    }

    private fun scheduleAutoScroll(force: Boolean = false) {
        val now = SystemClock.elapsedRealtime()
        if (!force && now - lastAutoScrollAt < STREAM_SCROLL_THROTTLE_MS) return
        lastAutoScrollAt = now
        chatScrollView.post { chatScrollView.fullScroll(View.FOCUS_DOWN) }
    }

    private fun startAssistantGeneration(prompt: String, assistant: ChatMessage, status: String) {
        val currentConversation = conversation
        if (currentConversation == null) {
            setStatusError("تعذر تشغيل النموذج. جرّب نموذجًا أخف أو أعد استيراده.")
            return
        }

        isGenerating = true
        updateButtons()
        setStatusInfo(status)

        scope.launch {
            try {
                val output = StringBuilder()
                withContext(Dispatchers.IO) {
                    currentConversation.sendMessageAsync(prompt).collect { chunk ->
                        output.append(chunk.toString())
                        val shouldRefresh = output.length <= 256 || output.length - assistant.text.length >= STREAM_UPDATE_MIN_CHARS
                        if (shouldRefresh) {
                            val snapshot = output.toString()
                            withContext(Dispatchers.Main) {
                                assistant.text = snapshot
                                updateAssistantMessage(snapshot)
                            }
                        }
                    }
                }

                val finalText = output.toString().ifBlank { "(فارغ)" }
                assistant.text = finalText
                lastAssistantResponse = finalText
                updateAssistantMessage(finalText, forceScroll = true)
                saveActiveSessionDebounced(immediate = true)
                setStatusSuccess("جاهز")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: OutOfMemoryError) {
                setStatusError("حدث خطأ أثناء توليد الرد.")
            } catch (_: Exception) {
                setStatusError("حدث خطأ أثناء توليد الرد.")
            } finally {
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
            val messagesJson = withContext(Dispatchers.Default) {
                JSONArray().apply {
                    snapshotMessages.forEach { message ->
                        put(
                            JSONObject()
                                .put("role", message.role.name)
                                .put("text", message.text)
                                .put("timestamp", message.timestamp)
                        )
                    }
                }.toString()
            }

            withContext(Dispatchers.IO) {
                chatSessionStore.updateActiveSession(
                    messagesJson = messagesJson,
                    lastAssistantResponse = snapshotAssistantResponse,
                    selectedDocumentId = selectedDocumentId,
                    documentAnswerLength = "short",
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
        if (loadedModelId == selectedModel.id && engine != null && conversation != null) {
            setStatusSuccess("تم تشغيل نبض")
            return
        }

        isLoadingModel = true
        updateButtons()
        setStatusInfo("جاري تشغيل نبض...")

        scope.launch {
            try {
                val requestModel = selectedModel
                val loaded = withContext(Dispatchers.IO) {
                    synchronized(modelLock) {
                        closeModelResourcesLocked()
                        val config = EngineConfig(
                            modelPath = modelManager.modelPath(requestModel),
                            cacheDir = cacheDir.absolutePath,
                            backend = Backend.CPU()
                        )
                        val newEngine = Engine(config)
                        newEngine.initialize()
                        val newConversation = newEngine.createConversation()
                        Triple(newEngine, newConversation, requestModel.id)
                    }
                }

                engine = loaded.first
                conversation = loaded.second
                loadedModelId = loaded.third
                setStatusSuccess("تم تشغيل نبض")
            } catch (_: OutOfMemoryError) {
                closeModelResources()
                setStatusError("تعذر تشغيل النموذج. جرّب نموذجًا أخف أو أعد استيراده.")
            } catch (_: Exception) {
                closeModelResources()
                setStatusError("تعذر تشغيل النموذج. جرّب نموذجًا أخف أو أعد استيراده.")
            } finally {
                isLoadingModel = false
                updateButtons()
            }
        }
    }

    private fun unloadModel() {
        if (isLoadingModel) return
        isLoadingModel = true
        updateButtons()
        setStatusInfo("جاري إيقاف نبض...")

        scope.launch {
            try {
                withContext(Dispatchers.IO) { closeModelResources() }
                setStatusInfo("تم إيقاف نبض")
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
        runCatching { conversation?.close() }
        conversation = null
        runCatching { engine?.close() }
        engine = null
        loadedModelId = null
    }

    private fun showOptionsBottomSheet() {
        val sheet = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_options, null)
        val dialog = BottomSheetDialog(this)
        dialog.setContentView(sheet)

        addOptionRow(
            sheet.findViewById(R.id.sectionModel),
            "▶",
            if (engine != null) "إيقاف نبض" else "تشغيل نبض"
        ) {
            dialog.dismiss()
            if (engine != null) unloadModel() else loadModel()
        }
        addOptionRow(sheet.findViewById(R.id.sectionTools), "◈", "مركز الأدوات") {
            dialog.dismiss()
            showToolsCenter()
        }
        addOptionRow(sheet.findViewById(R.id.sectionTools), "＋", "إضافة ملف") {
            dialog.dismiss()
            showAttachmentTypeDialog()
        }
        addOptionRow(
            sheet.findViewById(R.id.sectionConversation),
            "×",
            "مسح",
            titleColor = Color.RED
        ) {
            dialog.dismiss()
            confirmClearChat()
        }
        dialog.show()
    }

    private fun showToolsCenter() {
        val items = arrayOf("البطارية", "الجهاز", "المكتبة", "السجل")
        MaterialAlertDialogBuilder(this)
            .setTitle("مركز الأدوات")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> appendToolResultToChat(phoneToolManager.getBatteryStatus())
                    1 -> appendToolResultToChat(phoneToolManager.getDeviceInfo())
                    2 -> showDocumentLibraryDialog()
                    3 -> showChatHistoryDialog()
                }
            }
            .show()
    }

    private fun addOptionRow(
        container: LinearLayout,
        icon: String,
        title: String,
        titleColor: Int = Color.BLACK,
        onClick: () -> Unit
    ) {
        val row = LayoutInflater.from(this).inflate(R.layout.item_option_row, container, false)
        row.findViewById<TextView>(R.id.tvOptionIcon).text = icon
        row.findViewById<TextView>(R.id.tvOptionTitle).apply {
            text = title
            setTextColor(titleColor)
        }
        row.setOnClickListener { onClick() }
        container.addView(row)
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
                        val result = withContext(Dispatchers.IO) {
                            performLocalDocumentSearch(request.query.orEmpty())
                        }
                        addChatMessage(ChatMessage(role = Role.ASSISTANT, text = result))
                    }

                    DocumentToolIntent.SHOW_DOCUMENT_LIBRARY -> showDocumentLibraryDialog()
                    DocumentToolIntent.CLEAR_SELECTED_DOCUMENT -> {
                        documentStore.clearSelectedDocumentId()
                        addChatMessage(ChatMessage(role = Role.ASSISTANT, text = "تم إلغاء اختيار المستند"))
                        setStatusInfo(currentStatus())
                    }

                    DocumentToolIntent.CURRENT_DOCUMENT_SUMMARY -> {
                        addChatMessage(
                            ChatMessage(role = Role.ASSISTANT, text = performCurrentDocumentSummary())
                        )
                    }
                }
            }
            saveActiveSessionDebounced(immediate = true)
        }
    }

    private fun performLocalDocumentSearch(query: String): String {
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

    private fun performCurrentDocumentSummary(): String {
        val document = getSelectedDocument() ?: return "لا يوجد مستند محدد"
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
        activeAssistantTextView = null
        renderChatHistory()
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
            .setTitle("ملف")
            .setItems(arrayOf("صورة", "PDF")) { _, which ->
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

    private fun showDocumentLibraryDialog() {
        scope.launch {
            val documents = withContext(Dispatchers.IO) { documentStore.getDocuments() }
            if (documents.isEmpty()) {
                setStatusError("المكتبة فارغة")
                return@launch
            }

            val labels = documents.map { document ->
                "${document.title} • ${document.type} • ${formatDate(document.createdAt)} • ${formatDocumentSize(document.extractedText.length)}"
            }.toTypedArray()

            MaterialAlertDialogBuilder(this@MainActivity)
                .setTitle("المكتبة")
                .setItems(labels) { _, which ->
                    documentStore.setSelectedDocumentId(documents[which].id)
                    setStatusSuccess("تم اختيار: ${documents[which].title}")
                    setStatusInfo(currentStatus())
                }
                .show()
        }
    }

    private fun showChatHistoryDialog() {
        scope.launch {
            val sessions = withContext(Dispatchers.IO) { chatSessionStore.getAllSessions() }
            if (sessions.isEmpty()) {
                Toast.makeText(this@MainActivity, "لا يوجد سجل", Toast.LENGTH_SHORT).show()
                return@launch
            }

            MaterialAlertDialogBuilder(this@MainActivity)
                .setTitle("السجل")
                .setItems(sessions.map { it.title }.toTypedArray()) { _, which ->
                    switchSession(sessions[which].id)
                }
                .show()
        }
    }

    private fun selectModel(position: Int) {
        val model = supportedModels[position]
        if (selectedModel.id == model.id) return

        selectedModel = model
        preferences.edit().putString(KEY_SELECTED_MODEL_ID, model.id).apply()

        if (loadedModelId != null && loadedModelId != model.id) {
            unloadModel()
        } else {
            setStatusInfo(currentStatus())
        }
        updateButtons()
    }

    private suspend fun importModelFile(uri: Uri) {
        contentResolver.openInputStream(uri)?.buffered()?.use { input ->
            modelManager.modelFile(selectedModel).outputStream().buffered().use { output ->
                input.copyTo(output, DEFAULT_COPY_BUFFER_SIZE)
            }
        } ?: throw IOException("Unable to open model stream")
    }

    private fun importModelFromUri(uri: Uri) {
        scope.launch {
            isLoadingModel = true
            updateButtons()
            setStatusInfo("جاري الاستيراد...")
            try {
                withContext(Dispatchers.IO) { importModelFile(uri) }
                setStatusSuccess("تم استيراد النموذج بنجاح")
                delay(1000)
                setStatusInfo(currentStatus())
            } catch (_: Exception) {
                setStatusError("فشل الاستيراد")
            } finally {
                isLoadingModel = false
                updateButtons()
            }
        }
    }

    private fun processImageUri(uri: Uri) {
        if (engine == null) {
            setStatusError("شغل نبض أولاً")
            return
        }

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
                    withContext(Dispatchers.IO) {
                        saveExtractedDocument(getDisplayName(uri) ?: "صورة", "image", extractedText)
                    }
                    addChatMessage(
                        ChatMessage(role = Role.USER, text = "تحليل صورة: ${getDisplayName(uri)}")
                    )
                    val assistant = ChatMessage(role = Role.ASSISTANT, text = "")
                    addChatMessage(assistant)
                    startAssistantGeneration(
                        "أنت نبض، حلل النص المستخرج من الصورة:\n${extractedText.safeTruncate(PROMPT_TEXT_LIMIT)}",
                        assistant,
                        "جاري التحليل..."
                    )
                } else {
                    setStatusError("لم يتم العثور على نص واضح.")
                }
            } catch (_: OutOfMemoryError) {
                setStatusError("الملف كبير جدًا للمعالجة الحالية.")
            } catch (_: Exception) {
                setStatusError("الملف كبير جدًا للمعالجة الحالية.")
            } finally {
                isProcessingFile = false
                updateButtons()
            }
        }
    }

    private fun processPdfUri(uri: Uri) {
        if (engine == null) {
            setStatusError("شغل نبض أولاً")
            return
        }

        isProcessingFile = true
        updateButtons()
        setStatusInfo("قراءة PDF...")

        scope.launch {
            try {
                val extractedText = withContext(Dispatchers.IO) { extractPdfText(uri) }
                if (extractedText.isNotEmpty()) {
                    withContext(Dispatchers.IO) {
                        saveExtractedDocument(getDisplayName(uri) ?: "PDF", "pdf", extractedText)
                    }
                    addChatMessage(
                        ChatMessage(role = Role.USER, text = "تحليل ملف PDF: ${getDisplayName(uri)}")
                    )
                    val assistant = ChatMessage(role = Role.ASSISTANT, text = "")
                    addChatMessage(assistant)
                    startAssistantGeneration(
                        "أنت نبض، حلل النص المستخرج من PDF:\n${extractedText.safeTruncate(PROMPT_TEXT_LIMIT)}",
                        assistant,
                        "جاري التحليل..."
                    )
                } else {
                    setStatusError("لم يتم العثور على نص واضح.")
                }
            } catch (_: PdfTooLargeException) {
                setStatusError("تعذر تحليل الملف بسبب حجمه الكبير. جرّب ملفًا أصغر.")
            } catch (_: OutOfMemoryError) {
                setStatusError("الملف كبير جدًا للمعالجة الحالية.")
            } catch (_: Exception) {
                setStatusError("الملف كبير جدًا للمعالجة الحالية.")
            } finally {
                isProcessingFile = false
                updateButtons()
            }
        }
    }

    private suspend fun extractPdfText(uri: Uri): String {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
                val renderer = PdfRenderer(descriptor)
                try {
                    val text = StringBuilder()
                    val pageCount = minOf(renderer.pageCount, MAX_PDF_PAGES)
                    for (index in 0 until pageCount) {
                        val page = renderer.openPage(index)
                        try {
                            val bitmap = createSafePdfBitmap(page)
                            try {
                                val result = recognizer.process(InputImage.fromBitmap(bitmap, 0)).awaitTask()
                                val pageText = result.text.trim()
                                if (pageText.isNotEmpty()) {
                                    if (text.isNotEmpty()) text.append("\n\n")
                                    text.append(pageText)
                                }
                            } finally {
                                if (!bitmap.isRecycled) bitmap.recycle()
                            }
                        } finally {
                            page.close()
                        }
                    }
                    return text.toString().trim()
                } finally {
                    renderer.close()
                }
            } ?: return ""
        } catch (_: OutOfMemoryError) {
            throw PdfTooLargeException()
        } finally {
            recognizer.close()
        }
    }

    private fun createSafePdfBitmap(page: PdfRenderer.Page): Bitmap {
        val width = page.width.coerceAtLeast(1)
        val height = page.height.coerceAtLeast(1)
        val scaledWidth = (width * PDF_RENDER_SCALE).toInt().coerceAtLeast(1)
        val scaledHeight = (height * PDF_RENDER_SCALE).toInt().coerceAtLeast(1)
        val ratio = minOf(
            1f,
            MAX_PDF_BITMAP_DIMENSION.toFloat() / scaledWidth,
            MAX_PDF_BITMAP_DIMENSION.toFloat() / scaledHeight
        )
        val targetWidth = (scaledWidth * ratio).toInt().coerceAtLeast(1)
        val targetHeight = (scaledHeight * ratio).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        return bitmap
    }

    private fun formatDate(timestamp: Long): String {
        return SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(Date(timestamp))
    }

    private fun formatDocumentSize(chars: Int): String {
        return when {
            chars >= 1_000_000 -> String.format(Locale.US, "%.1fM", chars / 1_000_000f)
            chars >= 1_000 -> String.format(Locale.US, "%.1fK", chars / 1_000f)
            else -> "$chars"
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
        val uri = data?.data ?: return
        when (requestCode) {
            pickModelRequestCode -> importModelFromUri(uri)
            pickImageRequestCode -> processImageUri(uri)
            pickPdfRequestCode -> processPdfUri(uri)
        }
    }

    private class PdfTooLargeException : Exception()

    companion object {
        private const val KEY_CHAT_HISTORY_TEXT = "chat_history_text"
        private const val KEY_CHAT_MESSAGES_JSON = "chat_messages_json"
        private const val KEY_SELECTED_MODEL_ID = "selected_model_id"
        private const val MAX_PDF_PAGES = 3
        private const val MAX_PDF_BITMAP_DIMENSION = 2048
        private const val PDF_RENDER_SCALE = 1.2f
        private const val MAX_DOCUMENT_CONTEXT_CHARS = 5_000
        private const val MAX_RETRIEVED_CHUNKS = 3
        private const val DOCUMENT_CHUNK_SIZE = 1_200
        private const val DOCUMENT_TEXT_LIMIT = 200_000
        private const val PROMPT_TEXT_LIMIT = 6_000
        private const val STREAM_SCROLL_THROTTLE_MS = 300L
        private const val STREAM_UPDATE_MIN_CHARS = 48
        private const val MAX_CHAT_DISPLAY_CHARS = 40_000
        private const val MAX_DOCUMENT_SEARCH_RESULTS = 5
        private const val DOCUMENT_SEARCH_EXCERPT_CHARS = 250
        private const val DEFAULT_COPY_BUFFER_SIZE = 8_192
    }
}

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
