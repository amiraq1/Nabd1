package com.example.localqwen

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.localqwen.chat.ChatAdapter
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
import com.example.localqwen.prompt.NabdSystemPrompt
import com.example.localqwen.tools.PhoneToolIntent
import com.example.localqwen.tools.PhoneToolManager
import com.example.localqwen.tools.PhoneToolResult
import com.example.localqwen.tools.PhoneToolRouter
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

    private val pickModelRequestCode = 200
    private val pickImageRequestCode = 201
    private val pickPdfRequestCode = 202
    private val settingsRequestCode = 203
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val chatMessages = mutableListOf<ChatMessage>()
    private val supportedModels = ModelManager.SUPPORTED_MODELS
    private val preferences by lazy { getSharedPreferences("nabd_prefs", MODE_PRIVATE) }
    private val workManager by lazy { WorkManager.getInstance(applicationContext) }
    private val modelLock = Any()
    private val handledPdfWorkIds = mutableSetOf<UUID>()

    private var selectedModel: SupportedModel = supportedModels.first()
    private var loadedModelId: String? = null
    private var lastAssistantResponse: String = ""
    private var activeAssistantMessageIndex: Int = -1
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
        chatMessages.addAll(loadChatMessages(session.messagesJson))
        lastAssistantResponse = cleanForDisplay(session.lastAssistantResponse, preserveMarkdown = true)
        activeAssistantMessageIndex = chatMessages.indexOfLast { it.role == Role.ASSISTANT }
        session.selectedDocumentId?.let(documentStore::setSelectedDocumentId) ?: documentStore.clearSelectedDocumentId()
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
        chatSessionStore.createNewSession()
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

    private fun currentModelStatusLabel(): String {
        return when {
            engine != null && loadedModelId == selectedModel.id -> "مشغّل"
            modelManager.isModelReady(selectedModel) -> "غير مشغّل"
            else -> "غير مستورد"
        }
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

    private fun currentDocumentAnswerLength(): String {
        return chatSessionStore.getActiveOrCreateSession().documentAnswerLength ?: "short"
    }

    private fun documentAnswerLengthLabel(value: String): String {
        return when (value) {
            "medium" -> "متوسط"
            "long" -> "مفصل"
            else -> "مختصر"
        }
    }

    private fun updateDocumentAnswerLength(value: String) {
        val session = chatSessionStore.getActiveOrCreateSession()
        session.documentAnswerLength = value
        session.updatedAt = System.currentTimeMillis()
        chatSessionStore.saveSession(session)
        setStatusSuccess("تم ضبط طول إجابة المستند: ${documentAnswerLengthLabel(value)}")
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
            NabdSystemPrompt.documentPrompt(
                userInput = input,
                contextChunks = context,
                answerLengthInstruction = currentDocumentAnswerLengthInstruction()
            )
        } else {
            NabdSystemPrompt.normalChatPrompt(input)
        }

        inputView.setText("")
        addChatMessage(ChatMessage(role = Role.USER, text = input))
        val assistantMessage = ChatMessage(role = Role.ASSISTANT, text = "")
        addChatMessage(assistantMessage)
        startAssistantGeneration(
            prompt = prompt,
            assistant = assistantMessage,
            status = "جاري التوليد...",
            autoTitle = chatMessages.count { it.role == Role.USER } == 1,
            firstUserMessage = input
        )
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

    private fun updateAssistantMessage(text: String, forceScroll: Boolean = false) {
        if (activeAssistantMessageIndex !in chatMessages.indices) return
        val cleanedText = cleanForDisplay(text, preserveMarkdown = true)
        chatMessages[activeAssistantMessageIndex].text = cleanedText
        chatAdapter.updateLastAssistantMessage(cleanedText, renderMarkdown = forceScroll)
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
                        .replace("`", "")
                        .replace(Regex("^\\s*#{1,4}\\s*"), "")
                        .replace(Regex("^\\s*[\\*-]\\s+"), "• ")
                        .replace(Regex("^\\s*•\\s+"), "• ")
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
        firstUserMessage: String? = null
    ) {
        val currentConversation = conversation
        if (currentConversation == null) {
            setStatusError("تعذر تشغيل النموذج. جرّب نموذجًا أخف أو أعد استيراده.")
            return
        }

        isGenerating = true
        updateButtons()
        setStatusInfo(status)
        chatAdapter.markLastAssistantStreaming()

        scope.launch {
            try {
                val output = StringBuilder()
                var lastRenderedRawLength = 0
                withContext(Dispatchers.IO) {
                    currentConversation.sendMessageAsync(prompt).collect { chunk ->
                        output.append(chunk.toString())
                        val shouldRefresh =
                            output.length <= 256 || output.length - lastRenderedRawLength >= STREAM_UPDATE_MIN_CHARS
                        if (shouldRefresh) {
                            val snapshot = output.toString()
                            lastRenderedRawLength = output.length
                            withContext(Dispatchers.Main) {
                                val cleanedSnapshot = cleanForDisplay(snapshot, preserveMarkdown = true)
                                assistant.text = cleanedSnapshot
                                updateAssistantMessage(cleanedSnapshot)
                            }
                        }
                    }
                }

                val finalText = cleanForDisplay(
                    output.toString(),
                    preserveMarkdown = true
                ).ifBlank { "(فارغ)" }
                assistant.text = finalText
                lastAssistantResponse = finalText
                updateAssistantMessage(finalText, forceScroll = true)
                saveActiveSessionDebounced(
                    autoTitle = autoTitle,
                    firstUserMessage = firstUserMessage,
                    immediate = true
                )
                setStatusSuccess("جاهز")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: OutOfMemoryError) {
                saveActiveSessionDebounced(
                    autoTitle = autoTitle,
                    firstUserMessage = firstUserMessage,
                    immediate = true
                )
                setStatusError("حدث خطأ أثناء توليد الرد.")
            } catch (_: Exception) {
                saveActiveSessionDebounced(
                    autoTitle = autoTitle,
                    firstUserMessage = firstUserMessage,
                    immediate = true
                )
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

        val subtitleView = sheet.findViewById<TextView>(R.id.tvOptionsSubtitle)
        val statusChip = sheet.findViewById<TextView>(R.id.tvStatusChip)
        subtitleView.text = modelDescription(selectedModel)
        val isModelActive = engine != null && loadedModelId == selectedModel.id
        val isModelImported = modelManager.isModelReady(selectedModel)
        statusChip.text = when {
            isModelActive -> "مشغّل"
            isModelImported -> "جاهز"
            else -> "غير مستورد"
        }
        if (isModelImported) {
            statusChip.background = ContextCompat.getDrawable(this, R.drawable.bg_status_chip_ready)
            statusChip.setTextColor(ContextCompat.getColor(this, R.color.nabd_success))
        } else {
            statusChip.background = ContextCompat.getDrawable(this, R.drawable.bg_status_chip_inactive)
            statusChip.setTextColor(ContextCompat.getColor(this, R.color.nabd_text_secondary))
        }

        addOptionRow(
            sheet.findViewById(R.id.sectionModel),
            if (isModelActive) "■" else "▶",
            if (isModelActive) "إيقاف نبض" else "تشغيل نبض",
            subtitle = currentModelStatusLabel()
        ) {
            dialog.dismiss()
            if (isModelActive) unloadModel() else loadModel()
        }
        addOptionRow(
            sheet.findViewById(R.id.sectionTools),
            "◈",
            "مركز الأدوات"
        ) {
            dialog.dismiss()
            showToolsCenter()
        }
        addOptionRow(sheet.findViewById(R.id.sectionTools), "＋", "إضافة ملف") {
            dialog.dismiss()
            showAttachmentTypeDialog()
        }
        addOptionRow(sheet.findViewById(R.id.sectionTools), "≡", "الإعدادات") {
            dialog.dismiss()
            openSettingsPage()
        }
        addOptionRow(sheet.findViewById(R.id.sectionConversation), "＋", "محادثة جديدة") {
            dialog.dismiss()
            startNewChat()
        }
        addOptionRow(sheet.findViewById(R.id.sectionConversation), "◷", "سجل المحادثات") {
            dialog.dismiss()
            showChatHistoryDialog()
        }
        addOptionRow(
            sheet.findViewById(R.id.sectionInfo),
            "؟",
            "حول نبض"
        ) {
            dialog.dismiss()
            showAboutDialog()
        }

        dialog.setOnShowListener {
            val bottomSheet =
                dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet) ?: return@setOnShowListener
            val behavior = BottomSheetBehavior.from(bottomSheet)
            val screenHeight = resources.displayMetrics.heightPixels
            bottomSheet.layoutParams = bottomSheet.layoutParams.apply {
                height = (screenHeight * 0.72f).toInt()
            }
            behavior.peekHeight = (screenHeight * 0.60f).toInt()
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
            behavior.isDraggable = true
        }
        dialog.show()
    }

    private fun openSettingsPage() {
        val session = chatSessionStore.getActiveOrCreateSession()
        val intent = SettingsActivity.createIntent(
            context = this,
            modelDescription = modelDescription(selectedModel),
            modelStatus = currentModelStatusLabel(),
            documentAnswerLength = currentDocumentAnswerLength(),
            selectedDocumentTitle = getSelectedDocument()?.title,
            sessionTitle = session.title,
            appVersion = packageManager.getPackageInfo(packageName, 0).versionName ?: "1.0"
        )
        startActivityForResult(intent, settingsRequestCode)
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
        subtitle: String? = null,
        titleColor: Int = ContextCompat.getColor(this, R.color.nabd_on_surface),
        iconColor: Int = Color.parseColor("#A0A0A0"),
        enabled: Boolean = true,
        onClick: () -> Unit
    ) {
        val row = LayoutInflater.from(this).inflate(R.layout.item_option_row, container, false)
        row.findViewById<TextView>(R.id.tvOptionIcon).apply {
            text = icon
            setTextColor(iconColor)
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
        row.alpha = if (enabled) 1.0f else 0.45f
        row.isEnabled = enabled
        row.setOnClickListener { if (enabled) onClick() }
        container.addView(row)
    }

    private fun modelDescription(model: SupportedModel): String {
        return when (model.id) {
            "gemma_e2b" -> "${model.displayName} — سريع ومتوازن"
            "gemma_e4b" -> "${model.displayName} — للمهام الثقيلة"
            else -> model.displayName
        }
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
        MaterialAlertDialogBuilder(this)
            .setTitle("حول نبض")
            .setMessage(
                """
                نبض
                مساعد ذكاء اصطناعي محلي يعمل على جهازك.

                النماذج المدعومة:
                • Gemma E2B
                • Gemma E4B
                """.trimIndent()
            )
            .setPositiveButton("إغلاق", null)
            .show()
    }

    private fun handleSettingsAction(data: Intent?) {
        val action = data?.getStringExtra(SettingsActivity.EXTRA_ACTION) ?: return
        when (action) {
            SettingsActivity.ACTION_SELECT_MODEL -> showModelSelectionDialog()
            SettingsActivity.ACTION_IMPORT_MODEL -> openFilePicker()
            SettingsActivity.ACTION_OPEN_CHAT_HISTORY -> showChatHistoryDialog()
            SettingsActivity.ACTION_OPEN_DOCUMENT_LIBRARY -> showDocumentLibraryDialog()
            SettingsActivity.ACTION_SET_DOCUMENT_ANSWER_LENGTH -> {
                val value = data.getStringExtra(SettingsActivity.EXTRA_VALUE) ?: return
                updateDocumentAnswerLength(value)
                saveActiveSessionDebounced(immediate = true)
            }
            SettingsActivity.ACTION_CLEAR_SELECTED_DOCUMENT -> {
                documentStore.clearSelectedDocumentId()
                saveActiveSessionDebounced(immediate = true)
                setStatusSuccess("تم إلغاء اختيار المستند")
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
        val title = getDisplayName(uri) ?: DEFAULT_PDF_TITLE
        val request = OneTimeWorkRequestBuilder<PdfProcessingWorker>()
            .setInputData(
                workDataOf(
                    PdfProcessingWorker.KEY_PDF_URI to uri.toString(),
                    PdfProcessingWorker.KEY_PDF_TITLE to title
                )
            )
            .build()

        addChatMessage(ChatMessage(role = Role.SYSTEM, text = "تمت إضافة ملف PDF للمعالجة في الخلفية..."))
        saveActiveSessionDebounced(immediate = true)
        setStatusInfo("جاري تحليل ملف PDF في الخلفية...")

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
                        setStatusInfo("جاري استخراج النص من الصفحة $page من $total...")
                    } else {
                        setStatusInfo("جاري تحليل ملف PDF في الخلفية...")
                    }
                }

                WorkInfo.State.SUCCEEDED -> {
                    if (!handledPdfWorkIds.add(workId)) return@observe
                    val title = workInfo.outputData.getString(PdfProcessingWorker.KEY_PDF_TITLE)
                        ?: DEFAULT_PDF_TITLE
                    val extractedChars = workInfo.outputData.getInt(PdfProcessingWorker.KEY_EXTRACTED_CHARS, 0)
                    addChatMessage(
                        ChatMessage(
                            role = Role.SYSTEM,
                            text = "تم تحليل ملف PDF وحفظه في مكتبة المستندات: $title\nعدد الأحرف المستخرجة: $extractedChars"
                        )
                    )
                    saveActiveSessionDebounced(immediate = true)
                    setStatusSuccess("اكتمل تحليل ملف PDF")
                }

                WorkInfo.State.FAILED -> {
                    if (!handledPdfWorkIds.add(workId)) return@observe
                    val error = workInfo.outputData.getString(PdfProcessingWorker.KEY_ERROR_MESSAGE)
                        ?: "تعذر تحليل ملف PDF"
                    addChatMessage(ChatMessage(role = Role.SYSTEM, text = "تعذر تحليل ملف PDF: $error"))
                    saveActiveSessionDebounced(immediate = true)
                    setStatusError(error)
                }

                WorkInfo.State.CANCELLED -> {
                    if (!handledPdfWorkIds.add(workId)) return@observe
                    addChatMessage(ChatMessage(role = Role.SYSTEM, text = "تم إلغاء تحليل ملف PDF"))
                    saveActiveSessionDebounced(immediate = true)
                    setStatusError("تم إلغاء تحليل ملف PDF")
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
        if (requestCode == settingsRequestCode) {
            handleSettingsAction(data)
            return
        }
        val uri = data?.data ?: return
        when (requestCode) {
            pickModelRequestCode -> importModelFromUri(uri)
            pickImageRequestCode -> processImageUri(uri)
            pickPdfRequestCode -> {
                tryPersistPdfReadPermission(uri)
                processPdfUri(uri)
            }
        }
    }

    companion object {
        private const val KEY_CHAT_HISTORY_TEXT = "chat_history_text"
        private const val KEY_CHAT_MESSAGES_JSON = "chat_messages_json"
        private const val KEY_SELECTED_MODEL_ID = "selected_model_id"
        private const val MAX_DOCUMENT_CONTEXT_CHARS = 5_000
        private const val MAX_RETRIEVED_CHUNKS = 3
        private const val DOCUMENT_CHUNK_SIZE = 1_200
        private const val DOCUMENT_TEXT_LIMIT = 200_000
        private const val PROMPT_TEXT_LIMIT = 6_000
        private const val STREAM_UPDATE_MIN_CHARS = 48
        private const val MAX_DOCUMENT_SEARCH_RESULTS = 5
        private const val DOCUMENT_SEARCH_EXCERPT_CHARS = 250
        private const val DEFAULT_COPY_BUFFER_SIZE = 8_192
        private const val DEFAULT_PDF_TITLE = "ملف PDF"
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
