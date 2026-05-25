package com.njfu.schedule.widget

import android.content.Context
import android.graphics.Color
import com.njfu.schedule.AppDatabase
import com.njfu.schedule.bean.TimeNode
import com.njfu.schedule.utils.WeekUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

    /** 缓存上一次查询结果，避免重复读库 */
    @Volatile
    private var cachedTableId: Int? = null

    @Volatile
    private var cachedDay: Int = -1

    @Volatile
    private var cachedWeek: Int = -1

    @Volatile
    private var cachedAllCourses: List<WidgetCourse> = emptyList()

    fun invalidateCache() {
        cachedTableId = null
        cachedDay = -1
        cachedWeek = -1
        cachedAllCourses = emptyList()
    }

    suspend fun loadTodayCourses(context: Context): List<WidgetCourse> {
        return loadCourses(context, filterUpcoming = false)
    }

    suspend fun loadUpcomingCourses(context: Context): List<WidgetCourse> {
        return loadCourses(context, filterUpcoming = true)
    }

    private suspend fun loadCourses(context: Context, filterUpcoming: Boolean): List<WidgetCourse> =
        withContext(Dispatchers.IO) {
            try {
                TimeNode.load(context)
                val todayOfWeek = WeekUtils.getTodayOfWeek()
                val calendar = Calendar.getInstance()
                val nowTotalMin = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)

                val db = AppDatabase.getDatabase(context)
                val dao = db.courseDao()
                val table = dao.getFirstTable() ?: return@withContext emptyList()
                val currentWeek = WeekUtils.getCurrentWeek(table.startDate)

                // 判断缓存是否有效：同一天、同一周次、同一张课表
                val cacheValid = table.id == cachedTableId
                        && todayOfWeek == cachedDay
                        && currentWeek == cachedWeek
                        && cachedAllCourses.isNotEmpty()

                val allCourses: List<WidgetCourse> = if (cacheValid) {
                    cachedAllCourses
                } else {
                    // 只查当天的 detail，减少数据传输
                    val dayDetails = dao.getCourseDetailsByDay(table.id, todayOfWeek)
                    val allBases = dao.getCourseBaseById_sync(table.id)
                    val nameMap = allBases.associate { it.id to it.courseName }
                    val colorMap = allBases.associate { it.id to it.color }

                    dayDetails.filter { d ->
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
                    }.also {
                        // 更新缓存
                        cachedTableId = table.id
                        cachedDay = todayOfWeek
                        cachedWeek = currentWeek
                        cachedAllCourses = it
                    }
                }

                if (filterUpcoming) {
                    allCourses.filter { course ->
                        val endMin = parseMinutes(course.time.substringAfter("-")) ?: 0
                        endMin > nowTotalMin
                    }
                } else {
                    allCourses
                }
            } catch (_: Exception) {
                emptyList()
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
        return SimpleDateFormat("M月d日 E", Locale.CHINA).format(Date())
    }
}
