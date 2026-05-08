package com.example.localqwen.engine

data class InferenceResult(
    val text: String,
    val metrics: InferenceMetrics? = null,
    val error: String? = null
)