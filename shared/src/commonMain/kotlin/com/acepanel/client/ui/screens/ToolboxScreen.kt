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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.acepanel.client.data.AceStore
import com.acepanel.client.data.formatBytes
import com.acepanel.client.data.formatSpeed
import com.acepanel.client.data.obj
import com.acepanel.client.data.string
import com.acepanel.client.data.nowMillis
import com.acepanel.client.ui.components.AceCard
import com.acepanel.client.ui.components.AceDialog
import com.acepanel.client.ui.components.AceIcon
import com.acepanel.client.ui.components.AceText
import com.acepanel.client.ui.components.InlineNotice
import com.acepanel.client.ui.components.MobilePageHeader
import com.acepanel.client.ui.components.PrimaryPillButton
import com.acepanel.client.ui.components.rememberScreenScrollState
import com.acepanel.client.ui.theme.AceTheme
import com.acepanel.client.ui.theme.AceType
import com.composables.icons.lucide.Activity
import com.composables.icons.lucide.Bolt
import com.composables.icons.lucide.BookOpen
import com.composables.icons.lucide.Cpu
import com.composables.icons.lucide.Database
import com.composables.icons.lucide.FileText
import com.composables.icons.lucide.HardDrive
import com.composables.icons.lucide.Link
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Network
import com.composables.icons.lucide.Radar
import com.composables.icons.lucide.Rocket
import com.composables.icons.lucide.Server
import com.composables.icons.lucide.Trash2
import com.composables.icons.lucide.Wrench
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import com.acepanel.client.data.runSafely

private data class ToolItem(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val action: ToolAction,
)

private enum class ToolAction { SYSTEM_INFO, PROCESS, DISK_CLEAN, NETWORK, PORT_SCAN, PING, MIGRATION, WEBHOOK, BENCHMARK }

@Composable
fun ToolboxScreen(store: AceStore) {
    val colors = AceTheme.colors
    val scope = rememberCoroutineScope()
    var dialog by remember { mutableStateOf<ToolAction?>(null) }
    var content by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var webhookCount by remember { mutableStateOf(0) }

    val tools = listOf(
        ToolItem("系统信息", "内核 · 负载", Lucide.Cpu, ToolAction.SYSTEM_INFO),
        ToolItem("进程管理", "${store.currentInfo?.topProcesses?.cpu?.size ?: 0} 个高负载进程", Lucide.Activity, ToolAction.PROCESS),
        ToolItem("日志清理", "释放磁盘空间", Lucide.HardDrive, ToolAction.DISK_CLEAN),
        ToolItem("网络状态", "实时吞吐 · 连接", Lucide.Network, ToolAction.NETWORK),
        ToolItem("端口扫描", "扫描感知 Top 端口", Lucide.Radar, ToolAction.PORT_SCAN),
        ToolItem("Ping 测试", "使用终端调试网络", Lucide.Bolt, ToolAction.PING),
        ToolItem("面板迁移", "导入导出 · 迁移", Lucide.Server, ToolAction.MIGRATION),
        ToolItem("WebHook", "${webhookCount} 个回调", Lucide.Link, ToolAction.WEBHOOK),
        ToolItem("基准测试", "综合评分", Lucide.Rocket, ToolAction.BENCHMARK),
    )

    suspend fun openTool(action: ToolAction) {
        dialog = action
        loading = true
        content = ""
        error = null
        val client = store.api
        if (client == null) {
            error = "面板未连接"
            loading = false
            return
        }
        runSafely {
            when (action) {
                ToolAction.SYSTEM_INFO -> {
                    val sys = store.systemInfo
                    val cur = store.currentInfo
                    content = buildString {
                        appendLine("主机名：${sys?.hostname ?: "—"}")
                        appendLine("系统：${sys?.osName ?: "—"}")
                        appendLine("内核：${sys?.kernelVersion ?: "—"} ${sys?.kernelArch ?: ""}")
                        appendLine("运行时长：${sys?.uptime?.let { "${it / 86400} 天 ${(it % 86400) / 3600} 小时" } ?: "—"}")
                        appendLine("CPU：${cur?.cpus?.size ?: 0} 核 · 使用率 ${cur?.percent?.toInt() ?: 0}%")
                        appendLine("内存：${formatBytes(cur?.mem?.used ?: 0)} / ${formatBytes(cur?.mem?.total ?: 0)}")
                        appendLine("负载：${cur?.load?.load1 ?: 0} / ${cur?.load?.load5 ?: 0} / ${cur?.load?.load15 ?: 0}")
                        appendLine("面板版本：${sys?.panelVersion ?: "—"}")
                    }
                }
                ToolAction.PROCESS -> {
                    val tops = store.currentInfo?.topProcesses
                    content = buildString {
                        appendLine("CPU 占用 Top")
                        tops?.cpu?.take(6)?.forEach { appendLine("${it.pid.toString().padEnd(7)} ${it.name.take(18).padEnd(19)} ${it.value}%") }
                        appendLine("\n内存占用 Top")
                        tops?.memory?.take(6)?.forEach { appendLine("${it.pid.toString().padEnd(7)} ${it.name.take(18).padEnd(19)} ${formatBytes(it.value * 1024 * 1024)}") }
                    }.ifBlank { "暂无进程数据" }
                }
                ToolAction.DISK_CLEAN -> {
                    val data = client.getData("/api/toolbox_log/scan", mapOf("type" to "system"))
                    content = formatJsonItems(data)
                }
                ToolAction.NETWORK -> {
                    val cur = store.currentInfo
                    content = buildString {
                        appendLine("网络接口")
                        cur?.net?.forEach { net ->
                            appendLine("${net.name}  发送 ${formatBytes(net.bytesSent)}  接收 ${formatBytes(net.bytesRecv)}")
                        }
                        appendLine("\n磁盘")
                        cur?.diskUsage?.forEach {
                            appendLine("${it.path}  ${formatBytes(it.used)} / ${formatBytes(it.total)}  ${it.usedPercent.toInt()}%")
                        }
                    }.ifBlank { "暂无网络数据" }
                }
                ToolAction.PORT_SCAN -> {
                    val data = client.getData("/api/firewall/scan/top_ports")
                    content = formatJsonItems(data)
                }
                ToolAction.PING -> {
                    content = "Ping 测试依赖服务器 shell 权限。可在「终端」中执行：\n\nping -c 4 example.com\n\n或使用面板工具中的网络诊断。"
                }
                ToolAction.MIGRATION -> {
                    val data = client.getData("/api/toolbox_migration/status")
                    content = data?.toString()?.take(4000) ?: "暂无迁移状态"
                }
                ToolAction.WEBHOOK -> {
                    val data = client.getData("/api/webhook", mapOf("page" to "1", "limit" to "100"))
                    webhookCount = runSafely {
                        val obj = data as? JsonObject
                        (obj?.get("total") as? JsonPrimitive)?.content?.toIntOrNull() ?: 0
                    }.getOrDefault(0)
                    content = "当前 WebHook：$webhookCount 个\n\n请在「自动化与进程 → WebHook」中创建、编辑或删除回调脚本。"
                }
                ToolAction.BENCHMARK -> {
                    content = "基准测试会占用较多 CPU，建议在低峰期运行。\n\n面板会依次执行图像处理、编译模拟、加密、压缩、物理模拟与 JSON 处理等测试，并给出综合评分。\n\n可在「接口中心」搜索 /api/toolbox_benchmark/test，选择测试项后执行。"
                }
            }
        }.onFailure { error = store.friendlyError(it) }
        loading = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg)
            .windowInsetsPadding(WindowInsets.statusBars)
            .verticalScroll(rememberScreenScrollState()),
    ) {
        MobilePageHeader(
            title = "工具箱",
            subtitle = "系统与网络诊断",
            onBack = store::closePage,
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 26.dp, end = 26.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionLabel("系统")
            ToolGrid(tools.subList(0, 3)) { scope.launch { openTool(it) } }
            SectionLabel("网络")
            ToolGrid(tools.subList(3, 6)) { scope.launch { openTool(it) } }
            SectionLabel("数据与运维")
            ToolGrid(tools.subList(6, 9)) { scope.launch { openTool(it) } }
            AceCard(padding = 16.dp) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        modifier = Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).background(colors.fill),
                        contentAlignment = Alignment.Center,
                    ) {
                        AceIcon(Lucide.Server, tint = colors.text1, size = 19.dp)
                    }
                    Column(Modifier.weight(1f)) {
                        AceText("AcePanel v${store.systemInfo?.panelVersion ?: "-"}", style = AceType.listPrimary, color = colors.text1)
                        AceText(
                            "${store.systemInfo?.goVersion?.ifBlank { "Go" } ?: "Go"} · ${store.systemInfo?.osName ?: "Linux"} · 已是最新",
                            style = AceType.caption,
                            color = colors.text3,
                            maxLines = 1,
                        )
                    }
                    AceText("详情", style = AceType.caption.copy(fontWeight = FontWeight.Medium), color = colors.accent, modifier = Modifier.clickable { scope.launch { openTool(ToolAction.SYSTEM_INFO) } })
                }
            }
        }
    }

    dialog?.let { action ->
        AceDialog(
            title = when (action) {
                ToolAction.SYSTEM_INFO -> "系统信息"
                ToolAction.PROCESS -> "进程管理"
                ToolAction.DISK_CLEAN -> "日志清理"
                ToolAction.NETWORK -> "网络与磁盘"
                ToolAction.PORT_SCAN -> "扫描端口排行"
                ToolAction.PING -> "Ping 测试"
                ToolAction.MIGRATION -> "面板迁移"
                ToolAction.WEBHOOK -> "WebHook"
                ToolAction.BENCHMARK -> "基准测试"
            },
            onDismiss = { dialog = null },
        ) {
            if (loading) {
                AceText("正在读取…", style = AceType.bodySmall, color = colors.text3)
            } else if (error != null) {
                InlineNotice(error!!, color = colors.error, background = colors.errorSoft)
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 100.dp, max = 360.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.surface2)
                        .padding(12.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    AceText(content, style = AceType.caption.copy(fontFamily = FontFamily.Monospace, lineHeight = 17.sp), color = colors.text1)
                }
                if (action == ToolAction.DISK_CLEAN) {
                    Spacer(Modifier.height(12.dp))
                    PrimaryPillButton("清理系统日志", {
                        scope.launch {
                            runSafely {
                                store.api?.postData("/api/toolbox_log/clean", buildJsonObject { put("type", "system") })
                            }.onSuccess {
                                store.toast("日志清理完成")
                                openTool(ToolAction.DISK_CLEAN)
                            }.onFailure { store.toast(store.friendlyError(it), true) }
                        }
                    }, height = 44.dp, leadingIcon = Lucide.Trash2)
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    AceText(text, style = AceType.caption, color = AceTheme.colors.text3, modifier = Modifier.padding(start = 4.dp, top = 2.dp))
}

@Composable
private fun ToolGrid(items: List<ToolItem>, onClick: (ToolAction) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { tool ->
                    ToolCard(tool, Modifier.weight(1f)) { onClick(tool.action) }
                }
            }
        }
    }
}

@Composable
private fun ToolCard(tool: ToolItem, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = AceTheme.colors
    AceCard(modifier = modifier.clickable(onClick = onClick), padding = 13.dp) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).background(colors.fill),
                contentAlignment = Alignment.Center,
            ) {
                AceIcon(tool.icon, tint = colors.accent, size = 20.dp)
            }
            AceText(tool.title, style = AceType.bodySmall.copy(fontWeight = FontWeight.Medium), color = colors.text1, maxLines = 1)
            AceText(tool.subtitle, style = AceType.caption.copy(fontSize = 10.sp), color = colors.text3, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun formatJsonItems(data: kotlinx.serialization.json.JsonElement?): String {
    return when (data) {
        is JsonArray -> data.take(12).joinToString("\n") { item ->
            if (item is JsonObject) {
                item.entries.joinToString("  ") { (k, v) -> "$k=${(v as? JsonPrimitive)?.content ?: v}" }
            } else item.toString()
        }
        is JsonObject -> data.entries.joinToString("\n") { (k, v) -> "$k = ${(v as? JsonPrimitive)?.content ?: v}" }
        else -> data?.toString() ?: "暂无数据"
    }
}
