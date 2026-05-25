package com.njfu.schedule.ui.schedule

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.njfu.schedule.AppDatabase
import com.njfu.schedule.R
import com.njfu.schedule.databinding.ActivityFreeRoomBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 空闲教室查询 Activity。
 *
 * 基于教室课表(jx0601)数据，查询指定时间段没有课的教室。
 * 用户选择 星期 + 节次 + 周次 后，系统找出该时间段没有排课的教室。
 */
class FreeRoomActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFreeRoomBinding
    private var selectedDay: Int = -1
    private var selectedSection: String = ""
    private var selectedWeek: String = ""

    /** 当前周次，用于默认选中 */
    private var currentWeek: Int = 1

    private val resultAdapter = FreeRoomAdapter()

    companion object {
        /** 节次选项：每节课或组合节次 */
        val SECTION_OPTIONS = listOf(
            "1,2" to "第1,2节",
            "3,4" to "第3,4节",
            "5,6" to "第5,6节",
            "7,8" to "第7,8节",
            "9,10,11" to "第9,10,11节"
        )
        val DAY_LABELS = arrayOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFreeRoomBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        setupChips()
        setupSearchButton()

        // 获取当前周次
        lifecycleScope.launch {
            currentWeek = withContext(Dispatchers.IO) {
                try {
                    val dao = com.njfu.schedule.App.instance.database.courseDao()
                    val table = dao.getFirstTable()
                    if (table != null) com.njfu.schedule.utils.WeekUtils.getCurrentWeek(table.startDate) else 1
                } catch (_: Exception) { 1 }
            }
            // 默认选中当前周
            selectDefaultWeek()
        }

        // 显示初始提示
        binding.layoutEmpty.visibility = View.VISIBLE
        binding.tvEmpty.text = "请选择星期、节次和周次，然后点击查询"
    }

    private fun setupChips() {
        // 星期 chips
        for ((index, label) in DAY_LABELS.withIndex()) {
            val chip = com.google.android.material.chip.Chip(this).apply {
                text = label
                isCheckable = true
                id = View.generateViewId()
                tag = index + 1 // day 1-7
            }
            binding.cgDay.addView(chip)
        }

        // 节次 chips
        for ((value, label) in SECTION_OPTIONS) {
            val chip = com.google.android.material.chip.Chip(this).apply {
                text = label
                isCheckable = true
                id = View.generateViewId()
                tag = value // section string like "1,2"
            }
            binding.cgSection.addView(chip)
        }

        // 周次 chips（动态生成 1-20 周）
        for (w in 1..20) {
            val chip = com.google.android.material.chip.Chip(this).apply {
                text = "第${w}周"
                isCheckable = true
                id = View.generateViewId()
                tag = w.toString()
            }
            binding.cgWeek.addView(chip)
        }

        // 监听选择变化
        binding.cgDay.setOnCheckedStateChangeListener { group, checkedIds ->
            selectedDay = if (checkedIds.isNotEmpty()) {
                group.findViewById<View>(checkedIds[0])?.tag as? Int ?: -1
            } else -1
        }
        binding.cgSection.setOnCheckedStateChangeListener { group, checkedIds ->
            selectedSection = if (checkedIds.isNotEmpty()) {
                group.findViewById<View>(checkedIds[0])?.tag as? String ?: ""
            } else ""
        }
        binding.cgWeek.setOnCheckedStateChangeListener { group, checkedIds ->
            selectedWeek = if (checkedIds.isNotEmpty()) {
                group.findViewById<View>(checkedIds[0])?.tag as? String ?: ""
            } else ""
        }
    }

    private fun selectDefaultWeek() {
        // 自动选中当前周次
        for (i in 0 until binding.cgWeek.childCount) {
            val chip = binding.cgWeek.getChildAt(i) as com.google.android.material.chip.Chip
            if (chip.tag.toString() == currentWeek.toString()) {
                chip.isChecked = true
                selectedWeek = currentWeek.toString()
                break
            }
        }
    }

    private fun setupSearchButton() {
        binding.btnSearch.setOnClickListener {
            if (selectedDay < 0) {
                android.widget.Toast.makeText(this, "请选择星期", android.widget.Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (selectedSection.isEmpty()) {
                android.widget.Toast.makeText(this, "请选择节次", android.widget.Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (selectedWeek.isEmpty()) {
                android.widget.Toast.makeText(this, "请选择周次", android.widget.Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            searchFreeRooms()
        }
    }

    private fun searchFreeRooms() {
        binding.layoutLoading.visibility = View.VISIBLE
        binding.layoutEmpty.visibility = View.GONE
        binding.rvResults.visibility = View.GONE
        binding.tvResultSummary.visibility = View.GONE
        binding.tvLoadingText.text = "正在查询空闲教室..."

        lifecycleScope.launch {
            try {
                val dao = AppDatabase.getDatabase(this@FreeRoomActivity).globalCourseDao()

                // 获取所有教室
                val allRooms = withContext(Dispatchers.IO) { dao.getAllRoomNames() }

                if (allRooms.isEmpty()) {
                    binding.layoutLoading.visibility = View.GONE
                    binding.layoutEmpty.visibility = View.VISIBLE
                    binding.tvEmpty.text = "没有教室数据，请先同步全校课表缓存"
                    return@launch
                }

                // 获取该星期的教室课表数据，在内存中过滤周次和节次
                val targetDay = selectedDay
                val targetWeek = selectedWeek.toIntOrNull() ?: 1
                val targetSections = selectedSection.split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()

                val occupiedRooms = withContext(Dispatchers.IO) {
                    val dayRecords = dao.getByTypeSync("jx0601").filter { it.day == targetDay }
                    dayRecords.filter { record ->
                        // 检查周次是否匹配
                        val recordWeeks = parseWeeks(record.weeksStr)
                        val isOddOnly = record.weeksStr.contains("单")
                        val isEvenOnly = record.weeksStr.contains("双")
                        val weekMatch = targetWeek in recordWeeks &&
                            !(isOddOnly && targetWeek % 2 == 0) &&
                            !(isEvenOnly && targetWeek % 2 == 1)
                        if (!weekMatch) return@filter false
                        // 检查节次是否重叠
                        val recordSections = record.sectionNumbers
                            .split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()
                        recordSections.intersect(targetSections).isNotEmpty()
                    }.map { it.room }.filter { it.isNotEmpty() }.toSet()
                }

                // 计算空闲教室
                val freeRooms = allRooms.filter { it !in occupiedRooms }

                // 按校区分组显示
                val grouped = freeRooms.groupBy { room ->
                    when {
                        room.contains("新庄") -> "新庄校区"
                        room.contains("白马") -> "白马校区"
                        room.contains("淮安") -> "淮安校区"
                        else -> "其他"
                    }
                }.toSortedMap()

                binding.layoutLoading.visibility = View.GONE

                val sectionLabel = SECTION_OPTIONS.firstOrNull { it.first == selectedSection }?.second ?: "第${selectedSection}节"
                val weekLabel = "第${selectedWeek}周"

                if (freeRooms.isEmpty()) {
                    binding.layoutEmpty.visibility = View.VISIBLE
                    binding.tvEmpty.text = "${DAY_LABELS[selectedDay - 1]} ${sectionLabel} ${weekLabel} 没有空闲教室"
                } else {
                    binding.rvResults.visibility = View.VISIBLE
                    binding.rvResults.layoutManager = LinearLayoutManager(this@FreeRoomActivity)
                    binding.rvResults.adapter = resultAdapter
                    resultAdapter.setData(grouped)

                    binding.tvResultSummary.visibility = View.VISIBLE
                    val totalOccupied = occupiedRooms.size
                    binding.tvResultSummary.text = "${DAY_LABELS[selectedDay - 1]} ${sectionLabel} ${weekLabel} · " +
                            "共 ${allRooms.size} 间教室，空闲 ${freeRooms.size} 间，占用 ${totalOccupied} 间"
                }

            } catch (e: Exception) {
                binding.layoutLoading.visibility = View.GONE
                binding.layoutEmpty.visibility = View.VISIBLE
                binding.tvEmpty.text = "查询失败: ${e.message ?: "未知错误"}"
            }
        }
    }

    /**
     * 解析周次字符串，返回周次编号列表。
     * 支持 "1-16周"、"1-16周单"、"1,3,5周" 等格式。
     */
    private fun parseWeeks(weeksStr: String): List<Int> {
        if (weeksStr.isBlank()) return emptyList()
        val cleaned = weeksStr.replace(Regex("[周单双\\s()（）]"), "")
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

    /**
     * 空闲教室列表 Adapter，按校区分组显示。
     */
    private class FreeRoomAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        companion object {
            private const val TYPE_HEADER = 0
            private const val TYPE_ROOM = 1
        }

        private val items = mutableListOf<Item>()

        sealed class Item {
            data class Header(val campus: String, val count: Int) : Item()
            data class Room(val name: String) : Item()
        }

        fun setData(grouped: Map<String, List<String>>) {
            items.clear()
            for ((campus, rooms) in grouped) {
                items.add(Item.Header(campus, rooms.size))
                for (room in rooms) {
                    items.add(Item.Room(room))
                }
            }
            notifyDataSetChanged()
        }

        override fun getItemViewType(position: Int) = when (items[position]) {
            is Item.Header -> TYPE_HEADER
            is Item.Room -> TYPE_ROOM
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            return when (viewType) {
                TYPE_HEADER -> HeaderVH(inflater.inflate(R.layout.item_free_room_header, parent, false))
                else -> RoomVH(inflater.inflate(R.layout.item_free_room, parent, false))
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val item = items[position]) {
                is Item.Header -> (holder as HeaderVH).bind(item)
                is Item.Room -> (holder as RoomVH).bind(item)
            }
        }

        override fun getItemCount() = items.size

        class HeaderVH(view: View) : RecyclerView.ViewHolder(view) {
            private val tvCampus: TextView = view.findViewById(R.id.tv_campus)
            private val tvCount: TextView = view.findViewById(R.id.tv_count)
            fun bind(item: Item.Header) {
                tvCampus.text = item.campus
                tvCount.text = "${item.count}间"
            }
        }

        class RoomVH(view: View) : RecyclerView.ViewHolder(view) {
            private val tvRoomName: TextView = view.findViewById(R.id.tv_room_name)
            fun bind(item: Item.Room) {
                tvRoomName.text = item.name
            }
        }
    }
}
