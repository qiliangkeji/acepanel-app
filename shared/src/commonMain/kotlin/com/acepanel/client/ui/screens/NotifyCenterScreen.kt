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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.acepanel.client.data.AceStore
import com.acepanel.client.data.dateTimePart
import com.acepanel.client.data.long
import com.acepanel.client.data.string
import com.acepanel.client.ui.components.AceCard
import com.acepanel.client.ui.components.AceConfirmDialog
import com.acepanel.client.ui.components.AceDialog
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
import com.composables.icons.lucide.Bell
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Mail
import com.composables.icons.lucide.Plus
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import com.acepanel.client.data.runSafely

private val alertTypeOptions = listOf(
    "cpu" to "CPU",
    "memory" to "内存",
    "swap" to "Swap",
    "load1" to "负载 1m",
    "load5" to "负载 5m",
    "load15" to "负载 15m",
    "disk" to "磁盘使用率",
    "disk_inode" to "inode",
    "disk_read" to "磁盘读",
    "disk_write" to "磁盘写",
    "net_in" to "入站流量",
    "net_out" to "出站流量",
    "website_5xx" to "网站 5xx",
    "website_error" to "网站错误",
    "service" to "系统服务",
    "project" to "项目",
    "container" to "容器",
    "app" to "应用",
    "database" to "数据库",
    "cert_expire" to "证书到期",
    "website_expire" to "网站到期",
)

private val operatorOptions = listOf("gt" to ">", "gte" to "≥", "lt" to "<", "lte" to "≤")

private fun JsonObject.text(key: String): String = when (val value = this[key]) {
    null, JsonNull -> ""
    is JsonPrimitive -> if (value.isString) value.content else if (value.content == "null") "" else value.content
    else -> value.toString()
}

private fun JsonObject.boolValue(key: String, default: Boolean = false): Boolean =
    (this[key] as? JsonPrimitive)?.booleanOrNull ?: default

private fun JsonObject.strings(key: String): List<String> =
    (this[key] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content } ?: emptyList()

private fun JsonObject.longList(key: String): List<Long> =
    (this[key] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content?.toLongOrNull() } ?: emptyList()

@Composable
fun NotifyCenterScreen(store: AceStore) {
    val colors = AceTheme.colors
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var channels by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var rules by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var channelEditor by remember { mutableStateOf<JsonObject?>(null) }
    var ruleEditor by remember { mutableStateOf<JsonObject?>(null) }
    var deleteChannel by remember { mutableStateOf<JsonObject?>(null) }
    var deleteRule by remember { mutableStateOf<JsonObject?>(null) }

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
            val channelList = client.notifyChannelsAll()
            val rulePage = client.alertRules(1, 100)
            channelList to rulePage.items
        }.onSuccess { (channelList, ruleList) ->
            channels = channelList
            rules = ruleList
        }.onFailure { error = store.friendlyError(it) }
        loading = false
    }

    LaunchedEffect(store.api) { load() }

    fun refresh() {
        scope.launch { load() }
    }

    fun action(success: String, block: suspend () -> Unit) {
        scope.launch {
            runSafely { block() }
                .onSuccess {
                    store.toast(success)
                    load()
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
            title = "通知与告警",
            subtitle = "通知渠道 · 告警规则 · 事件触达",
            onBack = store::closePage,
            actions = {
                SoftPillButton("刷新", { refresh() }, height = 36.dp, tint = colors.text2, background = colors.fill)
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
                options = listOf("通知渠道", "告警规则"),
                selectedIndex = tab,
                onSelect = { tab = it },
            )
            when {
                loading -> LoadingState(rows = 4)
                error != null -> ErrorState(error!!) { refresh() }
                tab == 0 -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AceText(
                            "SMTP 通知渠道",
                            style = AceType.section.copy(fontSize = 14.sp),
                            color = colors.text1,
                            modifier = Modifier.weight(1f),
                        )
                        SoftPillButton(
                            "新建渠道",
                            { channelEditor = JsonObject(emptyMap()) },
                            height = 34.dp,
                            tint = colors.accent,
                            background = colors.accentSoft,
                        )
                    }
                    if (channels.isEmpty()) {
                        EmptyState(
                            title = "暂无通知渠道",
                            subtitle = "创建 SMTP 渠道后，告警规则即可发送邮件通知",
                            icon = Lucide.Mail,
                        )
                    }
                    channels.forEach { channel ->
                        ChannelCard(
                            channel = channel,
                            colors = colors,
                            onTest = {
                                action("测试邮件已发送") { store.api?.testNotifyChannel(channel.long("id")) }
                            },
                            onEdit = { channelEditor = channel },
                            onDelete = { deleteChannel = channel },
                        )
                    }
                }
                else -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            AceText("告警规则", style = AceType.section.copy(fontSize = 14.sp), color = colors.text1)
                            AceText("${rules.size} 条规则 · 触发后推送到所选渠道", style = AceType.caption, color = colors.text3)
                        }
                        SoftPillButton(
                            "新建规则",
                            { ruleEditor = JsonObject(emptyMap()) },
                            height = 34.dp,
                            tint = colors.accent,
                            background = colors.accentSoft,
                        )
                    }
                    if (rules.isEmpty()) {
                        EmptyState(
                            title = "暂无告警规则",
                            subtitle = "可对 CPU、内存、磁盘、网站、证书等指标设置阈值",
                            icon = Lucide.Bell,
                        )
                    }
                    rules.forEach { rule ->
                        RuleCard(
                            rule = rule,
                            channelCount = channels.size,
                            colors = colors,
                            onEdit = { ruleEditor = rule },
                            onDelete = { deleteRule = rule },
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    channelEditor?.let { channel ->
        val isNew = channel.isEmpty()
        ChannelDialog(
            channel = channel.takeIf { !isNew },
            onDismiss = { channelEditor = null },
            onSave = { name, config, enabled ->
                scope.launch {
                    runSafely {
                        val client = store.api
                        if (isNew) {
                            client?.createNotifyChannel(name, "smtp", config, enabled)
                        } else {
                            client?.updateNotifyChannel(channel.long("id"), name, "smtp", config, enabled)
                        }
                    }.onSuccess {
                        channelEditor = null
                        store.toast("渠道已保存")
                        load()
                    }.onFailure { store.toast(store.friendlyError(it), true) }
                }
            },
        )
    }

    ruleEditor?.let { rule ->
        val isNew = rule.isEmpty()
        RuleDialog(
            rule = rule.takeIf { !isNew },
            channels = channels,
            onDismiss = { ruleEditor = null },
            onSave = { body ->
                scope.launch {
                    runSafely {
                        if (isNew) store.api?.createAlertRule(body)
                        else store.api?.updateAlertRule(rule.long("id"), body)
                    }.onSuccess {
                        ruleEditor = null
                        store.toast("规则已保存")
                        load()
                    }.onFailure { store.toast(store.friendlyError(it), true) }
                }
            },
        )
    }

    deleteChannel?.let { channel ->
        AceConfirmDialog(
            title = "删除通知渠道",
            message = "确认删除「${channel.text("name")}」？使用该渠道的告警规则将不再发送通知。",
            confirmText = "删除",
            danger = true,
            onDismiss = { deleteChannel = null },
        ) {
            val id = channel.long("id")
            deleteChannel = null
            action("渠道已删除") { store.api?.deleteNotifyChannel(id) }
        }
    }

    deleteRule?.let { rule ->
        AceConfirmDialog(
            title = "删除告警规则",
            message = "确认删除规则「${rule.text("name")}」？历史告警记录不会被删除。",
            confirmText = "删除",
            danger = true,
            onDismiss = { deleteRule = null },
        ) {
            val id = rule.long("id")
            deleteRule = null
            action("规则已删除") { store.api?.deleteAlertRule(id) }
        }
    }
}

@Composable
private fun ChannelCard(
    channel: JsonObject,
    colors: AceColors,
    onTest: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val config = channel["config"] as? JsonObject ?: JsonObject(emptyMap())
    val to = config.strings("to")
    val enabled = channel.boolValue("enabled", true)
    AceCard(padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).background(colors.accentSoft),
                contentAlignment = Alignment.Center,
            ) {
                AceIcon(Lucide.Mail, tint = colors.accent, size = 18.dp)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AceText(
                        channel.text("name").ifBlank { "未命名渠道" },
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
                    "${config.text("host").ifBlank { "SMTP" }}:${config.text("port").ifBlank { "-" }}",
                    style = AceType.caption.copy(fontFamily = FontFamily.Monospace),
                    color = colors.text3,
                )
                AceText(
                    if (to.isEmpty()) "收件人未设置" else "收件人：${to.joinToString(", ")}",
                    style = AceType.caption,
                    color = colors.text3,
                    maxLines = 1,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            MiniChip("测试", colors, onClick = onTest)
            MiniChip("编辑", colors, onClick = onEdit)
            Spacer(Modifier.weight(1f))
            MiniChip("删除", colors, danger = true, onClick = onDelete)
        }
    }
}

@Composable
private fun RuleCard(
    rule: JsonObject,
    channelCount: Int,
    colors: AceColors,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val enabled = rule.boolValue("enabled", true)
    val type = rule.text("type")
    val typeLabel = alertTypeOptions.firstOrNull { it.first == type }?.second ?: type
    val operator = operatorOptions.firstOrNull { it.first == rule.text("operator") }?.second ?: rule.text("operator")
    val channelIds = rule.longList("channels")
    AceCard(padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).background(
                    if (enabled) colors.warningSoft else colors.fill,
                ),
                contentAlignment = Alignment.Center,
            ) {
                AceIcon(Lucide.Bell, tint = if (enabled) colors.warning else colors.text3, size = 18.dp)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AceText(
                        rule.text("name").ifBlank { "未命名规则" },
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
                    "$typeLabel $operator ${formatNumber(rule.text("threshold"))}" +
                        " · 持续 ${rule.text("duration").ifBlank { "1" }} 次 · 静默 ${rule.text("silence").ifBlank { "30" }} 分钟",
                    style = AceType.caption,
                    color = colors.text3,
                )
                AceText(
                    buildString {
                        if (rule.text("target").isNotBlank()) append("目标：${rule.text("target")} · ")
                        append("通知渠道：${channelIds.size} 个")
                        if (channelCount == 0) append("（尚未创建渠道）")
                    },
                    style = AceType.caption,
                    color = colors.text3,
                    maxLines = 1,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            MiniChip("编辑", colors, onClick = onEdit)
            Spacer(Modifier.weight(1f))
            MiniChip("删除", colors, danger = true, onClick = onDelete)
        }
    }
}

private fun formatNumber(value: String): String {
    val number = value.toDoubleOrNull() ?: return value
    return if (number == number.toLong().toDouble()) number.toLong().toString()
    else number.toString()
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

@Composable
private fun ChannelDialog(
    channel: JsonObject?,
    onDismiss: () -> Unit,
    onSave: (String, JsonObject, Boolean) -> Unit,
) {
    val colors = AceTheme.colors
    val config = channel?.get("config") as? JsonObject ?: JsonObject(emptyMap())
    var name by remember { mutableStateOf(channel?.text("name").orEmpty()) }
    var host by remember { mutableStateOf(config.text("host")) }
    var port by remember { mutableStateOf(config.text("port").ifBlank { "465" }) }
    var encryption by remember { mutableStateOf(config.text("encryption").ifBlank { "ssl" }) }
    var username by remember { mutableStateOf(config.text("username")) }
    var password by remember { mutableStateOf(config.text("password")) }
    var from by remember { mutableStateOf(config.text("from")) }
    var fromName by remember { mutableStateOf(config.text("from_name")) }
    var to by remember { mutableStateOf(config.strings("to").joinToString(", ")) }
    var skipVerify by remember { mutableStateOf(config.boolValue("skip_verify")) }
    var enabled by remember { mutableStateOf(channel?.boolValue("enabled", true) ?: true) }
    var error by remember { mutableStateOf<String?>(null) }

    AceDialog(title = if (channel == null) "新建通知渠道" else "编辑通知渠道", onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 450.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AceTextField(name, { name = it }, placeholder = "渠道名称", height = 44.dp, textStyle = AceType.bodySmall)
            AceTextField(host, { host = it }, placeholder = "SMTP 服务器 host", height = 44.dp, textStyle = AceType.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) {
                    AceTextField(port, { port = it }, placeholder = "端口", height = 44.dp, textStyle = AceType.bodySmall, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number))
                }
                OptionScroll(options = listOf("ssl", "starttls", "none"), selected = encryption, colors = colors) { encryption = it }
            }
            AceTextField(username, { username = it }, placeholder = "SMTP 用户名", height = 44.dp, textStyle = AceType.bodySmall)
            AceTextField(password, { password = it }, placeholder = "SMTP 密码", height = 44.dp, textStyle = AceType.bodySmall, visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
            AceTextField(from, { from = it }, placeholder = "发件邮箱", height = 44.dp, textStyle = AceType.bodySmall)
            AceTextField(fromName, { fromName = it }, placeholder = "发件人名称（可选）", height = 44.dp, textStyle = AceType.bodySmall)
            AceTextField(to, { to = it }, placeholder = "收件人，逗号分隔", height = 44.dp, textStyle = AceType.bodySmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                AceText("跳过 TLS 证书校验", style = AceType.bodySmall, color = colors.text1, modifier = Modifier.weight(1f))
                AceSwitch(skipVerify) { skipVerify = it }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                AceText("启用渠道", style = AceType.bodySmall, color = colors.text1, modifier = Modifier.weight(1f))
                AceSwitch(enabled) { enabled = it }
            }
            error?.let {
                AceText(it, style = AceType.caption, color = colors.error)
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SoftPillButton("取消", onDismiss, modifier = Modifier.weight(1f), height = 44.dp)
            Box(Modifier.weight(1f)) {
                PrimaryPillButton("保存", {
                    val recipients = to.split(',', '\n').map { it.trim() }.filter { it.isNotEmpty() }
                    if (name.isBlank() || host.isBlank()) {
                        error = "请填写渠道名称和 SMTP 服务器"
                    } else if (recipients.isEmpty()) {
                        error = "请至少填写一个收件人"
                    } else {
                        onSave(
                            name.trim(),
                            buildJsonObject {
                                put("host", host.trim())
                                put("port", port.toIntOrNull() ?: 465)
                                put("encryption", encryption)
                                put("username", username.trim())
                                put("password", password)
                                put("from", from.trim())
                                put("from_name", fromName.trim())
                                put("to", JsonArray(recipients.map(::JsonPrimitive)))
                                put("skip_verify", skipVerify)
                            },
                            enabled,
                        )
                    }
                }, height = 44.dp)
            }
        }
    }
}

@Composable
private fun RuleDialog(
    rule: JsonObject?,
    channels: List<JsonObject>,
    onDismiss: () -> Unit,
    onSave: (JsonObject) -> Unit,
) {
    val colors = AceTheme.colors
    var name by remember { mutableStateOf(rule?.text("name").orEmpty()) }
    var type by remember { mutableStateOf(rule?.text("type")?.ifBlank { "cpu" } ?: "cpu") }
    var target by remember { mutableStateOf(rule?.text("target").orEmpty()) }
    var operator by remember { mutableStateOf(rule?.text("operator")?.ifBlank { "gt" } ?: "gt") }
    var threshold by remember { mutableStateOf(rule?.text("threshold")?.let(::formatNumber).orEmpty().ifBlank { "90" }) }
    var duration by remember { mutableStateOf(rule?.text("duration")?.ifBlank { "3" } ?: "3") }
    var silence by remember { mutableStateOf(rule?.text("silence")?.ifBlank { "30" } ?: "30") }
    var selectedChannels by remember {
        mutableStateOf(rule?.longList("channels")?.toSet() ?: emptySet())
    }
    var enabled by remember { mutableStateOf(rule?.boolValue("enabled", true) ?: true) }
    var error by remember { mutableStateOf<String?>(null) }

    AceDialog(title = if (rule == null) "新建告警规则" else "编辑告警规则", onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AceTextField(name, { name = it }, placeholder = "规则名称", height = 44.dp, textStyle = AceType.bodySmall)
            AceText("监控指标", style = AceType.caption, color = colors.text3)
            // 4 列时每格 ~59dp，"磁盘使用率" 这类五个字的指标名放不下
            alertTypeOptions.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    row.forEach { (value, label) ->
                        OptionChip(
                            label = label,
                            selected = type == value,
                            colors = colors,
                            modifier = Modifier.weight(1f),
                        ) { type = value }
                    }
                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            AceTextField(target, { target = it }, placeholder = "监控目标（挂载点 / 网卡 / 服务名 / 域名，可留空）", height = 44.dp, textStyle = AceType.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                AceText("条件", style = AceType.caption, color = colors.text3)
                OptionScroll(options = operatorOptions.map { it.first }, selected = operator, display = { value ->
                    operatorOptions.firstOrNull { it.first == value }?.second ?: value
                }, colors = colors) { operator = it }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) {
                    AceTextField(threshold, { threshold = it }, placeholder = "阈值", height = 44.dp, textStyle = AceType.bodySmall)
                }
                Box(Modifier.weight(1f)) {
                    AceTextField(duration, { duration = it }, placeholder = "持续次数", height = 44.dp, textStyle = AceType.bodySmall)
                }
                Box(Modifier.weight(1f)) {
                    AceTextField(silence, { silence = it }, placeholder = "静默分钟", height = 44.dp, textStyle = AceType.bodySmall)
                }
            }
            AceText("通知渠道", style = AceType.caption, color = colors.text3)
            if (channels.isEmpty()) {
                AceText("尚未创建通知渠道，规则仍可保存但不会发送通知。", style = AceType.caption, color = colors.warning)
            } else {
                channels.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        row.forEach { channel ->
                            val id = channel.long("id")
                            OptionChip(
                                label = channel.text("name").ifBlank { "渠道 $id" },
                                selected = id in selectedChannels,
                                colors = colors,
                                modifier = Modifier.weight(1f),
                            ) {
                                selectedChannels = if (id in selectedChannels) selectedChannels - id else selectedChannels + id
                            }
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                AceText("启用规则", style = AceType.bodySmall, color = colors.text1, modifier = Modifier.weight(1f))
                AceSwitch(enabled) { enabled = it }
            }
            error?.let { AceText(it, style = AceType.caption, color = colors.error) }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SoftPillButton("取消", onDismiss, modifier = Modifier.weight(1f), height = 44.dp)
            Box(Modifier.weight(1f)) {
                PrimaryPillButton("保存", {
                    if (name.isBlank()) {
                        error = "请填写规则名称"
                    } else {
                        onSave(
                            buildJsonObject {
                                put("name", name.trim())
                                put("type", type)
                                put("target", target.trim())
                                put("operator", operator)
                                put("threshold", threshold.toDoubleOrNull() ?: 0.0)
                                put("duration", (duration.toIntOrNull() ?: 1).coerceIn(1, 60))
                                put("silence", (silence.toIntOrNull() ?: 0).coerceIn(0, 1440))
                                put("channels", JsonArray(selectedChannels.sorted().map(::JsonPrimitive)))
                                put("enabled", enabled)
                            },
                        )
                    }
                }, height = 44.dp)
            }
        }
    }
}

@Composable
private fun OptionChip(
    label: String,
    selected: Boolean,
    colors: AceColors,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) colors.accentSoft else colors.fill)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        AceText(
            label,
            style = AceType.tag,
            color = if (selected) colors.accent else colors.text2,
            maxLines = 1,
        )
    }
}

@Composable
private fun OptionScroll(
    options: List<String>,
    selected: String,
    colors: AceColors,
    display: (String) -> String = { it },
    onSelect: (String) -> Unit,
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        options.forEach { option ->
            OptionChip(display(option), selected == option, colors) { onSelect(option) }
        }
    }
}
