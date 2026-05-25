package com.example.localqwen.modelruntime

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ModelRuntimeModelsTest {

    @Test
    fun testDefaultCandidates() {
        assertEquals(ModelCandidateStatus.STABLE_DEFAULT, DefaultModelCandidates.Qwen.status)
        assertEquals(ModelCandidateStatus.EXPERIMENTAL, DefaultModelCandidates.MiniCPM.status)
        assertEquals(".litertlm", DefaultModelCandidates.Qwen.fileExtension)
    }

    @Test
    fun testMeetsQualityThreshold() {
        val goodResult = ModelQualityResult(
            modelId = "test",
            arabicQualityScore = 4.8,
            safetyScore = 4.9,
            hallucinationResistanceScore = 4.7,
            programmingScore = 4.5,
            verificationComplianceScore = 4.6,
            averageScore = 4.7,
            notes = "Good"
        )
        assertTrue(goodResult.meetsQualityThreshold())

        val poorSafety = goodResult.copy(safetyScore = 4.0, averageScore = 4.6)
        assertFalse(poorSafety.meetsQualityThreshold())
        
        val lowAverage = goodResult.copy(averageScore = 4.4)
        assertFalse(lowAverage.meetsQualityThreshold())
    }

    @Test
    fun testMeetsStabilityThreshold() {
        val stableResult = ModelBenchmarkResult(
            modelId = "test",
            timeToFirstTokenMs = 500L,
            tokensPerSecond = 15.0,
            peakMemoryMb = 1200L,
            modelSizeMb = 2000L,
            isStableOnAndroid = true,
            notes = "Fast"
        )
        assertTrue(stableResult.meetsStabilityThreshold())

        val unstable = stableResult.copy(isStableOnAndroid = false)
        assertFalse(unstable.meetsStabilityThreshold())

        val missingMetric = stableResult.copy(tokensPerSecond = null)
        assertFalse(missingMetric.meetsStabilityThreshold())
    }

    @Test
    fun testIsEligibleForDefault() {
        val candidate = DefaultModelCandidates.MiniCPM
        val quality = ModelQualityResult("test", 4.7, 4.7, 4.7, 4.7, 4.7, 4.7, "Ok")
        val benchmark = ModelBenchmarkResult("test", 500L, 10.0, 1000L, 1500L, true, "Ok")
        
        val experiment = ModelRuntimeExperiment(
            candidate = candidate,
            qualityResult = quality,
            benchmarkResult = benchmark,
            canBecomeDefault = true
        )

        // Experimental but can become default
        assertTrue(experiment.isEligibleForDefault())

        // Experimental but flagged as NOT for default
        assertFalse(experiment.copy(canBecomeDefault = false).isEligibleForDefault())

        // Rejected status
        val rejected = experiment.copy(candidate = candidate.copy(status = ModelCandidateStatus.REJECTED))
        assertFalse(rejected.isEligibleForDefault())
    }
}
