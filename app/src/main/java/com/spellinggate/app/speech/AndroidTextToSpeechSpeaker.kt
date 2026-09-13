package com.spellinggate.app.speech

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
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

            val languageResult = engine.setLanguage(Locale.US)
            if (
                languageResult == TextToSpeech.LANG_MISSING_DATA ||
                languageResult == TextToSpeech.LANG_NOT_SUPPORTED
            ) {
                Log.e(TAG, "Speech: English (United States) is unavailable")
                notifyStatus(SpeechStatus.LANGUAGE_UNAVAILABLE)
                return@post
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

            Log.d(TAG, "Speech: TTS engine ready with English (United States)")
            notifyStatus(SpeechStatus.READY)
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
