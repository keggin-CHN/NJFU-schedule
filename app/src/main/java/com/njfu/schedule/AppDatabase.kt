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
    version = 9,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun courseDao(): CourseDao

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
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `global_courses_new` (" +
                        "`uid` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`type` TEXT NOT NULL, " +
                        "`typeLabel` TEXT NOT NULL DEFAULT '', " +
                        "`term` TEXT NOT NULL DEFAULT '', " +
                        "`entityName` TEXT NOT NULL DEFAULT '', " +
                        "`courseName` TEXT NOT NULL, " +
                        "`teacher` TEXT NOT NULL, " +
                        "`room` TEXT NOT NULL, " +
                        "`weeksStr` TEXT NOT NULL, " +
                        "`day` INTEGER NOT NULL, " +
                        "`sectionsStr` TEXT NOT NULL, " +
                        "`className` TEXT NOT NULL, " +
                        "`collegeName` TEXT NOT NULL DEFAULT '', " +
                        "`sectionNumbers` TEXT NOT NULL DEFAULT '', " +
                        "`slotIndex` INTEGER NOT NULL DEFAULT 0, " +
                        "`tableIndex` INTEGER NOT NULL DEFAULT 0, " +
                        "`rowIndex` INTEGER NOT NULL DEFAULT 0, " +
                        "`colIndex` INTEGER NOT NULL DEFAULT 0, " +
                        "`rawText` TEXT NOT NULL DEFAULT '', " +
                        "`rawHtml` TEXT NOT NULL DEFAULT '', " +
                        "`rawLinesJson` TEXT NOT NULL DEFAULT '')"
                )
                db.execSQL(
                    "INSERT INTO `global_courses_new` (" +
                        "`uid`, `type`, `typeLabel`, `term`, `entityName`, `courseName`, `teacher`, `room`, " +
                        "`weeksStr`, `day`, `sectionsStr`, `className`, `collegeName`, `sectionNumbers`, " +
                        "`slotIndex`, `tableIndex`, `rowIndex`, `colIndex`, `rawText`, `rawHtml`, `rawLinesJson`) " +
                        "SELECT `uid`, `type`, `typeLabel`, `term`, `entityName`, `courseName`, `teacher`, `room`, " +
                        "`weeksStr`, `day`, `sectionsStr`, `className`, `collegeName`, `sectionNumbers`, " +
                        "`slotIndex`, `tableIndex`, `rowIndex`, `colIndex`, `rawText`, '', '' FROM `global_courses`"
                )
                db.execSQL("DROP TABLE `global_courses`")
                db.execSQL("ALTER TABLE `global_courses_new` RENAME TO `global_courses`")
            }
        }

        /**
         * v7 → v8: 添加索引 + FTS4 全文搜索表
         */
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 创建索引（IF NOT EXISTS 避免重复）
                db.execSQL("CREATE INDEX IF NOT EXISTS `idx_gc_type` ON `global_courses` (`type`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `idx_gc_type_entity` ON `global_courses` (`type`, `entityName`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `idx_gc_type_course` ON `global_courses` (`type`, `courseName`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `idx_gc_type_teacher` ON `global_courses` (`type`, `teacher`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `idx_gc_type_room` ON `global_courses` (`type`, `room`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `idx_gc_type_class` ON `global_courses` (`type`, `className`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `idx_gc_type_day` ON `global_courses` (`type`, `day`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `idx_gc_term` ON `global_courses` (`term`)")

                // 创建 FTS4 虚拟表（使用 content= 关联主表）
                db.execSQL(
                    "CREATE VIRTUAL TABLE IF NOT EXISTS `global_courses_fts` USING fts4(" +
                        "`entityName`, `courseName`, `teacher`, `room`, `className`, `collegeName`, `typeLabel`, `rawText`, " +
                        "content=`global_courses`)"
                )

                // 填充 FTS 索引
                db.execSQL(
                    "INSERT INTO `global_courses_fts`(`rowid`, `entityName`, `courseName`, `teacher`, `room`, `className`, `collegeName`, `typeLabel`, `rawText`) " +
                        "SELECT `uid`, `entityName`, `courseName`, `teacher`, `room`, `className`, `collegeName`, `typeLabel`, `rawText` FROM `global_courses`"
                )
            }
        }

        /** v8 → v9: 删除全局课表相关表 */
        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS `global_courses_fts`")
                db.execSQL("DROP TABLE IF EXISTS `global_courses`")
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
                ).addMigrations(
                    MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4,
                    MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7,
                    MIGRATION_7_8, MIGRATION_8_9
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
