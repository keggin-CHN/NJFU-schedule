package com.njfu.schedule.ui.schedule

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
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
import com.njfu.schedule.ui.import_.AddCourseActivity
import com.njfu.schedule.ui.import_.ImportActivity
import com.njfu.schedule.utils.WeekUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ScheduleActivity : AppCompatActivity() {

    private lateinit var binding: ActivityScheduleBinding
    private var table: TableBean? = null
    private var allBases: List<CourseBaseBean> = emptyList()
    private var allDetails: List<CourseDetailBean> = emptyList()
    private var maxWeek = 20
    private var currentWeek = 1
    private var todayOfWeek = 1

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

        todayOfWeek = WeekUtils.getTodayOfWeek()

        binding.btnImport.setOnClickListener {
            importLauncher.launch(Intent(this, ImportActivity::class.java))
        }
        binding.btnAdd.setOnClickListener {
            addCourseLauncher.launch(Intent(this, AddCourseActivity::class.java))
        }
        binding.btnSettings.setOnClickListener { showSettingsDialog() }
        binding.btnEmptyImport.setOnClickListener {
            importLauncher.launch(Intent(this, ImportActivity::class.java))
        }

        // 点击周标题回到当前周
        binding.weekSelector.setOnClickListener {
            binding.viewPager.setCurrentItem(currentWeek - 1, true)
        }

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                val week = position + 1
                updateWeekHeader(week)
            }
        })

        loadSchedule()
    }

    private fun updateWeekHeader(displayWeek: Int) {
        val isCurrentWeek = displayWeek == currentWeek
        binding.tvWeek.text = if (isCurrentWeek) {
            "第 $displayWeek 周（本周）"
        } else {
            getString(R.string.week_format, displayWeek)
        }
        binding.tvDateInfo.text = "${WeekUtils.getTodayString()} · ${table?.studentName ?: ""}"

        // 更新星期头部日期
        updateDayHeaders(displayWeek)
    }

    private fun updateDayHeaders(targetWeek: Int) {
        val headerRow = binding.headerRow
        // 移除旧的日期 header（保留第一个节次列）
        while (headerRow.childCount > 1) {
            headerRow.removeViewAt(1)
        }

        val startDate = table?.startDate ?: "2025-02-24"
        val dates = WeekUtils.getWeekDates(currentWeek, targetWeek, startDate)
        val days = resources.getStringArray(R.array.days)

        for (i in 0..6) {
            val isToday = (targetWeek == currentWeek && i + 1 == todayOfWeek)
            val tv = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
                gravity = Gravity.CENTER
                text = "${days[i]}\n${dates.getOrElse(i) { "" }}"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                setLineSpacing(0f, 0.9f)
                if (isToday) {
                    setTextColor(Color.WHITE)
                    setBackgroundColor(resources.getColor(R.color.primary, theme))
                    setTypeface(null, android.graphics.Typeface.BOLD)
                } else {
                    setTextColor(Color.parseColor("#666666"))
                }
            }
            headerRow.addView(tv)
        }
    }

    private fun loadSchedule() {
        lifecycleScope.launch {
            val dao = App.instance.database.courseDao()
            table = dao.getFirstTable()

            if (table == null) {
                showEmpty(true)
                return@launch
            }

            val t = table!!
            maxWeek = t.maxWeek
            currentWeek = WeekUtils.getCurrentWeek(t.startDate).coerceIn(1, maxWeek)

            dao.getCourseBaseByTable(t.id)
                .combine(dao.getCourseDetailByTable(t.id)) { bases, details ->
                    Pair(bases, details)
                }
                .collect { (bases, details) ->
                    allBases = bases
                    allDetails = details

                    if (bases.isEmpty()) {
                        showEmpty(true)
                    } else {
                        showEmpty(false)
                        setupViewPager()
                    }
                }
        }
    }

    private fun showEmpty(empty: Boolean) {
        binding.emptyView.visibility = if (empty) View.VISIBLE else View.GONE
        binding.viewPager.visibility = if (empty) View.GONE else View.VISIBLE
        if (empty) {
            binding.tvWeek.text = "南林课程表"
            binding.tvDateInfo.text = WeekUtils.getTodayString()
        }
    }

    private fun setupViewPager() {
        binding.viewPager.adapter = WeekPagerAdapter()
        binding.viewPager.setCurrentItem(currentWeek - 1, false)
        updateWeekHeader(currentWeek)
    }

    // ==================== ViewPager Adapter ====================

    inner class WeekPagerAdapter : RecyclerView.Adapter<WeekPagerAdapter.VH>() {
        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val grid: LinearLayout = view.findViewById(R.id.schedule_grid)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.fragment_week, parent, false)
            return VH(view)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            renderWeek(holder.grid, position + 1)
        }

        override fun getItemCount() = maxWeek
    }

    // ==================== 渲染课表 ====================

    private fun renderWeek(container: LinearLayout, week: Int) {
        container.removeAllViews()

        val maxNode = table?.nodes ?: 11
        val cellHeight = dpToPx(52)
        val colorMap = allBases.associate { it.id to it.color }
        val nameMap = allBases.associate { it.id to it.courseName }

        // 过滤当前周的课程
        val weekDetails = allDetails.filter { d ->
            d.startWeek <= week && d.endWeek >= week &&
                    (d.type == 0 || (d.type == 1 && week % 2 == 1) || (d.type == 2 && week % 2 == 0))
        }

        // 非本周课程（半透明显示）
        val otherWeekDetails = allDetails.filter { d ->
            !(d.startWeek <= week && d.endWeek >= week)
        }

        // 节次列
        val nodeCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(dpToPx(30), LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        for (node in 1..maxNode) {
            val time = TimeNode.getStartTime(node)
            val tv = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, cellHeight)
                gravity = Gravity.CENTER
                text = "$node\n$time"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 8f)
                setTextColor(Color.GRAY)
                setLineSpacing(0f, 0.9f)
            }
            nodeCol.addView(tv)
        }
        container.addView(nodeCol)

        // 7天
        for (day in 1..7) {
            val dayCourses = weekDetails.filter { it.day == day }.sortedBy { it.startNode }
            val otherDayCourses = otherWeekDetails.filter { it.day == day }.sortedBy { it.startNode }
            val isToday = (week == currentWeek && day == todayOfWeek)

            val col = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                if (isToday) setBackgroundColor(Color.parseColor("#08000000"))
            }

            var currentNode = 1
            while (currentNode <= maxNode) {
                val course = dayCourses.find { it.startNode == currentNode }
                val otherCourse = otherDayCourses.find { it.startNode == currentNode }

                if (course != null) {
                    val tv = createCourseView(course, nameMap, colorMap, cellHeight, 1.0f)
                    col.addView(tv)
                    currentNode += course.step
                } else if (otherCourse != null) {
                    // 非本周课程半透明
                    val tv = createCourseView(otherCourse, nameMap, colorMap, cellHeight, 0.3f)
                    col.addView(tv)
                    currentNode += otherCourse.step
                } else {
                    val empty = View(this).apply {
                        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, cellHeight)
                    }
                    col.addView(empty)
                    currentNode++
                }
            }
            container.addView(col)
        }
    }

    private fun createCourseView(
        course: CourseDetailBean,
        nameMap: Map<Int, String>,
        colorMap: Map<Int, String>,
        cellHeight: Int,
        alpha: Float
    ): TextView {
        val name = nameMap[course.id] ?: ""
        val room = course.room ?: ""
        val bgColor = colorMap[course.id] ?: "#ECEDFD"

        return TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                cellHeight * course.step
            ).apply { setMargins(1, 1, 1, 1) }
            gravity = Gravity.CENTER
            text = "$name\n@$room"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f)
            setTextColor(Color.parseColor("#333333"))
            setPadding(3, 2, 3, 2)
            maxLines = course.step * 2 + 1
            this.alpha = alpha

            val drawable = GradientDrawable().apply {
                setColor(try { Color.parseColor(bgColor) } catch (_: Exception) { Color.parseColor("#ECEDFD") })
                cornerRadius = dpToPx(4).toFloat()
            }
            background = drawable

            // 点击显示详情
            setOnClickListener { showCourseDetail(course, name) }
        }
    }

    // ==================== 课程详情弹窗 ====================

    private fun showCourseDetail(course: CourseDetailBean, name: String) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_course_detail, null)
        view.findViewById<TextView>(R.id.tv_course_name).text = name
        view.findViewById<TextView>(R.id.tv_teacher).text = "👤 教师：${course.teacher ?: "未知"}"
        view.findViewById<TextView>(R.id.tv_room).text = "📍 教室：${course.room ?: "未知"}"

        val startTime = TimeNode.getStartTime(course.startNode)
        val endTime = TimeNode.getEndTime(course.startNode + course.step - 1)
        val days = resources.getStringArray(R.array.days)
        val dayName = days.getOrElse(course.day - 1) { "" }
        view.findViewById<TextView>(R.id.tv_time).text = "🕐 $dayName 第${course.startNode}-${course.startNode + course.step - 1}节 ($startTime-$endTime)"
        view.findViewById<TextView>(R.id.tv_weeks).text = "📅 第${course.startWeek}-${course.endWeek}周"

        AlertDialog.Builder(this)
            .setView(view)
            .setPositiveButton("编辑") { _, _ ->
                val intent = Intent(this, AddCourseActivity::class.java)
                intent.putExtra("course_id", course.id)
                intent.putExtra("table_id", course.tableId)
                addCourseLauncher.launch(intent)
            }
            .setNegativeButton("关闭", null)
            .show()
    }

    // ==================== 设置弹窗 ====================

    private fun showSettingsDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_settings, null)
        val etStartDate = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.et_start_date)
        val etMaxWeek = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.et_max_week)
        val etNodes = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.et_nodes)
        val tvInfo = view.findViewById<TextView>(R.id.tv_current_week_info)

        val t = table
        if (t != null) {
            etStartDate.setText(t.startDate)
            etMaxWeek.setText(t.maxWeek.toString())
            etNodes.setText(t.nodes.toString())
            tvInfo.text = "当前自动计算为第 $currentWeek 周"
        }

        AlertDialog.Builder(this)
            .setView(view)
            .setPositiveButton("保存") { _, _ ->
                val startDate = etStartDate.text?.toString()?.trim() ?: return@setPositiveButton
                val maxW = etMaxWeek.text?.toString()?.toIntOrNull() ?: 20
                val nodes = etNodes.text?.toString()?.toIntOrNull() ?: 11

                lifecycleScope.launch {
                    val dao = App.instance.database.courseDao()
                    val tb = table ?: return@launch
                    tb.startDate = startDate
                    tb.maxWeek = maxW
                    tb.nodes = nodes
                    withContext(Dispatchers.IO) { dao.updateTable(tb) }
                    Toast.makeText(this@ScheduleActivity, "设置已保存", Toast.LENGTH_SHORT).show()
                    loadSchedule()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), resources.displayMetrics
        ).toInt()
    }
}
