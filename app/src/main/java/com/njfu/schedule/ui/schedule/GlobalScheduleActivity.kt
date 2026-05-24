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
import com.njfu.schedule.databinding.ActivityGlobalScheduleBinding
import com.njfu.schedule.utils.PinyinUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GlobalScheduleActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGlobalScheduleBinding
    private var currentType = "jg0101"
    private var allEntities: List<Pair<String, String>> = emptyList()
    private var entityCounts: Map<String, Int> = emptyMap()

    private var sortMode: SortMode = SortMode.PINYIN
    private val activeFilters: MutableSet<String> = mutableSetOf()

    enum class SortMode { PINYIN, COUNT }

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
        binding.chipGroupType.visibility = View.GONE

        binding.etFilter.hint = "搜索${currentTypeName()}名称..."
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
        binding.fabBack.visibility = View.GONE
        binding.btnSyncCache.visibility = View.GONE

        binding.letterIndexBar.onLetterChanged = { letter ->
            val pos = entityAdapter.getLetterPositions()[letter] ?: return@onLetterChanged
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
                    val grouped = when (currentType) {
                        "jg0101" -> rows.groupingBy { it.teacher.ifEmpty { "(未知教师)" } }.eachCount()
                        "jx0601" -> rows.groupingBy { it.room.ifEmpty { "(未知教室)" } }.eachCount()
                        "bj0101" -> rows.groupingBy { it.className.ifEmpty { "(未知班级)" } }.eachCount()
                        "kc0101" -> rows.groupingBy { it.courseName.ifEmpty { "(未知课程)" } }.eachCount()
                        else -> emptyMap()
                    }
                    grouped.entries.filter { it.key.isNotBlank() }.associate { it.key to it.value }
                }

                entityCounts = counts
                allEntities = counts.keys.map { Pair(it, it) }

                if (allEntities.isEmpty()) {
                    showError("本地没有${currentTypeName()}数据，请到查询页点击同步")
                } else {
                    val q = binding.etFilter.text?.toString()?.trim().orEmpty()
                    applyListUpdate(q)
                    showEntities()
                }
            } catch (e: Exception) {
                showError("读取本地缓存失败: ${e.message ?: "未知错误"}")
            }
        }
    }

    private fun applyListUpdate(query: String) {
        var list = allEntities
        if (activeFilters.isNotEmpty()) {
            list = list.filter { (name, _) -> activeFilters.any { f -> name.contains(f) } }
        }
        if (query.isNotEmpty()) {
            list = list.filter { it.first.contains(query, ignoreCase = true) }
        }
        if (sortMode == SortMode.COUNT) {
            list = list.sortedByDescending { entityCounts[it.first] ?: 0 }
            entityAdapter.setFlatList(list)
        } else {
            entityAdapter.setFullList(list)
        }
        binding.letterIndexBar.visibility = if (sortMode == SortMode.PINYIN) View.VISIBLE else View.GONE
        binding.letterIndexBar.setActiveLetters(entityAdapter.getActiveLetters())
    }

    private fun showFilterDialog() {
        val sortLabels = arrayOf("按拼音首字母", "按课程数（多→少）")
        val filterOptions = filterOptionsForType()

        val dialogView = layoutInflater.inflate(R.layout.dialog_filter, null)
        val rgSort = dialogView.findViewById<android.widget.RadioGroup>(R.id.rg_sort)
        val cgFilter = dialogView.findViewById<com.google.android.material.chip.ChipGroup>(R.id.cg_filter)
        val tvFilterTitle = dialogView.findViewById<android.widget.TextView>(R.id.tv_filter_title)

        sortLabels.forEachIndexed { idx, label ->
            val rb = android.widget.RadioButton(this).apply {
                text = label
                id = idx
                isChecked = (idx == 0 && sortMode == SortMode.PINYIN) || (idx == 1 && sortMode == SortMode.COUNT)
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

        AlertDialog.Builder(this)
            .setTitle("筛选与排序")
            .setView(dialogView)
            .setPositiveButton("确定") { _, _ ->
                sortMode = if (rgSort.checkedRadioButtonId == 1) SortMode.COUNT else SortMode.PINYIN
                activeFilters.clear()
                for (i in 0 until cgFilter.childCount) {
                    val chip = cgFilter.getChildAt(i) as com.google.android.material.chip.Chip
                    if (chip.isChecked) activeFilters.add(chip.text.toString())
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
        "bj0101" -> "年级"
        else -> "筛选"
    }

    private fun filterOptionsForType(): List<String> {
        return when (currentType) {
            "jx0601" -> listOf("新庄", "白马", "淮安").filter { campus ->
                allEntities.any { it.first.contains(campus) }
            }
            "bj0101" -> {
                val years = allEntities.mapNotNull { e ->
                    Regex("^(20\\d{2})").find(e.first)?.groupValues?.get(1)
                }.distinct().sortedDescending()
                years
            }
            else -> emptyList()
        }
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
        binding.rvResults.visibility = View.GONE
        binding.layoutEmpty.visibility = View.GONE
        binding.fabBack.visibility = View.GONE
    }

    private fun showEntities() {
        binding.layoutLoading.visibility = View.GONE
        binding.layoutEmpty.visibility = View.GONE
        binding.layoutEntityList.visibility = View.VISIBLE
        binding.rvResults.visibility = View.GONE
    }

    private fun showError(msg: String) {
        binding.layoutLoading.visibility = View.GONE
        binding.layoutEntityList.visibility = View.GONE
        binding.rvResults.visibility = View.GONE
        binding.layoutEmpty.visibility = View.VISIBLE
        binding.tvEmpty.text = msg
        binding.btnRetry.visibility = View.VISIBLE
    }

    private fun currentTypeName() = when (currentType) {
        "jg0101" -> "教师"
        "jx0601" -> "教室"
        "bj0101" -> "班级"
        "kc0101" -> "课程"
        else -> "列表"
    }
}
