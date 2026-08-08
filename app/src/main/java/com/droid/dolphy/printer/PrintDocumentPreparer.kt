package com.droid.dolphy.printer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import com.hp.jipp.model.Orientation
import com.hp.jipp.model.PrintQuality
import com.hp.jipp.model.PwgRasterDocumentSheetBack
import com.hp.jipp.model.Sides
import com.hp.jipp.pdl.ColorSpace
import com.hp.jipp.pdl.OutputSettings
import com.hp.jipp.pdl.RenderableDocument
import com.hp.jipp.pdl.RenderablePage
import com.hp.jipp.pdl.pwg.PwgSettings
import com.hp.jipp.pdl.pwg.PwgWriter
import java.io.File
import java.io.FileOutputStream
import kotlin.math.min
import kotlin.math.roundToInt

class PrintDocumentPreparer(private val context: Context) {
    fun prepare(
        document: PrintDocument,
        capabilities: PrinterCapabilities,
        options: PrintOptions,
    ): PreparedDocument {
        val normalizedMime = normalizeMime(document.mimeType, document.name)
        if (capabilities.formats.any { it.equals(normalizedMime, true) }) {
            return copySource(document.uri, normalizedMime)
        }
        if (capabilities.formats.none { it.equals(PWG_MIME, true) }) {
            throw IllegalArgumentException("Printer does not support $normalizedMime or $PWG_MIME")
        }
        return rasterize(document.uri, normalizedMime, capabilities, options)
    }

    fun prepareTestPage(
        capabilities: PrinterCapabilities,
        options: PrintOptions,
    ): PreparedDocument {
        if (capabilities.formats.none { it.equals(PWG_MIME, true) }) {
            throw IllegalArgumentException("Printer does not support $PWG_MIME")
        }
        val dpi = chooseDpi(capabilities)
        val (width, height) = pagePixels(options.media, options.orientationCode, dpi)
        return writeRaster(TestDocument(dpi, width, height), capabilities, options)
    }

    private fun rasterize(
        uri: Uri,
        mime: String,
        capabilities: PrinterCapabilities,
        options: PrintOptions,
    ): PreparedDocument {
        val dpi = chooseDpi(capabilities)
        val (width, height) = pagePixels(options.media, options.orientationCode, dpi)
        return when (mime) {
            "application/pdf" -> {
                val descriptor = context.contentResolver.openFileDescriptor(uri, "r")
                    ?: throw IllegalArgumentException("Cannot open PDF")
                descriptor.use { pfd ->
                    PdfRenderer(pfd).use { renderer ->
                        writeRaster(PdfDocument(renderer, dpi, width, height), capabilities, options)
                    }
                }
            }
            "image/jpeg", "image/png", "image/webp" -> {
                val bitmap = decodeBitmap(uri, width, height)
                try {
                    writeRaster(BitmapDocument(bitmap, dpi, width, height), capabilities, options)
                } finally {
                    bitmap.recycle()
                }
            }
            else -> throw IllegalArgumentException("Unsupported document type: $mime")
        }
    }

    private fun writeRaster(
        document: RenderableDocument,
        capabilities: PrinterCapabilities,
        options: PrintOptions,
    ): PreparedDocument {
        val file = File.createTempFile("dolphy_print_", ".pwg", context.cacheDir)
        val colorSpace = if (options.colorMode == "monochrome") ColorSpace.Grayscale else ColorSpace.Rgb
        val quality = when (options.qualityCode) {
            PrintQuality.draft.code -> PrintQuality.draft
            PrintQuality.high.code -> PrintQuality.high
            else -> PrintQuality.normal
        }
        val output = OutputSettings(
            colorSpace = colorSpace,
            sides = options.sides ?: Sides.oneSided,
            quality = quality,
        )
        val settings = PwgSettings(
            output = output,
            sheetBack = capabilities.sheetBack ?: PwgRasterDocumentSheetBack.normal,
            orientation = Orientation.portrait,
        )
        try {
            FileOutputStream(file).use { PwgWriter(it, settings).write(document) }
        } catch (error: Throwable) {
            file.delete()
            throw error
        }
        return PreparedDocument(file, PWG_MIME, true)
    }

    private fun copySource(uri: Uri, mime: String): PreparedDocument {
        val suffix = when (mime) {
            "application/pdf" -> ".pdf"
            "image/jpeg" -> ".jpg"
            "image/png" -> ".png"
            else -> ".bin"
        }
        val file = File.createTempFile("dolphy_print_", suffix, context.cacheDir)
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(file).use { output -> input.copyTo(output, 64 * 1024) }
            } ?: throw IllegalArgumentException("Cannot open document")
        } catch (error: Throwable) {
            file.delete()
            throw error
        }
        return PreparedDocument(file, mime, false)
    }

    private fun decodeBitmap(uri: Uri, targetWidth: Int, targetHeight: Int): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw IllegalArgumentException("Cannot decode image")
        }
        var sample = 1
        val decodeWidth = min(targetWidth, 4096)
        val decodeHeight = min(targetHeight, 4096)
        while (bounds.outWidth / sample > decodeWidth || bounds.outHeight / sample > decodeHeight) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: throw IllegalArgumentException("Cannot decode image")
    }

    private fun chooseDpi(capabilities: PrinterCapabilities): Int {
        val resolutions = capabilities.resolutions.filter { it.x == it.y }
        return resolutions.minByOrNull { kotlin.math.abs(it.x - 300) }?.x ?: 300
    }

    private fun pagePixels(media: String?, orientationCode: Int?, dpi: Int): Pair<Int, Int> {
        val (widthInches, heightInches) = parseMedia(media)
        var width = (widthInches * dpi).roundToInt()
        var height = (heightInches * dpi).roundToInt()
        if (orientationCode == Orientation.landscape.code || orientationCode == Orientation.reverseLandscape.code) {
            val swap = width
            width = height
            height = swap
        }
        return width to height
    }

    private fun parseMedia(media: String?): Pair<Double, Double> {
        val value = media.orEmpty()
        val match = MEDIA_SIZE.find(value)
        if (match != null) {
            val width = match.groupValues[1].toDoubleOrNull()
            val height = match.groupValues[2].toDoubleOrNull()
            val unit = match.groupValues[3]
            if (width != null && height != null) {
                return if (unit == "mm") width / 25.4 to height / 25.4 else width to height
            }
        }
        return 210.0 / 25.4 to 297.0 / 25.4
    }

    private fun normalizeMime(mime: String, name: String): String {
        if (mime != "application/octet-stream" && mime != "*/*") return mime.lowercase()
        return when (name.substringAfterLast('.', "").lowercase()) {
            "pdf" -> "application/pdf"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            else -> mime.lowercase()
        }
    }

    companion object {
        const val PWG_MIME = "image/pwg-raster"
        private val MEDIA_SIZE = Regex(".*_(\\d+(?:\\.\\d+)?)x(\\d+(?:\\.\\d+)?)(mm|in).*")
    }
}

private class PdfDocument(
    private val renderer: PdfRenderer,
    override val dpi: Int,
    private val pageWidth: Int,
    private val pageHeight: Int,
) : RenderableDocument() {
    override fun iterator(): Iterator<RenderablePage> =
        (0 until renderer.pageCount).map { PdfPage(renderer, it, pageWidth, pageHeight) }.iterator()
}

private class PdfPage(
    private val renderer: PdfRenderer,
    private val index: Int,
    width: Int,
    height: Int,
) : RenderablePage(width, height) {
    override fun render(yOffset: Int, swathHeight: Int, colorSpace: ColorSpace, byteArray: ByteArray) {
        val bitmap = Bitmap.createBitmap(widthPixels, swathHeight, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(Color.WHITE)
            renderer.openPage(index).use { page ->
                val scale = min(widthPixels.toFloat() / page.width, heightPixels.toFloat() / page.height)
                val left = (widthPixels - page.width * scale) / 2f
                val top = (heightPixels - page.height * scale) / 2f
                val matrix = Matrix().apply {
                    setScale(scale, scale)
                    postTranslate(left, top - yOffset)
                }
                page.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
            }
            bitmap.writePixels(colorSpace, byteArray)
        } finally {
            bitmap.recycle()
        }
    }
}

private class BitmapDocument(
    private val bitmap: Bitmap,
    override val dpi: Int,
    private val pageWidth: Int = bitmap.width,
    private val pageHeight: Int = bitmap.height,
) : RenderableDocument() {
    override fun iterator(): Iterator<RenderablePage> =
        listOf(BitmapPage(bitmap, pageWidth, pageHeight)).iterator()
}

private class TestDocument(
    override val dpi: Int,
    private val pageWidth: Int,
    private val pageHeight: Int,
) : RenderableDocument() {
    override fun iterator(): Iterator<RenderablePage> = listOf(TestPage(pageWidth, pageHeight)).iterator()
}

private class TestPage(width: Int, height: Int) : RenderablePage(width, height) {
    override fun render(yOffset: Int, swathHeight: Int, colorSpace: ColorSpace, byteArray: ByteArray) {
        val bitmap = Bitmap.createBitmap(widthPixels, swathHeight, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(20, 25, 35)
                textSize = widthPixels * 0.065f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val body = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(65, 75, 90)
                textSize = widthPixels * 0.032f
            }
            val accent = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(72, 116, 255) }
            canvas.drawRoundRect(
                widthPixels * 0.08f,
                heightPixels * 0.09f - yOffset,
                widthPixels * 0.92f,
                heightPixels * 0.12f - yOffset,
                widthPixels * 0.015f,
                widthPixels * 0.015f,
                accent,
            )
            canvas.drawText("Dolphy Wi-Fi Print", widthPixels * 0.08f, heightPixels * 0.21f - yOffset, title)
            canvas.drawText("IPP Everywhere test page", widthPixels * 0.08f, heightPixels * 0.28f - yOffset, body)
            canvas.drawText("Connection and raster printing are working.", widthPixels * 0.08f, heightPixels * 0.34f - yOffset, body)
            canvas.drawText(java.time.LocalDateTime.now().toString(), widthPixels * 0.08f, heightPixels * 0.40f - yOffset, body)
            bitmap.writePixels(colorSpace, byteArray)
        } finally {
            bitmap.recycle()
        }
    }
}

private class BitmapPage(
    private val source: Bitmap,
    width: Int,
    height: Int,
) : RenderablePage(width, height) {
    override fun render(yOffset: Int, swathHeight: Int, colorSpace: ColorSpace, byteArray: ByteArray) {
        val bitmap = Bitmap.createBitmap(widthPixels, swathHeight, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            val scale = min(widthPixels.toFloat() / source.width, heightPixels.toFloat() / source.height)
            val left = (widthPixels - source.width * scale) / 2f
            val top = (heightPixels - source.height * scale) / 2f - yOffset
            canvas.drawBitmap(
                source,
                Rect(0, 0, source.width, source.height),
                RectF(left, top, left + source.width * scale, top + source.height * scale),
                Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
            )
            bitmap.writePixels(colorSpace, byteArray)
        } finally {
            bitmap.recycle()
        }
    }
}

private fun Bitmap.writePixels(colorSpace: ColorSpace, output: ByteArray) {
    val pixels = IntArray(width * height)
    getPixels(pixels, 0, width, 0, 0, width, height)
    var offset = 0
    pixels.forEach { pixel ->
        val red = Color.red(pixel)
        val green = Color.green(pixel)
        val blue = Color.blue(pixel)
        when (colorSpace) {
            ColorSpace.Grayscale -> output[offset++] = (red * 0.2126 + green * 0.7152 + blue * 0.0722).roundToInt().toByte()
            ColorSpace.Rgb -> {
                output[offset++] = red.toByte()
                output[offset++] = green.toByte()
                output[offset++] = blue.toByte()
            }
            ColorSpace.Rgba -> {
                output[offset++] = red.toByte()
                output[offset++] = green.toByte()
                output[offset++] = blue.toByte()
                output[offset++] = 0xff.toByte()
            }
        }
    }
}

