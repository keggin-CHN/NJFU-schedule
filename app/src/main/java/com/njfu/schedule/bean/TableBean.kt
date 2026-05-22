package com.njfu.schedule.bean

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class TableBean(
    @PrimaryKey(autoGenerate = true)
    var id: Int = 0,
    var tableName: String = "",
    var nodes: Int = 11,
    var startDate: String = "2025-02-24",  // 学期开始日期
    var maxWeek: Int = 20,
    var currentWeek: Int = 1,
    var showSat: Boolean = true,
    var showSun: Boolean = true,
    var studentName: String = "",
    var studentId: String = ""
)
