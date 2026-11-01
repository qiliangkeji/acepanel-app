package com.acepanel.client.data

import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

private fun trimZero(value: String): String =
    value.replace(Regex("\\.0+$"), "").replace(Regex("(\\.[0-9]*?)0+$"), "$1")

private fun fixed(value: Double, digits: Int): String {
    if (!value.isFinite()) return "0"
    val factor = when (digits) {
        0 -> 1.0
        1 -> 10.0
        2 -> 100.0
        3 -> 1000.0
        else -> 1.0
    }
    val rounded = (value * factor).roundToLong()
    val whole = rounded / factor.toLong()
    if (digits == 0) return whole.toString()
    val frac = abs(rounded % factor.toLong()).toString().padStart(digits, '0')
    return "$whole.$frac"
}

fun formatBytes(bytes: Double): String {
    if (bytes <= 0) return "0 B"
    val units = listOf("B", "KB", "MB", "GB", "TB", "PB")
    var value = bytes
    var index = 0
    while (value >= 1024 && index < units.lastIndex) {
        value /= 1024.0
        index++
    }
    val digits = if (index == 0) 0 else 1
    return trimZero(fixed(value, digits)) + " " + units[index]
}

fun formatBytes(bytes: Long): String = formatBytes(bytes.toDouble())

fun formatSpeed(bytesPerSecond: Double): String {
    if (bytesPerSecond <= 0) return "0 B/s"
    return formatBytes(bytesPerSecond) + "/s"
}

fun formatUptime(seconds: Long): String {
    if (seconds <= 0) return "刚刚启动"
    val days = seconds / 86_400
    val hours = (seconds % 86_400) / 3_600
    val minutes = (seconds % 3_600) / 60
    return when {
        days > 0 -> "$days 天${if (hours > 0) " $hours 小时" else ""}"
        hours > 0 -> "$hours 小时${if (minutes > 0) " $minutes 分" else ""}"
        else -> "$minutes 分钟"
    }
}

fun formatHostUptime(seconds: Long): String {
    if (seconds <= 0) return "—"
    val days = seconds / 86_400
    return if (days > 0) "已运行 $days 天" else "已运行 ${seconds / 3_600} 小时"
}

fun Double.asPercent(): String = trimZero(fixed(this, 1))

fun Double.asPercentInt(): String = this.roundToInt().toString()

fun String.datePart(): String {
    if (length >= 10) return substring(0, 10)
    return this
}

fun String.dateTimePart(): String {
    if (length >= 16) {
        return substring(0, 10) + " " + substring(11, 16)
    }
    return this
}

fun String.timePart(): String {
    if (length >= 16) return substring(11, 16)
    return this
}

fun String.toDoubleOrZero(): Double = toDoubleOrNull() ?: 0.0

fun Long.toGb(): String {
    if (this <= 0) return "0"
    return trimZero(fixed(this / 1024.0 / 1024.0 / 1024.0, 1))
}

fun Long.toMb(): String {
    if (this <= 0) return "0"
    return trimZero(fixed(this / 1024.0 / 1024.0, 0))
}

fun formatCronTime(expr: String): String = expr.trim()

fun containerStateLabel(state: String): Pair<String, Boolean> = when (state.lowercase()) {
    "running" -> "运行中" to true
    "exited", "dead" -> "已停止" to false
    "paused" -> "已暂停" to false
    "restarting" -> "重启中" to false
    "created" -> "已创建" to false
    else -> (state.ifBlank { "未知" }) to false
}

fun appStatus(label: String): Boolean =
    label.contains("running", true) || label.contains("active", true) || label.contains("运行", true)

fun nowMillis(): Long = com.acepanel.client.platform.currentTimeMillis()

fun currentTimeMillis(): Long = com.acepanel.client.platform.currentTimeMillis()

fun timeAgo(epochMillis: Long): String {
    if (epochMillis <= 0) return "从未"
    val diff = (nowMillis() - epochMillis).coerceAtLeast(0)
    return when {
        diff < 60_000 -> "刚刚"
        diff < 3_600_000 -> "${diff / 60_000} 分钟前"
        diff < 86_400_000 -> "${diff / 3_600_000} 小时前"
        else -> "${diff / 86_400_000} 天前"
    }
}
