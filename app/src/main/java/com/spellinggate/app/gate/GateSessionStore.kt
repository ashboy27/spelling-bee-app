package com.spellinggate.app.gate

import android.content.Context
import com.spellinggate.app.data.AssetWordRepository
import com.spellinggate.app.domain.ChallengeSession
import com.spellinggate.app.domain.ChallengeWordSelector
import com.spellinggate.app.model.SpellingWord
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

class GateSessionStore(context: Context) {
    private val repository = AssetWordRepository(context)
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    @Synchronized
    fun loadOrPrepareGate(): ChallengeSession? {
        if (!isLocked()) prepareNewGate()
        return preferences.getString(KEY_SESSION, null)?.let { readSession(it) }
    }

    @Synchronized
    fun prepareNewGate() {
        check(
            preferences.edit()
                .putString(KEY_STATE, GateState.LOCKED.name)
                .remove(KEY_SESSION)
                .commit(),
        ) { "Could not prepare a new gate session." }
    }

    @Synchronized
    fun startChallenge(): ChallengeSession {
        val words = ChallengeWordSelector().select(
            words = repository.getAllWords(),
            count = CHALLENGE_SIZE,
        )
        val session = ChallengeSession.start(words)
        writeLockedSession(session)
        return session
    }

    @Synchronized
    fun saveProgress(session: ChallengeSession) {
        check(isLocked()) { "Cannot save progress after the gate is released." }
        writeLockedSession(session)
    }

    @Synchronized
    fun replaceCurrentWord(session: ChallengeSession): ChallengeSession {
        check(isLocked()) { "Cannot replace a word after the gate is released." }
        val currentSpelling = session.currentWord.spelling.lowercase(Locale.ROOT)
        val usedSpellings = session.challengeWords
            .map { it.spelling.lowercase(Locale.ROOT) }
            .toSet()
        val candidates = repository.getAllWords().filter { word ->
            word.spelling.lowercase(Locale.ROOT) !in usedSpellings &&
                !word.spelling.equals(currentSpelling, ignoreCase = true)
        }
        check(candidates.isNotEmpty()) { "No unused replacement words are available." }
        val replacement = candidates.random()
        val updatedSession = session.replaceCurrentWord(replacement)
        writeLockedSession(updatedSession)
        return updatedSession
    }

    @Synchronized
    fun release() {
        check(
            preferences.edit()
                .putString(KEY_STATE, GateState.RELEASED.name)
                .remove(KEY_SESSION)
                .commit(),
        ) { "Could not persist the released gate state." }
    }

    fun isLocked(): Boolean =
        preferences.getString(KEY_STATE, GateState.RELEASED.name) == GateState.LOCKED.name

    private fun readSession(rawSession: String): ChallengeSession {
        val json = JSONObject(rawSession)
        val wordArray = json.getJSONArray(JSON_WORDS)
        val replacementCountArray = json.optJSONArray(JSON_REPLACEMENT_COUNTS)
        val words = buildList {
            for (index in 0 until wordArray.length()) {
                val value = wordArray.get(index)
                val spelling = if (value is JSONObject) {
                    value.getString(JSON_SPELLING)
                } else {
                    value.toString()
                }
                add(SpellingWord(spelling = spelling))
            }
        }
        check(words.size == CHALLENGE_SIZE) {
            "The saved challenge must contain exactly $CHALLENGE_SIZE words."
        }
        check(words.map { it.spelling.lowercase(Locale.ROOT) }.distinct().size == words.size) {
            "The saved challenge contains duplicate words."
        }
        val replacementCounts = List(words.size) { index ->
            replacementCountArray?.optInt(index, 0) ?: 0
        }
        return ChallengeSession.restore(
            words = words,
            currentWordIndex = json.getInt(JSON_CURRENT_INDEX),
            replacementCounts = replacementCounts,
        )
    }

    private fun writeLockedSession(session: ChallengeSession) {
        val wordsJson = JSONArray().apply {
            session.challengeWords.forEach { word ->
                put(word.spelling)
            }
        }
        val replacementCountsJson = JSONArray().apply {
            session.replacementCounts.forEach { put(it) }
        }
        val sessionJson = JSONObject()
            .put(JSON_CURRENT_INDEX, session.currentWordIndex)
            .put(JSON_WORDS, wordsJson)
            .put(JSON_REPLACEMENT_COUNTS, replacementCountsJson)

        check(
            preferences.edit()
                .putString(KEY_STATE, GateState.LOCKED.name)
                .putString(KEY_SESSION, sessionJson.toString())
                .commit(),
        ) { "Could not persist the spelling challenge." }
    }

    private enum class GateState {
        LOCKED,
        RELEASED,
    }

    companion object {
        private const val PREFERENCES_NAME = "spelling_gate_session"
        private const val KEY_STATE = "gate_state"
        private const val KEY_SESSION = "challenge_session"
        private const val JSON_CURRENT_INDEX = "currentWordIndex"
        private const val JSON_WORDS = "words"
        private const val JSON_REPLACEMENT_COUNTS = "replacementCounts"
        private const val JSON_SPELLING = "spelling"
        const val CHALLENGE_SIZE = 10

    }
}
