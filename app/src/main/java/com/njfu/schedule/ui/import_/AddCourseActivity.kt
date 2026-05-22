package com.njfu.schedule.ui.import_

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
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

    // 多时间段支持
    private data class TimeSlot(var day: Int, var startNode: Int, var endNode: Int, var weeks: String, var room: String, var teacher: String)
    private var timeSlots = mutableListOf<TimeSlot>()
    private var currentSlotIndex = 0

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

            binding.etName.setText(base.courseName)

            // 按 (day, startNode) 分组为不同时间段
            val grouped = details.groupBy { Pair(it.day, it.startNode) }
            timeSlots.clear()
            for ((key, slotDetails) in grouped) {
                val first = slotDetails.first()
                val weeksStr = slotDetails.joinToString(",") { d ->
                    if (d.startWeek == d.endWeek) "${d.startWeek}" else "${d.startWeek}-${d.endWeek}"
                }
                timeSlots.add(TimeSlot(
                    day = key.first,
                    startNode = key.second,
                    endNode = key.second + first.step - 1,
                    weeks = weeksStr,
                    room = first.room ?: "",
                    teacher = first.teacher ?: ""
                ))
            }

            if (timeSlots.isEmpty()) return@launch

            // 显示时间段切换器
            if (timeSlots.size > 1) {
                showSlotSelector()
            }

            // 加载第一个时间段
            loadSlot(0)
        }
    }

    private fun showSlotSelector() {
        val container = binding.root.findViewById<LinearLayout>(R.id.slot_selector_container)
            ?: return // 如果布局中没有这个容器，动态添加
        container.visibility = View.VISIBLE
        container.removeAllViews()

        val dayNames = arrayOf("周一","周二","周三","周四","周五","周六","周日")

        // 添加标题
        val title = TextView(this).apply {
            text = "时间段（${timeSlots.size}个，点击切换）"
            textSize = 13f
            setTextColor(android.graphics.Color.parseColor("#888888"))
            setPadding(0, dpToPx(8), 0, dpToPx(4))
        }
        container.addView(title)

        // 添加每个时间段按钮
        timeSlots.forEachIndexed { idx, slot ->
            val dayName = dayNames.getOrElse(slot.day - 1) { "" }
            val btn = com.google.android.material.button.MaterialButton(this, null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = "$dayName 第${slot.startNode}-${slot.endNode}节"
                textSize = 12f
                isAllCaps = false
                val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dpToPx(36))
                lp.setMargins(0, 0, dpToPx(8), 0)
                layoutParams = lp
                setOnClickListener {
                    saveCurrentSlot()
                    currentSlotIndex = idx
                    loadSlot(idx)
                    highlightSlot(container, idx)
                }
            }
            container.addView(btn)
        }

        highlightSlot(container, 0)
    }

    private fun highlightSlot(container: LinearLayout, activeIdx: Int) {
        // 从 index 1 开始（跳过标题）
        for (i in 1 until container.childCount) {
            val child = container.getChildAt(i)
            if (child is com.google.android.material.button.MaterialButton) {
                val slotIdx = i - 1
                child.alpha = if (slotIdx == activeIdx) 1f else 0.5f
            }
        }
    }

    private fun loadSlot(index: Int) {
        if (index >= timeSlots.size) return
        val slot = timeSlots[index]
        currentSlotIndex = index

        binding.etTeacher.setText(slot.teacher)
        binding.etRoom.setText(slot.room)
        binding.etStartNode.setText(slot.startNode.toString())
        binding.etEndNode.setText(slot.endNode.toString())
        binding.etWeeks.setText(slot.weeks)

        // 选中星期
        for (i in 0 until binding.chipGroupDay.childCount) {
            val chip = binding.chipGroupDay.getChildAt(i) as? Chip
            if (chip?.tag == slot.day) {
                binding.chipGroupDay.check(chip.id)
                break
            }
        }
    }

    private fun saveCurrentSlot() {
        if (currentSlotIndex >= timeSlots.size) return
        val slot = timeSlots[currentSlotIndex]
        slot.teacher = binding.etTeacher.text?.toString()?.trim() ?: ""
        slot.room = binding.etRoom.text?.toString()?.trim() ?: ""
        slot.startNode = binding.etStartNode.text?.toString()?.toIntOrNull() ?: slot.startNode
        slot.endNode = binding.etEndNode.text?.toString()?.toIntOrNull() ?: slot.endNode
        slot.weeks = binding.etWeeks.text?.toString()?.trim() ?: slot.weeks

        val checkedId = binding.chipGroupDay.checkedChipId
        val chip = binding.chipGroupDay.findViewById<Chip>(checkedId)
        slot.day = (chip?.tag as? Int) ?: slot.day
    }

    private fun saveCourse() {
        // 先保存当前正在编辑的时间段
        saveCurrentSlot()

        val name = binding.etName.text?.toString()?.trim() ?: ""

        if (name.isEmpty()) {
            binding.inputName.error = "请输入课程名称"
            return
        }

        // 编辑模式：保存所有时间段
        if (editCourseId >= 0 && timeSlots.isNotEmpty()) {
            lifecycleScope.launch {
                withContext(Dispatchers.IO) {
                    val dao = App.instance.database.courseDao()
                    // 删除旧的详情
                    dao.deleteDetailsByCourseId(editCourseId, tableId)
                    // 更新课程名
                    dao.insertCourseBase(CourseBaseBean(editCourseId, name,
                        courseColors[editCourseId % courseColors.size], tableId))
                    // 插入所有时间段
                    for (slot in timeSlots) {
                        val weeks = parseWeeks(slot.weeks)
                        val step = slot.endNode - slot.startNode + 1
                        val ranges = toWeekRanges(weeks)
                        for ((startWeek, endWeek) in ranges) {
                            dao.insertCourseDetail(CourseDetailBean(
                                editCourseId, slot.day, slot.room, slot.teacher,
                                slot.startNode, step, startWeek, endWeek, 0, tableId
                            ))
                        }
                    }
                }
                Toast.makeText(this@AddCourseActivity, "保存成功", Toast.LENGTH_SHORT).show()
                setResult(RESULT_OK)
                finish()
            }
            return
        }

        // 新增模式
        val teacher = binding.etTeacher.text?.toString()?.trim() ?: ""
        val room = binding.etRoom.text?.toString()?.trim() ?: ""
        val startNode = binding.etStartNode.text?.toString()?.toIntOrNull() ?: 0
        val endNode = binding.etEndNode.text?.toString()?.toIntOrNull() ?: 0
        val weeksStr = binding.etWeeks.text?.toString()?.trim() ?: ""

        if (startNode < 1 || endNode < startNode) {
            Toast.makeText(this, "请正确填写节次", Toast.LENGTH_SHORT).show()
            return
        }
        if (weeksStr.isEmpty()) {
            Toast.makeText(this, "请填写周次", Toast.LENGTH_SHORT).show()
            return
        }

        val checkedId = binding.chipGroupDay.checkedChipId
        val chip = binding.chipGroupDay.findViewById<Chip>(checkedId)
        val day = (chip?.tag as? Int) ?: 1
        val weeks = parseWeeks(weeksStr)
        if (weeks.isEmpty()) {
            Toast.makeText(this, "周次格式错误", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                val dao = App.instance.database.courseDao()
                var table = dao.getFirstTable()
                if (table == null) {
                    val id = dao.insertTable(TableBean(tableName = "我的课表"))
                    table = dao.getTableById(id.toInt())!!
                }
                val tid = table.id
                val newId = (dao.getMaxCourseId(tid) ?: -1) + 1
                val color = courseColors[newId % courseColors.size]
                dao.insertCourseBase(CourseBaseBean(newId, name, color, tid))
                val step = endNode - startNode + 1
                val ranges = toWeekRanges(weeks)
                for ((startWeek, endWeek) in ranges) {
                    dao.insertCourseDetail(CourseDetailBean(newId, day, room, teacher, startNode, step, startWeek, endWeek, 0, tid))
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

    private fun dpToPx(dp: Int): Int {
        return android.util.TypedValue.applyDimension(
            android.util.TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), resources.displayMetrics
        ).toInt()
    }
}
