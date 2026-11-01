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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.acepanel.client.data.AceStore
import com.acepanel.client.data.CronTask
import com.acepanel.client.ui.components.AceCard
import com.acepanel.client.ui.components.AceDialog
import com.acepanel.client.ui.components.AceIcon
import com.acepanel.client.ui.components.AceSwitch
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
import com.acepanel.client.ui.components.rememberScreenScrollState
import com.acepanel.client.ui.theme.AceTheme
import com.acepanel.client.ui.theme.AceType
import com.composables.icons.lucide.CalendarClock
import com.composables.icons.lucide.Clock
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Play
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.RotateCw
import com.composables.icons.lucide.Terminal
import com.composables.icons.lucide.Trash2
import kotlinx.coroutines.launch
import com.acepanel.client.data.runSafely

@Composable
fun CronScreen(store: AceStore) {
    val colors = AceTheme.colors
    val scope = rememberCoroutineScope()
    var tasks by remember { mutableStateOf<List<CronTask>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var showCreate by remember { mutableStateOf(false) }
    var logsFor by remember { mutableStateOf<CronTask?>(null) }
    var logs by remember { mutableStateOf<String>("") }
    var logsLoading by remember { mutableStateOf(false) }
    var toggling by remember { mutableStateOf<Long?>(null) }
    var manage by remember { mutableStateOf<CronTask?>(null) }

    suspend fun load() {
        loading = true
        error = null
        runSafely { tasks = store.api?.cronTasks()?.items ?: emptyList() }
            .onFailure { error = store.friendlyError(it) }
        loading = false
    }

    LaunchedEffect(store.api) { load() }

    fun openLogs(task: CronTask) {
        logsFor = task
        if (task.log.isBlank()) {
            logs = "该任务未配置日志文件。\n\n计划任务的日志由任务脚本自行输出；面板会把任务输出写入其 log 路径。备份、WebHook 等内置任务可在对应模块查看结果。"
            logsLoading = false
            return
        }
        logs = "正在读取 ${task.log}…"
        logsLoading = true
        scope.launch {
            runSafely { store.api?.fileTail(task.log, 300) }
                .onSuccess { lines ->
                    logs = if (lines.isNullOrEmpty()) "日志文件存在，但当前没有可显示的内容。" else lines.joinToString("\n")
                }
                .onFailure { logs = store.friendlyError(it) }
            logsLoading = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        MobilePageHeader(
            title = "计划任务",
            subtitle = "${tasks.size} 个任务 · ${tasks.count { it.status }} 个运行中",
            onBack = store::closePage,
            actions = {
                CircleAction(Lucide.Plus, { showCreate = true }, background = colors.accent, tint = Color.White, contentDescription = "新建任务")
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
                LoadingState(rows = 4)
            } else if (error != null) {
                ErrorState(error!!, onRetry = { scope.launch { load() } })
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    StatBlock("任务总数", tasks.size.toString(), "个", Modifier.weight(1f))
                    StatBlock("运行中", tasks.count { it.status }.toString(), "个", Modifier.weight(1f))
                }
                if (tasks.isEmpty()) {
                    EmptyState(
                        title = "还没有计划任务",
                        subtitle = "用 Cron 定时执行备份、脚本或 WebHook",
                        icon = Lucide.CalendarClock,
                        actionText = "新建任务",
                        onAction = { showCreate = true },
                    )
                } else {
                    tasks.forEach { task ->
                        CronCard(
                            task = task,
                            toggling = toggling == task.id,
                            onToggle = { enabled ->
                                toggling = task.id
                                scope.launch {
                                    runSafely { store.api?.cronStatus(task.id, enabled) }
                                        .onSuccess { store.toast(if (enabled) "任务已启用" else "任务已暂停"); load() }
                                        .onFailure { store.toast(store.friendlyError(it), true) }
                                    toggling = null
                                }
                            },
                            onLogs = { openLogs(task) },
                            onManage = { manage = task },
                        )
                    }
                }
                if (tasks.isNotEmpty()) {
                    InlineNotice(
                        "计划任务使用服务器本地时区执行。修改系统时区后，请重新检查任务时间。",
                        color = colors.accent,
                        background = colors.accentSoft,
                        icon = Lucide.Clock,
                    )
                }
            }
        }
        Box(Modifier.fillMaxWidth().padding(horizontal = 26.dp, vertical = 14.dp)) {
            PrimaryPillButton("新建任务", { showCreate = true }, height = 50.dp, leadingIcon = Lucide.Plus)
        }
    }

    if (showCreate) {
        CreateCronDialog(store, { showCreate = false }) {
            showCreate = false
            scope.launch { load() }
        }
    }
    logsFor?.let { task ->
        AceDialog(title = "${task.name} · 执行记录", onDismiss = { logsFor = null }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp, max = 360.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.terminal)
                    .padding(12.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    logs,
                    style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 16.sp, color = colors.terminalText),
                )
            }
            Spacer(Modifier.height(14.dp))
            PrimaryPillButton("关闭", { logsFor = null }, height = 44.dp)
        }
    }
    manage?.let { task ->
        AceDialog(title = task.name, onDismiss = { manage = null }) {
            AceText(task.time, style = AceType.bodySmall.copy(fontFamily = FontFamily.Monospace), color = colors.text2)
            AceText(task.shell.ifBlank { task.type }, style = AceType.caption, color = colors.text3)
            Spacer(Modifier.height(16.dp))
            SoftPillButton("删除任务", {
                scope.launch {
                    runSafely { store.api?.deleteCron(task.id) }
                        .onSuccess {
                            store.toast("任务已删除")
                            manage = null
                            load()
                        }
                        .onFailure { store.toast(store.friendlyError(it), true) }
                }
            }, height = 46.dp, tint = colors.error, background = colors.errorSoft)
        }
    }
}

@Composable
private fun StatBlock(label: String, value: String, unit: String, modifier: Modifier = Modifier) {
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
private fun CronCard(
    task: CronTask,
    toggling: Boolean,
    onToggle: (Boolean) -> Unit,
    onLogs: () -> Unit,
    onManage: () -> Unit,
) {
    val colors = AceTheme.colors
    AceCard(padding = 16.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AceText(task.name, style = AceType.listStrong, color = colors.text1, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (task.status) {
                    Box(Modifier.size(6.dp).clip(androidx.compose.foundation.shape.CircleShape).background(colors.success))
                }
                AceSwitch(task.status, enabled = !toggling, onCheckedChange = onToggle)
            }
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                AceText(task.time, style = AceType.caption.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium), color = colors.text2, modifier = Modifier.weight(1f))
                AceText(
                    when (task.type) {
                        "backup" -> "备份"
                        "cutoff" -> "日志切割"
                        "url" -> "WebHook"
                        "synctime" -> "时间同步"
                        else -> "Shell 脚本"
                    },
                    style = AceType.caption,
                    color = colors.text3,
                )
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(colors.divider))
            Row(verticalAlignment = Alignment.CenterVertically) {
                AceText("下次执行时间以服务器 Cron 为准", style = AceType.caption, color = colors.text3, modifier = Modifier.weight(1f))
                Row(
                    modifier = Modifier.clip(RoundedCornerShape(999.dp)).clickable(onClick = onLogs).padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    AceIcon(Lucide.Play, tint = colors.accent, size = 12.dp)
                    AceText("执行记录", style = AceType.caption.copy(fontWeight = FontWeight.Medium), color = colors.accent)
                }
                Row(
                    modifier = Modifier.clip(RoundedCornerShape(999.dp)).clickable(onClick = onManage).padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AceIcon(Lucide.Trash2, tint = colors.text3, size = 13.dp)
                }
            }
        }
    }
}

@Composable
private fun CreateCronDialog(store: AceStore, onDismiss: () -> Unit, onCreated: () -> Unit) {
    val colors = AceTheme.colors
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var time by remember { mutableStateOf("0 2 * * *") }
    var script by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    AceDialog(title = "新建计划任务", onDismiss = onDismiss) {
        AceText("任务名称", style = AceType.caption, color = colors.text3)
        Spacer(Modifier.height(6.dp))
        AceTextField(name, { name = it }, placeholder = "例如 每日数据库备份")
        Spacer(Modifier.height(12.dp))
        AceText("Cron 表达式", style = AceType.caption, color = colors.text3)
        Spacer(Modifier.height(6.dp))
        AceTextField(time, { time = it }, placeholder = "0 2 * * *")
        Spacer(Modifier.height(12.dp))
        AceText("Shell 脚本", style = AceType.caption, color = colors.text3)
        Spacer(Modifier.height(6.dp))
        AceTextField(script, { script = it }, placeholder = "#!/bin/bash\nacepanel backup database -n blog -s 0", singleLine = false, height = 110.dp)
        error?.let {
            Spacer(Modifier.height(10.dp))
            InlineNotice(it, color = colors.error, background = colors.errorSoft)
        }
        Spacer(Modifier.height(16.dp))
        PrimaryPillButton(if (busy) "创建中…" else "创建任务", {
            if (busy) return@PrimaryPillButton
            if (name.isBlank() || time.isBlank() || script.isBlank()) {
                error = "请填写任务名称、时间和脚本"
                return@PrimaryPillButton
            }
            busy = true
            scope.launch {
                runSafely { store.api?.createCron(name.trim(), time.trim(), script) }
                    .onSuccess {
                        store.toast("计划任务已创建")
                        onCreated()
                    }
                    .onFailure { error = store.friendlyError(it) }
                busy = false
            }
        }, height = 46.dp)
    }
}
