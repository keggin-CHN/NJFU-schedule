package com.njfu.schedule.ui.schedule

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.njfu.schedule.AppDatabase
import com.njfu.schedule.R
import com.njfu.schedule.bean.GlobalCourseEntity
import com.njfu.schedule.databinding.ActivityGlobalScheduleBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GlobalScheduleActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGlobalScheduleBinding
    private var currentType = "jg0101"
    private var allEntities: List<Pair<String, String>> = emptyList()
    private var allRows: List<GlobalCourseEntity> = emptyList()
    private var entityCounts: Map<String, Int> = emptyMap()
    private var entityCampuses: Map<String, Set<String>> = emptyMap()
    private var entityMetadata: Map<String, String> = emptyMap()
    private var entitySearchText: Map<String, String> = emptyMap()

    private var sortMode: SortMode = SortMode.PINYIN
    private val activeFilters: MutableSet<String> = mutableSetOf()

    enum class SortMode { PINYIN, COUNT, COLLEGE }

    companion object {
        private const val SORT_RADIO_ID_BASE = 12000
    }

    private val entityAdapter = EntityAdapter { name, _ ->
        val intent = android.content.Intent(this, EntityScheduleActivity::class.java)
        intent.putExtra("type", currentType)
        intent.putExtra("entity_name", name)
        startActivity(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGlobalScheduleBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        val layoutManager = LinearLayoutManager(this)
        binding.rvEntities.layoutManager = layoutManager
        binding.rvEntities.adapter = entityAdapter

        val title = intent.getStringExtra("title") ?: "全校课表查询"
        currentType = when (title) {
            "教师课表" -> "jg0101"
            "教室课表" -> "jx0601"
            "班级课表" -> "bj0101"
            "课程课表" -> "kc0101"
            else -> "jg0101"
        }
        supportActionBar?.title = title

        binding.etFilter.hint = "搜索${currentTypeName()}、学期、原文、节次..."
        binding.etFilter.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val q = s?.toString()?.trim().orEmpty()
                binding.btnClear.visibility = if (q.isNotEmpty()) View.VISIBLE else View.GONE
                applyListUpdate(q)
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.btnClear.setOnClickListener {
            binding.etFilter.setText("")
        }

        binding.btnRetry.setOnClickListener { loadEntities() }

        binding.letterIndexBar.onLetterChanged = fun(letter: String) {
            val pos = entityAdapter.getLetterPositions()[letter] ?: return
            layoutManager.scrollToPositionWithOffset(pos, 0)
            showLetterOverlay(letter)
        }

        loadEntities()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_global_schedule, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_filter -> { showFilterDialog(); true }
            R.id.action_stats -> { showStatsDialog(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun loadEntities() {
        showLoading("正在读取本地缓存...")
        lifecycleScope.launch {
            try {
                val dao = AppDatabase.getDatabase(this@GlobalScheduleActivity).globalCourseDao()
                val rows = withContext(Dispatchers.IO) { dao.getByType(currentType).first() }

                val counts = withContext(Dispatchers.Default) {
                    rows.groupingBy { entityNameOf(it) }.eachCount()
                }

                val campusesMap = withContext(Dispatchers.Default) {
                    val map = mutableMapOf<String, MutableSet<String>>()
                    rows.forEach { row ->
                        val entity = entityNameOf(row)
                        if (entity.isNotBlank()) {
                            val campusList = listOf("新庄", "白马", "淮安").filter { row.room.contains(it) }
                            map.getOrPut(entity) { mutableSetOf() }.addAll(campusList)
                            if ((currentType == "kc0101" || currentType == "jg0101" || currentType == "bj0101") && row.collegeName.isNotBlank()) {
                                map.getOrPut(entity) { mutableSetOf() }.add(row.collegeName)
                            }
                        }
                    }
                    map
                }

                entityCounts = counts
                entityCampuses = campusesMap
                entityMetadata = buildEntityMetadata(counts, campusesMap)
                entityAdapter.setMetadata(entityMetadata)
                allRows = rows
                entitySearchText = withContext(Dispatchers.Default) {
                    rows.groupBy { entityNameOf(it) }.mapValues { (_, entityRows) ->
                        entityRows.joinToString("\n") { row ->
                            listOf(
                                row.courseName,
                                row.teacher,
                                row.room,
                                row.weeksStr,
                                row.sectionsStr,
                                row.className,
                                row.collegeName,
                                row.typeLabel,
                                row.term,
                                row.entityName,
                                row.sectionNumbers,
                                row.rawText,
                                row.rawHtml,
                                row.rawLinesJson
                            ).joinToString(" ")
                        }
                    }
                }
                allEntities = counts.keys.map { Pair(it, it) }

                if (allEntities.isEmpty()) {
                    showError("本地没有${currentTypeName()}数据，请先返回首页同步全校课表缓存")
                } else {
                    val q = binding.etFilter.text?.toString()?.trim().orEmpty()
                    applyListUpdate(q)
                }
            } catch (e: Exception) {
                showError("读取本地缓存失败: ${e.message ?: "未知错误"}")
            }
        }
    }

    private fun applyListUpdate(query: String) {
        var list = allEntities
        if (activeFilters.isNotEmpty()) {
            list = list.filter { (name, _) ->
                val campuses = entityCampuses[name] ?: emptySet()
                activeFilters.any { f -> matchesEntityFilter(name, campuses, f) }
            }
        }
        if (query.isNotEmpty()) {
            list = list.filter { (name, _) ->
                val campuses = entityCampuses[name] ?: emptySet()
                val searchText = entitySearchText[name].orEmpty()
                name.contains(query, ignoreCase = true) ||
                    campuses.any { it.contains(query, ignoreCase = true) } ||
                    searchText.contains(query, ignoreCase = true)
            }
        }
        if (sortMode == SortMode.COUNT) {
            list = list.sortedByDescending { entityCounts[it.first] ?: 0 }
            entityAdapter.setFlatList(list)
        } else if (sortMode == SortMode.COLLEGE) {
            list = list.sortedBy { namePair ->
                val colleges = entityCampuses[namePair.first]?.filter { isCollegeLike(it) } ?: emptyList()
                val collegeStr = if (colleges.isNotEmpty()) colleges.sorted().joinToString() else "ZZZ"
                collegeStr + namePair.first
            }
            entityAdapter.setFlatList(list)
        } else {
            entityAdapter.setFullList(list)
        }
        binding.letterIndexBar.visibility = if (sortMode == SortMode.PINYIN && list.isNotEmpty()) View.VISIBLE else View.GONE
        binding.letterIndexBar.setActiveLetters(entityAdapter.getActiveLetters())

        if (list.isEmpty()) {
            showNoMatches(query)
        } else {
            showEntities()
            binding.tvStateSummary.text = buildStateSummary(list.size, query)
        }
    }

    private fun showFilterDialog() {
        val sortLabels = if (currentType == "jg0101" || currentType == "kc0101" || currentType == "bj0101") arrayOf("按拼音首字母", "按课程数（多→少）", "按学院排序") else arrayOf("按拼音首字母", "按课程数（多→少）")
        val filterOptions = filterOptionsForType()

        val dialogView = layoutInflater.inflate(R.layout.dialog_filter, null)
        val rgSort = dialogView.findViewById<android.widget.RadioGroup>(R.id.rg_sort)
        val cgFilter = dialogView.findViewById<com.google.android.material.chip.ChipGroup>(R.id.cg_filter)
        val tvFilterTitle = dialogView.findViewById<android.widget.TextView>(R.id.tv_filter_title)

        sortLabels.forEachIndexed { idx, label ->
            val rb = android.widget.RadioButton(this).apply {
                text = label
                id = SORT_RADIO_ID_BASE + idx
                isChecked = (idx == 0 && sortMode == SortMode.PINYIN) ||
                            (idx == 1 && sortMode == SortMode.COUNT) || 
                            (idx == 2 && sortMode == SortMode.COLLEGE)
            }
            rgSort.addView(rb)
        }

        if (filterOptions.isEmpty()) {
            tvFilterTitle.visibility = View.GONE
            cgFilter.visibility = View.GONE
        } else {
            tvFilterTitle.text = filterTitleForType()
            for (opt in filterOptions) {
                val chip = com.google.android.material.chip.Chip(this).apply {
                    text = opt
                    isCheckable = true
                    isChecked = opt in activeFilters
                }
                cgFilter.addView(chip)
            }
        }

        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("筛选与排序")
            .setView(dialogView)
            .setPositiveButton("确定") { _, _ ->
                sortMode = when (rgSort.checkedRadioButtonId - SORT_RADIO_ID_BASE) {
                    1 -> SortMode.COUNT
                    2 -> SortMode.COLLEGE
                    else -> SortMode.PINYIN
                }
                activeFilters.clear()
                for (i in 0 until cgFilter.childCount) {
                    val chip = cgFilter.getChildAt(i) as com.google.android.material.chip.Chip
                    if (chip.isChecked && chip.visibility == View.VISIBLE) activeFilters.add(chip.text.toString())
                }
                applyListUpdate(binding.etFilter.text?.toString()?.trim().orEmpty())
            }
            .setNeutralButton("重置") { _, _ ->
                sortMode = SortMode.PINYIN
                activeFilters.clear()
                applyListUpdate(binding.etFilter.text?.toString()?.trim().orEmpty())
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun filterTitleForType(): String = when (currentType) {
        "jx0601" -> "校区"
        "kc0101", "jg0101" -> "校区与学院"
        "bj0101" -> "校区、年级与学院"
        else -> "筛选"
    }

    private fun filterOptionsForType(): List<String> {
        val campuses = listOf("新庄", "白马", "淮安").filter { campus ->
            entityCampuses.values.any { values -> campus in values }
        }
        val colleges = entityCampuses.values
            .flatten()
            .filter { isCollegeLike(it) }
            .distinct()
            .sorted()
        return when (currentType) {
            "jx0601" -> campuses
            "kc0101", "jg0101" -> campuses + colleges
            "bj0101" -> {
                val years = allEntities.mapNotNull { e ->
                    Regex("^(\\d{2})").find(e.first)?.groupValues?.get(1)
                }.distinct().sortedDescending().map { "${it}级" }
                campuses + years + colleges
            }
            else -> emptyList()
        }
    }

    private fun entityNameOf(row: GlobalCourseEntity): String {
        val unknown = "(未知${currentTypeName()})"
        return when (currentType) {
            "jg0101" -> row.teacher
            "jx0601" -> row.room
            "bj0101" -> row.className
            "kc0101" -> row.courseName
            else -> row.entityName
        }.ifEmpty { unknown }
    }

    private fun matchesEntityFilter(name: String, campuses: Set<String>, filter: String): Boolean {
        if (campuses.contains(filter) || name.contains(filter)) return true
        val yearPrefix = filter.removeSuffix("级")
        return currentType == "bj0101" && filter.endsWith("级") && name.startsWith(yearPrefix)
    }

    private fun isCollegeLike(value: String): Boolean {
        return value.endsWith("学院") || value.endsWith("系") || value.endsWith("部") || value.contains("中心")
    }

    private fun buildEntityMetadata(
        counts: Map<String, Int>,
        tagsByEntity: Map<String, Set<String>>
    ): Map<String, String> {
        val campusOrder = listOf("新庄", "白马", "淮安")
        return counts.mapValues { (name, count) ->
            val tags = tagsByEntity[name].orEmpty()
            val campuses = campusOrder.filter { it in tags }
            val colleges = tags.filter { isCollegeLike(it) }.sorted()
            buildList {
                add("${count} 条记录")
                if (campuses.isNotEmpty()) add(campuses.joinToString("、"))
                if (colleges.isNotEmpty()) add(colleges.take(2).joinToString("、") + if (colleges.size > 2) "等" else "")
            }.joinToString(" · ")
        }
    }

    private fun buildStateSummary(visibleCount: Int, query: String): String {
        val parts = mutableListOf<String>()
        parts.add("共 ${allEntities.size} 个${currentTypeName()}，当前显示 $visibleCount 个")
        parts.add("本地记录 ${allRows.size} 条")
        if (query.isNotBlank()) parts.add("搜索：$query")
        if (activeFilters.isNotEmpty()) parts.add("筛选：${activeFilters.sorted().joinToString("、")}")
        parts.add("排序：${sortLabel()}")
        return parts.joinToString(" · ")
    }

    private fun sortLabel(): String = when (sortMode) {
        SortMode.PINYIN -> "拼音首字母"
        SortMode.COUNT -> "记录数"
        SortMode.COLLEGE -> "学院"
    }

    private fun showStatsDialog() {
        if (allRows.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("${currentTypeName()}统计")
                .setMessage("暂无本地缓存数据")
                .setPositiveButton("关闭", null)
                .show()
            return
        }

        val termStats = allRows.groupingBy { it.term.ifBlank { "未知学期" } }.eachCount()
        val dayLabels = arrayOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
        val dayStats = allRows.groupingBy { row -> dayLabels.getOrNull(row.day - 1) ?: "未知星期" }.eachCount()
        val campusStats = allRows.flatMap { row ->
            listOf("新庄", "白马", "淮安").filter { row.room.contains(it) }.ifEmpty { listOf("未知校区") }
        }.groupingBy { it }.eachCount()
        val collegeStats = allRows.groupingBy { it.collegeName.ifBlank { "未知学院" } }.eachCount()
        val topEntities = entityCounts.entries.associate { it.key to it.value }

        val message = buildString {
            appendLine("记录数：${allRows.size}")
            appendLine("${currentTypeName()}数：${allEntities.size}")
            appendLine()
            appendLine("学期分布")
            appendLine(statsText(termStats))
            appendLine()
            appendLine("星期分布")
            appendLine(statsText(dayStats))
            appendLine()
            appendLine("校区分布")
            appendLine(statsText(campusStats))
            appendLine()
            appendLine("学院分布")
            appendLine(statsText(collegeStats))
            appendLine()
            appendLine("课程数最多的${currentTypeName()}")
            append(statsText(topEntities, 10))
        }

        AlertDialog.Builder(this)
            .setTitle("${currentTypeName()}统计")
            .setMessage(message)
            .setPositiveButton("关闭", null)
            .show()
    }

    private fun statsText(stats: Map<String, Int>, limit: Int = 8): String {
        return stats.entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(limit)
            .joinToString("\n") { "${it.key}: ${it.value}" }
            .ifBlank { "-" }
    }

    private val overlayHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val hideOverlay = Runnable { binding.tvLetterOverlay.visibility = View.GONE }
    private fun showLetterOverlay(letter: String) {
        binding.tvLetterOverlay.text = letter
        binding.tvLetterOverlay.visibility = View.VISIBLE
        overlayHandler.removeCallbacks(hideOverlay)
        overlayHandler.postDelayed(hideOverlay, 700)
    }

    private fun showLoading(msg: String) {
        binding.layoutLoading.visibility = View.VISIBLE
        binding.tvLoadingText.text = msg
        binding.layoutEntityList.visibility = View.GONE
        binding.layoutEmpty.visibility = View.GONE
        binding.btnRetry.visibility = View.GONE
        binding.tvStateSummary.text = msg
    }

    private fun showEntities() {
        binding.layoutLoading.visibility = View.GONE
        binding.layoutEmpty.visibility = View.GONE
        binding.layoutEntityList.visibility = View.VISIBLE
        binding.btnRetry.visibility = View.GONE
    }

    private fun showNoMatches(query: String) {
        val parts = mutableListOf<String>()
        if (query.isNotBlank()) parts.add("搜索“$query”")
        if (activeFilters.isNotEmpty()) parts.add("筛选：${activeFilters.sorted().joinToString("、")}")
        val condition = parts.joinToString("，").ifBlank { "当前条件" }
        val msg = "$condition 下没有匹配的${currentTypeName()}"
        binding.layoutLoading.visibility = View.GONE
        binding.layoutEntityList.visibility = View.GONE
        binding.layoutEmpty.visibility = View.VISIBLE
        binding.tvEmpty.text = msg
        binding.tvStateSummary.text = msg
        binding.btnRetry.visibility = View.GONE
    }

    private fun showError(msg: String) {
        binding.layoutLoading.visibility = View.GONE
        binding.layoutEntityList.visibility = View.GONE
        binding.layoutEmpty.visibility = View.VISIBLE
        binding.tvEmpty.text = msg
        binding.tvStateSummary.text = msg
        binding.btnRetry.visibility = View.VISIBLE
    }

    private fun currentTypeName() = when (currentType) {
        "jg0101" -> "教师"
        "jx0601" -> "教室"
        "bj0101" -> "班级"
        "kc0101" -> "课程"
        else -> "未知"
    }
}
