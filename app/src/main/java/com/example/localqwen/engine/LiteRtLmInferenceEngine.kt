package com.example.localqwen.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.lang.reflect.Array as ReflectArray
import java.lang.reflect.InvocationTargetException

class LiteRtLmInferenceEngine : NabdInferenceEngine {
    private var engine: Any? = null
    private var conversation: Any? = null

    override suspend fun load(modelPath: String, cacheDir: String) {
        withContext(Dispatchers.IO) {
            try {
                unload()

                val refs = LiteRtRefs()
                val cpuBackend = refs.cpuBackendClass.getConstructor().newInstance()
                val config = refs.engineConfigClass
                    .getConstructor(
                        String::class.java,
                        refs.backendClass,
                        refs.backendClass,
                        refs.backendClass,
                        Integer::class.java,
                        Integer::class.java,
                        String::class.java
                    )
                    .newInstance(modelPath, cpuBackend, cpuBackend, cpuBackend, null, null, cacheDir)

                val newEngine = refs.engineClass.getConstructor(refs.engineConfigClass).newInstance(config)
                refs.engineClass.getMethod("initialize").invoke(newEngine)
                engine = newEngine
                conversation = refs.createConversation(newEngine)
            } catch (error: Throwable) {
                unload()
                throw error.unwrapInvocation()
            }
        }
    }

    override suspend fun unload() {
        withContext(Dispatchers.IO) {
            (conversation as? AutoCloseable)?.close()
            conversation = null
            (engine as? AutoCloseable)?.close()
            engine = null
        }
    }

    override fun isReady(): Boolean {
        return engine != null && conversation != null
    }

    override fun resetConversation() {
        val currentEngine = engine ?: return
        val refs = LiteRtRefs()
        try {
            (conversation as? AutoCloseable)?.close()
        } catch (_: Exception) {}
        conversation = refs.createConversation(currentEngine)
    }

    override fun generate(prompt: String): Flow<String> {
        val refs = LiteRtRefs()
        val currentConversation = conversation ?: throw IllegalStateException("Engine not initialized")
        return refs.sendTextAsync(currentConversation, prompt).map { it.toString() }
    }

    override fun generateVision(imagePath: String, prompt: String): Flow<String> {
        val refs = LiteRtRefs()
        val currentConversation = conversation ?: throw IllegalStateException("Engine not initialized")
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

    private class LiteRtRefs {
        val engineClass: Class<*> = Class.forName("com.google.ai.edge.litertlm.Engine")
        val engineConfigClass: Class<*> = Class.forName("com.google.ai.edge.litertlm.EngineConfig")
        val backendClass: Class<*> = Class.forName("com.google.ai.edge.litertlm.Backend")
        val cpuBackendClass: Class<*> = Class.forName("com.google.ai.edge.litertlm.Backend\$CPU")
        val conversationClass: Class<*> = Class.forName("com.google.ai.edge.litertlm.Conversation")
        val conversationConfigClass: Class<*> = Class.forName("com.google.ai.edge.litertlm.ConversationConfig")
        val contentsClass: Class<*> = Class.forName("com.google.ai.edge.litertlm.Contents")
        val contentsCompanionClass: Class<*> = Class.forName("com.google.ai.edge.litertlm.Contents\$Companion")
        val contentClass: Class<*> = Class.forName("com.google.ai.edge.litertlm.Content")
        val textContentClass: Class<*> = Class.forName("com.google.ai.edge.litertlm.Content\$Text")
        val imageFileContentClass: Class<*> = Class.forName("com.google.ai.edge.litertlm.Content\$ImageFile")
        val contentsCompanion: Any = contentsClass.getField("Companion").get(null)
            ?: error("LiteRT Contents companion is unavailable")

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
