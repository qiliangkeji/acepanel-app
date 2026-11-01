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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.acepanel.client.data.MonitorDetail
import com.acepanel.client.data.PageKey
import com.acepanel.client.data.diskSummary
import com.acepanel.client.data.percentOrZero
import com.acepanel.client.data.primaryOrAll
import com.acepanel.client.data.QuickActionKey
import com.acepanel.client.data.RootTab
import com.acepanel.client.data.asPercent
import com.acepanel.client.data.asPercentInt
import com.acepanel.client.data.buildChart
import com.acepanel.client.data.formatBytes
import com.acepanel.client.data.formatHostUptime
import com.acepanel.client.data.toDoubleOrZero
import com.acepanel.client.data.toGb
import com.acepanel.client.data.timePart
import com.acepanel.client.ui.components.AceCard
import com.acepanel.client.ui.components.AceDialog
import com.acepanel.client.ui.components.AceIcon
import com.acepanel.client.ui.components.AceText
import com.acepanel.client.ui.components.CircleAction
import com.acepanel.client.ui.components.EmptyState
import com.acepanel.client.ui.components.ErrorState
import com.acepanel.client.ui.components.LineChart
import com.acepanel.client.ui.components.LoadingState
import com.acepanel.client.ui.components.PrimaryPillButton
import com.acepanel.client.ui.components.SoftPillButton
import com.acepanel.client.ui.components.StatusPill
import com.acepanel.client.ui.components.rememberScreenScrollState
import com.acepanel.client.ui.theme.AceDimens
import com.acepanel.client.ui.theme.AceTheme
import com.acepanel.client.ui.theme.AceType
import com.composables.icons.lucide.Activity
import com.composables.icons.lucide.Archive
import com.composables.icons.lucide.Bell
import com.composables.icons.lucide.Box
import com.composables.icons.lucide.Boxes
import com.composables.icons.lucide.Braces
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Clock
import com.composables.icons.lucide.Code
import com.composables.icons.lucide.Container
import com.composables.icons.lucide.Database
import com.composables.icons.lucide.FileText
import com.composables.icons.lucide.Folder
import com.composables.icons.lucide.Globe
import com.composables.icons.lucide.KeyRound
import com.composables.icons.lucide.LayoutGrid
import com.composables.icons.lucide.ListChecks
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Package
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Rocket
import com.composables.icons.lucide.SquareTerminal
import com.composables.icons.lucide.ShieldCheck
import com.composables.icons.lucide.UserRound
import com.composables.icons.lucide.Wrench
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.roundToInt
import com.acepanel.client.data.runSafely

private enum class ChartRange(val label: String, val millis: Long) {
    HOUR("近 1 小时", 3_600_000L),
    SIX_HOURS("近 6 小时", 21_600_000L),
    DAY("近 24 小时", 86_400_000L),
    WEEK("近 7 天", 604_800_000L),
}

private fun Float.asPercent(): String = this.toDouble().asPercent()

@Composable
fun OverviewScreen(store: AceStore) {
    val colors = AceTheme.colors
    val profile = store.activeProfile
    val system = store.systemInfo
    val current = store.currentInfo
    val counts = store.countInfo

    var range by remember { mutableStateOf(ChartRange.HOUR) }
    var rangeDialog by remember { mutableStateOf(false) }
    var monitor by remember { mutableStateOf(MonitorDetail()) }
    var alerts by remember { mutableStateOf(0) }
    var showQuickEdit by remember { mutableStateOf(false) }

    LaunchedEffect(store.api, range) {
        val client = store.api
        if (client == null) return@LaunchedEffect
        var tick = 0
        while (isActive) {
            runSafely {
                val end = com.acepanel.client.data.nowMillis()
                monitor = client.monitorList(end - range.millis, end)
                alerts = client.alerts(1, 5).total.toInt()
                if (tick % 3 == 0) store.refreshDashboard(showLoading = false)
            }
            tick += 1
            delay(10_000)
        }
    }
    LaunchedEffect(store.api) {
        while (isActive) {
            delay(5_000)
            store.pollOverview()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg)
            .windowInsetsPadding(WindowInsets.statusBars)
            .verticalScroll(rememberScreenScrollState()),
    ) {
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 26.dp, end = 26.dp, top = 4.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { store.selectedTab = RootTab.SERVERS },
            ) {
                AceText("当前服务器", style = AceType.secondary, color = colors.text3)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AceText(
                        profile?.name?.ifBlank { system?.hostname ?: "未命名服务器" } ?: "未连接",
                        style = AceType.pageTitle,
                        color = colors.text1,
                        maxLines = 1,
                        // 主机名长于剩余宽度时，同行的下拉箭头会被顶出屏幕（右上角还有两个圆形操作按钮）
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    AceIcon(Lucide.ChevronDown, tint = colors.text3, size = 17.dp)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box {
                    CircleAction(
                        icon = Lucide.Bell,
                        onClick = { store.openPage(PageKey.ALERTS) },
                        background = colors.surface,
                        tint = colors.text1,
                        contentDescription = "告警通知",
                    )
                    if (alerts > 0) {
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 8.dp, end = 9.dp)
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(colors.error),
                        )
                    }
                }
                CircleAction(
                    icon = Lucide.Plus,
                    onClick = store::openAddServer,
                    background = colors.accent,
                    tint = Color.White,
                    contentDescription = "添加服务器",
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 26.dp, end = 26.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (system == null || current == null) {
                if (store.dashboardError != null) {
                    ErrorState(store.dashboardError!!, onRetry = { store.refreshDashboard() })
                } else {
                    LoadingState(rows = 4)
                }
            } else {
                ServerSummaryCard(store, onEditQuickActions = { showQuickEdit = true })
                MetricsGrid(store)
                ChartCard(range, monitor, onRangeClick = { rangeDialog = true })
                AppsCard(store)
            }
        }
    }

    if (rangeDialog) {
        AceDialog(title = "时间范围", onDismiss = { rangeDialog = false }) {
            ChartRange.entries.forEach { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            range = item
                            rangeDialog = false
                        }
                        .padding(horizontal = 12.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AceText(
                        item.label,
                        style = AceType.body,
                        color = if (item == range) AceTheme.colors.accent else AceTheme.colors.text1,
                        modifier = Modifier.weight(1f),
                    )
                    if (item == range) {
                        AceIcon(Lucide.Check, tint = AceTheme.colors.accent, size = 17.dp)
                    }
                }
            }
        }
    }

    if (showQuickEdit) {
        QuickActionsEditor(store) { showQuickEdit = false }
    }
}

@Composable
private fun ServerSummaryCard(store: AceStore, onEditQuickActions: () -> Unit) {
    val colors = AceTheme.colors
    val system = store.systemInfo ?: return
    val current = store.currentInfo ?: return
    val profile = store.activeProfile
    val cores = current.cpus.size.takeIf { it > 0 } ?: system.procs.takeIf { it > 0 } ?: 0
    val memGb = current.mem?.total?.toGb() ?: "0"
    val ip = profile?.baseUrl?.removePrefix("https://")?.removePrefix("http://")?.substringBefore('/') ?: "—"
    AceCard(padding = 20.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(colors.success))
                    AceText("运行正常", style = AceType.body.copy(fontWeight = FontWeight.SemiBold), color = colors.success)
                }
                AceText(formatHostUptime(system.uptime), style = AceType.secondary, color = colors.text3)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AceText(
                    ip,
                    style = AceType.secondary.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                    color = colors.text2,
                    maxLines = 1,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Box(Modifier.width(1.dp).height(12.dp).background(colors.divider))
                AceText(
                    "${system.osName.ifBlank { "Linux" }}${if (cores > 0) " · $cores 核 $memGb G" else ""}",
                    style = AceType.secondary,
                    color = colors.text2,
                    maxLines = 1,
                    // IPv6 / “Ubuntu 24.04 LTS · 4 核 16 G” 两段都不限宽时，后一段会被硬切
                    modifier = Modifier.weight(1.6f, fill = false),
                )
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(colors.divider))
            Row(verticalAlignment = Alignment.CenterVertically) {
                AceText("常用功能", style = AceType.caption, color = colors.text3, modifier = Modifier.weight(1f))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .clickable(onClick = onEditQuickActions)
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    AceIcon(Lucide.Pencil, tint = colors.accent, size = 12.dp)
                    AceText("编辑", style = AceType.tag, color = colors.accent)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                store.quickActions.forEach { action ->
                    QuickAction(action, Modifier.weight(1f)) { store.openQuickAction(action) }
                }
            }
        }
    }
}

@Composable
internal fun QuickAction(action: QuickActionKey, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        AceIcon(quickActionIcon(action), tint = AceTheme.colors.accent, size = 23.dp)
        AceText(
            action.label,
            style = AceType.secondary,
            color = AceTheme.colors.text1,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
    }
}

internal fun quickActionIcon(action: QuickActionKey): ImageVector = when (action) {
    QuickActionKey.TERMINAL -> Lucide.SquareTerminal
    QuickActionKey.FILES -> Lucide.Folder
    QuickActionKey.WEBSITES -> Lucide.Globe
    QuickActionKey.CONTAINERS -> Lucide.Box
    QuickActionKey.DATABASES -> Lucide.Database
    QuickActionKey.APPS -> Lucide.Package
    QuickActionKey.CRON -> Lucide.Clock
    QuickActionKey.SECURITY -> Lucide.ShieldCheck
    QuickActionKey.MONITOR -> Lucide.Activity
    QuickActionKey.BACKUPS -> Lucide.Archive
    QuickActionKey.CERTS -> Lucide.KeyRound
    QuickActionKey.ENVIRONMENTS -> Lucide.Rocket
    QuickActionKey.LOGS -> Lucide.FileText
    QuickActionKey.PROJECTS -> Lucide.ListChecks
    QuickActionKey.TOOLBOX -> Lucide.Wrench
    QuickActionKey.ALERTS -> Lucide.Bell
    QuickActionKey.ACCOUNT_CENTER -> Lucide.UserRound
    QuickActionKey.NOTIFY_CENTER -> Lucide.Bell
    QuickActionKey.DATA_CENTER -> Lucide.Database
    QuickActionKey.OPS_CENTER -> Lucide.Activity
    QuickActionKey.API_CENTER -> Lucide.Braces
    QuickActionKey.FEATURE_CENTER -> Lucide.LayoutGrid
}

@Composable
internal fun QuickActionsEditor(store: AceStore, onDismiss: () -> Unit) {
    val colors = AceTheme.colors
    val chosen = remember { mutableStateListOf<QuickActionKey>().also { it.addAll(store.quickActions) } }
    AceDialog(title = "编辑常用功能", onDismiss = onDismiss) {
        AceText(
            "选择 4 个常用功能，显示在首页快捷入口（${chosen.size}/4）",
            style = AceType.caption,
            color = colors.text3,
        )
        Spacer(Modifier.height(10.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 360.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            QuickActionKey.entries.forEach { action ->
                val index = chosen.indexOf(action)
                val selected = index >= 0
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selected) colors.accentSoft else Color.Transparent)
                        .clickable {
                            if (selected) {
                                chosen.remove(action)
                            } else {
                                if (chosen.size >= 4) chosen.removeAt(chosen.lastIndex)
                                chosen.add(action)
                            }
                        }
                        .padding(horizontal = 10.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    AceIcon(quickActionIcon(action), tint = if (selected) colors.accent else colors.text2, size = 18.dp)
                    AceText(action.label, style = AceType.bodySmall, color = colors.text1, modifier = Modifier.weight(1f))
                    if (selected) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(colors.accent),
                            contentAlignment = Alignment.Center,
                        ) {
                            AceText("${index + 1}", style = AceType.tag, color = Color.White)
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(colors.fill),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SoftPillButton("取消", onDismiss, modifier = Modifier.weight(1f), height = 44.dp)
            Box(Modifier.weight(1f)) {
                PrimaryPillButton(
                    "保存",
                    {
                        store.updateQuickActions(chosen.toList())
                        onDismiss()
                    },
                    enabled = chosen.size == 4,
                    height = 44.dp,
                )
            }
        }
    }
}

@Composable
private fun MetricsGrid(store: AceStore) {
    val colors = AceTheme.colors
    val current = store.currentInfo ?: return
    val disk = current.diskSummary()
    val diskTotal = disk.total
    val diskUsed = disk.used
    val diskPercent = disk.percent
    val mem = current.mem
    val memPercent = mem.percentOrZero()
    val load = current.load?.load1 ?: 0.0
    val cores = current.cpus.size.coerceAtLeast(1)
    val loadPercent = (load / cores * 100.0).coerceIn(0.0, 100.0)

    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            MetricCard(
                label = "CPU",
                value = current.percent.asPercentInt(),
                unit = "%",
                percent = current.percent,
                color = colors.accent,
                caption = "${current.cpus.size.coerceAtLeast(1)} 核 · 实时采样",
            )
            MetricCard(
                label = "磁盘",
                value = diskPercent.asPercentInt(),
                unit = "%",
                percent = diskPercent,
                color = if (diskPercent >= 80) colors.warning else colors.accent,
                caption = "${formatBytes(diskUsed)} / ${formatBytes(diskTotal)}",
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            MetricCard(
                label = "内存",
                value = memPercent.asPercentInt(),
                unit = "%",
                percent = memPercent,
                color = colors.accent,
                caption = "${formatBytes(mem?.used ?: 0)} / ${formatBytes(mem?.total ?: 0)}",
            )
            MetricCard(
                label = "负载",
                value = if (load < 10) load.toString().let { if (it.length > 4) it.take(4) else it } else load.asPercentInt(),
                unit = "",
                percent = loadPercent,
                color = if (loadPercent > 80) colors.warning else colors.success,
                caption = "1/5/15 分钟",
            )
        }
    }
}

@Composable
private fun MetricCard(
    label: String,
    value: String,
    unit: String,
    percent: Double,
    color: Color,
    caption: String,
) {
    val colors = AceTheme.colors
    AceCard(padding = 16.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                AceText(label, style = AceType.bodySmall, color = colors.text2, modifier = Modifier.weight(1f))
                Row(verticalAlignment = Alignment.Bottom) {
                    AceText(
                        value,
                        style = AceType.number,
                        color = colors.text1,
                    )
                    if (unit.isNotEmpty()) {
                        AceText(
                            unit,
                            style = AceType.caption.copy(fontWeight = FontWeight.Medium),
                            color = colors.text2,
                            modifier = Modifier.padding(start = 1.dp, bottom = 2.dp),
                        )
                    }
                }
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(colors.divider),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth((percent / 100.0).coerceIn(0.0, 1.0).toFloat())
                        .height(5.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(color),
                )
            }
            AceText(caption, style = AceType.caption, color = colors.text3, maxLines = 1)
        }
    }
}

@Composable
private fun ChartCard(
    range: ChartRange,
    monitor: MonitorDetail,
    onRangeClick: () -> Unit,
) {
    val colors = AceTheme.colors
    val times = monitor.times
    val cpuValues = monitor.cpu.percent.map { it.toDoubleOrZero().toFloat() }
    val down = sumNet(monitor, true)
    val up = sumNet(monitor, false)
    // 优先展示网络吞吐；没有网络采样时再展示 CPU。两条曲线必须使用同一单位，不能混画。
    val hasNetwork = down.size > 1 || up.size > 1
    val isCpu = !hasNetwork
    // 按桶取峰值降采样，时间标签与数值保持同一下标，鼠标悬浮提示才不会错位。
    val chart = remember(monitor, hasNetwork) {
        if (hasNetwork) buildChart(times, down, up) else buildChart(times, cpuValues)
    }
    val chartValues = chart.primary
    val chartSecondary = chart.secondary?.takeIf { it.size > 1 }
    val axisLabels = remember(times) { pickTimeLabels(times) }
    val lastDown = down.lastOrNull() ?: 0f
    val lastUp = up.lastOrNull() ?: 0f
    val lastCpu = cpuValues.lastOrNull() ?: 0f

    AceCard(padding = 0.dp) {
        Column(
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                AceText("实时监控", style = AceType.section, color = colors.text1, modifier = Modifier.weight(1f))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(colors.fill)
                        .clickable(onClick = onRangeClick)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    AceText(range.label, style = AceType.caption, color = colors.text2)
                    AceIcon(Lucide.ChevronDown, tint = colors.text2, size = 13.dp)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                if (hasNetwork) {
                    LegendItem("下行", lastDown.asPercent() + " MB/s", colors.accent)
                    LegendItem("上行", lastUp.asPercent() + " MB/s", colors.text3)
                } else {
                    LegendItem("CPU", lastCpu.asPercent() + "%", colors.accent)
                }
            }
            if (chartValues.size > 1) {
                LineChart(
                    values = chartValues,
                    secondaryValues = chartSecondary,
                    color = colors.accent,
                    secondaryColor = colors.text3,
                    height = 118.dp,
                    hoverLabels = chart.labels,
                    primaryLabel = if (isCpu) "CPU" else "下行",
                    secondaryLabel = if (isCpu) null else "上行",
                    valueFormatter = { value ->
                        if (isCpu) value.asPercent() + "%" else value.asPercent() + " MB/s"
                    },
                    secondaryFormatter = { value -> value.asPercent() + " MB/s" },
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(118.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.surface2),
                    contentAlignment = Alignment.Center,
                ) {
                    AceText(
                        if (cpuValues.isEmpty() && down.isEmpty()) "暂无监控数据，面板需先开启监控采样" else "正在积累监控数据…",
                        style = AceType.caption,
                        color = colors.text3,
                    )
                }
            }
            if (axisLabels.isNotEmpty()) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    axisLabels.forEach {
                        AceText(it, style = AceType.caption.copy(fontSize = 10.5.sp), color = colors.text3)
                    }
                }
            }
        }
    }
}

@Composable
private fun LegendItem(label: String, value: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        Box(Modifier.width(12.dp).height(3.dp).clip(RoundedCornerShape(999.dp)).background(color))
        AceText(label, style = AceType.caption, color = AceTheme.colors.text2)
        AceText(value, style = AceType.caption.copy(fontWeight = FontWeight.SemiBold), color = AceTheme.colors.text1, maxLines = 1)
    }
}

@Composable
private fun AppsCard(store: AceStore) {
    val colors = AceTheme.colors
    val apps = store.appsCache.take(4)
    AceCard(padding = 0.dp) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AceText("常用应用", style = AceType.section, color = colors.text1, modifier = Modifier.weight(1f))
                AceText(
                    "全部 ${store.appsTotal.coerceAtLeast(store.appsCache.size)} 个",
                    style = AceType.caption.copy(fontWeight = FontWeight.Medium),
                    color = colors.accent,
                    modifier = Modifier.clickable { store.openPage(PageKey.APPS) },
                )
            }
            if (apps.isEmpty()) {
                AceText(
                    "暂无已安装应用，前往应用商店安装 MySQL、Nginx 等",
                    style = AceType.secondary,
                    color = colors.text3,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                )
            } else {
                apps.forEachIndexed { index, app ->
                    AppRow(app) { store.openPage(PageKey.APPS) }
                    if (index != apps.lastIndex) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(start = 55.dp, end = 20.dp)
                                .height(1.dp)
                                .background(colors.divider.copy(alpha = 0.6f)),
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun AppRow(app: HomeApp, onClick: () -> Unit) {
    val colors = AceTheme.colors
    val (label, active) = appStatusPair(app)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AceIcon(appIcon(app.slug), tint = colors.text1, size = 21.dp)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            AceText(app.name, style = AceType.listPrimary, color = colors.text1)
            AceText(
                listOfNotNull(
                    app.installedVersion.ifBlank { app.channels.firstOrNull()?.version ?: "" },
                    knownPort(app.slug),
                ).joinToString(" · "),
                style = AceType.caption.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                color = colors.text3,
            )
        }
        StatusPill(
            text = label,
            color = if (active) colors.success else colors.error,
            background = if (active) colors.successSoft else colors.errorSoft,
        )
    }
}

private fun appStatusPair(app: HomeApp): Pair<String, Boolean> {
    val status = app.status.lowercase()
    return when {
        status.contains("running") || status.contains("active") -> "运行中" to true
        status.contains("stop") || status.contains("inactive") || status.contains("dead") -> "已停止" to false
        app.installed -> "已安装" to true
        else -> "未安装" to false
    }
}

private fun appIcon(slug: String): ImageVector = when (slug.lowercase()) {
    "mysql", "mariadb", "percona", "postgresql", "redis", "clickhouse", "mongodb" -> Lucide.Database
    "nginx", "openresty", "apache" -> Lucide.Globe
    "docker", "podman" -> Lucide.Container
    "php" -> Lucide.Code
    "nodejs", "node" -> Lucide.Braces
    "prometheus", "grafana" -> Lucide.Activity
    "fail2ban" -> Lucide.ShieldCheck
    else -> Lucide.Boxes
}

private fun knownPort(slug: String): String? = when (slug.lowercase()) {
    "mysql", "mariadb", "percona" -> ":3306"
    "postgresql" -> ":5432"
    "redis" -> ":6379"
    "nginx", "openresty" -> ":80"
    "apache" -> ":80"
    "clickhouse" -> ":8123"
    "mongodb" -> ":27017"
    "prometheus" -> ":9090"
    "grafana" -> ":3000"
    else -> null
}

private fun sumNet(monitor: MonitorDetail, down: Boolean): List<Float> {
    if (monitor.net.isEmpty()) return emptyList()
    val nets = monitor.net.primaryOrAll()
    val size = if (down) nets.maxOfOrNull { it.rx.size } ?: 0 else nets.maxOfOrNull { it.tx.size } ?: 0
    return List(size) { index ->
        nets.sumOf { net ->
            val list = if (down) net.rx else net.tx
            list.getOrNull(index)?.toDoubleOrZero() ?: 0.0
        }.toFloat()
    }
}

private fun pickTimeLabels(times: List<String>): List<String> {
    if (times.isEmpty()) return emptyList()
    if (times.size <= 6) return times.map { it.timePart() }
    val count = 6
    val step = (times.size - 1).toFloat() / (count - 1).toFloat()
    return List(count) { index ->
        val pos = (index * step).roundToInt().coerceIn(0, times.lastIndex)
        times[pos].timePart()
    }
}
