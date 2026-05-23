package com.njfu.schedule.ui.schedule

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.njfu.schedule.databinding.ActivityEmptyRoomBinding
import com.njfu.schedule.njfu.NjfuImporter
import com.njfu.schedule.ui.schedule.SimpleTextAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class EmptyRoomActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEmptyRoomBinding
    private val importer = NjfuImporter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEmptyRoomBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }

        setupSpinners()

        binding.btnSearch.setOnClickListener {
            performSearch()
        }
        
        binding.rvResults.layoutManager = LinearLayoutManager(this)
        
        // Auto-login check
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    importer.prepareSession()
                }
            } catch (e: Exception) {
                // Ignore, will prompt on search
            }
        }
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

    private fun performSearch() {
        val xnxqh = listOf("2025-2026-2", "2025-2026-1")[binding.spinnerXnxqh.selectedItemPosition]
        val xqid = listOf("", "1", "2", "3")[binding.spinnerXqid.selectedItemPosition]
        val zc = listOf("") + (1..30).map { "$it" }
        val xq = listOf("", "1", "2", "3", "4", "5", "6", "7")
        val jc = listOf("") + (1..15).map { "$it" }

        val selZc = zc[binding.spinnerZc.selectedItemPosition]
        val selXq = xq[binding.spinnerXq.selectedItemPosition]
        val selJc1 = jc[binding.spinnerJc1.selectedItemPosition]
        val selJc2 = jc[binding.spinnerJc2.selectedItemPosition]

        if (selZc.isEmpty() || selXq.isEmpty() || selJc1.isEmpty() || selJc2.isEmpty()) {
            Toast.makeText(this, "请选择周次、星期和节次", Toast.LENGTH_SHORT).show()
            return
        }

        binding.progress.visibility = View.VISIBLE
        binding.rvResults.visibility = View.GONE
        binding.tvEmpty.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val rooms = withContext(Dispatchers.IO) {
                    importer.fetchEmptyRooms(xnxqh, xqid, selZc, selXq, selJc1, selJc2)
                }
                binding.progress.visibility = View.GONE
                if (rooms.isEmpty()) {
                    binding.tvEmpty.visibility = View.VISIBLE
                    binding.tvEmpty.text = "没有找到空闲教室"
                } else {
                    binding.rvResults.visibility = View.VISIBLE
                    binding.rvResults.adapter = SimpleTextAdapter(rooms) { }
                }
            } catch (e: Exception) {
                binding.progress.visibility = View.GONE
                Toast.makeText(this@EmptyRoomActivity, "查询失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
