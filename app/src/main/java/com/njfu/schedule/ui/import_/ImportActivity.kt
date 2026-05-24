package com.njfu.schedule.ui.import_

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.njfu.schedule.App
import com.njfu.schedule.R
import com.njfu.schedule.bean.CourseBaseBean
import com.njfu.schedule.bean.CourseDetailBean
import com.njfu.schedule.bean.TableBean
import com.njfu.schedule.databinding.ActivityImportBinding
import com.njfu.schedule.njfu.NjfuImporter
import com.njfu.schedule.utils.SecurePrefs
import com.njfu.schedule.widget.NextCourseWidget
import com.njfu.schedule.widget.TodayCourseWidget
import com.njfu.schedule.worker.GlobalCacheScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ImportActivity : AppCompatActivity() {

    private lateinit var binding: ActivityImportBinding

    private val courseColors = listOf(
        "#7E57C2", "#EF5350", "#FF7043", "#5C6BC0", "#66BB6A",
        "#42A5F5", "#26C6DA", "#EC407A", "#FFA726", "#AB47BC",
        "#26A69A", "#8D6E63", "#78909C", "#9CCC65", "#FFCA28"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityImportBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val prefs = SecurePrefs.get(this)
        val savedId = prefs.getString("student_id", "") ?: ""
        val savedPwd = prefs.getString("password", "") ?: ""
        if (savedId.isNotEmpty()) binding.etId.setText(savedId)
        if (savedPwd.isNotEmpty()) binding.etPwd.setText(savedPwd)

        binding.btnImport.setOnClickListener { doImport() }
    }

    private fun doImport() {
        val studentId = binding.etId.text?.toString()?.trim() ?: ""
        val password = binding.etPwd.text?.toString()?.trim() ?: ""

        if (studentId.isEmpty()) {
            binding.inputId.error = "请输入学号"
            return
        }
        if (password.isEmpty()) {
            binding.inputPwd.error = "请输入密码"
            return
        }

        binding.inputId.error = null
        binding.inputPwd.error = null
        setLoading(true)

        lifecycleScope.launch {
            try {
                val importer = NjfuImporter()

                updateStep("正在连接教务系统...")
                withContext(Dispatchers.IO) {
                    importer.prepareSession()
                }

                updateStep("正在访问统一认证...")
                val loginParams = withContext(Dispatchers.IO) {
                    importer.fetchLoginPage()
                }

                updateStep("正在验证账号密码...")
                withContext(Dispatchers.IO) {
                    importer.doLogin(studentId, password, loginParams)
                }

                updateStep("登录成功，正在获取课表...")
                val result = withContext(Dispatchers.IO) {
                    importer.fetchAndParseSchedule()
                }

                if (result.courses.isEmpty()) {
                    setLoading(false)
                    binding.tvStatus.text = "未获取到课程数据，可能本学期尚未排课"
                    return@launch
                }

                updateStep("正在保存 ${result.courses.map { it.name }.distinct().size} 门课程...")

                SecurePrefs.get(this@ImportActivity).edit()
                    .putString("student_id", studentId)
                    .putString("password", password)
                    .putString("remarks", result.remarks.joinToString("\n"))
                    .apply()

                withContext(Dispatchers.IO) {
                    saveCourses(result, studentId)
                }

                setLoading(false)
                val courseNames = result.courses.map { it.name }.distinct()
                val remarksText = if (result.remarks.isNotEmpty()) {
                    "\n\n备注：\n${result.remarks.joinToString("\n")}"
                } else ""
                val msg = if (result.studentName.isNotEmpty()) {
                    "✓ ${result.studentName}，导入成功！\n共 ${courseNames.size} 门课程，${result.courses.size} 条时间安排$remarksText"
                } else {
                    "✓ 导入成功！共 ${courseNames.size} 门课程$remarksText"
                }
                binding.tvStatus.text = msg
                Toast.makeText(this@ImportActivity, "导入成功！", Toast.LENGTH_SHORT).show()
                TodayCourseWidget.refreshAll(this@ImportActivity)
                NextCourseWidget.refreshAll(this@ImportActivity)

                GlobalCacheScheduler.scheduleOneShot(this@ImportActivity)
                GlobalCacheScheduler.schedulePeriodic(this@ImportActivity)

                binding.root.postDelayed({
                    setResult(RESULT_OK)
                    finish()
                }, 2000)

            } catch (e: Exception) {
                setLoading(false)
                binding.tvStatus.text = "✗ ${e.message}"
            }
        }
    }

    private suspend fun saveCourses(
        result: NjfuImporter.ImportResult,
        studentId: String
    ) {
        val dao = App.instance.database.courseDao()
        val courses = result.courses
        val studentName = result.studentName

        var table = dao.getFirstTable()
        if (table == null) {
            val id = dao.insertTable(TableBean(
                tableName = "南林课表",
                studentName = studentName,
                studentId = studentId,
                startDate = result.semesterStartDate
            ))
            table = dao.getTableById(id.toInt())!!
        } else {
            table.studentName = studentName
            table.studentId = studentId
            table.startDate = result.semesterStartDate
            dao.updateTable(table)
        }
        val tableId = table.id

        dao.deleteCoursesByTable(tableId)
        dao.deleteDetailsByTable(tableId)

        val courseNames = courses.map { it.name }.distinct()
        val nameToId = courseNames.mapIndexed { idx, name -> name to idx }.toMap()

        for ((name, id) in nameToId) {
            val color = courseColors[id % courseColors.size]
            dao.insertCourseBase(CourseBaseBean(id, name, color, tableId))
        }

        for (course in courses) {
            val id = nameToId[course.name]!!
            val step = course.endNode - course.startNode + 1
            val weekRanges = toWeekRanges(course.weeks)

            for ((startWeek, endWeek) in weekRanges) {
                dao.insertCourseDetail(
                    CourseDetailBean(id, course.day, course.room, course.teacher,
                        course.startNode, step, startWeek, endWeek, 0, tableId)
                )
            }
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

    private fun setLoading(loading: Boolean) {
        binding.progress.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnImport.isEnabled = !loading
        binding.btnImport.text = if (loading) getString(R.string.importing) else getString(R.string.btn_import)
        if (loading) binding.tvStatus.text = ""
    }

    private fun updateStep(text: String) {
        binding.tvStatus.text = text
    }
}
