package com.example.localqwen.chat

data class ChatMessage(
    val role: Role,
    var text: String,
    val timestamp: Long = System.currentTimeMillis()
)

enum class Role {
    USER,
    ASSISTANT,
    SYSTEM
}
