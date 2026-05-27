package com.example.localqwen.verification

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VerificationModelsTest {

    @Test
    fun testRequiresCaution() {
        assertFalse(VerificationLevel.LEVEL_0_DIRECT.requiresCaution())
        assertTrue(VerificationLevel.LEVEL_1_CONTEXTUAL_CAUTION.requiresCaution())
        assertTrue(VerificationLevel.LEVEL_2_RECENT_OR_SENSITIVE.requiresCaution())
    }

    @Test
    fun testRequiresExternalInput() {
        assertFalse(SourceRequirement.NONE.requiresExternalInput())
        assertFalse(SourceRequirement.STATIC_KNOWLEDGE.requiresExternalInput())
        assertTrue(SourceRequirement.RECENT_SOURCE.requiresExternalInput())
        assertTrue(SourceRequirement.USER_PROVIDED_SOURCE.requiresExternalInput())
        assertTrue(SourceRequirement.PROFESSIONAL_ADVICE.requiresExternalInput())
    }

    @Test
    fun testVerificationDecisionCreation() {
        val decision = VerificationDecision(
            level = VerificationLevel.LEVEL_1_CONTEXTUAL_CAUTION,
            sourceRequirement = SourceRequirement.STATIC_KNOWLEDGE,
            answerState = VerifiedAnswerState.PARTIALLY_VERIFIED,
            shouldAnswerDirectly = true,
            shouldAddCaution = true,
            shouldAskForSource = false,
            reason = "Old sports data"
        )
        
        assertEquals(VerificationLevel.LEVEL_1_CONTEXTUAL_CAUTION, decision.level)
        assertTrue(decision.shouldAddCaution)
        assertEquals("Old sports data", decision.reason)
    }
}
