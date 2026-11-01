package com.acepanel.client.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.acepanel.client.data.AceStore
import com.acepanel.client.data.RootTab
import com.acepanel.client.data.ServerProfile
import com.acepanel.client.data.SshHost
import com.acepanel.client.data.timeAgo
import com.acepanel.client.ui.components.AceCard
import com.acepanel.client.ui.components.AceDialog
import com.acepanel.client.ui.components.AceIcon
import com.acepanel.client.ui.components.AceText
import com.acepanel.client.ui.components.AceTextField
import com.acepanel.client.ui.components.CircleAction
import com.acepanel.client.ui.components.EmptyState
import com.acepanel.client.ui.components.SoftPillButton
import com.acepanel.client.ui.components.StatusPill
import com.acepanel.client.ui.components.rememberScreenScrollState
import com.acepanel.client.ui.theme.AceDimens
import com.acepanel.client.ui.theme.AceTheme
import com.acepanel.client.ui.theme.AceType
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Gauge
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.RefreshCw
import com.composables.icons.lucide.ScanLine
import com.composables.icons.lucide.Search
import com.composables.icons.lucide.Server
import com.composables.icons.lucide.TriangleAlert
import kotlinx.coroutines.launch
import com.acepanel.client.data.runSafely

private enum class ServerFilter(val label: String) {
    ALL("全部"),
    PANEL("面板"),
    SSH("SSH"),
    OFFLINE("离线"),
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ServersScreen(store: AceStore) {
    val colors = AceTheme.colors
    var filter by remember { mutableStateOf(ServerFilter.ALL) }
    var query by remember { mutableStateOf("") }
    var deleteProfile by remember { mutableStateOf<ServerProfile?>(null) }
    var deleteSsh by remember { mutableStateOf<SshHost?>(null) }
    var scanning by remember { mutableStateOf(false) }
    var scanValue by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    LaunchedEffect(store.api) { store.loadSshHosts() }

    val onlineCount = store.profiles.count { it.online || it.id == store.activeProfileId }
    val allCount = store.profiles.size + store.sshHosts.size
    val offlineCount = store.profiles.count { !it.online }

    val filteredProfiles = store.profiles.filter { p ->
        val matches = query.isBlank() || p.name.contains(query, true) ||
            p.baseUrl.contains(query, true) || p.username.contains(query, true) ||
            p.group.contains(query, true)
        matches && when (filter) {
            ServerFilter.ALL, ServerFilter.PANEL -> true
            ServerFilter.SSH -> false
            ServerFilter.OFFLINE -> !p.online
        }
    }
    val filteredSsh = store.sshHosts.filter { h ->
        val matches = query.isBlank() || h.name.contains(query, true) ||
            h.host.contains(query, true) || h.remark.contains(query, true) ||
            h.config.user.contains(query, true)
        matches && (filter == ServerFilter.ALL || filter == ServerFilter.SSH)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 26.dp, end = 26.dp, top = 4.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                AceText("服务器", style = AceType.pageTitle, color = colors.text1)
                Spacer(Modifier.height(4.dp))
                AceText("$allCount 台主机 · $onlineCount 台在线", style = AceType.secondary, color = colors.text3)
            }
            CircleAction(Lucide.Plus, store::openAddServer, background = colors.accent, tint = Color.White, contentDescription = "添加服务器")
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScreenScrollState())
                .padding(start = 26.dp, end = 26.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AceTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = "搜索名称、IP 或标签",
                height = 46.dp,
                leading = { AceIcon(Lucide.Search, tint = colors.text3, size = 18.dp) },
                trailing = {
                    Box(
                        Modifier.size(32.dp).clickable { scanning = true },
                        contentAlignment = Alignment.Center,
                    ) {
                        AceIcon(Lucide.ScanLine, tint = colors.text3, size = 18.dp)
                    }
                },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(26.dp)) {
                TabChip(ServerFilter.ALL, allCount, filter) { filter = it }
                TabChip(ServerFilter.PANEL, store.profiles.size, filter) { filter = it }
                TabChip(ServerFilter.SSH, store.sshHosts.size, filter) { filter = it }
                TabChip(ServerFilter.OFFLINE, offlineCount, filter) { filter = it }
            }
            if (filteredProfiles.isEmpty() && filteredSsh.isEmpty()) {
                EmptyState(
                    title = if (query.isNotBlank()) "没有匹配的服务器" else "还没有添加服务器",
                    subtitle = if (query.isNotBlank()) "换一个名称、IP 或标签试试" else "添加 AcePanel 面板或 SSH 主机，开始托管你的服务器",
                    icon = Lucide.Server,
                    actionText = if (query.isBlank()) "添加服务器" else null,
                    onAction = store::openAddServer,
                )
            }
            filteredProfiles.forEach { profile ->
                PanelServerCard(
                    profile = profile,
                    active = profile.id == store.activeProfileId,
                    onConnect = { store.switchProfile(profile) },
                    onLongClick = { deleteProfile = profile },
                )
            }
            filteredSsh.forEach { host ->
                SshHostCard(
                    host = host,
                    online = store.activeProfile != null,
                    onConnect = { store.openSshTerminal(host.id, host.name, "${host.config.user}@${host.host}") },
                    onLongClick = { deleteSsh = host },
                )
            }
            if (store.profiles.isNotEmpty() || store.sshHosts.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { store.loadSshHosts() }
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AceIcon(Lucide.RefreshCw, tint = colors.text3, size = 15.dp)
                    Spacer(Modifier.width(7.dp))
                    AceText("刷新状态", style = AceType.caption, color = colors.text3)
                }
            }
            Spacer(Modifier.height(4.dp))
            AceText(
                "长按主机卡片可移除。面板、SSH 密钥与主机配置均保存在当前设备。",
                style = AceType.caption,
                color = colors.text3,
            )
        }
    }

    deleteProfile?.let { profile ->
        AceDialog(title = "移除服务器", onDismiss = { deleteProfile = null }) {
            AceText(
                "将从本机移除「${profile.name.ifBlank { profile.baseUrl }}」，不会删除面板或服务器数据。",
                style = AceType.bodySmall,
                color = colors.text2,
            )
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SoftPillButton("取消", { deleteProfile = null }, modifier = Modifier.weight(1f), height = 44.dp, background = colors.fill)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(colors.error)
                        .clickable {
                            store.removeProfile(profile)
                            deleteProfile = null
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    AceText("移除", style = AceType.button.copy(fontSize = 14.sp), color = Color.White)
                }
            }
        }
    }
    deleteSsh?.let { host ->
        AceDialog(title = "删除 SSH 主机", onDismiss = { deleteSsh = null }) {
            AceText(
                "将在面板中删除「${host.name}」及其保存的凭据，此操作不可撤销。",
                style = AceType.bodySmall,
                color = colors.text2,
            )
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SoftPillButton("取消", { deleteSsh = null }, modifier = Modifier.weight(1f), height = 44.dp, background = colors.fill)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(colors.error)
                        .clickable {
                            val h = host
                            deleteSsh = null
                            scope.launch {
                                runSafely { store.api?.deleteSshHost(h.id) }
                                    .onSuccess {
                                        store.toast("已删除 ${h.name}")
                                        store.loadSshHosts()
                                    }
                                    .onFailure { store.toast(store.friendlyError(it), true) }
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    AceText("删除", style = AceType.button.copy(fontSize = 14.sp), color = Color.White)
                }
            }
        }
    }
    if (scanning) {
        AceDialog(title = "扫码 / 粘贴地址", onDismiss = { scanning = false }) {
            AceText("将面板地址粘贴到下方，或使用扫码结果，随后继续填写登录凭据。", style = AceType.bodySmall, color = colors.text2)
            Spacer(Modifier.height(12.dp))
            AceTextField(
                value = scanValue,
                onValueChange = { scanValue = it },
                placeholder = "https://1.2.3.4:8443",
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SoftPillButton("取消", { scanning = false }, modifier = Modifier.weight(1f), height = 44.dp, background = colors.fill)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (scanValue.isBlank()) colors.fill else colors.accent)
                        .clickable(enabled = scanValue.isNotBlank()) {
                            store.loginAddress = scanValue.trim()
                            scanning = false
                            store.openAddServer()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    AceText("继续添加", style = AceType.button.copy(fontSize = 14.sp), color = if (scanValue.isBlank()) colors.text3 else Color.White)
                }
            }
        }
    }
}

@Composable
private fun TabChip(
    value: ServerFilter,
    count: Int,
    selected: ServerFilter,
    onSelect: (ServerFilter) -> Unit,
) {
    val colors = AceTheme.colors
    val active = selected == value
    Column(
        modifier = Modifier.clickable { onSelect(value) },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            AceText(
                value.label,
                style = AceType.tab.copy(fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal),
                color = if (active) colors.text1 else colors.text2,
            )
            AceText(
                count.toString(),
                style = AceType.caption,
                color = if (active) colors.accent else colors.text3,
            )
        }
        Box(
            Modifier
                .width(if (active) 22.dp else 0.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(colors.accent),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PanelServerCard(
    profile: ServerProfile,
    active: Boolean,
    onConnect: () -> Unit,
    onLongClick: () -> Unit,
) {
    val colors = AceTheme.colors
    val online = profile.online || active
    val latency = profile.latencyMs
    val latencyColor = when {
        latency == null -> colors.text3
        latency < 60 -> colors.success
        latency < 150 -> colors.warning
        else -> colors.error
    }
    val latencyBg = when {
        latency == null -> colors.fill
        latency < 60 -> colors.successSoft
        latency < 150 -> colors.warningSoft
        else -> colors.errorSoft
    }
    val addr = profile.baseUrl
        .removePrefix("https://")
        .removePrefix("http://")
        .trimEnd('/')
    AceCard(
        modifier = Modifier.combinedClickable(onClick = onConnect, onLongClick = onLongClick),
        padding = 0.dp,
    ) {
        Column(Modifier.padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(if (online) colors.success else colors.text3))
                    AceText(
                        profile.name.ifBlank { profile.hostname.ifBlank { addr } },
                        style = AceType.listStrong,
                        color = colors.text1,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        // 主机名不限宽时会先把同行的“当前”徽标挤出卡片（Row 先测量不带 weight 的子项）
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (active) {
                        Box(
                            Modifier.clip(RoundedCornerShape(999.dp)).background(colors.accentSoft).padding(horizontal = 7.dp, vertical = 2.dp),
                        ) {
                            AceText("当前", style = AceType.caption.copy(fontSize = 10.sp), color = colors.accent)
                        }
                    }
                }
                Row(
                    modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(latencyBg).padding(horizontal = 9.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    AceIcon(Lucide.Gauge, tint = latencyColor, size = 12.dp)
                    AceText(
                        latency?.let { "${it}ms" } ?: "—",
                        style = AceType.tag.copy(fontSize = 11.sp),
                        color = latencyColor,
                    )
                }
            }
            AceText(
                "$addr  ${profile.username}${if (profile.lastConnectedAt > 0) " · ${timeAgo(profile.lastConnectedAt)}" else ""}",
                style = AceType.secondary.copy(fontFamily = FontFamily.Monospace),
                color = colors.text2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Row(modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (profile.panelVersion.isNotBlank()) MiniTag("面板 v${profile.panelVersion}")
                    val os = profile.osName.split(" ").take(2).joinToString(" ").take(18)
                    if (os.isNotBlank()) MiniTag(os)
                    if (profile.entrance.isNotBlank()) MiniTag("入口 ${profile.entrance}")
                    if (!online) MiniTag("离线")
                }
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (online) colors.accent else colors.fill)
                        .clickable(onClick = onConnect)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    AceText(if (online) "连接" else "登录", style = AceType.bodySmall.copy(fontWeight = FontWeight.Medium), color = if (online) Color.White else colors.text2)
                    AceIcon(Lucide.ChevronRight, tint = if (online) Color.White else colors.text2, size = 14.dp)
                }
            }
        }
    }
}

@Composable
private fun SshHostCard(
    host: SshHost,
    online: Boolean,
    onConnect: () -> Unit,
    onLongClick: () -> Unit,
) {
    val colors = AceTheme.colors
    AceCard(
        modifier = Modifier.combinedClickable(onClick = onConnect, onLongClick = onLongClick),
        padding = 0.dp,
    ) {
        Column(Modifier.padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(colors.text3))
                    AceText(host.name.ifBlank { host.host }, style = AceType.listStrong, color = colors.text1, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Row(
                    modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(colors.warningSoft).padding(horizontal = 9.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    AceIcon(Lucide.TriangleAlert, tint = colors.warning, size = 12.dp)
                    AceText("SSH", style = AceType.tag.copy(fontSize = 11.sp), color = colors.warning)
                }
            }
            AceText(
                "${host.host} ${host.config.user} · :${host.port}",
                style = AceType.secondary.copy(fontFamily = FontFamily.Monospace),
                color = colors.text2,
                maxLines = 1,
            )
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Row(modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    MiniTag(if (host.config.authMethod == "publickey") "SSH 密钥" else "密码登录")
                    if (host.remark.isNotBlank()) MiniTag(host.remark)
                }
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(colors.accent)
                        .clickable(onClick = onConnect)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    AceText("连接", style = AceType.bodySmall.copy(fontWeight = FontWeight.Medium), color = Color.White)
                    AceIcon(Lucide.ChevronRight, tint = Color.White, size = 14.dp)
                }
            }
        }
    }
}

@Composable
private fun MiniTag(text: String) {
    Box(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(AceTheme.colors.fill)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        AceText(text, style = AceType.caption, color = AceTheme.colors.text2, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
