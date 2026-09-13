package com.spellinggate.app.data

import android.content.Context
import com.spellinggate.app.model.SpellingWord
import java.util.Locale
import org.json.JSONArray

class AssetWordRepository(
    context: Context,
    private val assetFileName: String = DEFAULT_ASSET_FILE,
) : WordRepository {
    private val assets = context.applicationContext.assets

    override fun getAllWords(): List<SpellingWord> {
        try {
            val json = assets.open(assetFileName).bufferedReader().use { it.readText() }
            val array = JSONArray(json)
            val words = buildList {
                for (index in 0 until array.length()) {
                    val spelling = array.getString(index).trim()
                    if (spelling.isBlank()) {
                        throw WordBankException("Entry " + (index + 1) + " is blank.")
                    }
                    add(SpellingWord(spelling))
                }
            }

            if (words.isEmpty()) {
                throw WordBankException("The word list is empty.")
            }

            val duplicateSpellings = words
                .groupBy { it.spelling.lowercase(Locale.ROOT) }
                .filterValues { it.size > 1 }
                .keys
            if (duplicateSpellings.isNotEmpty()) {
                throw WordBankException(
                    "Duplicate words found: " + duplicateSpellings.sorted().joinToString(),
                )
            }

            return words
        } catch (exception: WordBankException) {
            throw exception
        } catch (exception: Exception) {
            throw WordBankException(
                "Could not load " + assetFileName + ": " +
                    (exception.message ?: "unknown error"),
                exception,
            )
        }
    }

    private companion object {
        const val DEFAULT_ASSET_FILE = "words.json"
    }
}

class WordBankException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
