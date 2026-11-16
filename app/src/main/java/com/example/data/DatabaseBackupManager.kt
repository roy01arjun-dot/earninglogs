package com.example.data

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.time.Instant

object DatabaseBackupManager {

    private const val AUTO_BACKUP_FILE_NAME = "work_logs_backup.json"

    fun exportToJson(
        logs: List<WorkLogEntity>,
        settings: UserSettings
    ): String {
        val root = JSONObject()
        root.put("version", 2)
        root.put("backupDate", Instant.now().toString())

        // User Settings
        val settingsObj = JSONObject().apply {
            put("defaultWageType", settings.defaultWageType.name)
            put("defaultWageRate", settings.defaultWageRate)
            put("defaultOvertimeThreshold", settings.defaultOvertimeThreshold)
            put("defaultOvertimeMultiplier", settings.defaultOvertimeMultiplier)
            put("currencySymbol", settings.currencySymbol)
            put("monthlyTargetEarnings", settings.monthlyTargetEarnings)
            put("payPeriodType", settings.payPeriodType)
            put("workplaces", JSONArray(settings.workplaces))
            put("defaultTaxRate", settings.defaultTaxRate)
        }
        root.put("settings", settingsObj)

        // Work Logs
        val logsArray = JSONArray()
        logs.forEach { log ->
            val logObj = JSONObject().apply {
                put("id", log.id)
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
                put("uuid", log.uuid)
            }
            logsArray.put(logObj)
        }
        root.put("workLogs", logsArray)

        return root.toString(2)
    }

    fun importFromJson(jsonString: String): Pair<UserSettings?, List<WorkLogEntity>> {
        val root = JSONObject(jsonString)

        var settings: UserSettings? = null
        if (root.has("settings")) {
            val sObj = root.getJSONObject("settings")
            val wageTypeStr = sObj.optString("defaultWageType", WageType.HOURLY.name)
            val wageType = try { WageType.valueOf(wageTypeStr) } catch (e: Exception) { WageType.HOURLY }

            val wpList = mutableListOf<String>()
            if (sObj.has("workplaces")) {
                val wpArr = sObj.getJSONArray("workplaces")
                for (k in 0 until wpArr.length()) {
                    wpList.add(wpArr.getString(k))
                }
            }
            val finalWpList = wpList.ifEmpty { listOf("Main Workplace") }

            settings = UserSettings(
                defaultWageType = wageType,
                defaultWageRate = sObj.optDouble("defaultWageRate", 25000.0),
                defaultOvertimeThreshold = sObj.optDouble("defaultOvertimeThreshold", 8.0),
                defaultOvertimeMultiplier = sObj.optDouble("defaultOvertimeMultiplier", 1.5),
                currencySymbol = sObj.optString("currencySymbol", "₩"),
                monthlyTargetEarnings = sObj.optDouble("monthlyTargetEarnings", 3500000.0),
                payPeriodType = sObj.optString("payPeriodType", "MONTHLY"),
                workplaces = finalWpList,
                defaultTaxRate = sObj.optDouble("defaultTaxRate", 3.3)
            )
        }

        val logs = mutableListOf<WorkLogEntity>()
        if (root.has("workLogs")) {
            val logsArray = root.getJSONArray("workLogs")
            for (i in 0 until logsArray.length()) {
                val item = logsArray.getJSONObject(i)
                val wageTypeStr = item.optString("wageType", WageType.HOURLY.name)
                val wageType = try { WageType.valueOf(wageTypeStr) } catch (e: Exception) { WageType.HOURLY }

                val uuid = item.optString("uuid", "").ifBlank { java.util.UUID.randomUUID().toString() }
                logs.add(
                    WorkLogEntity(
                        id = 0,
                        uuid = uuid,
                        date = item.getString("date"),
                        hoursWorked = item.optDouble("hoursWorked", 8.0),
                        wageType = wageType,
                        wageRate = item.optDouble("wageRate", 25000.0),
                        overtimeThresholdHours = item.optDouble("overtimeThresholdHours", 8.0),
                        overtimeMultiplier = item.optDouble("overtimeMultiplier", 1.5),
                        note = item.optString("note", ""),
                        breakMinutes = item.optInt("breakMinutes", 0),
                        isPaid = item.optBoolean("isPaid", false),
                        workplace = item.optString("workplace", "Main Workplace")
                    )
                )
            }
        }

        return Pair(settings, logs)
    }

    fun saveAutoBackup(context: Context, jsonString: String) {
        try {
            val file = File(context.filesDir, AUTO_BACKUP_FILE_NAME)
            file.writeText(jsonString)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun readAutoBackup(context: Context): String? {
        return try {
            val file = File(context.filesDir, AUTO_BACKUP_FILE_NAME)
            if (file.exists()) file.readText() else null
        } catch (e: Exception) {
            null
        }
    }

    fun writeToUri(context: Context, uri: Uri, content: String): Boolean {
        return try {
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(content.toByteArray(Charsets.UTF_8))
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun readFromUri(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).use { reader ->
                    reader.readText()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
