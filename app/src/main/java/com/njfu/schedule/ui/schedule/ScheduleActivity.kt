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
            binding.tvWeek.text = "NJFU Schedule"
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
                // 一门课一行
                val lines = remarks.split("\n").filter { it.isNotBlank() }
                holder.tvRemarks.text = lines.joinToString("\n") { "· $it" }
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
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f)
                setTextColor(resources.getColor(R.color.text_secondary, theme))
                setLineSpacing(0f, 0.9f)
                setBackgroundResource(R.drawable.cell_border_bottom)
            }
            nodeCol.addView(tv)
        }
        container.addView(nodeCol)

        // 节次列右边的竖线
        container.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(1, LinearLayout.LayoutParams.MATCH_PARENT)
            setBackgroundColor(Color.parseColor("#30000000"))
        })

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
                    // 空格子，固定高度，底部有细线
                    col.addView(View(this).apply {
                        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, cellHeight)
                        setBackgroundResource(R.drawable.cell_border_bottom)
                    })
                    currentNode++
                }
            }
            container.addView(col)

            // 每天之间的竖线
            if (day < 7) {
                container.addView(View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(1, LinearLayout.LayoutParams.MATCH_PARENT)
                    setBackgroundColor(Color.parseColor("#30000000"))
                })
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
            )

            gravity = Gravity.CENTER
            val displayText = buildString {
                append(name)
                if (teacher.isNotEmpty() && course.step >= 2) append("\n$teacher")
                if (room.isNotEmpty()) append("\n$room")
            }
            text = displayText
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            setTextColor(Color.WHITE)
            setPadding(dpToPx(2), dpToPx(2), dpToPx(2), dpToPx(2))
            maxLines = course.step * 2 + 2
            includeFontPadding = false
            typeface = Typeface.DEFAULT_BOLD

            val color = try { Color.parseColor(bgColor) } catch (_: Exception) { Color.parseColor("#7986CB") }
            val drawable = GradientDrawable().apply {
                if (isOtherWeek) {
                    setColor(Color.argb(200, 255, 255, 255))
                    setStroke(dpToPx(1), Color.argb(60, Color.red(color), Color.green(color), Color.blue(color)))
                } else {
                    // 微渐变效果：顶部稍亮，底部原色
                    val lighterColor = Color.argb(255,
                        Math.min(255, Color.red(color) + 20),
                        Math.min(255, Color.green(color) + 20),
                        Math.min(255, Color.blue(color) + 20))
                    colors = intArrayOf(lighterColor, color)
                    orientation = GradientDrawable.Orientation.TOP_BOTTOM
                    setStroke(1, Color.argb(30, 0, 0, 0))
                }
                cornerRadius = dpToPx(5).toFloat()
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

        // 桌面小组件
        view.findViewById<View>(R.id.menu_widget).setOnClickListener {
            dialog.dismiss()
            AlertDialog.Builder(this)
                .setTitle("添加桌面小组件")
                .setMessage("长按手机桌面空白处，选择「小组件」或「Widgets」，找到「南林课程表」即可添加到桌面。\n\n小组件会显示今日课程安排，每30分钟自动刷新。")
                .setPositiveButton("知道了", null)
                .show()
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

        // 创建进度对话框
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_sync_progress, null)
        val tvLog = dialogView.findViewById<TextView>(R.id.tv_log)
        val progress = dialogView.findViewById<com.google.android.material.progressindicator.LinearProgressIndicator>(R.id.progress)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()
        dialog.show()

        fun log(msg: String) {
            runOnUiThread { tvLog.append("$msg\n") }
        }

        lifecycleScope.launch {
            try {
                val importer = com.njfu.schedule.njfu.NjfuImporter()

                log("正在连接教务系统...")
                withContext(Dispatchers.IO) { importer.prepareSession() }

                log("正在访问统一认证...")
                val params = withContext(Dispatchers.IO) { importer.fetchLoginPage() }

                log("正在验证账号密码...")
                withContext(Dispatchers.IO) { importer.doLogin(studentId, password, params) }

                log("登录成功，正在获取课表...")
                val result = withContext(Dispatchers.IO) { importer.fetchAndParseSchedule() }

                if (result.courses.isEmpty()) {
                    log("✗ 未获取到课程数据")
                    progress.visibility = View.GONE
                    dialog.setButton(AlertDialog.BUTTON_POSITIVE, "关闭") { d, _ -> d.dismiss() }
                    dialog.show()
                    return@launch
                }

                log("获取到 ${result.courses.map { it.name }.distinct().size} 门课程")

                // 找出自定义课程（本地有但教务系统没有的）
                val serverCourseNames = result.courses.map { it.name }.toSet()
                val localCourseNames = allBases.map { it.courseName }.toSet()
                val customCourseNames = localCourseNames - serverCourseNames

                // 对比变化
                val changes = findSyncChanges(result.courses)
                val conflicts = findTimeConflicts(result.courses, customCourseNames)

                progress.visibility = View.GONE

                if (changes.isEmpty() && conflicts.isEmpty()) {
                    log("✓ 课表无变化")
                    withContext(Dispatchers.IO) { overwriteCourses(result) }
                    dialog.dismiss()
                    Toast.makeText(this@ScheduleActivity, "同步完成，无变化", Toast.LENGTH_SHORT).show()
                    loadSchedule()
                } else {
                    log("发现 ${changes.size} 处变动")
                    if (customCourseNames.isNotEmpty()) log("保留 ${customCourseNames.size} 门自定义课程")
                    if (conflicts.isNotEmpty()) log("⚠ 存在时间冲突")
                    dialog.dismiss()
                    showSyncResultDialog(changes, conflicts, result, customCourseNames)
                }
            } catch (e: Exception) {
                progress.visibility = View.GONE
                log("✗ 同步失败：${e.message}")
                dialog.setButton(AlertDialog.BUTTON_POSITIVE, "关闭") { d, _ -> d.dismiss() }
                dialog.show()
            }
        }
    }

    data class SyncChange(
        val type: String,  // "changed", "added", "conflict"
        val timeDesc: String,
        val oldName: String,
        val newName: String
    )

    private fun findSyncChanges(newCourses: List<com.njfu.schedule.njfu.NjfuImporter.CourseInfo>): List<SyncChange> {
        val changes = mutableListOf<SyncChange>()
        val dayNames = arrayOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

        // 构建旧课表映射: (day, startNode, week) -> courseName
        val oldSlots = mutableMapOf<Triple<Int, Int, Int>, String>()
        for (base in allBases) {
            val details = allDetails.filter { it.id == base.id && it.tableId == base.tableId }
            for (d in details) {
                for (w in d.startWeek..d.endWeek) {
                    oldSlots[Triple(d.day, d.startNode, w)] = base.courseName
                }
            }
        }

        // 构建新课表映射
        val newSlots = mutableMapOf<Triple<Int, Int, Int>, String>()
        for (c in newCourses) {
            for (w in c.weeks) {
                newSlots[Triple(c.day, c.startNode, w)] = c.name
            }
        }

        // 1. 找出变化的（同一时间槽课程名不同）
        val checkedChanged = mutableSetOf<String>()
        for ((slot, oldName) in oldSlots) {
            val newName = newSlots[slot]
            if (newName != null && newName != oldName) {
                val key = "${slot.first}-${slot.second}-$oldName->$newName"
                if (key !in checkedChanged) {
                    checkedChanged.add(key)
                    val dayName = dayNames.getOrElse(slot.first - 1) { "" }
                    changes.add(SyncChange("changed", "$dayName 第${slot.second}节", oldName, newName))
                }
            }
        }

        // 2. 找出新增的（新课表有但旧课表没有的时间槽）
        val newCourseNames = mutableSetOf<String>()
        for ((slot, newName) in newSlots) {
            if (slot !in oldSlots) {
                newCourseNames.add(newName)
            }
        }
        for (name in newCourseNames) {
            changes.add(SyncChange("added", "", "", name))
        }

        return changes
    }

    private fun findTimeConflicts(
        newCourses: List<com.njfu.schedule.njfu.NjfuImporter.CourseInfo>,
        customCourseNames: Set<String>
    ): List<SyncChange> {
        val conflicts = mutableListOf<SyncChange>()
        val dayNames = arrayOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

        // 自定义课程的时间槽
        val customSlots = mutableMapOf<Triple<Int, Int, Int>, String>()
        for (base in allBases) {
            if (base.courseName in customCourseNames) {
                val details = allDetails.filter { it.id == base.id && it.tableId == base.tableId }
                for (d in details) {
                    for (w in d.startWeek..d.endWeek) {
                        for (n in d.startNode until d.startNode + d.step) {
                            customSlots[Triple(d.day, n, w)] = base.courseName
                        }
                    }
                }
            }
        }

        // 检查新课表是否和自定义课程冲突
        for (c in newCourses) {
            for (w in c.weeks) {
                for (n in c.startNode..c.endNode) {
                    val slot = Triple(c.day, n, w)
                    val customName = customSlots[slot]
                    if (customName != null) {
                        val dayName = dayNames.getOrElse(c.day - 1) { "" }
                        conflicts.add(SyncChange("conflict", "$dayName 第${n}节 第${w}周", customName, c.name))
                        return conflicts // 只报第一个冲突
                    }
                }
            }
        }
        return conflicts
    }

    private fun showSyncResultDialog(
        changes: List<SyncChange>,
        conflicts: List<SyncChange>,
        result: com.njfu.schedule.njfu.NjfuImporter.ImportResult,
        customCourseNames: Set<String>
    ) {
        val msg = buildString {
            if (changes.any { it.type == "changed" }) {
                append("课程变动：\n")
                changes.filter { it.type == "changed" }.forEach {
                    append("  ${it.timeDesc}: ${it.oldName} → ${it.newName}\n")
                }
                append("\n")
            }
            if (changes.any { it.type == "added" }) {
                append("新增课程：\n")
                changes.filter { it.type == "added" }.forEach {
                    append("  ${it.newName}\n")
                }
                append("\n")
            }
            if (customCourseNames.isNotEmpty()) {
                append("保留自定义课程：\n")
                customCourseNames.forEach { append("  $it\n") }
                append("\n")
            }
            if (conflicts.isNotEmpty()) {
                append("⚠ 时间冲突：\n")
                conflicts.forEach {
                    append("  ${it.timeDesc}: ${it.oldName}(自定义) 与 ${it.newName}(教务) 冲突\n")
                }
            }
        }

        val builder = AlertDialog.Builder(this)
            .setTitle("同步结果")
            .setMessage(msg.ifEmpty { "课表已更新" })

        if (conflicts.isNotEmpty()) {
            builder.setPositiveButton("更新并覆盖冲突") { _, _ ->
                doSyncUpdate(result, emptySet())
            }
            builder.setNeutralButton("更新但保留自定义") { _, _ ->
                doSyncUpdate(result, customCourseNames)
            }
            builder.setNegativeButton("取消", null)
        } else {
            builder.setPositiveButton("确认更新") { _, _ ->
                doSyncUpdate(result, customCourseNames)
            }
            builder.setNegativeButton("取消", null)
        }

        builder.show()
    }

    private fun doSyncUpdate(result: com.njfu.schedule.njfu.NjfuImporter.ImportResult, keepNames: Set<String>) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                val dao = App.instance.database.courseDao()
                val t = table ?: return@withContext

                if (keepNames.isEmpty()) {
                    // 全部覆盖
                    overwriteCourses(result)
                } else {
                    // 保留自定义课程，只更新教务系统的
                    val keepBases = allBases.filter { it.courseName in keepNames }
                    val keepDetails = allDetails.filter { d -> keepBases.any { it.id == d.id } }

                    dao.deleteCoursesByTable(t.id)
                    dao.deleteDetailsByTable(t.id)

                    // 写入新课表
                    val courses = result.courses
                    val courseNames = courses.map { it.name }.distinct()
                    val nameToId = courseNames.mapIndexed { idx, name -> name to idx }.toMap()
                    for ((name, id) in nameToId) {
                        dao.insertCourseBase(com.njfu.schedule.bean.CourseBaseBean(id, name, courseColors[id % courseColors.size], t.id))
                    }
                    for (course in courses) {
                        val id = nameToId[course.name]!!
                        val step = course.endNode - course.startNode + 1
                        for ((sw, ew) in toWeekRanges(course.weeks)) {
                            dao.insertCourseDetail(com.njfu.schedule.bean.CourseDetailBean(id, course.day, course.room, course.teacher, course.startNode, step, sw, ew, 0, t.id))
                        }
                    }

                    // 写回保留的自定义课程
                    val maxId = (nameToId.values.maxOrNull() ?: -1) + 1
                    keepBases.forEachIndexed { idx, base ->
                        val newId = maxId + idx
                        dao.insertCourseBase(com.njfu.schedule.bean.CourseBaseBean(newId, base.courseName, base.color, t.id))
                        keepDetails.filter { it.id == base.id }.forEach { d ->
                            dao.insertCourseDetail(com.njfu.schedule.bean.CourseDetailBean(newId, d.day, d.room, d.teacher, d.startNode, d.step, d.startWeek, d.endWeek, d.type, t.id))
                        }
                    }

                    t.startDate = result.semesterStartDate
                    dao.updateTable(t)
                }
            }
            Toast.makeText(this@ScheduleActivity, "同步完成", Toast.LENGTH_SHORT).show()
            loadSchedule()
        }
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
