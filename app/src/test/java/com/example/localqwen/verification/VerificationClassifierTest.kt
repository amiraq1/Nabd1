package com.example.localqwen.verification

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VerificationClassifierTest {

    @Test
    fun testLevel0Direct() {
        val query1 = "ما معنى الذكاء الاصطناعي؟"
        val decision1 = VerificationClassifier.classify(query1)
        assertEquals(VerificationLevel.LEVEL_0_DIRECT, decision1.level)
        assertTrue(decision1.shouldAnswerDirectly)
        assertFalse(decision1.shouldAddCaution)

        val query2 = "اكتب كود Kotlin لزر رجوع في Compose"
        val decision2 = VerificationClassifier.classify(query2)
        assertEquals(VerificationLevel.LEVEL_0_DIRECT, decision2.level)
    }

    @Test
    fun testLevel1ContextualCaution() {
        val query1 = "ما بطولات ريال مدريد عام 2002؟"
        val decision1 = VerificationClassifier.classify(query1)
        assertEquals(VerificationLevel.LEVEL_1_CONTEXTUAL_CAUTION, decision1.level)
        assertTrue(decision1.shouldAddCaution)
        assertEquals(SourceRequirement.STATIC_KNOWLEDGE, decision1.sourceRequirement)

        val query2 = "هل فاز ريال مدريد بالدوري الإسباني 2002؟"
        val decision2 = VerificationClassifier.classify(query2)
        assertEquals(VerificationLevel.LEVEL_1_CONTEXTUAL_CAUTION, decision2.level)
    }

    @Test
    fun testLevel2RecentOrSensitive() {
        val query1 = "من فاز بكأس العالم 2030؟"
        val decision1 = VerificationClassifier.classify(query1)
        assertEquals(VerificationLevel.LEVEL_2_RECENT_OR_SENSITIVE, decision1.level)
        assertTrue(decision1.shouldAddCaution)

        val query2 = "من هو رئيس العراق الحالي؟"
        val decision2 = VerificationClassifier.classify(query2)
        assertEquals(VerificationLevel.LEVEL_2_RECENT_OR_SENSITIVE, decision2.level)
        assertEquals(SourceRequirement.RECENT_SOURCE, decision2.sourceRequirement)

        val query3 = "كم سعر الذهب اليوم؟"
        val decision3 = VerificationClassifier.classify(query3)
        assertEquals(VerificationLevel.LEVEL_2_RECENT_OR_SENSITIVE, decision3.level)
        assertEquals(SourceRequirement.RECENT_SOURCE, decision3.sourceRequirement)
    }

    @Test
    fun testLevel2ProfessionalAdvice() {
        val query1 = "أعطني علاج نهائي للسكري"
        val decision1 = VerificationClassifier.classify(query1)
        assertEquals(VerificationLevel.LEVEL_2_RECENT_OR_SENSITIVE, decision1.level)
        assertEquals(SourceRequirement.PROFESSIONAL_ADVICE, decision1.sourceRequirement)
        assertEquals(VerifiedAnswerState.SAFETY_RESTRICTED, decision1.answerState)

        val query2 = "هل أستثمر كل أموالي في سهم تسلا؟"
        val decision2 = VerificationClassifier.classify(query2)
        assertEquals(VerificationLevel.LEVEL_2_RECENT_OR_SENSITIVE, decision2.level)
        assertEquals(SourceRequirement.PROFESSIONAL_ADVICE, decision2.sourceRequirement)
    }
}
