package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
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
import androidx.compose.runtime.CompositionLocalProvider
import com.example.ui.theme.LocalWorkplaces
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.DatabaseBackupManager
import com.example.data.SyncStatus
import com.example.ui.WorkLogViewModel
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
    SETTINGS("Settings", Icons.Outlined.Settings, Icons.Filled.Settings)
}

/** Editor states, kept as plain values so they survive rotation (rememberSaveable). */
private const val EDITOR_CLOSED = -1
private const val EDITOR_NEW = 0

@Composable
fun WorkLogApp(viewModel: WorkLogViewModel = viewModel()) {
    val settings by viewModel.userSettings.collectAsStateWithLifecycle()
    val user by viewModel.authUserState.collectAsStateWithLifecycle()
    val isLocked by viewModel.isAppLocked.collectAsStateWithLifecycle()
    val biometricStatus by viewModel.biometricStatus.collectAsStateWithLifecycle()
    val authBusy by viewModel.authBusy.collectAsStateWithLifecycle()

    // Lock only after real background time (no lock loop after the biometric prompt or file pickers).
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
            onSignIn = viewModel::signInWithEmail,
            onSignUp = viewModel::signUpWithEmail,
            onGoogle = viewModel::signInWithGoogle,
            onResetPassword = viewModel::sendPasswordReset,
            onContinueOffline = viewModel::continueWithoutAccount,
            onClose = null
        )

        else -> CompositionLocalProvider(LocalWorkplaces provides settings.workplaces) { MainShell(viewModel) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainShell(viewModel: WorkLogViewModel) {
    val context = LocalContext.current
    val settings by viewModel.userSettings.collectAsStateWithLifecycle()
    val user by viewModel.authUserState.collectAsStateWithLifecycle()
    val allLogs by viewModel.allLogs.collectAsStateWithLifecycle()
    val monthly by viewModel.currentMonthlySummary.collectAsStateWithLifecycle()
    val weekly by viewModel.currentWeeklySummary.collectAsStateWithLifecycle()
    val selectedMonth by viewModel.selectedMonth.collectAsStateWithLifecycle()
    val sync by viewModel.syncState.collectAsStateWithLifecycle()
    val pending by viewModel.pendingSyncCount.collectAsStateWithLifecycle()
    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
    val unpaidCount by viewModel.unpaidCount.collectAsStateWithLifecycle()
    val biometricStatus by viewModel.biometricStatus.collectAsStateWithLifecycle()
    val authBusy by viewModel.authBusy.collectAsStateWithLifecycle()

    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showAuth by rememberSaveable { mutableStateOf(false) }
    var showExport by rememberSaveable { mutableStateOf(false) }

    // Editor: EDITOR_CLOSED, EDITOR_NEW, or the id of the shift being edited.
    var editorId by rememberSaveable { mutableIntStateOf(EDITOR_CLOSED) }

    fun openEditor(id: Int) { editorId = id }
    fun closeEditor() { editorId = EDITOR_CLOSED }

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

    // Full-screen pages
    if (showAuth) {
        BackHandler { showAuth = false }
        AuthScreen(
            cloudAvailable = viewModel.isCloudAvailable,
            busy = authBusy,
            onSignIn = viewModel::signInWithEmail,
            onSignUp = viewModel::signUpWithEmail,
            onGoogle = viewModel::signInWithGoogle,
            onResetPassword = viewModel::sendPasswordReset,
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
    val openNew = { openEditor(EDITOR_NEW) }
    val openEdit = { log: com.example.data.WorkLogEntity -> openEditor(log.id) }

    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                title = { Text(if (tab == Tab.HOME) "Earninglogs" else tab.label, style = MaterialTheme.typography.titleLarge) },
                actions = {
                    if (user.isAuthenticated) {
                        SyncIndicator(sync.status, pending, isOnline) { tab = Tab.SETTINGS }
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 0.dp) {
                Tab.entries.forEach { t ->
                    val selected = tab == t
                    NavigationBarItem(
                        selected = selected,
                        onClick = { tab = t },
                        icon = {
                            if (t == Tab.SHIFTS && unpaidCount > 0) {
                                BadgedBox(badge = {
                                    Badge(
                                        containerColor = AppTheme.status.warning,
                                        contentColor = MaterialTheme.colorScheme.surface,
                                        modifier = Modifier.semantics { contentDescription = "$unpaidCount unpaid" }
                                    ) { Text(if (unpaidCount > 99) "99+" else "$unpaidCount") }
                                }) { Icon(if (selected) t.selectedIcon else t.icon, contentDescription = null) }
                            } else {
                                Icon(if (selected) t.selectedIcon else t.icon, contentDescription = null)
                            }
                        },
                        // The label is the accessible name; icons are decorative (no double reading).
                        label = { Text(t.label) },
                        alwaysShowLabel = true,
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.onSurface,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        },
        floatingActionButton = {
            if (tab == Tab.HOME || tab == Tab.SHIFTS) {
                ExtendedFloatingActionButton(
                    onClick = openNew,
                    icon = { Icon(Icons.Default.Add, null) },
                    text = { Text("Log shift") },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp)
                )
            }
        }
    ) { padding ->
        // No cross-fade between tabs: simpler, and nothing moves for people sensitive to motion.
        when (tab) {
            Tab.HOME -> HomeScreen(
                userName = user.displayName.trim().split(" ").firstOrNull().orEmpty(),
                monthly = monthly,
                settings = settings,
                contentPadding = padding,
                onNavigateMonth = viewModel::navigateMonth,
                onCurrentMonth = viewModel::jumpToCurrentMonth,
                onMarkMonthPaid = { viewModel.markAllUnpaidAsPaid(monthOnly = true) },
                onAddShift = openNew,
                onEditShift = openEdit,
                onDeleteShift = viewModel::deleteLog,
                onTogglePaid = viewModel::togglePaidStatus,
                onSeeAllShifts = { tab = Tab.SHIFTS }
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
                onAddShift = openNew
            )
            Tab.SETTINGS -> AccountScreen(
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

    if (editorId != EDITOR_CLOSED) {
        val editing = if (editorId == EDITOR_NEW) null else allLogs.firstOrNull { it.id == editorId }
        // While the database is still loading after a rotation, wait instead of opening a blank "new" form.
        if (editorId == EDITOR_NEW || editing != null) {
            ShiftEditor(
                initialLog = editing,
                settings = settings,
                onDismiss = ::closeEditor,
                onSave = { log -> viewModel.saveLog(log); closeEditor() },
                onDelete = { id -> viewModel.deleteLog(id); closeEditor() }
            )
        }
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
        status == SyncStatus.SYNCING -> Triple(Icons.Outlined.CloudSync, MaterialTheme.colorScheme.primary, "Syncing. Open sync settings")
        status == SyncStatus.ERROR -> Triple(Icons.Outlined.ErrorOutline, MaterialTheme.colorScheme.error, "Sync problem. Open sync settings")
        !online -> Triple(Icons.Outlined.CloudOff, s.warning, "Offline. Changes are saved on this phone")
        pending > 0 -> Triple(Icons.Outlined.CloudUpload, s.warning, "$pending changes waiting to sync")
        else -> Triple(Icons.Outlined.CloudDone, s.success, "All changes synced")
    }
    IconButton(onClick = onClick) { Icon(icon, contentDescription = desc, tint = tint) }
}
