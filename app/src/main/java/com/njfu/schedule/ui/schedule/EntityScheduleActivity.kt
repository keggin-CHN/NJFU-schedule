package com.njfu.schedule.ui.schedule

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.njfu.schedule.AppDatabase
import com.njfu.schedule.R
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
    private var viewMode = ViewMode.GRID

    private val courseColors = listOf(
        "#7986CB", "#4DB6AC", "#FF8A65", "#A1887F", "#90A4AE",
        "#4DD0E1", "#81C784", "#FFD54F", "#F06292", "#BA68C8",
        "#64B5F6", "#E57373", "#AED581", "#FFB74D", "#9575CD"
    )

    private val listAdapter = GlobalCourseAdapter { /* no-op */ }

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
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun loadCourses() {
        binding.layoutLoading.visibility = View.VISIBLE
        binding.scrollGrid.visibility = View.GONE
        binding.rvList.visibility = View.GONE
        binding.tvEmpty.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val dao = AppDatabase.getDatabase(this@EntityScheduleActivity).globalCourseDao()
                val all = withContext(Dispatchers.IO) { dao.getByType(type).first() }

                val filtered = withContext(Dispatchers.Default) {
                    all.filter { row ->
                        when (type) {
                            "jg0101" -> row.teacher == entityName
                            "jx0601" -> row.room == entityName
                            "bj0101" -> row.className == entityName
                            "kc0101" -> row.courseName == entityName
                            else -> false
                        }
                    }.map {
                        GlobalCourseInfo(
                            courseName = it.courseName,
                            teacher = it.teacher,
                            room = it.room,
                            weeksStr = it.weeksStr,
                            day = it.day,
                            sectionsStr = it.sectionsStr,
                            className = it.className
                        )
                    }
                }

                courses = filtered
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

    private fun renderView() {
        if (viewMode == ViewMode.GRID) {
            binding.scrollGrid.visibility = View.VISIBLE
            binding.rvList.visibility = View.GONE
            renderGrid()
        } else {
            binding.scrollGrid.visibility = View.GONE
            binding.rvList.visibility = View.VISIBLE
            val sorted = courses.sortedWith(compareBy({ it.day }, {
                it.sectionsStr.replace(Regex("\\D"), "").take(2).toIntOrNull() ?: 0
            }))
            listAdapter.submitList(sorted)
        }
    }

    private fun renderGrid() {
        val grid = binding.scheduleGrid
        grid.removeAllViews()

        val maxNode = 11
        val cellHeight = dpToPx(56)
        val nameToColor = courses.map { it.courseName }.distinct()
            .mapIndexed { idx, name -> name to courseColors[idx % courseColors.size] }.toMap()

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

        for (day in 1..7) {
            val col = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val dayCourses = courses.filter { it.day == day }.mapNotNull { c ->
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
        val tv = TextView(this).apply {
            val verticalMargin = dpToPx(2)
            val margin = dpToPx(3)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (cellHeight * span - verticalMargin * 2).coerceAtLeast(cellHeight / 2)
            ).apply { setMargins(margin, verticalMargin, margin, verticalMargin) }

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
            background = GradientDrawable().apply {
                val parsed = try { Color.parseColor(bgColor) } catch (_: Exception) { Color.parseColor("#7986CB") }
                setColor(parsed)
                cornerRadius = dpToPx(9).toFloat()
            }
        }
        return tv
    }

    private fun parseSection(s: String): Pair<Int, Int>? {
        val nums = Regex("\\d+").findAll(s).map { it.value.toInt() }.toList()
        if (nums.isEmpty()) return null
        val start = nums.first()
        val end = nums.last()
        return Pair(start, (end - start + 1).coerceAtLeast(1))
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
