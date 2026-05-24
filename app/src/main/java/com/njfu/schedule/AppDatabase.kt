package com.njfu.schedule

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.njfu.schedule.bean.CourseBaseBean
import com.njfu.schedule.bean.CourseDetailBean
import com.njfu.schedule.bean.GlobalCourseEntity
import com.njfu.schedule.bean.TableBean
import com.njfu.schedule.dao.CourseDao
import com.njfu.schedule.dao.GlobalCourseDao

@Database(
    entities = [CourseBaseBean::class, CourseDetailBean::class, TableBean::class, GlobalCourseEntity::class],
    version = 7,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun courseDao(): CourseDao
    abstract fun globalCourseDao(): GlobalCourseDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                addColumnIfMissing(db, "CourseDetailBean", "customStartTime", "TEXT")
                addColumnIfMissing(db, "CourseDetailBean", "customEndTime", "TEXT")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                addColumnIfMissing(db, "CourseDetailBean", "customStartTime", "TEXT")
                addColumnIfMissing(db, "CourseDetailBean", "customEndTime", "TEXT")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `global_courses` (" +
                        "`uid` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`type` TEXT NOT NULL, " +
                        "`courseName` TEXT NOT NULL, " +
                        "`teacher` TEXT NOT NULL, " +
                        "`room` TEXT NOT NULL, " +
                        "`weeksStr` TEXT NOT NULL, " +
                        "`day` INTEGER NOT NULL, " +
                        "`sectionsStr` TEXT NOT NULL, " +
                        "`className` TEXT NOT NULL)"
                )
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                addColumnIfMissing(db, "global_courses", "collegeName", "TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                addColumnIfMissing(db, "global_courses", "typeLabel", "TEXT NOT NULL DEFAULT ''")
                addColumnIfMissing(db, "global_courses", "term", "TEXT NOT NULL DEFAULT ''")
                addColumnIfMissing(db, "global_courses", "entityName", "TEXT NOT NULL DEFAULT ''")
                addColumnIfMissing(db, "global_courses", "sectionNumbers", "TEXT NOT NULL DEFAULT ''")
                addColumnIfMissing(db, "global_courses", "slotIndex", "INTEGER NOT NULL DEFAULT 0")
                addColumnIfMissing(db, "global_courses", "tableIndex", "INTEGER NOT NULL DEFAULT 0")
                addColumnIfMissing(db, "global_courses", "rowIndex", "INTEGER NOT NULL DEFAULT 0")
                addColumnIfMissing(db, "global_courses", "colIndex", "INTEGER NOT NULL DEFAULT 0")
                addColumnIfMissing(db, "global_courses", "rawText", "TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                addColumnIfMissing(db, "global_courses", "rawHtml", "TEXT NOT NULL DEFAULT ''")
                addColumnIfMissing(db, "global_courses", "rawLinesJson", "TEXT NOT NULL DEFAULT ''")
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
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
