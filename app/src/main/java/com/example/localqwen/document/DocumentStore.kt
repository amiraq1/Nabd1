package com.example.localqwen.document

import android.content.Context
import android.content.SharedPreferences
import com.example.localqwen.data.LocalDocumentEntity
import com.example.localqwen.data.NabdDatabase
import com.example.localqwen.data.SecurePreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DocumentStore(
    private val preferences: SharedPreferences,
    private val db: NabdDatabase
) {
    constructor(context: Context) : this(
        SecurePreferences.get(context),
        NabdDatabase.getInstance(context)
    )

    suspend fun getDocuments(): List<LocalDocument> = withContext(Dispatchers.IO) {
        db.localDocumentDao().getAllDocuments().map { fromEntity(it) }
    }

    suspend fun getDocument(documentId: String?): LocalDocument? {
        if (documentId.isNullOrBlank()) return null
        return withContext(Dispatchers.IO) {
            db.localDocumentDao().getDocument(documentId)?.let { fromEntity(it) }
        }
    }

    suspend fun saveDocument(document: LocalDocument) {
        val normalizedText = document.extractedText
            .replace(Regex("[\\t\\x0B\\f\\r ]+"), " ")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
            .truncateDocumentText(MAX_DOCUMENT_TEXT_CHARS)

        if (normalizedText.isBlank()) return

        val newDocument = document.copy(extractedText = normalizedText)

        withContext(Dispatchers.IO) {
            db.localDocumentDao().insertOrUpdate(toEntity(newDocument))
        }
    }

    suspend fun deleteDocument(documentId: String) {
        withContext(Dispatchers.IO) {
            db.localDocumentDao().deleteById(documentId)
        }
        if (getSelectedDocumentId() == documentId) {
            clearSelectedDocumentId()
        }
    }

    fun setSelectedDocumentId(documentId: String?) {
        preferences.edit().putString(KEY_SELECTED_DOCUMENT_ID, documentId).apply()
    }

    fun getSelectedDocumentId(): String? {
        return preferences.getString(KEY_SELECTED_DOCUMENT_ID, null)
    }

    fun clearSelectedDocumentId() {
        preferences.edit().remove(KEY_SELECTED_DOCUMENT_ID).apply()
    }

    private fun toEntity(document: LocalDocument) = LocalDocumentEntity(
        id = document.id,
        title = document.title,
        type = document.type,
        extractedText = document.extractedText,
        createdAt = document.createdAt
    )

    private fun fromEntity(entity: LocalDocumentEntity) = LocalDocument(
        id = entity.id,
        title = entity.title,
        type = entity.type,
        extractedText = entity.extractedText,
        createdAt = entity.createdAt
    )

    companion object {
        const val PREFERENCES_NAME = "nabd_prefs"
        const val KEY_SELECTED_DOCUMENT_ID = "selected_document_id"
        private const val MAX_DOCUMENT_TEXT_CHARS = 200_000
    }
}

private fun String.truncateDocumentText(maxChars: Int): String {
    return if (length <= maxChars) this else take(maxChars) + "\n(تم اختصار النص لطوله)"
}
