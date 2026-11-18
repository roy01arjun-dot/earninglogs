package com.example.data

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.util.DateUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Daily "log your shift" reminder, shown once a day between 11:00 and 11:59 PM.
 *
 * - Uses an inexact alarm that is allowed while the phone is idle, so no "exact alarm" permission is
 *   needed. Android may delay it by a few minutes; anything that would land after 11:59 PM is skipped.
 * - Stays quiet if a shift is already logged for that day.
 * - Rescheduled after it fires, after a reboot, an app update, or a time/time-zone change.
 */
object DailyReminder {
    const val CHANNEL_ID = "daily_log_reminder"
    const val EXTRA_OPEN_NEW_SHIFT = "com.example.OPEN_NEW_SHIFT"
    private const val ACTION_REMIND = "com.example.action.DAILY_LOG_REMINDER"
    private const val EXTRA_FOR_DATE = "for_date"
    private const val REQUEST_ALARM = 2300
    private const val NOTIFICATION_ID = 2300

    val WINDOW_START: LocalTime = LocalTime.of(23, 0)
    val WINDOW_END: LocalTime = LocalTime.of(23, 59, 59)

    fun canPostNotifications(context: Context): Boolean =
        (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    /** Turn the reminder on/off according to the user's setting. Safe to call often. */
    fun sync(context: Context, enabled: Boolean) {
        if (enabled) schedule(context) else cancel(context)
    }

    private fun alarmIntent(context: Context, forDate: LocalDate): PendingIntent =
        PendingIntent.getBroadcast(
            context, REQUEST_ALARM,
            Intent(context, DailyReminderReceiver::class.java)
                .setAction(ACTION_REMIND)
                .putExtra(EXTRA_FOR_DATE, DateUtils.formatDate(forDate)),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    /** The next 11 PM slot we haven't reminded for yet. */
    internal fun nextReminderDate(now: LocalDateTime, lastShown: LocalDate?): LocalDate {
        val today = now.toLocalDate()
        val todayStillOpen = now.toLocalTime() < WINDOW_END.minusMinutes(5) && lastShown != today
        return if (todayStillOpen) today else today.plusDays(1)
    }

    fun schedule(context: Context) {
        val app = context.applicationContext
        createChannel(app)
        val now = LocalDateTime.now()
        val date = nextReminderDate(now, lastShown(app))
        // 11:00 PM on that date, or right now if we're already inside today's window.
        val at = maxOf(date.atTime(WINDOW_START), now.plusSeconds(30))
        val triggerAt = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val am = app.getSystemService(AlarmManager::class.java) ?: return
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, alarmIntent(app, date))
    }

    fun cancel(context: Context) {
        val app = context.applicationContext
        app.getSystemService(AlarmManager::class.java)?.cancel(alarmIntent(app, LocalDate.now()))
        NotificationManagerCompat.from(app).cancel(NOTIFICATION_ID)
    }

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(CHANNEL_ID, "Daily shift reminder", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "A reminder at 11 PM to log today's shift"
        }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    // ---- remembers the last day a reminder was shown, so it never fires twice a day
    private fun prefs(context: Context) = context.getSharedPreferences("daily_reminder", Context.MODE_PRIVATE)
    private fun lastShown(context: Context): LocalDate? =
        prefs(context).getString("last_shown", null)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    private fun markShown(context: Context, date: LocalDate) =
        prefs(context).edit().putString("last_shown", date.toString()).apply()

    /** Called by the receiver when the alarm fires. */
    internal suspend fun onAlarm(context: Context, forDateText: String?) {
        val app = context.applicationContext
        AppContainer.init(app)
        if (!AppContainer.settingsRepo.current.dailyReminderEnabled) return
        val now = LocalDateTime.now()
        val forDate = forDateText?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: now.toLocalDate()
        val inWindow = now.toLocalDate() == forDate && now.toLocalTime() <= WINDOW_END
        try {
            if (inWindow && lastShown(app) != forDate) {
                val alreadyLogged = AppContainer.repository.countShiftsOn(DateUtils.formatDate(forDate)) > 0
                if (!alreadyLogged && canPostNotifications(app)) show(app)
                markShown(app, forDate)
            }
        } finally {
            schedule(app) // always line up tomorrow's reminder
        }
    }

    @Suppress("MissingPermission") // checked by canPostNotifications()
    private fun show(context: Context) {
        createChannel(context)
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java)
                .putExtra(EXTRA_OPEN_NEW_SHIFT, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_reminder)
            .setColor(0xFF5140D6.toInt())
            .setContentTitle("Did you work today?")
            .setContentText("Take a moment to log today's shift so your pay stays up to date.")
            .setStyle(NotificationCompat.BigTextStyle().bigText("Take a moment to log today's shift so your pay stays up to date."))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(open)
            .addAction(0, "Log shift", open)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }
}

/** Receives the reminder alarm, plus the system events after which alarms must be set again. */
class DailyReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    "com.example.action.DAILY_LOG_REMINDER" ->
                        DailyReminder.onAlarm(context, intent.getStringExtra("for_date"))
                    else -> { // boot, app update, time or time-zone change
                        AppContainer.init(context.applicationContext)
                        DailyReminder.sync(context, AppContainer.settingsRepo.current.dailyReminderEnabled)
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }
}
