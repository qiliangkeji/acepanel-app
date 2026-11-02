package com.acepanel.client.mcp

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.io.File

/**
 * 把本机 MCP 桥自动登记到常见 MCP 客户端。
 *
 * - `acepanel-client`：HTTP 条目（支持 HTTP 的客户端直接连 18799）；
 * - `acepanel`：stdio 条目（指向随客户端释放的 acepanel-mcp 代理，所有客户端通用）。
 *
 * 只改这两个 key，其它 server 与未知字段原样保留；内容一致时不写盘。
 * 只处理「配置目录已经存在」的客户端，没装的不会凭空创建。
 */
internal object McpConfigWriter {
    private const val HTTP_SERVER_NAME = "acepanel-client"
    private const val STDIO_SERVER_NAME = "acepanel"

    fun writeWorkBuddyConfig(file: File, port: Int, token: String): Boolean =
        mergeEntry(file, HTTP_SERVER_NAME, httpEntry(port, token))

    fun writeStdioConfig(file: File, command: String): Boolean =
        mergeEntry(file, STDIO_SERVER_NAME, stdioEntry(command))

    /** 通用配置：stdio 条目 + HTTP 端点，放在 ~/.acepanel/mcp-config.json，任何客户端都能参考。 */
    fun writeNeutralConfig(file: File, port: Int, token: String, command: String?) {
        val root = buildJsonObject {
            put(
                "mcpServers",
                buildJsonObject {
                    put(STDIO_SERVER_NAME, stdioEntry(command ?: "acepanel-mcp"))
                },
            )
            put(
                "http",
                buildJsonObject {
                    put("url", "http://127.0.0.1:$port/mcp")
                    put(
                        "headers",
                        buildJsonObject { put("Authorization", "Bearer $token") },
                    )
                },
            )
        }
        write(file, root)
    }

    fun writeDetectedClientConfigs(command: String, port: Int, token: String) {
        val home = System.getProperty("user.home")?.takeIf { it.isNotBlank() } ?: return
        val os = System.getProperty("os.name").orEmpty().lowercase()
        val appData = System.getenv("APPDATA").orEmpty()

        val candidates = buildList {
            add(Triple(File(home, ".workbuddy/mcp.json"), true, "WorkBuddy"))
            add(
                Triple(
                    if (os.contains("win")) {
                        File(appData, "Claude/claude_desktop_config.json")
                    } else {
                        File(home, "Library/Application Support/Claude/claude_desktop_config.json")
                    },
                    false,
                    "Claude Desktop",
                ),
            )
            add(Triple(File(home, ".cursor/mcp.json"), false, "Cursor"))
            add(
                Triple(
                    if (os.contains("win")) {
                        File(appData, "Code/User/globalStorage/saoudrizwan.claude-dev/settings/cline_mcp_settings.json")
                    } else {
                        File(
                            home,
                            "Library/Application Support/Code/User/globalStorage/saoudrizwan.claude-dev/settings/cline_mcp_settings.json",
                        )
                    },
                    false,
                    "Cline",
                ),
            )
            add(Triple(File(home, ".codeium/windsurf/mcp_config.json"), false, "Windsurf"))
        }

        for ((file, isWorkBuddy, label) in candidates) {
            val parent = file.parentFile ?: continue
            if (!parent.isDirectory) continue
            runCatching {
                if (isWorkBuddy) writeWorkBuddyConfig(file, port, token)
                writeStdioConfig(file, command)
                println("[McpBridge] MCP config updated for $label: ${file.path}")
            }.onFailure {
                println("[McpBridge] skip $label (${file.path}): ${it.message}")
            }
        }
    }

    private fun httpEntry(port: Int, token: String): JsonObject = buildJsonObject {
        put("type", "http")
        put("url", "http://127.0.0.1:$port/mcp")
        put("headers", buildJsonObject { put("Authorization", "Bearer $token") })
    }

    private fun stdioEntry(command: String): JsonObject = buildJsonObject {
        put("command", command)
        put("args", JsonArray(emptyList()))
    }

    private fun mergeEntry(file: File, name: String, entry: JsonObject): Boolean {
        val root = readRoot(file)
        val servers = (root["mcpServers"] as? JsonObject)?.toMutableMap() ?: mutableMapOf()
        if (servers[name] == entry) return false

        servers[name] = entry
        val updated = JsonObject(root.toMutableMap().apply { put("mcpServers", JsonObject(servers)) })
        write(file, updated)
        return true
    }

    private fun readRoot(file: File): JsonObject {
        if (!file.isFile) return buildJsonObject {}
        val text = file.readText().removePrefix("\uFEFF").trim()
        if (text.isEmpty()) return buildJsonObject {}
        return Json.parseToJsonElement(text).jsonObject
    }

    private fun write(file: File, root: JsonObject) {
        val content = prettyJson.encodeToString(JsonObject.serializer(), root)
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, "${file.name}.tmp")
        temp.writeText(content)
        if (temp.renameTo(file)) return

        // Windows 下目标已存在时 renameTo 会失败，退化为删旧再改名，最后兜底直接覆盖。
        if (file.delete() && temp.renameTo(file)) return
        file.writeText(content)
        temp.delete()
    }

    private val prettyJson = Json {
        prettyPrint = true
        encodeDefaults = true
    }
}
