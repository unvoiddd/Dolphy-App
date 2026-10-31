package com.droid.dolphy.rootd

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat

/**
 * P2 daemon skeleton: a persistent privileged broker service.
 *
 * This converts the P1 `PrivilegedBroker` (process-based `su` launcher) into
 * a `Service` that can be bound by `MainActivity` or `HardwareRuntime`.
 * The actual privileged operations still use `RootdProtocol` + `RootAuditLog`.
 */
class RootdService : Service() {

    private val binder = RootdBinder()
    private lateinit var broker: PrivilegedBroker
    private lateinit var auditLog: RootAuditLog

    inner class RootdBinder : Binder() {
        fun getBroker(): PrivilegedBroker = broker
        fun getAuditLog(): RootAuditLog = auditLog
    }

    override fun onCreate() {
        super.onCreate()
        auditLog = RootAuditLog(this)
        broker = PrivilegedBroker(this, auditLog, "rootd-service")
        createNotificationChannel()
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Dolphy Root Service")
            .setContentText("Monitoring privileged operations")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()
        startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "RootdService destroyed")
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Dolphy Root Daemon",
            NotificationManager.IMPORTANCE_LOW,
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val TAG = "RootdService"
        private const val CHANNEL_ID = "dolphy_rootd_channel"
        private const val NOTIFICATION_ID = 1001
    }
}
