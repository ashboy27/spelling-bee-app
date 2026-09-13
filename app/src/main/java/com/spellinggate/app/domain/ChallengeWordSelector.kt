package com.spellinggate.app.domain

import com.spellinggate.app.model.SpellingWord
import java.util.Locale
import kotlin.random.Random

class ChallengeWordSelector(
    private val random: Random = Random.Default,
) {
    fun select(words: List<SpellingWord>, count: Int): List<SpellingWord> {
        val uniqueWords = words.distinctBy { it.spelling.lowercase(Locale.ROOT) }

        require(count > 0) { "Challenge word count must be greater than zero." }
        require(count <= uniqueWords.size) {
            "Requested $count words, but only ${uniqueWords.size} unique words are available."
        }

        return uniqueWords.shuffled(random).take(count)
    }
}
