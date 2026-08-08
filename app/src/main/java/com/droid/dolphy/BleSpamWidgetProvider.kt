package com.droid.dolphy

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.RemoteViews

class BleSpamWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        BleSpamRuntime.init(context)
        updateAll(context, appWidgetManager, appWidgetIds)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        BleSpamRuntime.init(context)
        when (intent.action) {
            ACTION_TOGGLE_IOS -> BleSpamRuntime.toggleSection(BleSection.IOS)
            ACTION_TOGGLE_SAMSUNG -> BleSpamRuntime.toggleSection(BleSection.SAMSUNG)
            ACTION_TOGGLE_ANDROID -> BleSpamRuntime.toggleSection(BleSection.ANDROID)
            ACTION_TOGGLE_WINDOWS -> BleSpamRuntime.toggleSection(BleSection.WINDOWS)
            ACTION_STOP_ALL -> BleSpamRuntime.handleStopAllAction()
        }
        updateAll(context)
    }

    companion object {
        private const val PREFS = "DolphyPrefs"

        const val ACTION_TOGGLE_IOS = "com.droid.dolphy.action.TOGGLE_IOS"
        const val ACTION_TOGGLE_SAMSUNG = "com.droid.dolphy.action.TOGGLE_SAMSUNG"
        const val ACTION_TOGGLE_ANDROID = "com.droid.dolphy.action.TOGGLE_ANDROID"
        const val ACTION_TOGGLE_WINDOWS = "com.droid.dolphy.action.TOGGLE_WINDOWS"
        const val ACTION_STOP_ALL = "com.droid.dolphy.action.BLE_STOP_ALL"

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                android.content.ComponentName(context, BleSpamWidgetProvider::class.java)
            )
            updateAll(context, manager, ids)
        }

        fun updateAll(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val accentColor = prefs.getInt("accent_color", 0xFFF98022.toInt())
            val inactiveColor = Color.parseColor("#7A7A7A")

            val iosActive = BleSpamRuntime.isSectionActive(BleSection.IOS)
            val samsungActive = BleSpamRuntime.isSectionActive(BleSection.SAMSUNG)
            val androidActive = BleSpamRuntime.isSectionActive(BleSection.ANDROID)
            val windowsActive = BleSpamRuntime.isSectionActive(BleSection.WINDOWS)

            appWidgetIds.forEach { id ->
                val views = RemoteViews(context.packageName, R.layout.ble_spam_widget)
                views.setTextViewText(R.id.widget_title, "BLE spam")
                views.setTextColor(R.id.widget_title, accentColor)

                views.setImageViewResource(R.id.widget_icon_ios, R.drawable.ble_logo_ios_white)
                views.setImageViewResource(R.id.widget_icon_samsung, R.drawable.ble_widget_samsung)
                views.setImageViewResource(R.id.widget_icon_android, R.drawable.ble_widget_android)
                views.setImageViewResource(R.id.widget_icon_windows, R.drawable.ble_widget_windows)

                views.setInt(R.id.widget_icon_ios, "setColorFilter", if (iosActive) accentColor else inactiveColor)
                views.setInt(R.id.widget_icon_samsung, "setColorFilter", if (samsungActive) accentColor else inactiveColor)
                views.setInt(R.id.widget_icon_android, "setColorFilter", if (androidActive) accentColor else inactiveColor)
                views.setInt(R.id.widget_icon_windows, "setColorFilter", if (windowsActive) accentColor else inactiveColor)

                views.setOnClickPendingIntent(
                    R.id.widget_icon_ios,
                    buildPendingIntent(context, ACTION_TOGGLE_IOS, id, 1)
                )
                views.setOnClickPendingIntent(
                    R.id.widget_icon_samsung,
                    buildPendingIntent(context, ACTION_TOGGLE_SAMSUNG, id, 2)
                )
                views.setOnClickPendingIntent(
                    R.id.widget_icon_android,
                    buildPendingIntent(context, ACTION_TOGGLE_ANDROID, id, 3)
                )
                views.setOnClickPendingIntent(
                    R.id.widget_icon_windows,
                    buildPendingIntent(context, ACTION_TOGGLE_WINDOWS, id, 4)
                )

                manager.updateAppWidget(id, views)
            }
        }

        private fun buildPendingIntent(context: Context, action: String, widgetId: Int, requestCode: Int): PendingIntent {
            val intent = Intent(context, BleSpamWidgetProvider::class.java).apply { this.action = action }
            return PendingIntent.getBroadcast(
                context,
                widgetId * 10 + requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}

