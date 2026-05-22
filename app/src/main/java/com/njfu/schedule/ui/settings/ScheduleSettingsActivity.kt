package com.njfu.schedule.ui.settings

import android.app.DatePickerDialog
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.njfu.schedule.App
import com.njfu.schedule.bean.TableBean
import com.njfu.schedule.databinding.ActivityScheduleSettingsBinding
import com.njfu.schedule.utils.WeekUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

class ScheduleSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityScheduleSettingsBinding
    private var table: TableBean? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScheduleSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }

        lifecycleScope.launch {
            table = withContext(Dispatchers.IO) { App.instance.database.courseDao().getFirstTable() }
            table?.let { fillData(it) }
        }

        binding.tvStartDate.setOnClickListener { showDatePicker() }
        binding.btnSave.setOnClickListener { save() }
    }

    private fun fillData(t: TableBean) {
        binding.etTableName.setText(t.tableName)
        binding.tvStartDate.text = "${t.startDate}  星期一"
        binding.tvCurrentWeek.text = "第 ${WeekUtils.getCurrentWeek(t.startDate)} 周"
        binding.etNodes.setText(t.nodes.toString())
        binding.etMaxWeek.setText(t.maxWeek.toString())
        binding.switchSat.isChecked = t.showSat
        binding.switchSun.isChecked = t.showSun
    }

    private fun showDatePicker() {
        val cal = Calendar.getInstance()
        DatePickerDialog(this, { _, year, month, day ->
            val date = String.format("%04d-%02d-%02d", year, month + 1, day)
            binding.tvStartDate.text = "$date  星期一"
            table?.startDate = date
            binding.tvCurrentWeek.text = "第 ${WeekUtils.getCurrentWeek(date)} 周"
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun save() {
        val t = table ?: return
        t.tableName = binding.etTableName.text?.toString()?.trim() ?: "南林课表"
        t.nodes = binding.etNodes.text?.toString()?.toIntOrNull() ?: 11
        t.maxWeek = binding.etMaxWeek.text?.toString()?.toIntOrNull() ?: 20
        t.showSat = binding.switchSat.isChecked
        t.showSun = binding.switchSun.isChecked

        lifecycleScope.launch {
            withContext(Dispatchers.IO) { App.instance.database.courseDao().updateTable(t) }
            Toast.makeText(this@ScheduleSettingsActivity, "设置已保存", Toast.LENGTH_SHORT).show()
            setResult(RESULT_OK)
            finish()
        }
    }
}
