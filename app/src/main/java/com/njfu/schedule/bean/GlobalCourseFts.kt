package com.njfu.schedule.bean

import androidx.room.Entity
import androidx.room.Fts4

/**
 * FTS4 全文搜索虚拟表，镜像 GlobalCourseEntity 的可搜索字段。
 * 使用 contentEntity 与主表关联，Room 自动管理触发器同步。
 * 注意：默认 tokenizer 对中文按整词匹配；拼音搜索由 pinyinName 字段在内存层支持。
 */
@Fts4(contentEntity = GlobalCourseEntity::class)
@Entity(tableName = "global_courses_fts")
data class GlobalCourseFts(
    val entityName: String,
    val courseName: String,
    val teacher: String,
    val room: String,
    val className: String,
    val collegeName: String,
    val typeLabel: String,
    val rawText: String
)
