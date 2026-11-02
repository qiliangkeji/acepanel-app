package com.acepanel.client.mcp

import com.acepanel.client.platform.appDataDir
import com.acepanel.client.platform.platformName
import java.io.File

/**
 * 把随 App 打包的 acepanel-mcp 代理释放到用户数据目录，供任意 MCP 客户端拉起。
 *
 * 桌面端资源位于 shared 的 jvmMain resources：/mcp/acepanel-mcp-<os>-<arch>[.exe]。
 * Android 不打包这些资源，[ensureProxy] 直接返回 null。
 */
internal object McpInstaller {
    fun ensureProxy(): File? {
        if (platformName() != "Desktop") return null

        val os = System.getProperty("os.name").orEmpty().lowercase()
        val arch = System.getProperty("os.arch").orEmpty().lowercase()
        val isArm = arch.contains("aarch64") || arch.contains("arm64")

        val resourceName = when {
            os.contains("win") -> if (isArm) "acepanel-mcp-windows-arm64.exe" else "acepanel-mcp-windows-amd64.exe"
            os.contains("mac") -> if (isArm) "acepanel-mcp-darwin-arm64" else "acepanel-mcp-darwin-amd64"
            else -> if (isArm) "acepanel-mcp-linux-arm64" else "acepanel-mcp-linux-amd64"
        }
        val targetName = if (os.contains("win")) "acepanel-mcp.exe" else "acepanel-mcp"
        val target = File(appDataDir(), targetName)

        val stream = McpInstaller::class.java.getResourceAsStream("/mcp/$resourceName")
            ?: return target.takeIf { it.isFile }

        return try {
            val expectedSize = stream.available().toLong()
            if (target.isFile && expectedSize > 0 && target.length() == expectedSize) {
                stream.close()
                target.setExecutable(true)
                return target
            }

            target.parentFile?.mkdirs()
            val temp = File(target.parentFile, "$targetName.tmp")
            stream.use { input ->
                temp.outputStream().use { output -> input.copyTo(output) }
            }
            if (!temp.renameTo(target)) {
                target.delete()
                if (!temp.renameTo(target)) {
                    target.writeBytes(temp.readBytes())
                    temp.delete()
                }
            }
            target.setExecutable(true)
            println("[McpInstaller] proxy ready: ${target.path}")
            target
        } catch (t: Throwable) {
            println("[McpInstaller] extract failed: ${t.message}")
            target.takeIf { it.isFile }
        }
    }
}
