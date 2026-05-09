package com.example.localqwen.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "local_documents")
data class LocalDocumentEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val type: String,
    val extractedText: String,
    val createdAt: Long
)
