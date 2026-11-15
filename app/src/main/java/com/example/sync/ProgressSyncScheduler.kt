package com.example.sync

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

class ProgressSyncScheduler(context: Context) {
    private val manager = WorkManager.getInstance(context.applicationContext)
    private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    fun schedule(uid: String) {
        val input = workDataOf("uid" to uid)
        manager.enqueueUniquePeriodicWork("progress-periodic-$uid", ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<ProgressSyncWorker>(6, TimeUnit.HOURS)
                .setConstraints(network).setInputData(input).build())
        manager.enqueueUniqueWork("progress-now-$uid", ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<ProgressSyncWorker>().setConstraints(network).setInputData(input)
                .setInitialDelay(15, TimeUnit.SECONDS)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build())
    }

    fun cancel(uid: String) {
        manager.cancelUniqueWork("progress-periodic-$uid")
        manager.cancelUniqueWork("progress-now-$uid")
    }
}
