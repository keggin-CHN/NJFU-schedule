package com.njfu.schedule.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 系统广播接收器，用于在开机、时区变化、应用更新等场景下刷新所有桌面小组件。
 * 适配华为/三星/OPPO/小米等厂商的后台限制。
 */
class WidgetRefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Main).launch {
            try {
                TodayCourseWidget.refreshAll(context)
                NextCourseWidget.refreshAll(context)
            } catch (_: Exception) {
                // Silently handle any refresh errors
            } finally {
                pendingResult.finish()
            }
        }
    }
}
