package com.example.localqwen.engine

/**
 * Configuration for the Inference Engine.
 * 
 * @property modelPath Path to the model file (.litertlm or .task).
 * @property cacheDir Directory for caching inference data.
 * @property backend Preferred backend (e.g., "cpu", "gpu", "npu").
 * @property maxContextTokens Maximum number of tokens for context.
 * @property threads Number of CPU threads to use.
 */
data class EngineConfiguration(
    val modelPath: String = "",
    val cacheDir: String = "",
    val backend: String = "cpu",
    val maxContextTokens: Int = 2048,
    val threads: Int = 4
)
