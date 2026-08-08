package com.droid.dolphy.bluetooth.whisperpair

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.*
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import kotlin.concurrent.thread







class BluetoothAudioManager(private val context: Context) {

    companion object {
        private const val TAG = "BluetoothAudioManager"
        private const val SAMPLE_RATE = 16000
        private const val BUFFER_SIZE_FACTOR = 2
    }

    sealed class AudioState {
        object Disconnected : AudioState()
        object Connecting : AudioState()
        object Connected : AudioState()
        object Recording : AudioState()
        object Listening : AudioState()
        data class Error(val message: String) : AudioState()
    }

    data class RecordingInfo(
        val file: File,
        val durationMs: Long,
        val sizeBytes: Long
    )

    private val handler = Handler(Looper.getMainLooper())
    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        try {
            (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
        } catch (e: Exception) {
            Log.e(TAG, "Error getting BluetoothAdapter", e)
            null
        }
    }

    private var headsetProfile: BluetoothHeadset? = null
    private var a2dpProfile: BluetoothA2dp? = null
    private var audioManager: AudioManager? = null

    @Volatile private var audioRecord: AudioRecord? = null
    @Volatile private var audioTrack: AudioTrack? = null

    private var targetDevice: BluetoothDevice? = null
    @Volatile private var isRecording = false
    @Volatile private var isListening = false
    private var recordingFile: File? = null
    private var recordingStartTime = 0L

    private var stateCallback: ((AudioState) -> Unit)? = null
    private var recordingCallback: ((RecordingInfo) -> Unit)? = null

    private var scoReceiver: BroadcastReceiver? = null
    private var profileListener: BluetoothProfile.ServiceListener? = null

    private val lock = Object()

    @SuppressLint("MissingPermission")
    fun initialize(onReady: (Boolean) -> Unit) {
        try {
            audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

            val adapter = bluetoothAdapter
            if (adapter == null) {
                onReady(false)
                return
            }

            profileListener = object : BluetoothProfile.ServiceListener {
                override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                    try {
                        when (profile) {
                            BluetoothProfile.HEADSET -> {
                                headsetProfile = proxy as BluetoothHeadset
                                Log.d(TAG, "Headset profile connected")
                                onReady(true)
                            }
                            BluetoothProfile.A2DP -> {
                                a2dpProfile = proxy as BluetoothA2dp
                                Log.d(TAG, "A2DP profile connected")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in onServiceConnected", e)
                    }
                }

                override fun onServiceDisconnected(profile: Int) {
                    try {
                        when (profile) {
                            BluetoothProfile.HEADSET -> {
                                headsetProfile = null
                                Log.d(TAG, "Headset profile disconnected")
                            }
                            BluetoothProfile.A2DP -> {
                                a2dpProfile = null
                                Log.d(TAG, "A2DP profile disconnected")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in onServiceDisconnected", e)
                    }
                }
            }

            adapter.getProfileProxy(context, profileListener, BluetoothProfile.HEADSET)
            adapter.getProfileProxy(context, profileListener, BluetoothProfile.A2DP)
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing", e)
            onReady(false)
        }
    }

    @SuppressLint("MissingPermission")
    fun connectAudioProfile(deviceAddress: String, onStateChange: (AudioState) -> Unit) {
        stateCallback = onStateChange
        onStateChange(AudioState.Connecting)

        val adapter = bluetoothAdapter
        val headset = headsetProfile

        if (adapter == null || headset == null) {
            onStateChange(AudioState.Error("Bluetooth not available"))
            return
        }

        try {
            val device = adapter.getRemoteDevice(deviceAddress)
            targetDevice = device

            val connectedDevices = headset.connectedDevices
            if (connectedDevices.any { it.address == device.address }) {
                Log.d(TAG, "Device already connected to HFP")
                onStateChange(AudioState.Connected)
                return
            }

            if (device.bondState != BluetoothDevice.BOND_BONDED) {
                onStateChange(AudioState.Error("Устройство не сопряжено"))
                return
            }


            try {
                val connectMethod = BluetoothHeadset::class.java.getMethod("connect", BluetoothDevice::class.java)
                val result = connectMethod.invoke(headset, device) as Boolean

                if (result) {
                    Log.d(TAG, "HFP connection initiated")
                    registerScoReceiver()


                    handler.postDelayed({
                        try {
                            if (targetDevice?.address == device.address && !isHfpConnected(device.address)) {

                                val stillNotConnected = headset.connectedDevices.none { it.address == device.address }
                                if (stillNotConnected) {
                                    Log.w(TAG, "HFP connection timeout after 60s")
                                    onStateChange(AudioState.Error("HFP_TIMEOUT"))
                                } else {
                                    Log.d(TAG, "HFP connected just in time")
                                    onStateChange(AudioState.Connected)
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Connection check failed", e)
                        }
                    }, 60000)
                } else {
                    onStateChange(AudioState.Error("HFP_MANUAL_REQUIRED"))
                }
            } catch (e: java.lang.reflect.InvocationTargetException) {

                val cause = e.cause
                Log.w(TAG, "Programmatic HFP connect not available: ${cause?.message}")
                if (cause is SecurityException && cause.message?.contains("MODIFY_PHONE_STATE") == true) {
                    onStateChange(AudioState.Error("HFP_PERMISSION_DENIED"))
                } else {
                    onStateChange(AudioState.Error("HFP_MANUAL_REQUIRED"))
                }
            } catch (e: SecurityException) {
                Log.w(TAG, "HFP connect permission denied: ${e.message}")
                onStateChange(AudioState.Error("HFP_PERMISSION_DENIED"))
            } catch (e: NoSuchMethodException) {
                Log.w(TAG, "HFP connect method not found")
                onStateChange(AudioState.Error("HFP_MANUAL_REQUIRED"))
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error connecting audio profile", e)
            onStateChange(AudioState.Error("Connection error: ${e.message}"))
        }
    }

    @SuppressLint("MissingPermission")
    fun startRecording(outputDir: File, onStateChange: (AudioState) -> Unit, onRecordingComplete: (RecordingInfo) -> Unit) {

        safeStopRecording()
        safeStopListening()

        stateCallback = onStateChange
        recordingCallback = onRecordingComplete

        val am = audioManager
        if (am == null) {
            onStateChange(AudioState.Error("AudioManager not available"))
            return
        }

        registerScoReceiver()

        try {
            am.mode = AudioManager.MODE_IN_COMMUNICATION

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val scoDevice = am.availableCommunicationDevices.find {
                    it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                }
                if (scoDevice != null) {
                    am.setCommunicationDevice(scoDevice)
                } else {
                    Log.w(TAG, "No Bluetooth SCO device available")
                }
            } else {
                @Suppress("DEPRECATION")
                am.startBluetoothSco()
                @Suppress("DEPRECATION")
                am.isBluetoothScoOn = true
            }

            Log.d(TAG, "SCO connection requested for recording")

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val pcmFile = File(outputDir, "whisper_${timestamp}.pcm")
            val m4aFile = File(outputDir, "whisper_${timestamp}.m4a")
            recordingFile = m4aFile

            handler.postDelayed({
                startAudioCapture(pcmFile, m4aFile, onStateChange)
            }, 1000)

        } catch (e: Exception) {
            Log.e(TAG, "Error starting SCO for recording", e)
            onStateChange(AudioState.Error("SCO error: ${e.message}"))
        }
    }

    @SuppressLint("MissingPermission")
    private fun startAudioCapture(pcmFile: File, m4aFile: File, onStateChange: (AudioState) -> Unit) {
        synchronized(lock) {
            val bufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )

            if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
                onStateChange(AudioState.Error("Invalid buffer size"))
                return
            }

            val actualBufferSize = bufferSize * BUFFER_SIZE_FACTOR
            val am = audioManager

            try {
                val record = AudioRecord(
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    actualBufferSize
                )


                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val inputDevices = am?.getDevices(AudioManager.GET_DEVICES_INPUTS)
                    val btScoInput = inputDevices?.find {
                        it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                    }
                    if (btScoInput != null) {
                        record.setPreferredDevice(btScoInput)
                        Log.d(TAG, "Recording: Set to use Bluetooth SCO input")
                    } else {
                        Log.w(TAG, "Recording: No Bluetooth SCO input device found")
                    }
                }

                if (record.state != AudioRecord.STATE_INITIALIZED) {
                    record.release()
                    onStateChange(AudioState.Error("AudioRecord initialization failed"))
                    return
                }

                audioRecord = record
                isRecording = true
                recordingStartTime = System.currentTimeMillis()
                onStateChange(AudioState.Recording)

                thread(name = "BluetoothAudioRecorder") {
                    recordAudioLoop(pcmFile, m4aFile, actualBufferSize)
                }

                Log.d(TAG, "Recording started from BT mic")

            } catch (e: Exception) {
                Log.e(TAG, "Error starting audio capture", e)
                onStateChange(AudioState.Error("Capture error: ${e.message}"))
            }
        }
    }

    private fun recordAudioLoop(pcmFile: File, m4aFile: File, bufferSize: Int) {
        val buffer = ByteArray(bufferSize)
        var outputStream: FileOutputStream? = null
        val localRecord = audioRecord

        try {
            outputStream = FileOutputStream(pcmFile)
            localRecord?.startRecording()

            while (isRecording && localRecord != null && localRecord.state == AudioRecord.STATE_INITIALIZED) {
                val bytesRead = localRecord.read(buffer, 0, bufferSize)
                if (bytesRead > 0) {
                    outputStream.write(buffer, 0, bytesRead)
                } else if (bytesRead < 0) {
                    Log.e(TAG, "AudioRecord read error: $bytesRead")
                    break
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Recording loop error", e)
        } finally {
            try {
                outputStream?.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error closing output stream", e)
            }
        }


        val finalFile = try {
            convertPcmToM4a(pcmFile, m4aFile)
            pcmFile.delete()
            m4aFile
        } catch (e: Exception) {
            Log.e(TAG, "Error converting to M4A, keeping PCM", e)
            pcmFile
        }

        handler.post {
            val duration = System.currentTimeMillis() - recordingStartTime
            val info = RecordingInfo(
                file = finalFile,
                durationMs = duration,
                sizeBytes = finalFile.length()
            )
            recordingCallback?.invoke(info)
            stateCallback?.invoke(AudioState.Connected)
        }
    }

    private fun convertPcmToM4a(pcmFile: File, m4aFile: File) {
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        val muxer = MediaMuxer(m4aFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

        try {
            val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, SAMPLE_RATE, 1)
            format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            format.setInteger(MediaFormat.KEY_BIT_RATE, 128000)
            format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)

            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()

            val inputBuffers = codec.inputBuffers
            val outputBuffers = codec.outputBuffers
            val bufferInfo = MediaCodec.BufferInfo()

            var trackIndex = -1
            var muxerStarted = false
            var inputEof = false

            FileInputStream(pcmFile).use { inputStream ->
                val pcmBuffer = ByteArray(8192)
                var presentationTimeUs = 0L

                while (true) {

                    if (!inputEof) {
                        val inputBufferIndex = codec.dequeueInputBuffer(10000)
                        if (inputBufferIndex >= 0) {
                            val inputBuffer = inputBuffers[inputBufferIndex]
                            inputBuffer.clear()

                            val bytesRead = inputStream.read(pcmBuffer, 0, minOf(pcmBuffer.size, inputBuffer.remaining()))
                            if (bytesRead < 0) {
                                codec.queueInputBuffer(inputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputEof = true
                            } else {
                                inputBuffer.put(pcmBuffer, 0, bytesRead)
                                codec.queueInputBuffer(inputBufferIndex, 0, bytesRead, presentationTimeUs, 0)
                                presentationTimeUs += (bytesRead.toLong() * 1000000L) / (SAMPLE_RATE * 2)
                            }
                        }
                    }


                    val outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, 10000)
                    when {
                        outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            trackIndex = muxer.addTrack(codec.outputFormat)
                            muxer.start()
                            muxerStarted = true
                        }
                        outputBufferIndex >= 0 -> {
                            val outputBuffer = outputBuffers[outputBufferIndex]
                            if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                                bufferInfo.size = 0
                            }
                            if (bufferInfo.size > 0 && muxerStarted) {
                                outputBuffer.position(bufferInfo.offset)
                                outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                                muxer.writeSampleData(trackIndex, outputBuffer, bufferInfo)
                            }
                            codec.releaseOutputBuffer(outputBufferIndex, false)

                            if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                                break
                            }
                        }
                    }
                }
            }

            Log.d(TAG, "PCM to M4A conversion complete: ${m4aFile.absolutePath}")

        } finally {
            try { codec.stop() } catch (e: Exception) { }
            try { codec.release() } catch (e: Exception) { }
            try { muxer.stop() } catch (e: Exception) { }
            try { muxer.release() } catch (e: Exception) { }
        }
    }

    fun stopRecording() {
        isRecording = false
        safeStopRecording()
    }

    private fun safeStopRecording() {
        synchronized(lock) {
            try {
                val record = audioRecord
                audioRecord = null

                if (record != null) {
                    try {
                        if (record.state == AudioRecord.STATE_INITIALIZED) {
                            if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                                record.stop()
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error stopping AudioRecord", e)
                    }

                    try {
                        record.release()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error releasing AudioRecord", e)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in safeStopRecording", e)
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun startListening(onStateChange: (AudioState) -> Unit) {

        safeStopRecording()
        safeStopListening()

        stateCallback = onStateChange

        val am = audioManager
        if (am == null) {
            onStateChange(AudioState.Error("AudioManager not available"))
            return
        }

        registerScoReceiver()

        try {
            am.mode = AudioManager.MODE_IN_COMMUNICATION

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val scoDevice = am.availableCommunicationDevices.find {
                    it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                }
                if (scoDevice != null) {
                    am.setCommunicationDevice(scoDevice)
                }
            } else {
                @Suppress("DEPRECATION")
                am.startBluetoothSco()
                @Suppress("DEPRECATION")
                am.isBluetoothScoOn = true
            }

            handler.postDelayed({
                startAudioPassthrough(onStateChange)
            }, 1000)

        } catch (e: Exception) {
            Log.e(TAG, "Error starting listening", e)
            onStateChange(AudioState.Error("Listen error: ${e.message}"))
        }
    }

    @SuppressLint("MissingPermission")
    private fun startAudioPassthrough(onStateChange: (AudioState) -> Unit) {
        synchronized(lock) {
            val bufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )

            if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
                onStateChange(AudioState.Error("Invalid buffer size"))
                return
            }

            val actualBufferSize = bufferSize * BUFFER_SIZE_FACTOR
            val am = audioManager

            try {

                val record = AudioRecord(
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    actualBufferSize
                )


                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val inputDevices = am?.getDevices(AudioManager.GET_DEVICES_INPUTS)
                    val btScoInput = inputDevices?.find {
                        it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                    }
                    if (btScoInput != null) {
                        record.setPreferredDevice(btScoInput)
                        Log.d(TAG, "Set AudioRecord to use Bluetooth SCO input")
                    } else {
                        Log.w(TAG, "No Bluetooth SCO input device found")
                    }
                }

                if (record.state != AudioRecord.STATE_INITIALIZED) {
                    record.release()
                    onStateChange(AudioState.Error("AudioRecord initialization failed"))
                    return
                }

                val trackBufferSize = AudioTrack.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                ) * BUFFER_SIZE_FACTOR


                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(trackBufferSize)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()


                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val outputDevices = am?.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                    val speaker = outputDevices?.find { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                    if (speaker != null) {
                        track.setPreferredDevice(speaker)
                        Log.d(TAG, "Set AudioTrack to use phone speaker")
                    }
                }

                audioRecord = record
                audioTrack = track
                isListening = true
                onStateChange(AudioState.Listening)

                thread(name = "BluetoothAudioPassthrough") {
                    audioPassthroughLoop(actualBufferSize)
                }

                Log.d(TAG, "Real-time listening: BT mic -> phone speaker")

            } catch (e: Exception) {
                Log.e(TAG, "Error starting audio passthrough", e)
                onStateChange(AudioState.Error("Passthrough error: ${e.message}"))
            }
        }
    }

    private fun audioPassthroughLoop(bufferSize: Int) {
        val buffer = ByteArray(bufferSize)
        val localRecord = audioRecord
        val localTrack = audioTrack

        try {
            localRecord?.startRecording()
            localTrack?.play()

            while (isListening &&
                   localRecord != null &&
                   localTrack != null &&
                   localRecord.state == AudioRecord.STATE_INITIALIZED &&
                   localTrack.state == AudioTrack.STATE_INITIALIZED) {

                val bytesRead = localRecord.read(buffer, 0, bufferSize)
                if (bytesRead > 0) {
                    if (localTrack.state == AudioTrack.STATE_INITIALIZED &&
                        localTrack.playState == AudioTrack.PLAYSTATE_PLAYING) {
                        localTrack.write(buffer, 0, bytesRead)
                    }
                } else if (bytesRead < 0) {
                    Log.e(TAG, "AudioRecord read error: $bytesRead")
                    break
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Passthrough loop error", e)
        }

        handler.post {
            stateCallback?.invoke(AudioState.Connected)
        }
    }

    fun stopListening() {
        isListening = false
        safeStopListening()
    }

    private fun safeStopListening() {
        synchronized(lock) {

            try {
                val track = audioTrack
                audioTrack = null

                if (track != null) {
                    try {
                        if (track.state == AudioTrack.STATE_INITIALIZED) {
                            if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                                track.stop()
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error stopping AudioTrack", e)
                    }

                    try {
                        track.release()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error releasing AudioTrack", e)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in safeStopListening (track)", e)
            }


            try {
                val record = audioRecord
                audioRecord = null

                if (record != null) {
                    try {
                        if (record.state == AudioRecord.STATE_INITIALIZED) {
                            if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                                record.stop()
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error stopping AudioRecord", e)
                    }

                    try {
                        record.release()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error releasing AudioRecord", e)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in safeStopListening (record)", e)
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        isRecording = false
        isListening = false

        safeStopRecording()
        safeStopListening()

        val am = audioManager
        if (am != null) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    am.clearCommunicationDevice()
                } else {
                    @Suppress("DEPRECATION")
                    am.stopBluetoothSco()
                    @Suppress("DEPRECATION")
                    am.isBluetoothScoOn = false
                }
                am.mode = AudioManager.MODE_NORMAL
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping SCO", e)
            }
        }

        val headset = headsetProfile
        val device = targetDevice
        if (headset != null && device != null) {
            try {
                val disconnectMethod = BluetoothHeadset::class.java.getMethod(
                    "disconnect", BluetoothDevice::class.java
                )
                disconnectMethod.invoke(headset, device)
            } catch (e: Exception) {
                Log.e(TAG, "Error disconnecting HFP", e)
            }
        }

        unregisterScoReceiver()

        try {
            stateCallback?.invoke(AudioState.Disconnected)
        } catch (e: Exception) {
            Log.e(TAG, "Error invoking state callback", e)
        }

        stateCallback = null
        recordingCallback = null
        targetDevice = null
    }

    @SuppressLint("MissingPermission")
    fun isHfpConnected(deviceAddress: String): Boolean {
        return try {
            val headset = headsetProfile ?: return false
            val adapter = bluetoothAdapter ?: return false
            val device = adapter.getRemoteDevice(deviceAddress)
            headset.connectedDevices.contains(device)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking HFP connection", e)
            false
        }
    }

    @SuppressLint("MissingPermission")
    fun getConnectedDevices(): List<BluetoothDevice> {
        return try {
            headsetProfile?.connectedDevices ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting connected devices", e)
            emptyList()
        }
    }

    private fun registerScoReceiver() {
        if (scoReceiver != null) return

        try {
            scoReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    try {
                        when (intent.action) {
                            AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED -> {
                                val state = intent.getIntExtra(
                                    AudioManager.EXTRA_SCO_AUDIO_STATE,
                                    AudioManager.SCO_AUDIO_STATE_DISCONNECTED
                                )
                                Log.d(TAG, "SCO state: $state")

                                when (state) {
                                    AudioManager.SCO_AUDIO_STATE_CONNECTED -> {
                                        Log.d(TAG, "SCO audio connected")
                                    }
                                    AudioManager.SCO_AUDIO_STATE_DISCONNECTED -> {
                                        Log.d(TAG, "SCO audio disconnected")
                                        if (isRecording) {
                                            stopRecording()
                                            stateCallback?.invoke(AudioState.Error("SCO disconnected"))
                                        }
                                        if (isListening) {
                                            stopListening()
                                            stateCallback?.invoke(AudioState.Error("SCO disconnected"))
                                        }
                                    }
                                }
                            }
                            BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED -> {
                                val state = intent.getIntExtra(
                                    BluetoothProfile.EXTRA_STATE,
                                    BluetoothProfile.STATE_DISCONNECTED
                                )
                                @Suppress("DEPRECATION")
                                val device = intent.getParcelableExtra<BluetoothDevice>(
                                    BluetoothDevice.EXTRA_DEVICE
                                )
                                Log.d(TAG, "HFP connection state: $state for ${device?.address}")

                                when (state) {
                                    BluetoothProfile.STATE_CONNECTED -> {
                                        if (device?.address == targetDevice?.address) {
                                            Log.d(TAG, "HFP connected for target device: ${device?.address}")
                                            stateCallback?.invoke(AudioState.Connected)
                                        }
                                    }
                                    BluetoothProfile.STATE_DISCONNECTED -> {
                                        if (device?.address == targetDevice?.address) {
                                            disconnect()
                                        }
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in SCO receiver", e)
                    }
                }
            }

            val filter = IntentFilter().apply {
                addAction(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED)
                addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
                addAction(BluetoothHeadset.ACTION_AUDIO_STATE_CHANGED)
            }
            context.registerReceiver(scoReceiver, filter)
        } catch (e: Exception) {
            Log.e(TAG, "Error registering SCO receiver", e)
        }
    }

    private fun unregisterScoReceiver() {
        try {
            scoReceiver?.let {
                try {
                    context.unregisterReceiver(it)
                } catch (e: Exception) {

                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering SCO receiver", e)
        }
        scoReceiver = null
    }

    fun release() {
        try {
            disconnect()

            headsetProfile?.let {
                try {
                    bluetoothAdapter?.closeProfileProxy(BluetoothProfile.HEADSET, it)
                } catch (e: Exception) {
                    Log.e(TAG, "Error closing headset proxy", e)
                }
            }
            a2dpProfile?.let {
                try {
                    bluetoothAdapter?.closeProfileProxy(BluetoothProfile.A2DP, it)
                } catch (e: Exception) {
                    Log.e(TAG, "Error closing A2DP proxy", e)
                }
            }

            headsetProfile = null
            a2dpProfile = null
            profileListener = null
            audioManager = null
        } catch (e: Exception) {
            Log.e(TAG, "Error in release", e)
        }
    }
}

