package com.spellinggate.app.domain

import com.spellinggate.app.model.SpellingWord

class ChallengeSession private constructor(
    words: List<SpellingWord>,
    val currentWordIndex: Int,
) {
    val challengeWords: List<SpellingWord> = words.toList()

    init {
        require(challengeWords.isNotEmpty()) { "A challenge must contain at least one word." }
        require(currentWordIndex in 0..challengeWords.size) {
            "Current word index is outside the challenge."
        }
    }

    val totalWords: Int
        get() = challengeWords.size

    val isComplete: Boolean
        get() = currentWordIndex == challengeWords.size

    val currentNumber: Int
        get() = (currentWordIndex + 1).coerceAtMost(totalWords)

    val currentWord: SpellingWord
        get() = checkNotNull(challengeWords.getOrNull(currentWordIndex)) {
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
            words = challengeWords,
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

        fun restore(
            words: List<SpellingWord>,
            currentWordIndex: Int,
        ): ChallengeSession = ChallengeSession(
            words = words.toList(),
            currentWordIndex = currentWordIndex,
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
