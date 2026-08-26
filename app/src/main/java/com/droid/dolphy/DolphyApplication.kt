package com.droid.dolphy

import android.app.Application
import android.content.Context
import com.droid.dolphy.plugin.PluginManager
import com.droid.dolphy.plugin.PluginDownloadPolicy

class DolphyApplication : Application() {

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(AppLocale.wrap(base))
    }

    override fun onCreate() {
        super.onCreate()
        PluginDownloadPolicy.initialize(this)
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val log = buildString {
                append("Thread: ")
                append(thread.name)
                append('\n')
                append("Time: ")
                append(java.time.Instant.now().toString())
                append('\n')
                append(android.util.Log.getStackTraceString(throwable))
            }
            runCatching { java.io.File(filesDir, "crash_stack.txt").writeText(log) }
            if (PluginManager.hasRunningPlugins()) {
                PluginManager.activateSafeModeFromCrash(this, log)
            }
            previousHandler?.uncaughtException(thread, throwable)
        }
        try {
            PluginManager.initialize(this)
        } catch (t: Throwable) {
            android.util.Log.e("DolphyApplication", "Plugin init failed", t)
        }
    }
}

