package com.spellinggate.app.security

import android.app.Activity
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.hardware.fingerprint.FingerprintManager
import android.os.Build
import android.os.CancellationSignal

object DeviceBiometricAuthenticator {
    fun isAvailable(activity: Activity): Boolean = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
            activity.getSystemService(BiometricManager::class.java)?.canAuthenticate() ==
                BiometricManager.BIOMETRIC_SUCCESS
        }
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P -> {
            @Suppress("DEPRECATION")
            activity.getSystemService(FingerprintManager::class.java)?.let {
                it.isHardwareDetected && it.hasEnrolledFingerprints()
            } == true
        }
        else -> false
    }

    fun authenticate(
        activity: Activity,
        title: String,
        subtitle: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P || !isAvailable(activity)) {
            onError("No enrolled fingerprint or biometric is available on this phone.")
            return
        }

        val executor = activity.mainExecutor
        val prompt = BiometricPrompt.Builder(activity)
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButton("Use password", executor) { _, _ -> Unit }
            .build()

        prompt.authenticate(
            CancellationSignal(),
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(
                    result: BiometricPrompt.AuthenticationResult?,
                ) {
                    onSuccess()
                }

                override fun onAuthenticationFailed() {
                    onError("Fingerprint not recognized. Try again.")
                }

                override fun onAuthenticationError(errorCode: Int, errorString: CharSequence?) {
                    if (
                        errorCode != BiometricPrompt.BIOMETRIC_ERROR_CANCELED &&
                        errorCode != BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED &&
                        errorCode != ERROR_NEGATIVE_BUTTON
                    ) {
                        onError(errorString?.toString() ?: "Biometric authentication failed.")
                    }
                }
            },
        )
    }

    private const val ERROR_NEGATIVE_BUTTON = 13
}
