package com.example.localqwen.engine

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.lang.reflect.Array as ReflectArray
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.atomic.AtomicBoolean

class LiteRtLmInferenceEngine : NabdInferenceEngine {
    private val engineLock = Mutex()
    private val isReleased = AtomicBoolean(false)
    private var engine: Any? = null
    private var conversation: Any? = null

    override suspend fun load(modelPath: String, cacheDir: String) {
        load(modelPath, cacheDir, "cpu")
    }

    override suspend fun load(modelPath: String, cacheDir: String, backendName: String) {
        engineLock.withLock {
            if (isReleased.get()) {
                Log.e(TAG, "Attempted to load model on a released engine")
                return
            }
            withContext(Dispatchers.IO) {
                try {
                    unloadInternal()

                    val refs = getCachedRefs()
                    val backend = try {
                        val backendClassName = when (backendName.lowercase()) {
                            "gpu" -> "com.google.ai.edge.litertlm.Backend\$GPU"
                            "npu" -> "com.google.ai.edge.litertlm.Backend\$NPU"
                            else -> "com.google.ai.edge.litertlm.Backend\$CPU"
                        }
                        Class.forName(backendClassName).getConstructor().newInstance()
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to load backend $backendName, falling back to CPU", e)
                        refs.cpuBackendClass.getConstructor().newInstance()
                    }

                    // Detect if it's a multimodal model (.task)
                    val isMultimodal = modelPath.endsWith(".task", ignoreCase = true)
                    Log.d(TAG, "Model type: ${if (isMultimodal) "Multimodal (.task)" else "Text-only (.litertlm)"}")

                    val maxContextTokens = 2048 // Emergency Context Inflation
                    val numThreads = 4

                    Log.d(TAG, "Initializing engine with maxContextTokens=$maxContextTokens, numThreads=$numThreads, backend=$backendName")

                    // --- Dynamic MTP / Speculative Decoding Hunt ---
                    try {
                        val builder = refs.optionsBuilderClass.getConstructor().newInstance()
                        val mtpMethod = refs.optionsBuilderClass.methods.firstOrNull { 
                            it.name == "setEnableSpeculativeDecoding" || it.name == "setEnableMtp"
                        }
                        
                        if (mtpMethod != null) {
                            // MTP methods usually take a Boolean parameter
                            if (mtpMethod.parameterTypes.firstOrNull() == Boolean::class.java || 
                                mtpMethod.parameterTypes.firstOrNull() == Boolean::class.javaPrimitiveType) {
                                mtpMethod.invoke(builder, true)
                                Log.d("Nabd_MTP", "Speculative Decoding activated successfully via ${mtpMethod.name}")
                            }
                        } else {
                            Log.d("Nabd_MTP", "Speculative Decoding methods not found in SDK metadata. Falling back to standard mode.")
                        }
                    } catch (mtpError: Exception) {
                        Log.w("Nabd_MTP", "MTP Activation failed (non-critical): ${mtpError.message}")
                    }

                    // Reflection Audit: LiteRT-LM EngineConfig usually follows:
                    // (modelPath, genBackend, prefBackend, kvBackend, maxContext, numThreads, cacheDir)
                    // We will explicitly search for the matching constructor or use a safer instantiation.
                    
                    val constructors = refs.engineConfigClass.constructors
                    val constructor = constructors.firstOrNull { it.parameterTypes.size == 7 }
                        ?: error("Unable to find valid EngineConfig constructor with 7 parameters")
                    
                    val paramTypes = constructor.parameterTypes
                    val args = arrayOfNulls<Any>(7)
                    args[0] = modelPath
                    args[1] = backend
                    args[2] = backend
                    args[3] = backend
                    
                    // Safe type-checking for swapped parameters
                    // We assume paramTypes[4] is context and paramTypes[5] is threads, 
                    // but we inflate both if they are Integer to be absolutely safe against swapping.
                    for (i in 4..5) {
                        if (paramTypes[i] == Integer::class.java || paramTypes[i] == Int::class.javaPrimitiveType) {
                            // If we suspect swapping, we set a high enough value for BOTH 
                            // to ensure neither ends up as '4' for context.
                            args[i] = if (i == 4) Integer.valueOf(maxContextTokens) else Integer.valueOf(numThreads)
                        }
                    }
                    args[6] = cacheDir

                    Log.d(TAG, "Constructor types: ${paramTypes.joinToString { it.simpleName }}")
                    Log.d(TAG, "Arguments: ${args.joinToString { it?.toString() ?: "null" }}")

                    val config = constructor.newInstance(*args)

                    val newEngine = refs.engineClass.getConstructor(refs.engineConfigClass).newInstance(config)
                    refs.engineClass.getMethod("initialize").invoke(newEngine)
                    engine = newEngine
                    conversation = refs.createConversation(newEngine)
                } catch (error: Throwable) {
                    unloadInternal()
                    throw error.unwrapInvocation()
                }
            }
        }
    }

    override suspend fun unload() {
        engineLock.withLock {
            unloadInternal()
        }
    }

    private suspend fun unloadInternal() {
        withContext(Dispatchers.IO) {
            try {
                (conversation as? AutoCloseable)?.close()
            } catch (e: Exception) {
                Log.w(TAG, "Error closing conversation", e)
            } finally {
                conversation = null
            }
            try {
                (engine as? AutoCloseable)?.close()
            } catch (e: Exception) {
                Log.w(TAG, "Error closing engine", e)
            } finally {
                engine = null
            }
        }
    }

    override fun close() {
        if (isReleased.compareAndSet(false, true)) {
            runBlocking {
                unload()
            }
        }
    }

    override fun isReady(): Boolean {
        return !isReleased.get() && engine != null && conversation != null
    }

    override fun resetConversation() {
        runBlocking {
            engineLock.withLock {
                val currentEngine = engine ?: return@withLock
                val refs = getCachedRefs()
                try {
                    // Explicitly close the previous conversation to free native KV cache
                    (conversation as? AutoCloseable)?.close()
                } catch (e: Exception) {
                    Log.w(TAG, "Error closing previous native conversation", e)
                } finally {
                    conversation = null
                }
                
                try {
                    // Re-create a fresh conversation instance
                    conversation = refs.createConversation(currentEngine)
                    Log.d(TAG, "Native conversation context reset successfully")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to create new native conversation", e)
                }
            }
        }
    }

    override fun generate(prompt: String): Flow<String> {
        if (isReleased.get() || engine == null) return emptyFlow()
        
        Log.d(TAG, "Shipping payload to Native. Length: ${prompt.length} chars, Byte count: ${prompt.toByteArray().size}")
        
        val refs = getCachedRefs()
        val currentConversation = conversation ?: return emptyFlow()
        
        return refs.sendTextAsync(currentConversation, prompt)
            .map { it.toString() }
            .onCompletion {
                // Potential hook for cleanup if needed per-generation
            }
    }

    override fun generateVision(imagePath: String, prompt: String): Flow<String> {
        if (isReleased.get() || engine == null) return emptyFlow()
        
        val refs = getCachedRefs()
        val currentConversation = conversation ?: return emptyFlow()
        
        val imageContent = refs.imageFileContentClass.getConstructor(String::class.java).newInstance(imagePath)
        val textContent = refs.textContentClass.getConstructor(String::class.java).newInstance(prompt)
        val contentArray = ReflectArray.newInstance(refs.contentClass, 2)
        ReflectArray.set(contentArray, 0, imageContent)
        ReflectArray.set(contentArray, 1, textContent)
        val contents = refs.contentsCompanionClass
            .getMethod("of", contentArray.javaClass)
            .invoke(refs.contentsCompanion, contentArray)
            ?: error("Unable to create LiteRT message contents")

        return refs.sendContentsAsync(currentConversation, contents).map { it.toString() }
    }

    /**
     * Checks if the LiteRT API classes are available at runtime.
     * Call this before attempting to load a model to get a clear diagnostic
     * instead of a generic ClassNotFoundException.
     *
     * @return null if all classes are available, or a descriptive error message.
     */
    companion object {
        private const val TAG = "LiteRtEngine"

        private val REQUIRED_CLASSES = listOf(
            "com.google.ai.edge.litertlm.Engine" to "LiteRT Engine",
            "com.google.ai.edge.litertlm.EngineConfig" to "LiteRT EngineConfig",
            "com.google.ai.edge.litertlm.Backend" to "LiteRT Backend",
            "com.google.ai.edge.litertlm.Backend\$CPU" to "LiteRT CPU Backend",
            "com.google.ai.edge.litertlm.Conversation" to "LiteRT Conversation",
            "com.google.ai.edge.litertlm.ConversationConfig" to "LiteRT ConversationConfig",
            "com.google.ai.edge.litertlm.Contents" to "LiteRT Contents",
            "com.google.ai.edge.litertlm.Content" to "LiteRT Content",
            "com.google.ai.edge.litertlm.Content\$Text" to "LiteRT Text Content",
            "com.google.ai.edge.litertlm.Content\$ImageFile" to "LiteRT ImageFile Content"
        )

        /**
         * Checks that all required LiteRT classes are present on the classpath.
         * @return null if available, or a human-readable error string listing missing classes.
         */
        fun checkApiAvailability(): String? {
            val missing = mutableListOf<String>()
            for ((className, displayName) in REQUIRED_CLASSES) {
                try {
                    Class.forName(className)
                } catch (_: ClassNotFoundException) {
                    missing.add(displayName)
                }
            }
            return if (missing.isEmpty()) {
                null
            } else {
                "LiteRT API غير متوفر. الفئات المفقودة: ${missing.joinToString(", ")}"
            }
        }

        /** Cached refs instance — avoids repeated reflection lookups. */
        @Volatile
        private var cachedRefs: LiteRtRefs? = null

        private fun getCachedRefs(): LiteRtRefs {
            return cachedRefs ?: synchronized(this) {
                cachedRefs ?: LiteRtRefs().also { cachedRefs = it }
            }
        }
    }

    private fun getCachedRefs(): LiteRtRefs = Companion.getCachedRefs()

    private class LiteRtRefs {
        val engineClass: Class<*> = resolveClass("com.google.ai.edge.litertlm.Engine", "Engine")
        val engineConfigClass: Class<*> = resolveClass("com.google.ai.edge.litertlm.EngineConfig", "EngineConfig")
        val backendClass: Class<*> = resolveClass("com.google.ai.edge.litertlm.Backend", "Backend")
        val cpuBackendClass: Class<*> = resolveClass("com.google.ai.edge.litertlm.Backend\$CPU", "CPU Backend")
        val conversationClass: Class<*> = resolveClass("com.google.ai.edge.litertlm.Conversation", "Conversation")
        val conversationConfigClass: Class<*> = resolveClass("com.google.ai.edge.litertlm.ConversationConfig", "ConversationConfig")
        val optionsClass: Class<*> = resolveClass("com.google.ai.edge.litertlm.InferenceEngineOptions", "InferenceEngineOptions")
        val optionsBuilderClass: Class<*> = resolveClass("com.google.ai.edge.litertlm.InferenceEngineOptions\$Builder", "InferenceEngineOptions.Builder")
        val contentsClass: Class<*> = resolveClass("com.google.ai.edge.litertlm.Contents", "Contents")
        val contentsCompanionClass: Class<*> = resolveClass("com.google.ai.edge.litertlm.Contents\$Companion", "Contents.Companion")
        val contentClass: Class<*> = resolveClass("com.google.ai.edge.litertlm.Content", "Content")
        val textContentClass: Class<*> = resolveClass("com.google.ai.edge.litertlm.Content\$Text", "Content.Text")
        val imageFileContentClass: Class<*> = resolveClass("com.google.ai.edge.litertlm.Content\$ImageFile", "Content.ImageFile")
        val contentsCompanion: Any = contentsClass.getField("Companion").get(null)
            ?: error("LiteRT Contents companion is unavailable")

        private fun resolveClass(name: String, displayName: String): Class<*> {
            return try {
                Class.forName(name)
            } catch (e: ClassNotFoundException) {
                throw IllegalStateException(
                    "فئة LiteRT المطلوبة '$displayName' غير متوفرة. " +
                    "تأكد من أن مكتبة litertlm مضمّنة في التطبيق. ($name)", e
                )
            }
        }

        fun createConversation(engine: Any): Any {
            return engineClass
                .getMethod(
                    "createConversation\$default",
                    engineClass,
                    conversationConfigClass,
                    Int::class.javaPrimitiveType,
                    Any::class.java
                )
                .invoke(null, engine, null, 1, null)
                ?: error("Unable to create LiteRT conversation")
        }

        @Suppress("UNCHECKED_CAST")
        fun sendTextAsync(conversation: Any, prompt: String): Flow<Any> {
            return conversationClass
                .getMethod(
                    "sendMessageAsync\$default",
                    conversationClass,
                    String::class.java,
                    Map::class.java,
                    Int::class.javaPrimitiveType,
                    Any::class.java
                )
                .invoke(null, conversation, prompt, null, 2, null) as Flow<Any>
        }

        @Suppress("UNCHECKED_CAST")
        fun sendContentsAsync(conversation: Any, contents: Any): Flow<Any> {
            return conversationClass
                .getMethod(
                    "sendMessageAsync\$default",
                    conversationClass,
                    contentsClass,
                    Map::class.java,
                    Int::class.javaPrimitiveType,
                    Any::class.java
                )
                .invoke(null, conversation, contents, null, 2, null) as Flow<Any>
        }
    }

    private fun Throwable.unwrapInvocation(): Throwable {
        return if (this is InvocationTargetException && targetException != null) {
            targetException
        } else {
            this
        }
    }
}

