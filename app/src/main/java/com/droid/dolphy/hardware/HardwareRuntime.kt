package com.droid.dolphy.hardware

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.ConsumerIrManager
import android.hardware.usb.UsbDevice
import com.droid.dolphy.bridge.flipper.FlipperCliAdapter
import com.droid.dolphy.bridge.flipper.FlipperInfo
import com.droid.dolphy.bridge.flipper.FlipperUsbCdcTransport
import com.droid.dolphy.core.hal.HardwareManager
import com.droid.dolphy.core.model.DeviceState
import com.droid.dolphy.core.model.HardwareDescriptor
import com.droid.dolphy.core.model.HwCapability
import com.droid.dolphy.core.model.SafetyProfile
import com.droid.dolphy.core.model.SupportLevel
import com.droid.dolphy.core.model.Transport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Owns Dolphy's device graph for the lifetime of the process.
 *
 * Features read [manager] (the HAL facade) and never talk to USB directly. The
 * UI layer calls [connectFlipper] only after the user grants USB permission.
 */
object HardwareRuntime {

    val manager = HardwareManager()

    private val initialized = AtomicBoolean(false)

    private val _flipper = MutableStateFlow<FlipperInfo?>(null)
    val flipper: StateFlow<FlipperInfo?> = _flipper.asStateFlow()

    private val _connecting = MutableStateFlow(false)
    val connecting: StateFlow<Boolean> = _connecting.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    @Volatile
    private var adapter: FlipperCliAdapter? = null

    @Volatile
    private var transport: FlipperUsbCdcTransport? = null

    fun initialize(context: Context) {
        if (!initialized.compareAndSet(false, true)) return
        registerPhoneCapabilities(context.applicationContext)
    }

    /**
     * Reconciles the registry with the current USB CDC/Flipper devices. Devices
     * that disappeared are marked DISCONNECTED instead of being deleted so the
     * graph keeps a stable identity.
     */
    fun onUsbDevicesChanged(context: Context, devices: List<UsbDevice>) {
        initialize(context)
        val registry = manager.registry
        val previousUsb = registry.all()
            .filter { it.descriptor.transport == Transport.USB_SERIAL }

        devices.forEach { device ->
            val descriptor = descriptorFor(device)
            val existing = registry.get(descriptor.id)
            if (existing == null || existing.state == DeviceState.DISCONNECTED) {
                registry.upsert(descriptor, DeviceState.DETECTED)
            }
        }

        val attachedIds = devices.map { descriptorFor(it).id }.toSet()
        previousUsb
            .filter { it.descriptor.id !in attachedIds && it.state != DeviceState.DISCONNECTED }
            .forEach { registry.updateState(it.descriptor.id, DeviceState.DISCONNECTED) }

        if (_flipper.value != null && attachedIds.none { it.startsWith("flipper:") }) {
            disconnectFlipper(keepRegistryState = false)
        }
    }

    suspend fun connectFlipper(context: Context, device: UsbDevice): Result<FlipperInfo> =
        withContext(Dispatchers.IO) {
            _connecting.value = true
            _lastError.value = null
            try {
                disconnectFlipper()
                val usbTransport = FlipperUsbCdcTransport(context.applicationContext, device)
                if (!usbTransport.hasPermission()) {
                    _lastError.value = "usb_permission_missing"
                    return@withContext Result.failure(IllegalStateException("usb_permission_missing"))
                }
                usbTransport.open()
                val cli = FlipperCliAdapter(usbTransport)
                val info = cli.connect()
                val descriptor = cli.descriptor(info)
                manager.registry.upsert(descriptor, DeviceState.READY)
                transport = usbTransport
                adapter = cli
                _flipper.value = info
                Result.success(info)
            } catch (error: Throwable) {
                _lastError.value = error.message ?: error.javaClass.simpleName
                runCatching { transport?.close() }
                transport = null
                adapter = null
                _flipper.value = null
                Result.failure(error)
            } finally {
                _connecting.value = false
            }
        }

    suspend fun flipperCommand(command: String): Result<String> = withContext(Dispatchers.IO) {
        val cli = adapter
            ?: return@withContext Result.failure(IllegalStateException("flipper_not_connected"))
        runCatching { cli.sendCommand(command) }
            .onFailure { _lastError.value = it.message ?: it.javaClass.simpleName }
    }

    fun disconnectFlipper(keepRegistryState: Boolean = true) {
        runCatching { transport?.close() }
        transport = null
        adapter = null
        _flipper.value = null
        if (keepRegistryState) {
            manager.registry.all()
                .filter { it.descriptor.transport == Transport.USB_SERIAL }
                .forEach { manager.registry.updateState(it.descriptor.id, DeviceState.DETECTED) }
        }
    }

    private fun registerPhoneCapabilities(context: Context) {
        val irManager = context.getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager
        val capabilities = buildSet {
            add(HwCapability.BLE_SCAN)
            add(HwCapability.BLE_ADVERTISE)
            add(HwCapability.AUDIO)
            if (irManager?.hasIrEmitter() == true) add(HwCapability.IR_TX)
            if (context.packageManager.hasSystemFeature(PackageManager.FEATURE_NFC)) {
                add(HwCapability.NFC_READ)
                add(HwCapability.NFC_WRITE)
            }
        }
        manager.registry.upsert(
            HardwareDescriptor(
                id = "phone:internal",
                name = "This phone",
                transport = Transport.INTERNAL,
                capabilities = capabilities,
                supportLevel = SupportLevel.CERTIFIED,
                safety = SafetyProfile(requiresLabMode = false),
            ),
            DeviceState.READY,
        )
    }

    private fun descriptorFor(device: UsbDevice): HardwareDescriptor {
        val isFlipper = FlipperUsbCdcTransport.isFlipper(device)
        return HardwareDescriptor(
            id = if (isFlipper) "flipper:${device.deviceId}" else "usb:${device.deviceId}",
            name = if (isFlipper) {
                "Flipper Zero"
            } else {
                device.productName?.takeIf { it.isNotBlank() } ?: "USB serial device"
            },
            transport = Transport.USB_SERIAL,
            capabilities = if (isFlipper) FlipperCliAdapter.FLIPPER_CAPABILITIES else setOf(HwCapability.UART),
            vendorId = device.vendorId,
            productId = device.productId,
            driverId = if (isFlipper) "flipper-cli" else null,
            supportLevel = if (isFlipper) SupportLevel.BETA else SupportLevel.EXPERIMENTAL,
            safety = SafetyProfile(requiresLabMode = true),
        )
    }
}
