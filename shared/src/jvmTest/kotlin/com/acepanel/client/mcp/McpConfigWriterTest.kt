package com.acepanel.client.mcp

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class McpConfigWriterTest {

    private val dir = Files.createTempDirectory("mcp-config").toFile()
    private val file = File(dir, "mcp.json")

    @Test
    fun mergesEntryAndPreservesOtherServers() {
        file.writeText("""{"mcpServers":{"other":{"command":"x"}},"foo":1}""")

        assertTrue(McpConfigWriter.writeWorkBuddyConfig(file, 18799, "abc"))

        val root = Json.parseToJsonElement(file.readText()).jsonObject
        val servers = root["mcpServers"]!!.jsonObject
        assertEquals("x", servers["other"]!!.jsonObject["command"]!!.jsonPrimitive.content)

        val entry = servers["acepanel-client"]!!.jsonObject
        assertEquals("http", entry["type"]!!.jsonPrimitive.content)
        assertEquals("http://127.0.0.1:18799/mcp", entry["url"]!!.jsonPrimitive.content)
        assertEquals("Bearer abc", entry["headers"]!!.jsonObject["Authorization"]!!.jsonPrimitive.content)
        assertEquals(1, root["foo"]!!.jsonPrimitive.int)
    }

    @Test
    fun secondWriteIsNoOpButChangedPortRewrites() {
        file.writeText("""{"mcpServers":{}}""")

        assertTrue(McpConfigWriter.writeWorkBuddyConfig(file, 18799, "abc"))
        assertFalse(McpConfigWriter.writeWorkBuddyConfig(file, 18799, "abc"))
        assertTrue(McpConfigWriter.writeWorkBuddyConfig(file, 18800, "xyz"))
    }

    @Test
    fun writesStdioEntryAndNeutralConfig() {
        file.writeText("""{"mcpServers":{}}""")
        assertTrue(McpConfigWriter.writeStdioConfig(file, "/tmp/acepanel-mcp"))

        val root = Json.parseToJsonElement(file.readText()).jsonObject
        val stdio = root["mcpServers"]!!.jsonObject["acepanel"]!!.jsonObject
        assertEquals("/tmp/acepanel-mcp", stdio["command"]!!.jsonPrimitive.content)

        val neutral = File(dir, "mcp-config.json")
        McpConfigWriter.writeNeutralConfig(neutral, 18799, "tok", "/tmp/acepanel-mcp")
        val parsed = Json.parseToJsonElement(neutral.readText()).jsonObject
        assertEquals(
            "http://127.0.0.1:18799/mcp",
            parsed["http"]!!.jsonObject["url"]!!.jsonPrimitive.content,
        )
        assertEquals(
            "/tmp/acepanel-mcp",
            parsed["mcpServers"]!!.jsonObject["acepanel"]!!.jsonObject["command"]!!.jsonPrimitive.content,
        )
    }

    @AfterTest
    fun cleanup() {
        dir.deleteRecursively()
    }
}
