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

enum class BiometricStatus(val isAvailable: Boolean, val description: String) {
    AVAILABLE(true, "Biometric sensor & security ready"),
    DEVICE_CREDENTIAL_ONLY(true, "Device PIN / Pattern / Password available"),
    NONE_ENROLLED(false, "No biometric or screen lock enrolled"),
    NO_HARDWARE(false, "No biometric hardware present"),
    HARDWARE_UNAVAILABLE(false, "Biometric hardware currently unavailable"),
    SECURITY_UPDATE_REQUIRED(false, "Security update required for biometric sensor"),
    UNSUPPORTED(false, "Biometric authentication unsupported")
}

object BiometricAuthManager {

    fun checkBiometricAvailability(context: Context): BiometricStatus {
        val biometricManager = BiometricManager.from(context)
        
        // Check strong biometric or device credentials (PIN/Pattern/Password)
        val authenticators = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            BIOMETRIC_STRONG or DEVICE_CREDENTIAL
        } else {
            BIOMETRIC_WEAK or DEVICE_CREDENTIAL
        }

        return when (biometricManager.canAuthenticate(authenticators)) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricStatus.AVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricStatus.NONE_ENROLLED
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> {
                // If no biometric hardware, check if device credential alone works
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                    biometricManager.canAuthenticate(DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS
                ) {
                    BiometricStatus.DEVICE_CREDENTIAL_ONLY
                } else {
                    BiometricStatus.NO_HARDWARE
                }
            }
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> BiometricStatus.HARDWARE_UNAVAILABLE
            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED -> BiometricStatus.SECURITY_UPDATE_REQUIRED
            BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED -> BiometricStatus.UNSUPPORTED
            else -> BiometricStatus.UNSUPPORTED
        }
    }

    fun promptBiometricAuthentication(
        activity: FragmentActivity,
        title: String = "Unlock Work Log",
        subtitle: String = "Verify identity to access payroll & work hours",
        description: String = "Touch the fingerprint sensor or use your face/screen lock",
        negativeButtonText: String = "Cancel",
        allowDeviceCredentials: Boolean = true,
        onSuccess: (BiometricPrompt.AuthenticationResult) -> Unit,
        onError: (errorCode: Int, errString: CharSequence) -> Unit,
        onFailed: () -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(activity)

        val biometricPrompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onSuccess(result)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    onError(errorCode, errString)
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    onFailed()
                }
            }
        )

        val promptInfoBuilder = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setDescription(description)

        // If allowing device credential, setAllowedAuthenticators.
        // Note: On Android 10 (API 29) & lower or if setAllowedAuthenticators includes DEVICE_CREDENTIAL,
        // setNegativeButtonText MUST NOT be called.
        if (allowDeviceCredentials && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            promptInfoBuilder.setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
        } else if (allowDeviceCredentials && Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
            @Suppress("DEPRECATION")
            promptInfoBuilder.setDeviceCredentialAllowed(true)
        } else {
            promptInfoBuilder.setNegativeButtonText(negativeButtonText)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                promptInfoBuilder.setAllowedAuthenticators(BIOMETRIC_STRONG)
            }
        }

        try {
            biometricPrompt.authenticate(promptInfoBuilder.build())
        } catch (e: Exception) {
            // If device credential combination fails on specific vendor ROMs, fallback to standard negative button
            try {
                val fallbackBuilder = BiometricPrompt.PromptInfo.Builder()
                    .setTitle(title)
                    .setSubtitle(subtitle)
                    .setDescription(description)
                    .setNegativeButtonText(negativeButtonText)
                biometricPrompt.authenticate(fallbackBuilder.build())
            } catch (fallbackEx: Exception) {
                onError(BiometricPrompt.ERROR_UNABLE_TO_PROCESS, fallbackEx.localizedMessage ?: "Authentication failed to start")
            }
        }
    }
}
