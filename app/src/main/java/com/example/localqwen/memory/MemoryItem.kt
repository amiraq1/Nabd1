package com.example.localqwen.memory

data class MemoryItem(
    val id: String,
    val text: String,
    val createdAt: Long,
    val updatedAt: Long,
    val category: String
)
