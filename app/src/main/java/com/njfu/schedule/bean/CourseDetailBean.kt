package com.njfu.schedule.bean

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    foreignKeys = [ForeignKey(
        entity = CourseBaseBean::class,
        parentColumns = ["id", "tableId"],
        childColumns = ["id", "tableId"],
        onUpdate = ForeignKey.CASCADE,
        onDelete = ForeignKey.CASCADE
    )],
    primaryKeys = ["day", "startNode", "startWeek", "type", "tableId", "id"],
    indices = [Index(value = ["id", "tableId"])]
)
data class CourseDetailBean(
    var id: Int,
    var day: Int,           // 1-7 (周一到周日)
    var room: String?,
    var teacher: String?,
    var startNode: Int,     // 开始节次
    var step: Int,          // 持续节数
    var startWeek: Int,     // 开始周
    var endWeek: Int,       // 结束周
    var type: Int,          // 0=每周, 1=单周, 2=双周
    var tableId: Int
)
