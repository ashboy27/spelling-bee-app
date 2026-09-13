# Spelling Gate — Technical Journal

## Goal

Spelling Gate is a personal Android practice app that presents a mandatory 10-word spelling challenge after the user unlocks the phone. The user must either complete all words or enter the bypass password before normal phone use resumes. It preserves Android's normal PIN/fingerprint lock screen and runs afterward as a second exercise layer.

The current personal-phone implementation is a **soft gate**, not a fully managed-device kiosk. It uses an application overlay and foreground service so it can restore the challenge after Home, Recents, or app switching without factory-resetting the phone or provisioning Device Owner.

## Platform and build

- Native Android, Kotlin, Jetpack Compose, Gradle Kotlin DSL.
- Package/application ID: `com.spellinggate.app`.
- `minSdk 26`, `targetSdk 35`, `compileSdk 35`.
- App version: `1.0.0`.
- Java/Kotlin bytecode target: JVM 17; development machine uses JDK 21.
- Debug builds use a debug-only `android:testOnly="true"` manifest overlay. Release builds are explicitly non-debuggable.
- User installs through Android Studio Run; no custom signing keystore is required for debug testing.

## Word and challenge engine

- Editable word bank: `app/src/main/assets/words.json`.
- `AssetWordRepository` parses a plain JSON string array with `org.json`, trims entries, rejects blanks, rejects an empty bank, and rejects case-insensitive duplicates.
- `ChallengeWordSelector` randomly selects exactly 10 unique strings exclusively from the JSON bank; there is no difficulty metadata or weighting.
- Each challenge position supports up to 3 persisted replacements. “New Word” draws an unused word from the full JSON bank, keeps the same position, clears the answer, and automatically speaks the replacement.
- `AnswerChecker` trims surrounding whitespace and compares case-insensitively.
- `ChallengeSession` is immutable. It tracks selected words and current index, supports start/restore, and returns `INCORRECT`, `CORRECT`, or `COMPLETE` submission results.
- Incorrect answers do not advance. Correct answers persist progress before UI advancement.

## Session lifecycle and persistence

- `GateSessionStore` uses synchronous `SharedPreferences.commit()` for gate-critical state.
- Persistent states are `LOCKED` and `RELEASED`.
- A phone unlock calls `prepareNewGate()`, which marks the gate locked but deliberately does **not** select words.
- The Activity initially displays a Start Challenge screen. Calling `startChallenge()` selects and persists the 10 random words.
- Saved sessions include the exact word list, difficulties, and current index, allowing process/activity recreation to resume the same challenge.
- Corrupt/missing word data fails open: the gate is released rather than trapping the user.

## UI and Text-to-Speech

- `MainActivity` owns the Compose UI and blocks Back while `GateSessionStore.isLocked()` is true.
- Initial unlock UI offers **Start Challenge** and **Use Password Instead**.
- TTS is not created until Start Challenge is pressed, preventing the first word from being spoken before the user is ready.
- `AndroidTextToSpeechSpeaker` uses Android `TextToSpeech`, preferring installed offline English voices in `en-PK`, then `en-IN`, `en-GB`, and `en-US` order. It falls back safely when a regional voice is unavailable. It also provides automatic first pronunciation, replay support, initialization/error states, and lifecycle shutdown.
- Speech is tuned for spelling clarity with a slower `0.70` rate and maximum TTS utterance volume (`1.0`); the phone's media-volume setting remains the final loudness limit.
- The word itself is not displayed in the challenge UI.
- Each challenge word has an Admin Reveal action protected by the existing bypass password. A successful reveal shows the current spelling in a separate admin card and supports hiding it again; changing words resets the reveal state.
- The spelling input uses password-class IME input with `autoCorrectEnabled = false`, requesting that the active Android keyboard suppress autocomplete, prediction, and spelling corrections. No password visual transformation is applied, so typed letters remain visible.
- After the tenth correct answer or valid password, the gate state is released, overlays/lock-task are stopped, and `finishAndRemoveTask()` closes the app.

### Visual system

- The UI uses a restrained **Eucalyptus + Warm Paper** palette rather than a default blue/purple app theme.
- Light scheme: eucalyptus `#176B57` primary, warm paper `#F7F3E8` background, warm surface `#FFFBF2`, ink `#202521`, and soft sage `#D4E8DF` containers.
- Dark scheme: light eucalyptus `#83D9BD` primary, deep leaf `#0D392F`, night background `#101B18`, and night surface `#182520`.
- Clay `#AA493D` is reserved for errors. No decorative accent colors are added.
- Screens use one rounded, elevated content surface, consistent 52 dp primary actions, outlined secondary actions, keyboard-safe spacing, and scroll support on smaller displays.
- The challenge screen follows a hierarchy of progress header, listening card, secondary action row, and answer section; related actions are grouped with equal-width controls and generous spacing to avoid a crowded vertical button stack.
- The Activity uses `windowSoftInputMode="adjustResize"`; `imePadding`, vertical scrolling, and Compose `BringIntoViewRequester` keep the focused input plus Unlock/Submit actions visible above the on-screen keyboard.
- Android 12+ launch splash uses the user-provided `res/drawable-nodpi/spelling_splash.jpg` with the warm-paper (or night-leaf) background instead of the default Android app icon.

## Password bypass

- Current bypass password: `ashar`.
- `BypassPasswordVerifier` stores no plaintext password. It verifies a salted PBKDF2-HMAC-SHA256 hash with 120,000 iterations and constant-time `MessageDigest.isEqual()` comparison.
- Password input buffers/specifications are cleared after derivation and authentication values are never logged.

## Personal-phone soft gate

- `PersonalGateService` is a sticky foreground service with a persistent low-priority notification.
- It dynamically receives protected `ACTION_USER_PRESENT` broadcasts.
- On unlock it prepares a pending gate, displays a full-screen `TYPE_APPLICATION_OVERLAY`, and launches/returns `MainActivity` to the foreground.
- If the Activity stops while the gate is locked, it asks the service to show the blocking overlay and restore the Activity.
- The user must manually grant `SYSTEM_ALERT_WINDOW` through `ACTION_MANAGE_OVERLAY_PERMISSION` on first launch.
- Android 14+ foreground-service declarations use the `specialUse` type and `FOREGROUND_SERVICE_SPECIAL_USE` permission.
- `SpellingGateBootReceiver` restarts monitoring after boot when personal-gate monitoring was previously enabled.
- On Xiaomi, practical reliability may also require enabling Autostart and setting battery handling to No restrictions.

This soft gate is intentionally recoverable: disabling overlay permission, stopping the service through Android Settings, safe mode, or ADB can defeat it. An ordinary personal-device app cannot absolutely suppress System UI/Home without Device Owner.

## Device Owner infrastructure

Device-management support is implemented but not activated:

- `SpellingGateDeviceAdminReceiver` and `device_admin_receiver.xml` declare the admin component.
- `DevicePolicyController` can allowlist the package with `setLockTaskPackages()` and enter/exit true Lock Task Mode when the app is Device Owner.
- Lock-task features preserve global power actions while withholding Home, Overview, notifications, and other app launches.
- `SpellingGateDeviceAdminService` contains the Device Owner unlock path using `ACTION_USER_PRESENT` and atomic lock-task Activity launch.

Do not provision Device Owner on the current Xiaomi installation. The device is already provisioned and has an XSpace secondary user (`999`), which violates normal fully-managed-device test prerequisites and can create a difficult recovery situation.

## Manifest permissions/components

- `RECEIVE_BOOT_COMPLETED`
- `SYSTEM_ALERT_WINDOW`
- `FOREGROUND_SERVICE`
- `FOREGROUND_SERVICE_SPECIAL_USE`
- Main activity uses `singleTask`, `excludeFromRecents`, and `lockTaskMode="if_whitelisted"`.
- TTS service discovery is declared in `<queries>`.
- Application backup is disabled.

## Verification completed

- Full Kotlin/Compose source compiled against Android SDK 35 and cached Compose dependencies.
- Password hash verified: `ashar` succeeds; the previous password and incorrect values fail.
- Domain checks covered random uniqueness, incorrect/correct progression, and session restoration passed.
- Main/debug manifests and device-admin XML parsed successfully.
- JSON contains 20 valid words; challenge size is 10.
- Gradle execution inside Codex is blocked by its loopback-socket sandbox, so final APK installation/runtime testing is performed through Android Studio. The user has confirmed the personal overlay gate works on the phone.
