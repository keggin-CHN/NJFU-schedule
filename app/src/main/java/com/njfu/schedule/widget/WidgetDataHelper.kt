package com.njfu.schedule.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import com.njfu.schedule.AppDatabase
import com.njfu.schedule.R
import com.njfu.schedule.bean.TimeNode
import com.njfu.schedule.utils.WeekUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.text.SimpleDateFormat
import java.util.*

data class WidgetCourse(
    val name: String,
    val time: String,
    val room: String,
    val teacher: String,
    val color: String
)

object WidgetDataHelper {
    fun loadTodayCourses(context: Context): List<WidgetCourse> {
        TimeNode.load(context)
        val todayOfWeek = WeekUtils.getTodayOfWeek()
        
        val calendar = Calendar.getInstance()
        val nowTotalMin = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)

        return runBlocking(Dispatchers.IO) {
            try {
                val db = AppDatabase.getDatabase(context)
                val dao = db.courseDao()
                val table = dao.getFirstTable() ?: return@runBlocking emptyList<WidgetCourse>()
                val currentWeek = WeekUtils.getCurrentWeek(table.startDate)

                val allDetails = dao.getCourseDetailsById_sync(table.id)
                val allBases = dao.getCourseBaseById_sync(table.id)
                val nameMap = allBases.associate { it.id to it.courseName }
                val colorMap = allBases.associate { it.id to it.color }

                allDetails.filter { d ->
                    d.day == todayOfWeek &&
                    d.startWeek <= currentWeek && d.endWeek >= currentWeek &&
                    (d.type == 0 || (d.type == 1 && currentWeek % 2 == 1) || (d.type == 2 && currentWeek % 2 == 0))
                }.sortedWith(compareBy(
                    { d -> parseMinutes(d.customStartTime ?: TimeNode.getStartTime(d.startNode)) ?: 0 },
                    { d -> parseMinutes(d.customEndTime ?: TimeNode.getEndTime(d.startNode + d.step - 1)) ?: 0 },
                    { d -> nameMap[d.id].orEmpty().lowercase(Locale.CHINA) }
                )).map { course ->
                    val startTime = course.customStartTime ?: TimeNode.getStartTime(course.startNode)
                    val endTime = course.customEndTime ?: TimeNode.getEndTime(course.startNode + course.step - 1)
                    WidgetCourse(
                        name = nameMap[course.id].orEmpty(),
                        time = "$startTime-$endTime",
                        room = course.room.orEmpty(),
                        teacher = course.teacher.orEmpty(),
                        color = colorMap[course.id].orEmpty()
                    )
                }
            } catch (_: Exception) {
                emptyList()
            }
        }
    }

    fun loadUpcomingCourses(context: Context): List<WidgetCourse> {
        TimeNode.load(context)
        val todayOfWeek = WeekUtils.getTodayOfWeek()
        
        val calendar = Calendar.getInstance()
        val nowTotalMin = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)

        return runBlocking(Dispatchers.IO) {
            try {
                val db = AppDatabase.getDatabase(context)
                val dao = db.courseDao()
                val table = dao.getFirstTable() ?: return@runBlocking emptyList<WidgetCourse>()
                val currentWeek = WeekUtils.getCurrentWeek(table.startDate)

                val allDetails = dao.getCourseDetailsById_sync(table.id)
                val allBases = dao.getCourseBaseById_sync(table.id)
                val nameMap = allBases.associate { it.id to it.courseName }
                val colorMap = allBases.associate { it.id to it.color }

                allDetails.filter { d ->
                    d.day == todayOfWeek &&
                    d.startWeek <= currentWeek && d.endWeek >= currentWeek &&
                    (d.type == 0 || (d.type == 1 && currentWeek % 2 == 1) || (d.type == 2 && currentWeek % 2 == 0))
                }.filter { d ->
                    val endTimeStr = d.customEndTime ?: TimeNode.getEndTime(d.startNode + d.step - 1)
                    val endMin = parseMinutes(endTimeStr) ?: 0
                    endMin > nowTotalMin
                }.sortedWith(compareBy(
                    { d -> parseMinutes(d.customStartTime ?: TimeNode.getStartTime(d.startNode)) ?: 0 },
                    { d -> parseMinutes(d.customEndTime ?: TimeNode.getEndTime(d.startNode + d.step - 1)) ?: 0 },
                    { d -> nameMap[d.id].orEmpty().lowercase(Locale.CHINA) }
                )).map { course ->
                    val startTime = course.customStartTime ?: TimeNode.getStartTime(course.startNode)
                    val endTime = course.customEndTime ?: TimeNode.getEndTime(course.startNode + course.step - 1)
                    WidgetCourse(
                        name = nameMap[course.id].orEmpty(),
                        time = "$startTime-$endTime",
                        room = course.room.orEmpty(),
                        teacher = course.teacher.orEmpty(),
                        color = colorMap[course.id].orEmpty()
                    )
                }
            } catch (_: Exception) {
                emptyList()
            }
        }
    }

    private fun parseMinutes(time: String): Int? {
        val parts = time.split(":")
        if (parts.size != 2) return null
        val hour = parts[0].toIntOrNull() ?: return null
        val minute = parts[1].toIntOrNull() ?: return null
        return hour * 60 + minute
    }

    fun parseColor(color: String, defaultColor: String = "#5B8DEF"): Int {
        return try {
            Color.parseColor(color.ifBlank { defaultColor })
        } catch (_: Exception) {
            Color.parseColor(defaultColor)
        }
    }

    fun todayText(): String {
        val dayNames = arrayOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
        val day = WeekUtils.getTodayOfWeek().coerceIn(1, 7)
        return SimpleDateFormat("M月d日 E", Locale.CHINA).format(Date())
    }
}
