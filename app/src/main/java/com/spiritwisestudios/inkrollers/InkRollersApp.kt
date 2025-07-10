package com.spiritwisestudios.inkrollers

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class InkRollersApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Hilt setup is automatic, but you can add other application-wide
        // initialization logic here if needed.
    }
} 