package com.example.data

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.time.Instant
import java.util.UUID

/**
 * Local file export/import.
 *
 * This is a genuinely local feature - a JSON file the user saves wherever they like. It is
 * deliberately kept separate from cloud sync now that the two are no longer conflated: the
 * previous build wrote this same JSON to app-private storage, called the result a "cloud
 * vault", and reported it to the user as a successful cloud backup.
 */
object DatabaseBackupManager {

    private const val AUTO_BACKUP_FILE_NAME = "work_logs_backup.json"
    private const val FORMAT_VERSION = 2

    fun exportToJson(
        logs: List<WorkLogEntity>,
        settings: UserSettings
    ): String {
        val root = JSONObject()
        root.put("version", FORMAT_VERSION)
        root.put("backupDate", Instant.now().toString())

        val settingsObj = JSONObject().apply {
            put("defaultWageType", settings.defaultWageType.name)
            put("defaultWageRate", settings.defaultWageRate)
            put("defaultOvertimeThreshold", settings.defaultOvertimeThreshold)
            put("defaultOvertimeMultiplier", settings.defaultOvertimeMultiplier)
            put("currencySymbol", settings.currencySymbol)
            put("monthlyTargetEarnings", settings.monthlyTargetEarnings)
            put("payPeriodType", settings.payPeriodType)
            put("workplaces", JSONArray(settings.workplaces))
        }
        root.put("settings", settingsObj)

        val logsArray = JSONArray()
        logs.filter { it.deletedAt == null }.forEach { log ->
            logsArray.put(
                JSONObject().apply {
                    put("uuid", log.uuid)
                    put("date", log.date)
                    put("hoursWorked", log.hoursWorked)
                    put("wageType", log.wageType.name)
                    put("wageRate", log.wageRate)
                    put("overtimeThresholdHours", log.overtimeThresholdHours)
                    put("overtimeMultiplier", log.overtimeMultiplier)
                    put("note", log.note)
                    put("breakMinutes", log.breakMinutes)
                    put("isPaid", log.isPaid)
                    put("workplace", log.workplace)
                    put("updatedAt", log.updatedAt)
                }
            )
        }
        root.put("workLogs", logsArray)

        return root.toString(2)
    }

    /**
     * Accepts both the v1 format (int `id`, no sync fields) and v2. v1 rows get a fresh uuid so
     * importing an old backup on two devices doesn't collide.
     */
    fun importFromJson(jsonString: String): Pair<UserSettings?, List<WorkLogEntity>> {
        val root = JSONObject(jsonString)

        var settings: UserSettings? = null
        if (root.has("settings")) {
            val sObj = root.getJSONObject("settings")
            val wageType = runCatching {
                WageType.valueOf(sObj.optString("defaultWageType", WageType.HOURLY.name))
            }.getOrDefault(WageType.HOURLY)

            val wpList = mutableListOf<String>()
            if (sObj.has("workplaces")) {
                val wpArr = sObj.getJSONArray("workplaces")
                for (k in 0 until wpArr.length()) wpList.add(wpArr.getString(k))
            }

            settings = UserSettings(
                defaultWageType = wageType,
                defaultWageRate = sObj.optDouble("defaultWageRate", 25000.0),
                defaultOvertimeThreshold = sObj.optDouble("defaultOvertimeThreshold", 8.0),
                defaultOvertimeMultiplier = sObj.optDouble("defaultOvertimeMultiplier", 1.5),
                currencySymbol = sObj.optString("currencySymbol", "₩"),
                monthlyTargetEarnings = sObj.optDouble("monthlyTargetEarnings", 3500000.0),
                payPeriodType = sObj.optString("payPeriodType", "WEEKLY"),
                workplaces = wpList.ifEmpty { listOf("Main Workplace", "Secondary Job", "Freelance") }
            )
        }

        val logs = mutableListOf<WorkLogEntity>()
        if (root.has("workLogs")) {
            val logsArray = root.getJSONArray("workLogs")
            val now = System.currentTimeMillis()
            for (i in 0 until logsArray.length()) {
                val item = logsArray.getJSONObject(i)
                val date = item.optString("date", "")
                if (date.isBlank()) continue

                val wageType = runCatching {
                    WageType.valueOf(item.optString("wageType", WageType.HOURLY.name))
                }.getOrDefault(WageType.HOURLY)

                val uuid = item.optString("uuid", "").ifBlank { UUID.randomUUID().toString() }

                logs.add(
                    WorkLogEntity(
                        id = 0,
                        uuid = uuid,
                        date = date,
                        hoursWorked = item.optDouble("hoursWorked", 8.0),
                        wageType = wageType,
                        wageRate = item.optDouble("wageRate", 25000.0),
                        overtimeThresholdHours = item.optDouble("overtimeThresholdHours", 8.0),
                        overtimeMultiplier = item.optDouble("overtimeMultiplier", 1.5),
                        note = item.optString("note", ""),
                        breakMinutes = item.optInt("breakMinutes", 0),
                        isPaid = item.optBoolean("isPaid", false),
                        workplace = item.optString("workplace", "Main Workplace"),
                        updatedAt = item.optLong("updatedAt", now),
                        deletedAt = null,
                        pendingSync = true
                    )
                )
            }
        }

        return Pair(settings, logs)
    }

    fun saveAutoBackup(context: Context, jsonString: String) {
        runCatching { File(context.filesDir, AUTO_BACKUP_FILE_NAME).writeText(jsonString) }
    }

    fun readAutoBackup(context: Context): String? = runCatching {
        val file = File(context.filesDir, AUTO_BACKUP_FILE_NAME)
        if (file.exists()) file.readText() else null
    }.getOrNull()

    fun writeToUri(context: Context, uri: Uri, content: String): Boolean = runCatching {
        context.contentResolver.openOutputStream(uri)?.use { it.write(content.toByteArray(Charsets.UTF_8)) }
        true
    }.getOrDefault(false)

    fun readFromUri(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            BufferedReader(InputStreamReader(input, Charsets.UTF_8)).use { it.readText() }
        }
    }.getOrNull()
}
