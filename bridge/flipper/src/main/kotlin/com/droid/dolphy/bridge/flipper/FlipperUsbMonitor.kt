package com.droid.dolphy.bridge.flipper

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import androidx.core.content.ContextCompat

/**
 * Observes USB attach/detach and reports Flipper/CDC devices. Must be started
 * and stopped with the foreground screen/service that owns the device.
 */
class FlipperUsbMonitor(
    private val context: Context,
    private val onDevicesChanged: (List<UsbDevice>) -> Unit,
) {
    private var registered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                UsbManager.ACTION_USB_DEVICE_ATTACHED,
                UsbManager.ACTION_USB_DEVICE_DETACHED,
                -> notifyChanged()
            }
        }
    }

    fun start() {
        if (registered) return
        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        ContextCompat.registerReceiver(
            context.applicationContext,
            receiver,
            filter,
            ContextCompat.RECEIVER_EXPORTED,
        )
        registered = true
        notifyChanged()
    }

    fun stop() {
        if (!registered) return
        runCatching { context.applicationContext.unregisterReceiver(receiver) }
        registered = false
    }

    fun notifyChanged() {
        onDevicesChanged(FlipperUsbCdcTransport.connectedDevices(context))
    }
}
