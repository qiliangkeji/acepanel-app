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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.acepanel.client.data.AceStore
import com.acepanel.client.data.Alert
import com.acepanel.client.data.buildChart
import com.acepanel.client.data.diskSummary
import com.acepanel.client.data.percentOrZero
import com.acepanel.client.data.primaryOrAll
import com.acepanel.client.data.MonitorDetail
import com.acepanel.client.data.PageKey
import com.acepanel.client.data.asPercent
import com.acepanel.client.data.asPercentInt
import com.acepanel.client.data.dateTimePart
import com.acepanel.client.data.formatBytes
import com.acepanel.client.data.nowMillis
import com.acepanel.client.data.timePart
import com.acepanel.client.data.toDoubleOrZero
import com.acepanel.client.ui.components.AceCard
import com.acepanel.client.ui.components.AceConfirmDialog
import com.acepanel.client.ui.components.AceFormDialog
import com.acepanel.client.ui.components.AceFormField
import com.acepanel.client.ui.components.AceIcon
import com.acepanel.client.ui.components.AceText
import com.acepanel.client.ui.components.CircleAction
import com.acepanel.client.ui.components.DonutChart
import com.acepanel.client.ui.components.LineChart
import com.acepanel.client.ui.components.LoadingState
import com.acepanel.client.ui.components.NetworkBar
import com.acepanel.client.ui.components.StatusPill
import com.acepanel.client.ui.components.rememberScreenScrollState
import com.acepanel.client.ui.theme.AceDimens
import com.acepanel.client.ui.theme.AceTheme
import com.acepanel.client.ui.theme.AceType
import com.composables.icons.lucide.CircleAlert
import com.composables.icons.lucide.ChartNoAxesCombined
import com.composables.icons.lucide.HardDrive
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.MemoryStick
import com.composables.icons.lucide.RefreshCw
import com.composables.icons.lucide.Settings
import com.composables.icons.lucide.Trash2
import com.composables.icons.lucide.TriangleAlert
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import com.acepanel.client.data.runSafely

private enum class MonitorRange(val label: String, val millis: Long) {
    HOUR("近 1 小时", 3_600_000L),
    SIX_HOURS("近 6 小时", 21_600_000L),
    DAY("近 24 小时", 86_400_000L),
    WEEK("近 7 天", 604_800_000L),
}

@Composable
fun MonitorScreen(store: AceStore) {
    val colors = AceTheme.colors
    val scope = rememberCoroutineScope()
    var range by remember { mutableStateOf(MonitorRange.HOUR) }
    var monitor by remember { mutableStateOf(MonitorDetail()) }
    var alerts by remember { mutableStateOf<List<Alert>>(emptyList()) }
    var refreshKey by remember { mutableStateOf(0) }
    var monitorLoading by remember { mutableStateOf(false) }
    var settingsFields by remember { mutableStateOf<List<AceFormField>?>(null) }
    var showClear by remember { mutableStateOf(false) }

    fun openSettings() {
        scope.launch {
            runSafely { store.api?.monitorSetting() }
                .onSuccess { setting ->
                    settingsFields = listOf(
                        AceFormField("启用采集（true / false）", (setting?.get("enabled")?.toString() ?: "true").trim('"')),
                        AceFormField("数据保留天数", (setting?.get("days")?.toString() ?: "30").trim('"'), numeric = true),
                        AceFormField("采集间隔（秒）", (setting?.get("interval")?.toString() ?: "300").trim('"'), numeric = true),
                        AceFormField("告警保留天数", (setting?.get("alert_days")?.toString() ?: "30").trim('"'), numeric = true),
                    )
                }
                .onFailure { store.toast(store.friendlyError(it), true) }
        }
    }

    LaunchedEffect(store.api, range, refreshKey) {
        val client = store.api ?: return@LaunchedEffect
        // 切换区间时先清空，避免把 1 小时的曲线当成 7 天的曲线看
        monitor = MonitorDetail()
        monitorLoading = true
        while (isActive) {
            runSafely {
                val end = nowMillis()
                client.monitorList(end - range.millis, end) to client.alerts(1, 5).items
            }.onSuccess { (detail, records) ->
                monitor = detail
                alerts = records
                monitorLoading = false
            }
            delay(10_000)
        }
    }
    LaunchedEffect(store.api, refreshKey) {
        while (isActive) {
            delay(3_000)
            store.pollOverview()
        }
    }

    val system = store.systemInfo
    val current = store.currentInfo
    val cpuPercent = current?.percent ?: 0.0
    // 7 天区间约 1 万个采样点，必须先降采样再交给 Canvas，否则每帧上万段 Path 会堵死 UI 线程。
    val cpuChart = remember(monitor) {
        buildChart(monitor.times, monitor.cpu.percent.map { it.toDoubleOrZero().toFloat() })
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
            Column(modifier = Modifier.weight(1f)) {
                AceText("监控", style = AceType.pageTitle, color = colors.text1)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(colors.success))
                    AceText(
                        "${store.activeProfile?.name ?: system?.hostname ?: "未连接"} · 每 3 秒刷新",
                        style = AceType.secondary,
                        color = colors.text3,
                        maxLines = 1,
                    )
                }
            }
            CircleAction(Lucide.Trash2, { showClear = true }, background = colors.fill, tint = colors.text2, size = 38.dp, iconSize = 16.dp)
            Spacer(Modifier.width(8.dp))
            CircleAction(Lucide.Settings, { openSettings() }, contentDescription = "监控设置")
            Spacer(Modifier.width(8.dp))
            CircleAction(Lucide.RefreshCw, { refreshKey++ }, contentDescription = "刷新监控")
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 26.dp, end = 26.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MonitorRange.entries.forEach { item ->
                    val active = item == range
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (active) colors.text1 else colors.surface)
                            .clickable { range = item }
                            .padding(horizontal = 15.dp, vertical = 8.dp),
                    ) {
                        AceText(
                            item.label,
                            style = AceType.bodySmall.copy(fontWeight = if (active) FontWeight.Medium else FontWeight.Normal),
                            color = if (active) Color.White else colors.text2,
                        )
                    }
                }
            }

            if (current == null) {
                LoadingState(rows = 4)
            } else {
                CpuCard(
                    percent = cpuPercent,
                    series = cpuChart.primary,
                    times = cpuChart.labels,
                    cores = current.cpus.size,
                    model = current.cpus.firstOrNull()?.modelName ?: "",
                    load1 = current.load?.load1 ?: 0.0,
                    loading = monitorLoading,
                    rangeLabel = range.label,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    val mem = current.mem
                    val memPercent = mem.percentOrZero()
                    DonutStatCard(
                        title = "内存",
                        icon = Lucide.MemoryStick,
                        percent = memPercent,
                        color = colors.accent,
                        caption = "${formatBytes(mem?.used ?: 0)} / ${formatBytes(mem?.total ?: 0)}",
                        modifier = Modifier.weight(1f),
                    )
                    val disk = current.diskSummary()
                    val diskTotal = disk.total
                    val diskUsed = disk.used
                    val diskPercent = if (diskTotal > 0) diskUsed * 100.0 / diskTotal else 0.0
                    DonutStatCard(
                        title = "磁盘",
                        icon = Lucide.HardDrive,
                        percent = diskPercent,
                        color = if (diskPercent >= 80) colors.warning else colors.accent,
                        caption = "${formatBytes(diskUsed)} / ${formatBytes(diskTotal)}",
                        modifier = Modifier.weight(1f),
                    )
                }
                NetworkCard(monitor)
                AlertsCard(alerts) { store.openPage(PageKey.ALERTS) }
            }
        }
    }

    settingsFields?.let { fields ->
        AceFormDialog(
            title = "监控设置",
            fields = fields,
            confirmText = "保存",
            onDismiss = { settingsFields = null },
        ) { values ->
            val enabled = values[0].trim().equals("true", true)
            val days = values[1].toIntOrNull()?.coerceAtLeast(1) ?: 30
            val interval = values[2].toIntOrNull()?.coerceAtLeast(30) ?: 300
            val alertDays = values[3].toIntOrNull()?.coerceAtLeast(1) ?: 30
            scope.launch {
                runSafely { store.api?.updateMonitorSetting(enabled, days, interval, alertDays) }
                    .onSuccess {
                        settingsFields = null
                        store.toast("监控设置已保存")
                        refreshKey++
                    }
                    .onFailure { store.toast(store.friendlyError(it), true) }
            }
        }
    }

    if (showClear) {
        AceConfirmDialog(
            title = "清空监控数据",
            message = "确认删除全部历史监控数据？该操作不可恢复，当前实时数据不受影响。",
            confirmText = "清空",
            danger = true,
            onDismiss = { showClear = false },
        ) {
            showClear = false
            scope.launch {
                runSafely { store.api?.clearMonitorData() }
                    .onSuccess {
                        store.toast("监控数据已清空")
                        refreshKey++
                    }
                    .onFailure { store.toast(store.friendlyError(it), true) }
            }
        }
    }
}

@Composable
private fun CpuCard(
    percent: Double,
    series: List<Float>,
    times: List<String>,
    cores: Int,
    model: String,
    load1: Double,
    loading: Boolean = false,
    rangeLabel: String = "",
) {
    val colors = AceTheme.colors
    val peak = series.maxOrNull() ?: percent.toFloat()
    val avg = if (series.isNotEmpty()) series.average().toFloat() else percent.toFloat()
    AceCard(padding = 20.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    AceText("CPU 使用率", style = AceType.section, color = colors.text1)
                    AceText(
                        "${cores.coerceAtLeast(1)} 核${if (model.isNotBlank()) " · ${model.take(34)}" else ""}",
                        style = AceType.caption,
                        color = colors.text3,
                        maxLines = 1,
                    )
                }
                Row(verticalAlignment = Alignment.Bottom) {
                    AceText(percent.asPercentInt(), style = AceType.bigNumber.copy(fontSize = 28.sp, color = colors.accent), color = colors.accent)
                    AceText("%", style = AceType.secondary, color = colors.text2, modifier = Modifier.padding(bottom = 3.dp))
                }
            }
            if (series.size > 1) {
                LineChart(
                    series,
                    color = colors.accent,
                    height = 120.dp,
                    hoverLabels = times,
                    primaryLabel = "CPU",
                    valueFormatter = { value -> value.toDouble().asPercent() + "%" },
                )
            } else {
                Box(
                    Modifier.fillMaxWidth().height(120.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    AceText(
                        if (loading) "正在加载 $rangeLabel 数据…" else "正在积累监控数据…",
                        style = AceType.caption,
                        color = colors.text3,
                    )
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatItem("峰值", "${peak.toDouble().asPercentInt()}%")
                StatItem("均值", "${avg.toDouble().asPercentInt()}%")
                StatItem("负载 1 分钟", if (load1 < 10) load1.toString().take(4) else load1.asPercent())
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        AceText(label, style = AceType.caption, color = AceTheme.colors.text3)
        AceText(value, style = AceType.bodySmall.copy(fontWeight = FontWeight.SemiBold), color = AceTheme.colors.text1)
    }
}

@Composable
private fun DonutStatCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    percent: Double,
    color: Color,
    caption: String,
    modifier: Modifier = Modifier,
) {
    val colors = AceTheme.colors
    AceCard(modifier = modifier, padding = 16.dp) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                AceText(title, style = AceType.bodySmall, color = colors.text2, modifier = Modifier.weight(1f))
                AceIcon(icon, tint = colors.text3, size = 16.dp)
            }
            DonutChart(
                percent = percent,
                color = color,
                size = 96.dp,
                stroke = 9.dp,
                label = "${percent.asPercentInt()}%",
                labelColor = colors.text1,
                caption = "已用",
                captionColor = colors.text3,
            )
            AceText(caption, style = AceType.caption, color = colors.text3, maxLines = 1)
        }
    }
}

@Composable
private fun NetworkCard(monitor: MonitorDetail) {
    val colors = AceTheme.colors
    val nets = monitor.net.primaryOrAll()
    val iface = nets.firstOrNull()?.name?.let { name -> if (nets.size > 1) "$name 等 ${nets.size} 个" else name } ?: "网络"
    val down = nets.maxOfOrNull { it.rx.size }?.let { size ->
        nets.sumOf { it.rx.getOrNull(size - 1)?.toDoubleOrZero() ?: 0.0 }
    } ?: 0.0
    val up = nets.maxOfOrNull { it.tx.size }?.let { size ->
        nets.sumOf { it.tx.getOrNull(size - 1)?.toDoubleOrZero() ?: 0.0 }
    } ?: 0.0
    val peak = maxOf(down, up, 1.0)
    val hasSamples = nets.any { it.rx.isNotEmpty() || it.tx.isNotEmpty() }
    AceCard(padding = 20.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                AceText("网络吞吐", style = AceType.section, color = colors.text1, modifier = Modifier.weight(1f))
                AceText("$iface · 实时", style = AceType.caption, color = colors.text3)
            }
            if (!hasSamples) {
                // 面板要攒够 2 个采样点才会算出速率，这里说清楚原因，别让界面看起来像坏了
                AceText(
                    "暂无速率数据：面板需要至少 2 个采样周期才能算出吞吐（默认每 1 分钟采集一次）。" +
                        "若长时间为空，请在右上角「监控设置」里确认已开启采集。",
                    style = AceType.caption,
                    color = colors.text3,
                )
            } else {
                NetworkRow("下行", down, down / peak * 100, colors.accent)
                NetworkRow("上行", up, up / peak * 100, colors.success)
            }
        }
    }
}

@Composable
private fun NetworkRow(label: String, value: Double, percent: Double, color: Color) {
    val colors = AceTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            AceIcon(
                if (label == "下行") Lucide.ChartNoAxesCombined else Lucide.ChartNoAxesCombined,
                tint = color,
                size = 14.dp,
            )
            AceText(label, style = AceType.caption, color = colors.text2, modifier = Modifier.weight(1f))
            AceText("${value.asPercent()} MB/s", style = AceType.caption.copy(fontWeight = FontWeight.SemiBold), color = colors.text1)
        }
        NetworkBar(percent, color)
    }
}

@Composable
private fun AlertsCard(alerts: List<Alert>, onClick: () -> Unit) {
    val colors = AceTheme.colors
    AceCard(padding = 0.dp) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AceText("告警记录", style = AceType.section, color = colors.text1, modifier = Modifier.weight(1f))
                if (alerts.isNotEmpty()) {
                    StatusPill("${alerts.size} 条未读", colors.warning, colors.warningSoft, dot = false)
                }
            }
            if (alerts.isEmpty()) {
                AceText(
                    "暂无告警，资源与站点运行平稳",
                    style = AceType.secondary,
                    color = colors.text3,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                )
            } else {
                alerts.take(2).forEachIndexed { index, alert ->
                    val error = alert.message.contains("失败", true) || alert.message.contains("破解", true) ||
                        alert.type.contains("fail", true)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onClick)
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(11.dp),
                    ) {
                        AceIcon(
                            if (error) Lucide.CircleAlert else Lucide.TriangleAlert,
                            tint = if (error) colors.error else colors.warning,
                            size = 18.dp,
                        )
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            AceText(
                                alert.message.ifBlank { alert.ruleName },
                                style = AceType.bodySmall.copy(fontWeight = FontWeight.Medium),
                                color = colors.text1,
                                maxLines = 1,
                            )
                            AceText(
                                alert.createdAt.dateTimePart() + if (alert.notified) " · 已通知" else "",
                                style = AceType.caption,
                                color = colors.text3,
                            )
                        }
                    }
                    if (index != alerts.take(2).lastIndex) {
                        Box(Modifier.fillMaxWidth().padding(start = 49.dp).height(1.dp).background(colors.divider))
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}
