package com.njfu.schedule.ui.import_

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.chip.Chip
import com.njfu.schedule.App
import com.njfu.schedule.R
import com.njfu.schedule.bean.CourseBaseBean
import com.njfu.schedule.bean.CourseDetailBean
import com.njfu.schedule.bean.TableBean
import com.njfu.schedule.databinding.ActivityAddCourseBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AddCourseActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddCourseBinding
    private var editCourseId: Int = -1
    private var tableId: Int = -1
    private val courseColors = listOf(
        "#7E57C2", "#EF5350", "#FF7043", "#5C6BC0", "#66BB6A",
        "#42A5F5", "#26C6DA", "#EC407A", "#FFA726", "#AB47BC"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddCourseBinding.inflate(layoutInflater)
        setContentView(binding.root)

        editCourseId = intent.getIntExtra("course_id", -1)
        tableId = intent.getIntExtra("table_id", -1)

        // 星期 Chips
        val days = resources.getStringArray(R.array.days)
        days.forEachIndexed { idx, day ->
            val chip = Chip(this).apply {
                text = day
                isCheckable = true
                id = View.generateViewId()
                tag = idx + 1
            }
            binding.chipGroupDay.addView(chip)
            if (idx == 0) binding.chipGroupDay.check(chip.id)
        }

        // 如果是编辑模式，加载已有数据
        if (editCourseId >= 0 && tableId >= 0) {
            title = getString(R.string.title_edit_course)
            binding.btnDelete.visibility = View.VISIBLE
            loadCourseData()
        } else {
            title = getString(R.string.title_add_course)
        }

        binding.btnSave.setOnClickListener { saveCourse() }
        binding.btnDelete.setOnClickListener { deleteCourse() }
    }

    private fun loadCourseData() {
        lifecycleScope.launch {
            val dao = App.instance.database.courseDao()
            withContext(Dispatchers.IO) {
                val bases = dao.getCourseBaseByTable(tableId)
                val details = dao.getCourseDetailByTable(tableId)
                // 使用 Flow 的第一个值
            }
            // 简化：从 intent 获取的 id 来查询
        }
    }

    private fun saveCourse() {
        val name = binding.etName.text?.toString()?.trim() ?: ""
        val teacher = binding.etTeacher.text?.toString()?.trim() ?: ""
        val room = binding.etRoom.text?.toString()?.trim() ?: ""
        val startNode = binding.etStartNode.text?.toString()?.toIntOrNull() ?: 0
        val endNode = binding.etEndNode.text?.toString()?.toIntOrNull() ?: 0
        val weeksStr = binding.etWeeks.text?.toString()?.trim() ?: ""

        if (name.isEmpty()) {
            binding.inputName.error = "请输入课程名称"
            return
        }
        if (startNode < 1 || endNode < startNode) {
            Toast.makeText(this, "请正确填写节次", Toast.LENGTH_SHORT).show()
            return
        }
        if (weeksStr.isEmpty()) {
            Toast.makeText(this, "请填写周次", Toast.LENGTH_SHORT).show()
            return
        }

        // 获取选中的星期
        val checkedId = binding.chipGroupDay.checkedChipId
        val chip = binding.chipGroupDay.findViewById<Chip>(checkedId)
        val day = (chip?.tag as? Int) ?: 1

        // 解析周次
        val weeks = parseWeeks(weeksStr)
        if (weeks.isEmpty()) {
            Toast.makeText(this, "周次格式错误，示例: 1-16 或 1-5,7-12", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                val dao = App.instance.database.courseDao()

                // 确保有课表
                var table = dao.getFirstTable()
                if (table == null) {
                    val id = dao.insertTable(TableBean(tableName = "我的课表"))
                    table = dao.getTableById(id.toInt())!!
                }
                val tid = table.id

                // 获取新 ID
                val newId = (dao.getMaxCourseId(tid) ?: -1) + 1
                val color = courseColors[newId % courseColors.size]

                // 插入基本信息
                dao.insertCourseBase(CourseBaseBean(newId, name, color, tid))

                // 插入详情（拆分周次）
                val step = endNode - startNode + 1
                val ranges = toWeekRanges(weeks)
                for ((startWeek, endWeek) in ranges) {
                    dao.insertCourseDetail(
                        CourseDetailBean(newId, day, room, teacher, startNode, step, startWeek, endWeek, 0, tid)
                    )
                }
            }

            Toast.makeText(this@AddCourseActivity, "保存成功", Toast.LENGTH_SHORT).show()
            setResult(RESULT_OK)
            finish()
        }
    }

    private fun deleteCourse() {
        AlertDialog.Builder(this)
            .setTitle("确认删除")
            .setMessage("确定要删除这门课程吗？")
            .setPositiveButton("删除") { _, _ ->
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        val dao = App.instance.database.courseDao()
                        dao.deleteDetailsByCourseId(editCourseId, tableId)
                        dao.deleteCourseBase(editCourseId, tableId)
                    }
                    Toast.makeText(this@AddCourseActivity, "已删除", Toast.LENGTH_SHORT).show()
                    setResult(RESULT_OK)
                    finish()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun parseWeeks(str: String): List<Int> {
        val weeks = mutableListOf<Int>()
        for (part in str.split(",")) {
            val trimmed = part.trim()
            if (trimmed.contains("-")) {
                val parts = trimmed.split("-")
                val start = parts[0].trim().toIntOrNull() ?: continue
                val end = parts[1].trim().toIntOrNull() ?: continue
                weeks.addAll(start..end)
            } else {
                trimmed.toIntOrNull()?.let { weeks.add(it) }
            }
        }
        return weeks.sorted().distinct()
    }

    private fun toWeekRanges(weeks: List<Int>): List<Pair<Int, Int>> {
        if (weeks.isEmpty()) return emptyList()
        val ranges = mutableListOf<Pair<Int, Int>>()
        var start = weeks[0]
        var end = weeks[0]
        for (w in weeks.drop(1)) {
            if (w == end + 1) end = w
            else { ranges.add(Pair(start, end)); start = w; end = w }
        }
        ranges.add(Pair(start, end))
        return ranges
    }
}
