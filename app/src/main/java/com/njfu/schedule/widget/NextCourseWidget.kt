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

        suspend fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, NextCourseWidget::class.java)
            )
            for (id in ids) {
                updateWidget(context, manager, id)
            }
        }

        private suspend fun updateWidget(context: Context, manager: AppWidgetManager, widgetId: Int) {
            try {
                val views = RemoteViews(context.packageName, R.layout.widget_next_course)

                views.setTextViewText(R.id.tv_widget2_title, WidgetDataHelper.todayText())

                val intent = Intent(context, CourseListService::class.java)
                views.setRemoteAdapter(R.id.lv_courses, intent)

                views.setViewVisibility(R.id.tv_widget2_empty, View.GONE)
                views.setViewVisibility(R.id.lv_courses, View.VISIBLE)

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
                manager.notifyAppWidgetViewDataChanged(widgetId, R.id.lv_courses)
            } catch (t: Throwable) {
                try {
                    val views = RemoteViews(context.packageName, R.layout.widget_next_course)
                    views.setTextViewText(R.id.tv_widget2_title, "加载出错")
                    views.setViewVisibility(R.id.tv_widget2_empty, View.GONE)
                    manager.updateAppWidget(widgetId, views)
                } catch (_: Throwable) {}
            }
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
