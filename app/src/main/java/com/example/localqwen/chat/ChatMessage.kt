package com.example.localqwen.chat

import java.util.UUID

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: Role,
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

enum class Role {
    USER,
    ASSISTANT,
    SYSTEM
}
