package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.util.UUID

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
        private const val DB_NAME = "work_hours_database"

        /**
         * v11 -> v12: adds the sync columns and drops the unused `work_log_entries` table.
         *
         * Every pre-existing row is stamped `pendingSync = 1` with a fresh uuid so that the
         * first sync after signing in uploads the user's existing history rather than
         * stranding it on the device.
         */
        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE work_logs ADD COLUMN uuid TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE work_logs ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE work_logs ADD COLUMN deletedAt INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE work_logs ADD COLUMN pendingSync INTEGER NOT NULL DEFAULT 1")

                // Backfill a stable uuid per existing row. randomblob(16) gives us a distinct
                // value per row; hex() makes it a readable, index-friendly string.
                db.execSQL(
                    "UPDATE work_logs SET uuid = lower(hex(randomblob(16))) WHERE uuid = '' OR uuid IS NULL"
                )
                db.execSQL("UPDATE work_logs SET updatedAt = ${System.currentTimeMillis()} WHERE updatedAt = 0")

                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_work_logs_uuid ON work_logs (uuid)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_work_logs_pendingSync ON work_logs (pendingSync)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_work_logs_date ON work_logs (date)")

                db.execSQL("DROP TABLE IF EXISTS work_log_entries")
            }
        }

        @Volatile
        private var INSTANCE: WorkLogDatabase? = null

        fun getDatabase(context: Context): WorkLogDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    WorkLogDatabase::class.java,
                    DB_NAME
                )
                    .addMigrations(MIGRATION_11_12)
                    // Deliberately NOT fallbackToDestructiveMigration: a missing migration must
                    // fail loudly in testing rather than silently wipe a user's pay history.
                    .build()
                    .also { INSTANCE = it }
            }
        }

        /** Only for tests that need a throwaway instance. */
        fun newUuid(): String = UUID.randomUUID().toString()
    }
}
