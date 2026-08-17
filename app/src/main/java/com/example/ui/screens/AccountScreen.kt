package com.example.ui.screens

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.UserSettings
import com.example.data.auth.AuthState
import com.example.data.sync.SyncStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Account and backup screen.
 *
 * Replaces a screen that showed "Synced ✓ · Online" while the app was writing to a local file,
 * behind a sign-in dialog that listed three hardcoded email addresses and verified a one-time
 * code against itself. Everything here reflects real Firebase state.
 */
@Composable
fun AccountScreen(
    authState: AuthState,
    userSettings: UserSettings,
    syncStatus: SyncStatus,
    isOnline: Boolean,
    onSignIn: () -> Unit,
    onSignOut: (wipeLocal: Boolean) -> Unit,
    onSyncNow: () -> Unit,
    onRestore: () -> Unit,
    onToggleAutoSync: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var showSignOutDialog by remember { mutableStateOf(false) }
    var showRestoreDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        IdentityCard(authState = authState, onSignIn = onSignIn)

        Spacer(Modifier.height(12.dp))

        SyncStatusCard(
            syncStatus = syncStatus,
            isOnline = isOnline,
            lastSync = userSettings.lastCloudSyncTimestamp
        )

        if (authState is AuthState.SignedIn) {
            Spacer(Modifier.height(12.dp))

            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Automatic backup",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Upload each change as soon as you're online.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = userSettings.autoCloudSyncEnabled,
                            onCheckedChange = onToggleAutoSync
                        )
                    }

                    Spacer(Modifier.height(14.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(Modifier.height(14.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = onSyncNow,
                            enabled = syncStatus !is SyncStatus.Syncing,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Refresh, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Back up now")
                        }
                        OutlinedButton(
                            onClick = { showRestoreDialog = true },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Restore, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Restore")
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            TextButton(
                onClick = { showSignOutDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Logout, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Sign out")
            }
        }

        Spacer(Modifier.height(12.dp))

        Text(
            text = "Your shifts are always saved on this device first. Signing in adds an " +
                "encrypted-in-transit copy in your own Firebase account so you can move to a " +
                "new phone without losing history.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 18.sp,
            modifier = Modifier.padding(horizontal = 4.dp)
        )

        Spacer(Modifier.height(24.dp))
    }

    if (showSignOutDialog) {
        var wipeLocal by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { showSignOutDialog = false },
            icon = { Icon(Icons.Default.Logout, null) },
            title = { Text("Sign out?") },
            text = {
                Column {
                    Text("Your cloud copy stays in your account and you can sign back in any time.")
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = wipeLocal, onCheckedChange = { wipeLocal = it })
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Also delete the copy on this phone",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (wipeLocal) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "This device's shift history will be erased. If it hasn't finished " +
                                "backing up, that data is gone.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    onSignOut(wipeLocal)
                    showSignOutDialog = false
                }) { Text("Sign out") }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showRestoreDialog) {
        AlertDialog(
            onDismissRequest = { showRestoreDialog = false },
            icon = { Icon(Icons.Default.Restore, null) },
            title = { Text("Restore from cloud?") },
            text = {
                Text(
                    "Downloads every shift saved in your account and merges it with what's on " +
                        "this phone. Where the same shift exists in both, the most recently " +
                        "edited version wins."
                )
            },
            confirmButton = {
                Button(onClick = {
                    onRestore()
                    showRestoreDialog = false
                }) { Text("Restore") }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun IdentityCard(authState: AuthState, onSignIn: () -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(20.dp)) {
            when (authState) {
                is AuthState.SignedIn -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = authState.displayName.take(1).uppercase().ifBlank { "?" },
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp
                                )
                            }
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = authState.displayName.ifBlank { "Signed in" },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = authState.email,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }
                    }
                }

                AuthState.SignedOut -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Person,
                            null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Not signed in",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                "The app works fully offline. Sign in only if you want a backup.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = onSignIn,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.Login, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Text("Sign in with Google", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun SyncStatusCard(syncStatus: SyncStatus, isOnline: Boolean, lastSync: Long) {
    val (icon: ImageVector, title: String, detail: String) = when {
        !isOnline && syncStatus !is SyncStatus.NotSignedIn -> Triple(
            Icons.Default.CloudOff,
            "Offline",
            "Changes are saved here and will upload when you reconnect."
        )
        syncStatus is SyncStatus.NotSignedIn -> Triple(
            Icons.Default.CloudOff,
            "Backup off",
            "Sign in to keep a copy outside this phone."
        )
        syncStatus is SyncStatus.Syncing -> Triple(
            Icons.Default.CloudSync,
            "Backing up…",
            "Uploading your changes."
        )
        syncStatus is SyncStatus.Pending -> Triple(
            Icons.Default.CloudQueue,
            "${syncStatus.count} change${if (syncStatus.count == 1) "" else "s"} queued",
            "Waiting for a connection to upload."
        )
        syncStatus is SyncStatus.Failed -> Triple(
            Icons.Default.ErrorOutline,
            "Backup failed",
            syncStatus.message
        )
        syncStatus is SyncStatus.Synced -> Triple(
            Icons.Default.CloudDone,
            "Backed up",
            "Last upload ${formatTimestamp(syncStatus.at)}."
        )
        lastSync > 0L -> Triple(
            Icons.Default.CloudDone,
            "Backed up",
            "Last upload ${formatTimestamp(lastSync)}."
        )
        else -> Triple(
            Icons.Default.CloudQueue,
            "No backup yet",
            "Nothing has been uploaded from this device."
        )
    }

    val isError = syncStatus is SyncStatus.Failed
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isError) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            }
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (syncStatus is SyncStatus.Syncing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.5.dp
                )
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(26.dp),
                    tint = if (isError) {
                        MaterialTheme.colorScheme.onErrorContainer
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isError) {
                        MaterialTheme.colorScheme.onErrorContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isError) {
                        MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}

private fun formatTimestamp(millis: Long): String {
    if (millis <= 0L) return "never"
    val now = System.currentTimeMillis()
    val diff = now - millis
    return when {
        diff < 60_000 -> "just now"
        diff < 3_600_000 -> "${diff / 60_000} min ago"
        diff < 86_400_000 -> "${diff / 3_600_000} h ago"
        else -> SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()).format(Date(millis))
    }
}
