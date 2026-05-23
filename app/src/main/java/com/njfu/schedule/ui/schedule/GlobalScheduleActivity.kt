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

    private var currentType = "jg0101"

    // Adapters
    private val courseAdapter = GlobalCourseAdapter()

    // State
    private var sessionReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGlobalScheduleBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { onBackPressed() }

        // Setup RecyclerView
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
                showInitialState()
            }
        }

        // Filter input (direct search on submit)
        binding.etFilter.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                val q = binding.etFilter.text?.toString()?.trim() ?: ""
                if (q.isNotEmpty()) {
                    performSearch(q)
                }
                true
            } else {
                false
            }
        }

        // Filter input (dynamic network search)
        binding.etFilter.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val q = s?.toString()?.trim() ?: ""
                binding.btnClear.visibility = if (q.isNotEmpty()) View.VISIBLE else View.GONE
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.btnClear.setOnClickListener {
            binding.etFilter.setText("")
            showInitialState()
        }

        // Retry button
        binding.btnRetry.setOnClickListener {
            val q = binding.etFilter.text?.toString()?.trim() ?: ""
            if (q.isNotEmpty()) performSearch(q)
        }

        // FAB to clear search
        binding.fabBack.setOnClickListener {
            binding.etFilter.setText("")
            showInitialState()
        }

        // Start: login + load entity list
        loginAndLoad()
    }

    override fun onBackPressed() {
        if (binding.rvResults.visibility == View.VISIBLE) {
            showInitialState()
        } else {
            super.onBackPressed()
        }
    }

    private fun loginAndLoad() {
        showLoading("正在连接教务系统...")
        lifecycleScope.launch {
            val errMsg = withContext(Dispatchers.IO) { doLoginGetError() }
            if (errMsg == null) {
                sessionReady = true
                showInitialState()
            } else {
                showError("登录失败：$errMsg\n\n（请先在\"导入课表\"页面登录一次）")
            }
        }
    }

    private fun performSearch(keyword: String, isRetry: Boolean = false) {
        showLoading("正在获取课表...")
        lifecycleScope.launch {
            try {
                val courses = withContext(Dispatchers.IO) {
                    importer.fetchGlobalSchedule(currentType, keyword)
                }
                val sorted = courses.sortedWith(compareBy({ it.day }, {
                    it.sectionsStr.replace(Regex("\\D"), "").take(2).toIntOrNull() ?: 0
                }))
                courseAdapter.submitList(sorted)
                if (sorted.isEmpty()) {
                    showError("暂无排课数据或未找到对应${currentTypeName()}")
                } else {
                    showResults()
                }
            } catch (e: Exception) {
                val msg = e.message ?: "未知错误"
                if (!isRetry && (msg.contains("会话") || msg.contains("登录"))) {
                    showLoading("会话已过期，正在重新登录...")
                    val errMsg = withContext(Dispatchers.IO) { doLoginGetError() }
                    if (errMsg == null) {
                        performSearch(keyword, true)
                    } else {
                        showError("重新登录失败：$errMsg")
                    }
                } else {
                    showError("获取失败: $msg")
                }
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

    private fun showInitialState() {
        supportActionBar?.title = "全校课表查询"
        binding.layoutLoading.visibility = View.GONE
        binding.layoutEmpty.visibility = View.VISIBLE
        binding.tvEmpty.text = "请输入关键字并点击键盘上的【搜索】按钮查询\n（可输入班级、教师姓名等进行精确检索）"
        binding.btnRetry.visibility = View.GONE
        binding.rvEntities.visibility = View.GONE
        binding.rvResults.visibility = View.GONE
        binding.fabBack.visibility = View.GONE
        binding.etFilter.hint = "输入并按回车搜索${currentTypeName()}..."
    }

    private fun showResults() {
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
        binding.btnRetry.visibility = View.VISIBLE
        binding.fabBack.visibility = View.GONE
    }

    private fun currentTypeName() = when (currentType) {
        "jg0101" -> "教师"
        "jx0601" -> "教室"
        "bj0101" -> "班级"
        "kc0101" -> "课程"
        else -> "列表"
    }

    // --- Auth ---

    /** 尝试登录，成功返回 null，失败返回错误信息 */
    private fun doLoginGetError(): String? {
        val prefs = getSharedPreferences("njfu_login", Context.MODE_PRIVATE)
        val studentId = prefs.getString("student_id", "") ?: ""
        val password = prefs.getString("password", "") ?: ""
        if (studentId.isEmpty() || password.isEmpty()) {
            return "未找到登录凭证，请先在\"导入课表\"页面登录"
        }
        return try {
            updateLoadingText("正在连接教务系统...")
            importer.prepareSession()
            updateLoadingText("正在访问统一认证中心...")
            val params = importer.fetchLoginPage()
            updateLoadingText("正在验证账号密码...")
            importer.doLogin(studentId, password, params)
            sessionReady = true
            null
        } catch (e: Exception) {
            e.message ?: "未知错误"
        }
    }

    private fun updateLoadingText(text: String) {
        runOnUiThread { binding.tvLoadingText.text = text }
    }
}

