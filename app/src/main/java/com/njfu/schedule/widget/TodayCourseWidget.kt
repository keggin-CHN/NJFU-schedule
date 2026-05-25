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

class TodayCourseWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                for (id in appWidgetIds) {
                    updateWidget(context, appWidgetManager, id)
                }
            } finally {
                pendingResult.finish()
            }
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
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val manager = AppWidgetManager.getInstance(context)
                    val ids = manager.getAppWidgetIds(
                        ComponentName(context, TodayCourseWidget::class.java)
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

        suspend fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, TodayCourseWidget::class.java)
            )
            for (id in ids) {
                updateWidget(context, manager, id)
            }
        }

        private suspend fun updateWidget(context: Context, manager: AppWidgetManager, widgetId: Int) {
            try {
                val views = RemoteViews(context.packageName, R.layout.widget_today_courses)
                val courses = WidgetDataHelper.loadTodayCourses(context)

                views.setTextViewText(R.id.tv_widget_date, WidgetDataHelper.todayText())

                bindCourse(views, 0, courses.getOrNull(0))
                bindCourse(views, 1, courses.getOrNull(1))
                bindCourse(views, 2, courses.getOrNull(2))
                bindCourse(views, 3, courses.getOrNull(3))

                views.setViewVisibility(
                    R.id.tv_widget_empty,
                    if (courses.isEmpty()) View.VISIBLE else View.GONE
                )

                try {
                    val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                    if (launchIntent != null) {
                        val pi = PendingIntent.getActivity(
                            context, 100, launchIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                        views.setOnClickPendingIntent(R.id.widget_today_root, pi)
                        views.setOnClickPendingIntent(R.id.tv_widget_title, pi)
                    }
                } catch (_: Exception) {}

                manager.updateAppWidget(widgetId, views)
            } catch (t: Throwable) {
                try {
                    val views = RemoteViews(context.packageName, R.layout.widget_today_courses)
                    views.setTextViewText(R.id.tv_widget_date, "加载出错: ${t.javaClass.simpleName} ${t.message}")
                    views.setViewVisibility(R.id.tv_widget_empty, View.GONE)
                    manager.updateAppWidget(widgetId, views)
                } catch (e2: Throwable) {
                    // If even the fallback fails, it's a layout/RemoteViews issue
                }
            }
        }

        private fun bindCourse(views: RemoteViews, index: Int, course: WidgetCourse?) {
            val containerIds = intArrayOf(
                R.id.widget_course_1, R.id.widget_course_2, R.id.widget_course_3, R.id.widget_course_4
            )
            val colorIds = intArrayOf(
                R.id.widget_course_color_1, R.id.widget_course_color_2, R.id.widget_course_color_3, R.id.widget_course_color_4
            )
            val timeIds = intArrayOf(
                R.id.widget_course_time_1, R.id.widget_course_time_2, R.id.widget_course_time_3, R.id.widget_course_time_4
            )
            val nameIds = intArrayOf(
                R.id.widget_course_name_1, R.id.widget_course_name_2, R.id.widget_course_name_3, R.id.widget_course_name_4
            )
            val infoIds = intArrayOf(
                R.id.widget_course_info_1, R.id.widget_course_info_2, R.id.widget_course_info_3, R.id.widget_course_info_4
            )

            if (course == null) {
                views.setViewVisibility(containerIds[index], View.GONE)
                return
            }
            try {
                views.setViewVisibility(containerIds[index], View.VISIBLE)
                views.setTextViewText(timeIds[index], preventTimeWrap(course.time))
                views.setTextViewText(nameIds[index], course.name.ifBlank { "未命名课程" })

                val info = buildString {
                    if (course.room.isNotEmpty()) append(course.room)
                    if (course.teacher.isNotEmpty()) {
                        if (isNotEmpty()) append(" | ")
                        append(course.teacher)
                    }
                }
                views.setTextViewText(infoIds[index], info.ifBlank { "地点/教师待定" })
                views.setViewVisibility(infoIds[index], if (info.isEmpty()) View.GONE else View.VISIBLE)

                views.setInt(
                    colorIds[index],
                    "setBackgroundColor",
                    WidgetDataHelper.parseColor(course.color)
                )
            } catch (_: Exception) {}
        }

        private fun preventTimeWrap(time: String): String {
            return time
                .replace("-", "\u2060-\u2060")
                .replace("~", "\u2060~\u2060")
        }

        private fun schedulePeriodicRefresh(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val intent = Intent(context, TodayCourseWidget::class.java).apply {
                action = "com.njfu.schedule.REFRESH_WIDGET"
            }
            val pi = PendingIntent.getBroadcast(
                context, 100, intent,
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
            val intent = Intent(context, TodayCourseWidget::class.java).apply {
                action = "com.njfu.schedule.REFRESH_WIDGET"
            }
            val pi = PendingIntent.getBroadcast(
                context, 100, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pi)
        }
    }
}
