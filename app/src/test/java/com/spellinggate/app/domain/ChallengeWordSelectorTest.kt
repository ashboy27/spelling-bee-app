package com.spellinggate.app.domain

import com.spellinggate.app.model.SpellingWord
import com.spellinggate.app.model.WordDifficulty
import java.util.Locale
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Test

class ChallengeWordSelectorTest {
    private val testWords = (1..12).map { index ->
        SpellingWord("word$index", WordDifficulty.EASY)
    }

    @Test
    fun selectsRequestedNumberWithoutDuplicateSpellings() {
        val selectedWords = ChallengeWordSelector(random = Random(1234)).select(
            words = testWords,
            count = 10,
        )

        val uniqueSpellings = selectedWords
            .map { it.spelling.lowercase(Locale.ROOT) }
            .toSet()

        assertEquals(10, selectedWords.size)
        assertEquals(selectedWords.size, uniqueSpellings.size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsRequestLargerThanUniqueWordBank() {
        ChallengeWordSelector(random = Random(1234)).select(
            words = testWords,
            count = 13,
        )
    }
}
