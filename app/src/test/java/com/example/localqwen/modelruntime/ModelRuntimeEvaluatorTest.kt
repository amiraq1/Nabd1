package com.example.localqwen.modelruntime

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ModelRuntimeEvaluatorTest {

    @Test
    fun testEvaluationApprove() {
        val candidate = DefaultModelCandidates.MiniCPM
        val quality = ModelQualityResult("test", 4.8, 4.8, 4.8, 4.8, 4.8, 4.8, "Ok")
        val benchmark = ModelBenchmarkResult("test", 500L, 10.0, 1000L, 1500L, true, "Ok")
        
        val experiment = ModelRuntimeExperiment(
            candidate = candidate,
            qualityResult = quality,
            benchmarkResult = benchmark,
            canBecomeDefault = true
        )

        val summary = ModelRuntimeEvaluator.evaluate(experiment)
        
        assertEquals(ModelRecommendation.APPROVE, summary.recommendation)
        assertTrue(summary.isEligibleForDefault)
        assertTrue(summary.reasons.any { it.contains("يستوفي") })
    }

    @Test
    fun testEvaluationRejectDueToSafety() {
        val candidate = DefaultModelCandidates.MiniCPM
        val quality = ModelQualityResult("test", 4.8, 3.0, 4.8, 4.8, 4.8, 4.6, "Unsafe")
        val benchmark = ModelBenchmarkResult("test", 500L, 10.0, 1000L, 1500L, true, "Ok")
        
        val experiment = ModelRuntimeExperiment(
            candidate = candidate,
            qualityResult = quality,
            benchmarkResult = benchmark,
            canBecomeDefault = true
        )

        val summary = ModelRuntimeEvaluator.evaluate(experiment)
        
        assertEquals(ModelRecommendation.REJECT, summary.recommendation)
        assertFalse(summary.isEligibleForDefault)
        assertTrue(summary.reasons.any { it.contains("السلامة المهنية") })
    }

    @Test
    fun testEvaluationNeedsTesting() {
        val candidate = DefaultModelCandidates.MiniCPM
        
        val experiment = ModelRuntimeExperiment(
            candidate = candidate,
            qualityResult = null,
            benchmarkResult = null,
            canBecomeDefault = false
        )

        val summary = ModelRuntimeEvaluator.evaluate(experiment)
        
        assertEquals(ModelRecommendation.NEEDS_MORE_TESTING, summary.recommendation)
        assertFalse(summary.isEligibleForDefault)
        assertTrue(summary.reasons.any { it.contains("غير متوفرة") })
    }
}
