package com.acepanel.client.data

import com.jcraft.jsch.ChannelShell
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import com.jcraft.jsch.UserInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStreamReader
import java.util.concurrent.atomic.AtomicBoolean

actual suspend fun openDirectSsh(config: DirectSshConfig): TerminalChannel = withContext(Dispatchers.IO) {
    JschTerminalConnection.connect(config)
}

private class JschTerminalConnection(
    private val session: Session,
    private val channel: ChannelShell,
    private val input: java.io.InputStream,
    private val output: java.io.OutputStream,
) : TerminalChannel {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val uiScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val closed = AtomicBoolean(false)
    private var onClosed: ((String?) -> Unit)? = null

    override fun start(
        onOutput: (String) -> Unit,
        onClosed: (String?) -> Unit,
    ): Job {
        this.onClosed = onClosed
        return scope.launch {
            val reader = InputStreamReader(input, Charsets.UTF_8)
            val buffer = CharArray(4096)
            try {
                while (isActive && !channel.isClosed) {
                    val count = reader.read(buffer)
                    if (count < 0) break
                    if (count > 0) {
                        val text = String(buffer, 0, count)
                        uiScope.launch { onOutput(text) }
                    }
                }
                finish(null)
            } catch (t: Throwable) {
                finish(t.message ?: "SSH 会话已结束")
            }
        }
    }

    private fun finish(reason: String?) {
        if (!closed.compareAndSet(false, true)) return
        val callback = onClosed
        onClosed = null
        uiScope.launch { callback?.invoke(reason); uiScope.cancel() }
        runCatching { channel.disconnect() }
        runCatching { session.disconnect() }
        scope.cancel()
    }

    override suspend fun send(text: String) {
        withContext(Dispatchers.IO) {
            output.write(text.toByteArray(Charsets.UTF_8))
            output.flush()
        }
    }

    override suspend fun resize(columns: Int, rows: Int) {
        runCatching { channel.setPtySize(columns, rows, columns * 8, rows * 17) }
    }

    override suspend fun close() {
        finish("已断开 SSH 连接")
    }

    companion object {
        fun connect(config: DirectSshConfig): JschTerminalConnection {
            require(config.host.isNotBlank()) { "SSH 主机地址不能为空" }
            require(config.username.isNotBlank()) { "SSH 用户名不能为空" }
            val jsch = JSch()
            if (config.authMethod == "publickey") {
                require(config.privateKey.isNotBlank()) { "请填写 SSH 私钥" }
                val passphrase = config.passphrase.takeIf { it.isNotBlank() }?.toByteArray(Charsets.UTF_8)
                jsch.addIdentity(
                    "acepanel-client-key",
                    config.privateKey.toByteArray(Charsets.UTF_8),
                    null,
                    passphrase,
                )
            }
            val session = jsch.getSession(config.username, config.host, config.port)
            if (config.authMethod != "publickey") {
                session.setPassword(config.password)
            }
            session.setConfig("StrictHostKeyChecking", "no")
            session.setConfig("PreferredAuthentications", "publickey,keyboard-interactive,password")
            session.timeout = 15_000
            session.setUserInfo(object : UserInfo {
                override fun getPassphrase(): String = config.passphrase
                override fun getPassword(): String = config.password
                override fun promptPassword(message: String?): Boolean = config.password.isNotEmpty()
                override fun promptPassphrase(message: String?): Boolean = true
                override fun promptYesNo(message: String?): Boolean = true
                override fun showMessage(message: String?) = Unit
            })
            session.connect(15_000)
            val channel = session.openChannel("shell") as ChannelShell
            channel.setPtyType("xterm-256color")
            channel.setPtySize(80, 24, 640, 384)
            // JSch 要求先获取输入/输出流，再连接 channel，否则首屏输出可能丢失
            val input = channel.inputStream
            val output = channel.outputStream
            channel.connect(15_000)
            return JschTerminalConnection(session, channel, input, output)
        }
    }
}
