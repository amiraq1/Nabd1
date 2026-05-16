package com.example.localqwen.document

import android.content.Context
import com.example.localqwen.data.SecurePreferences

object PdfSettings {
    const val KEY_PDF_PAGE_LIMIT = "pdf_page_limit"

    private const val DEFAULT_PDF_PAGE_LIMIT = 10
    private val ALLOWED_PAGE_LIMITS = setOf(3, 10, 25, 50)

    fun getPdfPageLimit(context: Context): Int {
        val limit = SecurePreferences.get(context)
            .getInt(KEY_PDF_PAGE_LIMIT, DEFAULT_PDF_PAGE_LIMIT)
        return if (limit in ALLOWED_PAGE_LIMITS) limit else DEFAULT_PDF_PAGE_LIMIT
    }

    fun setPdfPageLimit(context: Context, limit: Int) {
        val safeLimit = if (limit in ALLOWED_PAGE_LIMITS) limit else DEFAULT_PDF_PAGE_LIMIT
        SecurePreferences.get(context)
            .edit()
            .putInt(KEY_PDF_PAGE_LIMIT, safeLimit)
            .apply()
    }
}
