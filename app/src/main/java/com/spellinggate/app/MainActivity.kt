package com.spellinggate.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material3.Button
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spellinggate.app.domain.ChallengeSession
import com.spellinggate.app.domain.SubmissionResult
import com.spellinggate.app.gate.DevicePolicyController
import com.spellinggate.app.gate.GateSessionStore
import com.spellinggate.app.gate.PersonalGateService
import com.spellinggate.app.security.BypassPasswordVerifier
import com.spellinggate.app.speech.AndroidTextToSpeechSpeaker
import com.spellinggate.app.speech.SpeechStatus
import com.spellinggate.app.ui.theme.SpellingGateTheme

private const val TAG = "SpellingGate"

class MainActivity : ComponentActivity() {
    private lateinit var sessionStore: GateSessionStore
    private lateinit var policyController: DevicePolicyController
    private var gateShown = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sessionStore = GateSessionStore(this)
        policyController = DevicePolicyController(this)

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (sessionStore.isLocked()) {
                        Log.d(TAG, "Gate: Back navigation blocked")
                    } else {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    }
                }
            },
        )
        if (Settings.canDrawOverlays(this)) {
            PersonalGateService.start(this)
            showGate()
        } else {
            showPersonalGateSetup()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        showGate()
    }

    override fun onResume() {
        super.onResume()
        if (!gateShown && Settings.canDrawOverlays(this)) {
            PersonalGateService.start(this)
            showGate()
        }
        if (sessionStore.isLocked()) {
            Log.d(TAG, "Gate: Lock task active=${policyController.enterLockTask(this)}")
        }
    }

    override fun onStart() {
        super.onStart()
        if (Settings.canDrawOverlays(this)) PersonalGateService.activityVisible(this)
    }

    override fun onStop() {
        super.onStop()
        if (
            gateShown && sessionStore.isLocked() && !isChangingConfigurations &&
            Settings.canDrawOverlays(this)
        ) {
            PersonalGateService.guard(this)
        }
    }

    private fun showGate() {
        gateShown = true
        val sessionResult = runCatching { sessionStore.loadOrPrepareGate() }
        if (sessionResult.isFailure) {
            Log.e(TAG, "Gate: Challenge could not be loaded", sessionResult.exceptionOrNull())
            runCatching { sessionStore.release() }
            policyController.releaseLockTask(this)
        }

        setContent {
            SpellingGateTheme {
                if (sessionResult.isSuccess) {
                    SpellingGateApp(
                        initialSession = sessionResult.getOrNull(),
                        onStart = sessionStore::startChallenge,
                        onProgress = sessionStore::saveProgress,
                        onRelease = ::releaseGate,
                    )
                } else WordBankErrorScreen(
                    sessionResult.exceptionOrNull()?.message ?: "Unknown word-bank error.",
                )
            }
        }
    }

    private fun releaseGate(reason: ReleaseReason): Boolean = runCatching {
        sessionStore.release()
        policyController.releaseLockTask(this)
        PersonalGateService.release(this)
        Log.d(TAG, "Gate: Released by $reason")
        finishAndRemoveTask()
    }.isSuccess

    private fun showPersonalGateSetup() {
        setContent {
            SpellingGateTheme {
                PersonalGateSetupScreen {
                    startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:$packageName"),
                        ),
                    )
                }
            }
        }
    }

    companion object {
        const val EXTRA_NEW_UNLOCK_EVENT = "com.spellinggate.app.extra.NEW_UNLOCK_EVENT"
    }
}

@Composable
private fun PersonalGateSetupScreen(onGrantPermission: () -> Unit) {
    Scaffold { padding ->
        GateColumn(padding.calculateTopPadding()) {
            Icon(Icons.Rounded.Lock, null, modifier = Modifier.height(64.dp))
            Spacer(Modifier.height(20.dp))
            Text("Enable Personal Gate", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(16.dp))
            Text(
                "Allow display over other apps so the spelling challenge can return after Home, Recents, or app switching.",
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = onGrantPermission) { Text("Grant Required Permission") }
        }
    }
}

private enum class ReleaseReason { CHALLENGE, PASSWORD, WORD_BANK_ERROR }

@Composable
private fun SpellingGateApp(
    initialSession: ChallengeSession?,
    onStart: () -> ChallengeSession,
    onProgress: (ChallengeSession) -> Unit,
    onRelease: (ReleaseReason) -> Boolean,
) {
    val context = LocalContext.current
    var session by remember(initialSession) { mutableStateOf(initialSession) }
    var answer by remember { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf("") }
    var statusIsError by remember { mutableStateOf(false) }
    var showPasswordScreen by remember { mutableStateOf(false) }
    var releaseReason by remember { mutableStateOf<ReleaseReason?>(null) }
    var password by remember { mutableStateOf("") }
    var passwordError by remember { mutableStateOf("") }

    releaseReason?.let {
        ReleasedScreen(it)
        return
    }

    if (showPasswordScreen) {
        val submitPassword: () -> Unit = {
            when {
                password.isEmpty() -> passwordError = "Enter the bypass password."
                !BypassPasswordVerifier.verify(password) -> {
                    passwordError = "Incorrect password."
                    Log.d(TAG, "Gate: Bypass password rejected")
                }
                onRelease(ReleaseReason.PASSWORD) -> {
                    password = ""
                    passwordError = ""
                    releaseReason = ReleaseReason.PASSWORD
                }
                else -> passwordError = "The gate could not be released. Please try again."
            }
        }
        PasswordBypassScreen(
            password,
            passwordError,
            onPasswordChanged = {
                password = it
                passwordError = ""
            },
            onSubmit = submitPassword,
            onBack = {
                password = ""
                passwordError = ""
                showPasswordScreen = false
            },
        )
        return
    }

    val activeSession = session
    if (activeSession == null) {
        StartChallengeScreen(
            onStart = {
                runCatching(onStart)
                    .onSuccess { session = it }
                    .onFailure {
                        Log.e(TAG, "Challenge: Word bank failed to load", it)
                        onRelease(ReleaseReason.WORD_BANK_ERROR)
                    }
            },
            onUsePassword = { showPasswordScreen = true },
        )
        return
    }

    val currentWord = activeSession.currentWord
    var speechStatus by remember { mutableStateOf(SpeechStatus.INITIALIZING) }
    var speaker by remember { mutableStateOf<AndroidTextToSpeechSpeaker?>(null) }
    var automaticallySpokenWord by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(activeSession.currentWordIndex) {
        Log.d(TAG, "Challenge: Word ${activeSession.currentNumber} of ${activeSession.totalWords}")
    }

    DisposableEffect(Unit) {
        val tts = AndroidTextToSpeechSpeaker(
            context = context,
            onStatusChanged = { newStatus ->
                speechStatus = newStatus
                speechStatusMessage(newStatus)?.let {
                    statusMessage = it
                    statusIsError = true
                }
            },
            onSynthesisError = {
                statusMessage = "The word could not be spoken. You can try Replay Word."
                statusIsError = true
            },
        )
        speaker = tts
        onDispose { tts.shutdown() }
    }

    LaunchedEffect(speechStatus, currentWord.spelling) {
        if (speechStatus == SpeechStatus.READY && automaticallySpokenWord != currentWord.spelling) {
            if (speaker?.speak(currentWord.spelling) == true) {
                automaticallySpokenWord = currentWord.spelling
            } else {
                statusMessage = "The word could not be spoken. You can try Replay Word."
                statusIsError = true
            }
        }
    }

    SpellingChallengeScreen(
        activeSession.currentNumber,
        activeSession.totalWords,
        answer,
        statusMessage,
        statusIsError,
        onAnswerChanged = { answer = it },
        onReplayWord = {
            when (speechStatus) {
                SpeechStatus.READY -> {
                    val spoken = speaker?.speak(currentWord.spelling) == true
                    statusMessage = if (spoken) "" else "The word could not be spoken. Please try again."
                    statusIsError = !spoken
                }
                SpeechStatus.INITIALIZING -> {
                    statusMessage = "Speech is still starting. Please try again."
                    statusIsError = false
                }
                else -> {
                    statusMessage = speechStatusMessage(speechStatus).orEmpty()
                    statusIsError = true
                }
            }
        },
        onSubmit = {
            if (answer.isBlank()) {
                statusMessage = "Enter a spelling before submitting."
                statusIsError = true
            } else {
                val submission = activeSession.submit(answer)
                when (submission.result) {
                    SubmissionResult.INCORRECT -> {
                        statusMessage = "Incorrect. Try again."
                        statusIsError = true
                    }
                    SubmissionResult.CORRECT -> runCatching {
                        onProgress(submission.session)
                    }.onSuccess {
                        session = submission.session
                        answer = ""
                        statusMessage = "Correct ✓"
                        statusIsError = false
                    }.onFailure {
                        statusMessage = "Progress could not be saved. Please try again."
                        statusIsError = true
                    }
                    SubmissionResult.COMPLETE -> {
                        if (onRelease(ReleaseReason.CHALLENGE)) {
                            answer = ""
                            releaseReason = ReleaseReason.CHALLENGE
                        } else {
                            statusMessage = "The gate could not be released. Submit again to retry."
                            statusIsError = true
                        }
                    }
                }
            }
        },
        onUsePassword = {
            password = ""
            passwordError = ""
            showPasswordScreen = true
        },
    )
}

@Composable
private fun PasswordBypassScreen(
    password: String,
    errorMessage: String,
    onPasswordChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    Scaffold { padding ->
        GateColumn(padding.calculateTopPadding()) {
            Icon(Icons.Rounded.Lock, null, modifier = Modifier.height(64.dp))
            Spacer(Modifier.height(20.dp))
            Text("Use Password Instead", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(24.dp))
            OutlinedTextField(
                value = password,
                onValueChange = onPasswordChanged,
                modifier = Modifier.fillMaxWidth().widthIn(max = 440.dp),
                label = { Text("Bypass password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { onSubmit() }),
                isError = errorMessage.isNotEmpty(),
                supportingText = if (errorMessage.isNotEmpty()) ({ Text(errorMessage) }) else null,
            )
            Spacer(Modifier.height(20.dp))
            Button(onClick = onSubmit) { Text("Unlock") }
            TextButton(onClick = onBack) { Text("Back to Spelling") }
        }
    }
}

@Composable
private fun ReleasedScreen(reason: ReleaseReason) {
    Scaffold { padding ->
        GateColumn(padding.calculateTopPadding()) {
            Icon(
                if (reason == ReleaseReason.CHALLENGE) Icons.Rounded.CheckCircle else Icons.Rounded.LockOpen,
                null,
                modifier = Modifier.height(72.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(24.dp))
            Text(
                if (reason == ReleaseReason.CHALLENGE) "Challenge Complete" else "Password Accepted",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(12.dp))
            Text("Spelling Gate released. Your phone is available.")
        }
    }
}

@Composable
private fun WordBankErrorScreen(errorMessage: String) {
    Scaffold { padding ->
        GateColumn(padding.calculateTopPadding()) {
            Text("Word Bank Error", color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(16.dp))
            Text("The challenge could not start. The app remains unrestricted.")
            Spacer(Modifier.height(12.dp))
            Text(errorMessage, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun speechStatusMessage(status: SpeechStatus): String? = when (status) {
    SpeechStatus.INITIALIZING, SpeechStatus.READY -> null
    SpeechStatus.LANGUAGE_UNAVAILABLE ->
        "English (United States) speech data is unavailable on this phone."
    SpeechStatus.ENGINE_UNAVAILABLE ->
        "No working text-to-speech engine is available on this phone."
}

@Composable
private fun StartChallengeScreen(
    onStart: () -> Unit,
    onUsePassword: () -> Unit,
) {
    BackHandler { }
    Scaffold { padding ->
        GateColumn(padding.calculateTopPadding()) {
            Icon(Icons.AutoMirrored.Rounded.VolumeUp, null, modifier = Modifier.height(64.dp))
            Spacer(Modifier.height(20.dp))
            Text("Spelling Gate", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(12.dp))
            Text(
                "Ready for a new 10-word challenge?",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(28.dp))
            Button(onClick = onStart) { Text("Start Challenge") }
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onUsePassword) { Text("Use Password Instead") }
        }
    }
}

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
    BackHandler { }
    Scaffold { padding ->
        GateColumn(padding.calculateTopPadding()) {
            Text(
                "SPELLING GATE",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
            )
            Spacer(Modifier.height(24.dp))
            Text("$currentWord / $totalWords", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(24.dp))
            Icon(Icons.AutoMirrored.Rounded.VolumeUp, null, modifier = Modifier.height(56.dp))
            Button(onClick = onReplayWord) { Text("Replay Word") }
            Spacer(Modifier.height(24.dp))
            OutlinedTextField(
                value = answer,
                onValueChange = onAnswerChanged,
                modifier = Modifier.fillMaxWidth().widthIn(max = 440.dp),
                label = { Text("Enter spelling") },
                placeholder = { Text("Type what you hear") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onSubmit() }),
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = onSubmit, modifier = Modifier.widthIn(min = 160.dp)) { Text("Submit") }
            if (statusMessage.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text(
                    statusMessage,
                    color = if (statusIsError) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(20.dp))
            TextButton(onClick = onUsePassword) { Text("Use Password Instead") }
        }
    }
}

@Composable
private fun GateColumn(topPadding: Dp, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = topPadding)
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) { content() }
}

@Preview(showBackground = true)
@Composable
private fun PreviewChallenge() {
    SpellingGateTheme {
        SpellingChallengeScreen(1, 10, "", "", false, {}, {}, {}, {})
    }
}
