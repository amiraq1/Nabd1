package com.example.localqwen.engine

import android.util.Log
import com.google.ai.edge.litertlm.Backend

/**
 * Provides the correct LiteRT Backend based on configuration strings.
 * DESIGN GOAL: ZERO REFLECTION.
 */
object BackendProvider {
    private const val TAG = "BackendProvider"

    fun getBackend(name: String): Backend {
        return when (name.lowercase()) {
            "gpu" -> Backend.GPU()
            "npu" -> {
                // Some versions might not support NPU yet, fallback to CPU
                try {
                    Backend.NPU()
                } catch (e: Throwable) {
                    Log.w(TAG, "NPU backend not supported, falling back to CPU")
                    Backend.CPU()
                }
            }
            else -> Backend.CPU()
        }
    }
}
