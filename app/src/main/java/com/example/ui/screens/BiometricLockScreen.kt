package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.example.ui.theme.AppColors
import com.example.data.UserSettings
import com.example.util.BiometricAuthManager
import com.example.util.BiometricStatus

@Composable
fun BiometricLockScreen(
    userSettings: UserSettings,
    biometricStatus: BiometricStatus,
    authErrorMessage: String?,
    onUnlockSuccess: () -> Unit,
    onAuthError: (String) -> Unit,
    onVerifyPin: (String) -> Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity

    var showPinDialog by remember { mutableStateOf(false) }
    var pinInput by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf(false) }
    var pinVisible by remember { mutableStateOf(false) }

    // Pulsing animation for the biometric fingerprint badge
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    fun launchBiometricPrompt() {
        if (activity != null) {
            BiometricAuthManager.promptBiometricAuthentication(
                activity = activity,
                title = "Unlock Work Log & Payroll",
                subtitle = "Authenticate to access sensitive work records",
                description = "Touch the fingerprint sensor or use face/device PIN",
                negativeButtonText = "Use Passcode",
                allowDeviceCredentials = true,
                onSuccess = {
                    onUnlockSuccess()
                },
                onError = { errorCode, errString ->
                    // Code 13 (ERROR_NEGATIVE_BUTTON) or 10 (ERROR_USER_CANCELED)
                    if (errorCode == androidx.biometric.BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                        showPinDialog = true
                    } else {
                        onAuthError(errString.toString())
                    }
                },
                onFailed = {
                    onAuthError("Biometric verification failed. Please try again.")
                }
            )
        } else {
            showPinDialog = true
        }
    }

    // Automatically prompt for biometric authentication once when lock screen appears
    LaunchedEffect(Unit) {
        launchBiometricPrompt()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AppColors.background)
            .padding(24.dp)
            .testTag("biometric_lock_screen"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Animated Biometric Shield / Fingerprint Container
            Box(
                modifier = Modifier
                    .size(140.dp)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                AppColors.accent,
                                AppColors.hero
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                // Secondary decorative ring
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Fingerprint,
                        contentDescription = "Biometric Lock",
                        tint = Color.White,
                        modifier = Modifier.size(72.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            // Primary Biometric Unlock Button
            Button(
                onClick = { launchBiometricPrompt() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("biometric_unlock_button"),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppColors.accent
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Fingerprint,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Unlock with Biometric",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Fallback PIN, only offered when the user has actually set one. Previously this
            // was always shown and always openable, because a hardcoded master PIN was accepted
            // whether or not the user had configured anything.
            if (userSettings.hasPin) {
                OutlinedButton(
                    onClick = { showPinDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("pin_unlock_button"),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, AppColors.cardBorder)
                ) {
                    Icon(
                        imageVector = Icons.Default.Password,
                        contentDescription = null,
                        tint = AppColors.textPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Use PIN instead",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AppColors.textPrimary
                    )
                }
            }
        }
    }

    // In-App PIN Dialog
    if (showPinDialog) {
        AlertDialog(
            onDismissRequest = {
                showPinDialog = false
                pinInput = ""
                pinError = false
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Key,
                        contentDescription = null,
                        tint = AppColors.accent,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Enter Security PIN",
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Serif
                    )
                }
            },
            text = {
                Column {
                    Text(
                        text = "Enter the PIN you set for this app:",
                        fontSize = 13.sp,
                        color = AppColors.textSecondary
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = {
                            pinInput = it.take(8)
                            pinError = false
                        },
                        label = { Text("Passcode / PIN") },
                        visualTransformation = if (pinVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.NumberPassword,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                if (onVerifyPin(pinInput)) {
                                    showPinDialog = false
                                    pinInput = ""
                                    onUnlockSuccess()
                                } else {
                                    pinError = true
                                }
                            }
                        ),
                        trailingIcon = {
                            IconButton(onClick = { pinVisible = !pinVisible }) {
                                Icon(
                                    imageVector = if (pinVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = null,
                                    tint = AppColors.textSecondary
                                )
                            }
                        },
                        isError = pinError,
                        supportingText = {
                            if (pinError) {
                                Text("Incorrect PIN. Please try again.", color = MaterialTheme.colorScheme.error)
                            }
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AppColors.accent,
                            focusedLabelColor = AppColors.accent
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("pin_input_field")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (onVerifyPin(pinInput)) {
                            showPinDialog = false
                            pinInput = ""
                            onUnlockSuccess()
                        } else {
                            pinError = true
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.accent),
                    modifier = Modifier.testTag("pin_submit_button")
                ) {
                    Text("Unlock", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showPinDialog = false
                        pinInput = ""
                        pinError = false
                    }
                ) {
                    Text("Cancel", color = AppColors.textSecondary)
                }
            },
            containerColor = AppColors.card,
            shape = RoundedCornerShape(20.dp)
        )
    }
}
