package com.njfu.schedule

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import com.njfu.schedule.bean.TimeNode

class App : Application() {

    val database: AppDatabase by lazy { AppDatabase.getDatabase(this) }

    companion object {
        lateinit var instance: App
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        val themeMode = getSharedPreferences("bg_settings", Context.MODE_PRIVATE).getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        AppCompatDelegate.setDefaultNightMode(themeMode)
        TimeNode.load(this)
    }
}
