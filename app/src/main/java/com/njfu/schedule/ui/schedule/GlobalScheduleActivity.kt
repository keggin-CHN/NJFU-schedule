package com.njfu.schedule.ui.schedule

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.njfu.schedule.AppDatabase
import com.njfu.schedule.bean.GlobalCourseInfo
import com.njfu.schedule.databinding.ActivityGlobalScheduleBinding
import com.njfu.schedule.worker.GlobalCacheScheduler
import com.njfu.schedule.worker.GlobalCacheWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GlobalScheduleActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGlobalScheduleBinding
    private var currentType = "jg0101"
    private var syncObserverAttached = false

    private val courseAdapter = GlobalCourseAdapter { item ->
        showCourseDetail(item)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGlobalScheduleBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { onBackPressed() }

        binding.rvResults.layoutManager = LinearLayoutManager(this)
        binding.rvResults.adapter = courseAdapter

        val title = intent.getStringExtra("title") ?: "全校课表查询"
        currentType = when (title) {
            "教师课表" -> "jg0101"
            "教室课表" -> "jx0601"
            "班级课表" -> "bj0101"
            "课程课表" -> "kc0101"
            else -> "jg0101"
        }
        supportActionBar?.title = title
        binding.chipGroupType.visibility = View.GONE

        binding.etFilter.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                val q = binding.etFilter.text?.toString()?.trim() ?: ""
                performSearch(q)
                true
            } else {
                false
            }
        }

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
            performSearch("")
        }

        binding.btnRetry.setOnClickListener {
            val q = binding.etFilter.text?.toString()?.trim() ?: ""
            performSearch(q)
        }

        binding.fabBack.setOnClickListener {
            binding.etFilter.setText("")
            performSearch("")
        }

        binding.btnSyncCache.setOnClickListener {
            manualSync()
        }

        performSearch("")
    }

    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (binding.rvResults.visibility == View.VISIBLE) {
            showInitialState()
        } else {
            super.onBackPressed()
        }
    }

    private fun performSearch(keyword: String) {
        showLoading("正在读取本地缓存...")
        lifecycleScope.launch {
            try {
                val dao = AppDatabase.getDatabase(this@GlobalScheduleActivity).globalCourseDao()
                val results = withContext(Dispatchers.IO) {
                    if (keyword.isBlank()) {
                        dao.getByType(currentType).first()
                    } else {
                        dao.search(currentType, keyword).first()
                    }
                }
                val converted = results.map {
                    GlobalCourseInfo(
                        courseName = it.courseName,
                        teacher = it.teacher,
                        room = it.room,
                        weeksStr = it.weeksStr,
                        day = it.day,
                        sectionsStr = it.sectionsStr,
                        className = it.className
                    )
                }.sortedWith(compareBy({ it.day }, {
                    it.sectionsStr.replace(Regex("\\D"), "").take(2).toIntOrNull() ?: 0
                }))

                courseAdapter.submitList(converted)
                if (converted.isEmpty()) {
                    showError("本地没有${currentTypeName()}课表数据，请先点右上角同步")
                } else {
                    showResults()
                }
            } catch (e: Exception) {
                showError("读取本地缓存失败: ${e.message ?: "未知错误"}")
            }
        }
    }

    private fun manualSync() {
        Toast.makeText(this, "已开始同步全校课表", Toast.LENGTH_SHORT).show()
        GlobalCacheScheduler.scheduleOneShot(this)
        observeSyncProgress()
    }

    private fun observeSyncProgress() {
        if (syncObserverAttached) return
        syncObserverAttached = true

        WorkManager.getInstance(this)
            .getWorkInfosForUniqueWorkLiveData(GlobalCacheWorker.WORK_NAME_ONESHOT)
            .observe(this) { infos ->
                val info = infos.firstOrNull {
                    it.state == WorkInfo.State.RUNNING ||
                        it.state == WorkInfo.State.ENQUEUED ||
                        it.state == WorkInfo.State.BLOCKED
                } ?: infos.firstOrNull() ?: return@observe
                when (info.state) {
                    WorkInfo.State.ENQUEUED,
                    WorkInfo.State.BLOCKED -> showSyncProgress("等待网络任务启动...", 0, 4)
                    WorkInfo.State.RUNNING -> {
                        val msg = info.progress.getString(GlobalCacheWorker.KEY_PROGRESS_MSG) ?: "同步中..."
                        val idx = info.progress.getInt(GlobalCacheWorker.KEY_PROGRESS_INDEX, 0)
                        val total = info.progress.getInt(GlobalCacheWorker.KEY_PROGRESS_TOTAL, 4)
                        showSyncProgress(msg, idx, total)
                    }
                    WorkInfo.State.SUCCEEDED -> {
                        hideSyncProgress()
                        performSearch(binding.etFilter.text?.toString()?.trim() ?: "")
                        Toast.makeText(this, "同步完成", Toast.LENGTH_SHORT).show()
                    }
                    WorkInfo.State.FAILED -> {
                        hideSyncProgress()
                        Toast.makeText(this, "同步失败，请先在导入课表页保存登录信息", Toast.LENGTH_SHORT).show()
                    }
                    WorkInfo.State.CANCELLED -> hideSyncProgress()
                    else -> Unit
                }
            }
    }

    private fun showSyncProgress(msg: String, current: Int, total: Int) {
        binding.layoutLoading.visibility = View.VISIBLE
        binding.tvLoadingText.text = "$msg ($current/$total)"
        binding.rvEntities.visibility = View.GONE
        binding.rvResults.visibility = View.GONE
        binding.layoutEmpty.visibility = View.GONE
        binding.fabBack.visibility = View.GONE
    }

    private fun hideSyncProgress() {
        binding.layoutLoading.visibility = View.GONE
    }

    private fun showLoading(msg: String) {
        binding.layoutLoading.visibility = View.VISIBLE
        binding.tvLoadingText.text = msg
        binding.rvEntities.visibility = View.GONE
        binding.rvResults.visibility = View.GONE
        binding.layoutEmpty.visibility = View.GONE
        binding.fabBack.visibility = View.GONE
    }

    private fun showCourseDetail(item: GlobalCourseInfo) {
        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val view = layoutInflater.inflate(com.njfu.schedule.R.layout.dialog_global_course_detail, null)
        dialog.setContentView(view)

        view.findViewById<android.widget.TextView>(com.njfu.schedule.R.id.tv_detail_course_name).text = item.courseName
        val tvClass = view.findViewById<android.widget.TextView>(com.njfu.schedule.R.id.tv_detail_class_name)
        if (item.className.isNotEmpty()) {
            tvClass.visibility = View.VISIBLE
            tvClass.text = item.className
        } else {
            tvClass.visibility = View.GONE
        }
        view.findViewById<android.widget.TextView>(com.njfu.schedule.R.id.tv_detail_teacher).text = item.teacher.ifEmpty { "未知教师" }
        view.findViewById<android.widget.TextView>(com.njfu.schedule.R.id.tv_detail_room).text = item.room.ifEmpty { "未知教室" }
        val days = arrayOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
        val dayStr = if (item.day in 1..7) days[item.day - 1] else "未知"
        view.findViewById<android.widget.TextView>(com.njfu.schedule.R.id.tv_detail_time).text = "$dayStr ${item.sectionsStr}"
        view.findViewById<android.widget.TextView>(com.njfu.schedule.R.id.tv_detail_weeks).text = item.weeksStr.ifEmpty { "未知周次" }

        view.findViewById<android.view.View>(com.njfu.schedule.R.id.btn_detail_close).setOnClickListener { dialog.dismiss() }

        dialog.show()
    }

    private fun showInitialState() {
        binding.layoutLoading.visibility = View.GONE
        binding.layoutEmpty.visibility = View.VISIBLE
        binding.tvEmpty.text = "请输入关键词并点击键盘上的搜索按钮查询\n可输入班级、教师、教室或课程名"
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
}
