package com.example.localqwen.engine

import kotlinx.coroutines.flow.Flow

interface NabdInferenceEngine {
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

    fun resetConversation() {}
}