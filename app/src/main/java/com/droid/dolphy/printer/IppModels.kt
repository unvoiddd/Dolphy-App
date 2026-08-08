package com.droid.dolphy.printer

import android.net.Uri
import com.hp.jipp.encoding.Resolution

data class IppPrinter(
    val name: String,
    val uri: String,
    val location: String? = null,
    val model: String? = null,
    val discovered: Boolean = true,
) {
    val id: String get() = uri
}

data class PrinterCapabilities(
    val displayName: String,
    val info: String?,
    val stateCode: Int?,
    val stateReasons: List<String>,
    val formats: List<String>,
    val media: List<String>,
    val mediaReady: List<String>,
    val sides: List<String>,
    val colorModes: List<String>,
    val orientations: List<Int>,
    val qualities: List<Int>,
    val copiesRange: IntRange,
    val resolutions: List<Resolution>,
    val sheetBack: String?,
)

data class PrintDocument(
    val uri: Uri,
    val name: String,
    val mimeType: String,
)

data class PrintOptions(
    val copies: Int = 1,
    val media: String? = null,
    val sides: String? = null,
    val colorMode: String? = null,
    val orientationCode: Int? = null,
    val qualityCode: Int? = null,
)

data class PrintResult(
    val jobId: Int?,
    val jobState: Int?,
    val status: String,
)

data class PreparedDocument(
    val file: java.io.File,
    val mimeType: String,
    val rasterized: Boolean,
)

