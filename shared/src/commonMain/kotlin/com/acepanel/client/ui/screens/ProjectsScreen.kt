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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
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
import com.acepanel.client.data.Project
import com.acepanel.client.data.formatBytes
import com.acepanel.client.ui.components.AceCard
import com.acepanel.client.ui.components.AceDialog
import com.acepanel.client.ui.components.AceIcon
import com.acepanel.client.ui.components.AceSegmented
import com.acepanel.client.ui.components.AceText
import com.acepanel.client.ui.components.AceTextField
import com.acepanel.client.ui.components.InlineNotice
import com.acepanel.client.ui.components.CircleAction
import com.acepanel.client.ui.components.EmptyState
import com.acepanel.client.ui.components.ErrorState
import com.acepanel.client.ui.components.LoadingState
import com.acepanel.client.ui.components.MobilePageHeader
import com.acepanel.client.ui.components.PanelDirectoryPickerDialog
import com.acepanel.client.ui.components.PrimaryPillButton
import com.acepanel.client.ui.components.SoftPillButton
import com.acepanel.client.ui.components.StatusPill
import com.acepanel.client.ui.components.rememberScreenScrollState
import com.acepanel.client.ui.theme.AceTheme
import com.acepanel.client.ui.theme.AceType
import com.composables.icons.lucide.CirclePlay
import com.composables.icons.lucide.FileText
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.RotateCw
import com.composables.icons.lucide.SquareTerminal
import com.composables.icons.lucide.Trash2
import kotlinx.coroutines.launch
import com.acepanel.client.data.runSafely

@Composable
fun ProjectsScreen(store: AceStore) {
    val colors = AceTheme.colors
    val scope = rememberCoroutineScope()
    var projects by remember { mutableStateOf<List<Project>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var manage by remember { mutableStateOf<Project?>(null) }
    var showCreate by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    suspend fun load() {
        loading = true
        error = null
        runSafely { projects = store.api?.projects()?.items ?: emptyList() }
            .onFailure { error = store.friendlyError(it) }
        loading = false
    }

    LaunchedEffect(store.api) { load() }

    val running = projects.count { it.status == "active" }
    val memory = projects.sumOf { it.memory }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        MobilePageHeader(
            title = "项目",
            subtitle = "${projects.size} 个项目 · $running 个运行中",
            onBack = store::closePage,
            actions = {
                CircleAction(Lucide.Plus, { showCreate = true }, background = colors.accent, tint = Color.White, contentDescription = "新建项目")
            },
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScreenScrollState())
                .padding(start = 26.dp, end = 26.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (loading) {
                LoadingState(rows = 4)
            } else if (error != null) {
                ErrorState(error!!, onRetry = { scope.launch { load() } })
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatBox("运行中", running.toString(), "个", Modifier.weight(1f))
                    StatBox("已停止", (projects.size - running).toString(), "个", Modifier.weight(1f))
                    StatBox("占用内存", formatBytes(memory), "", Modifier.weight(1f))
                }
                if (projects.isEmpty()) {
                    EmptyState(
                        title = "还没有项目",
                        subtitle = "项目用于守护常驻进程，如 API 服务、机器人或 worker",
                        icon = Lucide.SquareTerminal,
                    )
                } else {
                    projects.forEach { project ->
                        ProjectCard(project) { manage = project }
                    }
                }
            }
        }
    }

    if (showCreate) {
        CreateProjectDialog(store, { showCreate = false }) {
            showCreate = false
            scope.launch { load() }
        }
    }
    manage?.let { project ->
        AceDialog(title = project.name, onDismiss = { manage = null }) {
            AceText(
                "${project.execStart}${if (project.workingDir.isNotBlank()) " · ${project.workingDir}" else ""}",
                style = AceType.caption.copy(fontFamily = FontFamily.Monospace),
                color = colors.text3,
                maxLines = 3,
            )
            if (project.pid > 0) {
                AceText("PID ${project.pid} · 内存 ${formatBytes(project.memory)}", style = AceType.caption, color = colors.text3)
            }
            Spacer(Modifier.height(16.dp))
            SoftPillButton(
                if (project.status == "active") "停止项目" else "启动项目",
                onClick = {
                    if (busy) return@SoftPillButton
                    busy = true
                    scope.launch {
                        runSafely {
                            store.api?.systemctlAction(project.name, if (project.status == "active") "stop" else "start")
                        }.onSuccess {
                            store.toast(if (project.status == "active") "项目已停止" else "项目已启动")
                            manage = null
                            load()
                        }.onFailure { store.toast(store.friendlyError(it), true) }
                        busy = false
                    }
                },
                height = 46.dp,
                tint = if (project.status == "active") colors.warning else colors.success,
                background = if (project.status == "active") colors.warningSoft else colors.successSoft,
            )
            Spacer(Modifier.height(10.dp))
            SoftPillButton("重启项目", {
                if (busy) return@SoftPillButton
                busy = true
                scope.launch {
                    runSafely { store.api?.systemctlAction(project.name, "restart") }
                        .onSuccess { store.toast("项目已重启"); manage = null; load() }
                        .onFailure { store.toast(store.friendlyError(it), true) }
                    busy = false
                }
            }, height = 46.dp, tint = colors.accent, background = colors.accentSoft)
            Spacer(Modifier.height(10.dp))
            SoftPillButton("删除项目", {
                if (busy) return@SoftPillButton
                busy = true
                scope.launch {
                    runSafely { store.api?.deleteProject(project.id) }
                        .onSuccess { store.toast("项目已删除"); manage = null; load() }
                        .onFailure { store.toast(store.friendlyError(it), true) }
                    busy = false
                }
            }, height = 46.dp, tint = colors.error, background = colors.errorSoft)
        }
    }
}

@Composable
private fun StatBox(label: String, value: String, unit: String, modifier: Modifier) {
    val colors = AceTheme.colors
    AceCard(modifier = modifier, padding = 14.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            AceText(label, style = AceType.caption, color = colors.text3)
            Row(verticalAlignment = Alignment.Bottom) {
                AceText(value, style = AceType.bigNumber.copy(fontSize = 22.sp), color = colors.text1, maxLines = 1)
                if (unit.isNotBlank()) AceText(unit, style = AceType.caption, color = colors.text3, modifier = Modifier.padding(start = 3.dp, bottom = 3.dp))
            }
        }
    }
}

@Composable
private fun ProjectCard(project: Project, onClick: () -> Unit) {
    val colors = AceTheme.colors
    val running = project.status == "active"
    val color = if (running) colors.success else colors.text3
    AceCard(modifier = Modifier.clickable(onClick = onClick), padding = 16.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(colors.fill),
                    contentAlignment = Alignment.Center,
                ) {
                    AceIcon(Lucide.SquareTerminal, tint = colors.text1, size = 18.dp)
                }
                AceText(project.name, style = AceType.listStrong, color = colors.text1, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                StatusPill(if (running) "运行中" else (project.status.ifBlank { "已停止" }), color, color.copy(alpha = 0.12f))
            }
            AceText(
                listOfNotNull(
                    project.type.ifBlank { "general" },
                    project.execStart.ifBlank { project.rootDir }.ifBlank { null },
                ).joinToString(" · "),
                style = AceType.caption.copy(fontFamily = FontFamily.Monospace),
                color = colors.text3,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (running) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    AceText("PID ${project.pid}", style = AceType.caption.copy(fontFamily = FontFamily.Monospace), color = colors.text3)
                    AceText(formatBytes(project.memory), style = AceType.caption, color = colors.text3)
                    AceText(
                        "CPU ${project.cpu.toDouble().asPercentOne()}%",
                        style = AceType.caption,
                        color = colors.text3,
                    )
                }
            }
        }
    }
}

private fun Double.asPercentOne(): String {
    val v = (this * 10).toInt() / 10.0
    return v.toString()
}


@Composable
private fun CreateProjectDialog(store: AceStore, onDismiss: () -> Unit, onCreated: () -> Unit) {
    val colors = AceTheme.colors
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var typeIndex by remember { mutableStateOf(0) }
    var description by remember { mutableStateOf("") }
    var rootDir by remember { mutableStateOf("") }
    var execStart by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("root") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showDirPicker by remember { mutableStateOf(false) }
    var showStartFilePicker by remember { mutableStateOf(false) }
    var execStartTouched by remember { mutableStateOf(false) }
    val types = listOf("general", "go", "nodejs", "python", "php", "java", "dotnet")
    LaunchedEffect(typeIndex, name, rootDir) {
        if (!execStartTouched) {
            execStart = defaultStartCommand(types[typeIndex.coerceIn(types.indices)], name, rootDir)
        }
    }
    AceDialog(title = "新建项目", onDismiss = onDismiss) {
        AceText("项目名称", style = AceType.caption, color = colors.text3)
        Spacer(Modifier.height(6.dp))
        AceTextField(name, { name = it }, placeholder = "例如 api-server")
        Spacer(Modifier.height(12.dp))
        AceText("类型", style = AceType.caption, color = colors.text3)
        Spacer(Modifier.height(6.dp))
        AceSegmented(
            listOf("通用", "Go", "Node", "Python"),
            typeIndex.coerceIn(0, 3),
            onSelect = {
                typeIndex = it
                execStartTouched = false
            },
        )
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            AceText("启动命令", style = AceType.caption, color = colors.text3, modifier = Modifier.weight(1f))
            if (typeIndex > 0) {
                AceText(
                    "根据项目类型自动生成",
                    style = AceType.caption,
                    color = colors.text3,
                )
            }
            AceText(
                "选择文件",
                style = AceType.caption,
                color = colors.accent,
                modifier = Modifier
                    .clickable { showStartFilePicker = true }
                    .padding(start = 8.dp, end = 2.dp, top = 2.dp, bottom = 2.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        AceTextField(
            execStart,
            {
                execStart = it
                execStartTouched = true
            },
            placeholder = "/opt/ace/projects/api/server --port 8080",
        )
        if (typeIndex == 0) {
            Spacer(Modifier.height(5.dp))
            AceText(
                "通用项目没有固定的启动方式，请填写实际可执行命令（如 ./run.sh 或 systemd 可识别的命令）。",
                style = AceType.caption,
                color = colors.text3,
            )
        }
        Spacer(Modifier.height(12.dp))
        AceText("项目目录（选填）", style = AceType.caption, color = colors.text3)
        Spacer(Modifier.height(6.dp))
        AceTextField(
            rootDir,
            { rootDir = it },
            placeholder = "/opt/ace/projects/${name.ifBlank { "api-server" }}",
            trailing = {
                AceText(
                    "选择",
                    style = AceType.caption,
                    color = colors.accent,
                    modifier = Modifier.clickable { showDirPicker = true }.padding(horizontal = 4.dp),
                )
            },
        )
        Spacer(Modifier.height(5.dp))
        AceText(
            "项目根目录与工作目录统一使用此目录，无需重复选择。",
            style = AceType.caption,
            color = colors.text3,
        )
        Spacer(Modifier.height(12.dp))
        AceText("描述 / 运行用户（选填）", style = AceType.caption, color = colors.text3)
        Spacer(Modifier.height(6.dp))
        AceTextField(description, { description = it }, placeholder = "项目说明")
        Spacer(Modifier.height(8.dp))
        AceTextField(user, { user = it }, placeholder = "root")
        error?.let {
            Spacer(Modifier.height(10.dp))
            InlineNotice(it, color = colors.error, background = colors.errorSoft)
        }
        Spacer(Modifier.height(16.dp))
        PrimaryPillButton(if (busy) "创建中…" else "创建项目", {
            if (busy) return@PrimaryPillButton
            if (name.isBlank() || execStart.isBlank()) {
                error = "请填写项目名称和启动命令"
                return@PrimaryPillButton
            }
            busy = true
            scope.launch {
                runSafely {
                    store.api?.createProject(
                        name = name.trim(),
                        type = types[typeIndex.coerceIn(types.indices)],
                        description = description.trim(),
                        rootDir = rootDir.trim(),
                        workingDir = rootDir.trim(),
                        execStart = execStart.trim(),
                        user = user.trim().ifBlank { "root" },
                    )
                }.onSuccess {
                    store.toast("项目已创建")
                    onCreated()
                }.onFailure { error = store.friendlyError(it) }
                busy = false
            }
        }, height = 46.dp)
    }

    if (showDirPicker) {
        PanelDirectoryPickerDialog(
            store = store,
            initialPath = rootDir.ifBlank { "/opt/ace/projects" },
            title = "选择项目目录",
            onDismiss = { showDirPicker = false },
        ) { selected ->
            rootDir = selected
            showDirPicker = false
        }
    }

    if (showStartFilePicker) {
        PanelDirectoryPickerDialog(
            store = store,
            initialPath = rootDir.ifBlank { "/" },
            title = "选择启动文件",
            allowFileSelection = true,
            onDismiss = { showStartFilePicker = false },
        ) { selected ->
            execStart = selected
            execStartTouched = true
            showStartFilePicker = false
        }
    }
}

private fun defaultStartCommand(type: String, name: String, rootDir: String): String {
    val app = name.ifBlank { "app" }
    val base = rootDir.ifBlank {
        if (name.isBlank()) "/opt/ace/projects" else "/opt/ace/projects/$app"
    }.trimEnd('/')
    return when (type) {
        "go" -> "$base/$app"
        "nodejs" -> "npm start"
        "python" -> "python3 main.py"
        "php" -> "php -S 0.0.0.0:8000 -t $base"
        "java" -> "java -jar $base/$app.jar"
        "dotnet" -> "dotnet $base/$app.dll"
        else -> ""
    }
}
