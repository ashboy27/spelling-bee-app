package com.spellinggate.app.data

import com.spellinggate.app.model.SpellingWord

interface WordRepository {
    fun getAllWords(): List<SpellingWord>
}
