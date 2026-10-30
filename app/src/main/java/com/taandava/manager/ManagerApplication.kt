package com.taandava.manager

import android.app.Application
import android.util.Log

class ManagerApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("Manager", "CRITICAL UNCAUGHT EXCEPTION on thread ${thread.name}: ${throwable.message}", throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}
