package com.njfu.schedule.ui.schedule

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.njfu.schedule.R
import com.njfu.schedule.bean.GlobalCourseInfo

class GlobalCourseAdapter : RecyclerView.Adapter<GlobalCourseAdapter.ViewHolder>() {

    private val items = mutableListOf<GlobalCourseInfo>()
    private val dayNames = arrayOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

    fun submitList(list: List<GlobalCourseInfo>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_global_course, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.tvDay.text = if (item.day in 1..7) dayNames[item.day - 1] else "未知"
        holder.tvSections.text = item.sectionsStr.replace(Regex("\\[|\\]|节"), "")
        holder.tvCourseName.text = item.courseName
        
        holder.tvRoom.text = item.room.ifEmpty { "未安排教室" }
        holder.tvTeacher.text = item.teacher.ifEmpty { "未知教师" }
        holder.tvWeeks.text = item.weeksStr.ifEmpty { "未知周次" }

        if (item.className.isNotEmpty()) {
            holder.tvClassName.visibility = View.VISIBLE
            holder.tvClassName.text = item.className
        } else {
            holder.tvClassName.visibility = View.GONE
        }
    }

    override fun getItemCount() = items.size

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvDay: TextView = view.findViewById(R.id.tv_day)
        val tvSections: TextView = view.findViewById(R.id.tv_sections)
        val tvCourseName: TextView = view.findViewById(R.id.tv_course_name)
        val tvRoom: TextView = view.findViewById(R.id.tv_room)
        val tvTeacher: TextView = view.findViewById(R.id.tv_teacher)
        val tvWeeks: TextView = view.findViewById(R.id.tv_weeks)
        val tvClassName: TextView = view.findViewById(R.id.tv_class_name)
    }
}
