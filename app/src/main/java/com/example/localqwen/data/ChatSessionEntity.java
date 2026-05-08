package com.example.localqwen.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "chat_sessions")
public class ChatSessionEntity {
    @PrimaryKey
    @NonNull
    public String id;
    public String title;
    public long createdAt;
    public long updatedAt;
    public String messagesText;
    public String lastAssistantResponse;
    @Nullable
    public String selectedDocumentId;
    @Nullable
    public String documentAnswerLength;

    public ChatSessionEntity(@NonNull String id, String title, long createdAt, long updatedAt, String messagesText, String lastAssistantResponse, @Nullable String selectedDocumentId, @Nullable String documentAnswerLength) {
        this.id = id;
        this.title = title;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.messagesText = messagesText;
        this.lastAssistantResponse = lastAssistantResponse;
        this.selectedDocumentId = selectedDocumentId;
        this.documentAnswerLength = documentAnswerLength;
    }
}
