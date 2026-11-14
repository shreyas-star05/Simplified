package com.dusk.app.security

import android.os.SystemClock
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Asks for fingerprint or the phone PIN when Dusk opens, and again after a minute in the background.
 * If the phone has no screen lock at all, there is nothing to ask for: the lock screen says so.
 */
object AppLock {
    const val RELOCK_AFTER_MS = 60_000L
    private const val AUTHENTICATORS = BIOMETRIC_STRONG or DEVICE_CREDENTIAL

    var locked by mutableStateOf(true)
        private set
    var message by mutableStateOf<String?>(null)
        private set

    /** Tests turn this off so the system prompt does not cover the screen they are checking. */
    var autoPrompt = true

    private var backgroundedAt = 0L

    fun phoneHasLock(activity: FragmentActivity): Boolean =
        BiometricManager.from(activity).canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS

    fun onStart(enabled: Boolean) {
        if (!enabled) { locked = false; return }
        if (backgroundedAt != 0L && SystemClock.elapsedRealtime() - backgroundedAt > RELOCK_AFTER_MS) locked = true
        backgroundedAt = 0L
    }

    fun onStop() { backgroundedAt = SystemClock.elapsedRealtime() }

    fun lockNow() { locked = true }

    fun unlockWithoutPrompt() { locked = false; message = null }

    fun prompt(activity: FragmentActivity) {
        message = null
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    locked = false
                    message = null
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    message = if (errorCode == BiometricPrompt.ERROR_USER_CANCELED || errorCode == BiometricPrompt.ERROR_CANCELED) null else errString.toString()
                }
            },
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock Dusk")
                .setSubtitle("Your journal is locked")
                .setAllowedAuthenticators(AUTHENTICATORS)
                .build(),
        )
    }
}
