package com.example.localqwen.tools

data class SavedPlace(
    val name: String,
    val query: String,
    val createdAt: Long = System.currentTimeMillis()
)
