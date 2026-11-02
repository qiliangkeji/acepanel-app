package com.acepanel.client.mcp

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import kotlin.concurrent.thread

/** 一次 HTTP 响应。body 统一按 UTF-8 写出。 */
internal data class HttpResult(
    val status: Int,
    val body: String,
    val contentType: String = "application/json; charset=utf-8",
)

/**
 * 极简 MCP Streamable HTTP 服务端。
 *
 * 刻意不用 Ktor Server：桌面与 Android 共用同一份实现，原生 [ServerSocket]
 * 在两个平台都能跑，也避免为了一个本地回环端口引入一整套 server 引擎依赖。
 *
 * 只监听回环地址、只接受带 Bearer token 的请求，请求之间不共享会话状态，
 * 每个连接处理完即关闭（Connection: close），足够 MCP 客户端使用。
 */
internal class McpHttpServer(
    private val host: String,
    private val port: Int,
    private val token: String,
    private val handler: (method: String, path: String, body: String) -> HttpResult,
) {
    private var serverSocket: ServerSocket? = null
    private var acceptThread: Thread? = null

    @Volatile
    private var running = false

    val boundPort: Int
        get() = serverSocket?.localPort ?: port

    fun start() {
        if (running) return
        val socket = ServerSocket()
        socket.reuseAddress = true
        socket.bind(InetSocketAddress(host, port))
        serverSocket = socket
        running = true
        acceptThread = thread(name = "acepanel-mcp-accept", isDaemon = true) {
            while (running) {
                val client = try {
                    socket.accept()
                } catch (_: Throwable) {
                    if (!running) break else continue
                }
                thread(name = "acepanel-mcp-conn", isDaemon = true) {
                    runCatching { serve(client) }
                }
            }
        }
        println("[McpBridge] MCP HTTP listening on http://$host:${socket.localPort}/mcp")
    }

    fun stop() {
        running = false
        runCatching { serverSocket?.close() }
        serverSocket = null
        acceptThread = null
    }

    private fun serve(socket: Socket) {
        socket.use { client ->
            client.soTimeout = 30_000
            val input = BufferedInputStream(client.getInputStream())
            val output = BufferedOutputStream(client.getOutputStream())
            val request = readRequest(input)
            if (request == null) {
                write(output, HttpResult(400, """{"error":"bad request"}"""))
                return
            }

            val suppliedToken = request.headers["authorization"]
                ?.let { if (it.startsWith("Bearer ", ignoreCase = true)) it.substring(7).trim() else it.trim() }
                ?: request.headers["x-acepanel-token"]

            val result = when {
                request.path != "/mcp" && request.path != "/health" ->
                    HttpResult(404, """{"error":"not found"}""")

                request.method.equals("OPTIONS", ignoreCase = true) ->
                    HttpResult(204, "")

                suppliedToken.isNullOrBlank() || suppliedToken != token ->
                    HttpResult(401, """{"error":"unauthorized: missing or invalid bearer token"}""")

                request.path == "/health" ->
                    HttpResult(200, handler("HEALTH", "/health", "").body)

                request.method.equals("POST", ignoreCase = true) ->
                    runCatching { handler("POST", "/mcp", request.body) }
                        .getOrElse { failure ->
                            HttpResult(
                                500,
                                buildJsonObject {
                                    put("error", failure.message ?: "internal error")
                                }.toString(),
                            )
                        }

                request.method.equals("GET", ignoreCase = true) ->
                    HttpResult(405, """{"error":"this endpoint uses POST (MCP Streamable HTTP)"}""")

                request.method.equals("DELETE", ignoreCase = true) ->
                    HttpResult(200, "{}")

                else -> HttpResult(405, """{"error":"method not allowed"}""")
            }
            write(output, result)
        }
    }

    private fun readRequest(input: BufferedInputStream): HttpRequest? {
        val headerBytes = readHeaderBlock(input) ?: return null
        val headerText = String(headerBytes, StandardCharsets.ISO_8859_1)
        val lines = headerText.split("\r\n").filter { it.isNotEmpty() }
        if (lines.isEmpty()) return null

        val requestLine = lines.first().split(" ")
        if (requestLine.size < 2) return null
        val method = requestLine[0]
        val path = requestLine[1].substringBefore('?')

        val headers = LinkedHashMap<String, String>()
        for (line in lines.drop(1)) {
            val index = line.indexOf(':')
            if (index <= 0) continue
            headers[line.substring(0, index).trim().lowercase()] = line.substring(index + 1).trim()
        }

        val body = if (headers["transfer-encoding"]?.contains("chunked", ignoreCase = true) == true) {
            readChunked(input)
        } else {
            val length = headers["content-length"]?.toIntOrNull() ?: 0
            if (length <= 0) {
                ""
            } else {
                val bytes = ByteArray(length)
                var read = 0
                while (read < length) {
                    val count = input.read(bytes, read, length - read)
                    if (count < 0) break
                    read += count
                }
                String(bytes, 0, read, StandardCharsets.UTF_8)
            }
        }
        return HttpRequest(method, path, headers, body)
    }

    /** 一直读到 CRLFCRLF，返回不含结尾空行的头部字节。 */
    private fun readHeaderBlock(input: BufferedInputStream): ByteArray? {
        val out = ByteArrayOutputStream(512)
        var state = 0
        while (out.size() < 64 * 1024) {
            val byte = input.read()
            if (byte < 0) return if (out.size() == 0) null else out.toByteArray()
            out.write(byte)
            state = when (state) {
                0 -> if (byte == '\r'.code) 1 else 0
                1 -> when (byte) {
                    '\n'.code -> 2
                    '\r'.code -> 1
                    else -> 0
                }
                2 -> if (byte == '\r'.code) 3 else 0
                3 -> if (byte == '\n'.code) {
                    val all = out.toByteArray()
                    return all.copyOfRange(0, all.size - 4)
                } else 0
                else -> 0
            }
        }
        return null
    }

    private fun readChunked(input: BufferedInputStream): String {
        val out = ByteArrayOutputStream()
        while (true) {
            val sizeLine = readLine(input) ?: break
            val size = sizeLine.substringBefore(';').trim().toIntOrNull(16) ?: break
            if (size == 0) {
                while (true) {
                    val trailer = readLine(input) ?: break
                    if (trailer.isEmpty()) break
                }
                break
            }
            val chunk = ByteArray(size)
            var read = 0
            while (read < size) {
                val count = input.read(chunk, read, size - read)
                if (count < 0) break
                read += count
            }
            out.write(chunk, 0, read)
            readLine(input) // 每个 chunk 后面的 CRLF
        }
        return String(out.toByteArray(), StandardCharsets.UTF_8)
    }

    private fun readLine(input: BufferedInputStream): String? {
        val out = ByteArrayOutputStream()
        while (true) {
            val byte = input.read()
            if (byte < 0) {
                return if (out.size() == 0) null else String(out.toByteArray(), StandardCharsets.ISO_8859_1)
            }
            if (byte == '\n'.code) {
                val bytes = out.toByteArray()
                val end = if (bytes.isNotEmpty() && bytes.last() == '\r'.code.toByte()) bytes.size - 1 else bytes.size
                return String(bytes, 0, end, StandardCharsets.ISO_8859_1)
            }
            out.write(byte)
        }
    }

    private fun write(output: BufferedOutputStream, result: HttpResult) {
        val bodyBytes = result.body.toByteArray(StandardCharsets.UTF_8)
        val header = buildString {
            append("HTTP/1.1 ").append(result.status).append(' ').append(statusText(result.status)).append("\r\n")
            append("Content-Type: ").append(result.contentType).append("\r\n")
            append("Content-Length: ").append(bodyBytes.size).append("\r\n")
            append("Connection: close\r\n")
            append("Cache-Control: no-store\r\n")
            append("\r\n")
        }
        output.write(header.toByteArray(StandardCharsets.ISO_8859_1))
        if (bodyBytes.isNotEmpty()) output.write(bodyBytes)
        output.flush()
    }

    private fun statusText(status: Int): String = when (status) {
        200 -> "OK"
        202 -> "Accepted"
        204 -> "No Content"
        400 -> "Bad Request"
        401 -> "Unauthorized"
        404 -> "Not Found"
        405 -> "Method Not Allowed"
        500 -> "Internal Server Error"
        else -> "OK"
    }

    private class HttpRequest(
        val method: String,
        val path: String,
        val headers: Map<String, String>,
        val body: String,
    )
}
