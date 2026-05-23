package com.njfu.schedule.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.RemoteViews
import com.njfu.schedule.AppDatabase
import com.njfu.schedule.R
import com.njfu.schedule.bean.TimeNode
import com.njfu.schedule.utils.WeekUtils
import kotlinx.coroutines.runBlocking
import java.text.SimpleDateFormat
import java.util.*

/**
 * 今日课程桌面小组件
 * 兼容所有 Android 手机（华为/小米/OPPO/三星等）
 */
class TodayCourseWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        // 响应时间变化等广播，刷新小组件
        if (intent.action == Intent.ACTION_TIME_CHANGED ||
            intent.action == Intent.ACTION_DATE_CHANGED ||
            intent.action == "com.njfu.schedule.REFRESH_WIDGET") {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, TodayCourseWidget::class.java))
            onUpdate(context, manager, ids)
        }
    }

    companion object {
        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, TodayCourseWidget::class.java))
            val intent = Intent(context, TodayCourseWidget::class.java)
            intent.action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            context.sendBroadcast(intent)
        }

        private fun updateWidget(context: Context, manager: AppWidgetManager, widgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_today_courses)

            // 加载时间配置
            TimeNode.load(context)

            val todayOfWeek = WeekUtils.getTodayOfWeek()
            val dayNames = arrayOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
            val dateStr = SimpleDateFormat("M月d日 ", Locale.CHINA).format(Date()) + dayNames[todayOfWeek - 1]
            views.setTextViewText(R.id.tv_widget_date, dateStr)

            // 从数据库获取今日课程
            val todayCourses: List<WidgetCourse> = runBlocking(Dispatchers.IO) {
                try {
                    val db = AppDatabase.getDatabase(context)
                    val dao = db.courseDao()
                    val table = dao.getFirstTable() ?: return@runBlocking emptyList<WidgetCourse>()
                    val currentWeek = WeekUtils.getCurrentWeek(table.startDate)

                    val allDetails = dao.getCourseDetailsById_sync(table.id)
                    val allBases = dao.getCourseBaseById_sync(table.id)

                    val nameMap = allBases.associate { it.id to it.courseName }
                    val colorMap = allBases.associate { it.id to it.color }

                    allDetails.filter { d: com.njfu.schedule.bean.CourseDetailBean ->
                        d.day == todayOfWeek &&
                        d.startWeek <= currentWeek && d.endWeek >= currentWeek &&
                        (d.type == 0 || (d.type == 1 && currentWeek % 2 == 1) || (d.type == 2 && currentWeek % 2 == 0))
                    }.sortedWith(compareBy { d: com.njfu.schedule.bean.CourseDetailBean ->
                        parseMinutes(d.customStartTime ?: TimeNode.getStartTime(d.startNode)) ?: 0
                    }).map { d: com.njfu.schedule.bean.CourseDetailBean ->
                        val start = d.customStartTime ?: TimeNode.getStartTime(d.startNode)
                        val end = d.customEndTime ?: TimeNode.getEndTime(d.startNode + d.step - 1)
                        WidgetCourse(
                            name = nameMap[d.id] ?: "",
                            time = "$start-$end",
                            room = d.room ?: "",
                            color = colorMap[d.id] ?: "#5B8DEF"
                        )
                    }
                } catch (_: Exception) {
                    emptyList<WidgetCourse>()
                }
            }

            // 清除旧的课程列表
            views.removeAllViews(R.id.widget_course_list)

            if (todayCourses.isEmpty()) {
                views.setViewVisibility(R.id.tv_widget_empty, android.view.View.VISIBLE)
                views.setViewVisibility(R.id.widget_course_list, android.view.View.GONE)
            } else {
                views.setViewVisibility(R.id.tv_widget_empty, android.view.View.GONE)
                views.setViewVisibility(R.id.widget_course_list, android.view.View.VISIBLE)

                for (course in todayCourses) {
                    val itemView = RemoteViews(context.packageName, R.layout.widget_course_item)
                    itemView.setTextViewText(R.id.tv_widget_course_name, "${course.name}  ${course.room}")
                    itemView.setTextViewText(R.id.tv_widget_course_time, course.time)
                    try {
                        itemView.setInt(R.id.widget_color_dot, "setBackgroundColor", Color.parseColor(course.color))
                    } catch (_: Exception) {}
                    views.addView(R.id.widget_course_list, itemView)
                }
            }

            // 点击小组件打开APP
            val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            if (launchIntent != null) {
                val pendingIntent = android.app.PendingIntent.getActivity(
                    context, 0, launchIntent,
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.tv_widget_title, pendingIntent)
            }

            manager.updateAppWidget(widgetId, views)
        }

        private fun parseMinutes(time: String): Int? {
            val parts = time.split(":")
            if (parts.size != 2) return null
            val hour = parts[0].toIntOrNull() ?: return null
            val minute = parts[1].toIntOrNull() ?: return null
            return hour * 60 + minute
        }
    }

    data class WidgetCourse(val name: String, val time: String, val room: String, val color: String)
}
