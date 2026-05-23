package com.njfu.schedule

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.njfu.schedule.bean.CourseBaseBean
import com.njfu.schedule.bean.CourseDetailBean
import com.njfu.schedule.bean.TableBean
import com.njfu.schedule.dao.CourseDao

@Database(
    entities = [CourseBaseBean::class, CourseDetailBean::class, TableBean::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun courseDao(): CourseDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                addColumnIfMissing(database, "CourseDetailBean", "customStartTime", "TEXT")
                addColumnIfMissing(database, "CourseDetailBean", "customEndTime", "TEXT")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                addColumnIfMissing(database, "CourseDetailBean", "customStartTime", "TEXT")
                addColumnIfMissing(database, "CourseDetailBean", "customEndTime", "TEXT")
            }
        }

        private fun addColumnIfMissing(
            database: SupportSQLiteDatabase,
            tableName: String,
            columnName: String,
            columnType: String
        ) {
            val cursor = database.query("PRAGMA table_info(`$tableName`)")
            cursor.use {
                while (it.moveToNext()) {
                    val nameIndex = it.getColumnIndex("name")
                    if (nameIndex >= 0 && it.getString(nameIndex) == columnName) return
                }
            }
            database.execSQL("ALTER TABLE `$tableName` ADD COLUMN `$columnName` $columnType")
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "njfu_schedule"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
