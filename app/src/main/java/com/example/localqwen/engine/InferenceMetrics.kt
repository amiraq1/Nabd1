package com.example.localqwen.engine

data class InferenceMetrics(
    val firstTokenLatencyMs: Long?,
    val totalDurationMs: Long?,
    val tokensPerSecond: Float?,
    val charCount: Int?
)