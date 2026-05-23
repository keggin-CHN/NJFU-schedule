package com.njfu.schedule.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.njfu.schedule.AppDatabase
import com.njfu.schedule.R
import com.njfu.schedule.bean.TimeNode
import com.njfu.schedule.utils.WeekUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.text.SimpleDateFormat
import java.util.*

/**
 * 今日课程 2x2 紧凑小组件
 */
class NextCourseWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (id in appWidgetIds) {
            updateWidget(context, appWidgetManager, id)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == Intent.ACTION_TIME_CHANGED ||
            intent.action == Intent.ACTION_DATE_CHANGED ||
            intent.action == "com.njfu.schedule.REFRESH_WIDGET") {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, NextCourseWidget::class.java))
            onUpdate(context, manager, ids)
        }
    }

    companion object {
        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, NextCourseWidget::class.java))
            val intent = Intent(context, NextCourseWidget::class.java)
            intent.action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            context.sendBroadcast(intent)
        }

        private fun updateWidget(context: Context, manager: AppWidgetManager, widgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_next_course)
            TimeNode.load(context)

            val todayOfWeek = WeekUtils.getTodayOfWeek()
            val dateText = SimpleDateFormat("M月d日 E", Locale.CHINA).format(Date())

            val calendar = Calendar.getInstance()
            val nowTotalMin = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)

            val todayCourses = runBlocking(Dispatchers.IO) {
                try {
                    val db = AppDatabase.getDatabase(context)
                    val dao = db.courseDao()
                    val table = dao.getFirstTable() ?: return@runBlocking emptyList<WidgetCourseLine>()
                    val currentWeek = WeekUtils.getCurrentWeek(table.startDate)

                    val allDetails = dao.getCourseDetailsById_sync(table.id)
                    val allBases = dao.getCourseBaseById_sync(table.id)
                    val nameMap = allBases.associate { it.id to it.courseName }
                    val colorMap = allBases.associate { it.id to it.color }
                    val teacherMap = allDetails.associate { it.id to it.teacher }

                    allDetails.filter { d ->
                        d.day == todayOfWeek &&
                        d.startWeek <= currentWeek && d.endWeek >= currentWeek &&
                        (d.type == 0 || (d.type == 1 && currentWeek % 2 == 1) || (d.type == 2 && currentWeek % 2 == 0))
                    }.filter { d ->
                        val endTimeStr = d.customEndTime ?: TimeNode.getEndTime(d.startNode + d.step - 1)
                        val endMin = parseMinutes(endTimeStr) ?: 0
                        endMin > nowTotalMin // 只显示未结束的课程
                    }.sortedWith(compareBy(
                        { d -> parseMinutes(d.customStartTime ?: TimeNode.getStartTime(d.startNode)) ?: 0 },
                        { d -> parseMinutes(d.customEndTime ?: TimeNode.getEndTime(d.startNode + d.step - 1)) ?: 0 },
                        { d -> nameMap[d.id].orEmpty().lowercase(Locale.CHINA) }
                    )).map { course ->
                        val startTime = course.customStartTime ?: TimeNode.getStartTime(course.startNode)
                        val endTime = course.customEndTime ?: TimeNode.getEndTime(course.startNode + course.step - 1)
                        WidgetCourseLine(
                            time = "$startTime-$endTime",
                            name = nameMap[course.id].orEmpty(),
                            room = course.room.orEmpty(),
                            teacher = teacherMap[course.id].orEmpty(),
                            color = colorMap[course.id].orEmpty()
                        )
                    }
                } catch (_: Exception) {
                    emptyList()
                }
            }

            views.setTextViewText(R.id.tv_widget2_title, dateText)
            views.removeAllViews(R.id.ll_widget_courses)

            if (todayCourses.isEmpty()) {
                views.setViewVisibility(R.id.tv_widget2_empty, android.view.View.VISIBLE)
            } else {
                views.setViewVisibility(R.id.tv_widget2_empty, android.view.View.GONE)
                
                // 最多显示 3 个课程，以免超出 2x2 范围
                val displayCourses = todayCourses.take(3)
                for (course in displayCourses) {
                    val itemView = RemoteViews(context.packageName, R.layout.item_widget_course)
                    itemView.setTextViewText(R.id.tv_course_time, course.time)
                    itemView.setTextViewText(R.id.tv_course_name, course.name)
                    
                    val info = buildString {
                        if (course.room.isNotEmpty()) append(course.room)
                        if (course.teacher.isNotEmpty()) {
                            if (isNotEmpty()) append(" | ")
                            append(course.teacher)
                        }
                    }
                    itemView.setTextViewText(R.id.tv_course_info, info)
                    if (info.isEmpty()) {
                        itemView.setViewVisibility(R.id.tv_course_info, android.view.View.GONE)
                    } else {
                        itemView.setViewVisibility(R.id.tv_course_info, android.view.View.VISIBLE)
                    }
                    
                    try {
                        val parsedColor = android.graphics.Color.parseColor(course.color.ifEmpty { "#7986CB" })
                        itemView.setInt(R.id.view_course_color, "setBackgroundColor", parsedColor)
                    } catch (e: Exception) {
                        itemView.setInt(R.id.view_course_color, "setBackgroundColor", android.graphics.Color.parseColor("#7986CB"))
                    }
                    
                    views.addView(R.id.ll_widget_courses, itemView)
                }
            }

            // 点击打开APP
            val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            if (launchIntent != null) {
                val pi = android.app.PendingIntent.getActivity(context, 1, launchIntent,
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)
                views.setOnClickPendingIntent(R.id.widget2_root, pi)
                views.setOnClickPendingIntent(R.id.tv_widget2_badge, pi)
                views.setOnClickPendingIntent(R.id.tv_widget2_title, pi)
                views.setOnClickPendingIntent(R.id.ll_widget_courses, pi)
            }

            manager.updateAppWidget(widgetId, views)
        }

        private data class WidgetCourseLine(
            val time: String,
            val name: String,
            val room: String,
            val teacher: String,
            val color: String
        )

        private fun parseMinutes(time: String): Int? {
            val parts = time.split(":")
            if (parts.size != 2) return null
            val hour = parts[0].toIntOrNull() ?: return null
            val minute = parts[1].toIntOrNull() ?: return null
            return hour * 60 + minute
        }
    }
}
