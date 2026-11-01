package com.acepanel.client.ui.screens

import androidx.compose.foundation.background
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
import com.acepanel.client.data.ContainerItem
import com.acepanel.client.data.containerStateLabel
import com.acepanel.client.ui.components.AceCard
import com.acepanel.client.ui.components.AceDialog
import com.acepanel.client.ui.components.AceIcon
import com.acepanel.client.ui.components.AceText
import com.acepanel.client.ui.components.AceTextField
import com.acepanel.client.ui.components.CircleAction
import com.acepanel.client.ui.components.EmptyState
import com.acepanel.client.ui.components.ErrorState
import com.acepanel.client.ui.components.InlineNotice
import com.acepanel.client.ui.components.LoadingState
import com.acepanel.client.ui.components.MobilePageHeader
import com.acepanel.client.ui.components.PrimaryPillButton
import com.acepanel.client.ui.components.SoftPillButton
import com.acepanel.client.ui.components.StatusPill
import com.acepanel.client.ui.components.rememberScreenScrollState
import com.acepanel.client.ui.theme.AceTheme
import com.acepanel.client.ui.theme.AceType
import com.composables.icons.lucide.Box
import com.composables.icons.lucide.Container
import com.composables.icons.lucide.EllipsisVertical
import com.composables.icons.lucide.HardDrive
import com.composables.icons.lucide.Image
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.RotateCw
import com.composables.icons.lucide.Square
import com.composables.icons.lucide.Trash2
import kotlinx.coroutines.launch
import com.acepanel.client.data.runSafely

@Composable
fun ContainersScreen(store: AceStore) {
    val colors = AceTheme.colors
    val scope = rememberCoroutineScope()
    var containers by remember { mutableStateOf<List<ContainerItem>>(emptyList()) }
    var imageCount by remember { mutableStateOf(0) }
    var volumeCount by remember { mutableStateOf(0) }
    var networkCount by remember { mutableStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var manage by remember { mutableStateOf<ContainerItem?>(null) }
    var showCreate by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    suspend fun load() {
        loading = true
        error = null
        runSafely {
            containers = store.api?.containers()?.items ?: emptyList()
            imageCount = store.api?.containerImages()?.total?.toInt() ?: 0
            volumeCount = store.api?.containerVolumes()?.total?.toInt() ?: 0
            networkCount = store.api?.containerNetworks()?.total?.toInt() ?: 0
        }.onFailure { error = store.friendlyError(it) }
        loading = false
    }

    LaunchedEffect(store.api) { load() }

    val running = containers.count { it.running }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        MobilePageHeader(
            title = "容器",
            subtitle = "$running 个运行 · ${containers.size - running} 个已停止",
            onBack = store::closePage,
            actions = {
                CircleAction(Lucide.Plus, { showCreate = true }, background = colors.accent, tint = Color.White, contentDescription = "新建容器")
            },
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScreenScrollState())
                .padding(start = 26.dp, end = 26.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (loading) {
                LoadingState(rows = 5)
            } else if (error != null) {
                ErrorState(error!!, onRetry = { scope.launch { load() } })
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ContainerSummary("容器", containers.size.toString(), Lucide.Container, Modifier.weight(1f))
                    ContainerSummary("镜像", imageCount.toString(), Lucide.Image, Modifier.weight(1f))
                    ContainerSummary("数据卷", volumeCount.toString(), Lucide.HardDrive, Modifier.weight(1f))
                }
                if (containers.isEmpty()) {
                    EmptyState(
                        title = "还没有容器",
                        subtitle = "拉取镜像并创建第一个容器",
                        icon = Lucide.Container,
                        actionText = "新建容器",
                        onAction = { showCreate = true },
                    )
                } else {
                    containers.forEach { container ->
                        ContainerCard(container) { manage = container }
                    }
                }
                if (networkCount > 0) {
                    AceText("网络：$networkCount 个自定义网络", style = AceType.caption, color = colors.text3)
                }
            }
        }
        Box(Modifier.fillMaxWidth().padding(horizontal = 26.dp, vertical = 14.dp)) {
            PrimaryPillButton("新建容器", { showCreate = true }, height = 50.dp, leadingIcon = Lucide.Plus)
        }
    }

    manage?.let { container ->
        ContainerActions(container, { manage = null }, busy, { action ->
            if (busy) return@ContainerActions
            busy = true
            scope.launch {
                runSafely {
                    if (action == "delete") store.api?.deleteContainer(container.id)
                    else store.api?.containerAction(container.id, action)
                }.onSuccess {
                    store.toast("操作已完成")
                    manage = null
                    load()
                }.onFailure { store.toast(store.friendlyError(it), true) }
                busy = false
            }
        })
    }

    if (showCreate) {
        CreateContainerDialog(store, { showCreate = false }) {
            showCreate = false
            scope.launch { load() }
        }
    }
}

@Composable
private fun ContainerSummary(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier) {
    val colors = AceTheme.colors
    AceCard(modifier = modifier, padding = 14.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            AceText(label, style = AceType.caption, color = colors.text3)
            Row(verticalAlignment = Alignment.Bottom) {
                AceText(value, style = AceType.number.copy(fontSize = 21.sp), color = colors.text1)
                Spacer(Modifier.weight(1f))
                AceIcon(icon, tint = colors.text3, size = 15.dp)
            }
        }
    }
}

@Composable
private fun ContainerCard(container: ContainerItem, onManage: () -> Unit) {
    val colors = AceTheme.colors
    val (stateLabel, running) = containerStateLabel(container.state)
    val warning = container.state.contains("restart", true)
    val statusColor = when {
        running -> colors.success
        warning -> colors.warning
        else -> colors.text3
    }
    val statusBg = when {
        running -> colors.successSoft
        warning -> colors.warningSoft
        else -> colors.fill
    }
    AceCard(padding = 16.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AceIcon(Lucide.Container, tint = if (running) colors.accent else colors.text3, size = 20.dp)
                AceText(container.name, style = AceType.listStrong, color = colors.text1, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                StatusPill(stateLabel, statusColor, statusBg)
                Box(Modifier.size(28.dp).clip(CircleShape).clickable(onClick = onManage), contentAlignment = Alignment.Center) {
                    AceIcon(Lucide.EllipsisVertical, tint = colors.text3, size = 16.dp)
                }
            }
            AceText(container.image, style = AceType.caption.copy(fontFamily = FontFamily.Monospace), color = colors.text3, maxLines = 1)
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                AceText(
                    container.shortId.ifBlank { if (running) "运行中" else container.status.ifBlank { "—" } },
                    style = AceType.caption.copy(fontFamily = FontFamily.Monospace),
                    color = colors.text3,
                    modifier = Modifier.weight(1f),
                )
                val ports = container.ports.firstOrNull()?.let { port ->
                    if (port.hostStart > 0) "${port.hostStart} → ${port.containerStart}" else "${port.containerStart}"
                } ?: "—"
                AceText(ports, style = AceType.caption.copy(fontFamily = FontFamily.Monospace), color = colors.text3)
            }
        }
    }
}

@Composable
private fun ContainerActions(
    container: ContainerItem,
    onDismiss: () -> Unit,
    busy: Boolean,
    onAction: (String) -> Unit,
) {
    val colors = AceTheme.colors
    AceDialog(title = container.name, onDismiss = onDismiss) {
        AceText(container.image, style = AceType.caption.copy(fontFamily = FontFamily.Monospace), color = colors.text3)
        Spacer(Modifier.height(14.dp))
        ActionButton(if (container.running) "停止容器" else "启动容器", if (container.running) "stop" else "start", onAction, busy, colors.warning)
        Spacer(Modifier.height(8.dp))
        ActionButton("重启容器", "restart", onAction, busy, colors.accent)
        Spacer(Modifier.height(8.dp))
        ActionButton("删除容器", "delete", onAction, busy, colors.error)
    }
}

@Composable
private fun ActionButton(label: String, action: String, onAction: (String) -> Unit, busy: Boolean, color: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(color.copy(alpha = 0.12f))
            .clickable(enabled = !busy) { onAction(action) },
        contentAlignment = Alignment.Center,
    ) {
        AceText(label, style = AceType.button.copy(fontSize = 14.sp), color = color)
    }
}

@Composable
private fun CreateContainerDialog(store: AceStore, onDismiss: () -> Unit, onCreated: () -> Unit) {
    val colors = AceTheme.colors
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var image by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    AceDialog(title = "新建容器", onDismiss = onDismiss) {
        AceText("容器名称", style = AceType.caption, color = colors.text3)
        Spacer(Modifier.height(6.dp))
        AceTextField(name, { name = it }, placeholder = "例如 my-app")
        Spacer(Modifier.height(12.dp))
        AceText("镜像", style = AceType.caption, color = colors.text3)
        Spacer(Modifier.height(6.dp))
        AceTextField(image, { image = it }, placeholder = "nginx:latest")
        Spacer(Modifier.height(10.dp))
        InlineNotice("创建后会自动发布镜像暴露的端口，并设置 unless-stopped 重启策略。", color = colors.accent, background = colors.accentSoft)
        error?.let {
            Spacer(Modifier.height(10.dp))
            InlineNotice(it, color = colors.error, background = colors.errorSoft)
        }
        Spacer(Modifier.height(16.dp))
        PrimaryPillButton(if (busy) "创建中…" else "创建容器", {
            if (busy) return@PrimaryPillButton
            if (image.isBlank()) {
                error = "请输入镜像名称"
                return@PrimaryPillButton
            }
            busy = true
            scope.launch {
                runSafely { store.api?.createContainer(name.trim().ifBlank { "ace-" + image.substringBefore(':').substringAfterLast('/') }, image.trim()) }
                    .onSuccess {
                        store.toast("容器创建请求已提交")
                        onCreated()
                    }
                    .onFailure { error = store.friendlyError(it) }
                busy = false
            }
        }, height = 46.dp)
    }
}
