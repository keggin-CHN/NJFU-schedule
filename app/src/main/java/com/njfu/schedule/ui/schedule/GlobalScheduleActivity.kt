package com.njfu.schedule.ui.schedule

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.njfu.schedule.databinding.ActivityGlobalScheduleBinding
import com.njfu.schedule.njfu.NjfuImporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GlobalScheduleActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGlobalScheduleBinding
    private val adapter = GlobalCourseAdapter()
    private val importer = NjfuImporter()
    
    private var searchJob: Job? = null
    private var currentType = "jg0101" // 默认教师
    
    // 缓存搜索到的 ID 映射 (名字 -> ID)
    private val nameToIdMap = mutableMapOf<String, String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGlobalScheduleBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        // 获取初始查询类型
        val initTitle = intent.getStringExtra("title") ?: "教师课表"
        when (initTitle) {
            "教师课表" -> { binding.chipTeacher.isChecked = true; currentType = "jg0101" }
            "教室课表" -> { binding.chipRoom.isChecked = true; currentType = "jx0601" }
            "课程课表" -> { binding.chipCourse.isChecked = true; currentType = "kc0101" }
            "班级课表" -> { binding.chipClass.isChecked = true; currentType = "bj0101" }
        }
        updateHint()

        binding.chipGroupType.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isEmpty()) return@setOnCheckedStateChangeListener
            when (checkedIds[0]) {
                binding.chipTeacher.id -> currentType = "jg0101"
                binding.chipRoom.id -> currentType = "jx0601"
                binding.chipCourse.id -> currentType = "kc0101"
                binding.chipClass.id -> currentType = "bj0101"
            }
            updateHint()
            binding.etSearch.setText("")
            adapter.submitList(emptyList())
            binding.tvEmpty.visibility = View.VISIBLE
        }

        binding.etSearch.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val keyword = v.text.toString().trim()
                if (keyword.isNotEmpty()) {
                    performSearchAndFetch(keyword)
                }
                true
            } else {
                false
            }
        }

        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val keyword = s?.toString()?.trim() ?: ""
                if (keyword.length >= 2) {
                    debounceSearchSuggest(keyword)
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.etSearch.setOnItemClickListener { parent, _, position, _ ->
            val selectedName = parent.getItemAtPosition(position) as String
            performFetchWithSelected(selectedName)
        }
        
        // 初始确保有 Session
        prepareSessionSilently()
    }

    private fun updateHint() {
        val hint = when (currentType) {
            "jg0101" -> "输入教师姓名，如: 张三"
            "jx0601" -> "输入教室名称，如: 主楼"
            "kc0101" -> "输入课程名称，如: 高等数学"
            "bj0101" -> "输入班级名称，如: 软件"
            else -> "搜索"
        }
        binding.etSearch.hint = hint
    }

    private fun debounceSearchSuggest(keyword: String) {
        searchJob?.cancel()
        searchJob = lifecycleScope.launch {
            delay(500)
            binding.progressSearch.visibility = View.VISIBLE
            val results = withContext(Dispatchers.IO) {
                try {
                    importer.searchEntity(currentType, keyword)
                } catch (e: Exception) {
                    emptyList()
                }
            }
            binding.progressSearch.visibility = View.GONE
            if (results.isNotEmpty()) {
                nameToIdMap.clear()
                val names = results.map { 
                    nameToIdMap[it.first] = it.second
                    it.first 
                }
                val arrayAdapter = ArrayAdapter(this@GlobalScheduleActivity, android.R.layout.simple_dropdown_item_1line, names)
                binding.etSearch.setAdapter(arrayAdapter)
                binding.etSearch.showDropDown()
            }
        }
    }

    private fun updateLoadingText(text: String) {
        runOnUiThread {
            binding.tvLoadingText.text = text
        }
    }

    private fun performSearchAndFetch(keyword: String) {
        // 如果关键字已经在缓存里，直接查
        if (nameToIdMap.containsKey(keyword)) {
            performFetchWithSelected(keyword)
            return
        }
        
        // 否则先搜索一下拿ID，选第一个
        binding.layoutLoading.visibility = View.VISIBLE
        updateLoadingText("正在搜索$keyword...")
        binding.recyclerView.visibility = View.GONE
        binding.tvEmpty.visibility = View.GONE
        
        lifecycleScope.launch {
            val results = withContext(Dispatchers.IO) {
                try {
                    importer.searchEntity(currentType, keyword)
                } catch (e: Exception) {
                    emptyList()
                }
            }
            if (results.isEmpty()) {
                binding.layoutLoading.visibility = View.GONE
                binding.tvEmpty.visibility = View.VISIBLE
                binding.tvEmpty.text = "未找到相关实体"
            } else {
                nameToIdMap[results[0].first] = results[0].second
                binding.etSearch.setText(results[0].first)
                binding.etSearch.setSelection(results[0].first.length)
                performFetchWithSelected(results[0].first)
            }
        }
    }

    private fun performFetchWithSelected(name: String) {
        val id = nameToIdMap[name] ?: return
        
        binding.layoutLoading.visibility = View.VISIBLE
        updateLoadingText("准备获取课表...")
        binding.recyclerView.visibility = View.GONE
        binding.tvEmpty.visibility = View.GONE
        
        val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.hideSoftInputFromWindow(binding.etSearch.windowToken, 0)

        lifecycleScope.launch {
            try {
                updateLoadingText("正在向教务系统请求数据...")
                var courses = withContext(Dispatchers.IO) {
                    importer.fetchGlobalSchedule(currentType, id)
                }
                
                // 如果发现可能是登录失效返回空数据，尝试重新登录一次再查
                if (courses.isEmpty() && !isSessionValid()) {
                    updateLoadingText("登录状态可能过期，正在重新登录...")
                    val success = withContext(Dispatchers.IO) { loginSilently(true) }
                    if (success) {
                        updateLoadingText("登录成功，重新拉取排课数据...")
                        courses = withContext(Dispatchers.IO) {
                            importer.fetchGlobalSchedule(currentType, id)
                        }
                    }
                }

                binding.layoutLoading.visibility = View.GONE
                if (courses.isEmpty()) {
                    binding.tvEmpty.visibility = View.VISIBLE
                    binding.tvEmpty.text = "暂无排课数据"
                } else {
                    binding.recyclerView.visibility = View.VISIBLE
                    // 按星期和节次排序
                    val sorted = courses.sortedWith(compareBy({ it.day }, { 
                        val start = it.sectionsStr.substringBefore("-").replace(Regex("\\D"), "").toIntOrNull() ?: 0
                        start
                    }))
                    adapter.submitList(sorted)
                }
            } catch (e: Exception) {
                binding.layoutLoading.visibility = View.GONE
                binding.tvEmpty.visibility = View.VISIBLE
                binding.tvEmpty.text = "获取失败: ${e.message}"
            }
        }
    }

    private var sessionChecked = false
    
    private fun isSessionValid(): Boolean {
        // 简单标志位，实际也可以通过捕获特定异常判断
        return sessionChecked
    }

    private fun prepareSessionSilently() {
        lifecycleScope.launch(Dispatchers.IO) {
            loginSilently(false)
        }
    }

    private fun loginSilently(showProgress: Boolean = false): Boolean {
        val prefs = getSharedPreferences("njfu_login", Context.MODE_PRIVATE)
        val studentId = prefs.getString("student_id", "") ?: ""
        val password = prefs.getString("password", "") ?: ""
        if (studentId.isEmpty() || password.isEmpty()) return false

        return try {
            if (showProgress) updateLoadingText("正在连接教务系统...")
            importer.prepareSession()
            if (showProgress) updateLoadingText("正在访问统一认证中心...")
            val params = importer.fetchLoginPage()
            if (showProgress) updateLoadingText("正在验证账号密码...")
            importer.doLogin(studentId, password, params)
            if (showProgress) updateLoadingText("获取通行证成功...")
            sessionChecked = true
            true
        } catch (e: Exception) {
            false
        }
    }
}
