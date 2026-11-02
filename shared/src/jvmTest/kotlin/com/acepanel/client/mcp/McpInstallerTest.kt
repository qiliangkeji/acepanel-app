package com.acepanel.client.mcp

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class McpInstallerTest {

    @Test
    fun embeddedProxyResourcesExist() {
        val names = listOf(
            "acepanel-mcp-windows-amd64.exe",
            "acepanel-mcp-windows-arm64.exe",
            "acepanel-mcp-darwin-amd64",
            "acepanel-mcp-darwin-arm64",
            "acepanel-mcp-linux-amd64",
            "acepanel-mcp-linux-arm64",
        )
        for (name in names) {
            val stream = McpInstaller::class.java.getResourceAsStream("/mcp/$name")
            assertNotNull(stream, "missing resource /mcp/$name")
            stream.use {
                assertTrue(it.available() > 1_000_000, "resource too small: $name")
            }
        }
    }
}
