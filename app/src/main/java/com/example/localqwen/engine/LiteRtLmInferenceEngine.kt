package com.example.localqwen.engine

import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class LiteRtLmInferenceEngine : NabdInferenceEngine {
    private var engine: Engine? = null
    private var conversation: Conversation? = null

    override suspend fun load(modelPath: String, cacheDir: String) {
        withContext(Dispatchers.IO) {
            unload()
            val config = EngineConfig(
                modelPath = modelPath,
                cacheDir = cacheDir,
                backend = Backend.CPU()
            )
            engine = Engine(config).apply { initialize() }
            conversation = engine?.createConversation()
        }
    }

    override suspend fun unload() {
        withContext(Dispatchers.IO) {
            conversation?.close()
            conversation = null
            engine?.close()
            engine = null
        }
    }

    override fun isReady(): Boolean {
        return engine != null && conversation != null
    }

    override fun generate(prompt: String): Flow<String> {
        val currentConversation = conversation ?: throw IllegalStateException("Engine not initialized")
        return currentConversation.sendMessageAsync(prompt).map { it.toString() }
    }

    override fun generateVision(imagePath: String, prompt: String): Flow<String> {
        val currentConversation = conversation ?: throw IllegalStateException("Engine not initialized")
        val messageContent = Contents.of(
            Content.ImageFile(imagePath),
            Content.Text(prompt)
        )
        return currentConversation.sendMessageAsync(messageContent).map { it.toString() }
    }
}