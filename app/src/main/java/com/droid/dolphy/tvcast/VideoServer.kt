package com.droid.dolphy.tvcast

import fi.iki.elonen.NanoHTTPD
import java.io.File
import java.io.FileInputStream

class VideoServer(private val file: File, private val contentType: String) : NanoHTTPD(0) {
    override fun serve(session: IHTTPSession): Response {
        if (session.uri == "/" || session.uri == "/play") {
            return newFixedLengthResponse(Response.Status.OK, "text/html", """
                <!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"></head>
                <body style="margin:0;background:#111"><video src="/video" controls autoplay style="width:100%;height:100vh"></video></body></html>
            """.trimIndent())
        }
        if (session.uri != "/video") return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not found")
        val size = file.length()
        val header = session.headers["range"]
        val range = header?.let { HttpByteRange.parse(it, size) }
        if (header != null && range == null) return newFixedLengthResponse(Response.Status.RANGE_NOT_SATISFIABLE, "text/plain", "Invalid range").apply {
            addHeader("Content-Range", "bytes */$size")
        }
        val start = range?.start ?: 0L
        val stream = FileInputStream(file)
        stream.channel.position(start)
        return newFixedLengthResponse(if (range == null) Response.Status.OK else Response.Status.PARTIAL_CONTENT,
            contentType, stream, range?.length ?: size).apply {
            addHeader("Accept-Ranges", "bytes")
            if (range != null) addHeader("Content-Range", "bytes ${range.start}-${range.end}/$size")
        }
    }
}
