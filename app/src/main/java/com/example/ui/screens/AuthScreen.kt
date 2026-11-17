package com.example.ui.screens

import android.app.Activity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.heightIn
import com.example.ui.components.AppCard
import com.example.ui.components.ControlShape
import com.example.ui.components.Spacing
import com.example.ui.theme.AppTheme

/**
 * Clean sign-in / sign-up page. Replaces the old dialog and the fake "device session",
 * fake Google button and default-PIN hints.
 */
@Composable
fun AuthScreen(
    cloudAvailable: Boolean,
    busy: Boolean,
    onSignIn: (email: String, password: String, onDone: (Boolean, String?) -> Unit) -> Unit,
    onSignUp: (email: String, password: String, name: String, onDone: (Boolean, String?) -> Unit) -> Unit,
    onGoogle: (activity: Activity, onDone: (Boolean, String?) -> Unit) -> Unit,
    onResetPassword: (email: String, onDone: (Boolean, String) -> Unit) -> Unit,
    onContinueOffline: (() -> Unit)?,
    onClose: (() -> Unit)?
) {
    val activity = androidx.activity.compose.LocalActivity.current
    val focus = LocalFocusManager.current
    val status = AppTheme.status

    var mode by rememberSaveable { mutableIntStateOf(0) } // 0 = sign in, 1 = create account
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var showPassword by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var info by rememberSaveable { mutableStateOf<String?>(null) }

    fun submit() {
        focus.clearFocus()
        error = null; info = null
        val done: (Boolean, String?) -> Unit = { ok, msg -> if (!ok) error = msg }
        if (mode == 0) onSignIn(email, password, done) else onSignUp(email, password, name, done)
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
            .imePadding()
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.xl, vertical = Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (onClose != null) {
                Row(Modifier.fillMaxWidth()) {
                    IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = "Close") }
                }
            } else {
                Spacer(Modifier.height(Spacing.xl))
            }

            // Brand
            Box(
                Modifier.size(76.dp).clip(RoundedCornerShape(26.dp)).background(AppTheme.heroBrush),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Payments, null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(38.dp))
            }
            Spacer(Modifier.height(Spacing.lg))
            Text("Earninglogs", style = MaterialTheme.typography.headlineMedium)
            Text(
                if (mode == 0) "Sign in to sync your shifts across devices" else "Create an account to back up your shifts",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(Spacing.xl))

            Column(Modifier.widthIn(max = 440.dp).fillMaxWidth()) {
                if (!cloudAvailable) {
                    AppCard(color = status.warningContainer, border = false) {
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(Icons.Outlined.CloudOff, null, tint = status.warning)
                            Spacer(Modifier.width(Spacing.md))
                            Text(
                                "Cloud accounts aren't configured in this build yet, so sign-in is unavailable. " +
                                    "Everything still works offline on this device.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                    Spacer(Modifier.height(Spacing.lg))
                }

                AppCard {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = mode == 0,
                            onClick = { mode = 0; error = null; info = null },
                            shape = SegmentedButtonDefaults.itemShape(0, 2)
                        ) { Text("Sign in") }
                        SegmentedButton(
                            selected = mode == 1,
                            onClick = { mode = 1; error = null; info = null },
                            shape = SegmentedButtonDefaults.itemShape(1, 2)
                        ) { Text("Create account") }
                    }
                    Spacer(Modifier.height(Spacing.lg))

                    AnimatedVisibility(visible = mode == 1) {
                        Column {
                            OutlinedTextField(
                                value = name,
                                onValueChange = { name = it; error = null },
                                label = { Text("Your name") },
                                leadingIcon = { Icon(Icons.Outlined.Person, null) },
                                singleLine = true,
                                enabled = cloudAvailable && !busy,
                                keyboardOptions = KeyboardOptions(
                                    capitalization = KeyboardCapitalization.Words,
                                    imeAction = ImeAction.Next
                                ),
                                keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) }),
                                shape = ControlShape,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(Spacing.md))
                        }
                    }

                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it.trim(); error = null },
                        label = { Text("Email") },
                        leadingIcon = { Icon(Icons.Outlined.Email, null) },
                        singleLine = true,
                        enabled = cloudAvailable && !busy,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) }),
                        shape = ControlShape,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(Spacing.md))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it; error = null },
                        label = { Text("Password") },
                        leadingIcon = { Icon(Icons.Outlined.Lock, null) },
                        trailingIcon = {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(
                                    if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (showPassword) "Hide password" else "Show password"
                                )
                            }
                        },
                        supportingText = if (mode == 1) { { Text("At least 6 characters") } } else null,
                        singleLine = true,
                        enabled = cloudAvailable && !busy,
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { if (!busy) submit() }),
                        shape = ControlShape,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (mode == 0) {
                        TextButton(
                            onClick = {
                                error = null
                                onResetPassword(email) { ok, msg -> if (ok) info = msg else error = msg }
                            },
                            enabled = cloudAvailable && !busy,
                            modifier = Modifier.align(Alignment.End)
                        ) { Text("Forgot password?") }
                    } else {
                        Spacer(Modifier.height(Spacing.md))
                    }

                    AnimatedVisibility(visible = error != null || info != null) {
                        val isError = error != null
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(bottom = Spacing.md)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isError) MaterialTheme.colorScheme.errorContainer else status.successContainer)
                                .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Assertive }
                                .padding(Spacing.md),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                Icons.Default.ErrorOutline, null,
                                tint = if (isError) MaterialTheme.colorScheme.error else status.success,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(Spacing.sm))
                            Text(
                                error ?: info ?: "",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Button(
                        onClick = { submit() },
                        enabled = cloudAvailable && !busy && email.isNotBlank() && password.isNotBlank(),
                        shape = ControlShape,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                    ) {
                        if (busy) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        } else {
                            Text(if (mode == 0) "Sign in" else "Create account")
                        }
                    }

                    Row(Modifier.padding(vertical = Spacing.lg), verticalAlignment = Alignment.CenterVertically) {
                        HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
                        Text(
                            "or",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = Spacing.md)
                        )
                        HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
                    }

                    OutlinedButton(
                        onClick = {
                            error = null; info = null
                            activity?.let { onGoogle(it) { ok, msg -> if (!ok && msg != null) error = msg } }
                        },
                        enabled = cloudAvailable && !busy && activity != null,
                        shape = ControlShape,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                    ) {
                        Icon(Icons.Outlined.AccountCircle, null)
                        Spacer(Modifier.width(Spacing.sm))
                        Text("Continue with Google")
                    }
                }

                if (onContinueOffline != null) {
                    Spacer(Modifier.height(Spacing.lg))
                    TextButton(
                        onClick = onContinueOffline,
                        enabled = !busy,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) { Text("Continue without an account") }
                    Text(
                        "Your shifts stay on this device. You can sign in later from the Account tab.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(Modifier.height(Spacing.xl))
            }
        }
    }
}
