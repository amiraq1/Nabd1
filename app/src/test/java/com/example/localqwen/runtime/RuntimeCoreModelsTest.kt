package com.example.localqwen.runtime

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RuntimeCoreModelsTest {

    @Test
    fun testGenerationResultSuccess() {
        val metrics = RuntimeMetrics(timeToFirstTokenMs = 100L)
        val result = RuntimeGenerationResult(fullText = "Hello", metrics = metrics)
        assertTrue(result.isSuccess())
    }

    @Test
    fun testGenerationResultFailure() {
        val metrics = RuntimeMetrics(timeToFirstTokenMs = 100L)
        val result = RuntimeGenerationResult(
            fullText = "",
            metrics = metrics,
            error = RuntimeError.GenerationFailed("Error")
        )
        assertFalse(result.isSuccess())
    }

    @Test
    fun testMetricsTokensPerSecond() {
        val metricsWithTps = RuntimeMetrics(timeToFirstTokenMs = 100L, tokensPerSecond = 15.5)
        assertEquals(15.5, metricsWithTps.tokensPerSecondOrZero())

        val metricsNoTps = RuntimeMetrics(timeToFirstTokenMs = 100L, tokensPerSecond = null)
        assertEquals(0.0, metricsNoTps.tokensPerSecondOrZero())
    }

    @Test
    fun testRuntimeStatus() {
        assertTrue(RuntimeStatus.Ready.isReady())
        assertFalse(RuntimeStatus.Initializing.isReady())

        val failedStatus = RuntimeStatus.Failed(RuntimeError.Unknown("Ouch"))
        assertTrue(failedStatus.isFailed())
        assertFalse(RuntimeStatus.Ready.isFailed())
    }

    @Test
    fun testRuntimeErrors() {
        val path = "/models/test.gguf"
        val error1 = RuntimeError.ModelFileMissing(path)
        assertEquals(path, error1.path)

        val libName = "libllama.so"
        val error2 = RuntimeError.NativeLibraryMissing(libName)
        assertEquals(libName, error2.libraryName)
    }
}
