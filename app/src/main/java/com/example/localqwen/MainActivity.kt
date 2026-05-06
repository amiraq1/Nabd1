package com.example.localqwen

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.provider.OpenableColumns
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.text.Editable
import android.text.Spanned
import android.text.SpannableStringBuilder
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.localqwen.chat.ChatMessage
import com.example.localqwen.chat.Role
import com.example.localqwen.chat.ChatSession
import com.example.localqwen.chat.ChatSessionStore
import com.example.localqwen.document.DocumentStore
import com.example.localqwen.document.LocalDocument
import com.example.localqwen.document.DocumentToolIntent
import com.example.localqwen.document.DocumentToolRequest
import com.example.localqwen.document.DocumentToolRouter
import com.example.localqwen.model.ModelManager
import com.example.localqwen.model.ModelManager.SupportedModel
import com.example.localqwen.tools.PhoneToolManager
import com.example.localqwen.tools.PhoneToolResult
import com.example.localqwen.tools.PhoneToolIntent
import com.example.localqwen.tools.PhoneToolRouter
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.LogSeverity
import com.google.ai.edge.litertlm.Backend
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

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
    private var selectedModel: SupportedModel = supportedModels.first()
    private var loadedModelId: String? = null
    private var lastAssistantResponse: String = ""
    private var activeAssistantMessageIndex: Int = -1

    @Volatile private var engine: Engine? = null
    @Volatile private var conversation: Conversation? = null

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

        selectedModel = supportedModels.find { it.id == preferences.getString(KEY_SELECTED_MODEL_ID, null) }
            ?: supportedModels.first()

        migrateOldHistoryIfNeeded()
        loadActiveSession()
        setStatusInfo(currentStatus())
        renderChatHistory()

        optionsButton.setOnClickListener { showOptionsBottomSheet() }
        attachButton.setOnClickListener { showAttachmentTypeDialog() }
        sendButton.setOnClickListener { sendPrompt() }

        inputView.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = updateButtons()
            override fun afterTextChanged(s: Editable?) = Unit
        })

        updateButtons()
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
                val roleStr = item.optString("role", Role.ASSISTANT.name)
                val role = try { Role.valueOf(roleStr) } catch(e: Exception) { Role.ASSISTANT }
                chatMessages.add(ChatMessage(role = role, text = item.optString("text", ""), timestamp = item.optLong("timestamp", System.currentTimeMillis())))
            }
        }
        lastAssistantResponse = session.lastAssistantResponse
        activeAssistantMessageIndex = chatMessages.indexOfLast { it.role == Role.ASSISTANT }
        if (session.selectedDocumentId != null) documentStore.setSelectedDocumentId(session.selectedDocumentId!!)
        if (session.documentAnswerLength != null) saveAnswerLengthPreference(session.documentAnswerLength!!)
    }

    private fun startNewChat() {
        chatMessages.clear()
        lastAssistantResponse = ""
        activeAssistantMessageIndex = -1
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

    override fun onDestroy() {
        closeModelResources()
        scope.cancel()
        super.onDestroy()
    }

    private fun currentStatus(): String {
        val session = chatSessionStore.getActiveOrCreateSession()
        val base = if (engine != null) "جاهز • ${selectedModel.displayName}" else if (modelManager.isModelReady(selectedModel)) "غير مشغّل • ${selectedModel.displayName}" else "غير مستورد • ${selectedModel.displayName}"
        return "المحادثة: ${session.title}\n$base"
    }

    private fun updateButtons() {
        val hasInput = inputView.text?.toString()?.trim()?.isNotEmpty() == true
        val busy = isGenerating || isProcessingFile
        val loading = isLoadingModel
        optionsButton.isEnabled = !busy && !loading
        attachButton.isEnabled = !busy && !loading
        sendButton.isEnabled = !busy
        sendButton.alpha = if (hasInput && !isGenerating) 1.0f else 0.45f
        inputView.isEnabled = !busy
        sendButton.text = if (isGenerating) "…" else "↑"
        attachButton.text = "+"
        attachButton.alpha = if (attachButton.isEnabled) 1.0f else 0.5f
        typingIndicatorView.visibility = if (isGenerating) View.VISIBLE else View.GONE
        sendProgressBar.visibility = if (busy || loading) View.VISIBLE else View.GONE
    }

    private fun setStatusInfo(m: String) { statusView.text = m; statusView.setTextColor(ContextCompat.getColor(this, R.color.nabd_text_secondary)) }
    private fun setStatusSuccess(m: String) { statusView.text = m; statusView.setTextColor(ContextCompat.getColor(this, R.color.nabd_success)) }
    private fun setStatusError(m: String) { statusView.text = m; statusView.setTextColor(ContextCompat.getColor(this, R.color.nabd_error)) }
    private fun getSelectedDocument() = documentStore.getDocument(documentStore.getSelectedDocumentId())
    private fun getDisplayName(uri: Uri): String? = runCatching { contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(it.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)) else null } }.getOrNull()
    private fun saveExtractedDocument(t: String, type: String, text: String) {
        if (text.isBlank()) return
        documentStore.saveDocument(LocalDocument(id = UUID.randomUUID().toString(), title = t, type = type, extractedText = text, createdAt = System.currentTimeMillis()))
    }

    private fun chunkText(text: String, max: Int = 1200): List<String> {
        val normalized = text.trim(); if (normalized.isEmpty()) return emptyList()
        val chunks = mutableListOf<String>(); val current = StringBuilder()
        normalized.split(Regex("\\n\\s*\\n")).filter { it.isNotEmpty() }.forEach { p ->
            if (p.length > max) {
                if (current.isNotEmpty()) chunks.add(current.toString().trim())
                current.clear()
                var s = 0
                while(s < p.length) {
                    val e = (s+max).coerceAtMost(p.length)
                    chunks.add(p.substring(s,e).trim())
                    s=e
                }
            } else {
                if (current.length + p.length + 2 > max && current.isNotEmpty()) {
                    chunks.add(current.toString().trim())
                    current.clear()
                }
                if (current.isNotEmpty()) current.append("\n\n")
                current.append(p)
            }
        }
        if (current.isNotEmpty()) chunks.add(current.toString().trim())
        return chunks
    }

    data class RetrievedChunk(val documentId: String, val documentTitle: String, val chunkIndex: Int, val text: String, val score: Int)

    private fun retrieveDocumentChunks(q: String): List<RetrievedChunk> {
        val doc = getSelectedDocument() ?: return emptyList()
        val words = q.lowercase().split(Regex("\\s+")).filter { it.length >= 2 }.toSet()
        val scored = chunkText(doc.extractedText).mapIndexed { i, c ->
            RetrievedChunk(doc.id, doc.title, i, c, words.sumOf { if (c.lowercase().contains(it)) 1 else 0 })
        }.sortedByDescending { it.score }
        return if (words.isEmpty()) scored.take(3) else scored.filter { it.score > 0 }.take(3).ifEmpty { scored.take(2) }
    }

    private fun buildDocumentContext(q: String): String? {
        val chunks = retrieveDocumentChunks(q)
        if (chunks.isEmpty()) return null
        val sb = StringBuilder()
        chunks.forEachIndexed { i, c ->
            if (sb.length + c.text.length + 50 < 5000) {
                if (sb.isNotEmpty()) sb.append("\n\n")
                sb.append("[مقتطف ${i+1} من ${c.documentTitle}]\n${c.text}")
            }
        }
        return sb.toString().takeIf { it.isNotBlank() }
    }

    private fun sendPrompt() {
        if (isGenerating || isProcessingFile) return
        val input = inputView.text.toString().trim()
        if (input.isEmpty()) return

        val tools = PhoneToolRouter.detectToolIntents(input)
        if (tools.isNotEmpty()) {
            showPhoneToolConfirmation(input, tools)
            return
        }

        val docReqs = DocumentToolRouter.detectDocumentToolRequests(input)
        if (docReqs.isNotEmpty()) {
            showDocumentToolConfirmation(input, docReqs)
            return
        }

        if (engine == null || conversation == null) {
            setStatusError("يرجى تشغيل نبض أولًا")
            return
        }

        val ctx = buildDocumentContext(input)
        val prompt = if (ctx != null) "أنت نبض، أجب بناءً على السياق.\n\n$ctx\n\nالسؤال: $input" else "أنت نبض، أجب بالعربية.\n\nالسؤال: $input"
        
        inputView.setText("")
        addChatMessage(ChatMessage(Role.USER, input))
        val assistant = ChatMessage(Role.ASSISTANT, "")
        addChatMessage(assistant)
        saveChatHistory(autoTitle = chatMessages.count { it.role == Role.USER } == 1, firstUserMessage = input)
        startAssistantGeneration(prompt, assistant, "جاري التوليد...")
    }

    private fun addChatMessage(m: ChatMessage) {
        chatMessages.add(m)
        if (m.role == Role.ASSISTANT) activeAssistantMessageIndex = chatMessages.lastIndex
        renderChatHistory()
    }

    private fun updateAssistantMessage(t: String) {
        if (activeAssistantMessageIndex in chatMessages.indices) {
            chatMessages[activeAssistantMessageIndex].text = t
            renderChatHistory()
        }
    }

    private fun renderChatHistory() {
        chatContainer.removeAllViews()
        if (chatMessages.isEmpty()) {
            chatContainer.addView(TextView(this).apply {
                text = "ابدأ المحادثة الآن!"
                textAlignment = View.TEXT_ALIGNMENT_CENTER
                setPadding(0, dpToPx(100), 0, 0)
                setTextColor(ContextCompat.getColor(context, R.color.nabd_text_secondary))
            })
        } else {
            chatMessages.forEach { msg ->
                chatContainer.addView(when(msg.role) {
                    Role.USER -> createUserMessageView(msg.text)
                    Role.ASSISTANT -> createAssistantMessageView(msg.text)
                    Role.SYSTEM -> createSystemMessageView(msg.text)
                })
            }
        }
        chatScrollView.post { chatScrollView.fullScroll(View.FOCUS_DOWN) }
    }

    private fun createUserMessageView(t: String) = TextView(this).apply {
        text = t
        setTextColor(Color.WHITE)
        setPadding(32, 16, 32, 16)
        background = ContextCompat.getDrawable(context, R.drawable.bg_bubble_user)
        setTextIsSelectable(true)
    }

    private fun createAssistantMessageView(m: String) = TextView(this).apply {
        text = "نبض:\n$m"
        setTextColor(ContextCompat.getColor(context, R.color.nabd_text))
        setTextIsSelectable(true)
    }

    private fun createSystemMessageView(t: String) = TextView(this).apply {
        text = t
        setTextColor(Color.GRAY)
        gravity = Gravity.CENTER
    }

    private fun startAssistantGeneration(p: String, assistant: ChatMessage, status: String) {
        isGenerating = true
        updateButtons()
        setStatusInfo(status)
        scope.launch {
            try {
                val out = StringBuilder()
                withContext(Dispatchers.IO) {
                    conversation?.sendMessageAsync(p)?.collect { chunk ->
                        out.append(chunk.toString())
                        withContext(Dispatchers.Main) {
                            assistant.text = out.toString()
                            updateAssistantMessage(assistant.text)
                        }
                    }
                }
                val final = out.toString().ifBlank { "(فارغ)" }
                assistant.text = final
                lastAssistantResponse = final
                updateAssistantMessage(final)
                saveChatHistory()
                setStatusSuccess("جاهز")
            } catch (e: Exception) {
                setStatusError("فشل")
            } finally {
                isGenerating = false
                updateButtons()
            }
        }
    }

    private fun saveChatHistory(autoTitle: Boolean = false, firstUserMessage: String? = null) {
        val json = JSONArray().apply {
            chatMessages.forEach { msg ->
                put(JSONObject().put("role", msg.role.name).put("text", msg.text).put("timestamp", msg.timestamp))
            }
        }
        chatSessionStore.updateActiveSession(json.toString(), lastAssistantResponse, documentStore.getSelectedDocumentId(), getAnswerLengthPreference(), autoTitle, firstUserMessage)
    }

    private fun loadModel() {
        if (!modelManager.isModelReady(selectedModel)) return
        isLoadingModel = true
        updateButtons()
        setStatusInfo("Loading...")
        scope.launch {
            try {
                val pair = withContext(Dispatchers.IO) {
                    val config = EngineConfig(
                        modelPath = modelManager.modelPath(selectedModel),
                        cacheDir = cacheDir.absolutePath,
                        backend = Backend.CPU()
                    )
                    val e = Engine(config)
                    e.initialize()
                    e to e.createConversation()
                }
                engine = pair.first
                conversation = pair.second
                loadedModelId = selectedModel.id
                setStatusSuccess("تم التشغيل")
            } catch (e: Exception) {
                setStatusError("فشل")
            } finally {
                isLoadingModel = false
                updateButtons()
            }
        }
    }

    private fun unloadModel() {
        scope.launch {
            isLoadingModel = true
            updateButtons()
            withContext(Dispatchers.IO) { closeModelResources() }
            setStatusInfo(currentStatus())
            isLoadingModel = false
            updateButtons()
        }
    }

    private fun closeModelResources() {
        runCatching { conversation?.close() }
        conversation = null
        runCatching { engine?.close() }
        engine = null
        loadedModelId = null
    }

    private fun copyToClipboard(l: String, t: String) {
        (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText(l, t))
    }

    private fun showOptionsBottomSheet() {
        val sheet = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_options, null)
        val d = BottomSheetDialog(this)
        d.setContentView(sheet)
        val sectionModel = sheet.findViewById<LinearLayout>(R.id.sectionModel)
        val sectionTools = sheet.findViewById<LinearLayout>(R.id.sectionTools)
        val sectionConv = sheet.findViewById<LinearLayout>(R.id.sectionConversation)

        addOptionRow(sectionModel, "▶", if(engine!=null) "إيقاف نبض" else "تشغيل نبض") { d.dismiss(); if(engine!=null) unloadModel() else loadModel() }
        addOptionRow(sectionTools, "◈", "مركز الأدوات") { d.dismiss(); showToolsCenter() }
        addOptionRow(sectionTools, "＋", "إضافة ملف") { d.dismiss(); showAttachmentTypeDialog() }
        addOptionRow(sectionConv, "×", "مسح المحادثة") { d.dismiss(); confirmClearChat() }
        d.show()
    }

    private fun showToolsCenter() {
        val items = arrayOf("البطارية", "الجهاز", "المكتبة", "السجل")
        MaterialAlertDialogBuilder(this).setTitle("مركز الأدوات").setItems(items) { _, w ->
            when(w) {
                0 -> appendToolResultToChat(phoneToolManager.getBatteryStatus())
                1 -> appendToolResultToChat(phoneToolManager.getDeviceInfo())
                2 -> showDocumentLibraryDialog()
                3 -> showChatHistoryDialog()
            }
        }.show()
    }

    private fun addOptionRow(c: LinearLayout, i: String, t: String, onClick: () -> Unit) {
        val r = LayoutInflater.from(this).inflate(R.layout.item_option_row, c, false)
        r.findViewById<TextView>(R.id.tvOptionIcon).text = i
        r.findViewById<TextView>(R.id.tvOptionTitle).text = t
        r.setOnClickListener { onClick() }
        c.addView(r)
    }

    private fun showPhoneToolConfirmation(input: String, intents: List<PhoneToolIntent>) {
        inputView.setText("")
        addChatMessage(ChatMessage(Role.USER, input))
        val suggestion = "أستطيع تنفيذ أدوات الهاتف:\n" + intents.joinToString("\n") { "- " + getPhoneToolLabel(it) }
        addChatMessage(ChatMessage(Role.ASSISTANT, suggestion))
        MaterialAlertDialogBuilder(this).setTitle("تنفيذ؟").setPositiveButton("تنفيذ") { _, _ -> executePhoneTools(intents) }.setNegativeButton("إلغاء", null).show()
    }

    private fun getPhoneToolLabel(i: PhoneToolIntent) = when(i) {
        PhoneToolIntent.BATTERY -> "البطارية"
        PhoneToolIntent.DEVICE_INFO -> "الجهاز"
        PhoneToolIntent.STORAGE -> "التخزين"
        PhoneToolIntent.INSTALLED_APPS -> "التطبيقات"
    }

    private fun executePhoneTools(intents: List<PhoneToolIntent>) {
        val res = intents.joinToString("\n\n") { i ->
            val r = when(i) {
                PhoneToolIntent.BATTERY -> phoneToolManager.getBatteryStatus()
                PhoneToolIntent.DEVICE_INFO -> phoneToolManager.getDeviceInfo()
                PhoneToolIntent.STORAGE -> phoneToolManager.getStorageInfo()
                PhoneToolIntent.INSTALLED_APPS -> phoneToolManager.getInstalledAppsSummary()
            }
            "[${r.title}]\n${r.content}"
        }
        addChatMessage(ChatMessage(Role.ASSISTANT, res))
        saveChatHistory()
    }

    private fun showDocumentToolConfirmation(input: String, requests: List<DocumentToolRequest>) {
        inputView.setText("")
        addChatMessage(ChatMessage(Role.USER, input))
        val suggestion = "أستطيع استخدام أدوات المستندات:\n" + requests.joinToString("\n") { "- " + getDocToolLabel(it) }
        addChatMessage(ChatMessage(Role.ASSISTANT, suggestion))
        MaterialAlertDialogBuilder(this).setTitle("تنفيذ؟").setPositiveButton("تنفيذ") { _, _ -> executeDocumentTools(requests) }.setNegativeButton("إلغاء", null).show()
    }

    private fun getDocToolLabel(r: DocumentToolRequest) = when(r.intent) {
        DocumentToolIntent.SEARCH_DOCUMENTS -> "البحث عن: ${r.query}"
        DocumentToolIntent.SHOW_DOCUMENT_LIBRARY -> "المكتبة"
        DocumentToolIntent.CLEAR_SELECTED_DOCUMENT -> "إلغاء الاختيار"
        DocumentToolIntent.CURRENT_DOCUMENT_SUMMARY -> "التلخيص"
    }

    private fun executeDocumentTools(requests: List<DocumentToolRequest>) {
        requests.forEach { r ->
            when(r.intent) {
                DocumentToolIntent.SEARCH_DOCUMENTS -> addChatMessage(ChatMessage(Role.ASSISTANT, performLocalDocumentSearch(r.query ?: "")))
                DocumentToolIntent.SHOW_DOCUMENT_LIBRARY -> showDocumentLibraryDialog()
                DocumentToolIntent.CLEAR_SELECTED_DOCUMENT -> { documentStore.clearSelectedDocumentId(); addChatMessage(ChatMessage(Role.ASSISTANT, "تم إلغاء اختيار المستند")) }
                DocumentToolIntent.CURRENT_DOCUMENT_SUMMARY -> addChatMessage(ChatMessage(Role.ASSISTANT, performCurrentDocumentSummary()))
            }
        }
        saveChatHistory()
    }

    private fun performLocalDocumentSearch(q: String): String {
        val docs = documentStore.getDocuments().filter { it.title.contains(q, true) || it.extractedText.contains(q, true) }
        return if(docs.isEmpty()) "لا توجد نتائج لـ $q" else "تم العثور على ${docs.size} مستندات."
    }

    private fun performCurrentDocumentSummary() = getSelectedDocument()?.let { "ملخص المستند: ${it.title}" } ?: "لا يوجد مستند محدد"
    private fun appendToolResultToChat(r: PhoneToolResult) { addChatMessage(ChatMessage(Role.ASSISTANT, "[${r.title}]\n${r.content}")); saveChatHistory() }
    private fun clearChat() { chatMessages.clear(); saveChatHistory(); renderChatHistory() }
    private fun showAttachmentTypeDialog() { MaterialAlertDialogBuilder(this).setTitle("ملف").setItems(arrayOf("صورة", "PDF")) { _, w -> if(w==0) openImagePicker() else openPdfPicker() }.show() }
    private fun openImagePicker() { startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "image/*" }, pickImageRequestCode) }
    private fun openPdfPicker() { startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "application/pdf" }, pickPdfRequestCode) }
    private fun showDocumentLibraryDialog() { val docs = documentStore.getDocuments(); MaterialAlertDialogBuilder(this).setTitle("المكتبة").setItems(docs.map { it.title }.toTypedArray()) { _, w -> documentStore.setSelectedDocumentId(docs[w].id); setStatusInfo(currentStatus()) }.show() }
    private fun showChatHistoryDialog() { val sessions = chatSessionStore.getAllSessions(); MaterialAlertDialogBuilder(this).setTitle("السجل").setItems(sessions.map { it.title }.toTypedArray()) { _, w -> switchSession(sessions[w].id) }.show() }
    private fun selectModel(p: Int) { selectedModel = supportedModels[p]; if(loadedModelId!=null) unloadModel() else setStatusInfo(currentStatus()) }
    private fun getAnswerLengthPreference() = preferences.getString("document_answer_length", "short") ?: "short"
    private fun saveAnswerLengthPreference(v: String) = preferences.edit().putString("document_answer_length", v).apply()
    private fun dpToPx(v: Int) = (v * resources.displayMetrics.density).toInt()

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if(resultCode == RESULT_OK) data?.data?.let { uri -> when(requestCode){ pickModelRequestCode -> importModelFromUri(uri); pickImageRequestCode -> processImageUri(uri); pickPdfRequestCode -> processPdfUri(uri) } }
    }

    private fun importModelFromUri(uri: Uri) { scope.launch { withContext(Dispatchers.IO) { contentResolver.openInputStream(uri)?.use { input -> modelManager.modelFile(selectedModel).outputStream().use { input.copyTo(it) } } } } }
    private fun processImageUri(uri: Uri) { addChatMessage(ChatMessage(Role.USER, "صورة")) }
    private fun processPdfUri(uri: Uri) { addChatMessage(ChatMessage(Role.USER, "PDF")) }
    private fun confirmClearChat() { MaterialAlertDialogBuilder(this).setTitle("مسح؟").setPositiveButton("مسح") { _, _ -> clearChat() }.show() }

    companion object {
        private const val KEY_CHAT_HISTORY_TEXT = "chat_history_text"
        private const val KEY_CHAT_MESSAGES_JSON = "chat_messages_json"
        private const val KEY_SELECTED_MODEL_ID = "selected_model_id"
    }
}
