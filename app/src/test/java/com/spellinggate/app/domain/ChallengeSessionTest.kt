package com.spellinggate.app.domain

import com.spellinggate.app.model.SpellingWord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChallengeSessionTest {
    private val words = listOf(
        SpellingWord("calendar"),
        SpellingWord("necessary"),
        SpellingWord("conscientious"),
    )

    @Test
    fun incorrectAnswerDoesNotAdvance() {
        val session = ChallengeSession.start(words)
        val submission = session.submit("calender")

        assertEquals(SubmissionResult.INCORRECT, submission.result)
        assertEquals(0, submission.session.currentWordIndex)
        assertEquals("calendar", submission.session.currentWord.spelling)
    }

    @Test
    fun correctAnswerAdvancesAndUsesExistingAnswerRules() {
        val session = ChallengeSession.start(words)
        val submission = session.submit("  CALENDAR  ")

        assertEquals(SubmissionResult.CORRECT, submission.result)
        assertEquals(1, submission.session.currentWordIndex)
        assertEquals("necessary", submission.session.currentWord.spelling)
        assertFalse(submission.session.isComplete)
    }

    @Test
    fun finalCorrectAnswerCompletesChallenge() {
        var session = ChallengeSession.start(words)
        session = session.submit("calendar").session
        session = session.submit("necessary").session
        val finalSubmission = session.submit("conscientious")

        assertEquals(SubmissionResult.COMPLETE, finalSubmission.result)
        assertTrue(finalSubmission.session.isComplete)
        assertEquals(3, finalSubmission.session.currentNumber)
    }

    @Test
    fun restoredSessionResumesAtSavedWord() {
        val session = ChallengeSession.restore(words, currentWordIndex = 2)

        assertEquals(2, session.currentWordIndex)
        assertEquals(3, session.currentNumber)
        assertEquals("conscientious", session.currentWord.spelling)
    }

    @Test
    fun restoredCompleteSessionIsComplete() {
        val session = ChallengeSession.restore(words, currentWordIndex = words.size)

        assertTrue(session.isComplete)
        assertEquals(words, session.challengeWords)
    }
}
