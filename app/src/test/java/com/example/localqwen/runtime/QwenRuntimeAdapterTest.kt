package com.example.localqwen.runtime

import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class QwenRuntimeAdapterTest {

    private val adapter = QwenRuntimeAdapter()

    @Test
    fun testAdapterMetadata() {
        assertEquals("qwen-default", adapter.runtimeId)
        assertTrue(adapter.isAvailable())
    }

    @Test
    fun testInitialize() = runBlocking {
        val success = adapter.initialize("/path/to/model.litertlm")
        assertTrue(success is RuntimeStatus.Ready)

        val failure = adapter.initialize("")
        assertTrue(failure is RuntimeStatus.Failed)
        assertTrue((failure as RuntimeStatus.Failed).error is RuntimeError.ModelFileMissing)
    }

    @Test
    fun testGenerateStubBehavior() = runBlocking {
        val request = RuntimeGenerationRequest(prompt = "Test")
        val result = adapter.generate(request) { }
        
        assertTrue(result.error is RuntimeError.GenerationFailed)
        assertTrue(result.error?.let { (it as RuntimeError.GenerationFailed).message.contains("stub") } == true)
    }
}
