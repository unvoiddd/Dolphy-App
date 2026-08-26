package com.droid.dolphy.plugin

import android.app.Activity
import java.lang.ref.WeakReference

object PluginRuntimeAccess {
    private var activityReference = WeakReference<Activity>(null)

    fun attach(activity: Activity) {
        activityReference = WeakReference(activity)
    }

    fun detach(activity: Activity) {
        if (activityReference.get() === activity) activityReference.clear()
    }

    fun activity(): Activity? = activityReference.get()
}
