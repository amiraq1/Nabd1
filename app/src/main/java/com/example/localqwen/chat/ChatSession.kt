package com.example.localqwen.chat

import java.util.UUID

data class ChatSession(
    val id: String = UUID.randomUUID().toString(),
    var title: String = "محادثة جديدة",
    val createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis(),
    var messagesJson: String = "[]",
    var lastAssistantResponse: String = "",
    var selectedDocumentId: String? = null,
    var documentAnswerLength: String? = "short"
)
