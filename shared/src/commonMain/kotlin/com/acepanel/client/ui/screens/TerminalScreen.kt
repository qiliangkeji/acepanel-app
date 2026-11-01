package com.acepanel.client.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.acepanel.client.data.AceStore
import com.acepanel.client.data.DirectSshConfig
import com.acepanel.client.data.openDirectSsh
import com.acepanel.client.data.PageKey
import com.acepanel.client.data.RootTab
import com.acepanel.client.data.SshTerminalTarget
import com.acepanel.client.data.TerminalChannel
import com.acepanel.client.data.TerminalConnection
import com.acepanel.client.ui.components.AceIcon
import com.acepanel.client.ui.components.AceText
import com.acepanel.client.ui.components.CircleAction
import com.acepanel.client.ui.components.ProgressLine
import com.acepanel.client.ui.components.SoftPillButton
import com.acepanel.client.ui.components.StatusPill
import com.acepanel.client.ui.theme.AceTheme
import com.acepanel.client.ui.theme.AceType
import com.composables.icons.lucide.ArrowDown
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.ArrowUp
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.CircleHelp
import com.composables.icons.lucide.Copy
import com.composables.icons.lucide.Download
import com.composables.icons.lucide.EllipsisVertical
import com.composables.icons.lucide.FolderClosed
import com.composables.icons.lucide.KeyRound
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Maximize2
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.RotateCw
import com.composables.icons.lucide.SendHorizontal
import com.composables.icons.lucide.SquareTerminal
import com.composables.icons.lucide.Trash2
import com.composables.icons.lucide.X
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min
import com.acepanel.client.data.runSafely

private sealed interface TermStatus {
    data object Connecting : TermStatus
    data object Connected : TermStatus
    data class Disconnected(val reason: String? = null) : TermStatus
}

private class TerminalBuffer {
    var text by mutableStateOf("")
        private set
    private val builder = StringBuilder()
    private var lineStart = 0
    private var cursor = 0

    fun feed(raw: String) {
        if (raw.isEmpty()) return
        val cleaned = raw
            .replace(Regex("\u001B\\][^\u0007]*\u0007"), "")
            .replace(Regex("\u001B\\[[0-?]*[ -/]*[@-~]"), "")
            .replace(Regex("\u001B[=>]"), "")
        for (ch in cleaned) {
            when (ch) {
                '\r' -> cursor = lineStart
                '\n' -> {
                    // trim overwritten tail of the current line
                    if (cursor < builder.length) builder.setLength(cursor)
                    builder.append('\n')
                    cursor = builder.length
                    lineStart = cursor
                }
                '\b' -> if (cursor > lineStart) {
                    builder.deleteCharAt(cursor - 1)
                    cursor--
                }
                '\u0007' -> Unit
                else -> {
                    if (cursor < builder.length && cursor >= lineStart) {
                        builder.setCharAt(cursor, ch)
                    } else {
                        if (cursor > builder.length) builder.append(" ".repeat(cursor - builder.length))
                        builder.append(ch)
                    }
                    cursor++
                }
            }
        }
        if (builder.length > 120_000) {
            val cut = builder.length - 100_000
            builder.delete(0, cut)
            cursor = (cursor - cut).coerceAtLeast(0)
            lineStart = (lineStart - cut).coerceAtLeast(0)
        }
        text = builder.toString()
    }

    fun clear() {
        builder.clear()
        cursor = 0
        lineStart = 0
        text = ""
    }
}

/** 会话时长按 hh:mm:ss 展示，连接中随时间走，断开后停在断开那一刻 */
private fun formatSessionDuration(millis: Long): String {
    val total = (millis / 1000).coerceAtLeast(0L)
    return listOf(total / 3600, (total / 60) % 60, total % 60).joinToString(":") {
        it.toString().padStart(2, '0')
    }
}

/**
 * 自动重连的退避间隔：连不上时（面板不支持 WebSocket、地址错了）
 * 固定 3 秒会一直空转，这里按 3/6/12/30/60 秒退避，超过次数就停下来等用户手动重试。
 */
private const val MAX_AUTO_RECONNECTS = 5

private fun reconnectDelaySeconds(attempt: Int): Int = when (attempt) {
    1 -> 3
    2 -> 6
    3 -> 12
    4 -> 30
    else -> 60
}

private class TerminalSession(
    val key: String,
    val title: String,
    val hostId: Long?,
    val subtitle: String = "",
) {
    val buffer = TerminalBuffer()
    var status by mutableStateOf<TermStatus>(TermStatus.Connecting)
    var connection by mutableStateOf<TerminalChannel?>(null)
    var readJob: Job? = null
    var startedAt by mutableStateOf(com.acepanel.client.data.nowMillis())
    var endedAt by mutableStateOf(0L)

    /** 断开卡片里的时长：连接中实时计算，断开后固定 */
    fun durationText(): String {
        val end = if (status is TermStatus.Connected) com.acepanel.client.data.nowMillis()
        else endedAt.takeIf { it > 0L } ?: com.acepanel.client.data.nowMillis()
        return formatSessionDuration(end - startedAt)
    }

    fun markDisconnected(reason: String? = null) {
        endedAt = com.acepanel.client.data.nowMillis()
        status = TermStatus.Disconnected(reason)
    }

    fun disconnect() {
        connection?.let { c ->
            kotlinx.coroutines.MainScope().launch { c.close() }
        }
        connection = null
        readJob = null
        markDisconnected()
    }
}

@Composable
fun TerminalTabScreen(store: AceStore) {
    val scope = rememberCoroutineScope()
    val colors = AceTheme.colors
    var activeHostId by remember { mutableStateOf<Long?>(null) }
    var picker by remember { mutableStateOf(false) }
    var helpOpen by remember { mutableStateOf(false) }
    var expandInfo by remember { mutableStateOf(false) }
    val host = store.sshHosts.firstOrNull { it.id == activeHostId }
    val sessionKey = "local-${store.activeProfileId}-${activeHostId ?: "local"}"
    val session = remember(sessionKey) {
        TerminalSession(
            key = sessionKey,
            title = host?.name ?: "本地终端",
            hostId = activeHostId,
            subtitle = if (host != null) "${host.config.user}@${host.host}" else "",
        )
    }

    fun connect() {
        val client = store.api ?: return
        session.status = TermStatus.Connecting
        session.startedAt = com.acepanel.client.data.nowMillis()
        session.endedAt = 0L
        session.buffer.feed("\u001B[2m正在建立安全通道…\u001B[0m\r\n")
        scope.launch {
            runSafely {
                val c = if (session.hostId == null) client.openPty() else client.openSshTerminal(session.hostId)
                session.connection = c
                session.startedAt = com.acepanel.client.data.nowMillis()
                c.resize(80, 24)
                session.readJob = c.start(
                    onOutput = { session.buffer.feed(it) },
                    onClosed = { reason ->
                        if (session.status is TermStatus.Connected) {
                            session.markDisconnected(reason)
                            session.buffer.feed("\r\n\u001B[31m会话已断开。${reason ?: ""}\u001B[0m\r\n")
                        }
                    },
                )
                session.status = TermStatus.Connected
            }.onFailure {
                session.markDisconnected(store.friendlyError(it))
                session.buffer.feed("\r\n\u001B[31m连接失败：${store.friendlyError(it)}\u001B[0m\r\n")
            }
        }
    }

    LaunchedEffect(sessionKey) { connect() }
    DisposableEffect(sessionKey) {
        onDispose {
            session.connection?.let { c -> scope.launch { c.close() } }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.terminal)
            .windowInsetsPadding(WindowInsets.statusBars)
            .imePadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable { store.selectedTab = RootTab.SERVERS },
                contentAlignment = Alignment.Center,
            ) {
                AceIcon(Lucide.ArrowLeft, tint = Color.White, size = 20.dp)
            }
            Column(modifier = Modifier.weight(1f)) {
                AceText(
                    store.activeProfile?.name ?: store.systemInfo?.hostname ?: "终端",
                    style = AceType.listStrong.copy(fontSize = 16.sp),
                    color = Color.White,
                    maxLines = 1,
                )
                AceText(
                    if (host != null) "${host.config.user}@${host.host}" else "root@${store.activeProfile?.baseUrl?.removePrefix("https://")?.removePrefix("http://")?.substringBefore('/') ?: "localhost"}",
                    style = AceType.caption.copy(fontFamily = FontFamily.Monospace),
                    color = colors.terminalDim,
                    maxLines = 1,
                )
            }
            StatusPill(
                when (session.status) {
                    TermStatus.Connecting -> "连接中"
                    TermStatus.Connected -> "已连接"
                    is TermStatus.Disconnected -> "已断开"
                },
                when (session.status) {
                    TermStatus.Connected -> colors.terminalGreen
                    TermStatus.Connecting -> colors.warning
                    is TermStatus.Disconnected -> colors.error
                },
                when (session.status) {
                    TermStatus.Connected -> colors.terminalGreen.copy(alpha = 0.14f)
                    TermStatus.Connecting -> colors.warning.copy(alpha = 0.14f)
                    is TermStatus.Disconnected -> colors.error.copy(alpha = 0.14f)
                },
            )
            CircleAction(Lucide.Maximize2, { expandInfo = true }, background = Color.Transparent, tint = colors.terminalDim, size = 30.dp, iconSize = 17.dp)
            CircleAction(Lucide.CircleHelp, { helpOpen = true }, background = Color.Transparent, tint = colors.terminalDim, size = 30.dp, iconSize = 17.dp)
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TermTab("本地", activeHostId == null) { activeHostId = null }
            if (host != null) {
                TermTab(host.name, true, closable = true) { activeHostId = null }
            }
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.08f))
                    .clickable { picker = true },
                contentAlignment = Alignment.Center,
            ) {
                AceIcon(Lucide.Plus, tint = colors.terminalText, size = 16.dp)
            }
        }

        Spacer(Modifier.height(10.dp))

        if (session.status is TermStatus.Disconnected) {
            val reason = (session.status as TermStatus.Disconnected).reason
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.error.copy(alpha = 0.14f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AceIcon(Lucide.X, tint = colors.error, size = 14.dp)
                AceText(
                    reason?.takeIf { it.isNotBlank() } ?: "连接已断开",
                    style = AceType.caption,
                    color = colors.error,
                    modifier = Modifier.weight(1f),
                    // 断开原因经常是一整句（“无法建立 WebSocket 终端连接，请确认面板版本 ≥ …”），
                    // 单行会被省略号截成看不懂的半句话，这里放开到两行。
                    maxLines = 2,
                )
                AceText(
                    "重连",
                    style = AceType.caption.copy(fontWeight = FontWeight.Medium),
                    color = colors.error,
                    modifier = Modifier.clickable { connect() },
                )
            }
            Spacer(Modifier.height(8.dp))
        }

        val scroll = rememberScrollState()
        LaunchedEffect(session.buffer.text) {
            scroll.scrollTo(scroll.maxValue)
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
                .verticalScroll(scroll),
        ) {
            Text(
                text = session.buffer.text.ifBlank {
                    when (session.status) {
                        TermStatus.Connecting -> "正在建立会话通道…"
                        is TermStatus.Disconnected -> "连接已断开，请点击重连继续。"
                        TermStatus.Connected -> "终端已就绪"
                    }
                },
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = colors.terminalText,
                ),
            )
        }

        QuickKeys(onKey = { key ->
            val c = session.connection
            if (c != null) scope.launch { c.send(key) }
        })

        TerminalInputBar(
            enabled = session.status is TermStatus.Connected,
            placeholder = when (session.status) {
                TermStatus.Connecting -> "正在连接…"
                is TermStatus.Disconnected -> "连接已断开，点击重连后继续输入"
                TermStatus.Connected -> "输入命令，Enter 发送"
            },
            onSend = { command ->
                val c = session.connection
                if (c != null) {
                    scope.launch { c.send(command + "\r") }
                    session.buffer.feed(command + "\r\n")
                }
            },
        )

        TerminalToolbar(
            onCopy = { session.buffer.text },
            onClear = { session.buffer.clear() },
            connected = session.status is TermStatus.Connected,
            onDisconnect = {
                session.connection?.let { c -> scope.launch { c.close() } }
                session.connection = null
                session.status = TermStatus.Disconnected("已主动断开连接")
            },
            onFiles = { store.openPage(PageKey.FILES) },
            onKeys = { store.selectedTab = RootTab.SERVERS },
        )
    }

    if (picker) {
        SshPickerDialog(store, onDismiss = { picker = false }) { id ->
            picker = false
            activeHostId = id
        }
    }
    if (helpOpen) {
        com.acepanel.client.ui.components.AceDialog(title = "终端快捷键", onDismiss = { helpOpen = false }) {
            TerminalHelpText()
            Spacer(Modifier.height(14.dp))
            com.acepanel.client.ui.components.PrimaryPillButton("知道了", { helpOpen = false }, height = 42.dp)
        }
    }
    if (expandInfo) {
        com.acepanel.client.ui.components.AceDialog(title = "终端窗口", onDismiss = { expandInfo = false }) {
            AceText("终端会自动铺满当前窗口并跟随尺寸调整。桌面端可拖动窗口边角获得更大视野；移动端使用系统手势切换全屏。", style = AceType.bodySmall, color = AceTheme.colors.text2)
            Spacer(Modifier.height(14.dp))
            com.acepanel.client.ui.components.PrimaryPillButton("知道了", { expandInfo = false }, height = 42.dp)
        }
    }
}

@Composable
fun SshTerminalScreen(store: AceStore, target: SshTerminalTarget) {
    val scope = rememberCoroutineScope()
    val colors = AceTheme.colors
    val clipboard = LocalClipboardManager.current
    val direct: DirectSshConfig? = target.directConfig
    val host = store.sshHosts.firstOrNull { it.id == target.hostId }
    val displayHost = direct?.host ?: host?.host ?: target.subtitle
    val displayPort = direct?.port ?: host?.port ?: 22
    val displayUser = direct?.username ?: host?.config?.user ?: "root"
    val authMethod = direct?.authMethod ?: host?.config?.authMethod ?: "password"
    val session = remember(target) {
        TerminalSession(
            key = "ssh-${target.hostId}-${direct?.host ?: ""}${if (target.pty) "-pty" else ""}",
            title = target.name,
            hostId = target.hostId,
            subtitle = target.subtitle,
        )
    }
    var reconnectSeconds by remember { mutableStateOf<Int?>(null) }
    var reconnectAttempts by remember { mutableStateOf(0) }
    var autoReconnectPaused by remember { mutableStateOf(false) }
    var userClosed by remember { mutableStateOf(false) }
    var helpOpen by remember { mutableStateOf(false) }
    var infoOpen by remember { mutableStateOf(false) }
    var installOpen by remember { mutableStateOf(false) }
    // 会话时长要每秒跳动：连接期间挂一个 1 秒心跳，断开后自动停掉，不空转
    var sessionTick by remember { mutableStateOf(0L) }
    // 连接中的进度条按真实耗时推进，不写死百分比
    var connectTick by remember { mutableStateOf(0) }
    LaunchedEffect(session.status) {
        when (session.status) {
            is TermStatus.Connecting -> {
                connectTick = 0
                while (session.status is TermStatus.Connecting) {
                    delay(100)
                    connectTick += 1
                }
            }
            is TermStatus.Connected -> {
                while (session.status is TermStatus.Connected) {
                    delay(1000)
                    sessionTick += 1
                }
            }
            else -> Unit
        }
    }

    /**
     * 断开后安排下一次自动重连。连不上（面板不支持 WebSocket / 地址失效）时
     * 固定 3 秒会一直空转耗电，这里 3→6→12→30→60 秒退避，超过上限就暂停等用户手动重试。
     */
    fun scheduleReconnect() {
        if (!store.autoReconnect) return
        reconnectAttempts += 1
        if (reconnectAttempts > MAX_AUTO_RECONNECTS) {
            autoReconnectPaused = true
            reconnectSeconds = null
            session.buffer.feed("已暂停自动重连，点击“立即重连”可手动重试。\r\n")
            return
        }
        autoReconnectPaused = false
        reconnectSeconds = reconnectDelaySeconds(reconnectAttempts)
    }

    fun connect() {
        val client = if (direct == null) (store.api ?: return) else null
        userClosed = false
        session.status = TermStatus.Connecting
        session.startedAt = com.acepanel.client.data.nowMillis()
        session.endedAt = 0L
        session.buffer.clear()
        if (target.pty) {
            session.buffer.feed("正在打开面板本机终端 …\r\n")
        } else {
            session.buffer.feed("Connecting to $displayHost port $displayPort ...\r\n")
        }
        scope.launch {
            runSafely {
                val c = when {
                    direct != null -> openDirectSsh(direct)
                    // 文件系统的"终端"用面板自己的 PTY，不需要额外的 SSH 主机配置
                    target.pty -> client!!.openPty()
                    else -> client!!.openSshTerminal(target.hostId)
                }
                session.connection = c
                session.startedAt = com.acepanel.client.data.nowMillis()
                c.resize(80, 24)
                session.status = TermStatus.Connected
                c.start(
                    onOutput = { session.buffer.feed(it) },
                    onClosed = { reason ->
                        if (!userClosed && session.status !is TermStatus.Disconnected) {
                            session.markDisconnected(reason)
                            session.buffer.feed("\r\nConnection to $displayHost closed.${reason?.let { " $it" } ?: ""}\r\n")
                            scheduleReconnect()
                        }
                    },
                )
                session.buffer.feed("\r\n")
                // 连上了就清零，下次掉线重新从 3 秒开始退避
                reconnectAttempts = 0
                autoReconnectPaused = false
                session.status = TermStatus.Connected
                if (direct != null) {
                    session.buffer.feed("已通过 SSH 直连 $displayUser@$displayHost。\r\n")
                    session.buffer.feed("输入命令开始操作；点击右下角“安装面板”可执行 AcePanel 官方安装脚本。\r\n\r\n")
                }
                if (target.initialCommand.isNotBlank()) {
                    // 等 shell 提示符就绪再注入，否则首条命令可能被吞
                    delay(600)
                    c.send(target.initialCommand + "\r")
                }
            }.onFailure {
                session.markDisconnected(store.friendlyError(it))
                session.buffer.feed("✗ ${store.friendlyError(it)}\r\n")
                scheduleReconnect()
            }
        }
    }

    LaunchedEffect(target) { connect() }
    LaunchedEffect(reconnectSeconds) {
        val seconds = reconnectSeconds ?: return@LaunchedEffect
        if (seconds <= 0) {
            reconnectSeconds = null
            connect()
        } else {
            delay(1000)
            reconnectSeconds = seconds - 1
        }
    }
    DisposableEffect(target.hostId) {
        onDispose {
            userClosed = true
            session.connection?.let { c -> scope.launch { c.close() } }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.terminal)
            .windowInsetsPadding(WindowInsets.statusBars)
            .imePadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier.size(32.dp).clip(CircleShape).clickable { store.closeSshTerminal() },
                contentAlignment = Alignment.Center,
            ) {
                AceIcon(Lucide.ArrowLeft, tint = Color.White, size = 20.dp)
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    AceText(target.name, style = AceType.listStrong.copy(fontSize = 16.sp), color = Color.White, maxLines = 1)
                    Box(
                        Modifier.clip(RoundedCornerShape(999.dp)).background(colors.accent.copy(alpha = 0.18f)).padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        AceText(
                            when {
                                target.pty -> "本机"
                                direct != null -> "直连"
                                else -> "生产"
                            },
                            style = AceType.caption.copy(fontSize = 10.sp),
                            color = colors.terminalBlue,
                        )
                    }
                }
                AceText(
                    when {
                        target.pty -> "面板 shell · ${target.ptyDir.ifBlank { "当前目录" }}"
                        direct != null -> "ssh $displayUser@$displayHost -p $displayPort"
                        else -> "ssh -i ~/.ssh/id_ed25519 $displayUser@$displayHost"
                    },
                    style = AceType.caption.copy(fontFamily = FontFamily.Monospace, fontSize = 10.5.sp),
                    color = colors.terminalDim,
                    maxLines = 1,
                )
            }
            StatusPill(
                when (session.status) {
                    TermStatus.Connected -> "已连接"
                    TermStatus.Connecting -> "连接中"
                    is TermStatus.Disconnected -> "已断开"
                },
                when (session.status) {
                    TermStatus.Connected -> colors.terminalGreen
                    TermStatus.Connecting -> colors.warning
                    is TermStatus.Disconnected -> colors.error
                },
                when (session.status) {
                    TermStatus.Connected -> colors.terminalGreen.copy(alpha = 0.14f)
                    TermStatus.Connecting -> colors.warning.copy(alpha = 0.14f)
                    is TermStatus.Disconnected -> colors.error.copy(alpha = 0.14f)
                },
            )
            CircleAction(Lucide.Maximize2, { infoOpen = true }, background = Color.Transparent, tint = colors.terminalDim, size = 28.dp, iconSize = 16.dp)
            CircleAction(Lucide.EllipsisVertical, { infoOpen = true }, background = Color.Transparent, tint = colors.terminalDim, size = 28.dp, iconSize = 16.dp)
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            MetaItem("认证", if (authMethod == "publickey") "公钥" else "密码")
            MetaItem("加密", "curve25519")
            // 会话时长：连接中每秒跳动，断开后停在断开那一刻
            sessionTick
            MetaItem(
                "会话",
                if (session.status is TermStatus.Connected || session.status is TermStatus.Disconnected)
                    session.durationText()
                else "—",
            )
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.07f)))

        val scroll = rememberScrollState()
        LaunchedEffect(session.buffer.text) { scroll.scrollTo(scroll.maxValue) }
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .verticalScroll(scroll),
        ) {
            Column {
                Text(
                    text = session.buffer.text,
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.5.sp,
                        lineHeight = 17.sp,
                        color = colors.terminalText,
                    ),
                )
                if (session.status is TermStatus.Connecting) {
                    Spacer(Modifier.height(12.dp))
                    AceText("正在建立会话通道…", style = AceType.caption.copy(fontFamily = FontFamily.Monospace), color = colors.terminalBlue)
                    Spacer(Modifier.height(7.dp))
                    // 握手通常在 1 秒内完成，进度按耗时平滑推进到 95% 等待结果，不给假的"完成度"
                    ProgressLine(
                        (6.0 + connectTick * 1.4).coerceAtMost(95.0),
                        colors.accent,
                        height = 2.dp,
                        track = Color.White.copy(alpha = 0.12f),
                    )
                    Spacer(Modifier.height(5.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        AceText(
                            if (target.pty) "面板 shell · ${displayHost}" else "ssh $displayUser@$displayHost",
                            style = AceType.caption.copy(fontFamily = FontFamily.Monospace, fontSize = 10.sp),
                            color = colors.terminalDim,
                            maxLines = 1,
                            modifier = Modifier.weight(1f),
                        )
                        AceText(
                            "已用 ${connectTick / 10}.${connectTick % 10}s",
                            style = AceType.caption.copy(fontFamily = FontFamily.Monospace, fontSize = 10.sp),
                            color = colors.terminalDim,
                        )
                    }
                }
                if (session.status is TermStatus.Disconnected) {
                    Spacer(Modifier.height(12.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.error.copy(alpha = 0.12f))
                            .border(1.dp, colors.error.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            AceIcon(Lucide.X, tint = colors.error, size = 14.dp)
                            AceText(
                                if (target.pty) "面板终端已断开" else "连接已断开",
                                style = AceType.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                color = colors.error,
                                modifier = Modifier.weight(1f),
                            )
                            // 这次会话实际持续了多久（断开瞬间定格），之前是写死的 00:12:31
                            AceText(session.durationText(), style = AceType.caption.copy(fontFamily = FontFamily.Monospace, fontSize = 10.sp), color = colors.terminalDim)
                        }
                        val failureReason = (session.status as TermStatus.Disconnected).reason
                        AceText(
                            buildString {
                                append(
                                    failureReason?.takeIf { it.isNotBlank() }
                                        ?: if (target.pty) "面板 shell 连接已中断" else "SSH 会话已中断",
                                )
                                when {
                                    autoReconnectPaused -> append("；已暂停自动重连，可手动重试")
                                    store.autoReconnect && reconnectSeconds != null ->
                                        append("；${reconnectSeconds} 秒后自动重连（第 $reconnectAttempts 次）")
                                }
                            },
                            style = AceType.caption,
                            color = colors.terminalDim,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(colors.accent)
                                    .clickable {
                                        // 手动重试重新从 3 秒退避开始，不沿用之前累计的次数
                                        reconnectSeconds = null
                                        reconnectAttempts = 0
                                        autoReconnectPaused = false
                                        connect()
                                    }
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                            ) {
                                AceText("立即重连", style = AceType.caption.copy(fontWeight = FontWeight.Medium), color = Color.White)
                            }
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.White.copy(alpha = 0.08f))
                                    .clickable { store.closeSshTerminal() }
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                            ) {
                                AceText("返回主机", style = AceType.caption, color = colors.terminalText)
                            }
                        }
                    }
                }
            }
        }

        QuickKeys(onKey = { key ->
            session.connection?.let { c -> scope.launch { c.send(key) } }
        })
        TerminalInputBar(
            enabled = session.status is TermStatus.Connected,
            placeholder = when {
                session.status is TermStatus.Connected -> "输入命令，或下方快捷键"
                session.status is TermStatus.Connecting -> "连接成功后可输入命令"
                else -> "连接已断开，重连后可继续输入"
            },
            onSend = { command ->
                session.connection?.let { c -> scope.launch { c.send(command + "\r") } }
                session.buffer.feed(command + "\r\n")
            },
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            TerminalIcon(Lucide.FolderClosed) {
                if (direct == null) store.openPage(PageKey.FILES)
                else store.toast("安装并登录面板后可使用文件管理")
            }
            TerminalIcon(Lucide.KeyRound) {
                if (direct == null) store.selectedTab = RootTab.SERVERS
                else store.toast("安装并登录面板后可使用主机密钥管理")
            }
            TerminalIcon(Lucide.RotateCw) { connect() }
            TerminalIcon(Lucide.Maximize2) { helpOpen = true }
            Spacer(Modifier.weight(1f))
            if (direct != null) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(colors.accent.copy(alpha = 0.20f))
                        .clickable { installOpen = true }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    AceIcon(Lucide.Download, tint = colors.terminalBlue, size = 13.dp)
                    AceText("安装面板", style = AceType.caption.copy(fontWeight = FontWeight.Medium), color = colors.terminalBlue)
                }
            }
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(colors.error.copy(alpha = 0.18f))
                    .clickable {
                        userClosed = true
                        session.connection?.let { c -> scope.launch { c.close() } }
                        session.connection = null
                        session.status = TermStatus.Disconnected("已断开连接")
                    }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                AceIcon(Lucide.X, tint = colors.error, size = 13.dp)
                AceText("断开连接", style = AceType.caption.copy(fontWeight = FontWeight.Medium), color = colors.error)
            }
        }
    }
    if (helpOpen) {
        com.acepanel.client.ui.components.AceDialog(title = "SSH 快捷键与提示", onDismiss = { helpOpen = false }) {
            TerminalHelpText()
            Spacer(Modifier.height(14.dp))
            com.acepanel.client.ui.components.PrimaryPillButton("知道了", { helpOpen = false }, height = 42.dp)
        }
    }
    if (infoOpen) {
        com.acepanel.client.ui.components.AceDialog(title = "会话信息", onDismiss = { infoOpen = false }) {
            val command = when {
                target.pty -> ""
                direct != null -> "ssh $displayUser@$displayHost -p $displayPort"
                else -> "ssh -i ~/.ssh/id_ed25519 $displayUser@$displayHost -p $displayPort"
            }
            if (target.pty) {
                AceText("面板主机 ${target.subtitle.substringBeforeLast(" · ")}", style = AceType.caption, color = colors.text3)
                Spacer(Modifier.height(6.dp))
                AceText("工作目录 ${target.ptyDir.ifBlank { "—" }}", style = AceType.caption, color = colors.text3)
                Spacer(Modifier.height(10.dp))
                AceText(
                    "浏览器里直接打开面板进程的一个 shell，不经过 SSH，也不需要主机记录。",
                    style = AceType.bodySmall,
                    color = colors.text2,
                )
            } else {
                AceText("主机 $displayHost", style = AceType.caption, color = colors.text3)
                Spacer(Modifier.height(6.dp))
                AceText(
                    "用户 $displayUser · 端口 $displayPort · ${if (authMethod == "publickey") "公钥认证" else "密码认证"}",
                    style = AceType.caption,
                    color = colors.text3,
                )
                Spacer(Modifier.height(10.dp))
                AceText(command, style = AceType.caption.copy(fontFamily = FontFamily.Monospace), color = colors.text1)
                Spacer(Modifier.height(14.dp))
                com.acepanel.client.ui.components.SoftPillButton(
                    "复制 SSH 命令",
                    onClick = { clipboard.setText(AnnotatedString(command)); store.toast("SSH 命令已复制") },
                    height = 42.dp,
                    background = colors.surface2,
                )
            }
            Spacer(Modifier.height(10.dp))
            com.acepanel.client.ui.components.PrimaryPillButton("关闭", { infoOpen = false }, height = 42.dp)
        }
    }
    if (installOpen) {
        com.acepanel.client.ui.components.AceDialog(title = "安装 AcePanel", onDismiss = { installOpen = false }) {
            AceText("将在 $displayHost 上以当前用户 $displayUser 执行 AcePanel 官方一键安装脚本：", style = AceType.bodySmall, color = colors.text2)
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(colors.surface2).padding(10.dp),
            ) {
                AceText(
                    "bash <(curl -sSLm 10 https://dl.acepanel.net/helper.sh)",
                    style = AceType.caption.copy(fontFamily = FontFamily.Monospace),
                    color = colors.text1,
                )
            }
            Spacer(Modifier.height(10.dp))
            AceText(
                "请确认服务器为受支持的干净 Linux 系统，并具备 root 权限与外网访问。安装完成后会输出面板地址、账号和入口信息。",
                style = AceType.caption,
                color = colors.text3,
            )
            Spacer(Modifier.height(14.dp))
            com.acepanel.client.ui.components.PrimaryPillButton("执行安装", {
                installOpen = false
                val connection = session.connection
                if (session.status !is TermStatus.Connected || connection == null) {
                    store.toast("SSH 尚未连接，请先重连", true)
                } else {
                    scope.launch { connection.send("bash <(curl -sSLm 10 https://dl.acepanel.net/helper.sh)\r") }
                    session.buffer.feed("bash <(curl -sSLm 10 https://dl.acepanel.net/helper.sh)\r\n")
                }
            }, height = 44.dp)
            Spacer(Modifier.height(8.dp))
            com.acepanel.client.ui.components.SoftPillButton(
                "复制安装命令",
                onClick = {
                    clipboard.setText(AnnotatedString("bash <(curl -sSLm 10 https://dl.acepanel.net/helper.sh)"))
                    store.toast("安装命令已复制")
                },
                height = 42.dp,
                background = colors.surface2,
            )
        }
    }
}

@Composable
private fun MetaItem(label: String, value: String) {
    val colors = AceTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        AceText(label, style = AceType.caption.copy(fontSize = 10.sp), color = colors.terminalDim)
        AceText(value, style = AceType.caption.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp), color = colors.terminalText)
    }
}

@Composable
private fun TermTab(label: String, active: Boolean, closable: Boolean = false, onClick: () -> Unit) {
    val colors = AceTheme.colors
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(if (active) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.05f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        AceIcon(Lucide.SquareTerminal, tint = if (active) colors.terminalText else colors.terminalDim, size = 13.dp)
        AceText(label, style = AceType.caption.copy(fontSize = 12.sp), color = if (active) colors.terminalText else colors.terminalDim, maxLines = 1)
        if (closable) {
            AceIcon(Lucide.X, tint = colors.terminalDim, size = 12.dp)
        }
    }
}

@Composable
private fun QuickKeys(onKey: (String) -> Unit) {
    val colors = AceTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        QuickKey("Esc") { onKey("\u001B") }
        QuickKey("Tab") { onKey("\t") }
        QuickKey("Ctrl") { onKey("\u0003") }
        QuickKey("Alt") { onKey("\u001B") }
        QuickKey("↑") { onKey("\u001B[A") }
        QuickKey("↓") { onKey("\u001B[B") }
        QuickKey("←") { onKey("\u001B[D") }
        QuickKey("→") { onKey("\u001B[C") }
    }
}

@Composable
private fun QuickKey(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .heightIn(min = 32.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = 0.09f))
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        AceText(label, style = AceType.caption.copy(fontSize = 11.5.sp), color = AceTheme.colors.terminalText)
    }
}

@Composable
private fun TerminalInputBar(
    enabled: Boolean,
    placeholder: String,
    onSend: (String) -> Unit,
) {
    val colors = AceTheme.colors
    var value by remember { mutableStateOf("") }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF23252A))
            .padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AceText("$", style = AceType.caption.copy(fontFamily = FontFamily.Monospace, fontSize = 13.sp), color = colors.terminalGreen)
        BasicTextField(
            value = value,
            onValueChange = { value = it },
            modifier = Modifier.weight(1f),
            enabled = enabled,
            singleLine = true,
            textStyle = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                color = colors.terminalText,
            ),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(colors.terminalBlue),
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Send,
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
            ),
            keyboardActions = KeyboardActions(onSend = {
                if (value.isNotBlank()) {
                    onSend(value)
                    value = ""
                }
            }),
            decorationBox = { inner ->
                if (value.isEmpty()) {
                    AceText(placeholder, style = AceType.caption.copy(fontSize = 10.5.sp), color = colors.terminalDim)
                } else {
                    inner()
                }
            },
        )
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (enabled) colors.accent else Color.White.copy(alpha = 0.06f))
                .clickable(enabled = enabled) {
                    if (value.isNotBlank()) {
                        onSend(value)
                        value = ""
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            AceIcon(Lucide.SendHorizontal, tint = if (enabled) Color.White else colors.terminalDim, size = 16.dp)
        }
    }
}

@Composable
private fun TerminalToolbar(
    onCopy: () -> String,
    onClear: () -> Unit,
    connected: Boolean,
    onDisconnect: () -> Unit,
    onFiles: () -> Unit,
    onKeys: () -> Unit,
) {
    val colors = AceTheme.colors
    val clipboard = LocalClipboardManager.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 14.dp, end = 14.dp, top = 2.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        TerminalIcon(Lucide.FolderClosed) { onFiles() }
        TerminalIcon(Lucide.KeyRound) { onKeys() }
        TerminalIcon(Lucide.Copy) {
            val text = onCopy()
            if (text.isNotBlank()) clipboard.setText(AnnotatedString(text))
        }
        Spacer(Modifier.weight(1f))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(Color.White.copy(alpha = 0.08f))
                .clickable(onClick = onClear)
                .padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            AceText("清屏", style = AceType.caption.copy(fontWeight = FontWeight.Medium), color = colors.terminalText)
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(colors.error.copy(alpha = 0.18f))
                .clickable(onClick = onDisconnect)
                .padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            AceText(if (connected) "断开连接" else "重新连接", style = AceType.caption.copy(fontWeight = FontWeight.Medium), color = colors.error)
        }
    }
}

@Composable
private fun TerminalIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(30.dp).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        AceIcon(icon, tint = AceTheme.colors.terminalDim, size = 18.dp)
    }
}

@Composable
private fun SshPickerDialog(
    store: AceStore,
    onDismiss: () -> Unit,
    onSelect: (Long?) -> Unit,
) {
    val colors = AceTheme.colors
    com.acepanel.client.ui.components.AceDialog(title = "选择终端会话", onDismiss = onDismiss) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { onSelect(null) }
                .padding(horizontal = 10.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AceIcon(Lucide.SquareTerminal, tint = colors.accent, size = 18.dp)
            Column(Modifier.weight(1f)) {
                AceText("本地终端", style = AceType.listPrimary, color = colors.text1)
                AceText("在面板服务器上执行命令", style = AceType.caption, color = colors.text3)
            }
        }
        if (store.sshHosts.isEmpty()) {
            AceText("暂无 SSH 主机，请在「服务器」中添加", style = AceType.caption, color = colors.text3, modifier = Modifier.padding(10.dp))
        }
        store.sshHosts.forEach { host ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onSelect(host.id) }
                    .padding(horizontal = 10.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AceIcon(Lucide.SquareTerminal, tint = colors.text1, size = 18.dp)
                Column(Modifier.weight(1f)) {
                    AceText(host.name.ifBlank { host.host }, style = AceType.listPrimary, color = colors.text1)
                    AceText("${host.config.user}@${host.host}:${host.port}", style = AceType.caption.copy(fontFamily = FontFamily.Monospace), color = colors.text3)
                }
            }
        }
    }
}


@Composable
private fun TerminalHelpText() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HelpRow("Esc", "发送 ESC 键，退出当前交互")
        HelpRow("Tab", "命令补全")
        HelpRow("Ctrl", "发送 Ctrl+C，中断当前命令")
        HelpRow("Alt", "发送 Alt 组合前缀")
        HelpRow("↑ / ↓", "浏览历史命令")
        HelpRow("长按输出", "选择并复制终端文本")
    }
}

@Composable
private fun HelpRow(key: String, desc: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            Modifier.clip(RoundedCornerShape(6.dp)).background(AceTheme.colors.fill).padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            AceText(key, style = AceType.caption.copy(fontFamily = FontFamily.Monospace), color = AceTheme.colors.text1)
        }
        AceText(desc, style = AceType.caption, color = AceTheme.colors.text2)
    }
}
