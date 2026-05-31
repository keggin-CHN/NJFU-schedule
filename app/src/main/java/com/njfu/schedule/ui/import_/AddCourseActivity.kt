package com.njfu.schedule.ui.import_

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
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
import com.njfu.schedule.bean.TimeNode
import com.njfu.schedule.databinding.ActivityAddCourseBinding
import com.njfu.schedule.widget.NextCourseWidget
import com.njfu.schedule.widget.TodayCourseWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.google.android.material.datepicker.MaterialDatePicker
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AddCourseActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddCourseBinding
    private var editCourseId: Int = -1
    private var tableId: Int = -1
    private var originalColor: String = ""
    private var selectedColor: String = ""
    private val courseColors = listOf(
        "#7E57C2", "#EF5350", "#FF7043", "#5C6BC0", "#66BB6A",
        "#42A5F5", "#26C6DA", "#EC407A", "#FFA726", "#AB47BC"
    )

    private data class TimeSlot(var day: Int, var startNode: Int, var endNode: Int, var weeks: String, var room: String, var teacher: String, var customStartTime: String = "", var customEndTime: String = "")
    private var timeSlots = mutableListOf<TimeSlot>()
    private var currentSlotIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddCourseBinding.inflate(layoutInflater)
        setContentView(binding.root)

        editCourseId = intent.getIntExtra("course_id", -1)
        tableId = intent.getIntExtra("table_id", -1)

        val days = resources.getStringArray(R.array.days)
        days.forEachIndexed { idx, day ->
            val chip = Chip(this, null, com.google.android.material.R.attr.chipStyle).apply {
                text = day
                isCheckable = true
                id = View.generateViewId()
                tag = idx + 1
                setTextColor(resources.getColor(R.color.text_primary, theme))
                chipStrokeColor = android.content.res.ColorStateList.valueOf(resources.getColor(R.color.field_stroke, theme))
                chipStrokeWidth = dpToPx(1).toFloat()
            }
            binding.chipGroupDay.addView(chip)
            if (idx == 0) binding.chipGroupDay.check(chip.id)
        }

        setupColorChips()
        setupModeSwitches()

        if (editCourseId >= 0 && tableId >= 0) {
            title = getString(R.string.title_edit_course)
            binding.btnDelete.visibility = View.VISIBLE
            loadCourseData()
        } else {
            title = getString(R.string.title_add_course)

            val prefillDay = intent.getIntExtra("prefill_day", -1)
            val prefillStartNode = intent.getIntExtra("prefill_start_node", -1)
            if (prefillDay > 0 && prefillStartNode > 0) {
                applyPrefill(prefillDay, prefillStartNode)
            }
            if (timeSlots.isEmpty()) {
                timeSlots.add(TimeSlot(1, 1, 2, "1-20", "", ""))
            }
            showSlotSelector()
            loadSlot(0)
        }

        setupPickers()

        binding.btnSave.setOnClickListener { saveCourse() }
        binding.btnDelete.setOnClickListener { deleteCourse() }
        binding.btnAddSlot.setOnClickListener {
            saveCurrentSlot()
            val lastSlot = timeSlots.lastOrNull()
            val newDay = lastSlot?.day ?: 1
            val newStart = lastSlot?.startNode ?: 1
            val newEnd = lastSlot?.endNode ?: 2
            timeSlots.add(TimeSlot(newDay, newStart, newEnd, "1-20", "", ""))
            showSlotSelector()
            loadSlot(timeSlots.size - 1)
        }
    }

    private fun setupPickers() {
        binding.etCustomStartTime.isFocusable = false
        binding.etCustomStartTime.isClickable = true
        binding.etCustomStartTime.setOnClickListener {
            showTimePicker("开始时间") { time -> binding.etCustomStartTime.setText(time) }
        }

        binding.etCustomEndTime.isFocusable = false
        binding.etCustomEndTime.isClickable = true
        binding.etCustomEndTime.setOnClickListener {
            showTimePicker("结束时间") { time -> binding.etCustomEndTime.setText(time) }
        }

        binding.etDates.isFocusable = false
        binding.etDates.isClickable = true
        binding.etDates.setOnClickListener {
            SlideDatePickerDialog(this) { start, end ->
                binding.etDates.setText("$start~$end")
            }.show()
        }
    }

    private fun showTimePicker(title: String, onTimeSelected: (String) -> Unit) {
        val picker = MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_24H)
            .setHour(8)
            .setMinute(0)
            .setTitleText(title)
            .build()
        picker.addOnPositiveButtonClickListener {
            val h = picker.hour.toString().padStart(2, '0')
            val m = picker.minute.toString().padStart(2, '0')
            onTimeSelected("$h:$m")
        }
        picker.show(supportFragmentManager, "TIME_PICKER")
    }

    private fun setupColorChips() {
        binding.chipGroupColor.removeAllViews()
        courseColors.forEachIndexed { idx, colorHex ->
            val chip = Chip(this).apply {
                id = View.generateViewId()
                tag = colorHex
                isCheckable = true
                isCheckedIconVisible = true
                checkedIconTint = android.content.res.ColorStateList.valueOf(Color.WHITE)
                text = "" 
                minWidth = dpToPx(34)
                chipMinHeight = dpToPx(30).toFloat()
                chipBackgroundColor = android.content.res.ColorStateList.valueOf(parseColorSafe(colorHex))
                setTextColor(Color.WHITE)
                chipStrokeWidth = 0f 
            }
            binding.chipGroupColor.addView(chip)
            if (idx == 0) {
                binding.chipGroupColor.check(chip.id)
                selectedColor = colorHex
            }
        }
        binding.chipGroupColor.setOnCheckedStateChangeListener { group, checkedIds ->
            val chip = checkedIds.firstOrNull()?.let { group.findViewById<Chip>(it) }
            selectedColor = chip?.tag as? String ?: courseColors.first()
        }
    }

    private fun setupModeSwitches() {
        binding.chipGroupTimeMode.setOnCheckedStateChangeListener { _, checkedIds ->
            val useCustomTime = checkedIds.firstOrNull() == R.id.chip_time_custom
            binding.layoutNodeTime.visibility = if (useCustomTime) View.GONE else View.VISIBLE
            binding.layoutCustomTime.visibility = if (useCustomTime) View.VISIBLE else View.GONE
        }
        binding.chipGroupRangeMode.setOnCheckedStateChangeListener { _, checkedIds ->
            val useDate = checkedIds.firstOrNull() == R.id.chip_range_date
            binding.inputWeeks.visibility = if (useDate) View.GONE else View.VISIBLE
            binding.inputDates.visibility = if (useDate) View.VISIBLE else View.GONE
        }
    }

    private fun parseColorSafe(colorHex: String): Int {
        return try { Color.parseColor(colorHex) } catch (_: Exception) { Color.parseColor("#5C6BC0") }
    }

    private fun applyPrefill(day: Int, startNode: Int) {

        for (i in 0 until binding.chipGroupDay.childCount) {
            val chip = binding.chipGroupDay.getChildAt(i) as? Chip ?: continue
            if (chip.tag == day) {
                binding.chipGroupDay.check(chip.id)
                break
            }
        }

        if (startNode >= 1) {
            binding.etStartNode.setText(startNode.toString())
            binding.etEndNode.setText(startNode.toString())
        }

        if (timeSlots.isEmpty()) {
            timeSlots.add(TimeSlot(day, startNode, startNode, "1-20", "", ""))
        }
    }

    private fun loadCourseData() {
        lifecycleScope.launch {
            val dao = App.instance.database.courseDao()
            val base = withContext(Dispatchers.IO) { dao.getCourseBaseById(editCourseId, tableId) }
            val details = withContext(Dispatchers.IO) { dao.getCourseDetailsById(editCourseId, tableId) }

            if (base == null) return@launch

            originalColor = base.color
            selectedColor = base.color.ifEmpty { courseColors.first() }
            binding.etName.setText(base.courseName)
            checkColorChip(selectedColor)

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
                    teacher = first.teacher ?: "",
                    customStartTime = first.customStartTime ?: "",
                    customEndTime = first.customEndTime ?: ""
                ))
            }

            if (timeSlots.isEmpty()) return@launch

            if (timeSlots.size > 1) {
                showSlotSelector()
            }

            loadSlot(0)
        }
    }

    private fun checkColorChip(colorHex: String) {
        for (i in 0 until binding.chipGroupColor.childCount) {
            val chip = binding.chipGroupColor.getChildAt(i) as? Chip ?: continue
            if ((chip.tag as? String).equals(colorHex, ignoreCase = true)) {
                binding.chipGroupColor.check(chip.id)
                return
            }
        }
    }

    private fun showSlotSelector() {
        val container = binding.root.findViewById<LinearLayout>(R.id.slot_selector_container)
            ?: return 
        container.visibility = View.VISIBLE
        container.removeAllViews()

        val dayNames = arrayOf("周一","周二","周三","周四","周五","周六","周日")

        val title = TextView(this).apply {
            text = "时间段（${timeSlots.size}个，点击切换）"
            textSize = 13f
            setTextColor(resources.getColor(R.color.text_secondary, theme))
            setPadding(0, dpToPx(8), 0, dpToPx(4))
        }
        container.addView(title)

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
        binding.etCustomStartTime.setText(slot.customStartTime)
        binding.etCustomEndTime.setText(slot.customEndTime)
        val useCustomTime = slot.customStartTime.isNotEmpty() && slot.customEndTime.isNotEmpty()
        binding.chipGroupTimeMode.check(if (useCustomTime) R.id.chip_time_custom else R.id.chip_time_node)

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
        val useCustomTime = binding.chipGroupTimeMode.checkedChipId == R.id.chip_time_custom
        val customStart = if (useCustomTime) binding.etCustomStartTime.text?.toString()?.trim() ?: "" else ""
        val customEnd = if (useCustomTime) binding.etCustomEndTime.text?.toString()?.trim() ?: "" else ""

        val inferredStart = if (useCustomTime && customStart.isNotEmpty()) inferNodeByTime(customStart) else binding.etStartNode.text?.toString()?.toIntOrNull() ?: slot.startNode
        val inferredEnd = if (useCustomTime && customEnd.isNotEmpty()) inferNodeByTime(customEnd).coerceAtLeast(inferredStart) else binding.etEndNode.text?.toString()?.toIntOrNull() ?: slot.endNode

        slot.startNode = inferredStart
        slot.endNode = inferredEnd
        val useDateRange = binding.chipGroupRangeMode.checkedChipId == R.id.chip_range_date
        slot.weeks = if (useDateRange) {
            val datesStr = binding.etDates.text?.toString()?.trim() ?: ""
            val weeks = parseWeeksFromDateRange(datesStr)
            weeks.joinToString(",")
        } else {
            binding.etWeeks.text?.toString()?.trim() ?: slot.weeks
        }
        slot.customStartTime = customStart
        slot.customEndTime = customEnd

        val checkedId = binding.chipGroupDay.checkedChipId
        val chip = binding.chipGroupDay.findViewById<Chip>(checkedId)
        slot.day = (chip?.tag as? Int) ?: slot.day
    }

    private fun saveCourse() {

        saveCurrentSlot()

        val name = binding.etName.text?.toString()?.trim() ?: ""

        if (name.isEmpty()) {
            binding.inputName.error = "请输入课程名称"
            return
        }

        if (timeSlots.isEmpty()) {
            Toast.makeText(this, "请至少添加一个时间段", Toast.LENGTH_SHORT).show()
            return
        }

        for ((idx, slot) in timeSlots.withIndex()) {
            if (slot.customStartTime.isEmpty() && slot.customEndTime.isEmpty()) {
                if (slot.startNode < 1 || slot.endNode < slot.startNode) {
                    Toast.makeText(this, "第${idx + 1}个时间段：请选择有效节次", Toast.LENGTH_SHORT).show()
                    return
                }
            }
            if (slot.weeks.isEmpty()) {
                Toast.makeText(this, "第${idx + 1}个时间段：请输入周次范围", Toast.LENGTH_SHORT).show()
                return
            }
        }

        val isEdit = editCourseId >= 0 && tableId >= 0

        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                val dao = App.instance.database.courseDao()
                var tid = tableId

                if (isEdit) {
                    dao.deleteDetailsByCourseId(editCourseId, tid)
                    dao.insertCourseBase(CourseBaseBean(editCourseId, name,
                        selectedColor.ifEmpty { originalColor.ifEmpty { courseColors[editCourseId % courseColors.size] } }, tid))
                } else {
                    var table = dao.getFirstTable()
                    if (table == null) {
                        val id = dao.insertTable(TableBean(tableName = "我的课表"))
                        table = dao.getTableById(id.toInt())!!
                    }
                    tid = table.id
                    val newId = (dao.getMaxCourseId(tid) ?: -1) + 1
                    val color = selectedColor.ifEmpty { courseColors[newId % courseColors.size] }
                    dao.insertCourseBase(CourseBaseBean(newId, name, color, tid))
                    editCourseId = newId
                    tableId = tid
                }

                for (slot in timeSlots) {
                    val weeks = parseWeeks(slot.weeks)
                    val step = slot.endNode - slot.startNode + 1
                    val ranges = toWeekRanges(weeks)
                    val cStart = slot.customStartTime.ifEmpty { null }
                    val cEnd = slot.customEndTime.ifEmpty { null }
                    for ((startWeek, endWeek) in ranges) {
                        dao.insertCourseDetail(CourseDetailBean(
                            editCourseId, slot.day, slot.room, slot.teacher,
                            slot.startNode, step, startWeek, endWeek, 0, tid, cStart, cEnd
                        ))
                    }
                }
            }
            Toast.makeText(this@AddCourseActivity, "保存成功", Toast.LENGTH_SHORT).show()
            refreshWidgets()
            setResult(RESULT_OK)
            finish()
        }
    }

    private fun deleteCourse() {
        val deleteView = LayoutInflater.from(this).inflate(R.layout.dialog_confirm_card, null)
        deleteView.findViewById<TextView>(R.id.tv_title).text = "确认删除"
        deleteView.findViewById<TextView>(R.id.tv_message).text = "确定要删除这门课程吗？"
        val dialog = AlertDialog.Builder(this, com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog)
            .setView(deleteView)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        deleteView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_cancel).setOnClickListener {
            dialog.dismiss()
        }
        deleteView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_confirm).apply {
            text = "删除"
            setOnClickListener {
                dialog.dismiss()
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        val dao = App.instance.database.courseDao()
                        dao.deleteDetailsByCourseId(editCourseId, tableId)
                        dao.deleteCourseBase(editCourseId, tableId)
                    }
                    Toast.makeText(this@AddCourseActivity, "已删除", Toast.LENGTH_SHORT).show()
                    refreshWidgets()
                    setResult(RESULT_OK)
                    finish()
                }
            }
        }
        dialog.show()
    }

    private fun refreshWidgets() {
        lifecycleScope.launch {
            TodayCourseWidget.refreshAll(this@AddCourseActivity)
            NextCourseWidget.refreshAll(this@AddCourseActivity)
        }
    }

    private fun inferNodeByTime(time: String): Int {
        TimeNode.load(this)
        val target = parseMinutes(time) ?: return 1
        return TimeNode.times.minByOrNull { nodeTime ->
            kotlin.math.abs((parseMinutes(nodeTime.start) ?: 0) - target)
        }?.node ?: 1
    }

    private fun parseMinutes(time: String): Int? {
        val parts = time.split(":")
        if (parts.size != 2) return null
        val hour = parts[0].toIntOrNull() ?: return null
        val minute = parts[1].toIntOrNull() ?: return null
        return hour * 60 + minute
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

    private fun parseWeeksFromDateRange(str: String): List<Int> {
        val currentStartDate = runBlockingGetStartDate() ?: "2026-02-24"
        val parts = str.replace("至", "~").replace("—", "~").split("~")
        if (parts.size < 2) return emptyList()
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
            val semester = sdf.parse(currentStartDate) ?: return emptyList()
            val start = sdf.parse(parts[0].trim()) ?: return emptyList()
            val end = sdf.parse(parts[1].trim()) ?: return emptyList()
            val startDay = ((start.time - semester.time) / (1000L * 3600L * 24L)).toInt()
            val endDay = ((end.time - semester.time) / (1000L * 3600L * 24L)).toInt()
            val startWeek = (startDay / 7 + 1).coerceAtLeast(1)
            val endWeek = ((endDay / 7) + 1).coerceAtLeast(startWeek)
            (startWeek..endWeek).toList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun runBlockingGetStartDate(): String? {
        return try {
            kotlinx.coroutines.runBlocking(Dispatchers.IO) {
                App.instance.database.courseDao().getFirstTable()?.startDate
            }
        } catch (_: Exception) {
            null
        }
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
