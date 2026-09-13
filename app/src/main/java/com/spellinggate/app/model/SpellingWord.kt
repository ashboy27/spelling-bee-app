package com.spellinggate.app.model

data class SpellingWord(
    val spelling: String,
) {
    init {
        require(spelling.isNotBlank()) { "A spelling word cannot be blank." }
    }
}
