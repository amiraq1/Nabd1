package com.example.localqwen.engine

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pure Type-Safe Implementation of NabdInferenceEngine using LiteRT-LM Public APIs.
 * NO REFLECTION USED.
 */
@Singleton
class LiteRtLmInferenceEngine @Inject constructor(
    @ApplicationContext private val context: Context
) : NabdInferenceEngine {

    private val engineLock = Mutex()
    private val isReleased = AtomicBoolean(false)
    
    private var engine: Engine? = null
    private var conversation: Conversation? = null

    companion object {
        private const val TAG = "LiteRtEngine"
    }

    override suspend fun load(modelPath: String, cacheDir: String) {
        load(modelPath, cacheDir, "cpu")
    }

    override suspend fun load(modelPath: String, cacheDir: String, backendName: String) {
        engineLock.withLock {
            if (isReleased.get()) return
            withContext(Dispatchers.IO) {
                try {
                    unloadInternal()

                    val backendObj = BackendProvider.getBackend(backendName)
                    
                    // Actual Signature from javap and error analysis:
                    // EngineConfig(modelPath, backend, visionBackend, audioBackend, maxNumTokens, maxNumImages)
                    // Then set cacheDir via reflection if it's missing, or omit it if not supported.
                    // Based on the error "actual type is 'kotlin.String', but 'kotlin.Int?' was expected",
                    // the 6th argument is likely an Int (maybe maxNumImages).
                    // Let's use 0 for the 6th argument.
                    val config = EngineConfig(
                        modelPath,
                        backendObj,
                        backendObj, // Use same for vision
                        backendObj, // Use same for audio
                        2048,       // maxNumTokens
                        null        // maxNumImages (set to null to avoid validation constraints)
                    )
                    
                    val newEngine = Engine(config)
                    newEngine.initialize()
                    
                    engine = newEngine
                    conversation = newEngine.createConversation()
                    
                    Log.i(TAG, "✓ LiteRT Engine initialized on $backendName via Public API")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to load engine directly", e)
                    unloadInternal()
                    throw e
                }
            }
        }
    }

    override suspend fun unload() {
        engineLock.withLock { unloadInternal() }
    }

    private suspend fun unloadInternal() {
        withContext(Dispatchers.IO) {
            try {
                conversation?.close()
            } catch (e: Exception) {
            } finally {
                conversation = null
            }

            try {
                engine?.close()
            } catch (e: Exception) {
            } finally {
                engine = null
            }
        }
    }

    override fun isReady(): Boolean = !isReleased.get() && engine != null && conversation != null

    override fun generate(prompt: String): Flow<String> {
        if (isReleased.get() || engine == null) return emptyFlow()
        val currentConversation = conversation ?: return emptyFlow()
        return currentConversation.sendMessageAsync(prompt).map { it.toString() }
    }

    override fun generateVision(imagePath: String, prompt: String): Flow<String> {
        if (isReleased.get() || engine == null) return emptyFlow()
        val currentConversation = conversation ?: return emptyFlow()
        
        val contents = Contents.of(
            Content.ImageFile(imagePath),
            Content.Text(prompt)
        )

        return currentConversation.sendMessageAsync(contents).map { it.toString() }
    }

    override fun resetConversation() {
        runBlocking {
            engineLock.withLock {
                val currentEngine = engine ?: return@withLock
                try {
                    conversation?.close()
                } catch (_: Exception) {}
                
                conversation = currentEngine.createConversation()
            }
        }
    }

    override fun close() {
        if (isReleased.compareAndSet(false, true)) {
            runBlocking { unload() }
        }
    }
}
