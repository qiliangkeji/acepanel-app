package com.acepanel.client.mcp

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.security.SecureRandom

/**
 * 客户端内置 MCP 桥的启动入口。
 *
 * 桌面端在 `main()` 里调用，Android 端在 `MainActivity.onCreate()` 里调用。
 * 服务只监听回环地址（可用 ACEPANEL_MCP_HOST 显式放开），并且强制 Bearer token。
 *
 * 开关与端口：
 * - `-Dacepanel.mcp=0` 或环境变量 `ACEPANEL_MCP=0` 关闭（默认开启）
 * - `-Dacepanel.mcp.port=18799` / `ACEPANEL_MCP_PORT` 指定端口
 * - `-Dacepanel.mcp.host=127.0.0.1` / `ACEPANEL_MCP_HOST` 指定监听地址
 * - `-Dacepanel.mcp.token=...` / `ACEPANEL_MCP_TOKEN` 直接指定 token（否则读写 token 文件）
 */
object McpBridge {
    const val DEFAULT_PORT = 18799
    private const val DEFAULT_HOST = "127.0.0.1"

    @Volatile
    private var server: McpHttpServer? = null

    @Volatile
    var port: Int = 0
        private set

    @Volatile
    var token: String = ""
        private set

    /**
     * 按当前平台配置启动。tokenFile 建议：
     * - 桌面：`~/.acepanel/mcp-token.txt`
     * - Android：`context.filesDir/mcp-token.txt`
     *
     * 返回 false 表示被配置关闭或端口被占用（日志里会打印原因）。
     */
    fun startDefault(tokenFile: File, portOverride: Int? = null): Boolean {
        if (!enabled()) {
            println("[McpBridge] disabled (ACEPANEL_MCP=0)")
            return false
        }
        val resolvedPort = portOverride ?: configuredPort() ?: DEFAULT_PORT
        return start(configuredHost(), resolvedPort, readOrCreateToken(tokenFile))
    }

    @Synchronized
    fun start(host: String, port: Int, token: String): Boolean {
        if (server != null) return true

        val registry = McpToolRegistry()
        val instance = McpHttpServer(host, port, token) { _, path, body ->
            if (path == "/health") {
                val store = McpBridgeHost.store
                HttpResult(
                    200,
                    buildJsonObject {
                        put("ok", true)
                        put("service", "acepanel-client-bridge")
                        put("port", port)
                        put("clientRunning", store != null)
                        put("loggedIn", store?.api != null)
                    }.toString(),
                )
            } else {
                registry.handle(body)
            }
        }

        return try {
            instance.start()
            server = instance
            this.port = instance.boundPort
            this.token = token
            true
        } catch (t: Throwable) {
            println("[McpBridge] failed to start on $host:$port - ${t.message}")
            false
        }
    }

    @Synchronized
    fun stop() {
        server?.stop()
        server = null
    }

    fun isRunning(): Boolean = server != null

    /** 读取或生成 token；文件不可写时退化为临时 token（重启会变）。 */
    fun readOrCreateToken(file: File): String {
        val configured = System.getProperty("acepanel.mcp.token")
            ?: System.getenv("ACEPANEL_MCP_TOKEN")
        if (!configured.isNullOrBlank()) return configured.trim()

        return try {
            if (file.isFile) {
                val existing = file.readText().trim()
                if (existing.isNotBlank()) return existing
            }
            file.parentFile?.mkdirs()
            val generated = generateToken()
            file.writeText(generated)
            // 尽量收紧到「仅当前用户可读写」，失败不影响功能。
            runCatching {
                file.setReadable(false, false)
                file.setReadable(true, true)
                file.setWritable(false, false)
                file.setWritable(true, true)
            }
            generated
        } catch (t: Throwable) {
            println("[McpBridge] token file unavailable (${file.path}): ${t.message}; using ephemeral token")
            generateToken()
        }
    }

    private fun enabled(): Boolean {
        val raw = System.getProperty("acepanel.mcp") ?: System.getenv("ACEPANEL_MCP") ?: return true
        return raw != "0" && !raw.equals("false", ignoreCase = true)
    }

    private fun configuredPort(): Int? =
        System.getProperty("acepanel.mcp.port")?.toIntOrNull()
            ?: System.getenv("ACEPANEL_MCP_PORT")?.toIntOrNull()

    private fun configuredHost(): String =
        System.getProperty("acepanel.mcp.host")
            ?: System.getenv("ACEPANEL_MCP_HOST")
            ?: DEFAULT_HOST

    private fun generateToken(): String {
        val bytes = ByteArray(24)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it.toInt() and 0xFF) }
    }
}
