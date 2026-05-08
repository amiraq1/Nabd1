package com.example.localqwen.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "local_documents")
public class LocalDocumentEntity {
    @PrimaryKey
    @NonNull
    public String id;
    public String title;
    public String type;
    public String extractedText;
    public long createdAt;

    public LocalDocumentEntity(@NonNull String id, String title, String type, String extractedText, long createdAt) {
        this.id = id;
        this.title = title;
        this.type = type;
        this.extractedText = extractedText;
        this.createdAt = createdAt;
    }
}
