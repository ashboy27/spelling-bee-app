package com.spellinggate.app.model

data class SpellingWord(
    val spelling: String,
    val difficulty: WordDifficulty,
) {
    init {
        require(spelling.isNotBlank()) { "A spelling word cannot be blank." }
    }
}

enum class WordDifficulty {
    EASY,
    MEDIUM,
    HARD,
}
