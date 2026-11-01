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
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.acepanel.client.data.AceStore
import com.acepanel.client.data.FirewallRule
import com.acepanel.client.data.IpRule
import com.acepanel.client.data.boolean
import com.acepanel.client.data.obj
import com.acepanel.client.data.objOrNull
import com.acepanel.client.data.string
import com.acepanel.client.ui.components.AceCard
import com.acepanel.client.ui.components.AceDialog
import com.acepanel.client.ui.components.AceIcon
import com.acepanel.client.ui.components.AceSegmented
import com.acepanel.client.ui.components.AceText
import com.acepanel.client.ui.components.AceTextField
import com.acepanel.client.ui.components.CircleAction
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
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.CircleAlert
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Radar
import com.composables.icons.lucide.RefreshCw
import com.composables.icons.lucide.ShieldCheck
import com.composables.icons.lucide.ShieldOff
import com.composables.icons.lucide.Trash2
import com.composables.icons.lucide.X
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import com.acepanel.client.data.runSafely

@Composable
fun SecurityScreen(store: AceStore) {
    val colors = AceTheme.colors
    val scope = rememberCoroutineScope()
    var enabled by remember { mutableStateOf(false) }
    var rules by remember { mutableStateOf<List<FirewallRule>>(emptyList()) }
    var ipRules by remember { mutableStateOf<List<IpRule>>(emptyList()) }
    var tamper by remember { mutableStateOf(false) }
    var scanTotal by remember { mutableStateOf(0) }
    var scanIps by remember { mutableStateOf(0) }
    var topPorts by remember { mutableStateOf<List<Pair<Int, Int>>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var showRules by remember { mutableStateOf(false) }
    var showScan by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    suspend fun load() {
        loading = true
        error = null
        runSafely {
            val fw = store.api?.firewallStatus()
            enabled = (fw as? JsonPrimitive)?.content?.toBooleanStrictOrNull() ?: false
            rules = store.api?.firewallRules()?.items ?: emptyList()
            ipRules = store.api?.ipRules()?.items ?: emptyList()
            val tamperJson = store.api?.tamperStatus()
            tamper = tamperJson.obj()?.objOrNull("setting")?.boolean("enabled") ?: false
            val summary = store.api?.scanSummary()
            scanTotal = summary?.get("total_count")?.let { (it as? JsonPrimitive)?.content?.toIntOrNull() ?: 0 } ?: 0
            scanIps = summary?.get("unique_ips")?.let { (it as? JsonPrimitive)?.content?.toIntOrNull() ?: 0 } ?: 0
            val ports = store.api?.getData("/api/firewall/scan/top_ports")
            topPorts = ports?.let {
                runSafely {
                    kotlinx.serialization.json.Json { ignoreUnknownKeys = true }.decodeFromString<List<TopPort>>(it.toString())
                }.getOrDefault(emptyList()).map { p -> p.port to p.total_count }
            } ?: emptyList()
        }.onFailure { error = store.friendlyError(it) }
        loading = false
    }

    LaunchedEffect(store.api) { load() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        MobilePageHeader(
            title = "安全",
            subtitle = "${if (enabled) "防火墙已启用" else "防火墙未启用"} · ${rules.size} 条规则",
            onBack = store::closePage,
            actions = {
                CircleAction(Lucide.RefreshCw, { scope.launch { load() } }, contentDescription = "刷新安全状态")
            },
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScreenScrollState())
                .padding(start = 26.dp, end = 26.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (loading) {
                LoadingState(rows = 4)
            } else if (error != null) {
                ErrorState(error!!, onRetry = { scope.launch { load() } })
            } else {
                ProtectionCard(enabled, rules.size, ipRules.size, tamper, scanTotal)
                AceCard(padding = 0.dp) {
                    Column {
                        Row(
                            Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AceText("防火墙规则", style = AceType.section, color = colors.text1, modifier = Modifier.weight(1f))
                            AceText(
                                "全部 ${rules.size} 条",
                                style = AceType.caption.copy(fontWeight = FontWeight.Medium),
                                color = colors.accent,
                                modifier = Modifier.clickable { showRules = true },
                            )
                        }
                        rules.take(5).forEachIndexed { index, rule ->
                            FirewallRuleRow(rule)
                            if (index != rules.take(5).lastIndex) RowDivider()
                        }
                        if (rules.isEmpty()) {
                            AceText("防火墙暂无端口规则", style = AceType.secondary, color = colors.text3, modifier = Modifier.padding(18.dp))
                        }
                    }
                }
                AceCard(padding = 0.dp) {
                    Column {
                        Row(
                            Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AceText("IP 黑名单", style = AceType.section, color = colors.text1, modifier = Modifier.weight(1f))
                            AceText("${ipRules.size} 个", style = AceType.caption, color = colors.text3)
                        }
                        if (ipRules.isEmpty()) {
                            AceText("没有被封禁的 IP", style = AceType.secondary, color = colors.text3, modifier = Modifier.padding(18.dp))
                        } else {
                            ipRules.take(5).forEachIndexed { index, rule ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    AceIcon(Lucide.CircleAlert, tint = colors.error, size = 16.dp)
                                    Column(Modifier.weight(1f)) {
                                        AceText(rule.address, style = AceType.bodySmall.copy(fontFamily = FontFamily.Monospace), color = colors.text1)
                                        AceText(rule.strategy.ifBlank { "drop" } + " · " + rule.direction.ifBlank { "in" }, style = AceType.caption, color = colors.text3)
                                    }
                                    AceText("解封", style = AceType.caption.copy(fontWeight = FontWeight.Medium), color = colors.accent, modifier = Modifier.clickable {
                                        if (busy) return@clickable
                                        busy = true
                                        scope.launch {
                                            runSafely { store.api?.deleteIpRule(rule) }
                                                .onSuccess { store.toast("已解封 ${rule.address}"); load() }
                                                .onFailure { store.toast(store.friendlyError(it), true) }
                                            busy = false
                                        }
                                    })
                                }
                                if (index != ipRules.take(5).lastIndex) RowDivider()
                            }
                        }
                    }
                }
                if (scanTotal > 0) {
                    InlineNotice(
                        "扫描感知已记录 $scanTotal 次探测，来自 $scanIps 个 IP。",
                        color = colors.warning,
                        background = colors.warningSoft,
                        icon = Lucide.Radar,
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 26.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SoftPillButton("一键安全扫描", {
                if (busy) return@SoftPillButton
                showScan = true
            }, modifier = Modifier.weight(1f), height = 50.dp, leadingIcon = Lucide.Radar, background = colors.surface)
        }
    }

    if (showRules) {
        RulesDialog(store, rules, { showRules = false }) { scope.launch { load() } }
    }
    if (showScan) {
        AceDialog(title = "安全扫描概况", onDismiss = { showScan = false }) {
            AceText("面板防护状态", style = AceType.caption, color = colors.text3)
            Spacer(Modifier.height(8.dp))
            ScanLine("防火墙", if (enabled) "已启用" else "未启用", enabled)
            ScanLine("防篡改", if (tamper) "已开启" else "未开启", tamper)
            ScanLine("端口规则", "${rules.size} 条", rules.isNotEmpty())
            ScanLine("封禁 IP", "${ipRules.size} 个", ipRules.isEmpty())
            Spacer(Modifier.height(8.dp))
            AceText("扫描感知", style = AceType.caption, color = colors.text3)
            Spacer(Modifier.height(6.dp))
            AceText("共 $scanTotal 次探测 · $scanIps 个来源 IP", style = AceType.bodySmall, color = colors.text2)
            if (topPorts.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                AceText(
                    "高频目标端口：" + topPorts.take(4).joinToString("、") { it.first.toString() },
                    style = AceType.caption.copy(fontFamily = FontFamily.Monospace),
                    color = colors.text3,
                )
            }
            Spacer(Modifier.height(16.dp))
            PrimaryPillButton("完成", { showScan = false }, height = 44.dp)
        }
    }
}

private data class TopPort(val port: Int = 0, val total_count: Int = 0)

@Composable
private fun ProtectionCard(enabled: Boolean, ruleCount: Int, blocked: Int, tamper: Boolean, scans: Int) {
    val colors = AceTheme.colors
    val color = if (enabled) colors.success else colors.warning
    AceCard(padding = 18.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier.size(46.dp).clip(CircleShape).background(color.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    AceIcon(if (enabled) Lucide.ShieldCheck else Lucide.ShieldOff, tint = color, size = 23.dp)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    AceText(if (enabled) "服务器处于防护状态" else "防火墙未启用", style = AceType.listStrong, color = colors.text1)
                    AceText(
                        "防火墙 $ruleCount 条规则 · 失败登录 $scans 次被记录",
                        style = AceType.caption,
                        color = colors.text3,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ProtectionStat("封禁 IP", blocked.toString(), Modifier.weight(1f))
                ProtectionStat("规则", ruleCount.toString(), Modifier.weight(1f))
                ProtectionStat("防篡改", if (tamper) "已开" else "关闭", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ProtectionStat(label: String, value: String, modifier: Modifier = Modifier) {
    val colors = AceTheme.colors
    Box(
        modifier = modifier.clip(RoundedCornerShape(12.dp)).background(colors.surface2).padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            AceText(label, style = AceType.caption.copy(fontSize = 10.5.sp), color = colors.text3)
            AceText(value, style = AceType.bodySmall.copy(fontWeight = FontWeight.SemiBold), color = colors.text1)
        }
    }
}

@Composable
private fun FirewallRuleRow(rule: FirewallRule) {
    val colors = AceTheme.colors
    val allow = rule.strategy == "accept"
    val port = if (rule.portStart == rule.portEnd) rule.portStart.toString() else "${rule.portStart}-${rule.portEnd}"
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AceText(port, style = AceType.bodySmall.copy(fontFamily = FontFamily.Monospace), color = colors.text1, modifier = Modifier.width(92.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            AceText(
                rule.address.ifBlank { if (rule.portStart in listOf(22, 80, 443)) "Web / SSH 服务" else "自定义服务" },
                style = AceType.caption,
                color = colors.text2,
                maxLines = 1,
            )
            AceText("${rule.protocol.ifBlank { "tcp" }} · ${rule.direction.ifBlank { "in" }}", style = AceType.caption.copy(fontSize = 10.sp), color = colors.text3)
        }
        StatusPill(
            if (allow) "允许" else "拒绝",
            if (allow) colors.success else colors.error,
            if (allow) colors.successSoft else colors.errorSoft,
        )
    }
}

@Composable
private fun RulesDialog(store: AceStore, rules: List<FirewallRule>, onDismiss: () -> Unit, onChanged: () -> Unit) {
    val colors = AceTheme.colors
    val scope = rememberCoroutineScope()
    var adding by remember { mutableStateOf(false) }
    var port by remember { mutableStateOf("") }
    var endPort by remember { mutableStateOf("") }
    var protocol by remember { mutableStateOf(0) }
    var allow by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    AceDialog(title = "防火墙规则", onDismiss = onDismiss) {
        if (!adding) {
            AceText("共 ${rules.size} 条端口规则", style = AceType.caption, color = colors.text3)
            Spacer(Modifier.height(8.dp))
            Box(Modifier.height(220.dp).fillMaxWidth().verticalScroll(rememberScrollState())) {
                Column {
                    rules.forEach { rule ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                            AceText(
                                if (rule.portStart == rule.portEnd) "${rule.portStart}" else "${rule.portStart}-${rule.portEnd}",
                                style = AceType.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                color = colors.text1,
                                modifier = Modifier.width(62.dp),
                            )
                            AceText(rule.protocol, style = AceType.caption, color = colors.text3, modifier = Modifier.width(40.dp))
                            AceText(
                                rule.address.ifBlank { "0.0.0.0/0" },
                                style = AceType.caption.copy(fontFamily = FontFamily.Monospace),
                                color = colors.text3,
                                modifier = Modifier.weight(1f),
                            )
                            StatusPill(
                                if (rule.strategy == "accept") "允许" else "拒绝",
                                if (rule.strategy == "accept") colors.success else colors.error,
                                if (rule.strategy == "accept") colors.successSoft else colors.errorSoft,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            PrimaryPillButton("添加规则", { adding = true }, height = 44.dp, leadingIcon = Lucide.Plus)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AceTextField(port, { port = it.filter { c -> c.isDigit() }.take(5) }, placeholder = "起始端口", modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                AceTextField(endPort, { endPort = it.filter { c -> c.isDigit() }.take(5) }, placeholder = "结束端口", modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            }
            Spacer(Modifier.height(10.dp))
            AceSegmented(listOf("TCP", "UDP", "TCP/UDP"), protocol, onSelect = { protocol = it })
            Spacer(Modifier.height(10.dp))
            AceSegmented(listOf("允许", "拒绝"), if (allow) 0 else 1, onSelect = { allow = it == 0 })
            error?.let {
                Spacer(Modifier.height(8.dp))
                InlineNotice(it, color = colors.error, background = colors.errorSoft)
            }
            Spacer(Modifier.height(14.dp))
            PrimaryPillButton(if (busy) "保存中…" else "保存规则", {
                if (busy) return@PrimaryPillButton
                val start = port.toIntOrNull()
                val end = endPort.toIntOrNull() ?: start
                if (start == null || end == null || start < 1 || end > 65535 || end < start) {
                    error = "请输入有效的端口范围"
                    return@PrimaryPillButton
                }
                busy = true
                scope.launch {
                    runSafely {
                        store.api?.createFirewallRule(
                            portStart = start,
                            portEnd = end,
                            protocol = when (protocol) {
                                0 -> "tcp"
                                1 -> "udp"
                                else -> "tcp/udp"
                            },
                            strategy = if (allow) "accept" else "drop",
                        )
                    }.onSuccess {
                        store.toast("防火墙规则已添加")
                        onChanged()
                        onDismiss()
                    }.onFailure { error = store.friendlyError(it) }
                    busy = false
                }
            }, height = 44.dp)
        }
    }
}

@Composable
private fun ScanLine(label: String, value: String, ok: Boolean) {
    val colors = AceTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        AceIcon(if (ok) Lucide.Check else Lucide.X, tint = if (ok) colors.success else colors.warning, size = 14.dp)
        Spacer(Modifier.width(8.dp))
        AceText(label, style = AceType.caption, color = colors.text2, modifier = Modifier.weight(1f))
        AceText(value, style = AceType.caption.copy(fontWeight = FontWeight.Medium), color = colors.text1)
    }
}

@Composable
private fun RowDivider() {
    Box(Modifier.fillMaxWidth().padding(start = 18.dp).height(1.dp).background(AceTheme.colors.divider))
}
