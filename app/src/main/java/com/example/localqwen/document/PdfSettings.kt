package com.example.localqwen.document

import android.content.Context

object PdfSettings {
    const val KEY_PDF_PAGE_LIMIT = "pdf_page_limit"

    private const val PREFS_NAME = "nabd_prefs"
    private const val DEFAULT_PDF_PAGE_LIMIT = 10
    private val ALLOWED_PAGE_LIMITS = setOf(3, 10, 25, 50)

    fun getPdfPageLimit(context: Context): Int {
        val limit = context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_PDF_PAGE_LIMIT, DEFAULT_PDF_PAGE_LIMIT)
        return if (limit in ALLOWED_PAGE_LIMITS) limit else DEFAULT_PDF_PAGE_LIMIT
    }

    fun setPdfPageLimit(context: Context, limit: Int) {
        val safeLimit = if (limit in ALLOWED_PAGE_LIMITS) limit else DEFAULT_PDF_PAGE_LIMIT
        context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_PDF_PAGE_LIMIT, safeLimit)
            .apply()
    }
}
