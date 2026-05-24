package com.njfu.schedule.ui.schedule

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.njfu.schedule.AppDatabase
import com.njfu.schedule.databinding.ActivityEmptyRoomBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class EmptyRoomActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEmptyRoomBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEmptyRoomBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }

        setupSpinners()

        binding.btnSearch.setOnClickListener { performLocalSearch() }
        binding.rvResults.layoutManager = LinearLayoutManager(this)
    }

    private fun setupSpinners() {
        val terms = listOf("2025-2026-2" to "2025-2026第二学期", "2025-2026-1" to "2025-2026第一学期")
        val campuses = listOf("" to "-不限-", "1" to "新庄校区", "2" to "白马校区", "3" to "淮安校区")
        val weeks = listOf("" to "-不限-") + (1..30).map { "$it" to "第${it}周" }
        val days = listOf("" to "-不限-", "1" to "周一", "2" to "周二", "3" to "周三", "4" to "周四", "5" to "周五", "6" to "周六", "7" to "周日")
        val sections = listOf("" to "-不限-") + (1..15).map { "$it" to "第${it}节" }

        fun setAdapter(spinner: Spinner, items: List<Pair<String, String>>) {
            val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, items.map { it.second })
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinner.adapter = adapter
        }

        setAdapter(binding.spinnerXnxqh, terms)
        setAdapter(binding.spinnerXqid, campuses)
        setAdapter(binding.spinnerZc, weeks)
        setAdapter(binding.spinnerXq, days)
        setAdapter(binding.spinnerJc1, sections)
        setAdapter(binding.spinnerJc2, sections)
    }

    private fun performLocalSearch() {
        val campusKey = listOf("", "1", "2", "3")[binding.spinnerXqid.selectedItemPosition]
        val campusName = when (campusKey) {
            "1" -> "新庄"
            "2" -> "百马"
            "3" -> "淮安"
            else -> ""
        }
        val weeks = listOf("") + (1..30).map { "$it" }
        val xq = listOf("", "1", "2", "3", "4", "5", "6", "7")
        val jc = listOf("") + (1..15).map { "$it" }

        val selZc = weeks[binding.spinnerZc.selectedItemPosition].toIntOrNull()
        val selXq = xq[binding.spinnerXq.selectedItemPosition].toIntOrNull()
        val selJc1 = jc[binding.spinnerJc1.selectedItemPosition].toIntOrNull()
        val selJc2Idx = binding.spinnerJc2.selectedItemPosition
        val selJc2 = if (selJc2Idx == 0) selJc1 else jc[selJc2Idx].toIntOrNull()

        binding.progress.visibility = View.VISIBLE
        binding.rvResults.visibility = View.GONE
        binding.tvEmpty.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val dao = AppDatabase.getDatabase(this@EmptyRoomActivity).globalCourseDao()

                val (allRooms, occupied) = withContext(Dispatchers.IO) {
                    val rooms = dao.getAllRooms()
                    val courses = if (selZc != null || selXq != null || selJc1 != null) {
                        dao.getByTypeSync("jx0601")
                    } else emptyList()
                    Pair(rooms, courses)
                }

                if (allRooms.isEmpty()) {
                    binding.progress.visibility = View.GONE
                    binding.tvEmpty.visibility = View.VISIBLE
                    binding.tvEmpty.text = "本地没有教室数据，请先在查询页同步全校课表"
                    return@launch
                }

                val occupiedSet = withContext(Dispatchers.Default) {
                    val matched = mutableSetOf<String>()
                    for (c in occupied) {
                        if (selXq != null && c.day != selXq) continue
                        if (selJc1 != null) {
                            val nums = Regex("\\d+").findAll(c.sectionsStr).map { it.value.toInt() }.toList()
                            if (nums.isEmpty()) continue
                            val cs = nums.first()
                            val ce = nums.last()
                            val target1 = selJc1
                            val target2 = selJc2 ?: selJc1
                            if (ce < target1 || cs > target2) continue
                        }
                        if (selZc != null && !weekIncluded(c.weeksStr, selZc)) continue
                        matched.add(c.room)
                    }
                    matched
                }

                val freeRooms = withContext(Dispatchers.Default) {
                    allRooms.filter {
                        it.isNotEmpty() &&
                            (campusName.isEmpty() || it.contains(campusName) || (campusKey == "2" && it.contains("白马"))) &&
                            it !in occupiedSet
                    }
                }

                binding.progress.visibility = View.GONE
                if (freeRooms.isEmpty()) {
                    binding.tvEmpty.visibility = View.VISIBLE
                    binding.tvEmpty.text = "没有找到符合条件的空教室"
                } else {
                    binding.rvResults.visibility = View.VISIBLE
                    binding.rvResults.adapter = SimpleTextAdapter(freeRooms) { }
                }
            } catch (e: Exception) {
                binding.progress.visibility = View.GONE
                Toast.makeText(this@EmptyRoomActivity, "查询失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun weekIncluded(weeksStr: String, target: Int): Boolean {
        if (weeksStr.isEmpty()) return true
        val cleaned = weeksStr.replace(Regex("[周\\s()]"), "")
        for (part in cleaned.split(",")) {
            val p = part.trim()
            if (p.isEmpty()) continue
            if (p.contains("-")) {
                val (s, e) = p.split("-").mapNotNull { it.toIntOrNull() }.let {
                    if (it.size == 2) Pair(it[0], it[1]) else return@let null
                } ?: continue
                if (target in s..e) return true
            } else {
                if (p.toIntOrNull() == target) return true
            }
        }
        return false
    }
}
