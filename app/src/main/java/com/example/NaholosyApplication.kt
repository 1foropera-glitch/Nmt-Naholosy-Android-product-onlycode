package com.example

import android.app.Application

class NaholosyApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
    override fun onCreate() {
        super.onCreate()
        container.start()
    }
}
