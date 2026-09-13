package com.spellinggate.app.speech

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

enum class SpeechStatus {
    INITIALIZING,
    READY,
    LANGUAGE_UNAVAILABLE,
    ENGINE_UNAVAILABLE,
}

class AndroidTextToSpeechSpeaker(
    context: Context,
    private val onStatusChanged: (SpeechStatus) -> Unit,
    private val onSynthesisError: () -> Unit,
) : TextToSpeech.OnInitListener {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val utteranceCounter = AtomicLong(0)

    @Volatile
    private var currentStatus = SpeechStatus.INITIALIZING

    @Volatile
    private var isShutdown = false

    private var textToSpeech: TextToSpeech? = null

    init {
        notifyStatus(SpeechStatus.INITIALIZING)

        try {
            textToSpeech = TextToSpeech(context.applicationContext, this)
        } catch (exception: Exception) {
            Log.e(TAG, "Speech: Failed to initialize TTS engine", exception)
            notifyStatus(SpeechStatus.ENGINE_UNAVAILABLE)
        }
    }

    override fun onInit(status: Int) {
        mainHandler.post {
            if (isShutdown) return@post

            if (status != TextToSpeech.SUCCESS) {
                Log.e(TAG, "Speech: TTS engine initialization returned an error")
                notifyStatus(SpeechStatus.ENGINE_UNAVAILABLE)
                return@post
            }

            val engine = textToSpeech
            if (engine == null) {
                Log.e(TAG, "Speech: TTS engine was unavailable after initialization")
                notifyStatus(SpeechStatus.ENGINE_UNAVAILABLE)
                return@post
            }

            val selectedVoice = selectRegionalEnglishVoice(engine)
            val language = selectedVoice?.locale ?: Locale.US
            val languageResult = engine.setLanguage(language)
            if (
                languageResult == TextToSpeech.LANG_MISSING_DATA ||
                languageResult == TextToSpeech.LANG_NOT_SUPPORTED
            ) {
                Log.e(TAG, "Speech: English voice data is unavailable")
                notifyStatus(SpeechStatus.LANGUAGE_UNAVAILABLE)
                return@post
            }
            if (selectedVoice != null) {
                engine.voice = selectedVoice
                Log.d(TAG, "Speech: using voice ${selectedVoice.name} (${selectedVoice.locale})")
            } else {
                Log.d(TAG, "Speech: regional English voice unavailable; using engine default")
            }

            engine.setSpeechRate(SPEECH_RATE)
            engine.setOnUtteranceProgressListener(
                @Suppress("OVERRIDE_DEPRECATION")
                object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) = Unit

                    override fun onDone(utteranceId: String?) = Unit

                    override fun onError(utteranceId: String?) {
                        notifySynthesisError()
                    }

                    override fun onError(utteranceId: String?, errorCode: Int) {
                        notifySynthesisError()
                    }
                },
            )

            Log.d(TAG, "Speech: TTS engine ready with ${language.toLanguageTag()}")
            notifyStatus(SpeechStatus.READY)
        }
    }

    private fun selectRegionalEnglishVoice(engine: TextToSpeech): Voice? {
        val voices = engine.voices ?: return null
        val candidates = voices.filter { voice ->
            voice.locale.language == "en" && !voice.isNetworkConnectionRequired
        }
        return candidates.minByOrNull { voice ->
            when (voice.locale.country.uppercase(Locale.ROOT)) {
                "PK" -> 0
                "IN" -> 1
                "GB" -> 2
                "US" -> 3
                else -> 4
            }
        }
    }

    fun speak(word: String): Boolean {
        if (isShutdown || currentStatus != SpeechStatus.READY) return false

        val engine = textToSpeech ?: return false
        val utteranceId = "spelling-gate-${utteranceCounter.incrementAndGet()}"
        return engine.speak(
            word,
            TextToSpeech.QUEUE_FLUSH,
            null,
            utteranceId,
        ) == TextToSpeech.SUCCESS
    }

    fun shutdown() {
        if (isShutdown) return
        isShutdown = true
        mainHandler.removeCallbacksAndMessages(null)
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
    }

    private fun notifyStatus(status: SpeechStatus) {
        currentStatus = status
        mainHandler.post {
            if (!isShutdown) {
                onStatusChanged(status)
            }
        }
    }

    private fun notifySynthesisError() {
        mainHandler.post {
            if (!isShutdown) {
                onSynthesisError()
            }
        }
    }

    private companion object {
        const val TAG = "SpellingGate"
        const val SPEECH_RATE = 0.85f
    }
}
