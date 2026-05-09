package com.example.localqwen.viewmodel

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.localqwen.chat.ChatMessage
import com.example.localqwen.chat.ChatSession
import com.example.localqwen.chat.ChatSessionStore
import com.example.localqwen.chat.Role
import com.example.localqwen.data.NabdDatabase
import com.example.localqwen.document.DocumentStore
import com.example.localqwen.document.LocalDocument
import com.example.localqwen.engine.NabdInferenceEngine
import com.example.localqwen.prompt.NabdSystemPrompt
import com.example.localqwen.rag.EmbeddingEngine
import com.example.localqwen.rag.EmbeddingStore
import com.example.localqwen.rag.RagMode
import com.example.localqwen.rag.RetrievedChunk
import com.example.localqwen.rag.SemanticRetriever
import com.example.localqwen.rag.TextChunker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val preferences = application.getSharedPreferences("nabd_prefs", 0)
    private val db = NabdDatabase.getInstance(application)
    val chatSessionStore = ChatSessionStore(preferences, db)
    val documentStore = DocumentStore(preferences, db)

    private val _messages = MutableLiveData<List<ChatMessage>>(emptyList())
    val messages: LiveData<List<ChatMessage>> = _messages

    private val _isGenerating = MutableLiveData(false)
    val isGenerating: LiveData<Boolean> = _isGenerating

    private val _isPreparingContext = MutableLiveData(false)
    val isPreparingContext: LiveData<Boolean> = _isPreparingContext

    private val _statusEvent = MutableLiveData<StatusEvent>()
    val statusEvent: LiveData<StatusEvent> = _statusEvent

    private val _streamingUpdate = MutableLiveData<StreamingUpdate?>()
    val streamingUpdate: LiveData<StreamingUpdate?> = _streamingUpdate

    private val _scrollToBottom = MutableLiveData<Boolean>()
    val scrollToBottom: LiveData<Boolean> = _scrollToBottom

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
            _messages.value = chatMessages.toList()
            _scrollToBottom.value = true
        }
    }

    fun startNewChat() {
        viewModelScope.launch {
            chatMessages.clear()
            lastAssistantResponse = ""
            activeAssistantMessageIndex = -1
            documentStore.clearSelectedDocumentId()
            chatSessionStore.createNewSession()
            _messages.value = emptyList()
            _statusEvent.value = StatusEvent.Info("تم إنشاء محادثة جديدة")
        }
    }

    fun switchSession(id: String) {
        viewModelScope.launch {
            chatSessionStore.setActiveSessionId(id)
            loadActiveSession()
        }
    }

    fun sendMessage(
        input: String,
        engine: NabdInferenceEngine?,
        embeddingEngine: EmbeddingEngine?,
        embeddingStore: EmbeddingStore?,
        semanticRetriever: SemanticRetriever?,
        ragMode: RagMode,
        documentAnswerLengthInstruction: String
    ) {
        if (input.isBlank()) return
        if (_isGenerating.value == true) return

        val userMessage = ChatMessage(role = Role.USER, text = input.trim())
        addChatMessage(userMessage)

        val assistantMessage = ChatMessage(role = Role.ASSISTANT, text = "")
        addChatMessage(assistantMessage)

        _isPreparingContext.value = true

        val selectedDocument = getSelectedDocument()
        _statusEvent.value = if (selectedDocument != null) {
            StatusEvent.Info("جاري تجهيز سياق المستند...")
        } else {
            StatusEvent.Info("جاري التوليد...")
        }

        generationJob = viewModelScope.launch {
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

            val prompt = if (contextResult.context != null) {
                NabdSystemPrompt.documentPrompt(
                    userInput = input,
                    contextChunks = contextResult.context,
                    answerLengthInstruction = documentAnswerLengthInstruction
                )
            } else {
                NabdSystemPrompt.normalChatPrompt(input)
            }

            _isPreparingContext.value = false

            startGeneration(
                engine = engine,
                prompt = prompt,
                status = contextResult.generationStatus,
                autoTitle = chatMessages.count { it.role == Role.USER } == 1,
                firstUserMessage = input
            )
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
            _statusEvent.value = StatusEvent.Error("تعذر تشغيل النموذج. جرّب نموذجًا أخف أو أعد استيراده.")
            return
        }

        _isGenerating.value = true
        val generationStartedAt = SystemClock.elapsedRealtime()
        lastFirstTokenLatencyMs = null
        lastGenerationDurationMs = null
        _statusEvent.value = StatusEvent.Info(status)
        _streamingUpdate.value = StreamingUpdate(index = activeAssistantMessageIndex, isStreaming = true)

        try {
            val output = StringBuilder()
            var lastRenderedLength = 0
            var firstChunkCaptured = false

            withContext(Dispatchers.IO) {
                engine.generate(prompt).collect { chunk ->
                    if (!firstChunkCaptured) {
                        firstChunkCaptured = true
                        lastFirstTokenLatencyMs = SystemClock.elapsedRealtime() - generationStartedAt
                    }
                    output.append(chunk)
                    val shouldRefresh = output.length <= 256 || output.length - lastRenderedLength >= STREAM_UPDATE_MIN_CHARS
                    if (shouldRefresh) {
                        val snapshot = output.toString()
                        lastRenderedLength = output.length
                        withContext(Dispatchers.Main) {
                            val cleaned = cleanForDisplay(snapshot, preserveMarkdown = true)
                            updateAssistantMessageInternal(cleaned, renderMarkdown = false)
                        }
                    }
                }
            }

            val finalText = cleanForDisplay(output.toString(), preserveMarkdown = true).ifBlank { "(فارغ)" }
            updateAssistantMessageInternal(finalText, renderMarkdown = true)
            lastAssistantResponse = finalText
            lastResponseCharCount = finalText.length
            lastGenerationDurationMs = SystemClock.elapsedRealtime() - generationStartedAt

            saveActiveSessionDebounced(autoTitle = autoTitle, firstUserMessage = firstUserMessage, immediate = true)
            _statusEvent.value = StatusEvent.Success("جاهز")
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            lastGenerationDurationMs = SystemClock.elapsedRealtime() - generationStartedAt
            saveActiveSessionDebounced(autoTitle = autoTitle, firstUserMessage = firstUserMessage, immediate = true)
            _statusEvent.value = StatusEvent.Error("حدث خطأ أثناء توليد الرد.")
        } finally {
            _isGenerating.value = false
            _streamingUpdate.value = null
        }
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
        _messages.value = chatMessages.toList()
        _scrollToBottom.value = true
    }

    private fun updateAssistantMessageInternal(text: String, renderMarkdown: Boolean) {
        if (activeAssistantMessageIndex !in chatMessages.indices) return
        chatMessages[activeAssistantMessageIndex] = chatMessages[activeAssistantMessageIndex].copy(text = text)
        _messages.value = chatMessages.toList()
        _streamingUpdate.value = StreamingUpdate(
            index = activeAssistantMessageIndex,
            isStreaming = !renderMarkdown
        )
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

    fun getSelectedDocument(): LocalDocument? {
        val id = documentStore.getSelectedDocumentId() ?: return null
        // We need a synchronous check here for the document — use cached or return null
        // The actual document fetch should be done with suspend in buildDocumentContext
        return null // Will be resolved in the generation coroutine
    }

    suspend fun getSelectedDocumentAsync(): LocalDocument? {
        return documentStore.getDocument(documentStore.getSelectedDocumentId())
    }

    fun currentDocumentAnswerLength(): String {
        return preferences.getString("document_answer_length", "short") ?: "short"
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
    }

    companion object {
        private const val STREAM_UPDATE_MIN_CHARS = 24
        private const val DOCUMENT_TEXT_LIMIT = 100_000
        private const val DOCUMENT_CHUNK_SIZE = 1200
        private const val MAX_RETRIEVED_CHUNKS = 3
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
