package com.synapse.lantransfer.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors

/**
 * A lightweight HTTP file server that lets ANY device (e.g. laptop, iPhone, PC)
 * download the shared files via a standard web browser on the hotspot network.
 * Implemented using pure Java ServerSocket to run on Android.
 */
class SimpleHttpFileServer(
    private val context: Context,
    private val uris: List<Uri>,
    private val port: Int = 8080
) {
    companion object {
        private const val TAG = "SimpleHttpFileServer"
    }

    private var serverSocket: ServerSocket? = null
    private val executor = Executors.newCachedThreadPool()
    private var isRunning = false

    fun start() {
        if (isRunning) return
        isRunning = true
        executor.execute {
            try {
                serverSocket = ServerSocket(port)
                Log.d(TAG, "SimpleHttpFileServer listening on port $port")
                while (isRunning) {
                    val clientSocket = serverSocket?.accept() ?: break
                    executor.execute {
                        handleClient(clientSocket)
                    }
                }
            } catch (e: Exception) {
                if (isRunning) {
                    Log.e(TAG, "Server error: ${e.message}", e)
                }
            }
        }
    }

    fun stop() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            // Ignore
        }
        serverSocket = null
    }

    private fun handleClient(socket: Socket) {
        try {
            socket.tcpNoDelay = true
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val line = reader.readLine() ?: return
            val parts = line.split(" ")
            if (parts.size < 2) return
            val method = parts[0]
            val fullPath = parts[1]

            val output = socket.getOutputStream()
            if (fullPath == "/" || fullPath == "/index.html") {
                serveIndex(output)
            } else if (fullPath.startsWith("/download")) {
                serveFile(output, fullPath)
            } else {
                sendResponse(output, 404, "text/plain", "404 Not Found")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Client handler error: ${e.message}")
        } finally {
            try {
                socket.close()
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    private fun serveIndex(output: OutputStream) {
        val filesHtml = StringBuilder()
        uris.forEachIndexed { index, uri ->
            val info = getFileInfo(uri)
            val formattedSize = formatSize(info.size)
            filesHtml.append("""
                <div class="file-card">
                    <div class="file-info">
                        <span class="file-icon">📄</span>
                        <div class="file-details">
                            <span class="file-name">${escapeHtml(info.name)}</span>
                            <span class="file-size">$formattedSize</span>
                        </div>
                    </div>
                    <a href="/download?index=$index" class="download-btn">Download</a>
                </div>
            """.trimIndent())
        }

        val html = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Synapse Direct Share</title>
                <style>
                    body {
                        font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
                        background: radial-gradient(circle at top, #0f172a 0%, #020617 100%);
                        color: #f8fafc;
                        margin: 0;
                        padding: 24px;
                        display: flex;
                        justify-content: center;
                        align-items: center;
                        min-height: 100vh;
                        box-sizing: border-box;
                    }
                    .container {
                        width: 100%;
                        max-width: 600px;
                        background: rgba(30, 41, 59, 0.4);
                        backdrop-filter: blur(12px);
                        border: 1px solid rgba(255, 255, 255, 0.1);
                        border-radius: 24px;
                        padding: 32px;
                        box-shadow: 0 20px 40px rgba(0, 0, 0, 0.3);
                    }
                    h1 {
                        font-size: 28px;
                        font-weight: 700;
                        margin: 0 0 8px 0;
                        background: linear-gradient(135deg, #38bdf8 0%, #818cf8 100%);
                        -webkit-background-clip: text;
                        -webkit-text-fill-color: transparent;
                        text-align: center;
                    }
                    .subtitle {
                        color: #94a3b8;
                        text-align: center;
                        margin-bottom: 32px;
                        font-size: 14px;
                    }
                    .file-list {
                        display: flex;
                        flex-direction: column;
                        gap: 16px;
                    }
                    .file-card {
                        background: rgba(255, 255, 255, 0.05);
                        border: 1px solid rgba(255, 255, 255, 0.08);
                        border-radius: 16px;
                        padding: 16px;
                        display: flex;
                        justify-content: space-between;
                        align-items: center;
                        transition: all 0.2s ease;
                    }
                    .file-card:hover {
                        background: rgba(255, 255, 255, 0.08);
                        border-color: rgba(56, 189, 248, 0.4);
                    }
                    .file-info {
                        display: flex;
                        align-items: center;
                        gap: 16px;
                    }
                    .file-icon {
                        font-size: 28px;
                    }
                    .file-details {
                        display: flex;
                        flex-direction: column;
                        gap: 4px;
                    }
                    .file-name {
                        font-weight: 600;
                        font-size: 15px;
                        color: #f1f5f9;
                        word-break: break-all;
                    }
                    .file-size {
                        font-size: 13px;
                        color: #64748b;
                    }
                    .download-btn {
                        background: linear-gradient(135deg, #0284c7 0%, #4f46e5 100%);
                        color: #ffffff;
                        text-decoration: none;
                        padding: 10px 20px;
                        border-radius: 12px;
                        font-size: 14px;
                        font-weight: 600;
                        transition: transform 0.1s ease;
                    }
                    .download-btn:active {
                        transform: scale(0.96);
                    }
                </style>
            </head>
            <body>
                <div class="container">
                    <h1>Synapse Direct Share</h1>
                    <div class="subtitle">Direct local transfer over Wi-Fi Hotspot</div>
                    <div class="file-list">
                        $filesHtml
                    </div>
                </div>
            </body>
            </html>
        """.trimIndent()

        sendResponse(output, 200, "text/html; charset=utf-8", html)
    }

    private fun serveFile(output: OutputStream, path: String) {
        val index = path.substringAfter("index=").substringBefore("&").toIntOrNull()
        if (index == null || index < 0 || index >= uris.size) {
            sendResponse(output, 400, "text/plain", "400 Bad Request")
            return
        }

        val uri = uris[index]
        val info = getFileInfo(uri)

        val header = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: application/octet-stream\r\n" +
                "Content-Length: ${info.size}\r\n" +
                "Content-Disposition: attachment; filename=\"${info.name}\"\r\n" +
                "Connection: close\r\n\r\n"
        output.write(header.toByteArray(Charsets.UTF_8))

        val buffer = ByteArray(256 * 1024)
        context.contentResolver.openInputStream(uri)?.use { input ->
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                output.write(buffer, 0, bytesRead)
            }
        }
        output.flush()
    }

    private fun sendResponse(output: OutputStream, status: Int, contentType: String, content: String) {
        val bytes = content.toByteArray(Charsets.UTF_8)
        val statusText = when (status) {
            200 -> "200 OK"
            404 -> "404 Not Found"
            else -> "500 Internal Server Error"
        }
        val header = "HTTP/1.1 $statusText\r\n" +
                "Content-Type: $contentType\r\n" +
                "Content-Length: ${bytes.size}\r\n" +
                "Connection: close\r\n\r\n"
        output.write(header.toByteArray(Charsets.UTF_8))
        output.write(bytes)
        output.flush()
    }

    private fun escapeHtml(str: String): String {
        return str.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#x27;")
    }

    data class FileInfo(val name: String, val size: Long)

    private fun getFileInfo(uri: Uri): FileInfo {
        var name = "file"
        var size = 0L
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                if (nameIndex != -1) name = cursor.getString(nameIndex)
                if (sizeIndex != -1) size = cursor.getLong(sizeIndex)
            }
        }
        return FileInfo(name, size)
    }

    private fun formatSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        return String.format("%.2f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }
}
