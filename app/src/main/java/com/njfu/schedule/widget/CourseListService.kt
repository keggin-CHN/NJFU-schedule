package com.njfu.schedule.widget

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.njfu.schedule.R
import kotlinx.coroutines.runBlocking

class CourseListService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return CourseListFactory(applicationContext)
    }

    private class CourseListFactory(private val context: Context) : RemoteViewsFactory {

        private var courses: List<WidgetCourse> = emptyList()

        override fun onCreate() {}

        override fun onDataSetChanged() {
            // 只显示还未结束的课程（过滤掉已经结束的）
            courses = runBlocking { WidgetDataHelper.loadUpcomingCourses(context) }
        }

        override fun onDestroy() {
            courses = emptyList()
        }

        override fun getCount(): Int = courses.size

        override fun getViewAt(position: Int): RemoteViews {
            val course = courses[position]
            val views = RemoteViews(context.packageName, R.layout.widget_course_list_item)

            views.setTextViewText(R.id.item_name, course.name.ifBlank { "未命名课程" })

            val info = buildString {
                append(course.time)
                if (course.room.isNotEmpty()) append("   ").append(course.room)
                if (course.teacher.isNotEmpty()) append("   ").append(course.teacher)
            }
            views.setTextViewText(R.id.item_info, info)
            views.setInt(R.id.item_color, "setBackgroundColor", WidgetDataHelper.parseColor(course.color, "#7986CB"))

            // 设置 FillInIntent，配合 Widget 侧的 setPendingIntentTemplate 实现 item 点击跳转
            // FillInIntent 为空即可，系统会将其与模板 PendingIntent 合并后触发
            views.setOnClickFillInIntent(R.id.widget_list_item_root, Intent())

            return views
        }

        override fun getLoadingView(): RemoteViews? = null

        override fun getViewTypeCount(): Int = 1
        override fun getItemId(position: Int): Long = position.toLong()
        override fun hasStableIds(): Boolean = true
    }
}
