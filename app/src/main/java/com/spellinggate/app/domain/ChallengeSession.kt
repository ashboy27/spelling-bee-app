package com.spellinggate.app.domain

import com.spellinggate.app.model.SpellingWord

class ChallengeSession private constructor(
    words: List<SpellingWord>,
    val currentWordIndex: Int,
    replacementCounts: List<Int>,
) {
    val challengeWords: List<SpellingWord> = words.toList()
    val replacementCounts: List<Int> = replacementCounts.toList()

    init {
        require(challengeWords.isNotEmpty()) { "A challenge must contain at least one word." }
        require(currentWordIndex in 0..challengeWords.size) {
            "Current word index is outside the challenge."
        }
        require(replacementCounts.size == challengeWords.size) {
            "Replacement counts must match the challenge word count."
        }
        require(replacementCounts.all { it in 0..MAX_REPLACEMENTS_PER_WORD }) {
            "A word cannot have more than $MAX_REPLACEMENTS_PER_WORD replacements."
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

    val currentWordReplacementCount: Int
        get() = replacementCounts.getOrNull(currentWordIndex) ?: 0

    fun replaceCurrentWord(newWord: SpellingWord): ChallengeSession {
        check(!isComplete) { "A completed challenge has no current word." }
        check(currentWordReplacementCount < MAX_REPLACEMENTS_PER_WORD) {
            "This word has reached its replacement limit."
        }
        require(!newWord.spelling.equals(currentWord.spelling, ignoreCase = true)) {
            "Replacement word must be different from the current word."
        }
        val updatedWords = challengeWords.toMutableList().apply {
            this[currentWordIndex] = newWord
        }
        val updatedCounts = replacementCounts.toMutableList().apply {
            this[currentWordIndex] = currentWordReplacementCount + 1
        }
        return ChallengeSession(updatedWords, currentWordIndex, updatedCounts)
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
            replacementCounts = replacementCounts,
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
                replacementCounts = List(words.size) { 0 },
            )

        fun restore(
            words: List<SpellingWord>,
            currentWordIndex: Int,
            replacementCounts: List<Int> = List(words.size) { 0 },
        ): ChallengeSession = ChallengeSession(
            words = words.toList(),
            currentWordIndex = currentWordIndex,
            replacementCounts = replacementCounts,
        )

        const val MAX_REPLACEMENTS_PER_WORD = 3
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
