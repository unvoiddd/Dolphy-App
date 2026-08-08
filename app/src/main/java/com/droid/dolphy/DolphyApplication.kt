package com.droid.dolphy

import android.app.Application
import android.content.Context
import com.droid.dolphy.plugin.PluginManager

class DolphyApplication : Application() {

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(AppLocale.wrap(base))
    }

    override fun onCreate() {
        super.onCreate()
        try {
            PluginManager.initialize(this)
        } catch (t: Throwable) {
            android.util.Log.e("DolphyApplication", "Plugin init failed", t)
        }
    }
}

