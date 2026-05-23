package com.njfu.schedule.ui.schedule

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.njfu.schedule.databinding.ActivityGlobalScheduleBinding
import com.njfu.schedule.njfu.NjfuImporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GlobalScheduleActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGlobalScheduleBinding
    private val importer = NjfuImporter()

    // Current query type
    private var currentType = "jg0101"

    // Adapters
    private val entityAdapter = EntityAdapter { name, id -> onEntitySelected(name, id) }
    private val courseAdapter = GlobalCourseAdapter()

    // State
    private var isShowingResults = false
    private var sessionReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGlobalScheduleBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { onBackPressed() }

        // Setup RecyclerViews
        binding.rvEntities.layoutManager = LinearLayoutManager(this)
        binding.rvEntities.adapter = entityAdapter
        binding.rvEntities.addItemDecoration(DividerItemDecoration(this, DividerItemDecoration.VERTICAL))

        binding.rvResults.layoutManager = LinearLayoutManager(this)
        binding.rvResults.adapter = courseAdapter

        // Type selector
        binding.chipGroupType.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isEmpty()) return@setOnCheckedStateChangeListener
            val newType = when (checkedIds[0]) {
                binding.chipTeacher.id -> "jg0101"
                binding.chipRoom.id -> "jx0601"
                binding.chipClass.id -> "bj0101"
                binding.chipCourse.id -> "kc0101"
                else -> "jg0101"
            }
            if (newType != currentType) {
                currentType = newType
                binding.etFilter.setText("")
                showEntityList()
                loadEntityList()
            }
        }

        // Filter input (local filter on loaded list)
        binding.etFilter.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val q = s?.toString() ?: ""
                binding.btnClear.visibility = if (q.isNotEmpty()) View.VISIBLE else View.GONE
                if (!isShowingResults) {
                    entityAdapter.filter(q)
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.btnClear.setOnClickListener {
            binding.etFilter.setText("")
        }

        // Retry button
        binding.btnRetry.setOnClickListener {
            if (isShowingResults) {
                // Will be set before calling, not used here
            } else {
                loadEntityList()
            }
        }

        // FAB back to entity list
        binding.fabBack.setOnClickListener {
            showEntityList()
        }

        // Start: login + load entity list
        loginAndLoad()
    }

    override fun onBackPressed() {
        if (isShowingResults) {
            showEntityList()
        } else {
            super.onBackPressed()
        }
    }

    private fun loginAndLoad() {
        showLoading("正在连接教务系统...")
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) { doLogin("正在连接教务系统...") }
            if (ok) {
                loadEntityList()
            } else {
                showError("登录失败，请检查账号密码设置")
            }
        }
    }

    private fun loadEntityList() {
        showLoading("正在获取${currentTypeName()}列表...")
        lifecycleScope.launch {
            try {
                val list = withContext(Dispatchers.IO) {
                    importer.searchEntity(currentType, "")
                }
                if (list.isEmpty()) {
                    showError("获取列表为空，请检查网络或登录状态")
                } else {
                    entityAdapter.setFullList(list)
                    showEntityList()
                }
            } catch (e: Exception) {
                showError("获取失败: ${e.message}")
            }
        }
    }

    private fun onEntitySelected(name: String, id: String) {
        supportActionBar?.title = name
        showLoading("正在获取 $name 的课表...")
        lifecycleScope.launch {
            try {
                val courses = withContext(Dispatchers.IO) {
                    importer.fetchGlobalSchedule(currentType, id)
                }
                val sorted = courses.sortedWith(compareBy({ it.day }, {
                    it.sectionsStr.replace(Regex("\\D"), "").take(2).toIntOrNull() ?: 0
                }))
                courseAdapter.submitList(sorted)
                if (sorted.isEmpty()) {
                    showError("暂无排课数据")
                } else {
                    showResults()
                }
            } catch (e: Exception) {
                showError("获取失败: ${e.message}")
            }
        }
    }

    // --- UI state helpers ---

    private fun showLoading(msg: String) {
        binding.layoutLoading.visibility = View.VISIBLE
        binding.tvLoadingText.text = msg
        binding.rvEntities.visibility = View.GONE
        binding.rvResults.visibility = View.GONE
        binding.layoutEmpty.visibility = View.GONE
        binding.fabBack.visibility = View.GONE
    }

    private fun showEntityList() {
        isShowingResults = false
        supportActionBar?.title = "全校课表查询"
        binding.layoutLoading.visibility = View.GONE
        binding.layoutEmpty.visibility = View.GONE
        binding.rvEntities.visibility = View.VISIBLE
        binding.rvResults.visibility = View.GONE
        binding.fabBack.visibility = View.GONE
        binding.etFilter.hint = "筛选${currentTypeName()}..."
    }

    private fun showResults() {
        isShowingResults = true
        binding.layoutLoading.visibility = View.GONE
        binding.layoutEmpty.visibility = View.GONE
        binding.rvEntities.visibility = View.GONE
        binding.rvResults.visibility = View.VISIBLE
        binding.fabBack.visibility = View.VISIBLE
    }

    private fun showError(msg: String) {
        binding.layoutLoading.visibility = View.GONE
        binding.rvEntities.visibility = View.GONE
        binding.rvResults.visibility = View.GONE
        binding.layoutEmpty.visibility = View.VISIBLE
        binding.tvEmpty.text = msg
        binding.fabBack.visibility = if (isShowingResults) View.VISIBLE else View.GONE
    }

    private fun currentTypeName() = when (currentType) {
        "jg0101" -> "教师"
        "jx0601" -> "教室"
        "bj0101" -> "班级"
        "kc0101" -> "课程"
        else -> "列表"
    }

    // --- Auth ---

    private fun doLogin(statusPrefix: String): Boolean {
        val prefs = getSharedPreferences("njfu_login", Context.MODE_PRIVATE)
        val studentId = prefs.getString("student_id", "") ?: ""
        val password = prefs.getString("password", "") ?: ""
        if (studentId.isEmpty() || password.isEmpty()) return false
        return try {
            updateLoadingText("正在连接教务系统...")
            importer.prepareSession()
            updateLoadingText("正在访问统一认证中心...")
            val params = importer.fetchLoginPage()
            updateLoadingText("正在验证账号密码...")
            importer.doLogin(studentId, password, params)
            sessionReady = true
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun updateLoadingText(text: String) {
        runOnUiThread { binding.tvLoadingText.text = text }
    }
}
