package com.example.localqwen.chat

import androidx.compose.runtime.Immutable
import com.example.localqwen.verification.SourceRequirement
import com.example.localqwen.verification.VerificationLevel
import java.util.UUID

@Immutable
data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: Role,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val verificationLevel: VerificationLevel? = null,
    val sourceRequirement: SourceRequirement? = null
)

enum class Role {
    USER,
    ASSISTANT,
    SYSTEM
}
