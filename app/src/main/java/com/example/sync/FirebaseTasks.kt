package com.example.sync

import com.google.android.gms.tasks.Task
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/** A timeout is a recoverable request failure; parent cancellation still propagates. */
suspend fun <T> Task<T>.awaitRequest(): T {
    val result = withTimeoutOrNull(30_000L) {
        suspendCancellableCoroutine<Task<T>> { continuation ->
            addOnCompleteListener { completed ->
                if (continuation.isActive) continuation.resume(completed)
            }
        }
    } ?: throw IOException("Хмара не відповіла вчасно. Спробуйте ще раз.")
    if (result.isCanceled) throw IOException("Хмарний запит перервано.")
    if (!result.isSuccessful) throw result.exception ?: IOException("Не вдалося завершити хмарний запит.")
    return result.result
}
