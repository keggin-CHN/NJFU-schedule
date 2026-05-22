package com.njfu.schedule.ui.schedule

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.njfu.schedule.App
import com.njfu.schedule.bean.CourseBaseBean
import com.njfu.schedule.bean.CourseDetailBean
import com.njfu.schedule.databinding.ActivityScheduleBinding
import com.njfu.schedule.ui.import_.ImportActivity
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class ScheduleActivity : AppCompatActivity() {

    private lateinit var binding: ActivityScheduleBinding

    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { loadSchedule() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScheduleBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                com.njfu.schedule.R.id.action_import -> {
                    importLauncher.launch(Intent(this, ImportActivity::class.java))
                    true
                }
                else -> false
            }
        }

        loadSchedule()
    }

    private fun loadSchedule() {
        lifecycleScope.launch {
            val dao = App.instance.database.courseDao()
            val table = dao.getFirstTable()
            if (table == null) {
                binding.tvEmpty.visibility = View.VISIBLE
                return@launch
            }

            dao.getCourseBaseByTable(table.id)
                .combine(dao.getCourseDetailByTable(table.id)) { bases, details ->
                    Pair(bases, details)
                }
                .collect { (bases, details) ->
                    if (bases.isEmpty()) {
                        binding.tvEmpty.visibility = View.VISIBLE
                        binding.scheduleContainer.visibility = View.GONE
                    } else {
                        binding.tvEmpty.visibility = View.GONE
                        binding.scheduleContainer.visibility = View.VISIBLE
                        renderSchedule(bases, details)
                    }
                }
        }
    }

    private fun renderSchedule(bases: List<CourseBaseBean>, details: List<CourseDetailBean>) {
        binding.scheduleContainer.removeAllViews()

        val dayNames = arrayOf("时间", "周一", "周二", "周三", "周四", "周五", "周六", "周日")
        val maxNode = 11
        val cellWidth = dpToPx(52)
        val cellHeight = dpToPx(56)
        val headerHeight = dpToPx(32)

        val colorMap = bases.associate { it.id to it.color }
        val nameMap = bases.associate { it.id to it.courseName }

        // 确定显示哪些天
        val daysWithCourse = details.map { it.day }.distinct().sorted()
        val maxDay = if (daysWithCourse.any { it > 5 }) 7 else 5

        for (dayIdx in 0..maxDay) {
            val column = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    if (dayIdx == 0) dpToPx(32) else cellWidth,
                    LinearLayout.LayoutParams.MATCH_PARENT
                )
            }

            // Header
            val header = TextView(this).apply {
                text = dayNames[dayIdx]
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, headerHeight
                )
                setBackgroundColor(Color.parseColor("#F5F5F5"))
            }
            column.addView(header)

            if (dayIdx == 0) {
                // 节次列
                for (node in 1..maxNode) {
                    val nodeView = TextView(this).apply {
                        text = "$node"
                        gravity = Gravity.CENTER
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, cellHeight
                        )
                    }
                    column.addView(nodeView)
                }
            } else {
                // 课程列
                val dayCourses = details.filter { it.day == dayIdx }
                var currentNode = 1

                while (currentNode <= maxNode) {
                    val course = dayCourses.find { it.startNode == currentNode }
                    if (course != null) {
                        val courseView = TextView(this).apply {
                            val name = nameMap[course.id] ?: ""
                            val roomText = course.room ?: ""
                            text = "$name\n$roomText"
                            gravity = Gravity.CENTER
                            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                            setTextColor(Color.parseColor("#333333"))
                            setPadding(4, 4, 4, 4)
                            maxLines = 4

                            val bgColor = colorMap[course.id] ?: "#ECEDFD"
                            try {
                                setBackgroundColor(Color.parseColor(bgColor))
                            } catch (e: Exception) {
                                setBackgroundColor(Color.parseColor("#ECEDFD"))
                            }

                            layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                cellHeight * course.step
                            )
                        }
                        column.addView(courseView)
                        currentNode += course.step
                    } else {
                        val emptyView = View(this).apply {
                            layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT, cellHeight
                            )
                        }
                        column.addView(emptyView)
                        currentNode++
                    }
                }
            }

            binding.scheduleContainer.addView(column)
        }
    }

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), resources.displayMetrics
        ).toInt()
    }
}
