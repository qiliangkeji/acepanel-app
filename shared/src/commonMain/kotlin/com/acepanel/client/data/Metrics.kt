package com.acepanel.client.data

import kotlin.math.ceil

data class DiskSummary(val used: Long, val total: Long) {
    val percent: Double
        get() = if (total > 0) used * 100.0 / total else 0.0
}

private val virtualNetPrefixes = listOf(
    "lo", "veth", "docker", "br-", "virbr", "vmnet", "vnet", "tun", "tap",
    "wg", "tailscale", "utun", "cni", "flannel", "cali", "dummy",
)

/** 过滤回环、容器网桥、VPN 等虚拟网卡，避免吞吐量重复累计。 */
fun isPrimaryNetwork(name: String): Boolean {
    val value = name.lowercase()
    return virtualNetPrefixes.none { value == it || value.startsWith(it) }
}

fun List<MonitorNet>.primaryOrAll(): List<MonitorNet> {
    val primary = filter { isPrimaryNetwork(it.name) }
    return primary.ifEmpty { this }
}

fun List<NetIO>.primaryOrAllCurrent(): List<NetIO> {
    val primary = filter { isPrimaryNetwork(it.name) }
    return primary.ifEmpty { this }
}

/**
 * 面板会返回所有挂载点的使用量；同一块磁盘多个挂载点会重复累计。
 * 这里按 block device 去重，并优先保留第一个可用挂载点。
 */
fun CurrentInfo.diskSummary(): DiskSummary {
    if (diskUsage.isEmpty()) return DiskSummary(0, 0)
    val byMount = diskUsage.associateBy { it.path }
    val byDevice = disk.groupBy { it.device }.values.mapNotNull { partitions ->
        partitions.firstNotNullOfOrNull { byMount[it.mountpoint] }
    }
    val effective = byDevice.ifEmpty { diskUsage.distinctBy { it.path } }
    return DiskSummary(
        used = effective.sumOf { it.used },
        total = effective.sumOf { it.total },
    )
}

fun MemStat?.percentOrZero(): Double {
    if (this == null) return 0.0
    if (usedPercent > 0.0) return usedPercent
    return if (total > 0) used * 100.0 / total else 0.0
}

/**
 * 单张折线图最多画多少个点。
 *
 * 面板 1 分钟采集一次，「近 7 天」就是 1 万多个点。Compose 的 Canvas 每帧都要为这些点
 * 建 Offsets、拼上万段 cubicTo 的 Path，Android 上直接把 UI 线程堵死（实测 Blocked msg
 * took 7278ms → Input dispatching timed out → ANR 弹窗 → 用户看到的「闪退」）。
 * 所以超过这个阈值就按桶取峰值降采样，尖峰仍然保留，视觉信息基本不丢。
 */
const val MaxChartPoints: Int = 260

data class ChartData(
    val labels: List<String> = emptyList(),
    val primary: List<Float> = emptyList(),
    val secondary: List<Float>? = null,
)

/**
 * 把监控序列降采样成可直接喂给 LineChart 的数据。
 * 主副序列（如上行/下行）共用同一个桶内峰值下标，保证两条线在同一个时间点对齐。
 */
fun buildChart(
    times: List<String>,
    primary: List<Float>,
    secondary: List<Float>? = null,
    maxPoints: Int = MaxChartPoints,
): ChartData {
    val size = primary.size
    if (size == 0) return ChartData(primary = emptyList(), secondary = secondary?.let { emptyList() })
    if (size <= maxPoints) {
        return ChartData(
            labels = times.take(size),
            primary = primary,
            secondary = secondary?.take(size),
        )
    }
    val bucketSize = ceil(size / maxPoints.toDouble()).toInt().coerceAtLeast(1)
    val labels = ArrayList<String>(maxPoints + 2)
    val values = ArrayList<Float>(maxPoints + 2)
    val alt = secondary?.let { ArrayList<Float>(maxPoints + 2) }
    var start = 0
    while (start < size) {
        val end = minOf(start + bucketSize, size)
        var peak = start
        for (index in (start + 1) until end) {
            if (primary[index] > primary[peak]) peak = index
        }
        values.add(primary[peak])
        labels.add(times.getOrNull(peak).orEmpty())
        alt?.add(secondary?.getOrNull(peak) ?: 0f)
        start = end
    }
    return ChartData(labels, values, alt)
}
