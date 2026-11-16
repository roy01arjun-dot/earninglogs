package com.example.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max
import kotlin.math.round

data class ShiftTimerState(
    val isRunning: Boolean = false,
    val isPaused: Boolean = false,
    val startTimestamp: Long = 0L,
    val lastResumeTimestamp: Long = 0L,
    val pausedAccumulatedMs: Long = 0L,
    val workplace: String = "Main Workplace",
    val currentDurationMs: Long = 0L
) {
    val totalElapsedMs: Long
        get() = if (isRunning && !isPaused) {
            pausedAccumulatedMs + max(0L, System.currentTimeMillis() - lastResumeTimestamp)
        } else {
            pausedAccumulatedMs
        }

    val elapsedHoursDecimal: Double
        get() {
            val totalSeconds = totalElapsedMs / 1000.0
            val rawHours = totalSeconds / 3600.0
            return (round(rawHours * 100.0) / 100.0)
        }

    val formattedDuration: String
        get() {
            val totalSeconds = totalElapsedMs / 1000
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60
            return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
        }

    /** Day the shift started, so a shift that runs past midnight is logged on the right date. */
    val startDate: java.time.LocalDate
        get() = if (startTimestamp > 0) Instant.ofEpochMilli(startTimestamp).atZone(ZoneId.systemDefault()).toLocalDate()
        else java.time.LocalDate.now()

    val formattedStartTime: String
        get() = if (startTimestamp > 0) {
            try {
                val instant = Instant.ofEpochMilli(startTimestamp)
                val zonedDateTime = instant.atZone(ZoneId.systemDefault())
                val formatter = DateTimeFormatter.ofPattern("hh:mm a", Locale.getDefault())
                zonedDateTime.format(formatter)
            } catch (e: Exception) {
                ""
            }
        } else ""
}

class ShiftTimerManager(
    context: Context,
    private val scope: CoroutineScope
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("shift_timer_prefs", Context.MODE_PRIVATE)

    private val _timerState = MutableStateFlow(loadPersistedState())
    val timerState: StateFlow<ShiftTimerState> = _timerState.asStateFlow()

    private var tickerJob: Job? = null

    init {
        if (_timerState.value.isRunning && !_timerState.value.isPaused) {
            startTicker()
        }
    }

    private fun loadPersistedState(): ShiftTimerState {
        val isRunning = prefs.getBoolean("is_running", false)
        val isPaused = prefs.getBoolean("is_paused", false)
        val startTimestamp = prefs.getLong("start_timestamp", 0L)
        val lastResumeTimestamp = prefs.getLong("last_resume_timestamp", 0L)
        val pausedAccumulatedMs = prefs.getLong("paused_accumulated_ms", 0L)
        val workplace = prefs.getString("workplace", "Main Workplace") ?: "Main Workplace"

        val state = ShiftTimerState(
            isRunning = isRunning,
            isPaused = isPaused,
            startTimestamp = startTimestamp,
            lastResumeTimestamp = lastResumeTimestamp,
            pausedAccumulatedMs = pausedAccumulatedMs,
            workplace = workplace,
            currentDurationMs = 0L
        )
        return state.copy(currentDurationMs = state.totalElapsedMs)
    }

    private fun persistState(state: ShiftTimerState) {
        prefs.edit().apply {
            putBoolean("is_running", state.isRunning)
            putBoolean("is_paused", state.isPaused)
            putLong("start_timestamp", state.startTimestamp)
            putLong("last_resume_timestamp", state.lastResumeTimestamp)
            putLong("paused_accumulated_ms", state.pausedAccumulatedMs)
            putString("workplace", state.workplace)
            apply()
        }
    }

    fun startTimer(workplace: String) {
        val now = System.currentTimeMillis()
        val newState = ShiftTimerState(
            isRunning = true,
            isPaused = false,
            startTimestamp = now,
            lastResumeTimestamp = now,
            pausedAccumulatedMs = 0L,
            workplace = workplace,
            currentDurationMs = 0L
        )
        persistState(newState)
        _timerState.value = newState
        startTicker()
    }

    fun pauseTimer() {
        val current = _timerState.value
        if (!current.isRunning || current.isPaused) return
        val now = System.currentTimeMillis()
        val additional = max(0L, now - current.lastResumeTimestamp)
        val newAccumulated = current.pausedAccumulatedMs + additional
        val newState = current.copy(
            isPaused = true,
            pausedAccumulatedMs = newAccumulated,
            currentDurationMs = newAccumulated
        )
        persistState(newState)
        _timerState.value = newState
        stopTicker()
    }

    fun resumeTimer() {
        val current = _timerState.value
        if (!current.isRunning || !current.isPaused) return
        val now = System.currentTimeMillis()
        val newState = current.copy(
            isPaused = false,
            lastResumeTimestamp = now
        )
        persistState(newState)
        _timerState.value = newState
        startTicker()
    }

    fun setWorkplace(workplace: String) {
        val current = _timerState.value
        val newState = current.copy(workplace = workplace)
        persistState(newState)
        _timerState.value = newState
    }

    fun discardTimer() {
        stopTicker()
        val newState = ShiftTimerState()
        persistState(newState)
        _timerState.value = newState
    }

    /** Elapsed hours (2 decimals) without stopping the timer. */
    fun currentHours(): Double {
        val ms = _timerState.value.totalElapsedMs
        val rounded = round(ms / 3_600_000.0 * 100.0) / 100.0
        return if (ms > 1000L && rounded == 0.0) 0.01 else rounded
    }

    fun endTimer(): Double {
        val current = _timerState.value
        val totalMs = current.totalElapsedMs
        stopTicker()
        val emptyState = ShiftTimerState()
        persistState(emptyState)
        _timerState.value = emptyState

        val totalSeconds = totalMs / 1000.0
        val rawHours = totalSeconds / 3600.0
        val rounded = round(rawHours * 100.0) / 100.0
        // Minimum decimal representation for very short test runs
        return if (totalMs > 1000L && rounded == 0.0) 0.01 else rounded
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = scope.launch(Dispatchers.Default) {
            while (isActive) {
                val current = _timerState.value
                if (current.isRunning && !current.isPaused) {
                    _timerState.value = current.copy(currentDurationMs = current.totalElapsedMs)
                }
                delay(1000L)
            }
        }
    }

    private fun stopTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }
}
