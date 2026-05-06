package com.example.localqwen.document

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

class DocumentStore(
    private val preferences: SharedPreferences
) {
    fun getDocuments(): List<LocalDocument> {
        val raw = preferences.getString(KEY_LOCAL_DOCUMENTS_JSON, null).orEmpty()
        if (raw.isBlank()) return emptyList()

        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    add(
                        LocalDocument(
                            id = item.optString("id"),
                            title = item.optString("title"),
                            type = item.optString("type"),
                            extractedText = item.optString("extractedText"),
                            createdAt = item.optLong("createdAt", System.currentTimeMillis())
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun saveDocument(document: LocalDocument) {
        val normalizedText = document.extractedText
            .replace(Regex("[\\t\\x0B\\f\\r ]+"), " ")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
            .truncateDocumentText(MAX_DOCUMENT_TEXT_CHARS)
        if (normalizedText.isBlank()) return

        val updated = getDocuments()
            .filterNot { it.id == document.id }
            .toMutableList()
            .apply {
                add(
                    0,
                    document.copy(extractedText = normalizedText)
                )
            }
        saveDocuments(updated)
    }

    fun deleteDocument(documentId: String) {
        val updated = getDocuments().filterNot { it.id == documentId }
        saveDocuments(updated)
        if (getSelectedDocumentId() == documentId) {
            clearSelectedDocumentId()
        }
    }

    fun getDocument(documentId: String?): LocalDocument? {
        if (documentId.isNullOrBlank()) return null
        return getDocuments().firstOrNull { it.id == documentId }
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

    private fun saveDocuments(documents: List<LocalDocument>) {
        val array = JSONArray().apply {
            documents.forEach { document ->
                put(
                    JSONObject()
                        .put("id", document.id)
                        .put("title", document.title)
                        .put("type", document.type)
                        .put("extractedText", document.extractedText)
                        .put("createdAt", document.createdAt)
                )
            }
        }

        preferences.edit().putString(KEY_LOCAL_DOCUMENTS_JSON, array.toString()).apply()
    }

    companion object {
        const val KEY_LOCAL_DOCUMENTS_JSON = "local_documents_json"
        const val KEY_SELECTED_DOCUMENT_ID = "selected_document_id"
        private const val MAX_DOCUMENT_TEXT_CHARS = 200_000
    }
}

private fun String.truncateDocumentText(maxChars: Int): String {
    return if (length <= maxChars) this else take(maxChars) + "\n(تم اختصار النص لطوله)"
}
