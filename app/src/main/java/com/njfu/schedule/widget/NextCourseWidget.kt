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

            val todayCourses = runBlocking(Dispatchers.IO) {
                try {
                    val db = AppDatabase.getDatabase(context)
                    val dao = db.courseDao()
                    val table = dao.getFirstTable() ?: return@runBlocking emptyList<WidgetCourseLine>()
                    val currentWeek = WeekUtils.getCurrentWeek(table.startDate)

                    val allDetails = dao.getCourseDetailsById_sync(table.id)
                    val allBases = dao.getCourseBaseById_sync(table.id)
                    val nameMap = allBases.associate { it.id to it.courseName }

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
                        WidgetCourseLine(
                            time = "$startTime-$endTime",
                            name = nameMap[course.id].orEmpty(),
                            room = course.room.orEmpty()
                        )
                    }
                } catch (_: Exception) {
                    emptyList()
                }
            }

            views.setTextViewText(R.id.tv_widget2_title, dateText)
            if (todayCourses.isEmpty()) {
                views.setTextViewText(R.id.tv_widget2_name, "今天没有课")
                views.setTextViewText(R.id.tv_widget2_info, "可以休息一下")
                views.setTextViewText(R.id.tv_widget2_time, "")
            } else {
                val first = todayCourses.first()
                val second = todayCourses.getOrNull(1)
                val third = todayCourses.getOrNull(2)
                val moreCount = todayCourses.size - 3

                views.setTextViewText(R.id.tv_widget2_name, "${first.time}\n${first.name}")
                views.setTextViewText(
                    R.id.tv_widget2_info,
                    first.room.ifEmpty { second?.let { "${it.time}  ${it.name}" }.orEmpty() }
                )
                views.setTextViewText(
                    R.id.tv_widget2_time,
                    when {
                        second != null && first.room.isNotEmpty() -> "${second.time}  ${second.name}"
                        third != null -> "${third.time}  ${third.name}"
                        moreCount > 0 -> "还有 ${moreCount} 节课"
                        else -> ""
                    }
                )
            }

            // 点击打开APP
            val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            if (launchIntent != null) {
                val pi = android.app.PendingIntent.getActivity(context, 1, launchIntent,
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)
                views.setOnClickPendingIntent(R.id.widget2_root, pi)
                views.setOnClickPendingIntent(R.id.tv_widget2_badge, pi)
                views.setOnClickPendingIntent(R.id.tv_widget2_title, pi)
                views.setOnClickPendingIntent(R.id.tv_widget2_name, pi)
                views.setOnClickPendingIntent(R.id.tv_widget2_info, pi)
                views.setOnClickPendingIntent(R.id.tv_widget2_time, pi)
            }

            manager.updateAppWidget(widgetId, views)
        }

        private data class WidgetCourseLine(
            val time: String,
            val name: String,
            val room: String
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
