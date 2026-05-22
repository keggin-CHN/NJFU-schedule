package com.njfu.schedule

import android.app.Application

class App : Application() {

    val database: AppDatabase by lazy { AppDatabase.getDatabase(this) }

    companion object {
        lateinit var instance: App
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }
}
