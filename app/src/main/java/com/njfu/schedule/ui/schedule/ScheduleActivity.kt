package com.njfu.schedule.ui.schedule

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.njfu.schedule.App
import com.njfu.schedule.R
import com.njfu.schedule.bean.CourseBaseBean
import com.njfu.schedule.bean.CourseDetailBean
import com.njfu.schedule.bean.TableBean
import com.njfu.schedule.bean.TimeNode
import com.njfu.schedule.databinding.ActivityScheduleBinding
import com.njfu.schedule.ui.import_.ImportActivity
import com.njfu.schedule.ui.import_.AddCourseActivity
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class ScheduleActivity : AppCompatActivity() {

    private lateinit var binding: ActivityScheduleBinding
    private var table: TableBean? = null
    private var allBases: List<CourseBaseBean> = emptyList()
    private var allDetails: List<CourseDetailBean> = emptyList()
    private var maxWeek = 20

    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { loadSchedule() }

    private val addCourseLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { loadSchedule() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScheduleBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 构建星期头部
        val days = resources.getStringArray(R.array.days)
        for (day in days) {
            val tv = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(dpToPx(52), LinearLayout.LayoutParams.MATCH_PARENT)
                gravity = Gravity.CENTER
                text = day
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            }
            binding.headerRow.addView(tv)
        }

        binding.btnImport.setOnClickListener {
            importLauncher.launch(Intent(this, ImportActivity::class.java))
        }

        binding.btnAdd.setOnClickListener {
            addCourseLauncher.launch(Intent(this, AddCourseActivity::class.java))
        }

        // ViewPager 周切换
        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                val week = position + 1
                binding.tvWeek.text = getString(R.string.week_format, week)
            }
        })

        loadSchedule()
    }

    private fun loadSchedule() {
        lifecycleScope.launch {
            val dao = App.instance.database.courseDao()
            table = dao.getFirstTable()

            if (table == null) {
                binding.tvEmpty.visibility = View.VISIBLE
                binding.viewPager.visibility = View.GONE
                return@launch
            }

            val t = table!!
            maxWeek = t.maxWeek

            // 显示学生信息
            if (t.studentName.isNotEmpty()) {
                binding.tvStudent.visibility = View.VISIBLE
                binding.tvStudent.text = getString(R.string.welcome, t.studentName)
            }

            dao.getCourseBaseByTable(t.id)
                .combine(dao.getCourseDetailByTable(t.id)) { bases, details ->
                    Pair(bases, details)
                }
                .collect { (bases, details) ->
                    allBases = bases
                    allDetails = details

                    if (bases.isEmpty()) {
                        binding.tvEmpty.visibility = View.VISIBLE
                        binding.viewPager.visibility = View.GONE
                    } else {
                        binding.tvEmpty.visibility = View.GONE
                        binding.viewPager.visibility = View.VISIBLE
                        setupViewPager()
                    }
                }
        }
    }

    private fun setupViewPager() {
        binding.viewPager.adapter = WeekPagerAdapter()
        // 跳转到当前周
        val currentWeek = (table?.currentWeek ?: 1).coerceIn(1, maxWeek)
        binding.viewPager.setCurrentItem(currentWeek - 1, false)
        binding.tvWeek.text = getString(R.string.week_format, currentWeek)
    }

    inner class WeekPagerAdapter : RecyclerView.Adapter<WeekPagerAdapter.WeekViewHolder>() {

        inner class WeekViewHolder(val container: LinearLayout) : RecyclerView.ViewHolder(container)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WeekViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.fragment_week, parent, false)
            return WeekViewHolder(view.findViewById(R.id.schedule_grid))
        }

        override fun onBindViewHolder(holder: WeekViewHolder, position: Int) {
            val week = position + 1
            renderWeek(holder.container, week)
        }

        override fun getItemCount() = maxWeek
    }

    private fun renderWeek(container: LinearLayout, week: Int) {
        container.removeAllViews()

        val maxNode = table?.nodes ?: 11
        val cellWidth = dpToPx(52)
        val cellHeight = dpToPx(52)
        val colorMap = allBases.associate { it.id to it.color }
        val nameMap = allBases.associate { it.id to it.courseName }

        // 过滤当前周的课程
        val weekDetails = allDetails.filter { d ->
            d.startWeek <= week && d.endWeek >= week &&
                    (d.type == 0 || (d.type == 1 && week % 2 == 1) || (d.type == 2 && week % 2 == 0))
        }

        // 节次列
        val nodeCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(dpToPx(32), LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        for (node in 1..maxNode) {
            val tv = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, cellHeight)
                gravity = Gravity.CENTER
                text = "$node"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f)
                setTextColor(Color.GRAY)
            }
            nodeCol.addView(tv)
        }
        container.addView(nodeCol)

        // 7天的列
        for (day in 1..7) {
            val dayCourses = weekDetails.filter { it.day == day }.sortedBy { it.startNode }
            val col = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(cellWidth, LinearLayout.LayoutParams.WRAP_CONTENT)
            }

            var currentNode = 1
            while (currentNode <= maxNode) {
                val course = dayCourses.find { it.startNode == currentNode }
                if (course != null) {
                    val name = nameMap[course.id] ?: ""
                    val room = course.room ?: ""
                    val bgColor = colorMap[course.id] ?: "#ECEDFD"
                    val startTime = TimeNode.getStartTime(course.startNode)

                    val tv = TextView(this).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            cellHeight * course.step
                        ).apply { setMargins(1, 1, 1, 1) }
                        gravity = Gravity.CENTER
                        text = "$name\n@$room"
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f)
                        setTextColor(Color.parseColor("#333333"))
                        setPadding(4, 2, 4, 2)
                        maxLines = course.step * 2
                        try { setBackgroundColor(Color.parseColor(bgColor)) } catch (_: Exception) {
                            setBackgroundColor(Color.parseColor("#ECEDFD"))
                        }
                        // 点击编辑
                        setOnClickListener {
                            val intent = Intent(this@ScheduleActivity, AddCourseActivity::class.java)
                            intent.putExtra("course_id", course.id)
                            intent.putExtra("table_id", course.tableId)
                            addCourseLauncher.launch(intent)
                        }
                    }
                    col.addView(tv)
                    currentNode += course.step
                } else {
                    val empty = View(this).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, cellHeight
                        )
                    }
                    col.addView(empty)
                    currentNode++
                }
            }
            container.addView(col)
        }
    }

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), resources.displayMetrics
        ).toInt()
    }
}
