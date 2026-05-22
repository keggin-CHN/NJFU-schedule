package com.njfu.schedule.bean

/**
 * 南京林业大学作息时间表
 */
object TimeNode {
    data class NodeTime(val node: Int, val start: String, val end: String)

    val times = listOf(
        NodeTime(1, "08:00", "08:45"),
        NodeTime(2, "08:55", "09:40"),
        NodeTime(3, "10:00", "10:45"),
        NodeTime(4, "10:55", "11:40"),
        NodeTime(5, "14:00", "14:45"),
        NodeTime(6, "14:55", "15:40"),
        NodeTime(7, "16:00", "16:45"),
        NodeTime(8, "16:55", "17:40"),
        NodeTime(9, "19:00", "19:45"),
        NodeTime(10, "19:55", "20:40"),
        NodeTime(11, "20:50", "21:35"),
    )

    fun getStartTime(node: Int): String = times.getOrNull(node - 1)?.start ?: ""
    fun getEndTime(node: Int): String = times.getOrNull(node - 1)?.end ?: ""
}
