package com.acepanel.client.data

import kotlinx.coroutines.Job
import kotlinx.coroutines.delay

actual suspend fun openDirectSsh(config: DirectSshConfig): TerminalChannel {
    throw UnsupportedOperationException("iOS 直连 SSH 需要集成 libssh2；当前版本请使用面板的 SSH 主机或 Android/桌面客户端。")
}
