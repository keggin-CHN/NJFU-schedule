package com.njfu.schedule.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import com.njfu.schedule.R
import com.njfu.schedule.utils.WeekUtils
import java.text.SimpleDateFormat
import java.util.*

class TodayCourseWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
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
            manager.notifyAppWidgetViewDataChanged(ids, R.id.widget_course_list)

            val intent = Intent(context, TodayCourseWidget::class.java)
            intent.action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            context.sendBroadcast(intent)
        }

        private fun updateWidget(context: Context, manager: AppWidgetManager, widgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_today_courses)

            val todayOfWeek = WeekUtils.getTodayOfWeek()
            val dayNames = arrayOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
            val dateStr = SimpleDateFormat("M月d日 ", Locale.CHINA).format(Date()) + dayNames[todayOfWeek - 1]
            views.setTextViewText(R.id.tv_widget_date, dateStr)

            val intent = Intent(context, TodayCourseWidgetService::class.java)
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            intent.data = Uri.parse(intent.toUri(Intent.URI_INTENT_SCHEME))
            views.setRemoteAdapter(R.id.widget_course_list, intent)
            views.setEmptyView(R.id.widget_course_list, R.id.tv_widget_empty)

            val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            if (launchIntent != null) {
                val pendingIntent = android.app.PendingIntent.getActivity(
                    context, 0, launchIntent,
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.tv_widget_title, pendingIntent)
                views.setPendingIntentTemplate(R.id.widget_course_list, pendingIntent)
            }

            manager.updateAppWidget(widgetId, views)
        }
    }
}
