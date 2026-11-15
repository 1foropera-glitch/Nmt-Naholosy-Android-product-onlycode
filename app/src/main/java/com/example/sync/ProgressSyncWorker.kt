package com.example.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.NaholosyApplication
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import com.example.StartupState

class ProgressSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val uid = inputData.getString("uid") ?: return Result.failure()
        val container = (applicationContext as NaholosyApplication).container
        return try {
            if (container.startup.first { it != StartupState.Loading } != StartupState.Ready) return Result.retry()
            container.refreshAccount()
            if (container.signedInUid() != uid) return Result.success()
            if (container.syncRepository.syncNow(uid)) Result.success()
            else if ((container.syncRepository.state.value as? ProgressSyncState.Error)?.retryable == false) Result.failure()
            else Result.retry()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            container.telemetry.recordException(failure)
            Result.retry()
        }
    }
}
