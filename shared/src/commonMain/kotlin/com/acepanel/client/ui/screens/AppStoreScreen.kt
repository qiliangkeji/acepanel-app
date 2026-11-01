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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.acepanel.client.data.AceStore
import com.acepanel.client.data.HomeApp
import com.acepanel.client.data.Lv
import com.acepanel.client.ui.components.AceCard
import com.acepanel.client.ui.components.AceDialog
import com.acepanel.client.ui.components.AceIcon
import com.acepanel.client.ui.components.AceText
import com.acepanel.client.ui.components.AceTextField
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
import com.composables.icons.lucide.Activity
import com.composables.icons.lucide.Boxes
import com.composables.icons.lucide.Braces
import com.composables.icons.lucide.Code
import com.composables.icons.lucide.Container
import com.composables.icons.lucide.Database
import com.composables.icons.lucide.Globe
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Package
import com.composables.icons.lucide.Search
import com.composables.icons.lucide.ShieldCheck
import com.composables.icons.lucide.TriangleAlert
import kotlinx.coroutines.launch
import com.acepanel.client.data.runSafely

private enum class AppFilter(val label: String) { ALL("全部"), INSTALLED("已安装"), UPDATABLE("可更新") }

@Composable
fun AppStoreScreen(store: AceStore) {
    val colors = AceTheme.colors
    val scope = rememberCoroutineScope()
    var apps by remember { mutableStateOf<List<HomeApp>>(emptyList()) }
    var categories by remember { mutableStateOf<List<Lv>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(AppFilter.ALL) }
    var category by remember { mutableStateOf<String?>(null) }
    var busySlug by remember { mutableStateOf<String?>(null) }
    var manageApp by remember { mutableStateOf<HomeApp?>(null) }

    suspend fun load() {
        loading = true
        error = null
        runSafely {
            apps = store.api?.appList()?.items ?: emptyList()
            categories = store.api?.appCategories() ?: emptyList()
        }.onFailure { error = store.friendlyError(it) }
        loading = false
    }

    LaunchedEffect(store.api) { load() }

    fun install(app: HomeApp) {
        val channel = app.channels.firstOrNull()?.slug
        if (channel.isNullOrBlank()) {
            store.toast("应用渠道信息缺失，请先刷新应用缓存", true)
            return
        }
        busySlug = app.slug
        scope.launch {
            runSafely { store.api?.appInstall(app.slug, channel) }
                .onSuccess {
                    store.toast("${app.name} 已开始安装")
                    load()
                }
                .onFailure { store.toast(store.friendlyError(it), true) }
            busySlug = null
        }
    }

    fun update(app: HomeApp) {
        busySlug = app.slug
        scope.launch {
            runSafely { store.api?.appUpdate(app.slug) }
                .onSuccess {
                    store.toast("${app.name} 更新已提交")
                    load()
                }
                .onFailure { store.toast(store.friendlyError(it), true) }
            busySlug = null
        }
    }

    val installed = apps.count { it.installed }
    val updatable = apps.count { it.updateExist }
    val filtered = apps.filter { app ->
        (query.isBlank() || app.name.contains(query, true) || app.description.contains(query, true)) &&
            (category == null || app.categories.contains(category)) &&
            when (filter) {
                AppFilter.ALL -> true
                AppFilter.INSTALLED -> app.installed
                AppFilter.UPDATABLE -> app.updateExist
            }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        MobilePageHeader(
            title = "应用商店",
            subtitle = "${apps.size} 个应用 · 已装 $installed 个",
            onBack = store::closePage,
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
                placeholder = "搜索应用，如 PHP、MySQL",
                height = 46.dp,
                leading = { AceIcon(Lucide.Search, tint = colors.text3, size = 18.dp) },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                AppTab(AppFilter.ALL, apps.size, filter) { filter = it }
                AppTab(AppFilter.INSTALLED, installed, filter) { filter = it }
                AppTab(AppFilter.UPDATABLE, updatable, filter) { filter = it }
            }
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CategoryChip("全部", category == null) { category = null }
                categories.forEach { item ->
                    CategoryChip(item.label, category == item.valueText) { category = item.valueText }
                }
            }
            if (loading) {
                LoadingState(rows = 5)
            } else if (error != null) {
                ErrorState(error!!, onRetry = {
                    scope.launch { load() }
                })
            } else if (filtered.isEmpty()) {
                EmptyState(
                    title = if (query.isBlank()) "没有符合条件的应用" else "没有找到「$query」",
                    subtitle = "换个关键词，或检查分类筛选",
                    icon = Lucide.Package,
                )
            } else {
                filtered.forEach { app ->
                    AppCard(
                        app = app,
                        busy = busySlug == app.slug,
                        onInstall = { install(app) },
                        onUpdate = { update(app) },
                        onManage = { manageApp = app },
                    )
                }
            }
            val updateApps = apps.filter { it.updateExist }
            if (updateApps.isNotEmpty()) {
                InlineNotice(
                    "${updateApps.joinToString("、") { it.name }} 有新版本，升级前请先备份数据",
                    color = colors.warning,
                    background = colors.warningSoft,
                    icon = Lucide.TriangleAlert,
                )
            }
        }
    }

    manageApp?.let { app ->
        AceDialog(title = app.name, onDismiss = { manageApp = null }) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                appStoreIcon(app.slug, colors.text1, 28.dp)
                Column(modifier = Modifier.weight(1f)) {
                    AceText(app.description.ifBlank { "面板应用" }, style = AceType.secondary, color = colors.text2)
                    AceText(
                        listOfNotNull(
                            app.installedVersion.ifBlank { null }?.let { "已安装 v$it" },
                            app.channels.firstOrNull()?.version?.let { "最新 v$it" },
                        ).joinToString(" · "),
                        style = AceType.caption,
                        color = colors.text3,
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            if (app.installed) {
                SoftPillButton(
                    "卸载应用",
                    onClick = {
                        manageApp = null
                        busySlug = app.slug
                        scope.launch {
                            runSafely { store.api?.appUninstall(app.slug) }
                                .onSuccess {
                                    store.toast("${app.name} 已卸载")
                                    load()
                                }
                                .onFailure { store.toast(store.friendlyError(it), true) }
                            busySlug = null
                        }
                    },
                    height = 46.dp,
                    tint = colors.error,
                    background = colors.errorSoft,
                )
                Spacer(Modifier.height(10.dp))
            }
            if (app.updateExist) {
                PrimaryPillButton("更新到最新版", { manageApp = null; update(app) }, height = 46.dp)
            } else if (!app.installed) {
                PrimaryPillButton("安装", { manageApp = null; install(app) }, height = 46.dp)
            }
        }
    }
}

@Composable
private fun AppTab(filter: AppFilter, count: Int, selected: AppFilter, onSelect: (AppFilter) -> Unit) {
    val colors = AceTheme.colors
    val active = filter == selected
    Column(
        modifier = Modifier.clickable { onSelect(filter) },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            AceText(filter.label, style = AceType.tab.copy(fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal), color = if (active) colors.text1 else colors.text2)
            AceText(count.toString(), style = AceType.caption, color = if (active) colors.accent else colors.text3)
        }
        Box(Modifier.width(if (active) 22.dp else 0.dp).height(3.dp).clip(RoundedCornerShape(999.dp)).background(colors.accent))
    }
}

@Composable
private fun CategoryChip(label: String, active: Boolean, onClick: () -> Unit) {
    val colors = AceTheme.colors
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (active) colors.accentSoft else colors.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        AceText(label, style = AceType.caption.copy(fontWeight = if (active) FontWeight.Medium else FontWeight.Normal), color = if (active) colors.accent else colors.text2)
    }
}

@Composable
private fun AppCard(app: HomeApp, busy: Boolean, onInstall: () -> Unit, onUpdate: () -> Unit, onManage: () -> Unit) {
    val colors = AceTheme.colors
    AceCard(padding = 16.dp) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(colors.surface2),
                contentAlignment = Alignment.Center,
            ) {
                appStoreIcon(app.slug, colors.text1, 22.dp)
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                AceText(app.name, style = AceType.listStrong, color = colors.text1)
                AceText(
                    app.description.ifBlank { app.categories.joinToString(" · ") },
                    style = AceType.caption,
                    color = colors.text3,
                    maxLines = 1,
                )
                if (app.installed && app.installedVersion.isNotBlank()) {
                    AceText(
                        "v${app.installedVersion}${app.channels.firstOrNull()?.version?.let { " · 最新 $it" } ?: ""}",
                        style = AceType.caption.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                        color = colors.text3,
                    )
                }
            }
            val actionText = when {
                busy -> "处理中"
                app.updateExist -> "更新"
                app.installed -> "管理"
                else -> "安装"
            }
            val primary = !app.installed || app.updateExist
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (primary) colors.accent else colors.accentSoft)
                    .clickable(enabled = !busy) {
                        when {
                            app.updateExist -> onUpdate()
                            app.installed -> onManage()
                            else -> onInstall()
                        }
                    }
                    .padding(horizontal = 15.dp, vertical = 7.dp),
            ) {
                AceText(actionText, style = AceType.caption.copy(fontWeight = FontWeight.Medium), color = if (primary) Color.White else colors.accent)
            }
        }
    }
}

@Composable
private fun appStoreIcon(slug: String, tint: Color, size: androidx.compose.ui.unit.Dp) {
    val icon: ImageVector = when (slug.lowercase()) {
        "mysql", "mariadb", "percona", "postgresql", "redis", "clickhouse", "mongodb" -> Lucide.Database
        "nginx", "openresty", "apache" -> Lucide.Globe
        "docker", "podman" -> Lucide.Container
        "php" -> Lucide.Code
        "nodejs" -> Lucide.Braces
        "prometheus", "grafana" -> Lucide.Activity
        "fail2ban" -> Lucide.ShieldCheck
        else -> Lucide.Boxes
    }
    AceIcon(icon, tint = tint, size = size)
}
