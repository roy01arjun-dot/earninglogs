package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Converters {
    @TypeConverter
    fun fromWageType(value: WageType): String = value.name

    @TypeConverter
    fun toWageType(value: String): WageType = try {
        WageType.valueOf(value)
    } catch (e: Exception) {
        WageType.HOURLY
    }
}

@Database(entities = [WorkLogEntity::class], version = 12, exportSchema = false)
@TypeConverters(Converters::class)
abstract class WorkLogDatabase : RoomDatabase() {
    abstract fun workLogDao(): WorkLogDao

    companion object {
        @Volatile
        private var INSTANCE: WorkLogDatabase? = null

        /**
         * v11 -> v12: add sync columns and drop the unused work_log_entries table.
         * Existing shifts are KEPT (the old build silently wiped the database
         * whenever the schema changed).
         */
        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE work_logs ADD COLUMN uuid TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE work_logs ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE work_logs ADD COLUMN isDeleted INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE work_logs ADD COLUMN pendingSync INTEGER NOT NULL DEFAULT 1")
                // Give every existing row a random UUID (v4-style hex string).
                db.execSQL(
                    "UPDATE work_logs SET uuid = lower(hex(randomblob(4))) || '-' || " +
                        "lower(hex(randomblob(2))) || '-4' || substr(lower(hex(randomblob(2))),2) || '-' || " +
                        "substr('89ab', abs(random()) % 4 + 1, 1) || substr(lower(hex(randomblob(2))),2) || '-' || " +
                        "lower(hex(randomblob(6))) WHERE uuid = ''"
                )
                db.execSQL("UPDATE work_logs SET updatedAt = CAST(strftime('%s','now') AS INTEGER) * 1000 WHERE updatedAt = 0")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_work_logs_uuid ON work_logs (uuid)")
                db.execSQL("DROP TABLE IF EXISTS work_log_entries")
            }
        }

        fun getDatabase(context: Context): WorkLogDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    WorkLogDatabase::class.java,
                    "work_hours_database"
                )
                    .addMigrations(MIGRATION_11_12)
                    // Only very old (< v11) prototype databases are rebuilt.
                    .fallbackToDestructiveMigrationFrom(true, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
                    .fallbackToDestructiveMigrationOnDowngrade(true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
