package com.example

import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarViewWeek
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.DatabaseBackupManager
import com.example.data.WorkLogEntity
import com.example.data.auth.AuthState
import com.example.data.sync.SyncStatus
import com.example.ui.WorkLogViewModel
import com.example.ui.components.AddEditLogDialog
import com.example.ui.components.PayrollSummaryExportDialog
import com.example.ui.components.SettingsDialog
import com.example.ui.screens.AccountScreen
import com.example.ui.screens.BiometricLockScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.WeeklySummaryScreen
import com.example.ui.theme.WorkLogTheme
import kotlinx.coroutines.launch
import java.time.LocalDate

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WorkLogTheme {
                WorkLogApp()
            }
        }
    }
}

private enum class Tab(val label: String, val icon: ImageVector, val tag: String) {
    Overview("Overview", Icons.Default.Dashboard, "tab_dashboard"),
    Shifts("Shifts", Icons.Default.ReceiptLong, "tab_history"),
    Weekly("Weekly", Icons.Default.CalendarViewWeek, "tab_weekly"),
    Account("Account", Icons.Default.Person, "tab_account")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkLogApp(viewModel: WorkLogViewModel = viewModel()) {
    val userSettings by viewModel.userSettings.collectAsStateWithLifecycle()
    val allLogs by viewModel.allLogs.collectAsStateWithLifecycle()
    val weeklySummary by viewModel.currentWeeklySummary.collectAsStateWithLifecycle()
    val monthlySummary by viewModel.currentMonthlySummary.collectAsStateWithLifecycle()

    val selectedWeekStart by viewModel.selectedWeekStart.collectAsStateWithLifecycle()
    val selectedMonth by viewModel.selectedMonth.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedWorkplaceFilter by viewModel.selectedWorkplaceFilter.collectAsStateWithLifecycle()
    val monthlyWorkplaceBreakdown by viewModel.monthlyWorkplaceBreakdown.collectAsStateWithLifecycle()

    val authState by viewModel.authState.collectAsStateWithLifecycle()
    val syncStatus by viewModel.syncStatus.collectAsStateWithLifecycle()
    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
    val transientMessage by viewModel.transientMessage.collectAsStateWithLifecycle()

    val isAppLocked by viewModel.isAppLocked.collectAsStateWithLifecycle()
    val biometricStatus by viewModel.biometricStatus.collectAsStateWithLifecycle()
    val authErrorMessage by viewModel.authErrorMessage.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val showMessage: (String) -> Unit = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } }

    LaunchedEffect(transientMessage) {
        transientMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeTransientMessage()
        }
    }

    // Re-lock when returning from the background, if the user asked for that.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, userSettings.appLockEnabled, userSettings.requireAuthOnResume) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshBiometricStatus()
                if (userSettings.appLockEnabled && userSettings.requireAuthOnResume) {
                    viewModel.lockApp()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var selectedTab by remember { mutableIntStateOf(0) }
    var showAddEditDialog by remember { mutableStateOf(false) }
    var editingLog by remember { mutableStateOf<WorkLogEntity?>(null) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }

    val exportBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            val ok = DatabaseBackupManager.writeToUri(context, uri, viewModel.exportBackupJson())
            viewModel.setAuthErrorMessage(null)
            showMessage(if (ok) "Backup file saved." else "Could not save the backup file.")
        }
    }

    val importBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val json = DatabaseBackupManager.readFromUri(context, uri)
            val ok = !json.isNullOrBlank() && viewModel.restoreFromBackupJson(json)
            showMessage(if (ok) "Backup file imported." else "Could not read that backup file.")
        }
    }

    if (isAppLocked && userSettings.appLockEnabled) {
        BiometricLockScreen(
            userSettings = userSettings,
            biometricStatus = biometricStatus,
            authErrorMessage = authErrorMessage,
            onUnlockSuccess = { viewModel.unlockApp() },
            onAuthError = { viewModel.setAuthErrorMessage(it) },
            onVerifyPin = { pin -> viewModel.verifySecurityPin(pin) }
        )
        return
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.ReceiptLong,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Earninglogs",
                                fontFamily = FontFamily.Serif,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 18.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Shift & Overtime Payroll",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    if (userSettings.appLockEnabled) {
                        IconButton(
                            onClick = { viewModel.lockApp() },
                            modifier = Modifier.testTag("quick_lock_button")
                        ) {
                            Icon(
                                Icons.Default.Lock,
                                contentDescription = "Lock app",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    SyncChip(
                        syncStatus = syncStatus,
                        isOnline = isOnline,
                        onClick = { selectedTab = Tab.Account.ordinal }
                    )

                    IconButton(
                        onClick = { showSettingsDialog = true },
                        modifier = Modifier.testTag("settings_button")
                    ) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                Tab.entries.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = {
                            Text(
                                tab.label,
                                fontSize = 11.sp,
                                fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        modifier = Modifier.testTag(tab.tag)
                    )
                }
            }
        },
        floatingActionButton = {
            if (selectedTab != Tab.Account.ordinal) {
                ExtendedFloatingActionButton(
                    onClick = {
                        editingLog = null
                        showAddEditDialog = true
                    },
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.testTag("add_log_fab"),
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("Log Shift", fontWeight = FontWeight.Bold) }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Crossfade(
                targetState = selectedTab,
                label = "TabSwitchAnimation",
                modifier = Modifier.fillMaxSize()
            ) { tab ->
                when (tab) {
                    Tab.Overview.ordinal -> DashboardScreen(
                        monthlySummary = monthlySummary,
                        weeklySummary = weeklySummary,
                        workplaceBreakdown = monthlyWorkplaceBreakdown,
                        selectedWorkplaceFilter = selectedWorkplaceFilter,
                        selectedMonth = selectedMonth,
                        userSettings = userSettings,
                        onSelectWorkplaceFilter = viewModel::selectWorkplaceFilter,
                        onAddWorkplace = viewModel::addWorkplace,
                        onRenameWorkplace = viewModel::renameWorkplace,
                        onNavigateMonth = viewModel::navigateMonth,
                        onJumpToCurrentMonth = viewModel::jumpToCurrentMonth,
                        onAddLogClick = {
                            editingLog = null
                            showAddEditDialog = true
                        },
                        onEditLogClick = { log ->
                            editingLog = log
                            showAddEditDialog = true
                        },
                        onDeleteLogClick = viewModel::deleteLog,
                        onTogglePaidClick = viewModel::togglePaidStatus,
                        onPopulateSampleClick = viewModel::populateSampleData,
                        onViewAllShiftsClick = { selectedTab = Tab.Shifts.ordinal },
                        onViewWeeklyTabClick = { selectedTab = Tab.Weekly.ordinal }
                    )

                    Tab.Shifts.ordinal -> HistoryScreen(
                        allLogs = allLogs,
                        searchQuery = searchQuery,
                        userSettings = userSettings,
                        onSearchQueryChange = viewModel::setSearchQuery,
                        onEditLogClick = { log ->
                            editingLog = log
                            showAddEditDialog = true
                        },
                        onDeleteLogClick = viewModel::deleteLog,
                        onTogglePaidClick = viewModel::togglePaidStatus,
                        onAddLogClick = {
                            editingLog = null
                            showAddEditDialog = true
                        }
                    )

                    Tab.Weekly.ordinal -> WeeklySummaryScreen(
                        weeklySummary = weeklySummary,
                        selectedWeekStart = selectedWeekStart,
                        userSettings = userSettings,
                        onNavigateWeek = viewModel::navigateWeek,
                        onJumpToCurrentWeek = viewModel::jumpToCurrentWeek,
                        onAddLogClick = {
                            editingLog = null
                            showAddEditDialog = true
                        },
                        onEditLogClick = { log ->
                            editingLog = log
                            showAddEditDialog = true
                        },
                        onDeleteLogClick = viewModel::deleteLog,
                        onExportPayrollClick = { showExportDialog = true }
                    )

                    Tab.Account.ordinal -> AccountScreen(
                        authState = authState,
                        userSettings = userSettings,
                        syncStatus = syncStatus,
                        isOnline = isOnline,
                        onSignIn = { viewModel.signInWithGoogle(context) },
                        onSignOut = { wipe -> viewModel.signOut(wipe) },
                        onSyncNow = viewModel::syncNow,
                        onRestore = viewModel::restoreFromCloud,
                        onToggleAutoSync = viewModel::toggleAutoCloudSync
                    )
                }
            }
        }
    }

    if (showAddEditDialog) {
        AddEditLogDialog(
            initialLog = editingLog,
            userSettings = userSettings,
            onDismiss = { showAddEditDialog = false },
            onSave = { log ->
                if (log.id == 0) viewModel.addLog(log) else viewModel.updateLog(log)
                showAddEditDialog = false
            }
        )
    }

    if (showSettingsDialog) {
        SettingsDialog(
            userSettings = userSettings,
            onDismiss = { showSettingsDialog = false },
            onSaveSettings = viewModel::saveSettings,
            onSetPin = viewModel::setSecurityPin,
            onPopulateSampleData = viewModel::populateSampleData,
            onClearAllData = viewModel::clearAllLogs,
            onAddWorkplace = viewModel::addWorkplace,
            onRenameWorkplace = viewModel::renameWorkplace,
            onRemoveWorkplace = viewModel::removeWorkplace,
            onExportBackup = { exportBackupLauncher.launch("earninglogs_backup_${LocalDate.now()}.json") },
            onImportBackup = { importBackupLauncher.launch("*/*") },
            onRestoreAutoBackup = {
                val ok = viewModel.restoreFromAutoBackup()
                showMessage(if (ok) "Restored the local auto-backup." else "No local auto-backup found.")
            }
        )
    }

    if (showExportDialog) {
        PayrollSummaryExportDialog(
            summary = weeklySummary,
            currencySymbol = userSettings.currencySymbol,
            onDismiss = { showExportDialog = false }
        )
    }
}

@Composable
private fun SyncChip(
    syncStatus: SyncStatus,
    isOnline: Boolean,
    onClick: () -> Unit
) {
    val (icon, label) = when {
        !isOnline && syncStatus !is SyncStatus.NotSignedIn -> Icons.Default.CloudOff to "Offline"
        syncStatus is SyncStatus.NotSignedIn -> Icons.Default.CloudOff to "Local"
        syncStatus is SyncStatus.Syncing -> Icons.Default.CloudSync to "Syncing"
        syncStatus is SyncStatus.Pending -> Icons.Default.CloudQueue to "${syncStatus.count}"
        syncStatus is SyncStatus.Failed -> Icons.Default.ErrorOutline to "Failed"
        syncStatus is SyncStatus.Synced -> Icons.Default.CloudDone to "Saved"
        else -> Icons.Default.CloudQueue to "Local"
    }

    val isError = syncStatus is SyncStatus.Failed
    val container = if (isError) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val content = if (isError) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = container,
        modifier = Modifier
            .padding(end = 4.dp)
            .clickable(onClick = onClick)
            .testTag("sync_status_chip")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = "Backup status", tint = content, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = content)
        }
    }
}

