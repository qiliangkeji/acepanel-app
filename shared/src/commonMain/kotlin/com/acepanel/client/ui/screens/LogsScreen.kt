package com.acepanel.client.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.acepanel.client.data.AceStore
import com.acepanel.client.data.LogEntry
import com.acepanel.client.data.TaskInfo
import com.acepanel.client.data.boolean
import com.acepanel.client.data.string
import com.acepanel.client.data.timePart
import com.acepanel.client.ui.components.AceCard
import com.acepanel.client.ui.components.AceIcon
import com.acepanel.client.ui.components.AceText
import com.acepanel.client.ui.components.EmptyState
import com.acepanel.client.ui.components.ErrorState
import com.acepanel.client.ui.components.LoadingState
import com.acepanel.client.ui.components.MobilePageHeader
import com.acepanel.client.ui.components.StatusPill
import com.acepanel.client.ui.components.rememberScreenScrollState
import com.acepanel.client.ui.theme.AceTheme
import com.acepanel.client.ui.theme.AceType
import com.composables.icons.lucide.FileText
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.RefreshCw
import com.composables.icons.lucide.Search
import kotlinx.coroutines.launch
import com.acepanel.client.data.runSafely

private enum class LogTab(val label: String) { OPERATION("操作"), SSH("SSH"), PANEL("面板"), TASK("任务") }
private enum class LogFilter(val label: String) { ALL("全部"), LOGIN("登录"), CONFIG("配置变更"), DELETE("删除") }

@Composable
fun LogsScreen(store: AceStore) {
    val colors = AceTheme.colors
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    var tab by remember { mutableStateOf(LogTab.OPERATION) }
    var filter by remember { mutableStateOf(LogFilter.ALL) }
    var logs by remember { mutableStateOf<List<LogEntry>>(emptyList()) }
    var tasks by remember { mutableStateOf<List<TaskInfo>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    suspend fun load(tab: LogTab) {
        loading = true
        error = null
        runSafely {
            when (tab) {
                LogTab.OPERATION -> logs = store.api?.logs("app", 200) ?: emptyList()
                LogTab.SSH -> logs = store.api?.sshLogs(200) ?: emptyList()
                LogTab.PANEL -> logs = store.api?.logs("app", 200) ?: emptyList()
                LogTab.TASK -> {
                    tasks = store.api?.tasks(1, 100)?.items ?: emptyList()
                    logs = emptyList()
                }
            }
        }.onFailure { error = store.friendlyError(it) }
        loading = false
    }

    LaunchedEffect(store.api, tab) { load(tab) }

    val filteredLogs = logs.sortedByDescending { it.time }.filter { entry ->
        when (filter) {
            LogFilter.ALL -> true
            LogFilter.LOGIN -> entry.msg.contains("登录", true) || entry.msg.contains("login", true)
            LogFilter.CONFIG -> entry.msg.contains("修改", true) || entry.msg.contains("更新", true) || entry.msg.contains("创建", true)
            LogFilter.DELETE -> entry.msg.contains("删除", true) || entry.msg.contains("delete", true)
        }
    }
    val errorCount = logs.count { it.level.equals("error", true) || it.level.equals("warn", true) }
    val total = if (tab == LogTab.TASK) tasks.size else logs.size

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        MobilePageHeader(
            title = "日志",
            subtitle = "近 24 小时 · ${total} 条",
            onBack = store::closePage,
            actions = {
                Box(Modifier.size(30.dp).clickable { clipboard.setText(AnnotatedString(logs.joinToString("\n") { "${it.time} ${it.msg}" })); store.toast("日志已复制") }, contentAlignment = Alignment.Center) {
                    AceIcon(Lucide.Search, tint = colors.text3, size = 18.dp)
                }
            },
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScreenScrollState())
                .padding(start = 26.dp, end = 26.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                LogTab.entries.forEach { item ->
                    val active = tab == item
                    Column(
                        modifier = Modifier.clickable { tab = item },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        AceText(item.label, style = AceType.tab.copy(fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal), color = if (active) colors.text1 else colors.text2)
                        Box(Modifier.width(if (active) 18.dp else 0.dp).height(3.dp).clip(RoundedCornerShape(999.dp)).background(colors.accent))
                    }
                }
            }
            if (tab != LogTab.TASK) {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    LogSummary("今日记录", total.toString(), "条", Modifier.weight(1f))
                    LogSummary("异常", errorCount.toString(), "条", Modifier.weight(1f))
                }
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    LogFilter.entries.forEach { item ->
                        val active = filter == item
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(if (active) colors.accentSoft else colors.surface)
                                .clickable { filter = item }
                                .padding(horizontal = 14.dp, vertical = 7.dp),
                        ) {
                            AceText(item.label, style = AceType.caption.copy(fontWeight = if (active) FontWeight.Medium else FontWeight.Normal), color = if (active) colors.accent else colors.text2)
                        }
                    }
                }
            }
            when {
                loading -> LoadingState(rows = 5)
                error != null -> ErrorState(error!!, onRetry = { scope.launch { load(tab) } })
                tab == LogTab.TASK -> {
                    if (tasks.isEmpty()) {
                        EmptyState(title = "暂无后台任务", subtitle = "安装应用、备份或更新时产生的后台任务会显示在这里", icon = Lucide.FileText)
                    } else {
                        AceCard(padding = 0.dp) {
                            tasks.forEachIndexed { index, task ->
                                TaskLogRow(task)
                                if (index != tasks.lastIndex) RowDivider()
                            }
                        }
                    }
                }
                filteredLogs.isEmpty() -> EmptyState(
                    title = "暂无日志",
                    subtitle = "该时间范围内没有匹配的记录",
                    icon = Lucide.FileText,
                )
                else -> AceCard(padding = 0.dp) {
                    filteredLogs.take(100).forEachIndexed { index, entry ->
                        LogRow(entry)
                        if (index != filteredLogs.take(100).lastIndex) RowDivider()
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                AceText("已加载 ${if (tab == LogTab.TASK) tasks.size else filteredLogs.size} / $total 条", style = AceType.caption, color = colors.text3, modifier = Modifier.weight(1f))
                AceText(
                    "导出日志",
                    style = AceType.caption.copy(fontWeight = FontWeight.Medium),
                    color = colors.accent,
                    modifier = Modifier.clickable {
                        clipboard.setText(AnnotatedString(logs.joinToString("\n") { "${it.time} [${it.level}] ${it.msg}" }))
                        store.toast("日志已复制到剪贴板")
                    },
                )
            }
        }
    }
}

@Composable
private fun LogSummary(label: String, value: String, unit: String, modifier: Modifier) {
    val colors = AceTheme.colors
    AceCard(modifier = modifier, padding = 16.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            AceText(label, style = AceType.caption, color = colors.text3)
            Row(verticalAlignment = Alignment.Bottom) {
                AceText(value, style = AceType.bigNumber.copy(fontSize = 24.sp), color = colors.text1)
                AceText(unit, style = AceType.caption, color = colors.text3, modifier = Modifier.padding(start = 4.dp, bottom = 3.dp))
            }
        }
    }
}

@Composable
private fun LogRow(entry: LogEntry) {
    val colors = AceTheme.colors
    val error = entry.level.equals("error", true)
    val warning = entry.level.equals("warn", true) || entry.level.equals("warning", true)
    val dotColor = when {
        error -> colors.error
        warning -> colors.warning
        else -> colors.success
    }
    val ip = (entry.extra?.get("ip") as? kotlinx.serialization.json.JsonPrimitive)?.content
        ?: (entry.extra?.get("client_ip") as? kotlinx.serialization.json.JsonPrimitive)?.content
        ?: ""
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        AceText(entry.time.timePart().take(5), style = AceType.caption.copy(fontFamily = FontFamily.Monospace), color = colors.text3, modifier = Modifier.width(36.dp))
        Box(Modifier.padding(top = 5.dp).size(6.dp).clip(CircleShape).background(dotColor))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            AceText(
                (entry.operatorName.ifBlank { if (entry.msg.contains("root", true)) "root" else "system" }) + " " + entry.msg,
                style = AceType.bodySmall,
                color = colors.text1,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (ip.isNotBlank()) {
                AceText(ip, style = AceType.caption.copy(fontFamily = FontFamily.Monospace), color = colors.text3, maxLines = 1)
            }
        }
    }
}

@Composable
private fun TaskLogRow(task: TaskInfo) {
    val colors = AceTheme.colors
    val active = task.status.contains("running", true) || task.status.contains("waiting", true)
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AceIcon(Lucide.FileText, tint = colors.text3, size = 17.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            AceText(task.name, style = AceType.bodySmall, color = colors.text1, maxLines = 1, overflow = TextOverflow.Ellipsis)
            AceText(task.createdAt.timePart(), style = AceType.caption.copy(fontFamily = FontFamily.Monospace), color = colors.text3)
        }
        StatusPill(
            when {
                task.status.contains("success", true) || task.status.contains("done", true) -> "成功"
                task.status.contains("fail", true) -> "失败"
                task.status.contains("running", true) -> "执行中"
                else -> task.status.ifBlank { "等待" }
            },
            if (active) colors.accent else if (task.status.contains("fail", true)) colors.error else colors.success,
            if (active) colors.accentSoft else if (task.status.contains("fail", true)) colors.errorSoft else colors.successSoft,
        )
    }
}

@Composable
private fun RowDivider() {
    Box(Modifier.fillMaxWidth().padding(start = 62.dp).height(1.dp).background(AceTheme.colors.divider))
}
