package com.example.timemanager

import android.app.Application

import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class TimeManagerApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // Канал уведомлений нужен и при холодном старте с будильника.

    }
}
