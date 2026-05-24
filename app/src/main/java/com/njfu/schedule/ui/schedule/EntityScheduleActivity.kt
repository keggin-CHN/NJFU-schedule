package com.njfu.schedule.ui.schedule

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.njfu.schedule.AppDatabase
import com.njfu.schedule.R
import com.njfu.schedule.bean.GlobalCourseEntity
import com.njfu.schedule.bean.GlobalCourseInfo
import com.njfu.schedule.bean.TimeNode
import com.njfu.schedule.databinding.ActivityEntityScheduleBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class EntityScheduleActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEntityScheduleBinding
    private var entityName: String = ""
    private var type: String = ""
    private var courses: List<GlobalCourseInfo> = emptyList()
    private var maxWeek: Int = 20
    private var currentWeek: Int = 1
    private var viewMode: ViewMode = ViewMode.GRID

    private val activeWeekFilter = mutableSetOf<Int>()
    private val activeDayFilter = mutableSetOf<Int>()

    private val courseColors = listOf(
        "#7986CB", "#4DB6AC", "#FF8A65", "#A1887F", "#90A4AE",
        "#4DD0E1", "#81C784", "#FFD54F", "#F06292", "#BA68C8",
        "#64B5F6", "#E57373", "#AED581", "#FFB74D", "#9575CD"
    )
    private var nameToColor: Map<String, String> = emptyMap()

    private val listAdapter = GlobalCourseAdapter { showCourseDetail(it) }

    enum class ViewMode { GRID, LIST }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEntityScheduleBinding.inflate(layoutInflater)
        setContentView(binding.root)

        type = intent.getStringExtra("type") ?: "jg0101"
        entityName = intent.getStringExtra("entity_name") ?: ""

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = entityName
        supportActionBar?.subtitle = typeLabel(type)
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.rvList.layoutManager = LinearLayoutManager(this)
        binding.rvList.adapter = listAdapter

        binding.btnPrevWeek.setOnClickListener {
            val cur = binding.viewPager.currentItem
            if (cur > 0) binding.viewPager.setCurrentItem(cur - 1, true)
        }
        binding.btnNextWeek.setOnClickListener {
            val cur = binding.viewPager.currentItem
            if (cur < maxWeek - 1) binding.viewPager.setCurrentItem(cur + 1, true)
        }
        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                val w = position + 1
                binding.tvWeekLabel.text = if (w == currentWeek) "第${w}周（本周）" else "第${w}周"
            }
        })

        loadCourses()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_entity_schedule, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(R.id.action_view_mode)?.setIcon(
            if (viewMode == ViewMode.GRID) android.R.drawable.ic_menu_agenda
            else android.R.drawable.ic_menu_view
        )
        menu.findItem(R.id.action_view_mode)?.title =
            if (viewMode == ViewMode.GRID) "切换到列表视图" else "切换到课表视图"
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_view_mode -> {
                viewMode = if (viewMode == ViewMode.GRID) ViewMode.LIST else ViewMode.GRID
                renderView()
                invalidateOptionsMenu()
                true
            }
            R.id.action_filter -> { showFilterDialog(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun loadCourses() {
        binding.layoutLoading.visibility = View.VISIBLE
        binding.viewPager.visibility = View.GONE
        binding.rvList.visibility = View.GONE
        binding.tvEmpty.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val dao = AppDatabase.getDatabase(this@EntityScheduleActivity).globalCourseDao()
                val all = withContext(Dispatchers.IO) { dao.getByType(type).first() }

                val filtered = withContext(Dispatchers.Default) {
                    all.filter { rowEntityName(it) == entityName }.map {
                        GlobalCourseInfo(
                            courseName = it.courseName,
                            teacher = it.teacher,
                            room = it.room,
                            weeksStr = it.weeksStr,
                            day = it.day,
                            sectionsStr = it.sectionsStr,
                            className = it.className,
                            collegeName = it.collegeName,
                            type = it.type,
                            typeLabel = it.typeLabel,
                            term = it.term,
                            entityName = it.entityName,
                            sectionNumbers = it.sectionNumbers,
                            slotIndex = it.slotIndex,
                            tableIndex = it.tableIndex,
                            rowIndex = it.rowIndex,
                            colIndex = it.colIndex,
                            rawText = it.rawText,
                            rawHtml = it.rawHtml,
                            rawLinesJson = it.rawLinesJson
                        )
                    }
                }

                courses = filtered
                nameToColor = courses.map { it.courseName }.distinct()
                    .mapIndexed { idx, name -> name to courseColors[idx % courseColors.size] }.toMap()

                maxWeek = (courses.flatMap { parseWeeks(it.weeksStr) }.maxOrNull() ?: 20).coerceAtLeast(20)
                currentWeek = computeCurrentWeek().coerceIn(1, maxWeek)

                binding.layoutLoading.visibility = View.GONE
                if (courses.isEmpty()) {
                    binding.tvEmpty.visibility = View.VISIBLE
                    binding.tvEmpty.text = "没有找到该${typeLabel(type)}的课程"
                } else {
                    renderView()
                }
            } catch (e: Exception) {
                binding.layoutLoading.visibility = View.GONE
                binding.tvEmpty.visibility = View.VISIBLE
                binding.tvEmpty.text = "加载失败: ${e.message ?: ""}"
            }
        }
    }

    private suspend fun computeCurrentWeek(): Int {
        return try {
            val dao = com.njfu.schedule.App.instance.database.courseDao()
            val table = withContext(Dispatchers.IO) { dao.getFirstTable() }
            if (table != null) com.njfu.schedule.utils.WeekUtils.getCurrentWeek(table.startDate) else 1
        } catch (_: Exception) { 1 }
    }

    private fun renderView() {
        if (viewMode == ViewMode.GRID) {
            binding.viewPager.visibility = View.VISIBLE
            binding.weekHeader.visibility = View.VISIBLE
            binding.rvList.visibility = View.GONE
            binding.viewPager.adapter = WeekPagerAdapter()
            binding.viewPager.setCurrentItem(currentWeek - 1, false)
            binding.tvWeekLabel.text = "第${currentWeek}周（本周）"
            updateDayHeaders()
        } else {
            binding.viewPager.visibility = View.GONE
            binding.weekHeader.visibility = View.GONE
            binding.rvList.visibility = View.VISIBLE
            val sorted = filteredCourses().sortedWith(compareBy({ it.day }, {
                parseSection(it.sectionsStr)?.first ?: 0
            }))
            listAdapter.submitList(sorted)
        }
    }

    private fun filteredCourses(): List<GlobalCourseInfo> {
        var list = courses
        if (activeDayFilter.isNotEmpty()) list = list.filter { it.day in activeDayFilter }
        if (activeWeekFilter.isNotEmpty()) list = list.filter { c ->
            val ws = parseWeeks(c.weeksStr)
            ws.any { it in activeWeekFilter }
        }
        return list
    }

    private fun updateDayHeaders() {
        val headerRow = binding.headerRow
        while (headerRow.childCount > 1) headerRow.removeViewAt(1)
        val labels = arrayOf("一", "二", "三", "四", "五", "六", "日")
        for (i in 0 until 7) {
            val tv = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
                gravity = Gravity.CENTER
                text = labels[i]
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setTextColor(resources.getColor(R.color.text_secondary, theme))
            }
            headerRow.addView(tv)
        }
    }

    inner class WeekPagerAdapter : RecyclerView.Adapter<WeekPagerAdapter.VH>() {
        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val grid: LinearLayout = view.findViewById(R.id.schedule_grid)
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.fragment_entity_week, parent, false)
            return VH(view)
        }
        override fun getItemCount() = maxWeek
        override fun onBindViewHolder(holder: VH, position: Int) {
            renderWeekGrid(holder.grid, position + 1)
        }
    }

    private fun renderWeekGrid(grid: LinearLayout, week: Int) {
        grid.removeAllViews()
        val maxNode = 11
        val cellHeight = dpToPx(56)

        val nodeCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(dpToPx(30), LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        for (node in 1..maxNode) {
            val tv = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, cellHeight)
                gravity = Gravity.CENTER
                text = "$node\n${TimeNode.getStartTime(node)}\n~${TimeNode.getEndTime(node)}"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 8f)
                setTextColor(resources.getColor(R.color.text_secondary, theme))
                setLineSpacing(0f, 0.86f)
                includeFontPadding = false
                setBackgroundResource(R.drawable.cell_border_bottom)
            }
            nodeCol.addView(tv)
        }
        grid.addView(nodeCol)
        grid.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(1, LinearLayout.LayoutParams.MATCH_PARENT)
            setBackgroundColor(resources.getColor(R.color.divider, theme))
        })

        val weekCourses = filteredCourses().filter { c -> week in parseWeeks(c.weeksStr) }

        for (day in 1..7) {
            val col = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val dayCourses = weekCourses.filter { it.day == day }.mapNotNull { c ->
                val (start, span) = parseSection(c.sectionsStr) ?: return@mapNotNull null
                Triple(start, span, c)
            }.sortedBy { it.first }

            var currentNode = 1
            while (currentNode <= maxNode) {
                val match = dayCourses.firstOrNull { it.first == currentNode }
                if (match != null) {
                    val (_, span, c) = match
                    col.addView(buildCard(c, span, cellHeight, nameToColor[c.courseName] ?: "#7986CB"))
                    currentNode += span
                } else {
                    col.addView(View(this).apply {
                        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, cellHeight)
                        setBackgroundResource(R.drawable.cell_border_bottom)
                    })
                    currentNode++
                }
            }
            grid.addView(col)
            if (day < 7) {
                grid.addView(View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(1, LinearLayout.LayoutParams.MATCH_PARENT)
                    setBackgroundColor(resources.getColor(R.color.divider, theme))
                })
            }
        }
    }

    private fun buildCard(c: GlobalCourseInfo, span: Int, cellHeight: Int, bgColor: String): View {
        val container = com.google.android.material.card.MaterialCardView(this).apply {
            val verticalMargin = dpToPx(2)
            val margin = dpToPx(3)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (cellHeight * span - verticalMargin * 2).coerceAtLeast(cellHeight / 2)
            ).apply { setMargins(margin, verticalMargin, margin, verticalMargin) }
            radius = dpToPx(10).toFloat()
            cardElevation = dpToPx(2).toFloat()
            setCardBackgroundColor(try { Color.parseColor(bgColor) } catch (_: Exception) { Color.parseColor("#7986CB") })
            strokeWidth = 0
            isClickable = true
            isFocusable = true
            setOnClickListener { showCourseDetail(c) }
        }

        val tv = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT)
            gravity = Gravity.CENTER
            val info = buildString {
                append(c.courseName)
                if (span >= 2 && c.room.isNotEmpty()) append("\n@").append(c.room)
                if (span >= 2 && type != "jg0101" && c.teacher.isNotEmpty()) append("\n").append(c.teacher)
                if (span >= 2 && c.weeksStr.isNotEmpty()) append("\n").append(c.weeksStr)
            }
            text = info
            setTextSize(TypedValue.COMPLEX_UNIT_SP, if (span <= 1) 9f else 9.6f)
            setTextColor(Color.WHITE)
            setPadding(dpToPx(4), dpToPx(5), dpToPx(4), dpToPx(5))
            includeFontPadding = false
            typeface = Typeface.DEFAULT_BOLD
        }
        container.addView(tv)
        return container
    }

    private fun showCourseDetail(c: GlobalCourseInfo) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_global_course_detail, null)
        dialogView.findViewById<TextView>(R.id.tv_detail_course_name).text = c.courseName
        dialogView.findViewById<TextView>(R.id.tv_detail_class_name).text =
            c.className.ifBlank { c.entityName.ifBlank { "-" } }
        dialogView.findViewById<TextView>(R.id.tv_detail_type).text =
            listOf(c.typeLabel, c.type).filter { it.isNotBlank() }.joinToString(" / ")
        dialogView.findViewById<TextView>(R.id.tv_detail_term).text = c.term.ifBlank { "-" }
        dialogView.findViewById<TextView>(R.id.tv_detail_entity).text = c.entityName.ifBlank { "-" }
        dialogView.findViewById<TextView>(R.id.tv_detail_teacher).text = c.teacher.ifBlank { "-" }
        dialogView.findViewById<TextView>(R.id.tv_detail_room).text = c.room.ifBlank { "-" }
        dialogView.findViewById<TextView>(R.id.tv_detail_time).text =
            "${dayLabel(c.day)} ${c.sectionsStr.ifBlank { "-" }}"
        dialogView.findViewById<TextView>(R.id.tv_detail_weeks).text = c.weeksStr.ifBlank { "-" }
        dialogView.findViewById<TextView>(R.id.tv_detail_section).text =
            c.sectionNumbers.ifBlank { c.sectionsStr.ifBlank { "-" } }

        val bgColor = try { Color.parseColor(nameToColor[c.courseName] ?: "#7986CB") } catch (_: Exception) { Color.parseColor("#7986CB") }
        dialogView.findViewById<View>(R.id.color_bar).setBackgroundColor(bgColor)

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
        dialogView.findViewById<View>(R.id.btn_detail_close).setOnClickListener { dialog.dismiss() }
    }

    private fun showFilterDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_entity_filter, null)
        val cgWeek = dialogView.findViewById<com.google.android.material.chip.ChipGroup>(R.id.cg_week)
        val cgDay = dialogView.findViewById<com.google.android.material.chip.ChipGroup>(R.id.cg_day)

        for (w in 1..maxWeek) {
            val chip = com.google.android.material.chip.Chip(this).apply {
                text = "第${w}周"
                isCheckable = true
                isChecked = w in activeWeekFilter
            }
            cgWeek.addView(chip)
        }
        val dayLabels = arrayOf("周一","周二","周三","周四","周五","周六","周日")
        for (d in 1..7) {
            val chip = com.google.android.material.chip.Chip(this).apply {
                text = dayLabels[d-1]
                isCheckable = true
                isChecked = d in activeDayFilter
            }
            cgDay.addView(chip)
        }

        AlertDialog.Builder(this)
            .setTitle("筛选")
            .setView(dialogView)
            .setPositiveButton("确定") { _, _ ->
                activeWeekFilter.clear()
                activeDayFilter.clear()
                for (i in 0 until cgWeek.childCount) {
                    val chip = cgWeek.getChildAt(i) as com.google.android.material.chip.Chip
                    if (chip.isChecked) activeWeekFilter.add(i + 1)
                }
                for (i in 0 until cgDay.childCount) {
                    val chip = cgDay.getChildAt(i) as com.google.android.material.chip.Chip
                    if (chip.isChecked) activeDayFilter.add(i + 1)
                }
                renderView()
            }
            .setNeutralButton("重置") { _, _ ->
                activeWeekFilter.clear()
                activeDayFilter.clear()
                renderView()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun parseWeeks(weeksStr: String): List<Int> {
        if (weeksStr.isBlank()) return emptyList()
        val cleaned = weeksStr.replace(Regex("[周\\s()]"), "")
        val result = mutableListOf<Int>()
        for (part in cleaned.split(",")) {
            val p = part.trim()
            if (p.isEmpty()) continue
            if (p.contains("-")) {
                val nums = p.split("-").mapNotNull { it.toIntOrNull() }
                if (nums.size == 2) for (w in nums[0]..nums[1]) result.add(w)
            } else {
                p.toIntOrNull()?.let { result.add(it) }
            }
        }
        return result
    }

    private fun parseSection(s: String): Pair<Int, Int>? {
        val nums = Regex("\\d+").findAll(s).map { it.value.toInt() }.toList()
        if (nums.isEmpty()) return null
        val start = nums.first()
        val end = nums.last()
        return Pair(start, (end - start + 1).coerceAtLeast(1))
    }

    private fun rowEntityName(row: GlobalCourseEntity): String {
        val unknown = "(未知${typeLabel(type)})"
        return when (type) {
            "jg0101" -> row.teacher
            "jx0601" -> row.room
            "bj0101" -> row.className
            "kc0101" -> row.courseName
            else -> row.entityName
        }.ifEmpty { unknown }
    }

    private fun dayLabel(day: Int): String {
        val labels = arrayOf("星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日")
        return labels.getOrNull(day - 1) ?: "未知星期"
    }

    private fun typeLabel(t: String) = when (t) {
        "jg0101" -> "教师"
        "jx0601" -> "教室"
        "bj0101" -> "班级"
        "kc0101" -> "课程"
        else -> ""
    }

    private fun dpToPx(dp: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), resources.displayMetrics).toInt()
}
