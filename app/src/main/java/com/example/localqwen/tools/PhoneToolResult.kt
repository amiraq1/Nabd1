package com.example.localqwen.tools

data class PhoneToolResult(
    val title: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)
