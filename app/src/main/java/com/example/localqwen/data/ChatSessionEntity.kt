package com.example.localqwen.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_sessions")
data class ChatSessionEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val messagesText: String,
    val lastAssistantResponse: String?,
    val selectedDocumentId: String?,
    val documentAnswerLength: String?
)
