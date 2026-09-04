package com.example.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.DatabaseBackupManager
import com.example.data.ShiftTimerState
import com.example.data.UserSettings
import com.example.data.UserSettingsRepository
import com.example.data.WorkLogDatabase
import com.example.data.WorkLogEntity
import com.example.data.WorkLogRepository
import com.example.data.auth.AuthRepository
import com.example.data.auth.AuthState
import com.example.data.auth.SignInResult
import com.example.data.sync.SyncEngine
import com.example.data.sync.SyncStatus
import com.example.data.sync.SyncWorker
import com.example.util.BiometricAuthManager
import com.example.util.BiometricStatus
import com.example.util.DateUtils
import com.example.util.NetworkConnectivityMonitor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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
)

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

/**
 * Holds screen state and delegates work.
 *
 * Note what is absent compared to the previous version: no Firestore calls, no batch writes, no
 * snapshot listener, no `activeSyncJob`. Sync is queued through WorkManager and survives this
 * ViewModel being destroyed, which the old `viewModelScope.launch` upload did not.
 */
class WorkLogViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepo = UserSettingsRepository(application)
    private val dao = WorkLogDatabase.getDatabase(application).workLogDao()
    private val repository = WorkLogRepository(application, dao, settingsRepo)
    private val authRepository = AuthRepository(application)
    private val syncEngine = SyncEngine(application, dao, settingsRepo)
    private val networkMonitor = NetworkConnectivityMonitor(application)

    val userSettings: StateFlow<UserSettings> = repository.userSettings.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), settingsRepo.settings.value
    )

    val allLogs: StateFlow<List<WorkLogEntity>> = repository.allLogs.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    val authState: StateFlow<AuthState> = authRepository.authState.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), authRepository.currentState()
    )

    val isOnline: StateFlow<Boolean> = networkMonitor.isOnlineFlow.stateIn(
        viewModelScope, SharingStarted.Eagerly, networkMonitor.isCurrentlyOnline()
    )

    private val pendingCount: StateFlow<Int> = repository.pendingSyncCount.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), 0
    )

    private val syncRunning: StateFlow<Boolean> = SyncWorker.isRunning(application).stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), false
    )

    /** Transient message from a manual action, e.g. a restore or a sign-in failure. */
    private val _transientMessage = MutableStateFlow<String?>(null)
    val transientMessage: StateFlow<String?> = _transientMessage.asStateFlow()

    private val _manualSyncError = MutableStateFlow<String?>(null)

    /**
     * A single honest status. It can only report [SyncStatus.Synced] off a real backend
     * acknowledgement, because `lastCloudSyncTimestamp` is written by [SyncEngine.sync] after
     * Firestore confirms the batch.
     */
    val syncStatus: StateFlow<SyncStatus> = combine(
        authState, pendingCount, syncRunning, userSettings, _manualSyncError
    ) { auth, pending, running, settings, error ->
        when {
            auth is AuthState.SignedOut -> SyncStatus.NotSignedIn
            error != null -> SyncStatus.Failed(error)
            running -> SyncStatus.Syncing
            pending > 0 -> SyncStatus.Pending(pending)
            settings.lastCloudSyncTimestamp > 0L ->
                SyncStatus.Synced(settings.lastCloudSyncTimestamp, 0, 0)
            else -> SyncStatus.Idle
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SyncStatus.Idle)

    // --- App lock ---

    private val _isAppLocked = MutableStateFlow(settingsRepo.settings.value.appLockEnabled)
    val isAppLocked: StateFlow<Boolean> = _isAppLocked.asStateFlow()

    private val _biometricStatus = MutableStateFlow(BiometricAuthManager.checkBiometricAvailability(application))
    val biometricStatus: StateFlow<BiometricStatus> = _biometricStatus.asStateFlow()

    private val _authErrorMessage = MutableStateFlow<String?>(null)
    val authErrorMessage: StateFlow<String?> = _authErrorMessage.asStateFlow()

    // --- Shift Timer ---

    private val timerPrefs = application.getSharedPreferences("work_shift_timer_prefs", Context.MODE_PRIVATE)

    private fun loadTimerState(): ShiftTimerState {
        val isRunning = timerPrefs.getBoolean("timer_is_running", false)
        val isPaused = timerPrefs.getBoolean("timer_is_paused", false)
        val startTime = timerPrefs.getLong("timer_start_time", 0L)
        val accumulated = timerPrefs.getLong("timer_accumulated", 0L)
        val workplace = timerPrefs.getString("timer_workplace", "Main Workplace") ?: "Main Workplace"
        val startFormatted = timerPrefs.getString("timer_start_formatted", "") ?: ""
        return ShiftTimerState(
            isRunning = isRunning,
            isPaused = isPaused,
            startTimeMillis = startTime,
            accumulatedDurationMillis = accumulated,
            workplace = workplace,
            startFormattedTime = startFormatted
        )
    }

    private fun persistTimerState(state: ShiftTimerState) {
        timerPrefs.edit().apply {
            putBoolean("timer_is_running", state.isRunning)
            putBoolean("timer_is_paused", state.isPaused)
            putLong("timer_start_time", state.startTimeMillis)
            putLong("timer_accumulated", state.accumulatedDurationMillis)
            putString("timer_workplace", state.workplace)
            putString("timer_start_formatted", state.startFormattedTime)
            apply()
        }
    }

    private val _timerState = MutableStateFlow(loadTimerState())
    val timerState: StateFlow<ShiftTimerState> = _timerState.asStateFlow()

    private val _timerTick = MutableStateFlow(System.currentTimeMillis())
    val timerTick: StateFlow<Long> = _timerTick.asStateFlow()

    init {
        SyncWorker.enqueuePeriodic(application)

        // Ticker to drive UI refresh every second while timer is running
        viewModelScope.launch {
            while (true) {
                if (_timerState.value.isRunning && !_timerState.value.isPaused) {
                    _timerTick.value = System.currentTimeMillis()
                }
                kotlinx.coroutines.delay(1000)
            }
        }

        // Coming back online is the natural moment to flush anything queued.
        viewModelScope.launch {
            networkMonitor.isOnlineFlow.collect { online ->
                if (online && userSettings.value.autoCloudSyncEnabled &&
                    authRepository.currentUid() != null
                ) {
                    SyncWorker.enqueueOneShot(getApplication())
                }
            }
        }
    }

    // --- Derived summaries ---

    private val _selectedWeekStart = MutableStateFlow(DateUtils.getStartOfWeek(LocalDate.now()))
    val selectedWeekStart: StateFlow<LocalDate> = _selectedWeekStart.asStateFlow()

    private val _selectedMonth = MutableStateFlow(YearMonth.now())
    val selectedMonth: StateFlow<YearMonth> = _selectedMonth.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedWorkplaceFilter = MutableStateFlow("ALL")
    val selectedWorkplaceFilter: StateFlow<String> = _selectedWorkplaceFilter.asStateFlow()

    val currentWeeklySummary: StateFlow<WeeklyPayrollSummary> = combine(
        allLogs, selectedWeekStart, selectedWorkplaceFilter
    ) { logs, weekStart, wpFilter ->
        val weekEnd = DateUtils.getEndOfWeek(weekStart)
        val weekLogs = logs.filter { log ->
            val date = DateUtils.parseDate(log.date)
            val inWeek = !date.isBefore(weekStart) && !date.isAfter(weekEnd)
            inWeek && (wpFilter == "ALL" || log.workplace == wpFilter)
        }.sortedBy { it.date }
        weekLogs.toWeeklySummary(weekStart, weekEnd)
    }.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000),
        emptyList<WorkLogEntity>().toWeeklySummary(
            DateUtils.getStartOfWeek(LocalDate.now()),
            DateUtils.getEndOfWeek(LocalDate.now())
        )
    )

    val currentMonthlySummary: StateFlow<MonthlyPayrollSummary> = combine(
        allLogs, selectedMonth, selectedWorkplaceFilter
    ) { logs, yearMonth, wpFilter ->
        val monthStart = DateUtils.getStartOfMonth(yearMonth)
        val monthEnd = DateUtils.getEndOfMonth(yearMonth)

        val monthLogs = logs.filter { log ->
            val date = DateUtils.parseDate(log.date)
            val inMonth = !date.isBefore(monthStart) && !date.isAfter(monthEnd)
            inMonth && (wpFilter == "ALL" || log.workplace == wpFilter)
        }.sortedBy { it.date }

        val daysCount = monthLogs.map { it.date }.distinct().size
        val totalEarn = monthLogs.sumOf { it.totalPay }

        val weeklySummaries = mutableListOf<WeeklyPayrollSummary>()
        var currWeekStart = DateUtils.getStartOfWeek(monthStart)
        while (!currWeekStart.isAfter(monthEnd)) {
            val currWeekEnd = DateUtils.getEndOfWeek(currWeekStart)
            val wLogs = monthLogs.filter {
                val date = DateUtils.parseDate(it.date)
                !date.isBefore(currWeekStart) && !date.isAfter(currWeekEnd)
            }
            weeklySummaries.add(wLogs.toWeeklySummary(currWeekStart, currWeekEnd))
            currWeekStart = currWeekStart.plusWeeks(1)
        }

        MonthlyPayrollSummary(
            yearMonth = yearMonth,
            logs = monthLogs,
            totalHours = monthLogs.sumOf { it.netHoursWorked },
            regularHours = monthLogs.sumOf { it.regularHours },
            overtimeHours = monthLogs.sumOf { it.overtimeHours },
            regularPay = monthLogs.sumOf { it.regularPay },
            overtimePay = monthLogs.sumOf { it.overtimePay },
            totalEarnings = totalEarn,
            paidEarnings = monthLogs.filter { it.isPaid }.sumOf { it.totalPay },
            unpaidEarnings = monthLogs.filter { !it.isPaid }.sumOf { it.totalPay },
            daysWorked = daysCount,
            averageEarningsPerDay = if (daysCount > 0) totalEarn / daysCount else 0.0,
            weeklyBreakdown = weeklySummaries
        )
    }.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000),
        MonthlyPayrollSummary(
            yearMonth = YearMonth.now(), logs = emptyList(), totalHours = 0.0,
            regularHours = 0.0, overtimeHours = 0.0, regularPay = 0.0, overtimePay = 0.0,
            totalEarnings = 0.0, paidEarnings = 0.0, unpaidEarnings = 0.0, daysWorked = 0,
            averageEarningsPerDay = 0.0, weeklyBreakdown = emptyList()
        )
    )

    val monthlyWorkplaceBreakdown: StateFlow<List<WorkplaceSummary>> = combine(
        allLogs, selectedMonth, userSettings
    ) { logs, yearMonth, settings ->
        val monthStart = DateUtils.getStartOfMonth(yearMonth)
        val monthEnd = DateUtils.getEndOfMonth(yearMonth)
        val monthLogs = logs.filter {
            val date = DateUtils.parseDate(it.date)
            !date.isBefore(monthStart) && !date.isAfter(monthEnd)
        }
        val monthTotalPay = monthLogs.sumOf { it.totalPay }

        (settings.workplaces + monthLogs.map { it.workplace }).distinct().map { wpName ->
            val wpLogs = monthLogs.filter { it.workplace == wpName }
            val totalPay = wpLogs.sumOf { it.totalPay }
            WorkplaceSummary(
                name = wpName,
                totalHours = wpLogs.sumOf { it.netHoursWorked },
                totalEarnings = totalPay,
                paidEarnings = wpLogs.filter { it.isPaid }.sumOf { it.totalPay },
                unpaidEarnings = wpLogs.filter { !it.isPaid }.sumOf { it.totalPay },
                shiftCount = wpLogs.size,
                sharePercentage = if (monthTotalPay > 0) (totalPay / monthTotalPay).toFloat() else 0f
            )
        }.sortedByDescending { it.totalEarnings }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private fun List<WorkLogEntity>.toWeeklySummary(
        start: LocalDate,
        end: LocalDate
    ) = WeeklyPayrollSummary(
        startOfWeek = start,
        endOfWeek = end,
        logs = this,
        totalHours = sumOf { it.netHoursWorked },
        regularHours = sumOf { it.regularHours },
        overtimeHours = sumOf { it.overtimeHours },
        regularPay = sumOf { it.regularPay },
        overtimePay = sumOf { it.overtimePay },
        totalEarnings = sumOf { it.totalPay },
        paidEarnings = filter { it.isPaid }.sumOf { it.totalPay },
        unpaidEarnings = filter { !it.isPaid }.sumOf { it.totalPay },
        daysWorked = map { it.date }.distinct().size
    )

    // --- Navigation / filters ---

    fun navigateWeek(weeksDelta: Long) {
        _selectedWeekStart.value = _selectedWeekStart.value.plusWeeks(weeksDelta)
    }

    fun jumpToCurrentWeek() {
        _selectedWeekStart.value = DateUtils.getStartOfWeek(LocalDate.now())
    }

    fun navigateMonth(monthsDelta: Long) {
        _selectedMonth.value = _selectedMonth.value.plusMonths(monthsDelta)
    }

    fun jumpToCurrentMonth() {
        _selectedMonth.value = YearMonth.now()
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectWorkplaceFilter(workplace: String) {
        _selectedWorkplaceFilter.value = workplace
    }

    // --- Shift CRUD ---

    fun addLog(log: WorkLogEntity) = viewModelScope.launch { repository.insertLog(log) }

    fun updateLog(log: WorkLogEntity) = viewModelScope.launch { repository.updateLog(log) }

    fun deleteLog(id: Int) = viewModelScope.launch { repository.deleteLog(id) }

    fun togglePaidStatus(log: WorkLogEntity) = viewModelScope.launch {
        repository.updateLog(log.copy(isPaid = !log.isPaid))
    }

    fun clearAllLogs() = viewModelScope.launch { repository.clearAll() }

    // --- Workplaces ---

    fun addWorkplace(newWorkplace: String) {
        val trimmed = newWorkplace.trim()
        if (trimmed.isEmpty()) return
        val current = userSettings.value
        if (!current.workplaces.contains(trimmed)) {
            saveSettings(current.copy(workplaces = current.workplaces + trimmed))
        }
    }

    fun removeWorkplace(workplaceName: String) {
        val current = userSettings.value
        if (!current.workplaces.contains(workplaceName)) return
        saveSettings(current.copy(workplaces = current.workplaces.filter { it != workplaceName }))
        if (_selectedWorkplaceFilter.value == workplaceName) _selectedWorkplaceFilter.value = "ALL"
    }

    fun renameWorkplace(oldName: String, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty() || oldName == trimmed) return
        val current = userSettings.value
        saveSettings(
            current.copy(
                workplaces = current.workplaces.map { if (it == oldName) trimmed else it }.distinct()
            )
        )
        viewModelScope.launch {
            repository.renameWorkplace(oldName, trimmed)
            if (_selectedWorkplaceFilter.value == oldName) _selectedWorkplaceFilter.value = trimmed
        }
    }

    fun saveSettings(settings: UserSettings) = repository.updateSettings(settings)

    // --- Account & sync ---

    fun signInWithGoogle(activityContext: Context) {
        viewModelScope.launch {
            when (val result = authRepository.signInWithGoogle(activityContext)) {
                is SignInResult.Success -> {
                    _manualSyncError.value = null
                    _transientMessage.value = "Signed in. Backing up your shifts…"
                    SyncWorker.enqueueOneShot(getApplication(), manual = true)
                }
                is SignInResult.Cancelled -> Unit
                is SignInResult.Failure -> {
                    _transientMessage.value = result.message
                }
            }
        }
    }

    /**
     * @param wipeLocalData when true the local database is cleared as well, for handing the
     *        phone on. Default keeps the data so signing out is not destructive.
     */
    fun signOut(wipeLocalData: Boolean = false) {
        viewModelScope.launch {
            authRepository.signOut()
            SyncWorker.cancelAll(getApplication())
            if (wipeLocalData) {
                repository.hardDeleteAll()
            }
            saveSettings(userSettings.value.copy(lastCloudSyncTimestamp = 0L, lastPullCursor = 0L))
            _manualSyncError.value = null
            _transientMessage.value =
                if (wipeLocalData) "Signed out and local data removed." else "Signed out. Your shifts stay on this device."
        }
    }

    fun syncNow() {
        val uid = authRepository.currentUid()
        if (uid == null) {
            _transientMessage.value = "Sign in first to back up to the cloud."
            return
        }
        _manualSyncError.value = null
        SyncWorker.enqueueOneShot(getApplication(), manual = true)
    }

    fun toggleAutoCloudSync(enabled: Boolean) {
        saveSettings(userSettings.value.copy(autoCloudSyncEnabled = enabled))
        if (enabled && authRepository.currentUid() != null) {
            SyncWorker.enqueueOneShot(getApplication())
        } else if (!enabled) {
            SyncWorker.cancelAll(getApplication())
        }
    }

    /**
     * Pulls the full remote history onto this device. Unlike the previous implementation, this
     * reports what actually happened - the old one launched a coroutine, ignored the outcome and
     * unconditionally returned true, so it always claimed success.
     */
    fun restoreFromCloud() {
        val uid = authRepository.currentUid()
        if (uid == null) {
            _transientMessage.value = "Sign in first to restore from the cloud."
            return
        }
        viewModelScope.launch {
            _transientMessage.value = "Restoring from cloud…"
            try {
                val count = syncEngine.restoreAll(uid)
                _transientMessage.value = if (count > 0) {
                    "Restored $count shift${if (count == 1) "" else "s"} from your account."
                } else {
                    "Your account has no saved shifts yet."
                }
            } catch (e: Exception) {
                _transientMessage.value = "Restore failed: ${e.localizedMessage ?: "unknown error"}"
            }
        }
    }

    fun consumeTransientMessage() {
        _transientMessage.value = null
    }

    // --- App lock ---

    fun unlockApp() {
        _isAppLocked.value = false
        _authErrorMessage.value = null
    }

    fun lockApp() {
        if (userSettings.value.appLockEnabled) _isAppLocked.value = true
    }

    fun setAuthErrorMessage(message: String?) {
        _authErrorMessage.value = message
    }

    fun refreshBiometricStatus() {
        _biometricStatus.value = BiometricAuthManager.checkBiometricAvailability(getApplication())
    }

    fun toggleAppLock(enabled: Boolean) {
        saveSettings(userSettings.value.copy(appLockEnabled = enabled))
        if (!enabled) _isAppLocked.value = false
    }

    fun toggleRequireAuthOnResume(enabled: Boolean) {
        saveSettings(userSettings.value.copy(requireAuthOnResume = enabled))
    }

    fun setSecurityPin(pin: String) = repository.setPin(pin)

    /** Verifies against the stored salted hash. There is no master PIN. */
    fun verifySecurityPin(inputPin: String): Boolean = repository.verifyPin(inputPin)

    // --- Shift Timer Controls ---

    fun startShiftTimer(workplace: String = "Main Workplace") {
        val now = System.currentTimeMillis()
        val newState = ShiftTimerState(
            isRunning = true,
            isPaused = false,
            startTimeMillis = now,
            accumulatedDurationMillis = 0L,
            workplace = workplace.ifBlank { "Main Workplace" },
            startFormattedTime = ShiftTimerState.formatTimestamp(now)
        )
        _timerState.value = newState
        persistTimerState(newState)
        _timerTick.value = now
    }

    fun pauseShiftTimer() {
        val current = _timerState.value
        if (!current.isRunning || current.isPaused) return
        val now = System.currentTimeMillis()
        val totalAccumulated = current.accumulatedDurationMillis + (now - current.startTimeMillis).coerceAtLeast(0L)
        val newState = current.copy(
            isPaused = true,
            accumulatedDurationMillis = totalAccumulated,
            startTimeMillis = 0L
        )
        _timerState.value = newState
        persistTimerState(newState)
        _timerTick.value = now
    }

    fun resumeShiftTimer() {
        val current = _timerState.value
        if (!current.isRunning || !current.isPaused) return
        val now = System.currentTimeMillis()
        val newState = current.copy(
            isPaused = false,
            startTimeMillis = now
        )
        _timerState.value = newState
        persistTimerState(newState)
        _timerTick.value = now
    }

    fun resetShiftTimer() {
        val newState = ShiftTimerState()
        _timerState.value = newState
        persistTimerState(newState)
        _timerTick.value = System.currentTimeMillis()
    }

    /**
     * Stops the timer, resets state, and returns the calculated total hours and workplace.
     */
    fun endShiftTimer(): Pair<Double, String> {
        val current = _timerState.value
        val now = System.currentTimeMillis()
        val calculatedHours = current.currentElapsedHours(now)
        val wp = current.workplace
        resetShiftTimer()
        return Pair(calculatedHours, wp)
    }

    // --- Local file backup ---

    fun exportBackupJson(): String {
        val json = DatabaseBackupManager.exportToJson(allLogs.value, userSettings.value)
        DatabaseBackupManager.saveAutoBackup(getApplication(), json)
        return json
    }

    fun restoreFromBackupJson(jsonString: String): Boolean {
        return try {
            val (restoredSettings, restoredLogs) = DatabaseBackupManager.importFromJson(jsonString)
            viewModelScope.launch {
                restoredSettings?.let {
                    // Keep this device's account/lock state; only take the payroll preferences.
                    val current = settingsRepo.settings.value
                    repository.updateSettings(
                        current.copy(
                            defaultWageType = it.defaultWageType,
                            defaultWageRate = it.defaultWageRate,
                            defaultOvertimeThreshold = it.defaultOvertimeThreshold,
                            defaultOvertimeMultiplier = it.defaultOvertimeMultiplier,
                            currencySymbol = it.currencySymbol,
                            monthlyTargetEarnings = it.monthlyTargetEarnings,
                            payPeriodType = it.payPeriodType,
                            workplaces = it.workplaces
                        )
                    )
                }
                if (restoredLogs.isNotEmpty()) repository.insertLogs(restoredLogs)
            }
            DatabaseBackupManager.saveAutoBackup(getApplication(), jsonString)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun restoreFromAutoBackup(): Boolean {
        val backupJson = DatabaseBackupManager.readAutoBackup(getApplication())
        return if (!backupJson.isNullOrBlank()) restoreFromBackupJson(backupJson) else false
    }

    fun populateSampleData() {
        viewModelScope.launch {
            val settings = userSettings.value
            val rate = if (settings.currencySymbol == "₩" && settings.defaultWageRate < 100.0) {
                25000.0
            } else {
                settings.defaultWageRate
            }
            val today = LocalDate.now()
            val workplaces = settings.workplaces.ifEmpty {
                listOf("Main Workplace", "Secondary Job", "Freelance")
            }
            val notes = listOf(
                "Regular Shift - Main Project",
                "Overtime - Inspection & Review",
                "On-site Supervision",
                "Client Site Operations",
                "Standard Workday"
            )
            val sampleLogs = mutableListOf<WorkLogEntity>()

            for (i in 0..28) {
                val date = today.minusDays(i.toLong())
                val dayOfWeek = date.dayOfWeek.value
                if (dayOfWeek <= 5) {
                    val wpIndex = i % workplaces.size
                    val hours = when (i % 5) {
                        0 -> 8.0
                        1 -> 9.5
                        2 -> 10.0
                        3 -> 8.5
                        else -> 8.0
                    }
                    val wpRate = when (wpIndex) {
                        1 -> rate * 0.9
                        2 -> rate * 1.2
                        else -> rate
                    }
                    sampleLogs.add(
                        WorkLogEntity(
                            date = DateUtils.formatDate(date),
                            hoursWorked = hours,
                            wageType = settings.defaultWageType,
                            wageRate = wpRate,
                            overtimeThresholdHours = settings.defaultOvertimeThreshold,
                            overtimeMultiplier = settings.defaultOvertimeMultiplier,
                            note = notes[i % 5],
                            workplace = workplaces[wpIndex],
                            isPaid = i > 10
                        )
                    )
                } else if (i % 6 == 0) {
                    sampleLogs.add(
                        WorkLogEntity(
                            date = DateUtils.formatDate(date),
                            hoursWorked = 6.0,
                            wageType = settings.defaultWageType,
                            wageRate = rate * 1.1,
                            overtimeThresholdHours = 4.0,
                            overtimeMultiplier = 1.5,
                            note = "Weekend Special Project",
                            workplace = workplaces.getOrElse(1) { "Secondary Job" },
                            isPaid = true
                        )
                    )
                }
            }
            repository.insertLogs(sampleLogs)
        }
    }
}
