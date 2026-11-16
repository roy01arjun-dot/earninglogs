package com.example.ui

import android.app.Activity
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppContainer
import com.example.data.AuthResult
import com.example.data.AuthUserState
import com.example.data.DatabaseBackupManager
import com.example.data.FirebaseAuthManager
import com.example.data.ShiftTimerManager
import com.example.data.ShiftTimerState
import com.example.data.SyncState
import com.example.data.UserSettings
import com.example.data.UserSettingsRepository
import com.example.data.WorkLogEntity
import com.example.util.BiometricAuthManager
import com.example.util.BiometricStatus
import com.example.util.DateUtils
import com.example.util.NetworkConnectivityMonitor
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

data class WorkplaceSummary(
    val name: String,
    val totalHours: Double,
    val totalEarnings: Double,
    val paidEarnings: Double,
    val unpaidEarnings: Double,
    val shiftCount: Int,
    val sharePercentage: Float
)

data class WeeklyPayrollSummary(
    val startOfWeek: LocalDate,
    val endOfWeek: LocalDate,
    val logs: List<WorkLogEntity>,
    val totalHours: Double,
    val regularHours: Double,
    val overtimeHours: Double,
    val regularPay: Double,
    val overtimePay: Double,
    val totalEarnings: Double,
    val paidEarnings: Double = 0.0,
    val unpaidEarnings: Double = 0.0,
    val daysWorked: Int
) {
    companion object {
        fun from(start: LocalDate, end: LocalDate, logs: List<WorkLogEntity>) = WeeklyPayrollSummary(
            startOfWeek = start,
            endOfWeek = end,
            logs = logs,
            totalHours = logs.sumOf { it.netHoursWorked },
            regularHours = logs.sumOf { it.regularHours },
            overtimeHours = logs.sumOf { it.overtimeHours },
            regularPay = logs.sumOf { it.regularPay },
            overtimePay = logs.sumOf { it.overtimePay },
            totalEarnings = logs.sumOf { it.totalPay },
            paidEarnings = logs.filter { it.isPaid }.sumOf { it.totalPay },
            unpaidEarnings = logs.filter { !it.isPaid }.sumOf { it.totalPay },
            daysWorked = logs.map { it.date }.distinct().size
        )
    }
}

data class MonthlyPayrollSummary(
    val yearMonth: YearMonth,
    val logs: List<WorkLogEntity>,
    val totalHours: Double,
    val regularHours: Double,
    val overtimeHours: Double,
    val regularPay: Double,
    val overtimePay: Double,
    val totalEarnings: Double,
    val paidEarnings: Double = 0.0,
    val unpaidEarnings: Double = 0.0,
    val daysWorked: Int,
    val averageEarningsPerDay: Double,
    val weeklyBreakdown: List<WeeklyPayrollSummary>
)

data class OverallTotalSummary(
    val totalEarnings: Double = 0.0,
    val paidEarnings: Double = 0.0,
    val unpaidEarnings: Double = 0.0,
    val totalHours: Double = 0.0,
    val shiftCount: Int = 0,
    val unpaidShiftCount: Int = 0
)

const val ALL_WORKPLACES = "ALL"

class WorkLogViewModel(application: Application) : AndroidViewModel(application) {

    init { AppContainer.init(application) }

    private val repository = AppContainer.repository
    private val settingsRepo = AppContainer.settingsRepo
    private val sync = AppContainer.syncManager
    private val networkMonitor = NetworkConnectivityMonitor(application)
    private val shiftTimerManager = ShiftTimerManager(application, viewModelScope)

    // ------------------------------------------------------------------ state

    val userSettings: StateFlow<UserSettings> = settingsRepo.settings

    val allLogs: StateFlow<List<WorkLogEntity>> = repository.activeLogs
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val pendingSyncCount: StateFlow<Int> = repository.pendingSyncCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val authUserState: StateFlow<AuthUserState> = FirebaseAuthManager.userState
    val syncState: StateFlow<SyncState> = sync.state
    val isCloudAvailable: Boolean get() = FirebaseAuthManager.isCloudAvailable

    val isOnline: StateFlow<Boolean> = networkMonitor.isOnlineFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, networkMonitor.isCurrentlyOnline())

    val shiftTimerState: StateFlow<ShiftTimerState> = shiftTimerManager.timerState

    private val _selectedWeekStart = MutableStateFlow(DateUtils.getStartOfWeek(LocalDate.now()))
    val selectedWeekStart: StateFlow<LocalDate> = _selectedWeekStart.asStateFlow()

    private val _selectedMonth = MutableStateFlow(YearMonth.now())
    val selectedMonth: StateFlow<YearMonth> = _selectedMonth.asStateFlow()

    private val _selectedWorkplaceFilter = MutableStateFlow(ALL_WORKPLACES)
    val selectedWorkplaceFilter: StateFlow<String> = _selectedWorkplaceFilter.asStateFlow()

    private val _isAppLocked = MutableStateFlow(settingsRepo.current.biometricLockEnabled)
    val isAppLocked: StateFlow<Boolean> = _isAppLocked.asStateFlow()

    private val _biometricStatus = MutableStateFlow(BiometricAuthManager.checkBiometricAvailability(application))
    val biometricStatus: StateFlow<BiometricStatus> = _biometricStatus.asStateFlow()

    private val _authBusy = MutableStateFlow(false)
    val authBusy: StateFlow<Boolean> = _authBusy.asStateFlow()

    /** One-off messages for the snackbar. */
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()
    private fun toast(msg: String) { _messages.tryEmit(msg) }

    private var backgroundedAt = 0L
    private var newSignInPending = false

    init {
        // Start / stop cloud sync whenever the Firebase user changes.
        viewModelScope.launch {
            authUserState.map { it.uid }.distinctUntilChanged().collect { uid ->
                if (uid.isNotBlank()) {
                    sync.start(uid, isNewSignIn = newSignInPending)
                    newSignInPending = false
                } else {
                    sync.stop()
                }
            }
        }
        // Push queued changes when the connection comes back.
        viewModelScope.launch {
            isOnline.drop(1).collect { online -> if (online) sync.requestPush() }
        }
    }

    // ------------------------------------------------------------------ summaries

    private fun filtered(logs: List<WorkLogEntity>, wp: String) =
        if (wp == ALL_WORKPLACES) logs else logs.filter { it.workplace == wp }

    val overallTotalSummary: StateFlow<OverallTotalSummary> = allLogs.map { logs ->
        val unpaid = logs.filter { !it.isPaid }
        OverallTotalSummary(
            totalEarnings = logs.sumOf { it.totalPay },
            paidEarnings = logs.filter { it.isPaid }.sumOf { it.totalPay },
            unpaidEarnings = unpaid.sumOf { it.totalPay },
            totalHours = logs.sumOf { it.netHoursWorked },
            shiftCount = logs.size,
            unpaidShiftCount = unpaid.size
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), OverallTotalSummary())

    val unpaidCount: StateFlow<Int> = allLogs.map { l -> l.count { !it.isPaid } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val currentWeeklySummary: StateFlow<WeeklyPayrollSummary> = combine(
        allLogs, selectedWeekStart, selectedWorkplaceFilter
    ) { logs, weekStart, wp ->
        val weekEnd = DateUtils.getEndOfWeek(weekStart)
        val weekLogs = filtered(logs, wp).filter {
            val d = DateUtils.parseDate(it.date)
            !d.isBefore(weekStart) && !d.isAfter(weekEnd)
        }.sortedBy { it.date }
        WeeklyPayrollSummary.from(weekStart, weekEnd, weekLogs)
    }.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000),
        WeeklyPayrollSummary.from(
            DateUtils.getStartOfWeek(LocalDate.now()), DateUtils.getEndOfWeek(LocalDate.now()), emptyList()
        )
    )

    val currentMonthlySummary: StateFlow<MonthlyPayrollSummary> = combine(
        allLogs, selectedMonth, selectedWorkplaceFilter
    ) { logs, ym, wp -> buildMonthly(filtered(logs, wp), ym) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), buildMonthly(emptyList(), YearMonth.now()))

    private fun buildMonthly(logs: List<WorkLogEntity>, ym: YearMonth): MonthlyPayrollSummary {
        val start = DateUtils.getStartOfMonth(ym)
        val end = DateUtils.getEndOfMonth(ym)
        val monthLogs = logs.filter {
            val d = DateUtils.parseDate(it.date)
            !d.isBefore(start) && !d.isAfter(end)
        }.sortedBy { it.date }
        val total = monthLogs.sumOf { it.totalPay }
        val days = monthLogs.map { it.date }.distinct().size

        val weeks = mutableListOf<WeeklyPayrollSummary>()
        var ws = DateUtils.getStartOfWeek(start)
        while (!ws.isAfter(end)) {
            val we = DateUtils.getEndOfWeek(ws)
            val wl = monthLogs.filter {
                val d = DateUtils.parseDate(it.date)
                !d.isBefore(ws) && !d.isAfter(we)
            }
            weeks += WeeklyPayrollSummary.from(ws, we, wl)
            ws = ws.plusWeeks(1)
        }
        return MonthlyPayrollSummary(
            yearMonth = ym,
            logs = monthLogs,
            totalHours = monthLogs.sumOf { it.netHoursWorked },
            regularHours = monthLogs.sumOf { it.regularHours },
            overtimeHours = monthLogs.sumOf { it.overtimeHours },
            regularPay = monthLogs.sumOf { it.regularPay },
            overtimePay = monthLogs.sumOf { it.overtimePay },
            totalEarnings = total,
            paidEarnings = monthLogs.filter { it.isPaid }.sumOf { it.totalPay },
            unpaidEarnings = monthLogs.filter { !it.isPaid }.sumOf { it.totalPay },
            daysWorked = days,
            averageEarningsPerDay = if (days > 0) total / days else 0.0,
            weeklyBreakdown = weeks
        )
    }

    val monthlyWorkplaceBreakdown: StateFlow<List<WorkplaceSummary>> = combine(
        allLogs, selectedMonth, userSettings
    ) { logs, ym, settings ->
        val start = DateUtils.getStartOfMonth(ym)
        val end = DateUtils.getEndOfMonth(ym)
        val monthLogs = logs.filter {
            val d = DateUtils.parseDate(it.date)
            !d.isBefore(start) && !d.isAfter(end)
        }
        val monthTotal = monthLogs.sumOf { it.totalPay }
        (settings.workplaces + monthLogs.map { it.workplace }).distinct().map { wp ->
            val wl = monthLogs.filter { it.workplace == wp }
            val pay = wl.sumOf { it.totalPay }
            WorkplaceSummary(
                name = wp,
                totalHours = wl.sumOf { it.netHoursWorked },
                totalEarnings = pay,
                paidEarnings = wl.filter { it.isPaid }.sumOf { it.totalPay },
                unpaidEarnings = wl.filter { !it.isPaid }.sumOf { it.totalPay },
                shiftCount = wl.size,
                sharePercentage = if (monthTotal > 0) (pay / monthTotal).toFloat() else 0f
            )
        }.sortedByDescending { it.totalEarnings }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ------------------------------------------------------------------ navigation state

    fun navigateWeek(delta: Long) { _selectedWeekStart.value = _selectedWeekStart.value.plusWeeks(delta) }
    fun jumpToCurrentWeek() { _selectedWeekStart.value = DateUtils.getStartOfWeek(LocalDate.now()) }
    fun navigateMonth(delta: Long) { _selectedMonth.value = _selectedMonth.value.plusMonths(delta) }
    fun jumpToCurrentMonth() { _selectedMonth.value = YearMonth.now() }
    fun selectWorkplaceFilter(wp: String) { _selectedWorkplaceFilter.value = wp }

    // ------------------------------------------------------------------ shifts

    fun saveLog(log: WorkLogEntity) {
        viewModelScope.launch {
            if (log.id == 0) repository.addLog(log) else repository.updateLog(log)
            if (log.workplace.isNotBlank() && log.workplace !in userSettings.value.workplaces) {
                addWorkplace(log.workplace)
            }
            sync.requestPush()
            toast(if (log.id == 0) "Shift added" else "Shift updated")
        }
    }

    fun deleteLog(id: Int) {
        viewModelScope.launch {
            repository.deleteLog(id)
            sync.requestPush()
            toast("Shift deleted")
        }
    }

    fun togglePaidStatus(log: WorkLogEntity) {
        viewModelScope.launch {
            repository.setPaid(log, !log.isPaid)
            sync.requestPush()
        }
    }

    fun markAllUnpaidAsPaid(monthOnly: Boolean) {
        viewModelScope.launch {
            val ym = selectedMonth.value
            val wp = selectedWorkplaceFilter.value
            val targets = filtered(allLogs.value, wp).filter {
                !it.isPaid && (!monthOnly || YearMonth.from(DateUtils.parseDate(it.date)) == ym)
            }
            repository.markPaid(targets.map { it.id })
            sync.requestPush()
            toast("${targets.size} shift${if (targets.size == 1) "" else "s"} marked as paid")
        }
    }

    fun resetTodayLogs() = launchDelete("Today's shifts cleared") {
        repository.deleteLogsForDate(DateUtils.formatDate(LocalDate.now()))
    }

    fun resetMonthLogs() = launchDelete("Shifts for ${DateUtils.formatMonthYear(selectedMonth.value)} cleared") {
        val ym = selectedMonth.value
        repository.deleteLogsInRange(
            DateUtils.formatDate(DateUtils.getStartOfMonth(ym)),
            DateUtils.formatDate(DateUtils.getEndOfMonth(ym))
        )
    }

    fun clearAllLogs() = launchDelete("All shifts deleted") { repository.deleteAll() }

    private fun launchDelete(msg: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            block()
            sync.requestPush()
            toast(msg)
        }
    }

    // ------------------------------------------------------------------ settings & workplaces

    fun saveSettings(transform: (UserSettings) -> UserSettings) = settingsRepo.update(transform)

    fun updateTaxRate(rate: Double) = saveSettings { it.copy(defaultTaxRate = rate.coerceIn(0.0, 100.0)) }

    fun addWorkplace(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        saveSettings { s -> if (trimmed in s.workplaces) s else s.copy(workplaces = s.workplaces + trimmed) }
    }

    fun removeWorkplace(name: String) {
        saveSettings { s ->
            val list = s.workplaces.filter { it != name }
            s.copy(workplaces = list.ifEmpty { listOf("Main Workplace") })
        }
        if (_selectedWorkplaceFilter.value == name) _selectedWorkplaceFilter.value = ALL_WORKPLACES
    }

    fun renameWorkplace(oldName: String, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty() || trimmed == oldName) return
        saveSettings { s -> s.copy(workplaces = s.workplaces.map { if (it == oldName) trimmed else it }.distinct()) }
        viewModelScope.launch {
            repository.renameWorkplace(oldName, trimmed)
            if (_selectedWorkplaceFilter.value == oldName) _selectedWorkplaceFilter.value = trimmed
            sync.requestPush()
        }
    }

    // ------------------------------------------------------------------ shift timer

    fun startShiftTimer(workplace: String) = shiftTimerManager.startTimer(workplace)
    fun pauseShiftTimer() = shiftTimerManager.pauseTimer()
    fun resumeShiftTimer() = shiftTimerManager.resumeTimer()
    fun discardShiftTimer() = shiftTimerManager.discardTimer()

    /** Stops the timer and returns (hours, workplace) to pre-fill the shift editor. */
    fun endShiftTimer(): Pair<Double, String> {
        val wp = shiftTimerManager.timerState.value.workplace
        return shiftTimerManager.endTimer() to wp
    }

    // ------------------------------------------------------------------ authentication

    private fun handleAuth(result: AuthResult, onDone: (Boolean, String?) -> Unit) {
        _authBusy.value = false
        when (result) {
            is AuthResult.Success -> {
                saveSettings { it.copy(onboardingComplete = true) }
                toast(result.message)
                onDone(true, null)
            }
            is AuthResult.Error -> { newSignInPending = false; onDone(false, result.message) }
            AuthResult.Cancelled -> { newSignInPending = false; onDone(false, null) }
        }
    }

    fun signInWithEmail(email: String, pass: String, onDone: (Boolean, String?) -> Unit) {
        _authBusy.value = true
        newSignInPending = true
        viewModelScope.launch { handleAuth(FirebaseAuthManager.signInWithEmail(email, pass), onDone) }
    }

    fun signUpWithEmail(email: String, pass: String, name: String, onDone: (Boolean, String?) -> Unit) {
        _authBusy.value = true
        newSignInPending = true
        viewModelScope.launch { handleAuth(FirebaseAuthManager.signUpWithEmail(email, pass, name), onDone) }
    }

    fun signInWithGoogle(activity: Activity, onDone: (Boolean, String?) -> Unit) {
        _authBusy.value = true
        newSignInPending = true
        viewModelScope.launch { handleAuth(FirebaseAuthManager.signInWithGoogle(activity), onDone) }
    }

    fun sendPasswordReset(email: String, onDone: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val (ok, msg) = FirebaseAuthManager.sendPasswordReset(email)
            onDone(ok, msg)
        }
    }

    fun continueWithoutAccount() = saveSettings { it.copy(onboardingComplete = true) }

    /**
     * @param removeLocalData true = wipe this device's copy (it stays safe in the cloud).
     */
    fun signOut(removeLocalData: Boolean) {
        viewModelScope.launch {
            if (!removeLocalData) {
                // Let queued uploads finish first so nothing is lost.
                runCatching { sync.syncNow() }
            }
            sync.stop()
            FirebaseAuthManager.signOut(getApplication())
            if (removeLocalData) AppContainer.repository.wipeLocal()
            toast("Signed out")
        }
    }

    // ------------------------------------------------------------------ sync

    fun syncNow() {
        if (!isOnline.value) {
            toast("You're offline. Changes will sync when you reconnect.")
        }
        AppContainer.appScope.launch { sync.syncNow() }
    }

    fun toggleAutoCloudSync(enabled: Boolean) {
        saveSettings { it.copy(autoCloudSyncEnabled = enabled) }
        sync.onAutoSyncChanged(enabled)
    }

    // ------------------------------------------------------------------ app lock

    fun unlockApp() { _isAppLocked.value = false }

    fun lockApp() { if (userSettings.value.biometricLockEnabled) _isAppLocked.value = true }

    fun onAppBackgrounded() { backgroundedAt = System.currentTimeMillis() }

    /** Only re-lock after the app was really in the background for a while (not for pickers/prompts). */
    fun onAppForegrounded() {
        _biometricStatus.value = BiometricAuthManager.checkBiometricAvailability(getApplication())
        val s = userSettings.value
        val away = System.currentTimeMillis() - backgroundedAt
        if (backgroundedAt > 0 && s.biometricLockEnabled && s.requireBiometricOnResume && away > RELOCK_AFTER_MS) {
            _isAppLocked.value = true
        }
        backgroundedAt = 0L
    }

    fun setBiometricLock(enabled: Boolean) {
        if (enabled && !_biometricStatus.value.isAvailable && !userSettings.value.hasPin) {
            toast("Set up a screen lock on your phone or create an app PIN first")
            return
        }
        saveSettings { it.copy(biometricLockEnabled = enabled) }
        if (!enabled) _isAppLocked.value = false
    }

    fun setLockOnResume(enabled: Boolean) = saveSettings { it.copy(requireBiometricOnResume = enabled) }

    fun setSecurityPin(pin: String?) {
        val hash = if (pin.isNullOrBlank()) "" else UserSettingsRepository.hashPin(pin)
        saveSettings { it.copy(securityPinHash = hash) }
        toast(if (hash.isEmpty()) "PIN removed" else "PIN saved")
    }

    fun verifySecurityPin(pin: String): Boolean {
        val hash = userSettings.value.securityPinHash
        return hash.isNotBlank() && UserSettingsRepository.hashPin(pin) == hash
    }

    // ------------------------------------------------------------------ backup

    fun exportBackupJson(): String {
        val json = DatabaseBackupManager.exportToJson(allLogs.value, userSettings.value)
        DatabaseBackupManager.saveAutoBackup(getApplication(), json)
        return json
    }

    fun restoreFromBackupJson(json: String): Boolean {
        val parsed = runCatching { DatabaseBackupManager.importFromJson(json) }.getOrNull() ?: return false
        val (restoredSettings, restoredLogs) = parsed
        viewModelScope.launch {
            restoredSettings?.let { r -> saveSettings { it.withPayrollFrom(r.copy(payrollUpdatedAt = System.currentTimeMillis())) } }
            // Skip exact duplicates of shifts that already exist (old backups had no uuid).
            val existing = allLogs.value.map { it.signature() }.toSet()
            val existingUuids = allLogs.value.map { it.uuid }.toSet()
            val toImport = restoredLogs.filter { it.uuid in existingUuids || it.signature() !in existing }
            repository.importLogs(toImport)
            sync.requestPush()
            toast("Restored ${toImport.size} shift${if (toImport.size == 1) "" else "s"}")
        }
        return true
    }

    private fun WorkLogEntity.signature() = "$date|$hoursWorked|$wageRate|$workplace|$note|$breakMinutes"

    fun restoreFromAutoBackup(): Boolean {
        val json = DatabaseBackupManager.readAutoBackup(getApplication())
        return !json.isNullOrBlank() && restoreFromBackupJson(json)
    }

    companion object {
        private const val RELOCK_AFTER_MS = 30_000L
    }
}
