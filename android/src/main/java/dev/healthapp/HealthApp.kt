package dev.healthapp

import android.app.Application
import android.content.Context

class HealthApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

val Context.container: AppContainer get() = (applicationContext as HealthApp).container
