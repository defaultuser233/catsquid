package com.maoyou.typingpet

import android.app.Application
import android.content.Context
import android.util.Log

class CatSquidApp : Application() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate() {
        super.onCreate()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            DiagLog.log(this, "CRASH ${throwable.javaClass.name}: ${Log.getStackTraceString(throwable)}")
            previous?.uncaughtException(thread, throwable)
        }
    }
}
