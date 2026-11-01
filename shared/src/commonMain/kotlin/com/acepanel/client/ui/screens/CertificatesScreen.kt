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
import com.acepanel.client.data.Cert
import com.acepanel.client.data.CertAccount
import com.acepanel.client.data.CertDns
import com.acepanel.client.data.datePart
import com.acepanel.client.data.decodeOrNull
import com.acepanel.client.ui.components.AceCard
import com.acepanel.client.ui.components.AceConfirmDialog
import com.acepanel.client.ui.components.AceDialog
import com.acepanel.client.ui.components.AceFormDialog
import com.acepanel.client.ui.components.AceFormField
import com.acepanel.client.ui.components.AceIcon
import com.acepanel.client.ui.components.AceSegmented
import com.acepanel.client.ui.components.AceText
import com.acepanel.client.ui.components.AceTextField
import com.acepanel.client.ui.components.AceSwitch
import com.acepanel.client.ui.components.CircleAction
import com.acepanel.client.ui.components.EmptyState
import com.acepanel.client.ui.components.ErrorState
import com.acepanel.client.ui.components.InlineNotice
import com.acepanel.client.ui.components.LoadingState
import com.acepanel.client.ui.components.MobilePageHeader
import com.acepanel.client.ui.components.PrimaryPillButton
import com.acepanel.client.ui.components.ProgressLine
import com.acepanel.client.ui.components.SoftPillButton
import com.acepanel.client.ui.components.StatusPill
import com.acepanel.client.ui.components.rememberScreenScrollState
import com.acepanel.client.ui.theme.AceTheme
import com.acepanel.client.ui.theme.AceType
import com.composables.icons.lucide.KeyRound
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.RefreshCw
import com.composables.icons.lucide.Shield
import com.composables.icons.lucide.ShieldCheck
import com.composables.icons.lucide.TriangleAlert
import kotlinx.coroutines.launch
import com.acepanel.client.data.runSafely

@Composable
fun CertificatesScreen(store: AceStore) {
    val colors = AceTheme.colors
    val scope = rememberCoroutineScope()
    var certs by remember { mutableStateOf<List<Cert>>(emptyList()) }
    var accounts by remember { mutableStateOf<List<CertAccount>>(emptyList()) }
    var dnsRecords by remember { mutableStateOf<List<CertDns>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var showCreate by remember { mutableStateOf(false) }
    var showAccounts by remember { mutableStateOf(false) }
    var showDns by remember { mutableStateOf(false) }
    var showAccountForm by remember { mutableStateOf(false) }
    var showDnsForm by remember { mutableStateOf(false) }
    var deleteAccount by remember { mutableStateOf<CertAccount?>(null) }
    var deleteDns by remember { mutableStateOf<CertDns?>(null) }
    var manage by remember { mutableStateOf<Cert?>(null) }
    var busy by remember { mutableStateOf(false) }

    suspend fun load() {
        loading = true
        error = null
        runSafely {
            certs = store.api?.certs()?.items ?: emptyList()
            accounts = store.api?.certAccounts()?.items ?: emptyList()
            dnsRecords = store.api?.certDns()?.items ?: emptyList()
        }.onFailure { error = store.friendlyError(it) }
        loading = false
    }

    LaunchedEffect(store.api) { load() }

    val expiring = certs.filter { daysLeft(it) in 0..30 }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        MobilePageHeader(
            title = "SSL 证书",
            subtitle = "${certs.size} 张 · ${expiring.size} 张即将到期",
            onBack = store::closePage,
            actions = {
                CircleAction(Lucide.Plus, { showCreate = true }, background = colors.accent, tint = Color.White, contentDescription = "申请证书")
            },
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScreenScrollState())
                .padding(start = 26.dp, end = 26.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // 360dp 宽的标题栏里塞“账户 / DNS / +”三个操作时，最右侧的主操作会被挤出屏幕；
            // 两个次级入口挪到内容区顶部，标题栏只保留“申请证书”。
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SoftPillButton("账户", { showAccounts = true }, height = 34.dp, tint = colors.text2, background = colors.fill)
                SoftPillButton("DNS 解析", { showDns = true }, height = 34.dp, tint = colors.text2, background = colors.fill)
            }
            if (loading) {
                LoadingState(rows = 5)
            } else if (error != null) {
                ErrorState(error!!, onRetry = { scope.launch { load() } })
            } else {
                if (expiring.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(colors.warningSoft)
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        AceIcon(Lucide.TriangleAlert, tint = colors.warning, size = 18.dp)
                        Column(Modifier.weight(1f)) {
                            AceText(
                                "${expiring.first().title} ${daysLeft(expiring.first())} 天后到期",
                                style = AceType.bodySmall.copy(fontWeight = FontWeight.Medium),
                                color = colors.text1,
                            )
                            AceText("Let's Encrypt · 建议开启自动续期", style = AceType.caption, color = colors.text3)
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(colors.surface)
                                .clickable {
                                    scope.launch {
                                        runSafely { store.api?.certRenew(expiring.first().id) }
                                            .onSuccess { store.toast("续签任务已提交"); load() }
                                            .onFailure { store.toast(store.friendlyError(it), true) }
                                    }
                                }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        ) {
                            AceText("续期", style = AceType.caption.copy(fontWeight = FontWeight.Medium), color = colors.warning)
                        }
                    }
                }
                if (certs.isEmpty()) {
                    EmptyState(
                        title = "还没有证书",
                        subtitle = "申请 Let's Encrypt 或上传自定义证书",
                        icon = Lucide.KeyRound,
                        actionText = "申请新证书",
                        onAction = { showCreate = true },
                    )
                } else {
                    certs.forEach { cert ->
                        CertCard(cert) { manage = cert }
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().padding(horizontal = 26.dp, vertical = 14.dp)) {
            PrimaryPillButton("申请新证书", { showCreate = true }, height = 50.dp, leadingIcon = Lucide.Plus)
        }
    }

    if (showCreate) {
        CreateCertDialog(store, accounts, { showCreate = false }) {
            showCreate = false
            scope.launch { load() }
        }
    }
    if (showAccounts) {
        AceDialog(title = "ACME 账户", onDismiss = { showAccounts = false }) {
            if (accounts.isEmpty()) {
                AceText("尚未配置 ACME 账户，将只能签发自签名证书。", style = AceType.caption, color = colors.warning)
            }
            accounts.forEach { account ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        AceText(account.email.ifBlank { "账户 #${account.id}" }, style = AceType.bodySmall, color = colors.text1)
                        AceText(
                            "${account.type.ifBlank { "acme" }} · ${account.ca.ifBlank { "letsencrypt" }}",
                            style = AceType.caption,
                            color = colors.text3,
                        )
                    }
                    SoftPillButton("删除", { deleteAccount = account }, height = 30.dp, tint = colors.error, background = colors.errorSoft)
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SoftPillButton("关闭", { showAccounts = false }, modifier = Modifier.weight(1f), height = 42.dp)
                Box(Modifier.weight(1f)) {
                    PrimaryPillButton("添加账户", { showAccountForm = true }, height = 42.dp)
                }
            }
        }
    }

    if (showDns) {
        AceDialog(title = "DNS 服务商", onDismiss = { showDns = false }) {
            if (dnsRecords.isEmpty()) {
                AceText("尚未配置 DNS 服务商，无法使用 DNS-01 验证签发通配符证书。", style = AceType.caption, color = colors.warning)
            }
            dnsRecords.forEach { dns ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        AceText(dns.name.ifBlank { "DNS #${dns.id}" }, style = AceType.bodySmall, color = colors.text1)
                        AceText(dns.type, style = AceType.caption.copy(fontFamily = FontFamily.Monospace), color = colors.text3)
                    }
                    SoftPillButton("删除", { deleteDns = dns }, height = 30.dp, tint = colors.error, background = colors.errorSoft)
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SoftPillButton("关闭", { showDns = false }, modifier = Modifier.weight(1f), height = 42.dp)
                Box(Modifier.weight(1f)) {
                    PrimaryPillButton("添加 DNS", { showDnsForm = true }, height = 42.dp)
                }
            }
        }
    }

    if (showAccountForm) {
        AceFormDialog(
            title = "添加 ACME 账户",
            fields = listOf(
                AceFormField("CA（letsencrypt / google / zerossl / sslcom / litessl / googlecn）", "letsencrypt"),
                AceFormField("联系邮箱"),
                AceFormField("密钥类型（P256 / P384 / 2048 / 3072 / 4096）", "P256"),
                AceFormField("EAB KID（可选）"),
                AceFormField("EAB HMAC（可选）"),
            ),
            confirmText = "添加",
            onDismiss = { showAccountForm = false },
        ) { values ->
            val email = values.getOrElse(1) { "" }.trim()
            if (email.isBlank()) {
                store.toast("请填写联系邮箱", true)
            } else {
                scope.launch {
                    runSafely {
                        store.api?.createCertAccount(
                            ca = values[0].trim().ifBlank { "letsencrypt" },
                            email = email,
                            keyType = values[2].trim().ifBlank { "P256" },
                            kid = values.getOrElse(3) { "" }.trim(),
                            hmacEncoded = values.getOrElse(4) { "" }.trim(),
                        )
                    }.onSuccess {
                        showAccountForm = false
                        store.toast("账户已添加")
                        load()
                    }.onFailure { store.toast(store.friendlyError(it), true) }
                }
            }
        }
    }

    if (showDnsForm) {
        AceFormDialog(
            title = "添加 DNS 服务商",
            fields = listOf(
                AceFormField("类型（aliyun / tencent / huawei / cloudflare / gcore / porkbun / namesilo / cloudns / westcn）", "cloudflare"),
                AceFormField("名称"),
                AceFormField("凭据 JSON（如 {\"api_token\":\"...\"}）", multiline = true),
            ),
            confirmText = "添加",
            onDismiss = { showDnsForm = false },
        ) { values ->
            val name = values.getOrElse(1) { "" }.trim()
            val data = runSafely {
                com.acepanel.client.data.ApiJson.parseToJsonElement(
                    values.getOrElse(2) { "{}" }.ifBlank { "{}" },
                ) as kotlinx.serialization.json.JsonObject
            }.getOrNull()
            if (name.isBlank() || data == null) {
                store.toast("请填写名称和有效的凭据 JSON", true)
            } else {
                scope.launch {
                    runSafely {
                        store.api?.createCertDns(values[0].trim().ifBlank { "cloudflare" }, name, data)
                    }.onSuccess {
                        showDnsForm = false
                        store.toast("DNS 服务商已添加")
                        load()
                    }.onFailure { store.toast(store.friendlyError(it), true) }
                }
            }
        }
    }

    deleteAccount?.let { account ->
        AceConfirmDialog(
            title = "删除 ACME 账户",
            message = "确认删除账户「${account.email.ifBlank { "#${account.id}" }}」？已签发证书不受影响。",
            confirmText = "删除",
            danger = true,
            onDismiss = { deleteAccount = null },
        ) {
            val id = account.id
            deleteAccount = null
            scope.launch {
                runSafely { store.api?.deleteCertAccount(id) }
                    .onSuccess { store.toast("账户已删除"); load() }
                    .onFailure { store.toast(store.friendlyError(it), true) }
            }
        }
    }

    deleteDns?.let { dns ->
        AceConfirmDialog(
            title = "删除 DNS 服务商",
            message = "确认删除「${dns.name.ifBlank { "#${dns.id}" }}」？",
            confirmText = "删除",
            danger = true,
            onDismiss = { deleteDns = null },
        ) {
            val id = dns.id
            deleteDns = null
            scope.launch {
                runSafely { store.api?.deleteCertDns(id) }
                    .onSuccess { store.toast("DNS 服务商已删除"); load() }
                    .onFailure { store.toast(store.friendlyError(it), true) }
            }
        }
    }

    manage?.let { cert ->
        AceDialog(title = cert.title, onDismiss = { manage = null }) {
            AceText("签发者 ${cert.issuer.ifBlank { "未知" }}", style = AceType.caption, color = colors.text3)
            AceText("到期 ${cert.notAfter.datePart().ifBlank { "未知" }}", style = AceType.caption, color = colors.text3)
            Spacer(Modifier.height(16.dp))
            SoftPillButton(if (busy) "续签中…" else "续签证书", {
                if (busy) return@SoftPillButton
                busy = true
                scope.launch {
                    runSafely { store.api?.certRenew(cert.id) }
                        .onSuccess { store.toast("续签任务已提交"); manage = null; load() }
                        .onFailure { store.toast(store.friendlyError(it), true) }
                    busy = false
                }
            }, height = 46.dp, tint = colors.accent, background = colors.accentSoft)
            Spacer(Modifier.height(10.dp))
            SoftPillButton("删除证书", {
                scope.launch {
                    runSafely { store.api?.deleteCert(cert.id) }
                        .onSuccess { store.toast("证书已删除"); manage = null; load() }
                        .onFailure { store.toast(store.friendlyError(it), true) }
                }
            }, height = 46.dp, tint = colors.error, background = colors.errorSoft)
        }
    }
}

@Composable
private fun CertCard(cert: Cert, onClick: () -> Unit) {
    val colors = AceTheme.colors
    val days = daysLeft(cert)
    val expiring = days in 0..30
    val expired = days < 0
    val color = when {
        expired -> colors.text3
        expiring -> colors.warning
        else -> colors.success
    }
    val progress = if (expired) 0.0 else (days.coerceAtLeast(0) / 90.0 * 100).coerceIn(0.0, 100.0)
    AceCard(modifier = Modifier.clickable(onClick = onClick), padding = 16.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AceIcon(if (cert.autoRenewal) Lucide.ShieldCheck else Lucide.Shield, tint = color, size = 20.dp)
                AceText(cert.title, style = AceType.listStrong, color = colors.text1, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                StatusPill(
                    when {
                        expired -> "已过期"
                        cert.autoRenewal -> "自动续期"
                        expiring -> "即将到期"
                        else -> "有效"
                    },
                    color,
                    color.copy(alpha = 0.12f),
                    dot = false,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                AceText(
                    cert.issuer.ifBlank { if (cert.type == "upload") "自定义证书" else "Let's Encrypt" },
                    style = AceType.caption,
                    color = colors.text3,
                    modifier = Modifier.weight(1f),
                )
                AceText(
                    "到期 ${cert.notAfter.datePart().ifBlank { "—" }}",
                    style = AceType.caption.copy(fontFamily = FontFamily.Monospace),
                    color = if (expiring) colors.warning else colors.text3,
                )
            }
            ProgressLine(progress, color, height = 4.dp)
        }
    }
}

@Composable
private fun CreateCertDialog(store: AceStore, accounts: List<CertAccount>, onDismiss: () -> Unit, onCreated: () -> Unit) {
    val colors = AceTheme.colors
    val scope = rememberCoroutineScope()
    var domain by remember { mutableStateOf("") }
    var accountIndex by remember { mutableStateOf(0) }
    var auto by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    AceDialog(title = "申请证书", onDismiss = onDismiss) {
        AceText("域名", style = AceType.caption, color = colors.text3)
        Spacer(Modifier.height(6.dp))
        AceTextField(domain, { domain = it }, placeholder = "example.com")
        Spacer(Modifier.height(12.dp))
        AceText("签发方式", style = AceType.caption, color = colors.text3)
        Spacer(Modifier.height(6.dp))
        if (accounts.isEmpty()) {
            InlineNotice("未配置 ACME 账户，将签发自签名证书。可在面板 Web 的「证书 → 账户」中添加 Let's Encrypt 账户。", color = colors.warning, background = colors.warningSoft)
        } else {
            AceSegmented(accounts.map { it.email.ifBlank { "账户 ${it.id}" } }, accountIndex, onSelect = { accountIndex = it })
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                AceText("自动续期", style = AceType.listPrimary, color = colors.text1)
                AceText("到期前由面板自动续签", style = AceType.caption, color = colors.text3)
            }
            AceSwitch(auto, onCheckedChange = { auto = it })
        }
        error?.let {
            Spacer(Modifier.height(10.dp))
            InlineNotice(it, color = colors.error, background = colors.errorSoft)
        }
        Spacer(Modifier.height(16.dp))
        PrimaryPillButton(if (busy) "提交中…" else "申请证书", {
            if (busy) return@PrimaryPillButton
            val clean = domain.trim().lowercase()
            if (clean.isBlank()) {
                error = "请输入域名"
                return@PrimaryPillButton
            }
            busy = true
            scope.launch {
                runSafely {
                    val created = store.api?.createCert(
                        domains = listOf(clean),
                        autoRenewal = auto,
                        accountId = accounts.getOrNull(accountIndex)?.id ?: 0,
                    )
                    val cert = created.decodeOrNull<Cert>()
                    if (cert == null) kotlin.error("证书创建响应为空")
                    if (accounts.isEmpty()) store.api?.obtainCertSelfSigned(cert.id)
                    else store.api?.obtainCertAuto(cert.id)
                }.onSuccess {
                    store.toast("证书申请任务已提交")
                    onCreated()
                }.onFailure { error = store.friendlyError(it) }
                busy = false
            }
        }, height = 46.dp)
    }
}

private fun daysLeft(cert: Cert): Long {
    val cleaned = cert.notAfter.datePart()
    if (cleaned.length < 10) return -1
    return runSafely {
        val parts = cleaned.split("-").map { it.toInt() }
        val today = com.acepanel.client.data.currentTimeMillis() / 86_400_000L
        epochDay(parts[0], parts[1], parts[2]) - today
    }.getOrDefault(-1)
}

private fun epochDay(year: Int, month: Int, day: Int): Long {
    var y = year.toLong()
    val m = month.toLong()
    val d = day.toLong()
    y -= if (m <= 2) 1 else 0
    val era = (if (y >= 0) y else y - 399) / 400
    val yoe = y - era * 400
    val doy = (153 * (if (m > 2) m - 3 else m + 9) + 2) / 5 + d - 1
    val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
    return era * 146_097 + doe - 719_468
}
