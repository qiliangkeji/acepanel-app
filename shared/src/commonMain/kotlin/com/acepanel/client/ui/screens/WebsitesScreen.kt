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
import com.acepanel.client.data.Website
import com.acepanel.client.data.datePart
import com.acepanel.client.ui.components.AceCard
import com.acepanel.client.ui.components.AceDialog
import com.acepanel.client.ui.components.AceIcon
import com.acepanel.client.ui.components.AceSegmented
import com.acepanel.client.ui.components.AceText
import com.acepanel.client.ui.components.AceTextField
import com.acepanel.client.ui.components.CircleAction
import com.acepanel.client.ui.components.EmptyState
import com.acepanel.client.ui.components.ErrorState
import com.acepanel.client.ui.components.InlineNotice
import com.acepanel.client.ui.components.LoadingState
import com.acepanel.client.ui.components.MobilePageHeader
import com.acepanel.client.ui.components.PanelDirectoryPickerDialog
import com.acepanel.client.ui.components.PrimaryPillButton
import com.acepanel.client.ui.components.SoftPillButton
import com.acepanel.client.ui.components.StatusPill
import com.acepanel.client.ui.components.rememberScreenScrollState
import com.acepanel.client.ui.theme.AceTheme
import com.acepanel.client.ui.theme.AceType
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Globe
import com.composables.icons.lucide.Lock
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.ShieldCheck
import com.composables.icons.lucide.Search
import com.composables.icons.lucide.TriangleAlert
import com.composables.icons.lucide.LockOpen
import kotlinx.coroutines.launch
import com.acepanel.client.data.runSafely

private enum class WebsiteFilter(val label: String) { ALL("全部"), RUNNING("运行中"), STOPPED("已停止") }

@Composable
fun WebsitesScreen(store: AceStore) {
    val colors = AceTheme.colors
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<Website>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(WebsiteFilter.ALL) }
    var showCreate by remember { mutableStateOf(false) }
    var manage by remember { mutableStateOf<Website?>(null) }
    var busy by remember { mutableStateOf(false) }

    suspend fun load() {
        loading = true
        error = null
        runSafely { items = store.api?.websites()?.items ?: emptyList() }
            .onFailure { error = store.friendlyError(it) }
        loading = false
    }

    LaunchedEffect(store.api) { load() }

    val running = items.count { it.status }
    val filtered = items.filter {
        (query.isBlank() || it.name.contains(query, true) || it.entryDomain.contains(query, true) || it.remark.contains(query, true)) &&
            when (filter) {
                WebsiteFilter.ALL -> true
                WebsiteFilter.RUNNING -> it.status
                WebsiteFilter.STOPPED -> !it.status
            }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        MobilePageHeader(
            title = "网站",
            subtitle = "${items.size} 个站点 · ${items.count { it.ssl }} 个启用 HTTPS",
            onBack = store::closePage,
            actions = {
                CircleAction(Lucide.Plus, { showCreate = true }, background = colors.accent, tint = Color.White, contentDescription = "新建站点")
            },
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScreenScrollState())
                .padding(start = 26.dp, end = 26.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            AceTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = "搜索域名或备注",
                height = 46.dp,
                leading = { AceIcon(Lucide.Search, tint = colors.text3, size = 18.dp) },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                WebsiteTab(WebsiteFilter.ALL, items.size, filter) { filter = it }
                WebsiteTab(WebsiteFilter.RUNNING, running, filter) { filter = it }
                WebsiteTab(WebsiteFilter.STOPPED, items.size - running, filter) { filter = it }
            }
            when {
                loading -> LoadingState(rows = 4)
                error != null -> ErrorState(error!!, onRetry = { scope.launch { load() } })
                filtered.isEmpty() -> EmptyState(
                    title = if (items.isEmpty()) "还没有网站" else "没有匹配的站点",
                    subtitle = if (items.isEmpty()) "创建站点并绑定域名，配置 SSL 与运行环境" else "换个关键词或筛选条件试试",
                    icon = Lucide.Globe,
                    actionText = if (items.isEmpty()) "新建站点" else null,
                    onAction = { showCreate = true },
                )
                else -> filtered.forEach { site ->
                    WebsiteCard(site) { manage = site }
                }
            }
            if (items.any { it.ssl && it.certExpire.isNotBlank() }) {
                val expiring = items.filter { it.ssl && it.certExpire.isNotBlank() && daysUntil(it.certExpire) in 0..30 }
                if (expiring.isNotEmpty()) {
                    InlineNotice(
                        "${expiring.first().entryDomain} 的证书即将到期，请及时续签",
                        color = colors.warning,
                        background = colors.warningSoft,
                        icon = Lucide.TriangleAlert,
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 26.dp, vertical = 14.dp),
        ) {
            PrimaryPillButton("新建站点", { showCreate = true }, height = 50.dp, leadingIcon = Lucide.Plus)
        }
    }

    if (showCreate) {
        CreateWebsiteDialog(
            store = store,
            onDismiss = { showCreate = false },
            onCreated = {
                showCreate = false
                scope.launch { load() }
            },
        )
    }

    manage?.let { site ->
        AceDialog(title = site.entryDomain, onDismiss = { manage = null }) {
            AceText(
                "${site.typeLabel} · ${site.path.ifBlank { "面板默认目录" }}",
                style = AceType.caption.copy(fontFamily = FontFamily.Monospace),
                color = colors.text3,
            )
            Spacer(Modifier.height(16.dp))
            SoftPillButton(
                if (site.status) "停止站点" else "启动站点",
                onClick = {
                    if (busy) return@SoftPillButton
                    busy = true
                    scope.launch {
                        runSafely { store.api?.websiteStatus(site.id, !site.status) }
                            .onSuccess {
                                store.toast(if (site.status) "站点已停止" else "站点已启动")
                                manage = null
                                load()
                            }
                            .onFailure { store.toast(store.friendlyError(it), true) }
                        busy = false
                    }
                },
                height = 46.dp,
                tint = if (site.status) colors.warning else colors.success,
                background = if (site.status) colors.warningSoft else colors.successSoft,
            )
            Spacer(Modifier.height(10.dp))
            SoftPillButton(
                "删除站点",
                onClick = {
                    if (busy) return@SoftPillButton
                    busy = true
                    scope.launch {
                        runSafely { store.api?.deleteWebsite(site.id) }
                            .onSuccess {
                                store.toast("站点已删除")
                                manage = null
                                load()
                            }
                            .onFailure { store.toast(store.friendlyError(it), true) }
                        busy = false
                    }
                },
                height = 46.dp,
                tint = colors.error,
                background = colors.errorSoft,
            )
        }
    }
}

@Composable
private fun WebsiteTab(filter: WebsiteFilter, count: Int, selected: WebsiteFilter, onSelect: (WebsiteFilter) -> Unit) {
    val colors = AceTheme.colors
    val active = selected == filter
    Column(modifier = Modifier.clickable { onSelect(filter) }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            AceText(filter.label, style = AceType.tab.copy(fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal), color = if (active) colors.text1 else colors.text2)
            AceText(count.toString(), style = AceType.caption, color = if (active) colors.accent else colors.text3)
        }
        Box(Modifier.width(if (active) 22.dp else 0.dp).height(3.dp).clip(RoundedCornerShape(999.dp)).background(colors.accent))
    }
}

@Composable
private fun WebsiteCard(site: Website, onManage: () -> Unit) {
    val colors = AceTheme.colors
    AceCard(padding = 18.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AceIcon(Lucide.Globe, tint = if (site.status) colors.accent else colors.text3, size = 20.dp)
                AceText(site.entryDomain, style = AceType.listStrong, color = colors.text1, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                StatusPill(
                    if (site.status) "运行中" else "已停止",
                    if (site.status) colors.success else colors.text3,
                    if (site.status) colors.successSoft else colors.fill,
                )
                AceIcon(Lucide.ChevronRight, tint = colors.text3, size = 15.dp)
            }
            AceText(
                "${site.path.ifBlank { "—" }} · ${site.typeLabel}",
                style = AceType.caption.copy(fontFamily = FontFamily.Monospace),
                color = colors.text3,
                maxLines = 1,
            )
            Box(Modifier.fillMaxWidth().height(1.dp).background(colors.divider))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AceIcon(if (site.ssl) Lucide.Lock else Lucide.LockOpen, tint = if (site.ssl) colors.warning else colors.text3, size = 14.dp)
                    AceText(
                        if (site.ssl) "HTTPS · ${site.certExpire.ifBlank { "证书已部署" }}" else "HTTP · 未开启 HTTPS",
                        style = AceType.caption,
                        color = if (site.ssl) colors.text2 else colors.text3,
                        maxLines = 1,
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(colors.accentSoft)
                        .clickable(onClick = onManage)
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                ) {
                    AceText("管理", style = AceType.caption.copy(fontWeight = FontWeight.Medium), color = colors.accent)
                }
            }
        }
    }
}

@Composable
private fun CreateWebsiteDialog(store: AceStore, onDismiss: () -> Unit, onCreated: () -> Unit) {
    val colors = AceTheme.colors
    val scope = rememberCoroutineScope()
    var domain by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(0) }
    var path by remember { mutableStateOf("") }
    var showPathPicker by remember { mutableStateOf(false) }
    var proxy by remember { mutableStateOf("") }
    var remark by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var phpVersions by remember { mutableStateOf<List<String>>(emptyList()) }
    var phpLoading by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        runSafely { store.api?.environments("php")?.filter { it.installed }?.map { it.slug } }
            .onSuccess { phpVersions = it ?: emptyList() }
        phpLoading = false
    }

    AceDialog(title = "新建站点", onDismiss = onDismiss) {
        AceText("域名", style = AceType.caption, color = colors.text3)
        Spacer(Modifier.height(6.dp))
        AceTextField(domain, { domain = it }, placeholder = "example.com")
        Spacer(Modifier.height(12.dp))
        AceText("站点类型", style = AceType.caption, color = colors.text3)
        Spacer(Modifier.height(6.dp))
        AceSegmented(listOf("静态", "反向代理", "PHP"), type, onSelect = { type = it })
        Spacer(Modifier.height(12.dp))
        if (type == 1) {
            AceText("上游地址", style = AceType.caption, color = colors.text3)
            Spacer(Modifier.height(6.dp))
            AceTextField(proxy, { proxy = it }, placeholder = "http://127.0.0.1:3000")
            Spacer(Modifier.height(12.dp))
        }
        if (type == 2) {
            if (phpLoading) {
                AceText("正在读取可用 PHP 版本…", style = AceType.caption, color = colors.text3)
            } else if (phpVersions.isEmpty()) {
                InlineNotice("未安装 PHP 运行环境，请先在「运行环境」中安装 PHP 后再创建 PHP 站点。", color = colors.warning, background = colors.warningSoft)
            } else {
                AceText("PHP 版本：${phpVersions.first().let { slug -> if (slug.length >= 2) slug.substring(0, 1) + "." + slug.substring(1) else slug }}", style = AceType.caption, color = colors.text3)
            }
            Spacer(Modifier.height(12.dp))
        }
        AceText("站点目录（选填）", style = AceType.caption, color = colors.text3)
        Spacer(Modifier.height(6.dp))
        AceTextField(
            path,
            { path = it },
            placeholder = "/var/www/${domain.ifBlank { "example" }}",
            trailing = {
                AceText(
                    "选择",
                    style = AceType.caption,
                    color = colors.accent,
                    modifier = Modifier.clickable { showPathPicker = true }.padding(horizontal = 4.dp),
                )
            },
        )
        Spacer(Modifier.height(12.dp))
        AceText("备注", style = AceType.caption, color = colors.text3)
        Spacer(Modifier.height(6.dp))
        AceTextField(remark, { remark = it }, placeholder = "选填")
        error?.let {
            Spacer(Modifier.height(10.dp))
            InlineNotice(it, color = colors.error, background = colors.errorSoft)
        }
        Spacer(Modifier.height(16.dp))
        PrimaryPillButton(
            if (busy) "创建中…" else "创建站点",
            onClick = {
                if (busy) return@PrimaryPillButton
                if (domain.isBlank()) {
                    error = "请输入域名"
                    return@PrimaryPillButton
                }
                if (type == 1 && proxy.isBlank()) {
                    error = "请输入反向代理上游地址"
                    return@PrimaryPillButton
                }
                if (type == 2 && phpVersions.isEmpty()) {
                    error = "未安装 PHP 运行环境，无法创建 PHP 站点"
                    return@PrimaryPillButton
                }
                busy = true
                error = null
                val cleanDomain = domain.trim().lowercase()
                val name = cleanDomain.removePrefix("www.").replace("*.".toRegex(), "")
                scope.launch {
                    runSafely {
                        store.api?.createWebsite(
                            name = name.ifBlank { "site" },
                            domains = listOf(cleanDomain),
                            type = when (type) {
                                0 -> "static"
                                1 -> "proxy"
                                else -> "php"
                            },
                            path = path.trim(),
                            php = if (type == 2) phpVersions.first().toIntOrNull() ?: 0 else 0,
                            proxy = proxy.trim(),
                            remark = remark.trim(),
                        )
                    }.onSuccess {
                        store.toast("站点已创建")
                        onCreated()
                    }.onFailure { error = store.friendlyError(it) }
                    busy = false
                }
            },
            enabled = !busy,
            height = 48.dp,
        )
    }

    if (showPathPicker) {
        PanelDirectoryPickerDialog(
            store = store,
            initialPath = path.ifBlank { "/var/www" },
            title = "选择站点目录",
            onDismiss = { showPathPicker = false },
        ) { selected ->
            path = selected
            showPathPicker = false
        }
    }
}

private fun daysUntil(date: String): Long {
    val cleaned = date.datePart()
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
