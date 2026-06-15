package com.example.localqwen.engine

import kotlinx.coroutines.flow.Flow

interface NabdInferenceEngine : AutoCloseable {
    @Throws(Exception::class)
    suspend fun load(modelPath: String, cacheDir: String)

    @Throws(Exception::class)
    suspend fun load(modelPath: String, cacheDir: String, backendName: String) {
        load(modelPath, cacheDir)
    }
    
    suspend fun unload()
    
    fun isReady(): Boolean
    
    fun generate(prompt: String): Flow<String>
    
    fun generateVision(imagePath: String, prompt: String): Flow<String>

    suspend fun resetConversation() {}

    suspend fun closeSuspend() {}

    override fun close() {
        // Override in implementations to avoid runBlocking on the main thread.
        // Use closeSuspend() for synchronous cleanup from coroutine context.
    }
}