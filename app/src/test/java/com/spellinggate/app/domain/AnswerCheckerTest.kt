package com.spellinggate.app.domain

import com.spellinggate.app.model.SpellingWord
import com.spellinggate.app.model.WordDifficulty
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnswerCheckerTest {
    private val expectedWord = SpellingWord(
        spelling = "conscientious",
        difficulty = WordDifficulty.HARD,
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
