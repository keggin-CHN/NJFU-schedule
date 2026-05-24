package com.njfu.schedule.ui.schedule

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.njfu.schedule.AppDatabase
import com.njfu.schedule.R
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

    private val entityAdapter = EntityAdapter { name, _ ->
        val intent = android.content.Intent(this, EntityScheduleActivity::class.java)
        intent.putExtra("type", currentType)
        intent.putExtra("entity_name", name)
        startActivity(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGlobalScheduleBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.rvEntities.layoutManager = LinearLayoutManager(this)
        binding.rvEntities.adapter = entityAdapter

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

        binding.etFilter.hint = "搜索${currentTypeName()}名称..."
        binding.etFilter.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val q = s?.toString()?.trim().orEmpty()
                binding.btnClear.visibility = if (q.isNotEmpty()) View.VISIBLE else View.GONE
                entityAdapter.filter(q)
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.btnClear.setOnClickListener {
            binding.etFilter.setText("")
        }

        binding.btnRetry.setOnClickListener { loadEntities() }
        binding.fabBack.visibility = View.GONE
        binding.btnSyncCache.setOnClickListener { triggerBackgroundSync() }

        loadEntities()
        observeSyncStatus()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_global_schedule, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_sync -> { triggerBackgroundSync(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun loadEntities() {
        showLoading("正在读取本地缓存...")
        lifecycleScope.launch {
            try {
                val dao = AppDatabase.getDatabase(this@GlobalScheduleActivity).globalCourseDao()
                val rows = withContext(Dispatchers.IO) { dao.getByType(currentType).first() }

                val entities = withContext(Dispatchers.Default) {
                    val grouped = when (currentType) {
                        "jg0101" -> rows.groupingBy { it.teacher.ifEmpty { "(未知教师)" } }.eachCount()
                        "jx0601" -> rows.groupingBy { it.room.ifEmpty { "(未知教室)" } }.eachCount()
                        "bj0101" -> rows.groupingBy { it.className.ifEmpty { "(未知班级)" } }.eachCount()
                        "kc0101" -> rows.groupingBy { it.courseName.ifEmpty { "(未知课程)" } }.eachCount()
                        else -> emptyMap()
                    }
                    grouped.entries
                        .filter { it.key.isNotBlank() }
                        .sortedByDescending { it.value }
                        .map { Pair(it.key, it.key) }
                }

                if (entities.isEmpty()) {
                    showError("本地没有${currentTypeName()}数据，请点击右上角同步")
                } else {
                    entityAdapter.setFullList(entities)
                    val q = binding.etFilter.text?.toString()?.trim().orEmpty()
                    if (q.isNotEmpty()) entityAdapter.filter(q)
                    showEntities()
                }
            } catch (e: Exception) {
                showError("读取本地缓存失败: ${e.message ?: "未知错误"}")
            }
        }
    }

    private fun triggerBackgroundSync() {
        Toast.makeText(this, "已开始后台同步，进度可在通知栏查看", Toast.LENGTH_SHORT).show()
        GlobalCacheScheduler.scheduleOneShot(this)
    }

    private fun observeSyncStatus() {
        WorkManager.getInstance(this)
            .getWorkInfosForUniqueWorkLiveData(GlobalCacheWorker.WORK_NAME_ONESHOT)
            .observe(this) { infos ->
                val info = infos.firstOrNull() ?: return@observe
                if (info.state == WorkInfo.State.SUCCEEDED) {
                    loadEntities()
                }
            }
    }

    private fun showLoading(msg: String) {
        binding.layoutLoading.visibility = View.VISIBLE
        binding.tvLoadingText.text = msg
        binding.rvEntities.visibility = View.GONE
        binding.rvResults.visibility = View.GONE
        binding.layoutEmpty.visibility = View.GONE
        binding.fabBack.visibility = View.GONE
    }

    private fun showEntities() {
        binding.layoutLoading.visibility = View.GONE
        binding.layoutEmpty.visibility = View.GONE
        binding.rvEntities.visibility = View.VISIBLE
        binding.rvResults.visibility = View.GONE
    }

    private fun showError(msg: String) {
        binding.layoutLoading.visibility = View.GONE
        binding.rvEntities.visibility = View.GONE
        binding.rvResults.visibility = View.GONE
        binding.layoutEmpty.visibility = View.VISIBLE
        binding.tvEmpty.text = msg
        binding.btnRetry.visibility = View.VISIBLE
    }

    private fun currentTypeName() = when (currentType) {
        "jg0101" -> "教师"
        "jx0601" -> "教室"
        "bj0101" -> "班级"
        "kc0101" -> "课程"
        else -> "列表"
    }
}
