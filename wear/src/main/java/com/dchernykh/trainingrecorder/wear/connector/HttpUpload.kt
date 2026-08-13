package com.dchernykh.trainingrecorder.wear.connector

import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/** One field of a multipart body: either a value or the file itself. */
sealed interface Part {
    data class Text(
        val name: String,
        val value: String,
    ) : Part

    data class File(
        val name: String,
        val fileName: String,
        val open: () -> java.io.InputStream,
    ) : Part
}

/**
 * The multipart POST both upload endpoints expect.
 *
 * Written by hand rather than pulled from a library: the watch APK is the one
 * place where a dependency's size is felt, and this is one request shape used
 * twice. The file is streamed rather than buffered - a six-hour ride is
 * megabytes, and holding it in memory on a watch is how an upload gets killed.
 */
object HttpUpload {
    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 60_000
    private const val BUFFER_BYTES = 8 * 1024
    private const val LINE = "\r\n"

    data class Response(
        val statusCode: Int,
        val body: String,
    )

    fun post(
        url: String,
        parts: List<Part>,
        headers: Map<String, String> = emptyMap(),
    ): Response {
        val boundary = "----trainingrecorder${UUID.randomUUID()}"
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
            // Streamed in fixed chunks so the request body never has to be
            // buffered whole before the first byte goes out.
            connection.setChunkedStreamingMode(BUFFER_BYTES)
            connection.outputStream.use { writeBody(it, parts, boundary) }
            val status = connection.responseCode
            val stream = if (status in SUCCESS) connection.inputStream else connection.errorStream
            Response(status, stream?.bufferedReader()?.use { it.readText() }.orEmpty())
        } finally {
            connection.disconnect()
        }
    }

    private fun writeBody(
        out: OutputStream,
        parts: List<Part>,
        boundary: String,
    ) {
        parts.forEach { part ->
            out.write("--$boundary$LINE".toByteArray())
            when (part) {
                is Part.Text -> {
                    out.write("Content-Disposition: form-data; name=\"${part.name}\"$LINE$LINE".toByteArray())
                    out.write(part.value.toByteArray())
                }
                is Part.File -> {
                    out.write(
                        (
                            "Content-Disposition: form-data; name=\"${part.name}\"; " +
                                "filename=\"${part.fileName}\"$LINE" +
                                "Content-Type: application/octet-stream$LINE$LINE"
                        ).toByteArray(),
                    )
                    part.open().use { it.copyTo(out, BUFFER_BYTES) }
                }
            }
            out.write(LINE.toByteArray())
        }
        out.write("--$boundary--$LINE".toByteArray())
    }

    private val SUCCESS = 200..299
}
