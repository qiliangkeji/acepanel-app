package com.acepanel.client.data

import kotlinx.coroutines.Job

/**
 * 终端通道抽象：面板 WebSocket（PTY/SSH）与客户端直连 SSH 共用同一套终端 UI。
 */
interface TerminalChannel {
    fun start(
        onOutput: (String) -> Unit,
        onClosed: (String?) -> Unit = {},
    ): Job

    suspend fun send(text: String)
    suspend fun resize(columns: Int, rows: Int)
    suspend fun close()
}

data class DirectSshConfig(
    val name: String = "",
    val host: String = "",
    val port: Int = 22,
    val username: String = "root",
    val authMethod: String = "password",
    val password: String = "",
    val privateKey: String = "",
    val passphrase: String = "",
)

expect suspend fun openDirectSsh(config: DirectSshConfig): TerminalChannel
