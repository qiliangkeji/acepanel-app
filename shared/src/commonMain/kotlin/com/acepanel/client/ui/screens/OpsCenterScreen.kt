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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.acepanel.client.data.AceStore
import com.acepanel.client.data.Page
import com.acepanel.client.data.dateTimePart
import com.acepanel.client.data.decodeOrNull
import com.acepanel.client.data.formatBytes
import com.acepanel.client.data.long
import com.acepanel.client.data.string
import com.acepanel.client.ui.components.AceCard
import com.acepanel.client.ui.components.AceConfirmDialog
import com.acepanel.client.ui.components.AceDialog
import com.acepanel.client.ui.components.AceFormDialog
import com.acepanel.client.ui.components.AceFormField
import com.acepanel.client.ui.components.AceIcon
import com.acepanel.client.ui.components.AceSegmented
import com.acepanel.client.ui.components.AceSwitch
import com.acepanel.client.ui.components.AceText
import com.acepanel.client.ui.components.AceTextField
import com.acepanel.client.ui.components.EmptyState
import com.acepanel.client.ui.components.ErrorState
import com.acepanel.client.ui.components.LoadingState
import com.acepanel.client.ui.components.MobilePageHeader
import com.acepanel.client.ui.components.PrimaryPillButton
import com.acepanel.client.ui.components.SoftPillButton
import com.acepanel.client.ui.components.StatusPill
import com.acepanel.client.ui.components.rememberScreenScrollState
import com.acepanel.client.ui.theme.AceColors
import com.acepanel.client.ui.theme.AceTheme
import com.acepanel.client.ui.theme.AceType
import com.composables.icons.lucide.Activity
import com.composables.icons.lucide.Box
import com.composables.icons.lucide.Cpu
import com.composables.icons.lucide.Link
import com.composables.icons.lucide.ListChecks
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Package
import com.composables.icons.lucide.Search
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import com.acepanel.client.data.runSafely

private fun JsonObject.text(key: String): String = when (val value = this[key]) {
    null, JsonNull -> ""
    is JsonPrimitive -> if (value.isString) value.content else if (value.content == "null") "" else value.content
    else -> value.toString()
}

private fun JsonObject.boolValue(key: String, default: Boolean = false): Boolean =
    (this[key] as? JsonPrimitive)?.booleanOrNull ?: default

private fun JsonObject.stringList(key: String): List<String> =
    (this[key] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content } ?: emptyList()

private fun oneDecimal(value: Double): String {
    val rounded = (value * 10).toLong() / 10.0
    return if (rounded == rounded.toLong().toDouble()) rounded.toLong().toString() else rounded.toString()
}

private fun pretty(text: String): String = runSafely {
    val element = com.acepanel.client.data.ApiJson.parseToJsonElement(text)
    kotlinx.serialization.json.Json { prettyPrint = true }.encodeToString(
        kotlinx.serialization.json.JsonElement.serializer(),
        element,
    )
}.getOrDefault(text)

@Composable
fun OpsCenterScreen(store: AceStore) {
    val colors = AceTheme.colors
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    var tab by remember { mutableStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var processes by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var processSearch by remember { mutableStateOf("") }
    var processSort by remember { mutableStateOf("cpu") }
    var tasks by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var webhooks by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var templates by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var templateSearch by remember { mutableStateOf("") }
    var webhookEditor by remember { mutableStateOf<JsonObject?>(null) }
    var webhookKey by remember { mutableStateOf<String?>(null) }
    var deleteWebhook by remember { mutableStateOf<JsonObject?>(null) }
    var processDetail by remember { mutableStateOf<JsonObject?>(null) }
    var taskDetail by remember { mutableStateOf<JsonObject?>(null) }
    var signalProcess by remember { mutableStateOf<JsonObject?>(null) }
    var killProcess by remember { mutableStateOf<JsonObject?>(null) }
    var cancelTask by remember { mutableStateOf<JsonObject?>(null) }
    var deleteTask by remember { mutableStateOf<JsonObject?>(null) }
    var templateDetail by remember { mutableStateOf<JsonObject?>(null) }
    var deployTemplate by remember { mutableStateOf<JsonObject?>(null) }

    suspend fun load() {
        val client = store.api
        if (client == null) {
            error = "面板未连接"
            loading = false
            return
        }
        loading = true
        error = null
        runSafely {
            val processPage: Page<JsonObject> = client.processes(1, 100, processSearch, processSort, "desc")
            val taskPage: Page<JsonObject> = client.getData(
                "/api/task",
                mapOf("page" to "1", "limit" to "100"),
            ).decodeOrNull<Page<JsonObject>>() ?: Page()
            val hookPage = client.webhooks(1, 100)
            val templatePage = client.templates(templateSearch, 1, 100)
            listOf<List<JsonObject>>(processPage.items, taskPage.items, hookPage.items, templatePage.items)
        }.onSuccess { lists ->
            processes = lists[0]
            tasks = lists[1]
            webhooks = lists[2]
            templates = lists[3]
        }.onFailure { error = store.friendlyError(it) }
        loading = false
    }

    LaunchedEffect(store.api) { load() }

    fun action(success: String, after: () -> Unit = {}, block: suspend () -> Unit) {
        scope.launch {
            runSafely { block() }
                .onSuccess {
                    store.toast(success)
                    after()
                }
                .onFailure { store.toast(store.friendlyError(it), true) }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        MobilePageHeader(
            title = "自动化与进程",
            subtitle = "进程 · 任务 · WebHook · 应用模板",
            onBack = store::closePage,
            actions = {
                SoftPillButton("刷新", { scope.launch { load() } }, height = 36.dp, tint = colors.text2, background = colors.fill)
            },
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScreenScrollState())
                .padding(horizontal = 26.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AceSegmented(
                options = listOf("进程", "任务", "WebHook", "模板"),
                selectedIndex = tab,
                onSelect = { tab = it },
            )
            when {
                loading -> LoadingState(rows = 4)
                error != null -> ErrorState(error!!) { scope.launch { load() } }
                tab == 0 -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) {
                            AceTextField(
                                processSearch,
                                { processSearch = it },
                                height = 38.dp,
                                placeholder = "搜索进程名或 PID",
                                textStyle = AceType.caption,
                            )
                        }
                        SoftPillButton("查询", { scope.launch { load() } }, height = 38.dp, tint = colors.accent, background = colors.accentSoft)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("cpu" to "CPU", "rss" to "内存", "pid" to "PID").forEach { (value, label) ->
                            SegmentChip(label, processSort == value, colors) {
                                processSort = value
                                scope.launch { load() }
                            }
                        }
                    }
                    if (processes.isEmpty()) {
                        EmptyState(title = "没有进程数据", subtitle = "调整搜索或排序后重试", icon = Lucide.Activity)
                    }
                    processes.forEach { process ->
                        ProcessRow(
                            process = process,
                            colors = colors,
                            onDetail = { processDetail = process },
                            onSignal = { signalProcess = process },
                            onKill = { killProcess = process },
                        )
                    }
                }
                tab == 1 -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            AceText("后台任务", style = AceType.section.copy(fontSize = 14.sp), color = colors.text1)
                            AceText("${tasks.count { it.text("status") == "running" }} 个运行中 · 共 ${tasks.size} 个", style = AceType.caption, color = colors.text3)
                        }
                    }
                    if (tasks.isEmpty()) {
                        EmptyState(title = "暂无后台任务", subtitle = "安装应用、备份、迁移等操作会生成任务记录", icon = Lucide.ListChecks)
                    }
                    tasks.forEach { task ->
                        TaskRow(
                            task = task,
                            colors = colors,
                            onDetail = { scope.launch {
                                runSafely { store.api?.taskDetail(task.long("id")) }
                                    .onSuccess { taskDetail = it ?: task }
                                    .onFailure { store.toast(store.friendlyError(it), true) }
                            } },
                            onCancel = { cancelTask = task },
                            onDelete = { deleteTask = task },
                        )
                    }
                }
                tab == 2 -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            AceText("WebHook", style = AceType.section.copy(fontSize = 14.sp), color = colors.text1)
                            AceText("${webhooks.size} 个回调脚本 · 通过 /webhook/{key} 触发", style = AceType.caption, color = colors.text3)
                        }
                        SoftPillButton(
                            "新建 WebHook",
                            { webhookEditor = JsonObject(emptyMap()) },
                            height = 34.dp,
                            tint = colors.accent,
                            background = colors.accentSoft,
                        )
                    }
                    if (webhooks.isEmpty()) {
                        EmptyState(title = "暂无 WebHook", subtitle = "创建后可让外部系统触发面板脚本", icon = Lucide.Link)
                    }
                    webhooks.forEach { hook ->
                        WebhookRow(
                            hook = hook,
                            baseUrl = store.api?.baseUrl.orEmpty(),
                            colors = colors,
                            onCopy = { url ->
                                clipboard.setText(AnnotatedString(url))
                                store.toast("回调地址已复制")
                            },
                            onEdit = { webhookEditor = hook },
                            onDelete = { deleteWebhook = hook },
                        )
                    }
                }
                else -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) {
                            AceTextField(
                                templateSearch,
                                { templateSearch = it },
                                height = 38.dp,
                                placeholder = "搜索应用模板",
                                textStyle = AceType.caption,
                            )
                        }
                        SoftPillButton("搜索", { scope.launch { load() } }, height = 38.dp, tint = colors.accent, background = colors.accentSoft)
                    }
                    if (templates.isEmpty()) {
                        EmptyState(title = "暂无模板", subtitle = "面板内置 Docker Compose 应用模板", icon = Lucide.Package)
                    }
                    templates.forEach { template ->
                        TemplateRow(
                            template = template,
                            colors = colors,
                            onDetail = { templateDetail = template },
                            onDeploy = { deployTemplate = template },
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    processDetail?.let { process ->
        JsonDetailDialog(
            title = "进程 ${process.text("name")} · ${process.long("pid")}",
            content = pretty(process.toString()),
            colors = colors,
            onDismiss = { processDetail = null },
        )
    }

    signalProcess?.let { process ->
        AceFormDialog(
            title = "向进程 ${process.long("pid")} 发送信号",
            fields = listOf(AceFormField("信号（1/2/9/10/12/15/18/19）", "15", numeric = true)),
            confirmText = "发送",
            onDismiss = { signalProcess = null },
        ) { values ->
            val signal = values.first().toIntOrNull() ?: 15
            signalProcess = null
            action("信号已发送") {
                store.api?.signalProcess(process.long("pid"), signal)
            }
        }
    }

    killProcess?.let { process ->
        AceConfirmDialog(
            title = "结束进程",
            message = "确认结束「${process.text("name")}」（PID ${process.long("pid")}）？未保存的数据可能丢失。",
            confirmText = "结束进程",
            danger = true,
            onDismiss = { killProcess = null },
        ) {
            val pid = process.long("pid")
            killProcess = null
            action("进程已结束", after = { scope.launch { load() } }) { store.api?.killProcess(pid) }
        }
    }

    taskDetail?.let { task ->
        val log = task.text("log")
        AceDialog(title = task.text("name").ifBlank { "任务详情" }, onDismiss = { taskDetail = null }) {
            AceText(
                "${task.text("status")} · 创建于 ${task.text("created_at").dateTimePart().ifBlank { "—" }}",
                style = AceType.caption,
                color = colors.text3,
            )
            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 140.dp, max = 420.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.terminal)
                    .padding(12.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                AceText(
                    log.ifBlank { "<暂无日志>" },
                    style = AceType.caption.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 16.sp),
                    color = colors.terminalText,
                )
            }
            Spacer(Modifier.height(12.dp))
            PrimaryPillButton("关闭", { taskDetail = null }, height = 42.dp)
        }
    }

    cancelTask?.let { task ->
        AceConfirmDialog(
            title = "取消任务",
            message = "确认取消「${task.text("name")}」？执行中的命令将被终止。",
            confirmText = "取消任务",
            danger = true,
            onDismiss = { cancelTask = null },
        ) {
            val id = task.long("id")
            cancelTask = null
            action("任务已取消", after = { scope.launch { load() } }) { store.api?.cancelTask(id) }
        }
    }

    deleteTask?.let { task ->
        AceConfirmDialog(
            title = "删除任务",
            message = "确认删除任务记录「${task.text("name")}」？日志将一并删除。",
            confirmText = "删除",
            danger = true,
            onDismiss = { deleteTask = null },
        ) {
            val id = task.long("id")
            deleteTask = null
            action("任务已删除", after = { scope.launch { load() } }) { store.api?.deleteTask(id) }
        }
    }

    webhookEditor?.let { hook ->
        val isNew = hook.isEmpty()
        WebhookDialog(
            hook = hook.takeIf { !isNew },
            colors = colors,
            onDismiss = { webhookEditor = null },
            onSave = { name, user, script, raw, enabled ->
                scope.launch {
                    runSafely {
                        val client = store.api
                        if (isNew) {
                            val created = client?.createWebhook(name, script, raw, user)
                            webhookKey = created?.text("key")?.takeIf { it.isNotBlank() }
                        } else {
                            client?.updateWebhook(hook.long("id"), name, script, raw, user, enabled)
                        }
                    }.onSuccess {
                        webhookEditor = null
                        store.toast("WebHook 已保存")
                        load()
                    }.onFailure { store.toast(store.friendlyError(it), true) }
                }
            },
        )
    }

    webhookKey?.let { key ->
        AceDialog(title = "WebHook 已创建", onDismiss = { webhookKey = null }) {
            AceText("回调地址", style = AceType.caption, color = colors.text3)
            Spacer(Modifier.height(6.dp))
            val url = (store.api?.baseUrl ?: "") + "/webhook/" + key
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.fill)
                    .padding(12.dp),
            ) {
                AceText(url, style = AceType.caption.copy(fontFamily = FontFamily.Monospace), color = colors.text1)
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SoftPillButton("关闭", { webhookKey = null }, modifier = Modifier.weight(1f), height = 42.dp)
                SoftPillButton(
                    "复制",
                    {
                        clipboard.setText(AnnotatedString(url))
                        store.toast("回调地址已复制")
                    },
                    modifier = Modifier.weight(1f),
                    height = 42.dp,
                    tint = androidx.compose.ui.graphics.Color.White,
                    background = colors.accent,
                )
            }
        }
    }

    deleteWebhook?.let { hook ->
        AceConfirmDialog(
            title = "删除 WebHook",
            message = "确认删除「${hook.text("name")}」？现有回调地址将立即失效。",
            confirmText = "删除",
            danger = true,
            onDismiss = { deleteWebhook = null },
        ) {
            val id = hook.long("id")
            deleteWebhook = null
            action("WebHook 已删除", after = { scope.launch { load() } }) { store.api?.deleteWebhook(id) }
        }
    }

    templateDetail?.let { template ->
        JsonDetailDialog(
            title = template.text("name").ifBlank { template.text("slug") },
            content = template.text("compose").ifBlank { pretty(template.toString()) },
            colors = colors,
            onDismiss = { templateDetail = null },
        )
    }

    deployTemplate?.let { template ->
        AceFormDialog(
            title = "部署 ${template.text("name").ifBlank { template.text("slug") }}",
            fields = listOf(
                AceFormField("编排名称", template.text("slug")),
                AceFormField("环境变量（JSON 数组，形如 [{\"key\":\"PORT\",\"value\":\"80\"}]）", "[]", multiline = true),
            ),
            confirmText = "部署",
            onDismiss = { deployTemplate = null },
        ) { values ->
            val name = values[0].trim()
            val envsRaw = values[1].trim()
            if (name.isBlank()) {
                store.toast("请填写编排名称", true)
            } else {
                val envs = runSafely {
                    val array = com.acepanel.client.data.ApiJson.parseToJsonElement(envsRaw.ifBlank { "[]" }) as JsonArray
                    array.mapNotNull { item ->
                        val obj = item as? JsonObject ?: return@mapNotNull null
                        obj.text("key") to obj.text("value")
                    }
                }.getOrElse {
                    store.toast("环境变量 JSON 格式不正确", true)
                    return@AceFormDialog
                }
                scope.launch {
                    runSafely {
                        store.api?.deployTemplate(
                            slug = template.text("slug"),
                            name = name,
                            compose = template.text("compose"),
                            envs = envs,
                            autoFirewall = false,
                        )
                    }.onSuccess {
                        deployTemplate = null
                        store.toast("部署任务已提交")
                    }.onFailure { store.toast(store.friendlyError(it), true) }
                }
            }
        }
    }
}

@Composable
private fun ProcessRow(
    process: JsonObject,
    colors: AceColors,
    onDetail: () -> Unit,
    onSignal: () -> Unit,
    onKill: () -> Unit,
) {
    val cpu = process.text("cpu").toDoubleOrNull() ?: 0.0
    val rss = process.long("rss")
    val status = process.text("status")
    AceCard(padding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(colors.fill),
                contentAlignment = Alignment.Center,
            ) {
                AceIcon(Lucide.Cpu, tint = if (cpu > 30) colors.warning else colors.accent, size = 17.dp)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AceText(
                        process.text("name").ifBlank { "未命名" },
                        style = AceType.listPrimary,
                        color = colors.text1,
                        maxLines = 1,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    AceText("PID ${process.long("pid")}", style = AceType.caption.copy(fontFamily = FontFamily.Monospace), color = colors.text3)
                    if (status.isNotBlank()) {
                        StatusPill(status, colors.text3, colors.fill, dot = false)
                    }
                }
                AceText(
                    "CPU ${oneDecimal(cpu)}% · 内存 ${formatBytes(rss)} · ${process.text("username")}",
                    style = AceType.caption,
                    color = colors.text3,
                )
                AceText(
                    process.text("cmd_line").ifBlank { process.text("exe") },
                    style = AceType.caption.copy(fontFamily = FontFamily.Monospace),
                    color = colors.text3,
                    maxLines = 1,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            MiniChip("详情", colors, onClick = onDetail)
            MiniChip("信号", colors, onClick = onSignal)
            Spacer(Modifier.weight(1f))
            MiniChip("结束", colors, danger = true, onClick = onKill)
        }
    }
}

@Composable
private fun TaskRow(task: JsonObject, colors: AceColors, onDetail: () -> Unit, onCancel: () -> Unit, onDelete: () -> Unit) {
    val status = task.text("status")
    val running = status == "running" || status == "waiting"
    AceCard(padding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(colors.fill),
                contentAlignment = Alignment.Center,
            ) {
                AceIcon(Lucide.ListChecks, tint = if (running) colors.accent else colors.text2, size = 17.dp)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AceText(
                        task.text("name").ifBlank { "任务 #${task.long("id")}" },
                        style = AceType.listPrimary,
                        color = colors.text1,
                        maxLines = 1,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    StatusPill(
                        status.ifBlank { "未知" },
                        if (running) colors.accent else colors.text3,
                        if (running) colors.accentSoft else colors.fill,
                        dot = false,
                    )
                }
                AceText(
                    "创建于 ${task.text("created_at").dateTimePart().ifBlank { "—" }}",
                    style = AceType.caption,
                    color = colors.text3,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            MiniChip("详情", colors, onClick = onDetail)
            if (running) MiniChip("取消", colors, onClick = onCancel)
            Spacer(Modifier.weight(1f))
            MiniChip("删除", colors, danger = true, onClick = onDelete)
        }
    }
}

@Composable
private fun WebhookRow(
    hook: JsonObject,
    baseUrl: String,
    colors: AceColors,
    onCopy: (String) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val key = hook.text("key")
    val enabled = hook.boolValue("status", true)
    AceCard(padding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(colors.accentSoft),
                contentAlignment = Alignment.Center,
            ) {
                AceIcon(Lucide.Link, tint = colors.accent, size = 17.dp)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AceText(
                        hook.text("name").ifBlank { "未命名" },
                        style = AceType.listPrimary,
                        color = colors.text1,
                        maxLines = 1,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    StatusPill(
                        if (enabled) "启用" else "停用",
                        if (enabled) colors.success else colors.text3,
                        if (enabled) colors.successSoft else colors.fill,
                        dot = false,
                    )
                }
                AceText(
                    "$baseUrl/webhook/$key",
                    style = AceType.caption.copy(fontFamily = FontFamily.Monospace),
                    color = colors.text3,
                    maxLines = 1,
                )
                AceText(
                    "调用 ${hook.long("call_count")} 次 · 用户 ${hook.text("user").ifBlank { "root" }}",
                    style = AceType.caption,
                    color = colors.text3,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            MiniChip("复制地址", colors) { onCopy("$baseUrl/webhook/$key") }
            MiniChip("编辑", colors, onClick = onEdit)
            Spacer(Modifier.weight(1f))
            MiniChip("删除", colors, danger = true, onClick = onDelete)
        }
    }
}

@Composable
private fun TemplateRow(
    template: JsonObject,
    colors: AceColors,
    onDetail: () -> Unit,
    onDeploy: () -> Unit,
) {
    val categories = template.stringList("categories")
    AceCard(padding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(colors.fill),
                contentAlignment = Alignment.Center,
            ) {
                AceIcon(Lucide.Package, tint = colors.accent, size = 17.dp)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AceText(template.text("name").ifBlank { template.text("slug") }, style = AceType.listPrimary, color = colors.text1, maxLines = 1)
                    if (template.boolValue("local")) {
                        StatusPill("本地", colors.warning, colors.warningSoft, dot = false)
                    }
                }
                AceText(
                    template.text("description").ifBlank { template.text("slug") },
                    style = AceType.caption,
                    color = colors.text3,
                    maxLines = 2,
                )
                if (categories.isNotEmpty()) {
                    AceText(categories.joinToString(" · "), style = AceType.caption, color = colors.text3, maxLines = 1)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            MiniChip("查看编排", colors, onClick = onDetail)
            Spacer(Modifier.weight(1f))
            MiniChip("部署", colors) { onDeploy() }
        }
    }
}

@Composable
private fun JsonDetailDialog(title: String, content: String, colors: AceColors, onDismiss: () -> Unit) {
    AceDialog(title = title, onDismiss = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 140.dp, max = 420.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(colors.terminal)
                .padding(12.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            AceText(
                content,
                style = AceType.caption.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 16.sp),
                color = colors.terminalText,
            )
        }
        Spacer(Modifier.height(12.dp))
        PrimaryPillButton("关闭", onDismiss, height = 42.dp)
    }
}

@Composable
private fun WebhookDialog(
    hook: JsonObject?,
    colors: AceColors,
    onDismiss: () -> Unit,
    onSave: (String, String, String, Boolean, Boolean) -> Unit,
) {
    var name by remember { mutableStateOf(hook?.text("name").orEmpty()) }
    var user by remember { mutableStateOf(hook?.text("user")?.ifBlank { "root" } ?: "root") }
    var script by remember { mutableStateOf(hook?.text("script").orEmpty()) }
    var raw by remember { mutableStateOf(hook?.boolValue("raw") ?: false) }
    var enabled by remember { mutableStateOf(hook?.boolValue("status", true) ?: true) }
    AceDialog(title = if (hook == null) "新建 WebHook" else "编辑 WebHook", onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 450.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AceTextField(name, { name = it }, placeholder = "WebHook 名称", height = 44.dp, textStyle = AceType.bodySmall)
            AceTextField(user, { user = it }, placeholder = "执行用户（如 root / www）", height = 44.dp, textStyle = AceType.bodySmall)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 160.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.terminal)
                    .padding(12.dp),
            ) {
                if (script.isEmpty()) {
                    AceText("#!/bin/bash\n# WebHook 脚本", style = AceType.caption.copy(fontFamily = FontFamily.Monospace), color = colors.terminalDim)
                }
                BasicTextField(
                    value = script,
                    onValueChange = { script = it },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 136.dp),
                    textStyle = AceType.caption.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = colors.terminalText, lineHeight = 17.sp),
                    cursorBrush = SolidColor(colors.terminalGreen),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                AceText("返回原始输出", style = AceType.bodySmall, color = colors.text1, modifier = Modifier.weight(1f))
                AceSwitch(raw) { raw = it }
            }
            if (hook != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AceText("启用", style = AceType.bodySmall, color = colors.text1, modifier = Modifier.weight(1f))
                    AceSwitch(enabled) { enabled = it }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SoftPillButton("取消", onDismiss, modifier = Modifier.weight(1f), height = 44.dp)
            Box(Modifier.weight(1f)) {
                PrimaryPillButton(
                    "保存",
                    { onSave(name.trim(), user.trim().ifBlank { "root" }, script, raw, enabled) },
                    enabled = name.isNotBlank() && script.isNotBlank(),
                    height = 44.dp,
                )
            }
        }
    }
}

@Composable
private fun SegmentChip(label: String, selected: Boolean, colors: AceColors, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) colors.accentSoft else colors.fill)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        AceText(label, style = AceType.tag, color = if (selected) colors.accent else colors.text2, maxLines = 1)
    }
}

@Composable
private fun MiniChip(label: String, colors: AceColors, danger: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (danger) colors.errorSoft else colors.fill)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        AceText(label, style = AceType.tag, color = if (danger) colors.error else colors.text2)
    }
}
