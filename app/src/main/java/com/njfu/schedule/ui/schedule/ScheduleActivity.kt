package com.njfu.schedule.ui.schedule

import android.content.Intent
import android.content.res.Configuration
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
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.setPadding
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import androidx.work.WorkInfo
import androidx.work.WorkManager
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
import com.njfu.schedule.worker.GlobalCacheScheduler
import com.njfu.schedule.worker.GlobalCacheWorker
import com.njfu.schedule.widget.NextCourseWidget
import com.njfu.schedule.widget.TodayCourseWidget
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

        val bottomNav = findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottom_nav)
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_schedule -> { showScheduleView(); true }
                R.id.nav_query -> { showQueryView(); true }
                else -> false
            }
        }

        loadBackground()
        loadSchedule()
    }

    private fun showScheduleView() {
        binding.viewPager.visibility = if (allBases.isNotEmpty()) View.VISIBLE else View.GONE
        binding.emptyView.visibility = if (allBases.isEmpty()) View.VISIBLE else View.GONE
        findViewById<View>(R.id.query_container)?.visibility = View.GONE

        findViewById<View>(R.id.schedule_header)?.visibility = View.VISIBLE
        binding.headerRow.visibility = View.VISIBLE
    }

    private fun showQueryView() {
        binding.viewPager.visibility = View.GONE
        binding.emptyView.visibility = View.GONE

        findViewById<View>(R.id.schedule_header)?.visibility = View.GONE
        binding.headerRow.visibility = View.GONE

        var queryContainer = findViewById<View>(R.id.query_container)
        if (queryContainer == null) {
            val queryView = layoutInflater.inflate(R.layout.fragment_query, null)
            queryView.id = R.id.query_container
            val mainContent = findViewById<android.widget.LinearLayout>(R.id.main_content)
            val params = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
            mainContent.addView(queryView, mainContent.childCount, params)
            queryContainer = queryView

            queryView.findViewById<View>(R.id.card_teacher).setOnClickListener {
                openQuery("教师课表")
            }
            queryView.findViewById<View>(R.id.card_room).setOnClickListener {
                openQuery("教室课表")
            }
            queryView.findViewById<View>(R.id.card_course).setOnClickListener {
                openQuery("课程课表")
            }
            queryView.findViewById<View>(R.id.card_class).setOnClickListener {
                openQuery("班级课表")
            }
            queryView.findViewById<View>(R.id.card_empty_room).setOnClickListener {
                startActivity(Intent(this, com.njfu.schedule.ui.schedule.EmptyRoomActivity::class.java))
            }
            queryView.findViewById<View>(R.id.card_global_sync).setOnClickListener {
                syncGlobalCourseCache()
            }
        }
        queryContainer.visibility = View.VISIBLE
    }

    private fun openQuery(title: String) {
        val intent = Intent(this, com.njfu.schedule.ui.schedule.GlobalScheduleActivity::class.java)
        intent.putExtra("title", title)
        startActivity(intent)
    }

    private fun syncGlobalCourseCache() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_sync_progress, null)
        val tvLog = dialogView.findViewById<TextView>(R.id.tv_log)
        val progress = dialogView.findViewById<com.google.android.material.progressindicator.LinearProgressIndicator>(R.id.progress)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton("后台同步", null)
            .create()
        dialog.show()

        fun updateLog(msg: String) {
            tvLog.text = msg
        }

        updateLog("正在启动全校课表同步...")
        GlobalCacheScheduler.scheduleOneShot(this)

        val liveData = WorkManager.getInstance(this)
            .getWorkInfosForUniqueWorkLiveData(GlobalCacheWorker.WORK_NAME_ONESHOT)
        val observer = object : Observer<List<WorkInfo>> {
            override fun onChanged(infos: List<WorkInfo>) {
                val info = infos.firstOrNull {
                    it.state == WorkInfo.State.RUNNING ||
                        it.state == WorkInfo.State.ENQUEUED ||
                        it.state == WorkInfo.State.BLOCKED
                } ?: infos.firstOrNull() ?: return
                when (info.state) {
                    WorkInfo.State.ENQUEUED,
                    WorkInfo.State.BLOCKED -> {
                        progress.isIndeterminate = true
                        updateLog("等待网络任务启动...")
                    }
                    WorkInfo.State.RUNNING -> {
                        val msg = info.progress.getString(GlobalCacheWorker.KEY_PROGRESS_MSG) ?: "同步中..."
                        val current = info.progress.getInt(GlobalCacheWorker.KEY_PROGRESS_INDEX, 0)
                        val total = info.progress.getInt(GlobalCacheWorker.KEY_PROGRESS_TOTAL, 4).coerceAtLeast(1)
                        progress.isIndeterminate = false
                        progress.progress = ((current * 100) / total).coerceIn(0, 100)
                        updateLog("$msg ($current/$total)")
                    }
                    WorkInfo.State.SUCCEEDED -> {
                        progress.isIndeterminate = false
                        progress.progress = 100
                        updateLog("同步完成")
                        Toast.makeText(this@ScheduleActivity, "全校课表同步完成", Toast.LENGTH_SHORT).show()
                        liveData.removeObserver(this)
                    }
                    WorkInfo.State.FAILED -> {
                        progress.visibility = View.GONE
                        updateLog("同步失败，请先在导入课表页保存登录信息")
                        Toast.makeText(this@ScheduleActivity, "全校课表同步失败", Toast.LENGTH_SHORT).show()
                        liveData.removeObserver(this)
                    }
                    WorkInfo.State.CANCELLED -> {
                        progress.visibility = View.GONE
                        updateLog("同步已取消")
                        liveData.removeObserver(this)
                    }
                    else -> Unit
                }
            }
        }
        dialog.setOnDismissListener { liveData.removeObserver(observer) }
        liveData.observe(this, observer)
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
            tag.setTextColor(resources.getColor(R.color.text_secondary, theme))
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

        val showSat = table?.showSat ?: true
        val showSun = table?.showSun ?: true
        val maxDay = if (showSun) 7 else if (showSat) 6 else 5

        for (i in 0 until maxDay) {
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
                    setTextColor(resources.getColor(R.color.text_secondary, theme))
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

                    TodayCourseWidget.refreshAll(this@ScheduleActivity)
                    NextCourseWidget.refreshAll(this@ScheduleActivity)
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

            val prefs = com.njfu.schedule.utils.SecurePrefs.get(this@ScheduleActivity)
            val remarks = prefs.getString("remarks", null)
            if (!remarks.isNullOrEmpty()) {
                holder.tvRemarks.visibility = View.VISIBLE

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

        val otherWeekDetails = allDetails.filter { d ->
            !(d.startWeek <= week && d.endWeek >= week &&
                    (d.type == 0 || (d.type == 1 && week % 2 == 1) || (d.type == 2 && week % 2 == 0)))
        }

        val nodeCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(dpToPx(30), LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        for (node in 1..maxNode) {
            val startTime = TimeNode.getStartTime(node)
            val endTime = TimeNode.getEndTime(node)
            val tv = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, cellHeight)
                gravity = Gravity.CENTER
                text = "$node\n$startTime\n~$endTime"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 8f)
                setTextColor(resources.getColor(R.color.text_secondary, theme))
                setLineSpacing(0f, 0.86f)
                includeFontPadding = false
                setBackgroundResource(R.drawable.cell_border_bottom)
            }
            nodeCol.addView(tv)
        }
        container.addView(nodeCol)

        container.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(1, LinearLayout.LayoutParams.MATCH_PARENT)
            setBackgroundColor(resources.getColor(R.color.divider, theme))
        })

        val showSat = table?.showSat ?: true
        val showSun = table?.showSun ?: true
        val maxDay = if (showSun) 7 else if (showSat) 6 else 5

        for (day in 1..maxDay) {
            val dayCourses = weekDetails.filter { it.day == day }.sortedBy { it.startNode }
            val otherDayCourses = otherWeekDetails.filter { it.day == day }.sortedBy { it.startNode }
            val isToday = (week == currentWeek && day == todayOfWeek)

            val col = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                if (isToday) setBackgroundColor(Color.argb(32, 155, 190, 255))
            }

            var currentNode = 1
            while (currentNode <= maxNode) {

                val coursesAtNode = dayCourses.filter { it.startNode == currentNode }
                val otherCourse = otherDayCourses.find { it.startNode == currentNode }

                if (coursesAtNode.size > 1) {

                    val course = coursesAtNode.first()
                    val card = createCourseCard(course, nameMap, colorMap, cellHeight, false, coursesAtNode.size)
                    card.setOnClickListener {
                        showOverlapDialog(coursesAtNode, nameMap)
                    }
                    col.addView(card)
                    currentNode += course.step
                } else if (coursesAtNode.size == 1) {
                    val course = coursesAtNode.first()

                    val coveredCourses = dayCourses.filter {
                        it.startNode > currentNode && it.startNode < currentNode + course.step
                    }
                    if (coveredCourses.isNotEmpty()) {
                        val allOverlapping = listOf(course) + coveredCourses
                        val card = createCourseCard(course, nameMap, colorMap, cellHeight, false, allOverlapping.size)
                        card.setOnClickListener {
                            showOverlapDialog(allOverlapping, nameMap)
                        }
                        col.addView(card)
                    } else {
                        col.addView(createCourseCard(course, nameMap, colorMap, cellHeight, false))
                    }
                    currentNode += course.step
                } else if (otherCourse != null) {
                    col.addView(createCourseCard(otherCourse, nameMap, colorMap, cellHeight, true))
                    currentNode += otherCourse.step
                } else {

                    val capturedDay = day
                    val capturedNode = currentNode
                    val capturedTableId = table?.id ?: -1
                    col.addView(View(this).apply {
                        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, cellHeight)
                        setBackgroundResource(R.drawable.cell_border_bottom)
                        isClickable = true
                        isFocusable = true
                        setOnClickListener {
                            val intent = Intent(this@ScheduleActivity, AddCourseActivity::class.java)
                            intent.putExtra("table_id", capturedTableId)
                            intent.putExtra("prefill_day", capturedDay)
                            intent.putExtra("prefill_start_node", capturedNode)
                            addCourseLauncher.launch(intent)
                        }
                    })
                    currentNode++
                }
            }
            container.addView(col)

            if (day < maxDay) {
                container.addView(View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(1, LinearLayout.LayoutParams.MATCH_PARENT)
                    setBackgroundColor(resources.getColor(R.color.divider, theme))
                })
            }
        }
    }

    private fun createCourseCard(
        course: CourseDetailBean,
        nameMap: Map<Int, String>,
        colorMap: Map<Int, String>,
        cellHeight: Int,
        isOtherWeek: Boolean,
        overlapCount: Int = 1
    ): View {
        val name = nameMap[course.id] ?: ""
        val room = course.room ?: ""
        val teacher = course.teacher ?: ""
        val bgColor = colorMap[course.id] ?: courseColors[course.id % courseColors.size]

        val container = FrameLayout(this).apply {
            val margin = dpToPx(3)
            val verticalMargin = dpToPx(2)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (cellHeight * course.step - verticalMargin * 2).coerceAtLeast(cellHeight / 2)
            ).apply {
                setMargins(margin, verticalMargin, margin, verticalMargin)
            }
        }

        val tv = TextView(this).apply {
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            gravity = Gravity.CENTER
            val timeText = preventTimeWrap(formatCustomTime(course).ifEmpty {
                "${TimeNode.getStartTime(course.startNode)}~${TimeNode.getEndTime(course.startNode + course.step - 1)}"
            })
            val displayText = buildString {
                append(name)
                if (course.step >= 2) append("\n").append(timeText)
                if (teacher.isNotEmpty() && course.step >= 2) append("\n").append(teacher)
                if (room.isNotEmpty()) append("\n").append(room)
            }
            text = displayText
            setTextSize(TypedValue.COMPLEX_UNIT_SP, if (course.step <= 1) 9f else 9.6f)
            setTextColor(Color.WHITE)
            setPadding(dpToPx(4), dpToPx(5), dpToPx(4), dpToPx(5))
            maxLines = course.step * 2 + 2
            includeFontPadding = false
            typeface = Typeface.DEFAULT_BOLD
            elevation = 0f

            val color = try { Color.parseColor(bgColor) } catch (_: Exception) { Color.parseColor("#7986CB") }
            val isNightMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            val drawable = GradientDrawable().apply {
                if (isOtherWeek) {
                    if (isNightMode) {
                        setColor(Color.argb(24, Color.red(color), Color.green(color), Color.blue(color)))
                        setStroke(dpToPx(1), Color.argb(190, 120, 130, 148), dpToPx(4).toFloat(), dpToPx(3).toFloat())
                    } else {
                        setColor(Color.argb(18, Color.red(color), Color.green(color), Color.blue(color)))
                        setStroke(dpToPx(1), Color.argb(120, 180, 190, 205), dpToPx(4).toFloat(), dpToPx(3).toFloat())
                    }
                } else {
                    setColor(Color.argb(255, Color.red(color), Color.green(color), Color.blue(color)))
                    setStroke(dpToPx(1), if (isNightMode) Color.argb(235, 255, 255, 255) else Color.argb(60, 0, 0, 0))
                }
                cornerRadius = dpToPx(9).toFloat()
            }
            background = drawable

            if (isOtherWeek) {
                setTextColor(if (isNightMode) Color.argb(150, 226, 231, 239) else Color.argb(160, 90, 100, 115))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 8.8f)
            }
        }

        container.addView(tv)

        if (overlapCount > 1) {
            val layerBg = View(this).apply {
                layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT).apply {
                    setMargins(dpToPx(3), dpToPx(3), 0, 0)
                }
                background = GradientDrawable().apply {
                    val color = try { Color.parseColor(bgColor) } catch (_: Exception) { Color.parseColor("#7986CB") }
                    setColor(Color.argb(120, Color.red(color), Color.green(color), Color.blue(color)))
                    cornerRadius = dpToPx(9).toFloat()
                }
            }
            container.addView(layerBg, 0)

            (tv.layoutParams as FrameLayout.LayoutParams).setMargins(0, 0, dpToPx(3), dpToPx(3))

            val badge = TextView(this).apply {
                layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                    gravity = Gravity.TOP or Gravity.END
                    setMargins(0, dpToPx(2), dpToPx(2), 0)
                }
                text = "$overlapCount"
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                typeface = Typeface.DEFAULT_BOLD
                setPadding(dpToPx(4), dpToPx(1), dpToPx(4), dpToPx(1))
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#FF5252"))
                    cornerRadius = dpToPx(8).toFloat()
                    setStroke(dpToPx(1), Color.WHITE)
                }
            }
            container.addView(badge)
        }

        container.setOnClickListener {
            showCourseDetail(course, name)
        }

        return container
    }

    private fun formatCustomTime(course: CourseDetailBean): String {
        val start = course.customStartTime.orEmpty()
        val end = course.customEndTime.orEmpty()
        return if (start.isNotEmpty() && end.isNotEmpty()) "$start~$end" else ""
    }

    private fun preventTimeWrap(time: String): String {
        return time
            .replace("-", "\u2060-\u2060")
            .replace("~", "\u2060~\u2060")
    }

    private fun refreshWidgets() {
        TodayCourseWidget.refreshAll(this)
        NextCourseWidget.refreshAll(this)
    }

    private fun saveSyncRemarks(result: com.njfu.schedule.njfu.NjfuImporter.ImportResult) {
        com.njfu.schedule.utils.SecurePrefs.get(this).edit()
            .putString("remarks", result.remarks.joinToString("\n"))
            .apply()
    }

    private fun adjustAlpha(color: Int, factor: Float): Int {
        val alpha = (Color.alpha(color) * factor).toInt()
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
    }

    private fun showOverlapDialog(courses: List<CourseDetailBean>, nameMap: Map<Int, String>) {
        val dialog = BottomSheetDialog(this)
        val view = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_overlap, null)
        dialog.setContentView(view)

        val tvTitle = view.findViewById<TextView>(R.id.tv_title)
        tvTitle.text = "该时间段有 ${courses.size} 门课程重叠"

        val container = view.findViewById<LinearLayout>(R.id.ll_courses_container)

        courses.forEach { course ->
            val name = nameMap[course.id] ?: "未知课程"
            val time = (course.customStartTime ?: TimeNode.getStartTime(course.startNode)) + "-" +
                    (course.customEndTime ?: TimeNode.getEndTime(course.startNode + course.step - 1))
            val room = course.room ?: "未指定地点"
            val teacher = course.teacher ?: "未知教师"

            val cardView = LayoutInflater.from(this).inflate(R.layout.item_overlap_course, container, false)
            cardView.findViewById<TextView>(R.id.tv_course_name).text = name
            cardView.findViewById<TextView>(R.id.tv_course_time).text = time
            cardView.findViewById<TextView>(R.id.tv_course_room).text = room
            cardView.findViewById<TextView>(R.id.tv_course_teacher).text = teacher

            cardView.setOnClickListener {
                dialog.dismiss()
                showCourseDetail(course, name)
            }
            container.addView(cardView)
        }

        view.findViewById<View>(R.id.btn_close).setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
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

        val startTime = course.customStartTime ?: TimeNode.getStartTime(course.startNode)
        val endTime = course.customEndTime ?: TimeNode.getEndTime(course.startNode + course.step - 1)
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

        val studentInfo = table?.studentName?.let { if (it.isNotEmpty()) " · $it" else "" } ?: ""
        view.findViewById<TextView>(R.id.tv_table_name).text = "${table?.tableName ?: "南林课表"}$studentInfo"

        view.findViewById<View>(R.id.menu_time).setOnClickListener {
            dialog.dismiss()
            startActivity(Intent(this, TimeSettingsActivity::class.java))
        }

        view.findViewById<View>(R.id.menu_schedule_settings).setOnClickListener {
            dialog.dismiss()
            val intent = Intent(this, ScheduleSettingsActivity::class.java)
            settingsLauncher.launch(intent)
        }

        view.findViewById<View>(R.id.menu_courses).setOnClickListener {
            dialog.dismiss()
            showCourseList()
        }

        view.findViewById<View>(R.id.menu_background).setOnClickListener {
            dialog.dismiss()
            bgLauncher.launch(Intent(this, BackgroundSettingsActivity::class.java))
        }

        view.findViewById<View>(R.id.menu_about_page).setOnClickListener {
            dialog.dismiss()
            startActivity(Intent(this, com.njfu.schedule.ui.settings.AboutActivity::class.java))
        }

        view.findViewById<View>(R.id.menu_widget).setOnClickListener {
            dialog.dismiss()
            AlertDialog.Builder(this)
                .setTitle("添加桌面小组件")
                .setMessage("长按手机桌面空白处，选择「小组件」或「Widgets」，找到「南林课程表」即可添加到桌面。\n\n小组件会显示今日课程安排，每30分钟自动刷新。")
                .setPositiveButton("知道了", null)
                .show()
        }

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
                            refreshWidgets()
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

        startActivity(Intent(this, ScheduleSettingsActivity::class.java))
    }

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), resources.displayMetrics).toInt()
    }

    private fun syncSchedule() {
        val prefs = com.njfu.schedule.utils.SecurePrefs.get(this)
        val studentId = prefs.getString("student_id", "") ?: ""
        val password = prefs.getString("password", "") ?: ""

        if (studentId.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "请先导入课表（需要保存账号密码）", Toast.LENGTH_SHORT).show()
            return
        }

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

                val serverCourseNames = result.courses.map { it.name }.toSet()
                val localCourseNames = allBases.map { it.courseName }.toSet()
                val customCourseNames = localCourseNames - serverCourseNames

                log("本地 ${localCourseNames.size} 门，教务 ${serverCourseNames.size} 门")
                if (customCourseNames.isNotEmpty()) {
                    log("自定义课程：${customCourseNames.joinToString()}")
                }

                val changes = findSyncChanges(result.courses)
                val conflicts = findTimeConflicts(result.courses, customCourseNames)

                progress.visibility = View.GONE

                if (changes.isEmpty() && conflicts.isEmpty()) {
                    log("✓ 课表无变化")

                    withContext(Dispatchers.IO) { doSyncUpdateIO(result, customCourseNames) }
                    saveSyncRemarks(result)
                    dialog.dismiss()
                    Toast.makeText(this@ScheduleActivity, "同步完成，无变化", Toast.LENGTH_SHORT).show()
                    refreshWidgets()
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
        val type: String,  
        val timeDesc: String,
        val oldName: String,
        val newName: String
    )

    private fun findSyncChanges(newCourses: List<com.njfu.schedule.njfu.NjfuImporter.CourseInfo>): List<SyncChange> {
        val changes = mutableListOf<SyncChange>()
        val dayNames = arrayOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

        val oldSlots = mutableMapOf<Triple<Int, Int, Int>, String>()
        for (base in allBases) {
            val details = allDetails.filter { it.id == base.id && it.tableId == base.tableId }
            for (d in details) {
                for (w in d.startWeek..d.endWeek) {
                    oldSlots[Triple(d.day, d.startNode, w)] = base.courseName
                }
            }
        }

        val newSlots = mutableMapOf<Triple<Int, Int, Int>, String>()
        for (c in newCourses) {
            for (w in c.weeks) {
                newSlots[Triple(c.day, c.startNode, w)] = c.name
            }
        }

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

        for (c in newCourses) {
            for (w in c.weeks) {
                for (n in c.startNode..c.endNode) {
                    val slot = Triple(c.day, n, w)
                    val customName = customSlots[slot]
                    if (customName != null) {
                        val dayName = dayNames.getOrElse(c.day - 1) { "" }
                        val conflict = SyncChange("conflict", "$dayName 第${n}节 第${w}周", customName, c.name)
                        if (conflict !in conflicts) conflicts.add(conflict)
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
            withContext(Dispatchers.IO) { doSyncUpdateIO(result, keepNames) }
            saveSyncRemarks(result)
            Toast.makeText(this@ScheduleActivity, "同步完成", Toast.LENGTH_SHORT).show()
            refreshWidgets()
            loadSchedule()
        }
    }

    private suspend fun doSyncUpdateIO(result: com.njfu.schedule.njfu.NjfuImporter.ImportResult, keepNames: Set<String>) {
        val dao = App.instance.database.courseDao()
        val t = table ?: return

        val keepBases = allBases.filter { it.courseName in keepNames }
        val keepDetails = allDetails.filter { d -> keepBases.any { it.id == d.id && it.tableId == d.tableId } }

        dao.deleteCoursesByTable(t.id)
        dao.deleteDetailsByTable(t.id)

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

        if (keepNames.isNotEmpty()) {
            val maxId = (nameToId.values.maxOrNull() ?: -1) + 1
            keepBases.forEachIndexed { idx, base ->
                val newId = maxId + idx
                dao.insertCourseBase(com.njfu.schedule.bean.CourseBaseBean(newId, base.courseName, base.color, t.id))
                keepDetails.filter { it.id == base.id }.forEach { d ->
                    dao.insertCourseDetail(com.njfu.schedule.bean.CourseDetailBean(newId, d.day, d.room, d.teacher, d.startNode, d.step, d.startWeek, d.endWeek, d.type, t.id, d.customStartTime, d.customEndTime))
                }
            }
        }

        t.startDate = result.semesterStartDate
        dao.updateTable(t)
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
