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
import kotlinx.coroutines.runBlocking
import java.text.SimpleDateFormat
import java.util.*

/**
 * 下节课 2x2 小组件
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
            val now = Calendar.getInstance()
            val currentHour = now.get(Calendar.HOUR_OF_DAY)
            val currentMin = now.get(Calendar.MINUTE)
            val currentMinutes = currentHour * 60 + currentMin

            val nextCourse = runBlocking {
                try {
                    val db = AppDatabase.getDatabase(context)
                    val dao = db.courseDao()
                    val table = dao.getFirstTable() ?: return@runBlocking null
                    val currentWeek = WeekUtils.getCurrentWeek(table.startDate)

                    val allDetails = dao.getCourseDetailsById_sync(table.id)
                    val allBases = dao.getCourseBaseById_sync(table.id)
                    val nameMap = allBases.associate { it.id to it.courseName }

                    // 今天的课，按开始时间排序
                    val todayCourses = allDetails.filter { d ->
                        d.day == todayOfWeek &&
                        d.startWeek <= currentWeek && d.endWeek >= currentWeek &&
                        (d.type == 0 || (d.type == 1 && currentWeek % 2 == 1) || (d.type == 2 && currentWeek % 2 == 0))
                    }.sortedWith(compareBy { d ->
                        parseMinutes(d.customStartTime ?: TimeNode.getStartTime(d.startNode)) ?: 0
                    })

                    // 找下一节课（优先使用自定义时间，否则使用节次时间）
                    for (course in todayCourses) {
                        val startTime = course.customStartTime ?: TimeNode.getStartTime(course.startNode)
                        val courseMinutes = parseMinutes(startTime)
                        if (courseMinutes != null && courseMinutes > currentMinutes) {
                            val endTime = course.customEndTime ?: TimeNode.getEndTime(course.startNode + course.step - 1)
                            return@runBlocking Triple(
                                nameMap[course.id] ?: "",
                                "${course.room ?: ""} · ${course.teacher ?: ""}",
                                "$startTime - $endTime"
                            )
                        }
                    }
                    null
                } catch (_: Exception) { null }
            }

            if (nextCourse != null) {
                views.setTextViewText(R.id.tv_widget2_title, "下节课")
                views.setTextViewText(R.id.tv_widget2_name, nextCourse.first)
                views.setTextViewText(R.id.tv_widget2_info, nextCourse.second)
                views.setTextViewText(R.id.tv_widget2_time, nextCourse.third)
            } else {
                views.setTextViewText(R.id.tv_widget2_title, "今日")
                views.setTextViewText(R.id.tv_widget2_name, "没有更多课了")
                views.setTextViewText(R.id.tv_widget2_info, "")
                views.setTextViewText(R.id.tv_widget2_time, SimpleDateFormat("M月d日 E", Locale.CHINA).format(Date()))
            }

            // 点击打开APP
            val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            if (launchIntent != null) {
                val pi = android.app.PendingIntent.getActivity(context, 1, launchIntent,
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)
                views.setOnClickPendingIntent(R.id.tv_widget2_name, pi)
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
}
