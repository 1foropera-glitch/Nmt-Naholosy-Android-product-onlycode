package com.example.data

import android.content.Context
import java.util.UUID

/** Intentionally excluded from Android backup and device transfer. */
class InstallIdentity(context: Context) {
    val id: String
    init {
        val file = java.io.File(context.applicationContext.noBackupFilesDir, "sync-installation-id")
        id = synchronized(InstallIdentity::class.java) {
            if (file.exists()) file.readText().trim().also { require(it.isNotBlank()) }
            else UUID.randomUUID().toString().also { value ->
                val atomic = android.util.AtomicFile(file)
                val stream = atomic.startWrite()
                try {
                    stream.write(value.toByteArray(Charsets.UTF_8))
                    atomic.finishWrite(stream)
                } catch (error: Exception) {
                    atomic.failWrite(stream)
                    throw error
                }
            }
        }
    }
}
