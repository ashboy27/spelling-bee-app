package com.spellinggate.app.domain

import com.spellinggate.app.model.SpellingWord

object AnswerChecker {
    fun isCorrect(answer: String, expectedWord: SpellingWord): Boolean =
        answer.trim().equals(expectedWord.spelling, ignoreCase = true)
}
