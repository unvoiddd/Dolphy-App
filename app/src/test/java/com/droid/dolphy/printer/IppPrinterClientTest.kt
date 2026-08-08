package com.droid.dolphy.printer

import com.hp.jipp.encoding.IppInputStream
import com.hp.jipp.encoding.IppOutputStream
import com.hp.jipp.encoding.IppPacket
import com.hp.jipp.encoding.Resolution
import com.hp.jipp.encoding.ResolutionUnit
import com.hp.jipp.model.JobState
import com.hp.jipp.model.Operation
import com.hp.jipp.model.Orientation
import com.hp.jipp.model.PrintQuality
import com.hp.jipp.model.PrinterState
import com.hp.jipp.model.Status
import com.hp.jipp.model.Types
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

class IppPrinterClientTest {
    private lateinit var server: ServerSocket
    private lateinit var serverThread: Thread
    private val running = AtomicBoolean(false)
    private val operations = CopyOnWriteArrayList<Int>()
    private var receivedDocument = byteArrayOf()

    @Before
    fun startServer() {
        server = ServerSocket()
        server.bind(InetSocketAddress("127.0.0.1", 0))
        running.set(true)
        serverThread = Thread {
            while (running.get()) {
                runCatching { server.accept() }.getOrNull()?.use(::handleConnection)
            }
        }
        serverThread.start()
    }

    @After
    fun stopServer() {
        running.set(false)
        server.close()
        serverThread.join(2_000)
    }

    @Test
    fun capabilitiesAndPrintJobRoundTrip() {
        val printer = IppPrinter(
            name = "Mock",
            uri = "ipp://127.0.0.1:${server.localPort}/ipp/print",
        )
        val client = IppPrinterClient()
        val capabilities = client.getCapabilities(printer)
        assertEquals("Dolphy Test Printer", capabilities.displayName)
        assertTrue(capabilities.formats.contains("application/pdf"))
        assertEquals(1..10, capabilities.copiesRange)

        val bytes = "%PDF-1.7 Dolphy".toByteArray()
        val file = kotlin.io.path.createTempFile("dolphy_ipp_test", ".pdf").toFile()
        file.writeBytes(bytes)
        try {
            val result = client.print(
                printer = printer,
                document = PreparedDocument(file, "application/pdf", false),
                documentName = "test.pdf",
                options = PrintOptions(
                    copies = 2,
                    media = "iso_a4_210x297mm",
                    sides = "one-sided",
                    colorMode = "color",
                    orientationCode = Orientation.portrait.code,
                    qualityCode = PrintQuality.normal.code,
                ),
            )
            assertEquals(42, result.jobId)
            assertArrayEquals(bytes, receivedDocument)
            assertEquals(
                listOf(
                    Operation.getPrinterAttributes.code,
                    Operation.validateJob.code,
                    Operation.printJob.code,
                ),
                operations.toList(),
            )
        } finally {
            file.delete()
        }
    }

    private fun capabilitiesResponse(requestId: Int): IppPacket =
        IppPacket.Builder(Status.successfulOk, requestId = requestId)
            .putPrinterAttributes(
                Types.printerName.of("Dolphy Test Printer"),
                Types.printerState.of(PrinterState.idle),
                Types.documentFormatSupported.of("application/pdf", "image/pwg-raster"),
                Types.mediaSupported.of("iso_a4_210x297mm"),
                Types.mediaReady.of("iso_a4_210x297mm"),
                Types.sidesSupported.of("one-sided", "two-sided-long-edge"),
                Types.printColorModeSupported.of("color", "monochrome"),
                Types.orientationRequestedSupported.of(Orientation.portrait, Orientation.landscape),
                Types.printQualitySupported.of(PrintQuality.normal, PrintQuality.high),
                Types.copiesSupported.of(1..10),
                Types.pwgRasterDocumentResolutionSupported.of(
                    Resolution(300, 300, ResolutionUnit.dotsPerInch),
                ),
            ).build()

    private fun handleConnection(socket: Socket) {
        val input = BufferedInputStream(socket.getInputStream())
        readLine(input)
        val headers = mutableMapOf<String, String>()
        while (true) {
            val line = readLine(input)
            if (line.isEmpty()) break
            val separator = line.indexOf(':')
            if (separator > 0) {
                headers[line.substring(0, separator).trim().lowercase()] = line.substring(separator + 1).trim()
            }
        }
        val body = if (headers["transfer-encoding"]?.contains("chunked", true) == true) {
            readChunked(input)
        } else {
            input.readNBytes(headers["content-length"]?.toIntOrNull() ?: 0)
        }
        val ippInput = IppInputStream(ByteArrayInputStream(body))
        val request = ippInput.readPacket()
        operations += request.code
        if (request.code == Operation.printJob.code) receivedDocument = ippInput.readBytes()
        val response = when (request.code) {
            Operation.getPrinterAttributes.code -> capabilitiesResponse(request.requestId)
            Operation.printJob.code -> IppPacket.Builder(Status.successfulOk, requestId = request.requestId)
                .putJobAttributes(
                    Types.jobId.of(42),
                    Types.jobState.of(JobState.pending),
                ).build()
            else -> IppPacket.Builder(Status.successfulOk, requestId = request.requestId).build()
        }
        val responseBytes = ByteArrayOutputStream().also { IppOutputStream(it).use { output -> output.write(response) } }.toByteArray()
        val output = socket.getOutputStream()
        output.write(
            ("HTTP/1.1 200 OK\r\nContent-Type: application/ipp\r\nContent-Length: ${responseBytes.size}\r\nConnection: close\r\n\r\n")
                .toByteArray(StandardCharsets.US_ASCII),
        )
        output.write(responseBytes)
        output.flush()
    }

    private fun readChunked(input: BufferedInputStream): ByteArray {
        val output = ByteArrayOutputStream()
        while (true) {
            val size = readLine(input).substringBefore(';').trim().toInt(16)
            if (size == 0) {
                readLine(input)
                break
            }
            output.write(input.readNBytes(size))
            readLine(input)
        }
        return output.toByteArray()
    }

    private fun readLine(input: BufferedInputStream): String {
        val output = ByteArrayOutputStream()
        while (true) {
            val value = input.read()
            if (value == -1) break
            if (value == '\n'.code) break
            if (value != '\r'.code) output.write(value)
        }
        return output.toString(StandardCharsets.US_ASCII.name())
    }
}

