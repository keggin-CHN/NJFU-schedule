package com.njfu.schedule.worker

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

object GlobalCacheScheduler {

    fun scheduleOneShot(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<GlobalCacheWorker>()
            .setConstraints(constraints)
            .addTag(GlobalCacheWorker.WORK_NAME_ONESHOT)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork(
                GlobalCacheWorker.WORK_NAME_ONESHOT,
                ExistingWorkPolicy.REPLACE,
                request
            )
    }

    fun schedulePeriodic(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = PeriodicWorkRequestBuilder<GlobalCacheWorker>(30, TimeUnit.DAYS)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(
                GlobalCacheWorker.WORK_NAME_PERIODIC,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
    }
}
