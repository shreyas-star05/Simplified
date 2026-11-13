package com.dusk.core

import com.dusk.core.speech.SpeechScorer
import com.dusk.core.speech.TestScript
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechScorerTest {
    @Test
    fun identicalTextScoresZero() {
        val s = SpeechScorer.score(TestScript.TEXT, TestScript.TEXT)
        assertEquals(0.0, s.werPercent, 0.0001)
        assertEquals(0, s.sentencesDropped)
        assertEquals(30, s.sentencesTotal)
    }

    @Test
    fun punctuationCaseAndNumbersDoNotCount() {
        val s = SpeechScorer.score("I paid 450 rupees, today.", "i paid four hundred fifty rupees today")
        assertEquals(0.0, s.werPercent, 0.0001)
    }

    @Test
    fun aCutOffRecordingShowsDroppedSentences() {
        val half = TestScript.TEXT.substring(0, TestScript.TEXT.length / 2)
        val s = SpeechScorer.score(TestScript.TEXT, half)
        assertTrue(s.sentencesDropped >= 14)
        assertTrue(s.werPercent > 40)
    }

    @Test
    fun aMisheardNameIsOneSubstitution() {
        val s = SpeechScorer.score("She asked when I am coming to Buldhana.", "she asked when i am coming to buldana")
        assertEquals(1, s.substitutions)
        assertEquals(0, s.deletions)
        assertEquals(0, s.sentencesDropped)
    }
}
