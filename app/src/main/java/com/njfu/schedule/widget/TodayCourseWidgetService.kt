package com.njfu.schedule.widget

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.njfu.schedule.R
import com.njfu.schedule.bean.TimeNode
import com.njfu.schedule.AppDatabase
import com.njfu.schedule.utils.WeekUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.util.Calendar
import java.util.Locale

data class WidgetCourse(
    val name: String,
    val time: String,
    val room: String,
    val teacher: String,
    val color: String
)

class TodayCourseWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return TodayCourseWidgetFactory(this.applicationContext)
    }
}

class TodayCourseWidgetFactory(private val context: Context) : RemoteViewsService.RemoteViewsFactory {
    private var courseList = mutableListOf<WidgetCourse>()

    override fun onCreate() {
        // Init
    }

    override fun onDataSetChanged() {
        // Fetch data
        TimeNode.load(context)
        val todayOfWeek = WeekUtils.getTodayOfWeek()
        
        runBlocking(Dispatchers.IO) {
            try {
                val db = AppDatabase.getDatabase(context)
                val dao = db.courseDao()
                val table = dao.getFirstTable()
                if (table == null) {
                    courseList.clear()
                    return@runBlocking
                }
                
                val currentWeek = WeekUtils.getCurrentWeek(table.startDate)
                val allDetails = dao.getCourseDetailsById_sync(table.id)
                val allBases = dao.getCourseBaseById_sync(table.id)

                val nameMap = allBases.associate { it.id to it.courseName }
                val colorMap = allBases.associate { it.id to it.color }
                val now = currentMinutes()

                val newList = allDetails.filter { d ->
                    val end = d.customEndTime ?: TimeNode.getEndTime(d.startNode + d.step - 1)
                    val endMinutes = parseMinutes(end) ?: Int.MAX_VALUE

                    d.day == todayOfWeek &&
                    d.startWeek <= currentWeek && d.endWeek >= currentWeek &&
                    (d.type == 0 || (d.type == 1 && currentWeek % 2 == 1) || (d.type == 2 && currentWeek % 2 == 0)) &&
                    endMinutes > now
                }.sortedWith(
                    compareBy(
                        { d -> parseMinutes(d.customStartTime ?: TimeNode.getStartTime(d.startNode)) ?: 0 },
                        { d -> parseMinutes(d.customEndTime ?: TimeNode.getEndTime(d.startNode + d.step - 1)) ?: 0 },
                        { d -> nameMap[d.id].orEmpty().lowercase(Locale.CHINA) }
                    )
                ).map { d ->
                    val start = d.customStartTime ?: TimeNode.getStartTime(d.startNode)
                    val end = d.customEndTime ?: TimeNode.getEndTime(d.startNode + d.step - 1)
                    WidgetCourse(
                        name = nameMap[d.id] ?: "",
                        time = preventTimeWrap("$start-$end"),
                        room = d.room.orEmpty(),
                        teacher = d.teacher.orEmpty(),
                        color = colorMap[d.id] ?: "#5B8DEF"
                    )
                }
                courseList.clear()
                courseList.addAll(newList)
            } catch (e: Exception) {
                courseList.clear()
            }
        }
    }

    override fun onDestroy() {
        courseList.clear()
    }

    override fun getCount(): Int = courseList.size

    override fun getViewAt(position: Int): RemoteViews {
        if (position < 0 || position >= courseList.size) {
            return RemoteViews(context.packageName, R.layout.widget_course_item)
        }
        val course = courseList[position]
        val rv = RemoteViews(context.packageName, R.layout.widget_course_item)
        
        rv.setTextViewText(R.id.tv_course_name, course.name)
        rv.setTextViewText(R.id.tv_course_time, course.time)
        rv.setTextViewText(R.id.tv_course_room, course.room.ifBlank { "地点待定" })
        rv.setTextViewText(R.id.tv_course_teacher, course.teacher.ifBlank { "老师待定" })

        try {
            val colorInt = Color.parseColor(course.color)
            rv.setInt(R.id.view_course_color, "setBackgroundColor", colorInt)
            rv.setInt(R.id.tv_course_time, "setBackgroundColor", colorInt)
            rv.setTextColor(R.id.tv_course_time, readableTextColor(colorInt))
        } catch (_: Exception) {}

        return rv
    }

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = position.toLong()
    override fun hasStableIds(): Boolean = true

    private fun currentMinutes(): Int {
        val now = Calendar.getInstance()
        return now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
    }

    private fun parseMinutes(time: String): Int? {
        val parts = time.split(":")
        if (parts.size != 2) return null
        val hour = parts[0].toIntOrNull() ?: return null
        val minute = parts[1].toIntOrNull() ?: return null
        return hour * 60 + minute
    }

    private fun readableTextColor(backgroundColor: Int): Int {
        val red = Color.red(backgroundColor)
        val green = Color.green(backgroundColor)
        val blue = Color.blue(backgroundColor)
        val luminance = (0.299 * red + 0.587 * green + 0.114 * blue)
        return if (luminance > 186) Color.rgb(30, 34, 42) else Color.WHITE
    }

    private fun preventTimeWrap(time: String): String {
        return time
            .replace("-", "\u2060-\u2060")
            .replace("~", "\u2060~\u2060")
    }
}
