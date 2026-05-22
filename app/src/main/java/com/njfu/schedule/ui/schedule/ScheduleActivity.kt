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
import com.njfu.schedule.ui.settings.BackgroundSettingsActivity
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

    // 柔和但有辨识度的课程颜色
    private val courseColors = listOf(
        "#7986CB", "#4DB6AC", "#FF8A65", "#A1887F", "#90A4AE",
        "#4DD0E1", "#81C784", "#FFD54F", "#F06292", "#BA68C8",
        "#64B5F6", "#E57373", "#AED581", "#FFB74D", "#9575CD"
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
        binding.btnSync.setOnClickListener { syncSchedule() }
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

        loadBackground()
        loadSchedule()
    }

    private fun loadBackground() {
        val file = java.io.File(filesDir, "schedule_bg.jpg")
        val prefs = getSharedPreferences("bg_settings", android.content.Context.MODE_PRIVATE)
        val hasBg = prefs.getBoolean("has_bg", false)
        val alpha = prefs.getInt("alpha", 50)

        val ivBg = findViewById<android.widget.ImageView>(R.id.iv_background)
        if (hasBg && file.exists()) {
            val bitmap = android.graphics.BitmapFactory.decodeFile(file.absolutePath)
            ivBg.setImageBitmap(bitmap)
            ivBg.visibility = View.VISIBLE
            // 设置主内容区域的背景透明度
            val mainContent = binding.viewPager
            mainContent.setBackgroundColor(Color.argb(255 * alpha / 100, 255, 255, 255))
        } else {
            ivBg.visibility = View.GONE
        }
    }

    private fun updateWeekHeader(displayWeek: Int) {
        val isCurrentWeek = displayWeek == currentWeek
        binding.tvWeek.text = "第${displayWeek}周"

        val tag = findViewById<TextView>(R.id.tv_week_tag)
        if (isCurrentWeek) {
            tag.visibility = View.VISIBLE
            tag.text = "本周"
            tag.setTextColor(resources.getColor(R.color.secondary, theme))
            tag.setBackgroundResource(R.drawable.bg_tag)
        } else {
            tag.visibility = View.VISIBLE
            tag.text = "非本周"
            tag.setTextColor(Color.parseColor("#999999"))
            tag.setBackgroundResource(R.drawable.bg_tag_gray)
        }

        binding.tvDateInfo.text = "${WeekUtils.getTodayString()} ${table?.studentName ?: ""}"
        updateDayHeaders(displayWeek)
    }

    private fun updateDayHeaders(targetWeek: Int) {
        val headerRow = binding.headerRow
        while (headerRow.childCount > 1) headerRow.removeViewAt(1)

        val startDate = table?.startDate ?: "2026-02-24"
        val dates = WeekUtils.getWeekDates(targetWeek, startDate)
        val dayLabels = arrayOf("一", "二", "三", "四", "五", "六", "日")

        for (i in 0..6) {
            val isToday = (targetWeek == currentWeek && i + 1 == todayOfWeek)
            val tv = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
                gravity = Gravity.CENTER
                text = "${dayLabels[i]}\n${dates.getOrElse(i) { "" }}"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                setLineSpacing(dpToPx(1).toFloat(), 1f)
                if (isToday) {
                    setTextColor(resources.getColor(R.color.secondary, theme))
                    typeface = Typeface.DEFAULT_BOLD
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
            // 自动计算当前周（从系统时间）
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
            val tvRemarks: TextView = view.findViewById(R.id.tv_remarks)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.fragment_week, parent, false)
            return VH(view)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            renderWeek(holder.grid, position + 1)
            // 显示备注
            val prefs = getSharedPreferences("njfu_login", android.content.Context.MODE_PRIVATE)
            val remarks = prefs.getString("remarks", null)
            if (!remarks.isNullOrEmpty()) {
                holder.tvRemarks.visibility = View.VISIBLE
                holder.tvRemarks.text = "备注：$remarks"
            } else {
                holder.tvRemarks.visibility = View.GONE
            }
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

        val gridLineColor = Color.parseColor("#CCCCCC")
        val gridLineWidth = dpToPx(1)

        // 创建虚线View的辅助函数
        fun dashedH(): View = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(1))
            background = resources.getDrawable(R.drawable.dashed_line_horizontal, theme)
            setLayerType(View.LAYER_TYPE_SOFTWARE, null) // 虚线需要关闭硬件加速
        }
        fun dashedV(): View = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(dpToPx(1), LinearLayout.LayoutParams.MATCH_PARENT)
            background = resources.getDrawable(R.drawable.dashed_line_vertical, theme)
            setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        }

        // 节次列
        val nodeCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(dpToPx(30), LinearLayout.LayoutParams.WRAP_CONTENT)
            setBackgroundColor(Color.parseColor("#FAFAFA"))
        }
        for (node in 1..maxNode) {
            val time = TimeNode.getStartTime(node)
            val tv = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, cellHeight).apply {
                    // 底部边框用 background drawable 模拟不方便，用 margin+divider 代替
                }
                gravity = Gravity.CENTER
                text = "$node\n$time"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 8f)
                setTextColor(Color.parseColor("#AAAAAA"))
                setLineSpacing(0f, 0.85f)
            }
            nodeCol.addView(tv)
            // 节次之间的横线
            if (node < maxNode) {
                nodeCol.addView(dashedH())
            }
        }
        container.addView(nodeCol)

        // 节次列右边的竖虚线
        container.addView(dashedV())

        // 7天
        for (day in 1..7) {
            val dayCourses = weekDetails.filter { it.day == day }.sortedBy { it.startNode }
            val otherDayCourses = otherWeekDetails.filter { it.day == day }.sortedBy { it.startNode }
            val isToday = (week == currentWeek && day == todayOfWeek)

            val col = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                if (isToday) setBackgroundColor(Color.parseColor("#085B8DEF"))
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
                    // 空格子 + 底部虚线
                    val cell = View(this).apply {
                        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, cellHeight - dpToPx(1))
                    }
                    col.addView(cell)
                    col.addView(dashedH())
                    currentNode++
                }
            }
            container.addView(col)

            // 每天之间的竖虚线
            if (day < 7) {
                container.addView(dashedV())
            }
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
        val teacher = course.teacher ?: ""
        val bgColor = colorMap[course.id] ?: courseColors[course.id % courseColors.size]

        return TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                cellHeight * course.step
            ).apply { setMargins(dpToPx(1), dpToPx(1), dpToPx(1), dpToPx(1)) }

            gravity = Gravity.CENTER
            val displayText = buildString {
                append(name)
                if (room.isNotEmpty()) append("\n$room")
                if (teacher.isNotEmpty() && course.step >= 2) append("\n$teacher")
            }
            text = displayText
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            setTextColor(Color.WHITE)
            setPadding(dpToPx(3))
            maxLines = course.step * 2 + 2
            includeFontPadding = false
            typeface = Typeface.DEFAULT_BOLD

            val color = try { Color.parseColor(bgColor) } catch (_: Exception) { Color.parseColor("#7986CB") }
            val drawable = GradientDrawable().apply {
                if (isOtherWeek) {
                    // 非本周：白底 + 淡色边框，确保有背景时也能看清
                    setColor(Color.argb(200, 255, 255, 255))
                    setStroke(dpToPx(1), Color.argb(80, Color.red(color), Color.green(color), Color.blue(color)))
                } else {
                    setColor(color)
                }
                cornerRadius = dpToPx(6).toFloat()
            }
            background = drawable

            if (isOtherWeek) {
                setTextColor(Color.argb(150, Color.red(color), Color.green(color), Color.blue(color)))
                typeface = Typeface.DEFAULT
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f)
            }

            setOnClickListener {
                showCourseDetail(course, name)
            }
        }
    }

    private fun adjustAlpha(color: Int, factor: Float): Int {
        val alpha = (Color.alpha(color) * factor).toInt()
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
    }

    private fun showCourseDetail(course: CourseDetailBean, name: String) {
        val dialog = AlertDialog.Builder(this, com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog)
            .create()

        val view = LayoutInflater.from(this).inflate(R.layout.dialog_course_detail, null)

        val bgColor = allBases.find { it.id == course.id }?.color ?: "#5C6BC0"
        try {
            view.findViewById<View>(R.id.color_bar).setBackgroundColor(Color.parseColor(bgColor))
        } catch (_: Exception) {}

        view.findViewById<TextView>(R.id.tv_course_name).text = name
        view.findViewById<TextView>(R.id.tv_teacher).text = course.teacher ?: "未设置"
        view.findViewById<TextView>(R.id.tv_room).text = course.room ?: "未设置"

        val startTime = TimeNode.getStartTime(course.startNode)
        val endTime = TimeNode.getEndTime(course.startNode + course.step - 1)
        val dayLabels = arrayOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
        val dayName = dayLabels.getOrElse(course.day - 1) { "" }
        view.findViewById<TextView>(R.id.tv_time).text = "$dayName 第${course.startNode}-${course.startNode + course.step - 1}节  $startTime-$endTime"
        view.findViewById<TextView>(R.id.tv_weeks).text = "第${course.startWeek}-${course.endWeek}周"

        view.findViewById<View>(R.id.btn_edit).setOnClickListener {
            dialog.dismiss()
            val intent = Intent(this, AddCourseActivity::class.java)
            intent.putExtra("course_id", course.id)
            intent.putExtra("table_id", course.tableId)
            addCourseLauncher.launch(intent)
        }
        view.findViewById<View>(R.id.btn_close).setOnClickListener { dialog.dismiss() }

        dialog.setView(view)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }

    private fun showBottomMenu() {
        val dialog = BottomSheetDialog(this)
        val view = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_menu, null)
        dialog.setContentView(view)

        // 周数 SeekBar
        val seekbar = view.findViewById<SeekBar>(R.id.seekbar_week)
        val tvLabel = view.findViewById<TextView>(R.id.tv_seekbar_label)
        seekbar.max = maxWeek
        val displayedWeek = binding.viewPager.currentItem + 1
        seekbar.progress = displayedWeek
        tvLabel.text = "当前：第${displayedWeek}周"
        seekbar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (progress > 0) {
                    tvLabel.text = "当前：第${progress}周"
                    if (fromUser) {
                        binding.viewPager.setCurrentItem(progress - 1, false)
                    }
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        // 课表名
        val studentInfo = table?.studentName?.let { if (it.isNotEmpty()) " · $it" else "" } ?: ""
        view.findViewById<TextView>(R.id.tv_table_name).text = "${table?.tableName ?: "南林课表"}$studentInfo"

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

        // 背景设置
        view.findViewById<View>(R.id.menu_about).setOnClickListener {
            dialog.dismiss()
            val intent = Intent(this, BackgroundSettingsActivity::class.java)
            bgLauncher.launch(intent)
        }

        // 关于
        view.findViewById<View>(R.id.menu_about_page).setOnClickListener {
            dialog.dismiss()
            startActivity(Intent(this, com.njfu.schedule.ui.settings.AboutActivity::class.java))
        }

        // 删除课表
        view.findViewById<View>(R.id.menu_delete).setOnClickListener {
            dialog.dismiss()
            AlertDialog.Builder(this)
                .setTitle("删除课表")
                .setMessage("确定要删除当前课表的所有课程吗？此操作不可恢复。")
                .setPositiveButton("删除") { _, _ ->
                    lifecycleScope.launch {
                        table?.let { t ->
                            withContext(Dispatchers.IO) {
                                val dao = App.instance.database.courseDao()
                                dao.deleteCoursesByTable(t.id)
                                dao.deleteDetailsByTable(t.id)
                            }
                            Toast.makeText(this@ScheduleActivity, "课表已清空", Toast.LENGTH_SHORT).show()
                            loadSchedule()
                        }
                    }
                }
                .setNegativeButton("取消", null)
                .show()
        }

        dialog.show()
    }

    private val settingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { loadSchedule() }

    private val bgLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { loadBackground() }

    private fun showCourseList() {
        if (allBases.isEmpty()) {
            Toast.makeText(this, "暂无课程", Toast.LENGTH_SHORT).show()
            return
        }

        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_course_list, null)
        val recyclerView = dialogView.findViewById<RecyclerView>(R.id.recycler_view)
        recyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        recyclerView.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            inner class VH(view: View) : RecyclerView.ViewHolder(view)

            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_course_list, parent, false)
                return VH(view)
            }

            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                val base = allBases[position]
                val details = allDetails.filter { it.id == base.id && it.tableId == base.tableId }
                val color = try { Color.parseColor(base.color) } catch (_: Exception) { Color.GRAY }

                val dot = holder.itemView.findViewById<View>(R.id.color_dot)
                val bg = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(color)
                }
                dot.background = bg

                holder.itemView.findViewById<TextView>(R.id.tv_name).text = base.courseName

                val info = if (details.isNotEmpty()) {
                    val d = details.first()
                    val dayLabels = arrayOf("周一","周二","周三","周四","周五","周六","周日")
                    val dayName = dayLabels.getOrElse(d.day - 1) { "" }
                    "${d.teacher ?: ""} · $dayName · 第${d.startWeek}-${d.endWeek}周"
                } else ""
                holder.itemView.findViewById<TextView>(R.id.tv_info).text = info

                holder.itemView.setOnClickListener {
                    dialog.dismiss()
                    val intent = Intent(this@ScheduleActivity, AddCourseActivity::class.java)
                    intent.putExtra("course_id", base.id)
                    intent.putExtra("table_id", base.tableId)
                    addCourseLauncher.launch(intent)
                }
            }

            override fun getItemCount() = allBases.size
        }

        dialog.show()
    }

    private fun showSettingsDialog() {
        // 保留旧方法作为备用
        startActivity(Intent(this, ScheduleSettingsActivity::class.java))
    }

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), resources.displayMetrics).toInt()
    }

    // ==================== 同步课表 ====================

    private fun syncSchedule() {
        val prefs = getSharedPreferences("njfu_login", android.content.Context.MODE_PRIVATE)
        val studentId = prefs.getString("student_id", "") ?: ""
        val password = prefs.getString("password", "") ?: ""

        if (studentId.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "请先导入课表（需要保存账号密码）", Toast.LENGTH_SHORT).show()
            return
        }

        Toast.makeText(this, "正在同步...", Toast.LENGTH_SHORT).show()

        lifecycleScope.launch {
            try {
                val importer = com.njfu.schedule.njfu.NjfuImporter()
                val result = withContext(Dispatchers.IO) {
                    importer.prepareSession()
                    val params = importer.fetchLoginPage()
                    importer.doLogin(studentId, password, params)
                    importer.fetchAndParseSchedule()
                }

                if (result.courses.isEmpty()) {
                    Toast.makeText(this@ScheduleActivity, "未获取到课程", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                // 对比新旧课表，找出冲突
                val conflicts = findConflicts(result.courses)

                if (conflicts.isEmpty()) {
                    // 无冲突，直接更新
                    withContext(Dispatchers.IO) {
                        overwriteCourses(result)
                    }
                    Toast.makeText(this@ScheduleActivity, "同步完成，课表无变化", Toast.LENGTH_SHORT).show()
                    loadSchedule()
                } else {
                    // 有冲突，弹窗让用户选择
                    showConflictDialog(conflicts, result)
                }
            } catch (e: Exception) {
                Toast.makeText(this@ScheduleActivity, "同步失败：${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    data class Conflict(
        val day: Int,
        val startNode: Int,
        val weeks: String,
        val oldName: String,
        val newName: String
    )

    private fun findConflicts(newCourses: List<com.njfu.schedule.njfu.NjfuImporter.CourseInfo>): List<Conflict> {
        val conflicts = mutableListOf<Conflict>()
        val dayNames = arrayOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

        // 构建旧课表的时间槽映射: (day, startNode, week) -> courseName
        val oldSlots = mutableMapOf<Triple<Int, Int, Int>, String>()
        for (base in allBases) {
            val details = allDetails.filter { it.id == base.id && it.tableId == base.tableId }
            for (d in details) {
                for (w in d.startWeek..d.endWeek) {
                    oldSlots[Triple(d.day, d.startNode, w)] = base.courseName
                }
            }
        }

        // 构建新课表的时间槽映射
        val newSlots = mutableMapOf<Triple<Int, Int, Int>, String>()
        for (c in newCourses) {
            for (w in c.weeks) {
                newSlots[Triple(c.day, c.startNode, w)] = c.name
            }
        }

        // 找出同一时间槽课程名不同的情况
        val checkedSlots = mutableSetOf<Pair<Int, Int>>() // (day, startNode) 去重
        for ((slot, oldName) in oldSlots) {
            val newName = newSlots[slot]
            if (newName != null && newName != oldName) {
                val key = Pair(slot.first, slot.second)
                if (key !in checkedSlots) {
                    checkedSlots.add(key)
                    val dayName = dayNames.getOrElse(slot.first - 1) { "" }
                    conflicts.add(Conflict(slot.first, slot.second, "${dayName} 第${slot.second}节 第${slot.third}周", oldName, newName))
                }
            }
        }

        return conflicts
    }

    private fun showConflictDialog(conflicts: List<Conflict>, result: com.njfu.schedule.njfu.NjfuImporter.ImportResult) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_sync_conflict, null)
        val rv = view.findViewById<RecyclerView>(R.id.rv_conflicts)
        rv.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        rv.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            inner class VH(v: View) : RecyclerView.ViewHolder(v)
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
                VH(LayoutInflater.from(parent.context).inflate(R.layout.item_sync_conflict, parent, false))
            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                val c = conflicts[position]
                holder.itemView.findViewById<TextView>(R.id.tv_conflict_time).text = c.weeks
                holder.itemView.findViewById<TextView>(R.id.tv_old_course).text = c.oldName
                holder.itemView.findViewById<TextView>(R.id.tv_new_course).text = c.newName
            }
            override fun getItemCount() = conflicts.size
        }

        AlertDialog.Builder(this)
            .setView(view)
            .setPositiveButton("使用新课表") { _, _ ->
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) { overwriteCourses(result) }
                    Toast.makeText(this@ScheduleActivity, "已更新为新课表", Toast.LENGTH_SHORT).show()
                    loadSchedule()
                }
            }
            .setNegativeButton("保留原课表", null)
            .show()
    }

    private suspend fun overwriteCourses(result: com.njfu.schedule.njfu.NjfuImporter.ImportResult) {
        val dao = App.instance.database.courseDao()
        val t = table ?: return
        dao.deleteCoursesByTable(t.id)
        dao.deleteDetailsByTable(t.id)

        val courses = result.courses
        val courseNames = courses.map { it.name }.distinct()
        val nameToId = courseNames.mapIndexed { idx, name -> name to idx }.toMap()

        for ((name, id) in nameToId) {
            val color = courseColors[id % courseColors.size]
            dao.insertCourseBase(com.njfu.schedule.bean.CourseBaseBean(id, name, color, t.id))
        }

        for (course in courses) {
            val id = nameToId[course.name]!!
            val step = course.endNode - course.startNode + 1
            val weekRanges = toWeekRanges(course.weeks)
            for ((startWeek, endWeek) in weekRanges) {
                dao.insertCourseDetail(com.njfu.schedule.bean.CourseDetailBean(
                    id, course.day, course.room, course.teacher,
                    course.startNode, step, startWeek, endWeek, 0, t.id
                ))
            }
        }

        // 更新 startDate
        t.startDate = result.semesterStartDate
        dao.updateTable(t)
    }

    private fun toWeekRanges(weeks: List<Int>): List<Pair<Int, Int>> {
        if (weeks.isEmpty()) return emptyList()
        val ranges = mutableListOf<Pair<Int, Int>>()
        var start = weeks[0]; var end = weeks[0]
        for (w in weeks.drop(1)) {
            if (w == end + 1) end = w
            else { ranges.add(Pair(start, end)); start = w; end = w }
        }
        ranges.add(Pair(start, end))
        return ranges
    }
}
