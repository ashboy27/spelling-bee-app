package com.spellinggate.app.domain

import com.spellinggate.app.model.SpellingWord
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnswerCheckerTest {
    private val expectedWord = SpellingWord(
        spelling = "conscientious",
    )

    @Test
    fun exactSpellingIsCorrect() {
        assertTrue(AnswerChecker.isCorrect("conscientious", expectedWord))
    }

    @Test
    fun surroundingWhitespaceAndCapitalizationAreIgnored() {
        assertTrue(AnswerChecker.isCorrect("  CONSCIENTIOUS  ", expectedWord))
    }

    @Test
    fun misspellingIsIncorrect() {
        assertFalse(AnswerChecker.isCorrect("consciencious", expectedWord))
    }

    @Test
    fun internalWhitespaceIsNotIgnored() {
        assertFalse(AnswerChecker.isCorrect("con scientious", expectedWord))
    }
}
