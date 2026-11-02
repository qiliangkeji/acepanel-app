package com.acepanel.client.mcp

import com.acepanel.client.data.AceStore
import kotlin.concurrent.Volatile

/**
 * 客户端内的 MCP 桥接点。
 *
 * [com.acepanel.client.App] 创建 [AceStore] 时把它登记到这里；平台侧的
 * MCP 桥（桌面 / Android）通过它拿到**已经登录**的 AceStore / AceApi，
 * 从而复用客户端现有会话，不需要 agent 重新登录面板。
 */
object McpBridgeHost {
    @Volatile
    var store: AceStore? = null
}
