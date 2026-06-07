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

                    // Discover the correct EngineConfig constructor dynamically
                    val constructors = refs.engineConfigClass.constructors
                    Log.d(TAG, "EngineConfig has ${constructors.size} constructors:")
                    constructors.forEachIndexed { i, c ->
                        Log.d(TAG, "  [$i] params: ${c.parameterTypes.map { it.name }}")
                    }

                    val config = try {
                        // Try 3-param: (String modelPath, Backend backend, String cacheDir)
                        refs.engineConfigClass
                            .getConstructor(String::class.java, refs.backendClass, String::class.java)
                            .newInstance(modelPath, backend, cacheDir)
                    } catch (_: NoSuchMethodException) {
                        try {
                            // Try 7-param v2: (String, Backend, String?, Int?, Int?, Backend?, Backend?)
                            refs.engineConfigClass
                                .getConstructor(
                                    String::class.java,
                                    refs.backendClass,
                                    String::class.java,
                                    Integer::class.java,
                                    Integer::class.java,
                                    refs.backendClass,
                                    refs.backendClass
                                )
                                .newInstance(modelPath, backend, cacheDir, null, null, backend, backend)
                        } catch (_: NoSuchMethodException) {
                            try {
                                // Try 7-param v1: (String, Backend, Backend, Backend, Int?, Int?, String?)
                                refs.engineConfigClass
                                    .getConstructor(
                                        String::class.java,
                                        refs.backendClass,
                                        refs.backendClass,
                                        refs.backendClass,
                                        Integer::class.java,
                                        Integer::class.java,
                                        String::class.java
                                    )
                                    .newInstance(modelPath, backend, backend, backend, null, null, cacheDir)
                            } catch (_: NoSuchMethodException) {
                                // Fallback: find any constructor starting with (String, Backend, ...)
                                val ctor = constructors.firstOrNull { c ->
                                    val params = c.parameterTypes
                                    params.size >= 2 &&
                                        params[0] == String::class.java &&
                                        refs.backendClass.isAssignableFrom(params[1])
                                } ?: throw NoSuchMethodException(
                                    "No compatible EngineConfig constructor found. " +
                                    "Available: ${constructors.map { it.parameterTypes.map { p -> p.simpleName } }}"
                                )
                                val params = ctor.parameterTypes
                                Log.d(TAG, "Using fallback constructor with ${params.size} params")
                                val args = arrayOfNulls<Any>(params.size)
                                args[0] = modelPath
                                args[1] = backend
                                // Fill remaining Backend params with our backend, String params with cacheDir
                                for (idx in 2 until params.size) {
                                    args[idx] = when {
                                        params[idx] == String::class.java || params[idx] == String::class.javaObjectType -> cacheDir
                                        refs.backendClass.isAssignableFrom(params[idx]) -> backend
                                        else -> null
                                    }
                                }
                                ctor.newInstance(*args)
                            }
                        }
                    }

                    // ── MTP / Speculative Decoding Activation ──────────────────
                    val inferenceOptions = tryConfigureInferenceEngineOptions()
                    val mtpActivated = (inferenceOptions != null) || tryEnableSpeculativeDecoding()
                    if (mtpActivated) {
                        Log.d(TAG_MTP, "✓ Speculative Decoding flag is SET — drafter will link on GPU")
                    } else {
                        Log.d(TAG_MTP, "MTP not available in this SDK version — single-token inference")
                    }

                    val newEngine = try {
                        if (inferenceOptions != null) {
                            try {
                                refs.engineClass.getConstructor(inferenceOptions.javaClass).newInstance(inferenceOptions)
                            } catch (_: NoSuchMethodException) {
                                val ctor = refs.engineClass.constructors.firstOrNull { c ->
                                    c.parameterTypes.size == 1 && c.parameterTypes[0].isAssignableFrom(inferenceOptions.javaClass)
                                }
                                if (ctor != null) {
                                    ctor.newInstance(inferenceOptions)
                                } else {
                                    throw NoSuchMethodException()
                                }
                            }
                        } else {
                            throw NoSuchMethodException()
                        }
                    } catch (_: Exception) {
                        // Fallback to standard EngineConfig
                        refs.engineClass.getConstructor(refs.engineConfigClass).newInstance(config)
                    }

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
        private const val TAG_MTP = "Nabd_MTP"

        /**
         * Attempts to dynamically configure and construct InferenceEngineOptions via its Builder.
         * Sets stable threads and context size parameters, and scans for MTP/Speculative Decoding setters.
         */
        fun tryConfigureInferenceEngineOptions(): Any? {
            val candidateBuilderClasses = listOf(
                "com.google.ai.edge.litertlm.InferenceEngineOptions\$Builder",
                "com.google.ai.edge.litertlm.InferenceEngineOptions.Builder",
                "com.google.ai.edge.litertlm.experimental.InferenceEngineOptions\$Builder",
                "com.google.ai.edge.litertlm.experimental.InferenceEngineOptions.Builder",
                "com.google.ai.edge.litertlm.EngineConfig\$Builder",
                "com.google.ai.edge.litertlm.EngineConfig.Builder"
            )

            for (builderClassName in candidateBuilderClasses) {
                try {
                    val builderClass = Class.forName(builderClassName)
                    Log.d(TAG_MTP, "Found options builder class candidate: $builderClassName")

                    // Instantiate the builder
                    val builderInstance = try {
                        builderClass.getConstructor().newInstance()
                    } catch (_: NoSuchMethodException) {
                        // Check if there is a static builder() method on the enclosing class
                        val enclosingClassName = builderClassName.substringBeforeLast("$").substringBeforeLast(".")
                        val enclosingClass = Class.forName(enclosingClassName)
                        enclosingClass.getMethod("builder").invoke(null)
                    }

                    // 1. Maintain Core Stable Specs: setNumberOfThreads(4)
                    try {
                        val setThreadsMethod = builderClass.methods.firstOrNull { m ->
                            (m.name == "setNumberOfThreads" || m.name == "setNumThreads" || m.name == "threads") &&
                                    m.parameterTypes.size == 1 &&
                                    (m.parameterTypes[0] == Int::class.javaPrimitiveType || m.parameterTypes[0] == Integer::class.java)
                        }
                        if (setThreadsMethod != null) {
                            setThreadsMethod.invoke(builderInstance, 4)
                            Log.d(TAG_MTP, "✓ Configured threads = 4 via ${setThreadsMethod.name}")
                        } else {
                            Log.d(TAG_MTP, "No thread setter method found on options builder")
                        }
                    } catch (e: Exception) {
                        Log.w(TAG_MTP, "Error setting thread count on builder", e)
                    }

                    // 1. Maintain Core Stable Specs: setMaxContextTokens(2048)
                    try {
                        val setTokensMethod = builderClass.methods.firstOrNull { m ->
                            (m.name == "setMaxContextTokens" || m.name == "setMaxContextLength" || m.name == "setMaxTokens" || m.name == "maxTokens" || m.name == "maxContextTokens") &&
                                    m.parameterTypes.size == 1 &&
                                    (m.parameterTypes[0] == Int::class.javaPrimitiveType || m.parameterTypes[0] == Integer::class.java)
                        }
                        if (setTokensMethod != null) {
                            setTokensMethod.invoke(builderInstance, 2048)
                            Log.d(TAG_MTP, "✓ Configured maxContextTokens = 2048 via ${setTokensMethod.name}")
                        } else {
                            Log.d(TAG_MTP, "No context token limit method found on options builder")
                        }
                    } catch (e: Exception) {
                        Log.w(TAG_MTP, "Error setting max context tokens on builder", e)
                    }

                    // 2. Dynamic MTP Method Hunting & 3. Safely Invoke Flag
                    var mtpInvoked = false
                    val mtpSignatures = listOf(
                        "setEnableSpeculativeDecoding",
                        "setEnableMtp",
                        "enableSpeculativeDecoding",
                        "enableMtp",
                        "setSpeculativeDecoding"
                    )
                    for (sigName in mtpSignatures) {
                        try {
                            val method = builderClass.methods.firstOrNull { m ->
                                m.name == sigName && m.parameterTypes.size == 1 &&
                                        (m.parameterTypes[0] == Boolean::class.javaPrimitiveType || m.parameterTypes[0] == Boolean::class.javaObjectType)
                            }
                            if (method != null) {
                                method.invoke(builderInstance, true)
                                Log.d(TAG_MTP, "✓ Speculative Decoding flag is SET via $sigName(true)")
                                mtpInvoked = true
                                break
                            }
                        } catch (_: Exception) {}
                    }

                    if (!mtpInvoked) {
                        // Regex pattern-match signature scan
                        val method = builderClass.methods.firstOrNull { m ->
                            val nameLower = m.name.lowercase()
                            (nameLower.contains("speculative") || nameLower.contains("mtp")) &&
                                    m.parameterTypes.size == 1 &&
                                    (m.parameterTypes[0] == Boolean::class.javaPrimitiveType || m.parameterTypes[0] == Boolean::class.javaObjectType)
                        }
                        if (method != null) {
                            method.invoke(builderInstance, true)
                            Log.d(TAG_MTP, "✓ Speculative Decoding flag is SET via ${method.name}(true)")
                            mtpInvoked = true
                        }
                    }

                    if (!mtpInvoked) {
                        Log.d(TAG_MTP, "MTP/Speculative configuration method not found in $builderClassName metadata")
                    }

                    // Build the final options/config object
                    val options = builderClass.getMethod("build").invoke(builderInstance)
                    Log.d(TAG_MTP, "✓ InferenceEngineOptions built successfully")
                    return options

                } catch (_: ClassNotFoundException) {
                    // Try next class candidate
                } catch (e: Exception) {
                    Log.w(TAG_MTP, "Error probing options builder candidate $builderClassName: ${e.message}", e)
                }
            }

            Log.d(TAG_MTP, "InferenceEngineOptions\$Builder class layer not discovered on classpath")
            return null
        }

        /** Candidate class names for the experimental flags singleton. */
        private val EXPERIMENTAL_FLAGS_CLASSES = listOf(
            "com.google.ai.edge.litertlm.ExperimentalFlags",
            "com.google.ai.edge.litertlm.experimental.ExperimentalFlags"
        )

        /** Method/field name patterns that indicate speculative decoding support. */
        private val MTP_SIGNATURES = listOf(
            "enableSpeculativeDecoding",
            "setEnableSpeculativeDecoding",
            "setEnableMtp",
            "enableMtp",
            "setSpeculativeDecoding"
        )

        /**
         * Attempts to enable Multi-Token Prediction / Speculative Decoding
         * through the LiteRT-LM experimental API surface.
         *
         * Strategy:
         *   1. Locate `ExperimentalFlags` class and set `enableSpeculativeDecoding` field
         *   2. Fallback: try setter method on ExperimentalFlags
         *   3. Fallback: scan EngineConfig for any MTP-related method
         *
         * @return true if MTP was successfully activated, false otherwise.
         */
        fun tryEnableSpeculativeDecoding(): Boolean {
            // ── Strategy 1: Static field on ExperimentalFlags ──────────────
            for (className in EXPERIMENTAL_FLAGS_CLASSES) {
                try {
                    val flagsClass = Class.forName(className)
                    Log.d(TAG_MTP, "Found ExperimentalFlags: $className")

                    // Enumerate available fields for diagnostics
                    flagsClass.declaredFields.forEach { field ->
                        Log.d(TAG_MTP, "  field: ${field.name} (${field.type.simpleName})")
                    }

                    // Try direct Kotlin companion property (static field)
                    for (sigName in MTP_SIGNATURES) {
                        try {
                            val field = flagsClass.getDeclaredField(sigName)
                            field.isAccessible = true
                            if (field.type == Boolean::class.javaPrimitiveType ||
                                field.type == Boolean::class.javaObjectType
                            ) {
                                field.set(null, true)
                                Log.d(TAG_MTP, "✓ Set $className.$sigName = true (field)")
                                return true
                            }
                        } catch (_: NoSuchFieldException) { /* try next */ }
                    }

                    // ── Strategy 2: Setter method on ExperimentalFlags ─────
                    for (sigName in MTP_SIGNATURES) {
                        try {
                            val setter = flagsClass.getMethod(sigName, Boolean::class.javaPrimitiveType)
                            setter.invoke(null, true)
                            Log.d(TAG_MTP, "✓ Called $className.$sigName(true) (method)")
                            return true
                        } catch (_: NoSuchMethodException) { /* try next */ }
                    }

                    // Try Kotlin-style set<PropertyName> patterns
                    val setterMethods = flagsClass.declaredMethods.filter { m ->
                        val nameLower = m.name.lowercase()
                        (nameLower.contains("speculative") || nameLower.contains("mtp")) &&
                            m.parameterTypes.singleOrNull()?.let {
                                it == Boolean::class.javaPrimitiveType || it == Boolean::class.javaObjectType
                            } == true
                    }
                    if (setterMethods.isNotEmpty()) {
                        val method = setterMethods.first()
                        method.isAccessible = true
                        method.invoke(null, true)
                        Log.d(TAG_MTP, "✓ Called ${method.name}(true) via pattern match")
                        return true
                    }

                    Log.d(TAG_MTP, "ExperimentalFlags found but no MTP setter in this version")
                } catch (_: ClassNotFoundException) {
                    Log.d(TAG_MTP, "ExperimentalFlags not found at $className")
                } catch (e: Exception) {
                    Log.w(TAG_MTP, "Error probing ExperimentalFlags at $className", e)
                }
            }

            // ── Strategy 3: Scan EngineConfig for MTP methods ──────────────
            try {
                val configClass = Class.forName("com.google.ai.edge.litertlm.EngineConfig")
                val mtpMethods = configClass.declaredMethods.filter { m ->
                    val nameLower = m.name.lowercase()
                    nameLower.contains("speculative") || nameLower.contains("mtp")
                }
                if (mtpMethods.isNotEmpty()) {
                    Log.d(TAG_MTP, "Found MTP methods on EngineConfig: ${mtpMethods.map { it.name }}")
                }
            } catch (e: Exception) {
                Log.d(TAG_MTP, "EngineConfig MTP scan skipped: ${e.message}")
            }

            return false
        }

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

