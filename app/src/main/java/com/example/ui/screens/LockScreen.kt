package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.heightIn
import androidx.fragment.app.FragmentActivity
import com.example.ui.components.ControlShape
import com.example.ui.components.Spacing
import com.example.util.BiometricAuthManager
import com.example.util.BiometricStatus

/**
 * App lock. Uses the phone's own biometrics / screen lock. The optional in-app PIN is only
 * offered if the user actually created one (no more hard-coded "1234" / "0000" master PIN).
 */
@Composable
fun LockScreen(
    biometricStatus: BiometricStatus,
    hasPin: Boolean,
    onUnlocked: () -> Unit,
    verifyPin: (String) -> Boolean
) {
    val activity = androidx.activity.compose.LocalActivity.current as? FragmentActivity
    var message by remember { mutableStateOf<String?>(null) }
    var pinMode by remember { mutableStateOf(!biometricStatus.isAvailable && hasPin) }
    var pin by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf(false) }

    fun prompt() {
        if (activity == null || !biometricStatus.isAvailable) {
            if (hasPin) pinMode = true else onUnlocked() // nothing to verify with – never lock the user out
            return
        }
        message = null
        BiometricAuthManager.promptBiometricAuthentication(
            activity = activity,
            title = "Unlock Earninglogs",
            subtitle = "Use your fingerprint, face or phone screen lock",
            onSuccess = { onUnlocked() },
            onError = { code, err ->
                when (code) {
                    androidx.biometric.BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                    androidx.biometric.BiometricPrompt.ERROR_USER_CANCELED,
                    androidx.biometric.BiometricPrompt.ERROR_CANCELED -> Unit
                    else -> message = err.toString()
                }
            },
            onFailed = { message = "Not recognised. Try again." }
        )
    }

    LaunchedEffect(Unit) { if (!pinMode) prompt() }

    fun submitPin() {
        if (verifyPin(pin)) onUnlocked() else { pinError = true; pin = "" }
    }

    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).systemBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.widthIn(max = 380.dp).fillMaxWidth().padding(Spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                Modifier.size(72.dp).clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Lock, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(34.dp))
            }
            Spacer(Modifier.height(Spacing.xl))
            Text("Earninglogs is locked", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            Spacer(Modifier.height(Spacing.xs))
            Text(
                if (pinMode) "Enter your app PIN" else "Unlock with your fingerprint, face or screen lock",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(Spacing.xl))

            if (pinMode) {
                OutlinedTextField(
                    value = pin,
                    onValueChange = { v -> pin = v.filter(Char::isDigit).take(8); pinError = false },
                    label = { Text("PIN") },
                    isError = pinError,
                    supportingText = if (pinError) { { Text("Incorrect PIN") } } else null,
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submitPin() }),
                    shape = ControlShape,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(Spacing.md))
                Button(
                    onClick = { submitPin() },
                    enabled = pin.length >= 4,
                    shape = ControlShape,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                ) { Text("Unlock") }
                if (biometricStatus.isAvailable) {
                    TextButton(onClick = { pinMode = false; prompt() }) { Text("Use biometrics instead") }
                }
            } else {
                Button(
                    onClick = { prompt() },
                    shape = ControlShape,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                ) {
                    Icon(Icons.Default.Fingerprint, null)
                    Spacer(Modifier.size(Spacing.sm))
                    Text("Unlock")
                }
                if (hasPin) {
                    TextButton(onClick = { pinMode = true }) { Text("Use app PIN") }
                }
            }

            message?.let {
                Spacer(Modifier.height(Spacing.md))
                Text(
                    it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                )
            }
        }
    }
}
