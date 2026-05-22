package com.njfu.schedule.ui.schedule

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
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
import androidx.core.view.setPadding
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
import com.njfu.schedule.ui.settings.ScheduleSettingsActivity
import com.njfu.schedule.ui.settings.TimeSettingsActivity
import com.njfu.schedule.utils.WeekUtils
import com.google.android.material.bottomsheet.BottomSheetDialog
import android.widget.SeekBar
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

    // 更鲜艳的课程颜色
    private val courseColors = listOf(
        "#7E57C2", "#EF5350", "#FF7043", "#5C6BC0", "#66BB6A",
        "#42A5F5", "#26C6DA", "#EC407A", "#FFA726", "#AB47BC",
        "#26A69A", "#8D6E63", "#78909C", "#9CCC65", "#FFCA28"
    )

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
        binding.btnSettings.setOnClickListener { showBottomMenu() }
        binding.btnEmptyImport.setOnClickListener {
            importLauncher.launch(Intent(this, ImportActivity::class.java))
        }
        binding.weekSelector.setOnClickListener {
            binding.viewPager.setCurrentItem(currentWeek - 1, true)
        }

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateWeekHeader(position + 1)
            }
        })

        loadSchedule()
    }

    private fun updateWeekHeader(displayWeek: Int) {
        val isCurrentWeek = displayWeek == currentWeek
        binding.tvWeek.text = if (isCurrentWeek) "第 $displayWeek 周" else "第 $displayWeek 周"
        val suffix = if (isCurrentWeek) "（本周）" else ""
        binding.tvDateInfo.text = "${WeekUtils.getTodayString()}$suffix ${table?.studentName ?: ""}"
        updateDayHeaders(displayWeek)
    }

    private fun updateDayHeaders(targetWeek: Int) {
        val headerRow = binding.headerRow
        while (headerRow.childCount > 1) headerRow.removeViewAt(1)

        val startDate = table?.startDate ?: "2026-02-24"
        val dates = WeekUtils.getWeekDates(currentWeek, targetWeek, startDate)
        val days = resources.getStringArray(R.array.days)

        for (i in 0..6) {
            val isToday = (targetWeek == currentWeek && i + 1 == todayOfWeek)
            val tv = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
                gravity = Gravity.CENTER
                text = "${days[i]}\n${dates.getOrElse(i) { "" }}"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                setLineSpacing(dpToPx(1).toFloat(), 1f)
                if (isToday) {
                    setTextColor(Color.WHITE)
                    val bg = GradientDrawable().apply {
                        setColor(resources.getColor(R.color.primary, theme))
                        cornerRadius = dpToPx(16).toFloat()
                    }
                    background = bg
                    setPadding(0, dpToPx(4), 0, dpToPx(4))
                    typeface = Typeface.DEFAULT_BOLD
                } else {
                    setTextColor(Color.parseColor("#555555"))
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
                .combine(dao.getCourseDetailByTable(t.id)) { bases, details -> Pair(bases, details) }
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

    inner class WeekPagerAdapter : RecyclerView.Adapter<WeekPagerAdapter.VH>() {
        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val grid: LinearLayout = view.findViewById(R.id.schedule_grid)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.fragment_week, parent, false)
            return VH(view)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            renderWeek(holder.grid, position + 1)
        }

        override fun getItemCount() = maxWeek
    }

    private fun renderWeek(container: LinearLayout, week: Int) {
        container.removeAllViews()

        val maxNode = table?.nodes ?: 11
        val cellHeight = dpToPx(56)
        val colorMap = allBases.associate { it.id to it.color }
        val nameMap = allBases.associate { it.id to it.courseName }

        val weekDetails = allDetails.filter { d ->
            d.startWeek <= week && d.endWeek >= week &&
                    (d.type == 0 || (d.type == 1 && week % 2 == 1) || (d.type == 2 && week % 2 == 0))
        }

        val otherWeekDetails = if (table?.showSat == true) {
            allDetails.filter { d -> !(d.startWeek <= week && d.endWeek >= week) }
        } else emptyList()

        // 节次列
        val nodeCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(dpToPx(30), LinearLayout.LayoutParams.WRAP_CONTENT)
            setBackgroundColor(Color.parseColor("#FAFAFA"))
        }
        for (node in 1..maxNode) {
            val time = TimeNode.getStartTime(node)
            val tv = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, cellHeight)
                gravity = Gravity.CENTER
                text = "$node\n$time"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 8f)
                setTextColor(Color.parseColor("#AAAAAA"))
                setLineSpacing(0f, 0.85f)
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
                if (isToday) setBackgroundColor(Color.parseColor("#0866BB6A"))
            }

            var currentNode = 1
            while (currentNode <= maxNode) {
                val course = dayCourses.find { it.startNode == currentNode }
                val otherCourse = otherDayCourses.find { it.startNode == currentNode }

                if (course != null) {
                    col.addView(createCourseCard(course, nameMap, colorMap, cellHeight, false))
                    currentNode += course.step
                } else if (otherCourse != null) {
                    col.addView(createCourseCard(otherCourse, nameMap, colorMap, cellHeight, true))
                    currentNode += otherCourse.step
                } else {
                    col.addView(View(this).apply {
                        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, cellHeight)
                    })
                    currentNode++
                }
            }
            container.addView(col)
        }
    }

    private fun createCourseCard(
        course: CourseDetailBean,
        nameMap: Map<Int, String>,
        colorMap: Map<Int, String>,
        cellHeight: Int,
        isOtherWeek: Boolean
    ): View {
        val name = nameMap[course.id] ?: ""
        val room = course.room ?: ""
        val bgColor = colorMap[course.id] ?: courseColors[course.id % courseColors.size]

        return TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                cellHeight * course.step
            ).apply { setMargins(dpToPx(1), dpToPx(1), dpToPx(1), dpToPx(1)) }

            gravity = Gravity.CENTER
            text = if (room.isNotEmpty()) "$name\n$room" else name
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            setTextColor(Color.WHITE)
            setPadding(dpToPx(3))
            maxLines = course.step * 2 + 1
            includeFontPadding = false
            typeface = Typeface.DEFAULT_BOLD

            val color = try { Color.parseColor(bgColor) } catch (_: Exception) { Color.parseColor("#5C6BC0") }
            val drawable = GradientDrawable().apply {
                setColor(if (isOtherWeek) adjustAlpha(color, 0.25f) else color)
                cornerRadius = dpToPx(6).toFloat()
            }
            background = drawable

            if (isOtherWeek) {
                setTextColor(Color.parseColor("#88666666"))
                typeface = Typeface.DEFAULT
            }

            setOnClickListener {
                if (!isOtherWeek) showCourseDetail(course, name)
            }
        }
    }

    private fun adjustAlpha(color: Int, factor: Float): Int {
        val alpha = (Color.alpha(color) * factor).toInt()
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
    }

    private fun showCourseDetail(course: CourseDetailBean, name: String) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_course_detail, null)
        view.findViewById<TextView>(R.id.tv_course_name).text = name
        view.findViewById<TextView>(R.id.tv_teacher).text = "👤 ${course.teacher ?: "未知"}"
        view.findViewById<TextView>(R.id.tv_room).text = "📍 ${course.room ?: "未知"}"

        val startTime = TimeNode.getStartTime(course.startNode)
        val endTime = TimeNode.getEndTime(course.startNode + course.step - 1)
        val days = resources.getStringArray(R.array.days)
        val dayName = days.getOrElse(course.day - 1) { "" }
        view.findViewById<TextView>(R.id.tv_time).text = "🕐 $dayName 第${course.startNode}-${course.startNode + course.step - 1}节 ($startTime ~ $endTime)"
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

    private fun showBottomMenu() {
        val dialog = BottomSheetDialog(this)
        val view = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_menu, null)
        dialog.setContentView(view)

        // 周数 SeekBar
        val seekbar = view.findViewById<SeekBar>(R.id.seekbar_week)
        seekbar.max = maxWeek
        seekbar.progress = binding.viewPager.currentItem + 1
        seekbar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser && progress > 0) {
                    binding.viewPager.setCurrentItem(progress - 1, false)
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        // 课表名
        view.findViewById<TextView>(R.id.tv_table_name).text = "${table?.tableName ?: "南林课表"} ✓"

        // 上课时间
        view.findViewById<View>(R.id.menu_time).setOnClickListener {
            dialog.dismiss()
            startActivity(Intent(this, TimeSettingsActivity::class.java))
        }

        // 课表设置
        view.findViewById<View>(R.id.menu_schedule_settings).setOnClickListener {
            dialog.dismiss()
            val intent = Intent(this, ScheduleSettingsActivity::class.java)
            settingsLauncher.launch(intent)
        }

        // 已添课程
        view.findViewById<View>(R.id.menu_courses).setOnClickListener {
            dialog.dismiss()
            showCourseList()
        }

        // 关于
        view.findViewById<View>(R.id.menu_about).setOnClickListener {
            dialog.dismiss()
            AlertDialog.Builder(this)
                .setTitle("南林课程表")
                .setMessage("版本 1.0.0\n\n专为南京林业大学学生打造\n一键导入教务系统课表\n\n基于 WakeUp 课程表开源项目")
                .setPositiveButton("确定", null)
                .show()
        }

        dialog.show()
    }

    private val settingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { loadSchedule() }

    private fun showCourseList() {
        val names = allBases.map { it.courseName }
        if (names.isEmpty()) {
            Toast.makeText(this, "暂无课程", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("已添加课程 (${names.size}门)")
            .setItems(names.toTypedArray()) { _, idx ->
                val course = allBases[idx]
                val intent = Intent(this, AddCourseActivity::class.java)
                intent.putExtra("course_id", course.id)
                intent.putExtra("table_id", course.tableId)
                addCourseLauncher.launch(intent)
            }
            .setPositiveButton("关闭", null)
            .show()
    }

    private fun showSettingsDialog() {
        // 保留旧方法作为备用
        startActivity(Intent(this, ScheduleSettingsActivity::class.java))
    }

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), resources.displayMetrics).toInt()
    }
}
