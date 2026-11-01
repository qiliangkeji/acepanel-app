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
import androidx.compose.foundation.layout.widthIn
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
import com.acepanel.client.data.Alert
import com.acepanel.client.data.PageKey
import com.acepanel.client.data.RootTab
import com.acepanel.client.data.dateTimePart
import com.acepanel.client.ui.components.AceCard
import com.acepanel.client.ui.components.AceIcon
import com.acepanel.client.ui.components.AceText
import com.acepanel.client.ui.components.EmptyState
import com.acepanel.client.ui.components.ErrorState
import com.acepanel.client.ui.components.LoadingState
import com.acepanel.client.ui.components.MobilePageHeader
import com.acepanel.client.ui.components.SoftPillButton
import com.acepanel.client.ui.components.StatusPill
import com.acepanel.client.ui.components.rememberScreenScrollState
import com.acepanel.client.ui.theme.AceTheme
import com.acepanel.client.ui.theme.AceType
import com.composables.icons.lucide.Activity
import com.composables.icons.lucide.Braces
import com.composables.icons.lucide.Archive
import com.composables.icons.lucide.Bell
import com.composables.icons.lucide.Box
import com.composables.icons.lucide.ChartNoAxesCombined
import com.composables.icons.lucide.CircleAlert
import com.composables.icons.lucide.Clock
import com.composables.icons.lucide.Database
import com.composables.icons.lucide.FileText
import com.composables.icons.lucide.Folder
import com.composables.icons.lucide.Globe
import com.composables.icons.lucide.KeyRound
import com.composables.icons.lucide.ListChecks
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Package
import com.composables.icons.lucide.Rocket
import com.composables.icons.lucide.Server
import com.composables.icons.lucide.ShieldCheck
import com.composables.icons.lucide.SquareTerminal
import com.composables.icons.lucide.UserRound
import com.composables.icons.lucide.Wrench
import kotlinx.coroutines.launch
import com.acepanel.client.data.runSafely

private data class FeatureItem(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val page: PageKey? = null,
    val tab: RootTab? = null,
)

@Composable
fun FeatureCenterScreen(store: AceStore) {
    val colors = AceTheme.colors
    val groups = listOf(
        "面板运维" to listOf(
            FeatureItem("网站", "域名 · SSL · 运行状态", Lucide.Globe, PageKey.WEBSITES),
            FeatureItem("数据库", "实例 · 库 · 用户", Lucide.Database, PageKey.DATABASES),
            FeatureItem("数据服务", "多实例 · Redis · ES", Lucide.Database, PageKey.DATA_CENTER),
            FeatureItem("文件", "浏览 · 上传 · 编辑", Lucide.Folder, PageKey.FILES),
            FeatureItem("容器", "镜像 · 数据卷 · 网络", Lucide.Box, PageKey.CONTAINERS),
            FeatureItem("应用商店", "安装与更新面板应用", Lucide.Package, PageKey.APPS),
            FeatureItem("运行环境", "PHP · Node · Java · Go", Lucide.Rocket, PageKey.ENVIRONMENTS),
            FeatureItem("接口中心", "全量路由 · 在线调试", Lucide.Braces, PageKey.API_CENTER),
        ),
        "监控与安全" to listOf(
            FeatureItem("监控", "资源曲线 · 历史数据", Lucide.Activity, tab = RootTab.MONITOR),
            FeatureItem("安全", "防火墙 · 防篡改 · 扫描", Lucide.ShieldCheck, PageKey.SECURITY),
            FeatureItem("告警记录", "面板告警与通知", Lucide.Bell, PageKey.ALERTS),
            FeatureItem("用户与安全", "用户 · 通行密钥 · 令牌", Lucide.UserRound, PageKey.ACCOUNT_CENTER),
            FeatureItem("通知与告警", "渠道 · 阈值规则", Lucide.Bell, PageKey.NOTIFY_CENTER),
            FeatureItem("日志", "操作 · SSH · 面板日志", Lucide.FileText, PageKey.LOGS),
        ),
        "自动化" to listOf(
            FeatureItem("计划任务", "定时备份与脚本", Lucide.Clock, PageKey.CRON),
            FeatureItem("项目", "常驻进程与守护", Lucide.ListChecks, PageKey.PROJECTS),
            FeatureItem("工具箱", "网络 · 磁盘 · 系统", Lucide.Wrench, PageKey.TOOLBOX),
            FeatureItem("自动化与进程", "任务 · WebHook · 模板", Lucide.Activity, PageKey.OPS_CENTER),
            FeatureItem("终端", "本地与 SSH 会话", Lucide.SquareTerminal, tab = RootTab.TERMINAL),
        ),
        "数据保护" to listOf(
            FeatureItem("备份", "数据库 · 网站 · 面板", Lucide.Archive, PageKey.BACKUPS),
            FeatureItem("SSL 证书", "申请 · 续签 · 部署", Lucide.KeyRound, PageKey.CERTS),
        ),
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        MobilePageHeader(
            title = "全部功能",
            subtitle = "面板运维 · 监控安全 · 数据保护",
            onBack = store::closePage,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScreenScrollState())
                .padding(start = 26.dp, end = 26.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            groups.forEach { (title, items) ->
                AceText(title, style = AceType.caption, color = colors.text3, modifier = Modifier.padding(start = 4.dp, top = 2.dp))
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            row.forEach { item ->
                                FeatureCard(item, Modifier.weight(1f)) {
                                    if (item.page != null) store.openPage(item.page)
                                    if (item.tab != null) {
                                        store.pageStack.clear()
                                        store.selectedTab = item.tab
                                    }
                                }
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FeatureCard(item: FeatureItem, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = AceTheme.colors
    AceCard(modifier = modifier.clickable(onClick = onClick), padding = 15.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.accentSoft),
                contentAlignment = Alignment.Center,
            ) {
                AceIcon(item.icon, tint = colors.accent, size = 19.dp)
            }
            AceText(item.title, style = AceType.listPrimary, color = colors.text1)
            AceText(item.subtitle, style = AceType.caption, color = colors.text3, maxLines = 1)
        }
    }
}

@Composable
fun AlertsScreen(store: AceStore) {
    val colors = AceTheme.colors
    val scope = rememberCoroutineScope()
    var alerts by remember { mutableStateOf<List<Alert>>(emptyList()) }
    var total by remember { mutableStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var clearing by remember { mutableStateOf(false) }

    suspend fun load() {
        loading = true
        error = null
        runSafely { store.api?.alerts(1, 100) }
            .onSuccess {
                alerts = it?.items ?: emptyList()
                total = it?.total?.toInt() ?: 0
            }
            .onFailure { error = store.friendlyError(it) }
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
            title = "告警记录",
            subtitle = "$total 条告警 · 面板规则触发记录",
            onBack = store::closePage,
            actions = {
                if (alerts.isNotEmpty()) {
                    SoftPillButton(
                        if (clearing) "清理中…" else "清空",
                        onClick = {
                            if (clearing) return@SoftPillButton
                            clearing = true
                            scope.launch {
                                runSafely { store.api?.postData("/api/alert/record/clear") }
                                    .onSuccess {
                                        alerts = emptyList()
                                        total = 0
                                        store.toast("告警记录已清空")
                                    }
                                    .onFailure { store.toast(store.friendlyError(it), true) }
                                clearing = false
                            }
                        },
                        height = 36.dp,
                        tint = colors.text2,
                        background = colors.fill,
                    )
                }
            },
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 26.dp, end = 26.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when {
                loading -> LoadingState(rows = 4)
                error != null -> ErrorState(error!!) { scope.launch { load() } }
                alerts.isEmpty() -> EmptyState(
                    title = "当前没有告警",
                    subtitle = "面板会按告警规则持续检查 CPU、内存、磁盘、证书与网站状态",
                    icon = Lucide.Bell,
                )
                else -> alerts.forEach { alert ->
                    AlertCard(alert)
                }
            }
        }
    }
}

@Composable
private fun AlertCard(alert: Alert) {
    val colors = AceTheme.colors
    val isError = alert.type.contains("fail", true) || alert.type.contains("error", true) ||
        alert.message.contains("失败", true) || alert.message.contains("破解", true)
    val color = if (isError) colors.error else colors.warning
    AceCard(padding = 16.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(color.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    AceIcon(if (isError) Lucide.CircleAlert else Lucide.ChartNoAxesCombined, tint = color, size = 16.dp)
                }
                AceText(
                    alert.ruleName.ifBlank { alert.type.ifBlank { "面板告警" } },
                    style = AceType.listPrimary,
                    color = colors.text1,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
                if (alert.notified) {
                    StatusPill("已通知", colors.success, colors.successSoft, dot = false)
                }
            }
            AceText(
                alert.message.ifBlank { "触发阈值：${alert.value}" },
                style = AceType.secondary,
                color = colors.text2,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AceText(
                    alert.createdAt.dateTimePart().ifBlank { "—" },
                    style = AceType.caption,
                    color = colors.text3,
                    modifier = Modifier.weight(1f),
                )
                if (alert.target.isNotBlank()) {
                    AceText(
                        alert.target,
                        style = AceType.caption.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                        color = colors.text3,
                        maxLines = 1,
                        modifier = Modifier.widthIn(max = 132.dp),
                    )
                }
            }
        }
    }
}
