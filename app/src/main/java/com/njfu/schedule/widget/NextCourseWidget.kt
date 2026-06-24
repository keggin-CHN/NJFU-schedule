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
        schedulePeriodicRefresh(context)
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
            schedulePeriodicRefresh(context)
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

                // 设置标题日期
                views.setTextViewText(R.id.tv_widget2_title, WidgetDataHelper.todayText())

                // 构建 RemoteAdapter Intent（必须加上 widgetId，否则多个小组件会共享同一个 Factory）
                val serviceIntent = Intent(context, CourseListService::class.java).apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                    data = android.net.Uri.fromParts("widget", widgetId.toString(), null)
                }

                // 将 ListView 绑定到 RemoteViewsService
                views.setRemoteAdapter(R.id.widget2_list, serviceIntent)

                // 点击标题栏 / 小组件背景打开 App
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

                        // ListView item 点击必须用 PendingIntentTemplate + FillInIntent 机制
                        // 这里设置模板，每个 item 在 CourseListFactory.getViewAt() 里设置 FillInIntent
                        val listItemPi = PendingIntent.getActivity(
                            context, 201, launchIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                        views.setPendingIntentTemplate(R.id.widget2_list, listItemPi)
                    }
                } catch (_: Exception) {}

                // 先检查是否有尚未结束的课程，决定是否显示空状态（与 ListView 数据源保持一致）
                val courses = WidgetDataHelper.loadUpcomingCourses(context)
                if (courses.isEmpty()) {
                    views.setViewVisibility(R.id.tv_widget2_empty, View.VISIBLE)
                    views.setViewVisibility(R.id.widget2_list, View.GONE)
                } else {
                    views.setViewVisibility(R.id.tv_widget2_empty, View.GONE)
                    views.setViewVisibility(R.id.widget2_list, View.VISIBLE)
                }

                manager.updateAppWidget(widgetId, views)
                // 通知 ListView 数据已变更，触发 Factory.onDataSetChanged()
                manager.notifyAppWidgetViewDataChanged(widgetId, R.id.widget2_list)
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
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
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
