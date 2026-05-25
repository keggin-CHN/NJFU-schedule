package com.njfu.schedule.ui.schedule

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.njfu.schedule.R
import com.njfu.schedule.bean.GlobalCourseInfo

class GlobalCourseAdapter(private val onItemClick: (GlobalCourseInfo) -> Unit) : ListAdapter<GlobalCourseInfo, GlobalCourseAdapter.VH>(DIFF) {

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<GlobalCourseInfo>() {
            override fun areItemsTheSame(a: GlobalCourseInfo, b: GlobalCourseInfo) =
                a.courseName == b.courseName &&
                    a.day == b.day &&
                    a.sectionsStr == b.sectionsStr &&
                    a.room == b.room &&
                    a.teacher == b.teacher &&
                    a.className == b.className &&
                    a.term == b.term &&
                    a.entityName == b.entityName &&
                    a.tableIndex == b.tableIndex &&
                    a.rowIndex == b.rowIndex &&
                    a.colIndex == b.colIndex &&
                    a.slotIndex == b.slotIndex
            override fun areContentsTheSame(a: GlobalCourseInfo, b: GlobalCourseInfo) = a == b
        }
        private val DAY_NAMES = arrayOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
    }

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvDay: TextView = view.findViewById(R.id.tv_day)
        val tvSections: TextView = view.findViewById(R.id.tv_sections)
        val tvCourseName: TextView = view.findViewById(R.id.tv_course_name)
        val tvRoom: TextView = view.findViewById(R.id.tv_room)
        val tvTeacher: TextView = view.findViewById(R.id.tv_teacher)
        val tvWeeks: TextView = view.findViewById(R.id.tv_weeks)
        val tvClassName: TextView = view.findViewById(R.id.tv_class_name)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        return VH(LayoutInflater.from(parent.context).inflate(R.layout.item_global_course, parent, false))
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        holder.tvDay.text = if (item.day in 1..7) DAY_NAMES[item.day - 1] else "?"

        val sectionNum = item.sectionsStr
            .replace(Regex("\\[|\\]|节|第"), "")
            .replace(Regex("\\(.*?\\)"), "")
            .trim()
        holder.tvSections.text = sectionNum.ifEmpty { "-" }
        holder.tvCourseName.text = item.courseName
        holder.tvRoom.text = item.room.ifEmpty { "未知教室" }
        // 如果 teacher 看起来像班级代码（纯数字），显示"未知教师"
        holder.tvTeacher.text = if (item.teacher.isEmpty() || item.teacher.all { it.isDigit() }) "未知教师" else item.teacher
        holder.tvWeeks.text = item.weeksStr.ifEmpty { "未知周次" }
        if (item.className.isNotEmpty()) {
            holder.tvClassName.visibility = View.VISIBLE
            holder.tvClassName.text = item.className
        } else {
            holder.tvClassName.visibility = View.GONE
        }

        holder.itemView.setOnClickListener {
            onItemClick(item)
        }
    }
}
