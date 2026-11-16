package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.DatabaseBackupManager
import com.example.data.SyncStatus
import com.example.data.WorkLogEntity
import com.example.ui.WorkLogViewModel
import com.example.ui.components.Avatar
import com.example.ui.components.PayrollExportSheet
import com.example.ui.components.ShiftEditor
import com.example.ui.screens.AccountScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.LockScreen
import com.example.ui.screens.ReportsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.ShiftsScreen
import com.example.ui.theme.AppTheme
import com.example.ui.theme.WorkLogTheme
import com.example.util.DateUtils
import java.time.LocalDate
import java.time.LocalTime

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

private enum class Tab(val label: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    HOME("Home", Icons.Outlined.Home, Icons.Filled.Home),
    SHIFTS("Shifts", Icons.AutoMirrored.Outlined.ReceiptLong, Icons.AutoMirrored.Filled.ReceiptLong),
    REPORTS("Reports", Icons.Outlined.BarChart, Icons.Filled.BarChart),
    ACCOUNT("Account", Icons.Outlined.Person, Icons.Filled.Person)
}

private data class EditorRequest(val log: WorkLogEntity?, val hours: Double? = null, val workplace: String? = null)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkLogApp(viewModel: WorkLogViewModel = viewModel()) {
    val settings by viewModel.userSettings.collectAsStateWithLifecycle()
    val user by viewModel.authUserState.collectAsStateWithLifecycle()
    val isLocked by viewModel.isAppLocked.collectAsStateWithLifecycle()
    val biometricStatus by viewModel.biometricStatus.collectAsStateWithLifecycle()
    val authBusy by viewModel.authBusy.collectAsStateWithLifecycle()

    // Lock only after real background time (fixes the old lock loop on every resume,
    // which also fired after the biometric prompt and file pickers).
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> viewModel.onAppBackgrounded()
                Lifecycle.Event.ON_START -> viewModel.onAppForegrounded()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val authProps = AuthHandlers(viewModel)

    when {
        isLocked && settings.biometricLockEnabled -> LockScreen(
            biometricStatus = biometricStatus,
            hasPin = settings.hasPin,
            onUnlocked = viewModel::unlockApp,
            verifyPin = viewModel::verifySecurityPin
        )

        !settings.onboardingComplete && !user.isAuthenticated -> AuthScreen(
            cloudAvailable = viewModel.isCloudAvailable,
            busy = authBusy,
            onSignIn = authProps.signIn,
            onSignUp = authProps.signUp,
            onGoogle = authProps.google,
            onResetPassword = authProps.reset,
            onContinueOffline = viewModel::continueWithoutAccount,
            onClose = null
        )

        else -> MainShell(viewModel, authProps)
    }
}

private class AuthHandlers(vm: WorkLogViewModel) {
    val signIn: (String, String, (Boolean, String?) -> Unit) -> Unit = vm::signInWithEmail
    val signUp: (String, String, String, (Boolean, String?) -> Unit) -> Unit = vm::signUpWithEmail
    val google: (android.app.Activity, (Boolean, String?) -> Unit) -> Unit = vm::signInWithGoogle
    val reset: (String, (Boolean, String) -> Unit) -> Unit = vm::sendPasswordReset
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainShell(viewModel: WorkLogViewModel, auth: AuthHandlers) {
    val context = LocalContext.current
    val settings by viewModel.userSettings.collectAsStateWithLifecycle()
    val user by viewModel.authUserState.collectAsStateWithLifecycle()
    val allLogs by viewModel.allLogs.collectAsStateWithLifecycle()
    val monthly by viewModel.currentMonthlySummary.collectAsStateWithLifecycle()
    val weekly by viewModel.currentWeeklySummary.collectAsStateWithLifecycle()
    val breakdown by viewModel.monthlyWorkplaceBreakdown.collectAsStateWithLifecycle()
    val selectedWorkplace by viewModel.selectedWorkplaceFilter.collectAsStateWithLifecycle()
    val selectedMonth by viewModel.selectedMonth.collectAsStateWithLifecycle()
    val timer by viewModel.shiftTimerState.collectAsStateWithLifecycle()
    val sync by viewModel.syncState.collectAsStateWithLifecycle()
    val pending by viewModel.pendingSyncCount.collectAsStateWithLifecycle()
    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
    val unpaidCount by viewModel.unpaidCount.collectAsStateWithLifecycle()
    val biometricStatus by viewModel.biometricStatus.collectAsStateWithLifecycle()
    val authBusy by viewModel.authBusy.collectAsStateWithLifecycle()

    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    var editor by remember { mutableStateOf<EditorRequest?>(null) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showAuth by rememberSaveable { mutableStateOf(false) }
    var showExport by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(user.isAuthenticated) { if (user.isAuthenticated) showAuth = false }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            val ok = DatabaseBackupManager.writeToUri(context, uri, viewModel.exportBackupJson())
            Toast.makeText(context, if (ok) "Backup saved" else "Couldn't save the backup file", Toast.LENGTH_SHORT).show()
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val json = DatabaseBackupManager.readFromUri(context, uri)
            if (json.isNullOrBlank() || !viewModel.restoreFromBackupJson(json)) {
                Toast.makeText(context, "That file isn't a valid Earninglogs backup", Toast.LENGTH_LONG).show()
            }
        }
    }

    BackHandler(enabled = tab != Tab.HOME && !showSettings && !showAuth) { tab = Tab.HOME }

    // Full-screen overlays
    if (showAuth) {
        BackHandler { showAuth = false }
        AuthScreen(
            cloudAvailable = viewModel.isCloudAvailable,
            busy = authBusy,
            onSignIn = auth.signIn, onSignUp = auth.signUp, onGoogle = auth.google, onResetPassword = auth.reset,
            onContinueOffline = null,
            onClose = { showAuth = false }
        )
        return
    }
    if (showSettings) {
        SettingsScreen(
            settings = settings,
            currentMonthLabel = DateUtils.formatMonthYear(selectedMonth),
            onBack = { showSettings = false },
            onSave = { s -> viewModel.saveSettings { it.withPayrollFrom(s.copy(payrollUpdatedAt = it.payrollUpdatedAt)) } },
            onAddWorkplace = viewModel::addWorkplace,
            onRenameWorkplace = viewModel::renameWorkplace,
            onRemoveWorkplace = viewModel::removeWorkplace,
            onClearToday = viewModel::resetTodayLogs,
            onClearMonth = viewModel::resetMonthLogs,
            onClearAll = viewModel::clearAllLogs
        )
        return
    }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())
    val openNew = { editor = EditorRequest(null) }
    val openEdit = { log: WorkLogEntity -> editor = EditorRequest(log) }

    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface
                ),
                title = {
                    when (tab) {
                        Tab.HOME -> androidx.compose.foundation.layout.Column {
                            Text(greeting(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                user.displayName.split(" ").firstOrNull()?.takeIf { it.isNotBlank() } ?: "Earninglogs",
                                style = MaterialTheme.typography.titleLarge
                            )
                        }
                        else -> Text(tab.label, style = MaterialTheme.typography.titleLarge)
                    }
                },
                actions = {
                    if (user.isAuthenticated) {
                        SyncIndicator(sync.status, pending, isOnline) { tab = Tab.ACCOUNT }
                    }
                    if (settings.biometricLockEnabled) {
                        IconButton(onClick = viewModel::lockApp) {
                            Icon(Icons.Outlined.Lock, contentDescription = "Lock now")
                        }
                    }
                    if (tab != Tab.ACCOUNT) {
                        IconButton(onClick = { tab = Tab.ACCOUNT }) {
                            if (user.isAuthenticated) Avatar(user.initials, size = 32.dp)
                            else Icon(Icons.Outlined.Person, contentDescription = "Account")
                        }
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                Tab.entries.forEach { t ->
                    val selected = tab == t
                    NavigationBarItem(
                        selected = selected,
                        onClick = { tab = t },
                        icon = {
                            if (t == Tab.SHIFTS && unpaidCount > 0) {
                                BadgedBox(badge = {
                                    Badge(containerColor = AppTheme.status.warning) { Text(if (unpaidCount > 99) "99+" else "$unpaidCount") }
                                }) { Icon(if (selected) t.selectedIcon else t.icon, contentDescription = t.label) }
                            } else {
                                Icon(if (selected) t.selectedIcon else t.icon, contentDescription = t.label)
                            }
                        },
                        label = { Text(t.label) },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.onSurface
                        )
                    )
                }
            }
        },
        floatingActionButton = {
            if (tab != Tab.ACCOUNT) {
                ExtendedFloatingActionButton(
                    onClick = openNew,
                    icon = { Icon(Icons.Default.Add, null) },
                    text = { Text("Log shift") },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    ) { padding ->
        AnimatedContent(
            targetState = tab,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "tabs",
            modifier = Modifier.fillMaxSize()
        ) { current ->
            when (current) {
                Tab.HOME -> HomeScreen(
                    monthly = monthly,
                    workplaceBreakdown = breakdown,
                    selectedWorkplace = selectedWorkplace,
                    settings = settings,
                    timer = timer,
                    contentPadding = padding,
                    onSelectWorkplace = viewModel::selectWorkplaceFilter,
                    onNavigateMonth = viewModel::navigateMonth,
                    onCurrentMonth = viewModel::jumpToCurrentMonth,
                    onUpdateTaxRate = viewModel::updateTaxRate,
                    onMarkMonthPaid = { viewModel.markAllUnpaidAsPaid(monthOnly = true) },
                    onAddShift = openNew,
                    onEditShift = openEdit,
                    onDeleteShift = viewModel::deleteLog,
                    onTogglePaid = viewModel::togglePaidStatus,
                    onSeeAllShifts = { tab = Tab.SHIFTS },
                    onStartTimer = viewModel::startShiftTimer,
                    onPauseTimer = viewModel::pauseShiftTimer,
                    onResumeTimer = viewModel::resumeShiftTimer,
                    onStopTimer = {
                        val (hours, wp) = viewModel.endShiftTimer()
                        editor = EditorRequest(null, hours = hours, workplace = wp)
                    },
                    onDiscardTimer = viewModel::discardShiftTimer
                )
                Tab.SHIFTS -> ShiftsScreen(
                    logs = allLogs,
                    settings = settings,
                    contentPadding = padding,
                    onAddShift = openNew,
                    onEditShift = openEdit,
                    onDeleteShift = viewModel::deleteLog,
                    onTogglePaid = viewModel::togglePaidStatus
                )
                Tab.REPORTS -> ReportsScreen(
                    weekly = weekly,
                    settings = settings,
                    contentPadding = padding,
                    onNavigateWeek = viewModel::navigateWeek,
                    onCurrentWeek = viewModel::jumpToCurrentWeek,
                    onExport = { showExport = true },
                    onAddShift = openNew,
                    onEditShift = openEdit,
                    onDeleteShift = viewModel::deleteLog,
                    onTogglePaid = viewModel::togglePaidStatus
                )
                Tab.ACCOUNT -> AccountScreen(
                    user = user,
                    settings = settings,
                    sync = sync,
                    pendingCount = pending,
                    isOnline = isOnline,
                    cloudAvailable = viewModel.isCloudAvailable,
                    biometricStatus = biometricStatus,
                    contentPadding = padding,
                    onSignIn = { showAuth = true },
                    onSignOut = viewModel::signOut,
                    onSyncNow = viewModel::syncNow,
                    onToggleAutoSync = viewModel::toggleAutoCloudSync,
                    onToggleLock = viewModel::setBiometricLock,
                    onToggleLockOnResume = viewModel::setLockOnResume,
                    onSetPin = viewModel::setSecurityPin,
                    onOpenSettings = { showSettings = true },
                    onExportBackup = { exportLauncher.launch("earninglogs-backup-${LocalDate.now()}.json") },
                    onImportBackup = { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                    onRestoreAutoBackup = {
                        if (!viewModel.restoreFromAutoBackup()) {
                            Toast.makeText(context, "No previous export found on this device", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
        }
    }

    editor?.let { req ->
        ShiftEditor(
            initialLog = req.log,
            settings = settings,
            prefillHours = req.hours,
            prefillWorkplace = req.workplace,
            onDismiss = { editor = null },
            onSave = { log -> viewModel.saveLog(log); editor = null },
            onDelete = { id -> viewModel.deleteLog(id); editor = null }
        )
    }

    if (showExport) {
        PayrollExportSheet(
            summary = weekly,
            currency = settings.currencySymbol,
            taxRate = settings.defaultTaxRate,
            onDismiss = { showExport = false }
        )
    }
}

@Composable
private fun SyncIndicator(status: SyncStatus, pending: Int, online: Boolean, onClick: () -> Unit) {
    val s = AppTheme.status
    val (icon, tint, desc) = when {
        status == SyncStatus.SYNCING -> Triple(Icons.Outlined.CloudSync, MaterialTheme.colorScheme.primary, "Syncing")
        status == SyncStatus.ERROR -> Triple(Icons.Outlined.ErrorOutline, MaterialTheme.colorScheme.error, "Sync problem")
        !online -> Triple(Icons.Outlined.CloudOff, s.warning, "Offline")
        pending > 0 -> Triple(Icons.Outlined.CloudUpload, s.warning, "Changes waiting to sync")
        else -> Triple(Icons.Outlined.CloudDone, s.success, "Synced")
    }
    IconButton(onClick = onClick) { Icon(icon, contentDescription = desc, tint = tint) }
}

private fun greeting(): String = when (LocalTime.now().hour) {
    in 5..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    else -> "Good evening"
}
