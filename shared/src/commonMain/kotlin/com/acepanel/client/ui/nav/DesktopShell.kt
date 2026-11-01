package com.acepanel.client.ui.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.acepanel.client.data.AceStore
import com.acepanel.client.data.PageKey
import com.acepanel.client.data.RootTab
import com.acepanel.client.ui.components.AceCard
import com.acepanel.client.ui.components.AceDialog
import com.acepanel.client.ui.components.AceIcon
import com.acepanel.client.ui.components.AceLogoMark
import com.acepanel.client.ui.components.AceText
import com.acepanel.client.ui.components.AceTextField
import com.acepanel.client.ui.components.CircleAction
import com.acepanel.client.ui.components.RowChevron
import com.acepanel.client.ui.components.SoftPillButton
import com.acepanel.client.ui.components.StatusPill
import com.acepanel.client.ui.screens.AddServerScreen
import com.acepanel.client.ui.screens.AccountCenterScreen
import com.acepanel.client.ui.screens.AlertsScreen
import com.acepanel.client.ui.screens.ApiCenterScreen
import com.acepanel.client.ui.screens.AppStoreScreen
import com.acepanel.client.ui.screens.BackupsScreen
import com.acepanel.client.ui.screens.CertificatesScreen
import com.acepanel.client.ui.screens.ContainersScreen
import com.acepanel.client.ui.screens.CronScreen
import com.acepanel.client.ui.screens.DataCenterScreen
import com.acepanel.client.ui.screens.DatabasesScreen
import com.acepanel.client.ui.screens.DesktopOverviewScreen
import com.acepanel.client.ui.screens.EnvironmentsScreen
import com.acepanel.client.ui.screens.FeatureCenterScreen
import com.acepanel.client.ui.screens.FilesScreen
import com.acepanel.client.ui.screens.LogsScreen
import com.acepanel.client.ui.screens.MonitorScreen
import com.acepanel.client.ui.screens.NotifyCenterScreen
import com.acepanel.client.ui.screens.OpsCenterScreen
import com.acepanel.client.ui.screens.ProjectsScreen
import com.acepanel.client.ui.screens.SecurityScreen
import com.acepanel.client.ui.screens.SettingsScreen
import com.acepanel.client.ui.screens.SshTerminalScreen
import com.acepanel.client.ui.screens.TerminalTabScreen
import com.acepanel.client.ui.screens.ToolboxScreen
import com.acepanel.client.ui.screens.WebsitesScreen
import com.acepanel.client.ui.theme.AceTheme
import com.acepanel.client.ui.theme.AceType
import com.acepanel.client.ui.theme.LocalDesktopLayout
import com.composables.icons.lucide.Activity
import com.composables.icons.lucide.Archive
import com.composables.icons.lucide.Bell
import com.composables.icons.lucide.Box
import com.composables.icons.lucide.Braces
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.ChevronUp
import com.composables.icons.lucide.Clock
import com.composables.icons.lucide.Database
import com.composables.icons.lucide.FileText
import com.composables.icons.lucide.Folder
import com.composables.icons.lucide.Globe
import com.composables.icons.lucide.KeyRound
import com.composables.icons.lucide.LayoutGrid
import com.composables.icons.lucide.ListChecks
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.LogOut
import com.composables.icons.lucide.Package
import com.composables.icons.lucide.RefreshCw
import com.composables.icons.lucide.Rocket
import com.composables.icons.lucide.Search
import com.composables.icons.lucide.Server
import com.composables.icons.lucide.Settings
import com.composables.icons.lucide.ShieldCheck
import com.composables.icons.lucide.SquareTerminal
import com.composables.icons.lucide.UserRound
import com.composables.icons.lucide.Wrench
import kotlinx.coroutines.launch

private data class DesktopNavItem(
    val label: String,
    val icon: ImageVector,
    val tab: RootTab? = null,
    val page: PageKey? = null,
)

private val coreNav = listOf(
    DesktopNavItem("总览", Lucide.LayoutGrid, tab = RootTab.OVERVIEW),
    DesktopNavItem("服务器", Lucide.Server, tab = RootTab.SERVERS),
    DesktopNavItem("终端", Lucide.SquareTerminal, tab = RootTab.TERMINAL),
    DesktopNavItem("网站", Lucide.Globe, page = PageKey.WEBSITES),
    DesktopNavItem("数据库", Lucide.Database, page = PageKey.DATABASES),
    DesktopNavItem("文件", Lucide.Folder, page = PageKey.FILES),
    DesktopNavItem("容器", Lucide.Box, page = PageKey.CONTAINERS),
    DesktopNavItem("监控", Lucide.Activity, tab = RootTab.MONITOR),
    DesktopNavItem("安全", Lucide.ShieldCheck, page = PageKey.SECURITY),
    DesktopNavItem("计划任务", Lucide.ListChecks, page = PageKey.CRON),
    DesktopNavItem("设置", Lucide.Settings, page = PageKey.SETTINGS),
)

private val extraNav = listOf(
    DesktopNavItem("应用商店", Lucide.Package, page = PageKey.APPS),
    DesktopNavItem("运行环境", Lucide.Rocket, page = PageKey.ENVIRONMENTS),
    DesktopNavItem("备份", Lucide.Archive, page = PageKey.BACKUPS),
    DesktopNavItem("SSL 证书", Lucide.KeyRound, page = PageKey.CERTS),
    DesktopNavItem("日志", Lucide.FileText, page = PageKey.LOGS),
    DesktopNavItem("项目", Lucide.ListChecks, page = PageKey.PROJECTS),
    DesktopNavItem("工具箱", Lucide.Wrench, page = PageKey.TOOLBOX),
    DesktopNavItem("告警", Lucide.Bell, page = PageKey.ALERTS),
    DesktopNavItem("接口中心", Lucide.Braces, page = PageKey.API_CENTER),
    DesktopNavItem("用户与安全", Lucide.UserRound, page = PageKey.ACCOUNT_CENTER),
    DesktopNavItem("通知与告警", Lucide.Bell, page = PageKey.NOTIFY_CENTER),
    DesktopNavItem("数据服务", Lucide.Database, page = PageKey.DATA_CENTER),
    DesktopNavItem("自动化与进程", Lucide.Activity, page = PageKey.OPS_CENTER),
)

@Composable
fun DesktopShell(store: AceStore) {
    val colors = AceTheme.colors
    var showExtra by remember { mutableStateOf(false) }
    var showPalette by remember { mutableStateOf(false) }
    var showServerPicker by remember { mutableStateOf(false) }
    var paletteQuery by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    fun goTab(tab: RootTab) {
        store.pageStack.clear()
        store.selectedTab = tab
    }

    fun goPage(page: PageKey) {
        store.pageStack.clear()
        store.openPage(page)
    }

    LaunchedEffect(showPalette) {
        if (showPalette) focusRequester.requestFocus()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg)
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.isCtrlPressed && event.key == Key.K) {
                    showPalette = true
                    true
                } else if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                    if (showPalette) {
                        showPalette = false
                        true
                    } else false
                } else false
            },
    ) {
        if (store.sshTerminal != null) {
            SshTerminalScreen(store, store.sshTerminal!!)
            return@Box
        }
        if (store.showAddServer) {
            Box(Modifier.fillMaxSize().padding(48.dp)) {
                AceCard(modifier = Modifier.fillMaxSize(), padding = 0.dp) {
                    AddServerScreen(store)
                }
            }
            return@Box
        }
        Row(Modifier.fillMaxSize()) {
            DesktopSidebar(
                store = store,
                showExtra = showExtra,
                onToggleExtra = { showExtra = !showExtra },
                onTab = ::goTab,
                onPage = ::goPage,
            )
            Column(Modifier.weight(1f).fillMaxHeight()) {
                DesktopTopBar(
                    store = store,
                    onSearch = { showPalette = true },
                    onServer = { showServerPicker = true },
                    onRefresh = { store.requestGlobalRefresh() },
                    onAlerts = { goPage(PageKey.ALERTS) },
                    onAccount = { goPage(PageKey.SETTINGS) },
                )
                CompositionLocalProvider(LocalDesktopLayout provides true) {
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        val destination: Any = store.pageStack.lastOrNull() ?: store.selectedTab
                        Crossfade(targetState = destination, animationSpec = tween(180), label = "desktop-page") { target ->
                            key(store.refreshTick, target) {
                                when (target) {
                                    is PageKey -> DesktopPage(store, target) { store.closePage() }
                                    is RootTab -> when (target) {
                                        RootTab.OVERVIEW -> DesktopOverviewScreen(store)
                                        RootTab.SERVERS -> DesktopPageFrame { com.acepanel.client.ui.screens.ServersScreen(store) }
                                        RootTab.TERMINAL -> TerminalTabScreen(store)
                                        RootTab.MONITOR -> DesktopPageFrame { MonitorScreen(store) }
                                        RootTab.PROFILE -> DesktopPageFrame { SettingsScreen(store) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showPalette) {
        CommandPalette(
            store = store,
            query = paletteQuery,
            onQuery = { paletteQuery = it },
            focusRequester = focusRequester,
            onDismiss = { showPalette = false; paletteQuery = "" },
            onNavigateTab = { goTab(it); showPalette = false; paletteQuery = "" },
            onNavigatePage = { goPage(it); showPalette = false; paletteQuery = "" },
        )
    }
    if (showServerPicker) {
        ServerPicker(store) { showServerPicker = false }
    }
}

@Composable
private fun DesktopSidebar(
    store: AceStore,
    showExtra: Boolean,
    onToggleExtra: () -> Unit,
    onTab: (RootTab) -> Unit,
    onPage: (PageKey) -> Unit,
) {
    val colors = AceTheme.colors
    val page = store.pageStack.lastOrNull()
    val expanded = showExtra || extraNav.any { it.page == page }
    Column(
        modifier = Modifier
            .width(236.dp)
            .fillMaxHeight()
            .background(colors.surface)
            .padding(horizontal = 14.dp, vertical = 20.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            AceLogoMark(size = 27.dp)
            AceText("AcePanel", style = AceType.section.copy(fontSize = 18.sp), color = colors.text1)
        }
        Spacer(Modifier.height(18.dp))
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            coreNav.forEach { item ->
                val selected = if (item.tab != null) page == null && store.selectedTab == item.tab else page == item.page
                NavRow(item, selected) {
                    if (item.tab != null) onTab(item.tab) else item.page?.let(onPage)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onToggleExtra)
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AceText(
                    "更多功能",
                    style = AceType.bodySmall.copy(
                        fontWeight = if (expanded && !coreNav.any { it.page == page }) FontWeight.SemiBold else FontWeight.Normal,
                    ),
                    color = if (expanded && !coreNav.any { it.page == page }) colors.accent else colors.text3,
                    modifier = Modifier.weight(1f),
                )
                AceIcon(if (expanded) Lucide.ChevronUp else Lucide.ChevronDown, tint = colors.text3, size = 14.dp)
            }
            if (expanded) {
                extraNav.forEach { item ->
                    val selected = item.page == page
                    NavRow(item, selected) { item.page?.let(onPage) }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        val user = store.user
        val initial = (user?.username ?: store.activeProfile?.username ?: "AP").take(2).uppercase()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(colors.surface2)
                .clickable { onPage(PageKey.FEATURE_CENTER) }
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Box(
                modifier = Modifier.size(34.dp).clip(CircleShape).background(colors.accentSoft),
                contentAlignment = Alignment.Center,
            ) {
                AceText(initial.ifBlank { "AP" }, style = AceType.caption.copy(fontWeight = FontWeight.SemiBold), color = colors.accent)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                AceText(user?.username ?: "面板用户", style = AceType.bodySmall.copy(fontWeight = FontWeight.Medium), color = colors.text1, maxLines = 1)
                AceText(
                    if (user?.role?.contains("admin") == true) "超级管理员" else "面板用户",
                    style = AceType.caption.copy(fontSize = 10.sp),
                    color = colors.text3,
                )
            }
            RowChevron()
        }
    }
}

@Composable
private fun NavRow(item: DesktopNavItem, selected: Boolean, onClick: () -> Unit) {
    val colors = AceTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(38.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) colors.accentSoft else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        AceIcon(item.icon, tint = if (selected) colors.accent else colors.text2, size = 17.dp)
        AceText(
            item.label,
            style = AceType.bodySmall.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal),
            color = if (selected) colors.accent else colors.text1,
        )
    }
}

@Composable
private fun DesktopTopBar(
    store: AceStore,
    onSearch: () -> Unit,
    onServer: () -> Unit,
    onRefresh: () -> Unit,
    onAlerts: () -> Unit,
    onAccount: () -> Unit,
) {
    val colors = AceTheme.colors
    val profile = store.activeProfile
    val initial = (store.user?.username ?: profile?.username ?: "AP").take(2).uppercase()
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(colors.bg)
            .padding(horizontal = 24.dp),
    ) {
        val compact = maxWidth < 940.dp
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onServer)
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AceText(
                    when {
                        store.pageStack.lastOrNull() != null -> pageTitle(store.pageStack.last())
                        else -> store.selectedTab.label
                    },
                    style = AceType.listStrong.copy(fontSize = 16.sp),
                    color = colors.text1,
                    maxLines = 1,
                )
                Box(Modifier.size(6.dp).clip(CircleShape).background(if (profile != null) colors.success else colors.text3))
                if (!compact) {
                    AceText(
                        "${profile?.name ?: "未连接"} · ${profile?.baseUrl?.removePrefix("https://")?.removePrefix("http://") ?: "—"}",
                        style = AceType.caption,
                        color = colors.text3,
                        maxLines = 1,
                        modifier = Modifier.widthIn(max = 240.dp),
                    )
                }
                AceIcon(Lucide.ChevronDown, tint = colors.text3, size = 13.dp)
            }
            Spacer(Modifier.weight(1f))
            if (compact) {
                CircleAction(Lucide.Search, onSearch, background = colors.surface, tint = colors.text2, size = 40.dp, iconSize = 17.dp)
                Spacer(Modifier.width(8.dp))
            } else {
                Row(
                    modifier = Modifier
                        .width(280.dp)
                        .height(40.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(colors.surface)
                        .clickable(onClick = onSearch)
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AceIcon(Lucide.Search, tint = colors.text3, size = 16.dp)
                    AceText("搜索功能、文件或命令", style = AceType.bodySmall, color = colors.text3, modifier = Modifier.weight(1f))
                    Box(
                        Modifier.clip(RoundedCornerShape(6.dp)).background(colors.fill).padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        AceText("Ctrl K", style = AceType.caption.copy(fontSize = 9.5.sp, fontFamily = FontFamily.Monospace), color = colors.text3)
                    }
                }
                Spacer(Modifier.width(12.dp))
            }
            CircleAction(Lucide.RefreshCw, onRefresh, background = colors.surface, tint = colors.text2, size = 40.dp, iconSize = 17.dp)
            Spacer(Modifier.width(8.dp))
            Box {
                CircleAction(Lucide.Bell, onAlerts, background = colors.surface, tint = colors.text1, size = 40.dp, iconSize = 18.dp)
                Box(Modifier.align(Alignment.TopEnd).padding(top = 9.dp, end = 10.dp).size(6.dp).clip(CircleShape).background(colors.error))
            }
            Spacer(Modifier.width(10.dp))
            Box(
                modifier = Modifier.size(38.dp).clip(CircleShape).background(colors.accentSoft).clickable(onClick = onAccount),
                contentAlignment = Alignment.Center,
            ) {
                AceText(initial.ifBlank { "AP" }, style = AceType.caption.copy(fontWeight = FontWeight.SemiBold), color = colors.accent)
            }
        }
    }
}

@Composable
private fun DesktopPageFrame(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.widthIn(max = 1180.dp).fillMaxWidth().fillMaxHeight()) {
            content()
        }
    }
}

@Composable
private fun DesktopPage(store: AceStore, page: PageKey, onBack: () -> Unit) {
    DesktopPageFrame {
        when (page) {
            PageKey.APPS -> AppStoreScreen(store)
            PageKey.WEBSITES -> WebsitesScreen(store)
            PageKey.DATABASES -> DatabasesScreen(store)
            PageKey.FILES -> FilesScreen(store)
            PageKey.CONTAINERS -> ContainersScreen(store)
            PageKey.SECURITY -> SecurityScreen(store)
            PageKey.CRON -> CronScreen(store)
            PageKey.SETTINGS -> SettingsScreen(store)
            PageKey.BACKUPS -> BackupsScreen(store)
            PageKey.CERTS -> CertificatesScreen(store)
            PageKey.ENVIRONMENTS -> EnvironmentsScreen(store)
            PageKey.LOGS -> LogsScreen(store)
            PageKey.PROJECTS -> ProjectsScreen(store)
            PageKey.TOOLBOX -> ToolboxScreen(store)
            PageKey.ALERTS -> AlertsScreen(store)
            PageKey.API_CENTER -> ApiCenterScreen(store)
            PageKey.ACCOUNT_CENTER -> AccountCenterScreen(store)
            PageKey.NOTIFY_CENTER -> NotifyCenterScreen(store)
            PageKey.DATA_CENTER -> DataCenterScreen(store)
            PageKey.OPS_CENTER -> OpsCenterScreen(store)
            PageKey.FEATURE_CENTER -> FeatureCenterScreen(store)
        }
    }
}

@Composable
private fun CommandPalette(
    store: AceStore,
    query: String,
    onQuery: (String) -> Unit,
    focusRequester: FocusRequester,
    onDismiss: () -> Unit,
    onNavigateTab: (RootTab) -> Unit,
    onNavigatePage: (PageKey) -> Unit,
) {
    val colors = AceTheme.colors
    val items = buildList {
        coreNav.forEach { item ->
            if (query.isBlank() || item.label.contains(query, true)) {
                add(Triple(item.label, "页面", item))
            }
        }
        extraNav.forEach { item ->
            if (query.isBlank() || item.label.contains(query, true)) {
                add(Triple(item.label, "更多", item))
            }
        }
        store.sshHosts.forEach { host ->
            if (query.isBlank() || host.name.contains(query, true) || host.host.contains(query, true)) {
                add(Triple("终端 · ${host.name}", host.host, null))
            }
        }
    }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .width(560.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(colors.surface)
                .padding(18.dp),
        ) {
            AceTextField(
                value = query,
                onValueChange = onQuery,
                placeholder = "搜索页面、服务器或命令",
                leading = { AceIcon(Lucide.Search, tint = colors.text3, size = 17.dp) },
                modifier = Modifier.focusRequester(focusRequester),
            )
            Spacer(Modifier.height(10.dp))
            Column(Modifier.height(320.dp).verticalScroll(rememberScrollState())) {
                items.take(14).forEach { (label, group, item) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                when {
                                    item?.tab != null -> onNavigateTab(item.tab)
                                    item?.page != null -> onNavigatePage(item.page)
                                    else -> {
                                        val host = store.sshHosts.firstOrNull { label.endsWith(it.name) }
                                        if (host != null) store.openSshTerminal(host.id, host.name, "${host.config.user}@${host.host}")
                                        onDismiss()
                                    }
                                }
                            }
                            .padding(horizontal = 10.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        AceIcon(item?.icon ?: Lucide.SquareTerminal, tint = colors.text2, size = 16.dp)
                        AceText(label, style = AceType.bodySmall, color = colors.text1, modifier = Modifier.weight(1f))
                        AceText(group, style = AceType.caption, color = colors.text3)
                    }
                }
                if (items.isEmpty()) {
                    AceText("没有匹配结果", style = AceType.caption, color = colors.text3, modifier = Modifier.padding(14.dp))
                }
            }
        }
    }
}

@Composable
private fun ServerPicker(store: AceStore, onDismiss: () -> Unit) {
    val colors = AceTheme.colors
    AceDialog(title = "切换服务器", onDismiss = onDismiss) {
        store.profiles.forEach { profile ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        store.switchProfile(profile)
                        onDismiss()
                    }
                    .padding(horizontal = 10.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(if (profile.online) colors.success else colors.text3))
                Column(Modifier.weight(1f)) {
                    AceText(profile.name.ifBlank { profile.baseUrl }, style = AceType.listPrimary, color = colors.text1)
                    AceText(
                        profile.baseUrl.removePrefix("https://").removePrefix("http://"),
                        style = AceType.caption.copy(fontFamily = FontFamily.Monospace),
                        color = colors.text3,
                    )
                }
                if (profile.id == store.activeProfileId) {
                    StatusPill("当前", colors.accent, colors.accentSoft, dot = false)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        SoftPillButton("添加服务器", { onDismiss(); store.openAddServer() }, height = 42.dp, background = colors.fill)
        Spacer(Modifier.height(8.dp))
        SoftPillButton("退出登录", { onDismiss(); store.signOut() }, height = 42.dp, tint = colors.error, background = colors.errorSoft)
    }
}

private fun pageTitle(page: PageKey): String = when (page) {
    PageKey.FEATURE_CENTER -> "全部功能"
    PageKey.APPS -> "应用商店"
    PageKey.WEBSITES -> "网站"
    PageKey.DATABASES -> "数据库"
    PageKey.FILES -> "文件"
    PageKey.CONTAINERS -> "容器"
    PageKey.SECURITY -> "安全"
    PageKey.CRON -> "计划任务"
    PageKey.SETTINGS -> "设置"
    PageKey.BACKUPS -> "备份"
    PageKey.CERTS -> "SSL 证书"
    PageKey.ENVIRONMENTS -> "运行环境"
    PageKey.LOGS -> "日志"
    PageKey.PROJECTS -> "项目"
    PageKey.TOOLBOX -> "工具箱"
    PageKey.ALERTS -> "告警记录"
    PageKey.API_CENTER -> "接口中心"
    PageKey.ACCOUNT_CENTER -> "用户与安全"
    PageKey.NOTIFY_CENTER -> "通知与告警"
    PageKey.DATA_CENTER -> "数据服务"
    PageKey.OPS_CENTER -> "自动化与进程"
}
