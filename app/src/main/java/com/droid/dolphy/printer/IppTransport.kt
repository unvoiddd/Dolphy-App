package com.droid.dolphy.printer

import com.hp.jipp.encoding.Attribute
import com.hp.jipp.encoding.IppInputStream
import com.hp.jipp.encoding.IppOutputStream
import com.hp.jipp.encoding.IppPacket
import com.hp.jipp.encoding.Tag
import com.hp.jipp.model.Orientation
import com.hp.jipp.model.PrintQuality
import com.hp.jipp.model.Status
import com.hp.jipp.model.Types
import com.hp.jipp.trans.IppClientTransport
import com.hp.jipp.trans.IppPacketData
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

class IppHttpTransport : IppClientTransport {
    override fun sendData(uri: URI, request: IppPacketData): IppPacketData {
        val httpUri = uri.toString()
            .replaceFirst("ipps://", "https://")
            .replaceFirst("ipp://", "http://")
        val connection = URL(httpUri).openConnection() as HttpURLConnection
        connection.connectTimeout = 8_000
        connection.readTimeout = 90_000
        connection.requestMethod = "POST"
        connection.setRequestProperty("Content-Type", "application/ipp")
        connection.setRequestProperty("Accept", "application/ipp")
        connection.setRequestProperty("User-Agent", "Dolphy/2.3 IPP")
        connection.setChunkedStreamingMode(64 * 1024)
        connection.doOutput = true

        try {
            IppOutputStream(connection.outputStream).use { output ->
                output.write(request.packet)
                request.data?.use { input -> input.copyTo(output, 64 * 1024) }
            }

            val httpCode = connection.responseCode
            if (httpCode != HttpURLConnection.HTTP_OK) {
                val detail = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                throw IOException("HTTP $httpCode ${connection.responseMessage}${if (detail.isBlank()) "" else ": $detail"}")
            }

            val responseBytes = ByteArrayOutputStream()
            connection.inputStream.use { it.copyTo(responseBytes, 32 * 1024) }
            val input = IppInputStream(ByteArrayInputStream(responseBytes.toByteArray()))
            return IppPacketData(input.readPacket(), input)
        } finally {
            connection.disconnect()
        }
    }
}

class IppPrinterClient(
    private val transport: IppClientTransport = IppHttpTransport(),
) {
    fun getCapabilities(printer: IppPrinter): PrinterCapabilities {
        val uri = URI.create(printer.uri)
        val request = IppPacket.getPrinterAttributes(
            uri,
            Types.printerName,
            Types.printerInfo,
            Types.printerState,
            Types.printerStateReasons,
            Types.documentFormatSupported,
            Types.mediaSupported,
            Types.mediaReady,
            Types.sidesSupported,
            Types.printColorModeSupported,
            Types.orientationRequestedSupported,
            Types.printQualitySupported,
            Types.copiesSupported,
            Types.pwgRasterDocumentResolutionSupported,
            Types.pwgRasterDocumentSheetBack,
        ).putOperationAttributes(Types.requestingUserName.of("Dolphy")).build()
        val response = transport.sendData(uri, IppPacketData(request)).packet
        requireSuccess(response)
        val group = Tag.printerAttributes
        val copies = response.getValue(group, Types.copiesSupported) ?: 1..1
        return PrinterCapabilities(
            displayName = response.getString(group, Types.printerName) ?: printer.name,
            info = response.getString(group, Types.printerInfo),
            stateCode = response.getValue(group, Types.printerState)?.code,
            stateReasons = response.getStrings(group, Types.printerStateReasons),
            formats = response.getStrings(group, Types.documentFormatSupported),
            media = response.getStrings(group, Types.mediaSupported),
            mediaReady = response.getStrings(group, Types.mediaReady),
            sides = response.getStrings(group, Types.sidesSupported),
            colorModes = response.getStrings(group, Types.printColorModeSupported),
            orientations = response.getValues(group, Types.orientationRequestedSupported).map { it.code },
            qualities = response.getValues(group, Types.printQualitySupported).map { it.code },
            copiesRange = copies,
            resolutions = response.getValues(group, Types.pwgRasterDocumentResolutionSupported),
            sheetBack = response.getString(group, Types.pwgRasterDocumentSheetBack),
        )
    }

    fun print(
        printer: IppPrinter,
        document: PreparedDocument,
        documentName: String,
        options: PrintOptions,
    ): PrintResult {
        val uri = URI.create(printer.uri)
        val operationAttributes = listOf(
            Types.requestingUserName.of("Dolphy"),
            Types.jobName.of(documentName.take(200)),
            Types.documentFormat.of(document.mimeType),
            Types.ippAttributeFidelity.of(true),
        )
        val jobAttributes = buildJobAttributes(options, document.rasterized)
        val validation = IppPacket.validateJob(uri)
            .putOperationAttributes(operationAttributes)
            .putJobAttributes(jobAttributes)
            .build()
        requireSuccess(transport.sendData(uri, IppPacketData(validation)).packet)

        val printRequest = IppPacket.printJob(uri)
            .putOperationAttributes(operationAttributes)
            .putJobAttributes(jobAttributes)
            .build()
        val response = FileInputStream(document.file).use { input ->
            transport.sendData(uri, IppPacketData(printRequest, input)).packet
        }
        requireSuccess(response)
        return PrintResult(
            jobId = response.getValue(Tag.jobAttributes, Types.jobId),
            jobState = response.getValue(Tag.jobAttributes, Types.jobState)?.code,
            status = response.status.name,
        )
    }

    private fun buildJobAttributes(options: PrintOptions, rasterized: Boolean): List<Attribute<*>> {
        val attributes = mutableListOf<Attribute<*>>()
        if (options.copies > 1) attributes += Types.copies.of(options.copies)
        options.media?.let { attributes += Types.media.of(it) }
        options.sides?.let { attributes += Types.sides.of(it) }
        options.colorMode?.let { attributes += Types.printColorMode.of(it) }
        if (!rasterized) {
            options.orientationCode?.let { attributes += Types.orientationRequested.of(Orientation[it]) }
        }
        options.qualityCode?.let { attributes += Types.printQuality.of(PrintQuality[it]) }
        return attributes
    }

    private fun requireSuccess(packet: IppPacket) {
        if (packet.code !in 0x0000..0x00ff) {
            val message = packet.getString(Tag.operationAttributes, Types.statusMessage)
            val status = Status[packet.code].name
            throw IOException(if (message.isNullOrBlank()) status else "$status: $message")
        }
    }
}

