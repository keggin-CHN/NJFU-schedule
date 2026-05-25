package com.njfu.schedule.worker

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.njfu.schedule.App
import com.njfu.schedule.AppDatabase
import com.njfu.schedule.R
import com.njfu.schedule.bean.GlobalCourseEntity
import com.njfu.schedule.njfu.NjfuImporter
import com.njfu.schedule.utils.SecurePrefs

class GlobalCacheWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val prefs = SecurePrefs.get(applicationContext)
        val studentId = prefs.getString("student_id", "") ?: ""
        val password = prefs.getString("password", "") ?: ""
        if (studentId.isEmpty() || password.isEmpty()) return Result.failure()

        setForeground(buildForegroundInfo("准备同步全校课表...", 0, 4))

        return try {
            val importer = NjfuImporter()
            importer.prepareSession()
            val loginParams = importer.fetchLoginPage()
            importer.doLogin(studentId, password, loginParams)

            val dao = AppDatabase.getDatabase(applicationContext).globalCourseDao()
            val types = listOf("jg0101", "jx0601", "bj0101", "kc0101")

            for ((index, type) in types.withIndex()) {
                val msg = "正在抓取${typeName(type)}课表..."
                val current = index + 1
                setProgress(workDataOf(
                    KEY_PROGRESS_TYPE to type,
                    KEY_PROGRESS_INDEX to current,
                    KEY_PROGRESS_TOTAL to types.size,
                    KEY_PROGRESS_MSG to msg
                ))
                updateNotification(msg, current, types.size)
                try {
                    val courses = importer.fetchGlobalSchedule(type, "", "", emptyMap()) { _ -> }
                    val entities = courses.map {
                        GlobalCourseEntity(
                            type = type,
                            typeLabel = it.typeLabel,
                            term = it.term,
                            entityName = it.entityName,
                            courseName = it.courseName,
                            teacher = it.teacher,
                            room = it.room,
                            weeksStr = it.weeksStr,
                            day = it.day,
                            sectionsStr = it.sectionsStr,
                            className = it.className,
                            collegeName = it.collegeName,
                            sectionNumbers = it.sectionNumbers,
                            slotIndex = it.slotIndex,
                            tableIndex = it.tableIndex,
                            rowIndex = it.rowIndex,
                            colIndex = it.colIndex,
                            rawText = it.rawText,
                            rawHtml = it.rawHtml,
                            rawLinesJson = it.rawLinesJson
                        )
                    }
                    // 使用事务性替换：deleteByType + insertAll 在同一事务中执行
                    dao.replaceByType(type, entities)
                } catch (_: Exception) {
                }
            }

            // 自动清理旧学期数据，仅保留最近 2 个学期
            try {
                cleanupOldTerms(dao)
            } catch (_: Exception) {
            }

            prefs.edit().putLong("global_cache_last_sync", System.currentTimeMillis()).apply()
            postFinalNotification("全校课表同步完成", "已更新本地缓存")
            Result.success()
        } catch (e: Exception) {
            postFinalNotification("全校课表同步失败", e.message ?: "请稍后重试")
            Result.retry()
        }
    }

    /**
     * 自动清理旧学期数据，仅保留最近 [KEEP_TERM_COUNT] 个学期。
     * 多学期累积会导致 global_courses 表膨胀，定期清理释放存储空间。
     */
    private suspend fun cleanupOldTerms(dao: com.njfu.schedule.dao.GlobalCourseDao) {
        val terms = dao.getAllTerms()
        if (terms.size > KEEP_TERM_COUNT) {
            val keepTerms = terms.take(KEEP_TERM_COUNT)
            dao.deleteOldTerms(keepTerms)
        }
    }

    private fun buildForegroundInfo(msg: String, current: Int, total: Int): ForegroundInfo {
        val notification = buildNotification(msg, current, total)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(msg: String, current: Int, total: Int): android.app.Notification {
        val pkgManager = applicationContext.packageManager
        val launchIntent = pkgManager.getLaunchIntentForPackage(applicationContext.packageName)
        val pendingIntent = launchIntent?.let {
            PendingIntent.getActivity(
                applicationContext, 0, it,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }

        val builder = NotificationCompat.Builder(applicationContext, App.CHANNEL_SYNC)
            .setContentTitle("全校课表同步中")
            .setContentText(msg)
            .setSmallIcon(R.drawable.ic_sync)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setProgress(total, current, total == 0)

        pendingIntent?.let { builder.setContentIntent(it) }
        return builder.build()
    }

    private fun updateNotification(msg: String, current: Int, total: Int) {
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification(msg, current, total))
    }

    private fun postFinalNotification(title: String, msg: String) {
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val n = NotificationCompat.Builder(applicationContext, App.CHANNEL_SYNC)
            .setContentTitle(title)
            .setContentText(msg)
            .setSmallIcon(R.drawable.ic_sync)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        nm.notify(NOTIFICATION_ID, n)
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
        const val NOTIFICATION_ID = 7001

        /** 保留最近的学期数量，超出的旧学期数据将被自动清理 */
        const val KEEP_TERM_COUNT = 2
    }
}
