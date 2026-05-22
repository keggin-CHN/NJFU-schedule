package com.njfu.schedule.bean

import android.content.Context
import android.content.SharedPreferences

/**
 * 南京林业大学作息时间表（可自定义修改）
 */
object TimeNode {
    data class NodeTime(val node: Int, var start: String, var end: String)

    private val defaultTimes = listOf(
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

    var times: MutableList<NodeTime> = defaultTimes.toMutableList()
        private set

    fun load(context: Context) {
        val prefs = context.getSharedPreferences("time_settings", Context.MODE_PRIVATE)
        times = (1..11).map { node ->
            val defaultNode = defaultTimes[node - 1]
            NodeTime(
                node,
                prefs.getString("start_$node", defaultNode.start) ?: defaultNode.start,
                prefs.getString("end_$node", defaultNode.end) ?: defaultNode.end
            )
        }.toMutableList()
    }

    fun save(context: Context) {
        val prefs = context.getSharedPreferences("time_settings", Context.MODE_PRIVATE)
        val editor = prefs.edit()
        for (t in times) {
            editor.putString("start_${t.node}", t.start)
            editor.putString("end_${t.node}", t.end)
        }
        editor.apply()
    }

    fun getStartTime(node: Int): String = times.getOrNull(node - 1)?.start ?: ""
    fun getEndTime(node: Int): String = times.getOrNull(node - 1)?.end ?: ""
}
