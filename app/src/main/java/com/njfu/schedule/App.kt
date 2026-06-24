package com.njfu.schedule

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import com.njfu.schedule.bean.TimeNode
import com.njfu.schedule.utils.SecurePrefs

class App : Application() {

    val database: AppDatabase by lazy { AppDatabase.getDatabase(this) }

    companion object {
        lateinit var instance: App
            private set
        const val CHANNEL_SYNC = "sync_progress"
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        val themeMode = getSharedPreferences("bg_settings", Context.MODE_PRIVATE).getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_YES)
        AppCompatDelegate.setDefaultNightMode(themeMode)
        TimeNode.load(this)
        SecurePrefs.migrateIfNeeded(this)
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_SYNC,
                "课表同步",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "全校课表后台同步进度"
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }
}
