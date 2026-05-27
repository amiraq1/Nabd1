package com.example.localqwen.runtime

import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GgufRuntimeAdapterTest {

    private val adapter = GgufRuntimeAdapter()

    @Test
    fun testAdapterMetadata() {
        assertEquals("gguf-experimental", adapter.runtimeId)
        assertFalse(adapter.isAvailable())
    }

    @Test
    fun testInitializeFailures() = runBlocking {
        // 1. Path missing
        val fail1 = adapter.initialize("")
        assertTrue((fail1 as RuntimeStatus.Failed).error is RuntimeError.ModelFileMissing)

        // 2. Unsupported format
        val fail2 = adapter.initialize("/path/model.litertlm")
        assertTrue((fail2 as RuntimeStatus.Failed).error is RuntimeError.UnsupportedFormat)

        // 3. Native library missing
        val fail3 = adapter.initialize("/path/model.gguf")
        assertTrue((fail3 as RuntimeStatus.Failed).error is RuntimeError.NativeLibraryMissing)
    }

    @Test
    fun testGenerateStubBehavior() = runBlocking {
        val request = RuntimeGenerationRequest(prompt = "Test")
        val result = adapter.generate(request) { }
        
        assertTrue(result.error is RuntimeError.GenerationFailed)
        assertTrue(result.error?.let { (it as RuntimeError.GenerationFailed).message.contains("stub") } == true)
    }
}
