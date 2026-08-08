package com.droid.dolphy.printer

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URI

data class WifiPrintState(
    val printers: List<IppPrinter> = emptyList(),
    val scanning: Boolean = false,
    val selectedPrinter: IppPrinter? = null,
    val capabilities: PrinterCapabilities? = null,
    val loadingCapabilities: Boolean = false,
    val document: PrintDocument? = null,
    val options: PrintOptions = PrintOptions(),
    val printing: Boolean = false,
    val message: String? = null,
    val error: String? = null,
)

class WifiPrintViewModel(application: Application) : AndroidViewModel(application) {
    private val client = IppPrinterClient()
    private val preparer = PrintDocumentPreparer(application)
    private val _state = MutableStateFlow(WifiPrintState())
    val state = _state.asStateFlow()
    private val discovery = IppDiscovery(
        context = application,
        scope = viewModelScope,
        onPrinter = { printer ->
            val current = _state.value
            val printers = (current.printers.filterNot { it.uri == printer.uri } + printer)
                .sortedBy { it.name.lowercase() }
            _state.value = current.copy(printers = printers)
        },
        onError = { error ->
            if (_state.value.printers.isEmpty()) {
                _state.value = _state.value.copy(error = error)
            }
        },
    )

    init {
        startDiscovery()
    }

    fun startDiscovery() {
        if (_state.value.scanning) return
        _state.value = _state.value.copy(scanning = true, error = null, message = null)
        discovery.start()
        viewModelScope.launch {
            delay(12_000)
            discovery.stop()
            _state.value = _state.value.copy(scanning = false)
        }
    }

    fun addManual(host: String, port: Int, path: String, secure: Boolean) {
        val cleanHost = host.trim().removePrefix("ipp://").removePrefix("ipps://").trimEnd('/')
        if (cleanHost.isBlank() || port !in 1..65535) {
            _state.value = _state.value.copy(error = "Invalid printer address")
            return
        }
        val cleanPath = path.trim().trim('/').ifBlank { "ipp/print" }
        val scheme = if (secure) "ipps" else "ipp"
        val uri = "$scheme://$cleanHost:$port/$cleanPath"
        if (runCatching { URI.create(uri) }.isFailure) {
            _state.value = _state.value.copy(error = "Invalid printer address")
            return
        }
        val printer = IppPrinter(name = cleanHost, uri = uri, discovered = false)
        _state.value = _state.value.copy(
            printers = (_state.value.printers.filterNot { it.uri == uri } + printer),
            error = null,
        )
        selectPrinter(printer)
    }

    fun selectPrinter(printer: IppPrinter) {
        _state.value = _state.value.copy(
            selectedPrinter = printer,
            capabilities = null,
            loadingCapabilities = true,
            error = null,
            message = null,
        )
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { client.getCapabilities(printer) } }
                .onSuccess { capabilities ->
                    if (_state.value.selectedPrinter?.uri != printer.uri) return@onSuccess
                    _state.value = _state.value.copy(
                        capabilities = capabilities,
                        loadingCapabilities = false,
                        options = defaultOptions(capabilities),
                    )
                }
                .onFailure { error ->
                    if (_state.value.selectedPrinter?.uri != printer.uri) return@onFailure
                    _state.value = _state.value.copy(
                        loadingCapabilities = false,
                        error = readableError(error),
                    )
                }
        }
    }

    fun selectDocument(uri: Uri, name: String, mimeType: String) {
        _state.value = _state.value.copy(
            document = PrintDocument(uri, name, mimeType),
            message = null,
            error = null,
        )
    }

    fun setCopies(value: Int) {
        val range = _state.value.capabilities?.copiesRange ?: 1..1
        _state.value = _state.value.copy(options = _state.value.options.copy(copies = value.coerceIn(range)))
    }

    fun setMedia(value: String) = updateOptions { copy(media = value) }
    fun setSides(value: String) = updateOptions { copy(sides = value) }
    fun setColorMode(value: String) = updateOptions { copy(colorMode = value) }
    fun setOrientation(value: Int) = updateOptions { copy(orientationCode = value) }
    fun setQuality(value: Int) = updateOptions { copy(qualityCode = value) }

    fun printDocument() {
        val snapshot = _state.value
        val printer = snapshot.selectedPrinter ?: return
        val capabilities = snapshot.capabilities ?: return
        val document = snapshot.document ?: return
        executePrint(document.name) {
            preparer.prepare(document, capabilities, snapshot.options)
        }
    }

    fun printTestPage() {
        val snapshot = _state.value
        val capabilities = snapshot.capabilities ?: return
        executePrint("Dolphy test page") {
            preparer.prepareTestPage(capabilities, snapshot.options)
        }
    }

    fun clearNotice() {
        _state.value = _state.value.copy(message = null, error = null)
    }

    private fun executePrint(name: String, prepare: () -> PreparedDocument) {
        if (_state.value.printing) return
        val snapshot = _state.value
        val printer = snapshot.selectedPrinter ?: return
        _state.value = snapshot.copy(printing = true, error = null, message = null)
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val prepared = prepare()
                    try {
                        client.print(printer, prepared, name, snapshot.options)
                    } finally {
                        prepared.file.delete()
                    }
                }
            }.onSuccess { result ->
                val job = result.jobId?.let { " #$it" }.orEmpty()
                _state.value = _state.value.copy(
                    printing = false,
                    message = "Print job$job accepted by printer",
                )
            }.onFailure { error ->
                _state.value = _state.value.copy(
                    printing = false,
                    error = readableError(error),
                )
            }
        }
    }

    private fun updateOptions(block: PrintOptions.() -> PrintOptions) {
        _state.value = _state.value.copy(options = _state.value.options.block())
    }

    private fun defaultOptions(capabilities: PrinterCapabilities): PrintOptions {
        val media = capabilities.mediaReady.firstOrNull()
            ?: capabilities.media.firstOrNull { it.contains("iso_a4", true) }
            ?: capabilities.media.firstOrNull()
        val sides = capabilities.sides.firstOrNull { it == "one-sided" }
            ?: capabilities.sides.firstOrNull()
        val color = capabilities.colorModes.firstOrNull { it == "color" }
            ?: capabilities.colorModes.firstOrNull { it == "monochrome" }
            ?: capabilities.colorModes.firstOrNull()
        val orientation = capabilities.orientations.firstOrNull { it == 3 }
            ?: capabilities.orientations.firstOrNull()
        val quality = capabilities.qualities.firstOrNull { it == 4 }
            ?: capabilities.qualities.firstOrNull()
        return PrintOptions(
            copies = 1.coerceIn(capabilities.copiesRange),
            media = media,
            sides = sides,
            colorMode = color,
            orientationCode = orientation,
            qualityCode = quality,
        )
    }

    private fun readableError(error: Throwable): String {
        val root = generateSequence(error) { it.cause }.last()
        return when {
            root is javax.net.ssl.SSLException -> "Printer certificate is not trusted"
            root is java.net.SocketTimeoutException -> "Printer response timed out"
            root is java.net.ConnectException -> "Cannot connect to printer"
            else -> root.message ?: root.javaClass.simpleName
        }
    }

    override fun onCleared() {
        discovery.stop()
        super.onCleared()
    }
}

