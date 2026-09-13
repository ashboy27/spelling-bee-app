package com.spellinggate.app

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.spellinggate.app.data.LocalWordRepository
import com.spellinggate.app.domain.AnswerChecker
import com.spellinggate.app.domain.ChallengeWordSelector
import com.spellinggate.app.speech.AndroidTextToSpeechSpeaker
import com.spellinggate.app.speech.SpeechStatus
import com.spellinggate.app.ui.theme.SpellingGateTheme

private const val TAG = "SpellingGate"
private const val DEFAULT_CHALLENGE_SIZE = 10

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "Milestone 3 app started")

        setContent {
            SpellingGateTheme {
                SpellingGateApp()
            }
        }
    }
}

@Composable
private fun SpellingGateApp() {
    val context = LocalContext.current
    val challengeWords = remember {
        val repository = LocalWordRepository()
        ChallengeWordSelector().select(
            words = repository.getAllWords(),
            count = DEFAULT_CHALLENGE_SIZE,
        )
    }
    val currentWord = challengeWords.first()
    var answer by remember { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf("") }
    var statusIsError by remember { mutableStateOf(false) }
    var speechStatus by remember { mutableStateOf(SpeechStatus.INITIALIZING) }
    var speaker by remember { mutableStateOf<AndroidTextToSpeechSpeaker?>(null) }
    var automaticallySpokenWord by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(currentWord) {
        Log.d(TAG, "Challenge: Selected ${challengeWords.size} unique words")
        Log.d(TAG, "Challenge: Word 1")
    }

    DisposableEffect(context) {
        val textToSpeechSpeaker = AndroidTextToSpeechSpeaker(
            context = context,
            onStatusChanged = { newStatus ->
                speechStatus = newStatus
                Log.d(TAG, "Speech: Status changed to $newStatus")

                speechStatusMessage(newStatus)?.let { message ->
                    statusMessage = message
                    statusIsError = true
                }
            },
            onSynthesisError = {
                statusMessage = "The word could not be spoken. You can try Replay Word."
                statusIsError = true
                Log.e(TAG, "Speech: Synthesis failed")
            },
        )
        speaker = textToSpeechSpeaker

        onDispose {
            textToSpeechSpeaker.shutdown()
            Log.d(TAG, "Speech: Engine shut down")
        }
    }

    LaunchedEffect(speechStatus, speaker, currentWord) {
        if (
            speechStatus == SpeechStatus.READY &&
            automaticallySpokenWord != currentWord.spelling
        ) {
            if (speaker?.speak(currentWord.spelling) == true) {
                automaticallySpokenWord = currentWord.spelling
                Log.d(TAG, "Speech: Automatically speaking current word")
            } else {
                statusMessage = "The word could not be spoken. You can try Replay Word."
                statusIsError = true
                Log.e(TAG, "Speech: Automatic speak request failed")
            }
        }
    }

    SpellingChallengeScreen(
        currentWord = 1,
        totalWords = challengeWords.size,
        answer = answer,
        statusMessage = statusMessage,
        statusIsError = statusIsError,
        onAnswerChanged = { answer = it },
        onReplayWord = {
            when (speechStatus) {
                SpeechStatus.READY -> {
                    if (speaker?.speak(currentWord.spelling) == true) {
                        statusMessage = ""
                        statusIsError = false
                        Log.d(TAG, "Speech: Replaying current word")
                    } else {
                        statusMessage = "The word could not be spoken. Please try again."
                        statusIsError = true
                        Log.e(TAG, "Speech: Replay request failed")
                    }
                }

                SpeechStatus.INITIALIZING -> {
                    statusMessage = "Speech is still starting. Please try again."
                    statusIsError = false
                }

                SpeechStatus.LANGUAGE_UNAVAILABLE,
                SpeechStatus.ENGINE_UNAVAILABLE,
                -> {
                    statusMessage = speechStatusMessage(speechStatus).orEmpty()
                    statusIsError = true
                }
            }
        },
        onSubmit = {
            when {
                answer.isBlank() -> {
                    statusMessage = "Enter a spelling before submitting."
                    statusIsError = true
                    Log.d(TAG, "Challenge: Empty answer rejected")
                }

                AnswerChecker.isCorrect(answer, currentWord) -> {
                    statusMessage = "Correct ✓"
                    statusIsError = false
                    Log.d(TAG, "Challenge: Correct")
                }

                else -> {
                    statusMessage = "Incorrect. Try again."
                    statusIsError = true
                    Log.d(TAG, "Challenge: Incorrect")
                }
            }
        },
        onUsePassword = {
            statusMessage = "Password bypass will be added in a later milestone."
            statusIsError = false
            Log.d(TAG, "Gate: Password placeholder selected")
        },
    )
}

private fun speechStatusMessage(status: SpeechStatus): String? = when (status) {
    SpeechStatus.INITIALIZING,
    SpeechStatus.READY,
    -> null

    SpeechStatus.LANGUAGE_UNAVAILABLE ->
        "English (United States) speech data is unavailable on this phone."

    SpeechStatus.ENGINE_UNAVAILABLE ->
        "No working text-to-speech engine is available on this phone."
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SpellingChallengeScreen(
    currentWord: Int,
    totalWords: Int,
    answer: String,
    statusMessage: String,
    statusIsError: Boolean,
    onAnswerChanged: (String) -> Unit,
    onReplayWord: () -> Unit,
    onSubmit: () -> Unit,
    onUsePassword: () -> Unit,
) {
    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "SPELLING GATE",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
            )

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = "$currentWord / $totalWords",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )

            Spacer(modifier = Modifier.height(28.dp))

            Icon(
                imageVector = Icons.Rounded.VolumeUp,
                contentDescription = null,
                modifier = Modifier.height(56.dp),
                tint = MaterialTheme.colorScheme.primary,
            )

            Spacer(modifier = Modifier.height(12.dp))

            Button(onClick = onReplayWord) {
                Text("Replay Word")
            }

            Spacer(modifier = Modifier.height(28.dp))

            OutlinedTextField(
                value = answer,
                onValueChange = onAnswerChanged,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 440.dp),
                label = { Text("Enter spelling") },
                placeholder = { Text("Type what you hear") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onSubmit() }),
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onSubmit,
                modifier = Modifier.widthIn(min = 160.dp),
            ) {
                Text("Submit")
            }

            if (statusMessage.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = statusMessage,
                    modifier = Modifier.widthIn(max = 440.dp),
                    color = if (statusIsError) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            TextButton(onClick = onUsePassword) {
                Text("Use Password Instead")
            }
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun SpellingChallengeScreenPreview() {
    SpellingGateTheme {
        SpellingChallengeScreen(
            currentWord = 1,
            totalWords = 10,
            answer = "",
            statusMessage = "",
            statusIsError = false,
            onAnswerChanged = {},
            onReplayWord = {},
            onSubmit = {},
            onUsePassword = {},
        )
    }
}
