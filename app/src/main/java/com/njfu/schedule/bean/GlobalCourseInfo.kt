package com.njfu.schedule.bean

data class GlobalCourseInfo(
    val courseName: String,
    val teacher: String,
    val room: String,
    val weeksStr: String,
    val day: Int,          
    val sectionsStr: String,
    val className: String,
    var collegeName: String = ""
)
