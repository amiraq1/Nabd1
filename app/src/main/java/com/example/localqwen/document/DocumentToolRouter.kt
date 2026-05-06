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
    private val loweredSearchKeywords = searchKeywords.map { it.lowercase() }
    private val loweredLibraryKeywords = libraryKeywords.map { it.lowercase() }
    private val loweredClearKeywords = clearKeywords.map { it.lowercase() }
    private val loweredSummaryKeywords = summaryKeywords.map { it.lowercase() }

    fun detectDocumentToolRequests(input: String): List<DocumentToolRequest> {
        val requests = mutableListOf<DocumentToolRequest>()
        val inputLow = input.lowercase()

        loweredSearchKeywords.forEachIndexed { index, keyword ->
            if (inputLow.contains(keyword)) {
                val query = extractSearchQuery(input, searchKeywords[index], keyword)
                if (!query.isNullOrBlank()) {
                    requests.add(DocumentToolRequest(DocumentToolIntent.SEARCH_DOCUMENTS, query))
                }
            }
        }

        if (loweredLibraryKeywords.any(inputLow::contains)) {
            requests.add(DocumentToolRequest(DocumentToolIntent.SHOW_DOCUMENT_LIBRARY))
        }
        if (loweredClearKeywords.any(inputLow::contains)) {
            requests.add(DocumentToolRequest(DocumentToolIntent.CLEAR_SELECTED_DOCUMENT))
        }
        if (loweredSummaryKeywords.any(inputLow::contains)) {
            requests.add(DocumentToolRequest(DocumentToolIntent.CURRENT_DOCUMENT_SUMMARY))
        }

        return requests.distinctBy { it.intent }
    }

    private fun extractSearchQuery(input: String, keyword: String, loweredKeyword: String): String? {
        val index = input.lowercase().indexOf(loweredKeyword)
        if (index == -1) return null

        var query = input.substring(index + keyword.length).trim()
        if (query.startsWith("عن ")) {
            query = query.substring(3).trim()
        } else if (query.startsWith("for ")) {
            query = query.substring(4).trim()
        }

        return query.takeIf { it.isNotBlank() }
    }
}
