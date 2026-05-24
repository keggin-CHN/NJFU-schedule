package com.njfu.schedule.bean

data class GlobalCourseInfo(
    val courseName: String,
    val teacher: String,
    val room: String,
    val weeksStr: String,
    val day: Int,          
    val sectionsStr: String,
    val className: String,
    var collegeName: String = "",
    val type: String = "",
    val typeLabel: String = "",
    val term: String = "",
    val entityName: String = "",
    val sectionNumbers: String = "",
    val slotIndex: Int = 0,
    val tableIndex: Int = 0,
    val rowIndex: Int = 0,
    val colIndex: Int = 0,
    val rawText: String = "",
    val rawHtml: String = "",
    val rawLinesJson: String = ""
)
