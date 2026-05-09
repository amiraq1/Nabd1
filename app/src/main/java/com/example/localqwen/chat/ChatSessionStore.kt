package com.example.localqwen.chat

import android.content.SharedPreferences
import com.example.localqwen.data.ChatSessionEntity
import com.example.localqwen.data.NabdDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ChatSessionStore(
    private val prefs: SharedPreferences,
    private val db: NabdDatabase
) {

    companion object {
        private const val KEY_ACTIVE_SESSION_ID = "active_chat_session_id"
    }

    suspend fun getAllSessions(): List<ChatSession> = withContext(Dispatchers.IO) {
        db.chatSessionDao().getAllSessions().map { fromEntity(it) }
    }

    suspend fun getSession(id: String): ChatSession? = withContext(Dispatchers.IO) {
        db.chatSessionDao().getSession(id)?.let { fromEntity(it) }
    }

    suspend fun saveSession(session: ChatSession) = withContext(Dispatchers.IO) {
        db.chatSessionDao().insertOrUpdate(toEntity(session))
    }

    suspend fun deleteSession(id: String) = withContext(Dispatchers.IO) {
        db.chatSessionDao().deleteById(id)
        if (getActiveSessionId() == id) {
            val sessions = db.chatSessionDao().getAllSessions()
            setActiveSessionId(sessions.firstOrNull()?.id)
        }
    }

    suspend fun createNewSession(): ChatSession {
        val session = ChatSession()
        saveSession(session)
        setActiveSessionId(session.id)
        return session
    }

    fun setActiveSessionId(id: String?) {
        prefs.edit().putString(KEY_ACTIVE_SESSION_ID, id).apply()
    }

    fun getActiveSessionId(): String? {
        return prefs.getString(KEY_ACTIVE_SESSION_ID, null)
    }

    suspend fun getActiveOrCreateSession(): ChatSession {
        val id = getActiveSessionId()
        if (id != null) {
            val session = getSession(id)
            if (session != null) return session
        }
        return createNewSession()
    }

    suspend fun updateActiveSession(
        messagesJson: String,
        lastAssistantResponse: String,
        selectedDocumentId: String?,
        documentAnswerLength: String?,
        autoTitle: Boolean = false,
        firstUserMessage: String? = null
    ) {
        val session = getActiveOrCreateSession()
        session.messagesJson = messagesJson
        session.lastAssistantResponse = lastAssistantResponse
        session.selectedDocumentId = selectedDocumentId
        session.documentAnswerLength = documentAnswerLength
        session.updatedAt = System.currentTimeMillis()

        if (autoTitle && firstUserMessage != null && (session.title == "محادثة جديدة" || session.title.isBlank())) {
            session.title = firstUserMessage.take(35).replace("\n", " ").trim()
            if (session.title.isEmpty()) session.title = "محادثة جديدة"
        }

        saveSession(session)
    }

    suspend fun renameSession(id: String, newTitle: String) {
        val session = getSession(id) ?: return
        session.title = newTitle
        session.updatedAt = System.currentTimeMillis()
        saveSession(session)
    }

    private fun toEntity(session: ChatSession) = ChatSessionEntity(
        id = session.id,
        title = session.title,
        createdAt = session.createdAt,
        updatedAt = session.updatedAt,
        messagesText = session.messagesJson,
        lastAssistantResponse = session.lastAssistantResponse,
        selectedDocumentId = session.selectedDocumentId,
        documentAnswerLength = session.documentAnswerLength
    )

    private fun fromEntity(entity: ChatSessionEntity) = ChatSession(
        id = entity.id,
        title = entity.title,
        createdAt = entity.createdAt,
        updatedAt = entity.updatedAt,
        messagesJson = entity.messagesText,
        lastAssistantResponse = entity.lastAssistantResponse ?: "",
        selectedDocumentId = entity.selectedDocumentId,
        documentAnswerLength = entity.documentAnswerLength
    )
}
