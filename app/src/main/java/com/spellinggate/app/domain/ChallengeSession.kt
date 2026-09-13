package com.spellinggate.app.domain

import com.spellinggate.app.model.SpellingWord

class ChallengeSession private constructor(
    private val words: List<SpellingWord>,
    val currentWordIndex: Int,
) {
    init {
        require(words.isNotEmpty()) { "A challenge must contain at least one word." }
        require(currentWordIndex in 0..words.size) {
            "Current word index is outside the challenge."
        }
    }

    val totalWords: Int
        get() = words.size

    val isComplete: Boolean
        get() = currentWordIndex == words.size

    val currentNumber: Int
        get() = (currentWordIndex + 1).coerceAtMost(totalWords)

    val currentWord: SpellingWord
        get() = checkNotNull(words.getOrNull(currentWordIndex)) {
            "A completed challenge has no current word."
        }

    fun submit(answer: String): ChallengeSubmission {
        if (!AnswerChecker.isCorrect(answer, currentWord)) {
            return ChallengeSubmission(
                result = SubmissionResult.INCORRECT,
                session = this,
            )
        }

        val advancedSession = ChallengeSession(
            words = words,
            currentWordIndex = currentWordIndex + 1,
        )
        val result = if (advancedSession.isComplete) {
            SubmissionResult.COMPLETE
        } else {
            SubmissionResult.CORRECT
        }

        return ChallengeSubmission(
            result = result,
            session = advancedSession,
        )
    }

    companion object {
        fun start(words: List<SpellingWord>): ChallengeSession =
            ChallengeSession(
                words = words.toList(),
                currentWordIndex = 0,
            )
    }
}

data class ChallengeSubmission(
    val result: SubmissionResult,
    val session: ChallengeSession,
)

enum class SubmissionResult {
    INCORRECT,
    CORRECT,
    COMPLETE,
}
