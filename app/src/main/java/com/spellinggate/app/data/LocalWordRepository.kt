package com.spellinggate.app.data

import com.spellinggate.app.model.SpellingWord
import com.spellinggate.app.model.WordDifficulty

class LocalWordRepository : WordRepository {
    private val words = listOf(
        SpellingWord("beautiful", WordDifficulty.EASY),
        SpellingWord("calendar", WordDifficulty.EASY),
        SpellingWord("necessary", WordDifficulty.EASY),
        SpellingWord("separate", WordDifficulty.EASY),
        SpellingWord("rhythm", WordDifficulty.EASY),
        SpellingWord("accommodate", WordDifficulty.MEDIUM),
        SpellingWord("definitely", WordDifficulty.MEDIUM),
        SpellingWord("embarrass", WordDifficulty.MEDIUM),
        SpellingWord("existence", WordDifficulty.MEDIUM),
        SpellingWord("fluorescent", WordDifficulty.MEDIUM),
        SpellingWord("guarantee", WordDifficulty.MEDIUM),
        SpellingWord("independent", WordDifficulty.MEDIUM),
        SpellingWord("maintenance", WordDifficulty.MEDIUM),
        SpellingWord("millennium", WordDifficulty.MEDIUM),
        SpellingWord("occasion", WordDifficulty.MEDIUM),
        SpellingWord("privilege", WordDifficulty.MEDIUM),
        SpellingWord("acquaintance", WordDifficulty.HARD),
        SpellingWord("conscientious", WordDifficulty.HARD),
        SpellingWord("liaison", WordDifficulty.HARD),
        SpellingWord("questionnaire", WordDifficulty.HARD),
    )

    override fun getAllWords(): List<SpellingWord> = words
}
