package com.example.localqwen.document

data class LocalDocument(
    val id: String,
    val title: String,
    val type: String,
    val extractedText: String,
    val createdAt: Long
)
