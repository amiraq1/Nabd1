package com.example.localqwen.chat

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class ChatSessionStore(private val prefs: SharedPreferences) {

    companion object {
        private const val KEY_SESSIONS = "chat_sessions_json"
        private const val KEY_ACTIVE_SESSION_ID = "active_chat_session_id"
    }

    fun getAllSessions(): List<ChatSession> {
        val json = prefs.getString(KEY_SESSIONS, null) ?: return emptyList()
        return try {
            val array = JSONArray(json)
            val sessions = mutableListOf<ChatSession>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                sessions.add(fromJson(obj))
            }
            sessions.sortedByDescending { it.updatedAt }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getSession(id: String): ChatSession? {
        return getAllSessions().find { it.id == id }
    }

    fun saveSession(session: ChatSession) {
        val sessions = getAllSessions().toMutableList()
        val index = sessions.indexOfFirst { it.id == session.id }
        if (index != -1) {
            sessions[index] = session
        } else {
            sessions.add(session)
        }
        persist(sessions)
    }

    fun deleteSession(id: String) {
        val sessions = getAllSessions().toMutableList()
        sessions.removeAll { it.id == id }
        persist(sessions)
        if (getActiveSessionId() == id) {
            setActiveSessionId(sessions.firstOrNull()?.id)
        }
    }

    fun createNewSession(): ChatSession {
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

    fun getActiveOrCreateSession(): ChatSession {
        val id = getActiveSessionId()
        if (id != null) {
            val session = getSession(id)
            if (session != null) return session
        }
        return createNewSession()
    }

    fun updateActiveSession(
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

    fun renameSession(id: String, newTitle: String) {
        val session = getSession(id) ?: return
        session.title = newTitle
        session.updatedAt = System.currentTimeMillis()
        saveSession(session)
    }

    private fun persist(sessions: List<ChatSession>) {
        val array = JSONArray()
        sessions.forEach { array.put(toJson(it)) }
        prefs.edit().putString(KEY_SESSIONS, array.toString()).apply()
    }

    private fun toJson(session: ChatSession): JSONObject {
        return JSONObject().apply {
            put("id", session.id)
            put("title", session.title)
            put("createdAt", session.createdAt)
            put("updatedAt", session.updatedAt)
            put("messagesJson", session.messagesJson)
            put("lastAssistantResponse", session.lastAssistantResponse)
            put("selectedDocumentId", session.selectedDocumentId)
            put("documentAnswerLength", session.documentAnswerLength)
        }
    }

    private fun fromJson(obj: JSONObject): ChatSession {
        return ChatSession(
            id = obj.getString("id"),
            title = obj.getString("title"),
            createdAt = obj.getLong("createdAt"),
            updatedAt = obj.getLong("updatedAt"),
            messagesJson = obj.optString("messagesJson", "[]"),
            lastAssistantResponse = obj.optString("lastAssistantResponse", ""),
            selectedDocumentId = obj.optString("selectedDocumentId", null).takeIf { it != "null" },
            documentAnswerLength = obj.optString("documentAnswerLength", "short")
        )
    }
}
