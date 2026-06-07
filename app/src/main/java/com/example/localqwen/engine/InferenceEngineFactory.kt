package com.example.localqwen.engine

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Factory for creating or obtaining the Inference Engine.
 */
@Singleton
class InferenceEngineFactory @Inject constructor(
    private val liteRtEngine: LiteRtLmInferenceEngine
) {
    /**
     * Returns the primary inference engine implementation.
     */
    fun getEngine(): NabdInferenceEngine {
        return liteRtEngine
    }
}
