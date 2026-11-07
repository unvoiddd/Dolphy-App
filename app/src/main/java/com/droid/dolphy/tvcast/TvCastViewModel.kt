package com.droid.dolphy.tvcast

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.droid.dolphy.R
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.net.NetworkInterface
import java.net.Inet4Address

data class TvCastState(
    val videoUrl: String? = null,
    val mime: String = "video/mp4",
    val importing: Boolean = false,
    val scanning: Boolean = false,
    val devices: List<DlnaRenderer> = emptyList(),
    val notice: String? = null,
    val loading: Boolean = false,
)

class TvCastViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application
    private val transport = DlnaTransport(application)
    private val _state = MutableStateFlow(TvCastState())
    val state = _state.asStateFlow()
    private var server: VideoServer? = null
    private var cachedVideo: File? = null
    private var importJob: Job? = null
    private var discoveryJob: Job? = null

    fun importVideo(uri: Uri) {
        importJob?.cancel()
        _state.value = _state.value.copy(importing = true, notice = app.getString(R.string.cast_importing))
        importJob = viewModelScope.launch {
            var candidate: File? = null
            var newServer: VideoServer? = null
            try {
                val ip = localAddress() ?: error(app.getString(R.string.cast_network_missing))
                val mime = app.contentResolver.getType(uri)?.takeIf { it.startsWith("video/") }
                    ?: error(app.getString(R.string.cast_import_failed))
                withContext(Dispatchers.IO) {
                    val file = File.createTempFile("dolphy_cast_", ".video", app.cacheDir)
                    candidate = file
                    app.contentResolver.openInputStream(uri)?.use { input ->
                        file.outputStream().use { output ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                ensureActive()
                                val count = input.read(buffer)
                                if (count < 0) break
                                check(app.cacheDir.usableSpace > count + 1024 * 1024) { app.getString(R.string.cast_file_too_large) }
                                output.write(buffer, 0, count)
                            }
                        }
                    } ?: error(app.getString(R.string.cast_import_failed))
                    check(file.length() > 0) { app.getString(R.string.cast_import_failed) }
                    newServer = VideoServer(file, mime).apply { start() }
                }
                ensureActive()
                server?.stop()
                cachedVideo?.delete()
                cachedVideo = candidate
                server = newServer
                candidate = null
                newServer = null
                _state.value = _state.value.copy(videoUrl = "http://$ip:${server!!.listeningPort}/video",
                    mime = mime, importing = false, notice = app.getString(R.string.cast_ready))
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                _state.value = _state.value.copy(importing = false,
                    notice = app.getString(R.string.cast_failed, error.message ?: error.javaClass.simpleName))
            } finally {
                newServer?.stop()
                candidate?.delete()
            }
        }
    }

    fun discover() {
        if (discoveryJob?.isActive == true) return
        _state.value = _state.value.copy(scanning = true, devices = emptyList())
        discoveryJob = viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    transport.discover { device ->
                        launch(Dispatchers.Main) {
                            _state.value = _state.value.copy(devices = (_state.value.devices.filterNot { it.controlUrl == device.controlUrl } + device))
                        }
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { _state.value = _state.value.copy(notice = app.getString(R.string.cast_discovery_failed, error.message.orEmpty())) }
            finally { _state.value = _state.value.copy(scanning = false) }
        }
    }

    fun play(device: DlnaRenderer) {
        val url = _state.value.videoUrl ?: return
        if (_state.value.loading) return
        loading()
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { transport.play(device, url) }
                result(true)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { result(false, error.message) }
        }
    }
    fun loading() { _state.value = _state.value.copy(loading = true, notice = app.getString(R.string.cast_loading)) }
    fun result(success: Boolean, reason: String? = null) {
        _state.value = _state.value.copy(loading = false, notice = if (success) app.getString(R.string.cast_loaded)
            else app.getString(R.string.cast_failed, reason.orEmpty()))
    }
    private fun localAddress(): String? {
        return NetworkInterface.getNetworkInterfaces()?.toList()?.filter { it.isUp && !it.isLoopback }
            ?.sortedBy { if (it.name.startsWith("wlan") || it.name.startsWith("eth") || it.name.startsWith("ap")) 0 else 1 }
            ?.flatMap { it.inetAddresses.toList() }
            ?.firstOrNull { it is Inet4Address && (it.isSiteLocalAddress || it.isLinkLocalAddress) }?.hostAddress
    }
    override fun onCleared() {
        importJob?.cancel()
        discoveryJob?.cancel()
        server?.stop()
        cachedVideo?.delete()
        super.onCleared()
    }
}
