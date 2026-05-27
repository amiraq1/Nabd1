package com.example.localqwen.verification

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VerificationPromptBuilderTest {

    private fun createDecision(level: VerificationLevel): VerificationDecision {
        return VerificationDecision(
            level = level,
            sourceRequirement = SourceRequirement.NONE,
            answerState = VerifiedAnswerState.VERIFIED,
            shouldAnswerDirectly = true,
            shouldAddCaution = false,
            shouldAskForSource = false,
            reason = "Test"
        )
    }

    @Test
    fun testLevel0Prompt() {
        val decision = createDecision(VerificationLevel.LEVEL_0_DIRECT)
        val instruction = VerificationPromptBuilder.buildInstruction(decision)
        
        assertTrue(instruction.contains("أجب مباشرة"))
        assertFalse(VerificationPromptBuilder.shouldInjectInstruction(decision))
        assertEquals("Direct", VerificationPromptBuilder.buildShortLabel(decision))
    }

    @Test
    fun testLevel1Prompt() {
        val decision = createDecision(VerificationLevel.LEVEL_1_CONTEXTUAL_CAUTION)
        val instruction = VerificationPromptBuilder.buildInstruction(decision)
        
        assertTrue(instruction.contains("فرّق بين السنة التقويمية والموسم"))
        assertTrue(VerificationPromptBuilder.shouldInjectInstruction(decision))
        assertEquals("Contextual caution", VerificationPromptBuilder.buildShortLabel(decision))
    }

    @Test
    fun testLevel2Prompt() {
        val decision = createDecision(VerificationLevel.LEVEL_2_RECENT_OR_SENSITIVE)
        val instruction = VerificationPromptBuilder.buildInstruction(decision)
        
        assertTrue(instruction.contains("لا تجزم بالمعلومة دون مصدر حديث"))
        assertTrue(VerificationPromptBuilder.shouldInjectInstruction(decision))
        assertEquals("Recent or sensitive", VerificationPromptBuilder.buildShortLabel(decision))
    }
}
