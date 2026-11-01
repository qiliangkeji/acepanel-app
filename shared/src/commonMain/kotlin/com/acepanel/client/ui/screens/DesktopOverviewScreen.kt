package com.acepanel.client.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.acepanel.client.data.AceStore
import com.acepanel.client.data.Alert
import com.acepanel.client.data.CronTask
import com.acepanel.client.data.HomeApp
import com.acepanel.client.data.MonitorDetail
import com.acepanel.client.data.diskSummary
import com.acepanel.client.data.percentOrZero
import com.acepanel.client.data.primaryOrAll
import com.acepanel.client.data.PageKey
import com.acepanel.client.data.Project
import com.acepanel.client.data.RootTab
import com.acepanel.client.data.Website
import com.acepanel.client.data.asPercent
import com.acepanel.client.data.asPercentInt
import com.acepanel.client.data.buildChart
import com.acepanel.client.data.dateTimePart
import com.acepanel.client.data.formatBytes
import com.acepanel.client.data.formatHostUptime
import com.acepanel.client.data.nowMillis
import com.acepanel.client.data.timePart
import com.acepanel.client.data.toDoubleOrZero
import com.acepanel.client.data.toGb
import com.acepanel.client.ui.components.AceCard
import com.acepanel.client.ui.components.AceDialog
import com.acepanel.client.ui.components.AceIcon
import com.acepanel.client.ui.components.AceSegmented
import com.acepanel.client.ui.components.AceText
import com.acepanel.client.ui.components.DonutChart
import com.acepanel.client.ui.components.InlineNotice
import com.acepanel.client.ui.components.SoftPillButton
import com.acepanel.client.ui.components.LineChart
import com.acepanel.client.ui.components.StatusPill
import com.acepanel.client.ui.components.rememberScreenScrollState
import com.acepanel.client.ui.theme.AceTheme
import com.acepanel.client.ui.theme.AceType
import com.composables.icons.lucide.Activity
import com.composables.icons.lucide.Bell
import com.composables.icons.lucide.Boxes
import com.composables.icons.lucide.Braces
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.CircleAlert
import com.composables.icons.lucide.Clock
import com.composables.icons.lucide.Code
import com.composables.icons.lucide.Container
import com.composables.icons.lucide.Database
import com.composables.icons.lucide.Ellipsis
import com.composables.icons.lucide.Folder
import com.composables.icons.lucide.Globe
import com.composables.icons.lucide.Gauge
import com.composables.icons.lucide.ListChecks
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.ShieldCheck
import com.composables.icons.lucide.SquareTerminal
import com.composables.icons.lucide.TriangleAlert
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.roundToInt
import com.acepanel.client.data.runSafely

private enum class DesktopRange(val label: String, val millis: Long) {
    HOUR("近 1 小时 · 每 3 秒", 3_600_000L),
    SIX("近 6 小时", 21_600_000L),
    DAY("近 24 小时", 86_400_000L),
    WEEK("近 7 天", 604_800_000L),
}

@Composable
fun DesktopOverviewScreen(store: AceStore) {
    val colors = AceTheme.colors
    var websites by remember { mutableStateOf<List<Website>>(emptyList()) }
    var projects by remember { mutableStateOf<List<Project>>(emptyList()) }
    var crons by remember { mutableStateOf<List<CronTask>>(emptyList()) }
    var alerts by remember { mutableStateOf<List<Alert>>(emptyList()) }
    var monitor by remember { mutableStateOf(MonitorDetail()) }
    var chartTab by remember { mutableStateOf(0) }
    var range by remember { mutableStateOf(DesktopRange.HOUR) }
    var showRange by remember { mutableStateOf(false) }
    var showQuickEdit by remember { mutableStateOf(false) }

    LaunchedEffect(store.api) {
        val client = store.api ?: return@LaunchedEffect
        var tick = 0
        while (isActive) {
            runSafely {
                monitor = client.monitorList(nowMillis() - range.millis, nowMillis())
                alerts = client.alerts(1, 5).items
                if (tick % 3 == 0) {
                    websites = client.websites().items
                    projects = client.projects().items
                    crons = client.cronTasks().items
                    store.refreshDashboard(showLoading = false)
                }
            }
            tick += 1
            delay(10_000)
        }
    }
    LaunchedEffect(range) {
        val client = store.api ?: return@LaunchedEffect
        runSafely { monitor = client.monitorList(nowMillis() - range.millis, nowMillis()) }
    }
    LaunchedEffect(store.api) {
        while (isActive) {
            delay(5_000)
            store.pollOverview()
        }
    }

    val counts = store.countInfo
    val current = store.currentInfo
    val system = store.systemInfo
    val profile = store.activeProfile

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val compact = maxWidth < 900.dp
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScreenScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
        if (store.dashboardError != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.errorSoft)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AceText(store.dashboardError!!, style = AceType.bodySmall, color = colors.error, modifier = Modifier.weight(1f))
                SoftPillButton("重试", { store.refreshDashboard() }, height = 34.dp, background = colors.surface, tint = colors.error)
            }
        }
        AceCard(padding = 16.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    AceText("常用功能", style = AceType.section.copy(fontSize = 14.sp), color = colors.text1)
                    AceText("可编辑的首页快捷入口", style = AceType.caption, color = colors.text3)
                }
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .clickable { showQuickEdit = true }
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    AceIcon(Lucide.Pencil, tint = colors.accent, size = 13.dp)
                    AceText("编辑", style = AceType.tag, color = colors.accent)
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                store.quickActions.forEach { action ->
                    QuickAction(action, Modifier.weight(1f)) { store.openQuickAction(action) }
                }
            }
        }
        val stats = listOf(
            OverviewStat(
                label = "网站",
                value = counts.website.coerceAtLeast(0).toString(),
                sub = "${websites.count { it.status }} 个运行中",
                icon = Lucide.Globe,
            ) { store.openPage(PageKey.WEBSITES) },
            OverviewStat(
                label = "数据库",
                value = counts.database.coerceAtLeast(0).toString(),
                sub = "${counts.database.coerceAtLeast(0)} 个数据库",
                icon = Lucide.Database,
            ) { store.openPage(PageKey.DATABASES) },
            OverviewStat(
                label = "项目",
                value = counts.project.coerceAtLeast(0).toString(),
                sub = "${projects.count { it.status == "active" }} 个运行中",
                icon = Lucide.ListChecks,
            ) { store.openPage(PageKey.PROJECTS) },
            OverviewStat(
                label = "计划任务",
                value = counts.cron.coerceAtLeast(0).toString(),
                sub = "${crons.count { !it.status }} 个已暂停",
                icon = Lucide.Clock,
                warning = crons.any { !it.status },
            ) { store.openPage(PageKey.CRON) },
        )
        if (compact) {
            stats.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    row.forEach { stat ->
                        StatCard(
                            stat.label,
                            stat.value,
                            stat.sub,
                            stat.icon,
                            Modifier.weight(1f),
                            warning = stat.warning,
                            onClick = stat.onClick,
                        )
                    }
                }
            }
            ServerInfoCard(store = store, modifier = Modifier.fillMaxWidth().height(370.dp))
            MonitorDesktopCard(
                monitor = monitor,
                chartTab = chartTab,
                onChartTab = { chartTab = it },
                range = range,
                onRange = { showRange = true },
                modifier = Modifier.fillMaxWidth().height(370.dp),
            )
            AppsStatusCard(store = store, modifier = Modifier.fillMaxWidth().height(380.dp))
            DesktopAlertsCard(
                alerts = alerts,
                modifier = Modifier.fillMaxWidth().height(360.dp),
            ) { store.openPage(PageKey.ALERTS) }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                stats.forEach { stat ->
                    StatCard(
                        stat.label,
                        stat.value,
                        stat.sub,
                        stat.icon,
                        Modifier.weight(1f),
                        warning = stat.warning,
                        onClick = stat.onClick,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                ServerInfoCard(
                    store = store,
                    modifier = Modifier.weight(1.35f).height(370.dp),
                )
                MonitorDesktopCard(
                    monitor = monitor,
                    chartTab = chartTab,
                    onChartTab = { chartTab = it },
                    range = range,
                    onRange = { showRange = true },
                    modifier = Modifier.weight(1f).height(370.dp),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                AppsStatusCard(
                    store = store,
                    modifier = Modifier.weight(1.35f).height(360.dp),
                )
                DesktopAlertsCard(
                    alerts = alerts,
                    modifier = Modifier.weight(1f).height(360.dp),
                ) { store.openPage(PageKey.ALERTS) }
            }
        }
        Spacer(Modifier.height(8.dp))
        }
    }

    if (showQuickEdit) {
        QuickActionsEditor(store) { showQuickEdit = false }
    }

    if (showRange) {
        AceDialog(title = "监控时间范围", onDismiss = { showRange = false }) {
            DesktopRange.entries.forEach { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            range = item
                            showRange = false
                        }
                        .padding(horizontal = 10.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AceText(item.label, style = AceType.bodySmall, color = if (item == range) colors.accent else colors.text1, modifier = Modifier.weight(1f))
                    if (item == range) AceIcon(Lucide.Check, tint = colors.accent, size = 15.dp)
                }
            }
        }
    }
}

private data class OverviewStat(
    val label: String,
    val value: String,
    val sub: String,
    val icon: ImageVector,
    val warning: Boolean = false,
    val onClick: () -> Unit,
)

@Composable
private fun StatCard(
    label: String,
    value: String,
    sub: String,
    icon: ImageVector,
    modifier: Modifier,
    warning: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = AceTheme.colors
    AceCard(modifier = modifier.clickable(onClick = onClick), padding = 18.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AceIcon(icon, tint = colors.accent, size = 16.dp)
                    AceText(label, style = AceType.caption, color = colors.text3)
                }
                Spacer(Modifier.height(12.dp))
                AceText(value, style = AceType.bigNumber.copy(fontSize = 28.sp), color = colors.text1)
            }
            Column(horizontalAlignment = Alignment.End) {
                AceIcon(Lucide.ChevronRight, tint = colors.text3, size = 15.dp)
                Spacer(Modifier.height(12.dp))
                AceText(sub, style = AceType.caption, color = if (warning) colors.warning else colors.success, maxLines = 1)
            }
        }
    }
}

@Composable
private fun ServerInfoCard(store: AceStore, modifier: Modifier) {
    val colors = AceTheme.colors
    val system = store.systemInfo
    val current = store.currentInfo
    val profile = store.activeProfile
    val memPercent = current?.mem.percentOrZero() ?: 0.0
    val disk = current?.diskSummary() ?: com.acepanel.client.data.DiskSummary(0, 0)
    val diskTotal = disk.total
    val diskUsed = disk.used
    val diskPercent = disk.percent
    AceCard(modifier = modifier, padding = 20.dp) {
        Column(Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AceText("服务器信息", style = AceType.section, color = colors.text1, modifier = Modifier.weight(1f))
                StatusPill("运行正常", colors.success, colors.successSoft)
            }
            Spacer(Modifier.height(18.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                DonutMetric("${current?.percent?.asPercentInt() ?: "0"}%", "CPU 使用率", current?.percent ?: 0.0, colors.accent)
                DonutMetric("${memPercent.asPercentInt()}%", "内存 ${formatBytes(current?.mem?.used ?: 0)}/${formatBytes(current?.mem?.total ?: 0)}", memPercent, colors.accent)
                DonutMetric("${diskPercent.asPercentInt()}%", "磁盘 ${formatBytes(diskUsed)}/${formatBytes(diskTotal)}", diskPercent, colors.warning)
            }
            Spacer(Modifier.height(16.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(colors.divider))
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    InfoPair("主机名", system?.hostname ?: "—")
                    InfoPair("操作系统", system?.osName ?: "—")
                    InfoPair("内核", system?.kernelVersion ?: "—")
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    InfoPair("公网 IP", profile?.baseUrl?.removePrefix("https://")?.removePrefix("http://")?.substringBefore('/') ?: "—")
                    InfoPair("在线时长", system?.uptime?.let { formatHostUptime(it).removePrefix("已运行 ") } ?: "—")
                    InfoPair(
                        "负载",
                        listOf(current?.load?.load1 ?: 0.0, current?.load?.load5 ?: 0.0, current?.load?.load15 ?: 0.0)
                            .joinToString(" / ") { it.toString().take(4) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DonutMetric(value: String, caption: String, percent: Double, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        DonutChart(
            percent = percent,
            color = color,
            size = 92.dp,
            stroke = 9.dp,
            label = value,
            labelColor = AceTheme.colors.text1,
        )
        AceText(caption, style = AceType.caption.copy(fontSize = 10.5.sp), color = AceTheme.colors.text3, maxLines = 1)
    }
}

@Composable
private fun InfoPair(label: String, value: String) {
    val colors = AceTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        AceText(label, style = AceType.caption, color = colors.text3, modifier = Modifier.width(66.dp))
        AceText(value.ifBlank { "—" }, style = AceType.bodySmall.copy(fontWeight = FontWeight.Medium), color = colors.text1, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun MonitorDesktopCard(
    monitor: MonitorDetail,
    chartTab: Int,
    onChartTab: (Int) -> Unit,
    range: DesktopRange,
    onRange: () -> Unit,
    modifier: Modifier,
) {
    val colors = AceTheme.colors
    val down = sumNetSeries(monitor, true)
    val up = sumNetSeries(monitor, false)
    val diskRead = monitor.diskIo.firstOrNull()?.readSpeed?.map { it.toDoubleOrZero().toFloat() } ?: emptyList()
    val diskWrite = monitor.diskIo.firstOrNull()?.writeSpeed?.map { it.toDoubleOrZero().toFloat() } ?: emptyList()
    val rawPrimary = if (chartTab == 0) down else diskRead
    val rawSecondary = if (chartTab == 0) up else diskWrite
    // 「近 7 天」约 1 万个采样点，Canvas 每帧都要重画，必须先按桶取峰值降采样，否则 UI 线程会被堵到 ANR。
    val chart = remember(monitor, chartTab) {
        buildChart(monitor.times, rawPrimary, rawSecondary)
    }
    val primary = chart.primary
    val secondary = chart.secondary ?: emptyList()
    val labels = pickTimes(monitor.times)
    AceCard(modifier = modifier, padding = 20.dp) {
        Column(Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AceText("实时监控", style = AceType.section, color = colors.text1, modifier = Modifier.weight(1f))
                Box(
                    Modifier.clip(RoundedCornerShape(999.dp)).background(colors.fill).clickable(onClick = onRange).padding(horizontal = 11.dp, vertical = 5.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        AceText(range.label, style = AceType.caption.copy(fontSize = 10.5.sp), color = colors.text2)
                        AceIcon(Lucide.ChevronDown, tint = colors.text2, size = 12.dp)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AceSegmented(listOf("网络", "磁盘"), chartTab, modifier = Modifier.width(150.dp), onSelect = onChartTab)
                Spacer(Modifier.weight(1f))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MiniLegend(if (chartTab == 0) "下行" else "读取", colors.accent)
                    MiniLegend(if (chartTab == 0) "上行" else "写入", colors.success)
                }
            }
            Spacer(Modifier.height(6.dp))
            if (primary.size > 1) {
                LineChart(
                    values = primary,
                    secondaryValues = secondary.takeIf { it.size > 1 },
                    color = colors.accent,
                    secondaryColor = colors.success,
                    height = 185.dp,
                    hoverLabels = chart.labels,
                    primaryLabel = if (chartTab == 0) "下行" else "读取",
                    secondaryLabel = if (chartTab == 0) "上行" else "写入",
                    valueFormatter = { value ->
                        value.toDouble().asPercent() + if (chartTab == 0) " MB/s" else " KB/s"
                    },
                    secondaryFormatter = { value ->
                        value.toDouble().asPercent() + if (chartTab == 0) " MB/s" else " KB/s"
                    },
                )
            } else {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    AceText("暂无监控数据，面板需先开启监控采样", style = AceType.caption, color = colors.text3)
                }
            }
            Spacer(Modifier.weight(1f))
            if (labels.isNotEmpty()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    labels.forEach {
                        AceText(it, style = AceType.caption.copy(fontSize = 10.sp), color = colors.text3)
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniLegend(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(Modifier.width(10.dp).height(3.dp).clip(RoundedCornerShape(999.dp)).background(color))
        AceText(label, style = AceType.caption.copy(fontSize = 10.5.sp), color = AceTheme.colors.text3)
    }
}

@Composable
private fun AppsStatusCard(store: AceStore, modifier: Modifier) {
    val colors = AceTheme.colors
    val apps = store.appsCache.take(5)
    AceCard(modifier = modifier, padding = 0.dp) {
        Column {
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AceText("应用运行状态", style = AceType.section, color = colors.text1, modifier = Modifier.weight(1f))
                Row(
                    modifier = Modifier.clip(RoundedCornerShape(999.dp)).clickable { store.openPage(PageKey.APPS) }.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    AceText("应用商店", style = AceType.caption.copy(fontWeight = FontWeight.Medium), color = colors.accent)
                    AceIcon(Lucide.ChevronRight, tint = colors.accent, size = 12.dp)
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AceText("应用", style = AceType.caption.copy(fontSize = 10.5.sp), color = colors.text3, modifier = Modifier.weight(1.5f))
                AceText("版本", style = AceType.caption.copy(fontSize = 10.5.sp), color = colors.text3, modifier = Modifier.weight(0.8f))
                AceText("监听", style = AceType.caption.copy(fontSize = 10.5.sp), color = colors.text3, modifier = Modifier.weight(0.7f))
                AceText("状态", style = AceType.caption.copy(fontSize = 10.5.sp), color = colors.text3, modifier = Modifier.weight(0.7f))
                AceText("操作", style = AceType.caption.copy(fontSize = 10.5.sp), color = colors.text3, modifier = Modifier.width(80.dp))
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(colors.divider))
            if (apps.isEmpty()) {
                AceText("暂无已安装应用", style = AceType.secondary, color = colors.text3, modifier = Modifier.padding(20.dp))
            } else {
                apps.forEachIndexed { index, app ->
                    val (statusLabel, active) = desktopAppStatus(app)
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(Modifier.weight(1.5f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                            AceIcon(desktopAppIcon(app.slug), tint = colors.text1, size = 17.dp)
                            AceText(app.name, style = AceType.bodySmall.copy(fontWeight = FontWeight.Medium), color = colors.text1, maxLines = 1)
                        }
                        AceText(app.installedVersion.ifBlank { "—" }, style = AceType.caption.copy(fontFamily = FontFamily.Monospace), color = colors.text3, modifier = Modifier.weight(0.8f))
                        AceText(desktopPort(app.slug) ?: "—", style = AceType.caption.copy(fontFamily = FontFamily.Monospace), color = colors.text3, modifier = Modifier.weight(0.7f))
                        Box(Modifier.weight(0.7f)) {
                            StatusPill(statusLabel, if (active) colors.success else colors.error, if (active) colors.successSoft else colors.errorSoft)
                        }
                        Row(Modifier.width(80.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            IconAction(Lucide.SquareTerminal) { store.selectedTab = RootTab.TERMINAL }
                            IconAction(Lucide.Folder) { store.openPage(PageKey.FILES) }
                            IconAction(Lucide.Ellipsis) { store.openPage(PageKey.APPS) }
                        }
                    }
                    if (index != apps.lastIndex) {
                        Box(Modifier.fillMaxWidth().padding(start = 46.dp, end = 20.dp).height(1.dp).background(colors.divider.copy(alpha = 0.7f)))
                    }
                }
            }
        }
    }
}

@Composable
private fun IconAction(icon: ImageVector, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(24.dp).clip(RoundedCornerShape(6.dp)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        AceIcon(icon, tint = AceTheme.colors.text3, size = 14.dp)
    }
}

@Composable
private fun DesktopAlertsCard(alerts: List<Alert>, modifier: Modifier, onClick: () -> Unit) {
    val colors = AceTheme.colors
    AceCard(modifier = modifier, padding = 0.dp) {
        Column {
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AceText("告警与通知", style = AceType.section, color = colors.text1, modifier = Modifier.weight(1f))
                if (alerts.isNotEmpty()) StatusPill("${alerts.size} 条未读", colors.warning, colors.warningSoft, dot = false)
            }
            if (alerts.isEmpty()) {
                InlineNotice("暂无告警，服务器运行平稳。", color = colors.success, background = colors.successSoft, icon = Lucide.ShieldCheck)
                Spacer(Modifier.height(12.dp))
            } else {
                alerts.take(3).forEachIndexed { index, alert ->
                    val isError = alert.message.contains("失败", true) || alert.message.contains("破解", true)
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            modifier = Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(
                                if (isError) colors.errorSoft else colors.warningSoft,
                            ),
                            contentAlignment = Alignment.Center,
                        ) {
                            AceIcon(if (isError) Lucide.CircleAlert else Lucide.TriangleAlert, tint = if (isError) colors.error else colors.warning, size = 14.dp)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            AceText(alert.message.ifBlank { alert.ruleName }, style = AceType.bodySmall.copy(fontWeight = FontWeight.Medium), color = colors.text1, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            AceText(alert.createdAt.dateTimePart() + if (alert.notified) " · 已推送通知" else "", style = AceType.caption, color = colors.text3)
                        }
                    }
                    if (index != alerts.take(3).lastIndex) {
                        Box(Modifier.fillMaxWidth().padding(start = 56.dp, end = 20.dp).height(1.dp).background(colors.divider))
                    }
                }
            }
            Spacer(Modifier.weight(1f))
        }
    }
}

private fun sumNetSeries(monitor: MonitorDetail, down: Boolean): List<Float> {
    if (monitor.net.isEmpty()) return emptyList()
    val nets = monitor.net.primaryOrAll()
    val size = nets.maxOfOrNull { if (down) it.rx.size else it.tx.size } ?: 0
    return List(size) { index ->
        nets.sumOf { net ->
            (if (down) net.rx else net.tx).getOrNull(index)?.toDoubleOrZero() ?: 0.0
        }.toFloat()
    }
}

private fun pickTimes(times: List<String>): List<String> {
    if (times.isEmpty()) return emptyList()
    val count = 6
    if (times.size <= count) return times.map { it.timePart() }
    val step = (times.size - 1).toFloat() / (count - 1)
    return List(count) { index ->
        val pos = (index * step).roundToInt().coerceIn(0, times.lastIndex)
        times[pos].timePart()
    }
}

private fun desktopAppStatus(app: HomeApp): Pair<String, Boolean> {
    val status = app.status.lowercase()
    return when {
        status.contains("running") || status.contains("active") -> "运行中" to true
        status.contains("stop") || status.contains("inactive") || status.contains("dead") -> "已停止" to false
        app.installed -> "已安装" to true
        else -> "未安装" to false
    }
}

private fun desktopAppIcon(slug: String): ImageVector = when (slug.lowercase()) {
    "mysql", "mariadb", "percona", "postgresql", "redis", "clickhouse", "mongodb" -> Lucide.Database
    "nginx", "openresty", "apache" -> Lucide.Globe
    "docker", "podman" -> Lucide.Container
    "php" -> Lucide.Code
    "nodejs" -> Lucide.Braces
    "prometheus", "grafana" -> Lucide.Activity
    "fail2ban" -> Lucide.ShieldCheck
    else -> Lucide.Boxes
}

private fun desktopPort(slug: String): String? = when (slug.lowercase()) {
    "mysql", "mariadb", "percona" -> ":3306"
    "postgresql" -> ":5432"
    "redis" -> ":6379"
    "nginx", "openresty", "apache" -> ":80 / :443"
    "prometheus" -> ":9090"
    "grafana" -> ":3000"
    else -> null
}
