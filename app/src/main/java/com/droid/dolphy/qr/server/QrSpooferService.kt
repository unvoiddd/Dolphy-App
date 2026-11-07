package com.droid.dolphy.qr.server

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.droid.dolphy.MainActivity
import com.droid.dolphy.nfc.NfcAudioSpoofState

class QrSpooferService : Service() {
    private val binder = LocalBinder()

    companion object {
        @Volatile var serverManager: KtorServerManager? = null
        @Volatile var foregroundReady = false
        const val ACTION_STOP = "com.droid.dolphy.qr.STOP_SERVICE"
    }

    inner class LocalBinder : Binder() {
        fun getService(): QrSpooferService = this@QrSpooferService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        if (serverManager == null) {
            serverManager = KtorServerManager(applicationContext)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        createNotificationChannel()
        try {
            startForeground(1001, createNotification())
            foregroundReady = true
        } catch (error: Exception) {
            android.util.Log.e("QrSpooferService", "Cannot start foreground service", error)
            serverManager?.close()
            serverManager = null
            foregroundReady = false
            stopSelf()
            return START_NOT_STICKY
        }

        if (intent?.action == ACTION_STOP) {
            try {
                serverManager?.close()
            } catch (_: Exception) {
            }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        foregroundReady = false
        serverManager?.close()
        serverManager = null
        NfcAudioSpoofState.stopSpoofing()
        super.onDestroy()
    }

    private fun createNotification(): Notification {
        val mainIntent = Intent(this, MainActivity::class.java)
        val mainPendingIntent = PendingIntent.getActivity(
            this, 0, mainIntent, PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, "qr_spoofer_channel")
            .setContentTitle("Dolphy Spoofer")
            .setContentText(getString(com.droid.dolphy.R.string.qr_service_active))
            .setSmallIcon(android.R.drawable.stat_sys_headset)
            .setOngoing(true)
            .setContentIntent(mainPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(android.R.drawable.ic_media_pause, getString(com.droid.dolphy.R.string.stop),
                PendingIntent.getService(this, 1, Intent(this, QrSpooferService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE))
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "qr_spoofer_channel",
                "QR Spoofer Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

}
