package com.njfu.schedule.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import com.njfu.schedule.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NextCourseWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (id in appWidgetIds) {
            try {
                updateWidget(context, appWidgetManager, id)
            } catch (_: Exception) {}
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == AppWidgetManager.ACTION_APPWIDGET_UPDATE) {
            super.onReceive(context, intent)
            return
        }
        if (action == Intent.ACTION_TIME_CHANGED ||
            action == Intent.ACTION_DATE_CHANGED ||
            action == Intent.ACTION_TIMEZONE_CHANGED ||
            action == "com.njfu.schedule.REFRESH_WIDGET" ||
            action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.Main).launch {
                try {
                    val manager = AppWidgetManager.getInstance(context)
                    val ids = manager.getAppWidgetIds(
                        ComponentName(context, NextCourseWidget::class.java)
                    )
                    for (id in ids) {
                        updateWidget(context, manager, id)
                    }
                } finally {
                    pendingResult.finish()
                }
            }
            return
        }
        super.onReceive(context, intent)
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        schedulePeriodicRefresh(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        cancelPeriodicRefresh(context)
    }

    companion object {
        private const val REFRESH_INTERVAL_MS = 30L * 60 * 1000

        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, NextCourseWidget::class.java)
            )
            ids.forEach { updateWidget(context, manager, it) }
        }

        private fun updateWidget(context: Context, manager: AppWidgetManager, widgetId: Int) {
            try {
                val views = RemoteViews(context.packageName, R.layout.widget_next_course)
                val courses = WidgetDataHelper.loadUpcomingCourses(context)

                views.setTextViewText(R.id.tv_widget2_title, WidgetDataHelper.todayText())

                bindCourse(views, 0, courses.getOrNull(0))
                bindCourse(views, 1, courses.getOrNull(1))
                bindCourse(views, 2, courses.getOrNull(2))

                views.setViewVisibility(
                    R.id.tv_widget2_empty,
                    if (courses.isEmpty()) View.VISIBLE else View.GONE
                )

                try {
                    val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                    if (launchIntent != null) {
                        val pi = PendingIntent.getActivity(
                            context, 200, launchIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                        views.setOnClickPendingIntent(R.id.widget2_root, pi)
                        views.setOnClickPendingIntent(R.id.tv_widget2_badge, pi)
                        views.setOnClickPendingIntent(R.id.tv_widget2_title, pi)
                    }
                } catch (_: Exception) {}

                manager.updateAppWidget(widgetId, views)
            } catch (t: Throwable) {
                try {
                    val views = RemoteViews(context.packageName, R.layout.widget_next_course)
                    views.setTextViewText(R.id.tv_widget2_title, "加载出错: ${t.javaClass.simpleName} ${t.message}")
                    views.setViewVisibility(R.id.tv_widget2_empty, View.GONE)
                    manager.updateAppWidget(widgetId, views)
                } catch (e2: Throwable) {
                    // Ignore
                }
            }
        }

        private fun bindCourse(views: RemoteViews, index: Int, course: WidgetCourse?) {
            val containerIds = intArrayOf(
                R.id.widget2_course_1, R.id.widget2_course_2, R.id.widget2_course_3
            )
            val colorIds = intArrayOf(
                R.id.widget2_course_color_1, R.id.widget2_course_color_2, R.id.widget2_course_color_3
            )
            val timeIds = intArrayOf(
                R.id.widget2_course_time_1, R.id.widget2_course_time_2, R.id.widget2_course_time_3
            )
            val nameIds = intArrayOf(
                R.id.widget2_course_name_1, R.id.widget2_course_name_2, R.id.widget2_course_name_3
            )
            val infoIds = intArrayOf(
                R.id.widget2_course_info_1, R.id.widget2_course_info_2, R.id.widget2_course_info_3
            )

            if (course == null) {
                views.setViewVisibility(containerIds[index], View.GONE)
                return
            }
            try {
                views.setViewVisibility(containerIds[index], View.VISIBLE)
                val timeText = compactTime(course.time)
                views.setViewVisibility(timeIds[index], View.GONE)
                views.setTextViewText(nameIds[index], course.name.ifBlank { "未命名课程" })
                
                val info = buildString {
                    append(timeText)
                    if (course.room.isNotEmpty()) {
                        append(" | ")
                        append(course.room)
                    }
                    if (course.teacher.isNotEmpty()) {
                        append(" | ")
                        append(course.teacher)
                    }
                }
                views.setTextViewText(infoIds[index], info)
                views.setViewVisibility(infoIds[index], View.VISIBLE)

                views.setInt(
                    colorIds[index],
                    "setBackgroundColor",
                    WidgetDataHelper.parseColor(course.color, "#7986CB")
                )
            } catch (_: Exception) {}
        }

        private fun compactTime(time: String): String {
            return time
                .replace(" ", "")
                .replace("~", "-")
        }


        private fun schedulePeriodicRefresh(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val intent = Intent(context, NextCourseWidget::class.java).apply {
                action = "com.njfu.schedule.REFRESH_WIDGET"
            }
            val pi = PendingIntent.getBroadcast(
                context, 200, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.setInexactRepeating(
                AlarmManager.ELAPSED_REALTIME,
                SystemClock.elapsedRealtime() + REFRESH_INTERVAL_MS,
                REFRESH_INTERVAL_MS,
                pi
            )
        }

        private fun cancelPeriodicRefresh(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val intent = Intent(context, NextCourseWidget::class.java).apply {
                action = "com.njfu.schedule.REFRESH_WIDGET"
            }
            val pi = PendingIntent.getBroadcast(
                context, 200, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pi)
        }
    }
}
