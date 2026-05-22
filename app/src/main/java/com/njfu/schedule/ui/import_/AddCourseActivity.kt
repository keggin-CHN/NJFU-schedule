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
            val base = withContext(Dispatchers.IO) { dao.getCourseBaseById(editCourseId, tableId) }
            val details = withContext(Dispatchers.IO) { dao.getCourseDetailsById(editCourseId, tableId) }

            if (base == null) return@launch

            // 填充课程名
            binding.etName.setText(base.courseName)

            // 一门课可能有多个时间段，取第一个填充基本信息
            // 其他时间段在下方列出
            val firstDetail = details.firstOrNull()
            if (firstDetail != null) {
                binding.etTeacher.setText(firstDetail.teacher ?: "")
                binding.etRoom.setText(firstDetail.room ?: "")

                // 选中星期
                for (i in 0 until binding.chipGroupDay.childCount) {
                    val chip = binding.chipGroupDay.getChildAt(i) as? com.google.android.material.chip.Chip
                    if (chip?.tag == firstDetail.day) {
                        binding.chipGroupDay.check(chip.id)
                        break
                    }
                }

                // 填充节次
                binding.etStartNode.setText(firstDetail.startNode.toString())
                binding.etEndNode.setText((firstDetail.startNode + firstDetail.step - 1).toString())

                // 只显示当前时间段的周次
                val sameSlotDetails = details.filter { it.day == firstDetail.day && it.startNode == firstDetail.startNode }
                val weeksStr = sameSlotDetails.joinToString(",") { d ->
                    if (d.startWeek == d.endWeek) "${d.startWeek}"
                    else "${d.startWeek}-${d.endWeek}"
                }
                binding.etWeeks.setText(weeksStr)
            }

            // 如果有多个不同时间段，在底部提示
            val uniqueSlots = details.map { Pair(it.day, it.startNode) }.distinct()
            if (uniqueSlots.size > 1) {
                val dayNames = arrayOf("周一","周二","周三","周四","周五","周六","周日")
                val slotsInfo = uniqueSlots.joinToString("\n") { (day, node) ->
                    val slotDetails = details.filter { it.day == day && it.startNode == node }
                    val weeks = slotDetails.joinToString(",") { d ->
                        if (d.startWeek == d.endWeek) "${d.startWeek}" else "${d.startWeek}-${d.endWeek}"
                    }
                    val endNode = node + (slotDetails.firstOrNull()?.step ?: 2) - 1
                    "  ${dayNames[day-1]} 第${node}-${endNode}节  第${weeks}周"
                }
                // 显示提示
                binding.btnDelete.visibility = View.VISIBLE
                val tipView = android.widget.TextView(this@AddCourseActivity).apply {
                    text = "该课程有 ${uniqueSlots.size} 个时间段：\n$slotsInfo\n\n当前编辑的是第一个时间段"
                    setTextColor(android.graphics.Color.parseColor("#888888"))
                    textSize = 12f
                    setPadding(0, dpToPx(16), 0, 0)
                }
                (binding.btnSave.parent as? android.view.ViewGroup)?.addView(tipView,
                    (binding.btnSave.parent as android.view.ViewGroup).indexOfChild(binding.btnSave))
            }
        }
    }

    private fun dpToPx(dp: Int): Int {
        return android.util.TypedValue.applyDimension(
            android.util.TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), resources.displayMetrics
        ).toInt()
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
