package com.example.localqwen.document

enum class DocumentToolIntent {
    SEARCH_DOCUMENTS,
    SHOW_DOCUMENT_LIBRARY,
    CLEAR_SELECTED_DOCUMENT,
    CURRENT_DOCUMENT_SUMMARY
}

data class DocumentToolRequest(
    val intent: DocumentToolIntent,
    val query: String? = null
)

object DocumentToolRouter {

    private val searchKeywords = listOf("ابحث في مستنداتي عن", "دور في المستندات عن", "فتش في المستندات عن", "البحث في المستندات", "search my documents", "search documents")
    private val libraryKeywords = listOf("افتح مكتبة المستندات", "اعرض المستندات", "مستنداتي", "document library", "show documents")
    private val clearKeywords = listOf("الغي المستند", "إلغاء اختيار المستند", "لا تستخدم المستند", "clear selected document")
    private val summaryKeywords = listOf("لخص المستند الحالي", "ملخص المستند", "اهم نقاط المستند الحالي", "ما هو المستند الحالي", "summarize current document")

    fun detectDocumentToolRequests(input: String): List<DocumentToolRequest> {
        val requests = mutableListOf<DocumentToolRequest>()
        val inputLow = input.lowercase()

        // Detect Search (Needs query extraction)
        searchKeywords.forEach { kw ->
            if (inputLow.contains(kw.lowercase())) {
                val query = extractSearchQuery(input, kw)
                if (!query.isNullOrBlank()) {
                    requests.add(DocumentToolRequest(DocumentToolIntent.SEARCH_DOCUMENTS, query))
                }
            }
        }

        if (libraryKeywords.any { inputLow.contains(it.lowercase()) }) requests.add(DocumentToolRequest(DocumentToolIntent.SHOW_DOCUMENT_LIBRARY))
        if (clearKeywords.any { inputLow.contains(it.lowercase()) }) requests.add(DocumentToolRequest(DocumentToolIntent.CLEAR_SELECTED_DOCUMENT))
        if (summaryKeywords.any { inputLow.contains(it.lowercase()) }) requests.add(DocumentToolRequest(DocumentToolIntent.CURRENT_DOCUMENT_SUMMARY))

        return requests.distinctBy { it.intent }
    }

    private fun extractSearchQuery(input: String, keyword: String): String? {
        val index = input.lowercase().indexOf(keyword.lowercase())
        if (index == -1) return null
        
        var query = input.substring(index + keyword.length).trim()
        
        // Remove common Arabic connector if it exists right after keyword
        if (query.startsWith("عن ")) {
            query = query.substring(3).trim()
        } else if (query.startsWith("for ")) {
            query = query.substring(4).trim()
        }
        
        return query.takeIf { it.isNotBlank() }
    }
}
