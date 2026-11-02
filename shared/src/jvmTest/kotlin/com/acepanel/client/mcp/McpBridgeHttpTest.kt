package com.acepanel.client.mcp

import java.net.Socket
import kotlin.test.Test
import kotlin.test.assertTrue

class McpBridgeHttpTest {

    @Test
    fun initializeListToolsAndRejectBadToken() {
        val token = "test-token"
        assertTrue(McpBridge.start("127.0.0.1", 0, token), "bridge should start")
        try {
            val port = McpBridge.port

            val init = post(
                port,
                token,
                """{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"test","version":"1"}}}""",
            )
            assertTrue(init.contains("200 OK"), init)
            assertTrue(init.contains("acepanel-client-bridge"), init)
            assertTrue(init.contains("2025-06-18"), init)

            val list = post(port, token, """{"jsonrpc":"2.0","id":2,"method":"tools/list"}""")
            assertTrue(list.contains("ace_status"), list)
            assertTrue(list.contains("ace_raw_request"), list)
            assertTrue(list.contains("ace_list_websites"), list)
            assertTrue(list.contains("ace_container_action"), list)
            assertTrue(list.contains("ui_open_page"), list)
            assertTrue(list.contains("ui_switch_tab"), list)

            val unauthorized = post(port, "wrong-token", """{"jsonrpc":"2.0","id":3,"method":"ping"}""")
            assertTrue(unauthorized.contains("401"), unauthorized)

            val call = post(
                port,
                token,
                """{"jsonrpc":"2.0","id":4,"method":"tools/call","params":{"name":"ace_status","arguments":{}}}""",
            )
            assertTrue(call.contains("clientRunning"), call)
            assertTrue(call.contains("false"), call)
        } finally {
            McpBridge.stop()
        }
    }

    private fun post(port: Int, token: String, body: String): String {
        Socket("127.0.0.1", port).use { socket ->
            socket.soTimeout = 10_000
            val bytes = body.toByteArray(Charsets.UTF_8)
            val headers = buildString {
                append("POST /mcp HTTP/1.1\r\n")
                append("Host: 127.0.0.1\r\n")
                append("Authorization: Bearer ").append(token).append("\r\n")
                append("Content-Type: application/json\r\n")
                append("Content-Length: ").append(bytes.size).append("\r\n")
                append("Connection: close\r\n\r\n")
            }
            socket.getOutputStream().apply {
                write(headers.toByteArray(Charsets.UTF_8))
                write(bytes)
                flush()
            }
            return socket.getInputStream().readBytes().toString(Charsets.UTF_8)
        }
    }
}
