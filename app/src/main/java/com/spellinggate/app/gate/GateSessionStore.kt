package com.spellinggate.app.gate

import android.content.Context
import com.spellinggate.app.data.AssetWordRepository
import com.spellinggate.app.domain.ChallengeSession
import com.spellinggate.app.domain.ChallengeWordSelector
import com.spellinggate.app.model.SpellingWord
import com.spellinggate.app.model.WordDifficulty
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
        val words = buildList {
            for (index in 0 until wordArray.length()) {
                val word = wordArray.getJSONObject(index)
                add(
                    SpellingWord(
                        spelling = word.getString(JSON_SPELLING),
                        difficulty = WordDifficulty.valueOf(word.getString(JSON_DIFFICULTY)),
                    ),
                )
            }
        }
        check(words.size == CHALLENGE_SIZE) {
            "The saved challenge must contain exactly $CHALLENGE_SIZE words."
        }
        check(words.map { it.spelling.lowercase(Locale.ROOT) }.distinct().size == words.size) {
            "The saved challenge contains duplicate words."
        }
        return ChallengeSession.restore(
            words = words,
            currentWordIndex = json.getInt(JSON_CURRENT_INDEX),
        )
    }

    private fun writeLockedSession(session: ChallengeSession) {
        val wordsJson = JSONArray().apply {
            session.challengeWords.forEach { word ->
                put(
                    JSONObject()
                        .put(JSON_SPELLING, word.spelling)
                        .put(JSON_DIFFICULTY, word.difficulty.name),
                )
            }
        }
        val sessionJson = JSONObject()
            .put(JSON_CURRENT_INDEX, session.currentWordIndex)
            .put(JSON_WORDS, wordsJson)

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
        private const val JSON_SPELLING = "spelling"
        private const val JSON_DIFFICULTY = "difficulty"
        const val CHALLENGE_SIZE = 10

    }
}
