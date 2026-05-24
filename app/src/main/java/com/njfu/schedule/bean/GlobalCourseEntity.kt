package com.njfu.schedule.bean

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "global_courses")
data class GlobalCourseEntity(
    @PrimaryKey(autoGenerate = true)
    val uid: Long = 0,
    val type: String,
    @ColumnInfo(defaultValue = "''")
    val typeLabel: String = "",
    @ColumnInfo(defaultValue = "''")
    val term: String = "",
    @ColumnInfo(defaultValue = "''")
    val entityName: String = "",
    val courseName: String,
    val teacher: String,
    val room: String,
    val weeksStr: String,
    val day: Int,
    val sectionsStr: String,
    val className: String,
    @ColumnInfo(defaultValue = "''")
    val collegeName: String = "",
    @ColumnInfo(defaultValue = "''")
    val sectionNumbers: String = "",
    @ColumnInfo(defaultValue = "0")
    val slotIndex: Int = 0,
    @ColumnInfo(defaultValue = "0")
    val tableIndex: Int = 0,
    @ColumnInfo(defaultValue = "0")
    val rowIndex: Int = 0,
    @ColumnInfo(defaultValue = "0")
    val colIndex: Int = 0,
    @ColumnInfo(defaultValue = "''")
    val rawText: String = "",
    @ColumnInfo(defaultValue = "''")
    val rawHtml: String = "",
    @ColumnInfo(defaultValue = "''")
    val rawLinesJson: String = ""
)
