package com.njfu.schedule.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.njfu.schedule.AppDatabase
import com.njfu.schedule.bean.GlobalCourseEntity
import com.njfu.schedule.njfu.NjfuImporter

class GlobalCacheWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences("njfu_login", Context.MODE_PRIVATE)
        val studentId = prefs.getString("student_id", "") ?: ""
        val password = prefs.getString("password", "") ?: ""
        if (studentId.isEmpty() || password.isEmpty()) return Result.failure()

        return try {
            val importer = NjfuImporter()
            importer.prepareSession()
            val params = importer.fetchLoginPage()
            importer.doLogin(studentId, password, params)

            val dao = AppDatabase.getDatabase(applicationContext).globalCourseDao()
            val types = listOf("jg0101", "jx0601", "bj0101", "kc0101")

            for ((index, type) in types.withIndex()) {
                setProgress(workDataOf(KEY_PROGRESS_TYPE to type, KEY_PROGRESS_INDEX to index, KEY_PROGRESS_TOTAL to types.size, KEY_PROGRESS_MSG to "正在抓取 ${typeName(type)} 课表..."))
                try {
                    val courses = importer.fetchGlobalSchedule(type, "", "", emptyMap()) { msg ->
                    }
                    val entities = courses.map {
                        GlobalCourseEntity(
                            type = type,
                            courseName = it.courseName,
                            teacher = it.teacher,
                            room = it.room,
                            weeksStr = it.weeksStr,
                            day = it.day,
                            sectionsStr = it.sectionsStr,
                            className = it.className
                        )
                    }
                    dao.deleteByType(type)
                    if (entities.isNotEmpty()) dao.insertAll(entities)
                } catch (_: Exception) {
                }
            }

            prefs.edit().putLong("global_cache_last_sync", System.currentTimeMillis()).apply()
            setProgress(workDataOf(KEY_PROGRESS_MSG to "同步完成", KEY_PROGRESS_INDEX to types.size, KEY_PROGRESS_TOTAL to types.size))
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private fun typeName(type: String) = when (type) {
        "jg0101" -> "教师"
        "jx0601" -> "教室"
        "bj0101" -> "班级"
        "kc0101" -> "课程"
        else -> type
    }

    companion object {
        const val WORK_NAME_PERIODIC = "global_cache_periodic"
        const val WORK_NAME_ONESHOT = "global_cache_oneshot"
        const val KEY_PROGRESS_MSG = "msg"
        const val KEY_PROGRESS_TYPE = "type"
        const val KEY_PROGRESS_INDEX = "index"
        const val KEY_PROGRESS_TOTAL = "total"
    }
}
