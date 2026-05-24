package com.njfu.schedule.bean

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "global_courses")
data class GlobalCourseEntity(
    @PrimaryKey(autoGenerate = true)
    val uid: Long = 0,
    val type: String,
    val courseName: String,
    val teacher: String,
    val room: String,
    val weeksStr: String,
    val day: Int,
    val sectionsStr: String,
    val className: String,
    val collegeName: String = ""
)
