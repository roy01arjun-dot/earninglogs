package com.example.util

import android.content.Context
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

enum class BiometricStatus(val isAvailable: Boolean) {
    AVAILABLE(true),
    NONE_ENROLLED(false),
    NO_HARDWARE(false),
    HARDWARE_UNAVAILABLE(false),
    SECURITY_UPDATE_REQUIRED(false),
    UNSUPPORTED(false)
}

object BiometricAuthManager {

    /**
     * The same authenticator set is used for checking and for prompting.
     *
     * Bug fixed: on Android 9 and lower the check accepted "screen lock only" phones, but the prompt
     * then allowed fingerprints only, so a user without a fingerprint (and without an app PIN) was
     * locked out of their own data. STRONG|DEVICE_CREDENTIAL isn't supported before Android 11,
     * so older versions use WEAK|DEVICE_CREDENTIAL for both the check and the prompt.
     */
    private val authenticators: Int
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            BIOMETRIC_STRONG or DEVICE_CREDENTIAL
        } else {
            BIOMETRIC_WEAK or DEVICE_CREDENTIAL
        }

    fun checkBiometricAvailability(context: Context): BiometricStatus =
        when (BiometricManager.from(context).canAuthenticate(authenticators)) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricStatus.AVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricStatus.NONE_ENROLLED
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> BiometricStatus.NO_HARDWARE
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> BiometricStatus.HARDWARE_UNAVAILABLE
            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED -> BiometricStatus.SECURITY_UPDATE_REQUIRED
            else -> BiometricStatus.UNSUPPORTED
        }

    fun promptBiometricAuthentication(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        onSuccess: () -> Unit,
        onError: (errorCode: Int, errString: CharSequence) -> Unit,
        onFailed: () -> Unit
    ) {
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onError(errorCode, errString)
                override fun onAuthenticationFailed() = onFailed()
            }
        )
        // With DEVICE_CREDENTIAL allowed a negative button must NOT be set; the system offers "Use PIN".
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(authenticators)
            .build()
        try {
            prompt.authenticate(info)
        } catch (e: Exception) {
            onError(BiometricPrompt.ERROR_UNABLE_TO_PROCESS, e.localizedMessage ?: "Couldn't start the unlock prompt")
        }
    }
}
