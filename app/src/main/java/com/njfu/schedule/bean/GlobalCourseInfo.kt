package com.njfu.schedule.bean

data class GlobalCourseInfo(
    val courseName: String,
    val teacher: String,
    val room: String,
    val weeksStr: String,
    val day: Int,          // 1-7 (星期一到日)
    val sectionsStr: String,
    val className: String  // 上课班级（如果有）
)
