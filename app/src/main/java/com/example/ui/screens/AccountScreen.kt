package com.example.ui.screens

import android.text.format.DateUtils as AndroidDateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.LockClock
import androidx.compose.material.icons.outlined.Password
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.heightIn
import com.example.data.AuthUserState
import com.example.data.SyncState
import com.example.data.SyncStatus
import com.example.data.UserSettings
import com.example.ui.components.AppCard
import com.example.ui.components.Avatar
import com.example.ui.components.ControlShape
import com.example.ui.components.IconBadge
import com.example.ui.components.SettingRow
import com.example.ui.components.Spacing
import com.example.ui.components.StatusPill
import com.example.ui.components.SwitchRow
import com.example.ui.components.TextInputDialog
import com.example.ui.theme.AppTheme
import com.example.util.BiometricStatus

@Composable
fun AccountScreen(
    user: AuthUserState,
    settings: UserSettings,
    sync: SyncState,
    pendingCount: Int,
    isOnline: Boolean,
    cloudAvailable: Boolean,
    biometricStatus: BiometricStatus,
    contentPadding: PaddingValues,
    onSignIn: () -> Unit,
    onSignOut: (removeLocal: Boolean) -> Unit,
    onSyncNow: () -> Unit,
    onToggleAutoSync: (Boolean) -> Unit,
    onToggleLock: (Boolean) -> Unit,
    onToggleLockOnResume: (Boolean) -> Unit,
    onSetPin: (String?) -> Unit,
    canNotify: Boolean,
    onToggleReminder: (Boolean) -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenSettings: () -> Unit,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit,
    onRestoreAutoBackup: () -> Unit
) {
    var showSignOut by remember { mutableStateOf(false) }
    var showPinDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: ""
    }

    LazyColumn(
        contentPadding = PaddingValues(
            start = Spacing.screen, end = Spacing.screen,
            top = contentPadding.calculateTopPadding() + Spacing.sm,
            bottom = contentPadding.calculateBottomPadding() + Spacing.xl
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        // ---- Profile
        item {
            if (user.isAuthenticated) {
                AppCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(user.initials, size = 56.dp)
                        Spacer(Modifier.width(Spacing.lg))
                        Column(Modifier.weight(1f)) {
                            Text(user.displayName, style = MaterialTheme.typography.titleLarge)
                            Text(user.email, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(Spacing.xs))
                            StatusPill(
                                if (user.authProvider == "GOOGLE") "Google account" else "Email account",
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.primaryContainer
                            )
                        }
                    }
                }
            } else {
                AppCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(Icons.Outlined.CloudUpload, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primaryContainer, size = 48.dp, iconSize = 24.dp)
                        Spacer(Modifier.width(Spacing.lg))
                        Column(Modifier.weight(1f)) {
                            Text("Back up your shifts", style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (cloudAvailable) "Sign in to keep your data safe and use it on all your devices."
                                else "Cloud sync isn't configured in this build. Your data is stored on this device.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (cloudAvailable) {
                        Spacer(Modifier.height(Spacing.lg))
                        Button(onClick = onSignIn, shape = ControlShape, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                            Text("Sign in or create account")
                        }
                    }
                }
            }
        }

        // ---- Sync
        if (user.isAuthenticated) {
            item { SyncCard(sync, pendingCount, isOnline, settings, onSyncNow, onToggleAutoSync) }
        }

        // ---- Preferences
        item { GroupLabel("Pay") }
        item {
            AppCard(contentPadding = PaddingValues(0.dp)) {
                SettingRow(
                    icon = Icons.Outlined.Tune,
                    title = "Pay & workplaces",
                    subtitle = "Rate, overtime, currency, goal, tax, workplaces",
                    onClick = onOpenSettings
                )
            }
        }

        // ---- Reminders
        item { GroupLabel("Reminders") }
        item {
            AppCard(contentPadding = PaddingValues(0.dp)) {
                SwitchRow(
                    icon = Icons.Outlined.NotificationsActive,
                    title = "Daily reminder",
                    subtitle = "Between 11:00 and 11:59 PM, if you haven't logged a shift that day",
                    checked = settings.dailyReminderEnabled,
                    onCheckedChange = onToggleReminder
                )
                if (settings.dailyReminderEnabled && !canNotify) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SettingRow(
                        icon = Icons.Outlined.NotificationsOff,
                        title = "Notifications are turned off",
                        subtitle = "Tap to allow notifications for Earninglogs, or the reminder can't appear",
                        tint = MaterialTheme.colorScheme.error,
                        onClick = onOpenNotificationSettings
                    )
                }
            }
        }

        // ---- Security
        item { GroupLabel("Security") }
        item {
            AppCard(contentPadding = PaddingValues(0.dp)) {
                SwitchRow(
                    icon = Icons.Outlined.Fingerprint,
                    title = "App lock",
                    subtitle = if (biometricStatus.isAvailable) "Use fingerprint, face or screen lock"
                    else "Set up a screen lock on your phone to use this",
                    checked = settings.biometricLockEnabled,
                    onCheckedChange = onToggleLock,
                    enabled = biometricStatus.isAvailable || settings.hasPin || settings.biometricLockEnabled
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SwitchRow(
                    icon = Icons.Outlined.LockClock,
                    title = "Lock when I leave the app",
                    subtitle = "After 30 seconds in the background",
                    checked = settings.requireBiometricOnResume,
                    onCheckedChange = onToggleLockOnResume,
                    enabled = settings.biometricLockEnabled
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingRow(
                    icon = Icons.Outlined.Password,
                    title = if (settings.hasPin) "Change app PIN" else "Create app PIN",
                    subtitle = "Backup unlock if biometrics aren't available",
                    onClick = { showPinDialog = true }
                )
                if (settings.hasPin) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SettingRow(
                        icon = Icons.Outlined.Password,
                        title = "Remove app PIN",
                        titleColor = MaterialTheme.colorScheme.error,
                        onClick = { onSetPin(null) }
                    )
                }
            }
        }

        // ---- Data
        item { GroupLabel("Backup") }
        item {
            AppCard(contentPadding = PaddingValues(0.dp)) {
                SettingRow(Icons.Outlined.FileUpload, "Export backup", subtitle = "Save all shifts as a JSON file", onClick = onExportBackup)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingRow(Icons.Outlined.FileDownload, "Import backup", subtitle = "Merge shifts from a JSON file", onClick = onImportBackup)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingRow(Icons.Outlined.History, "Restore last export", subtitle = "Re-import the most recent backup made on this device", onClick = onRestoreAutoBackup)
            }
        }

        if (user.isAuthenticated) {
            item {
                AppCard(contentPadding = PaddingValues(0.dp)) {
                    SettingRow(
                        icon = Icons.AutoMirrored.Outlined.Logout,
                        title = "Sign out",
                        tint = MaterialTheme.colorScheme.error,
                        titleColor = MaterialTheme.colorScheme.error,
                        onClick = { showSignOut = true }
                    )
                }
            }
        }

        item {
            Row(Modifier.fillMaxWidth().padding(top = Spacing.md), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Info, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(Spacing.xs))
                Text("Earninglogs $version", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    if (showSignOut) {
        AlertDialog(
            onDismissRequest = { showSignOut = false },
            icon = { Icon(Icons.AutoMirrored.Outlined.Logout, null) },
            title = { Text("Sign out?") },
            text = {
                Text(
                    "Your shifts are safely stored in your account. You can keep a copy on this device, " +
                        "or remove it (recommended on a shared phone)."
                )
            },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    TextButton(onClick = { showSignOut = false; onSignOut(false) }) { Text("Sign out, keep data") }
                    TextButton(onClick = { showSignOut = false; onSignOut(true) }) {
                        Text("Sign out and remove data", color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(onClick = { showSignOut = false }) { Text("Cancel") }
                }
            }
        )
    }

    if (showPinDialog) {
        TextInputDialog(
            title = if (settings.hasPin) "Change app PIN" else "Create app PIN",
            label = "4–8 digit PIN",
            keyboardType = KeyboardType.NumberPassword,
            isSecret = true,
            validate = { if (it.length !in 4..8 || !it.all(Char::isDigit)) "Use 4 to 8 digits" else null },
            onConfirm = { onSetPin(it) },
            onDismiss = { showPinDialog = false }
        )
    }
}

@Composable
private fun GroupLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = Spacing.xs, top = Spacing.md).semantics { heading() }
    )
}

@Composable
private fun SyncCard(
    sync: SyncState,
    pendingCount: Int,
    isOnline: Boolean,
    settings: UserSettings,
    onSyncNow: () -> Unit,
    onToggleAutoSync: (Boolean) -> Unit
) {
    val status = AppTheme.status
    val (icon, tint, container, title) = when {
        sync.status == SyncStatus.SYNCING -> Quad(Icons.Outlined.CloudSync, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primaryContainer, "Syncing…")
        sync.status == SyncStatus.ERROR -> Quad(Icons.Outlined.ErrorOutline, MaterialTheme.colorScheme.error, MaterialTheme.colorScheme.errorContainer, "Sync problem")
        !isOnline || sync.status == SyncStatus.WAITING_FOR_NETWORK ->
            Quad(Icons.Outlined.CloudOff, status.warning, status.warningContainer, "Offline")
        sync.status == SyncStatus.PAUSED -> Quad(Icons.Outlined.CloudOff, MaterialTheme.colorScheme.onSurfaceVariant, MaterialTheme.colorScheme.surfaceVariant, "Auto-sync paused")
        sync.status == SyncStatus.NOT_CONFIGURED -> Quad(Icons.Outlined.CloudOff, MaterialTheme.colorScheme.onSurfaceVariant, MaterialTheme.colorScheme.surfaceVariant, "Not configured")
        pendingCount > 0 -> Quad(Icons.Outlined.CloudUpload, status.warning, status.warningContainer, "$pendingCount change${if (pendingCount == 1) "" else "s"} waiting")
        else -> Quad(Icons.Outlined.CloudDone, status.success, status.successContainer, "Up to date")
    }

    AppCard(contentPadding = PaddingValues(0.dp)) {
        Column(Modifier.padding(Spacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(icon, tint, container)
                Spacer(Modifier.width(Spacing.md))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (sync.lastSyncedAt > 0) "Last synced " + AndroidDateUtils.getRelativeTimeSpanString(
                            sync.lastSyncedAt, System.currentTimeMillis(), AndroidDateUtils.MINUTE_IN_MILLIS
                        ) else "Not synced yet",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (sync.status == SyncStatus.SYNCING) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                }
            }
            if (sync.message.isNotBlank() && sync.status != SyncStatus.SYNCED) {
                Spacer(Modifier.height(Spacing.md))
                Text(
                    sync.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (sync.status == SyncStatus.ERROR) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(Spacing.md))
            if (sync.status == SyncStatus.ERROR) {
                Button(onClick = onSyncNow, shape = ControlShape, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Sync, null, Modifier.size(18.dp)); Spacer(Modifier.width(Spacing.sm)); Text("Try again")
                }
            } else {
                FilledTonalButton(
                    onClick = onSyncNow,
                    enabled = sync.status != SyncStatus.SYNCING,
                    shape = ControlShape,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Outlined.Sync, null, Modifier.size(18.dp)); Spacer(Modifier.width(Spacing.sm)); Text("Sync now")
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        SwitchRow(
            icon = Icons.Outlined.CloudSync,
            title = "Automatic sync",
            subtitle = "Upload changes and receive updates in real time",
            checked = settings.autoCloudSyncEnabled,
            onCheckedChange = onToggleAutoSync
        )
    }
}

private data class Quad(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val tint: androidx.compose.ui.graphics.Color,
    val container: androidx.compose.ui.graphics.Color,
    val title: String
)
