package com.spellinggate.app.domain

import com.spellinggate.app.data.LocalWordRepository
import java.util.Locale
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Test

class ChallengeWordSelectorTest {
    @Test
    fun selectsRequestedNumberWithoutDuplicateSpellings() {
        val selectedWords = ChallengeWordSelector(random = Random(1234)).select(
            words = LocalWordRepository().getAllWords(),
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
            words = LocalWordRepository().getAllWords(),
            count = 21,
        )
    }
}
