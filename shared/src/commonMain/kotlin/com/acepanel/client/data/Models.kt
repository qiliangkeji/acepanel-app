package com.acepanel.client.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class Envelope(
    val msg: String? = null,
    val data: JsonElement? = null,
)

/**
 * 带类型的响应信封。用它可以直接从响应文本流式解码出目标类型，
 * 省掉「整包 JSON 建成 JsonElement 树 → toString() → 再解析一遍」这三倍内存开销。
 * 监控曲线这类上万条记录的接口必须走这条路径，否则主线程会被拖到 ANR。
 */
@Serializable
data class TypedEnvelope<T>(
    val msg: String? = null,
    val data: T? = null,
)

@Serializable
data class Page<T>(
    val total: Long = 0,
    val items: List<T> = emptyList(),
)

@Serializable
data class Lv(
    val value: JsonElement? = null,
    val label: String = "",
) {
    val valueText: String
        get() = when (val v = value) {
            is JsonPrimitive -> v.content
            else -> ""
        }
}

@Serializable
data class PanelInfo(
    val name: String = "",
    val locale: String = "",
    @SerialName("hidden_menu") val hiddenMenu: List<String> = emptyList(),
    @SerialName("custom_logo") val customLogo: String = "",
)

@Serializable
data class SystemInfo(
    val procs: Int = 0,
    val hostname: String = "",
    @SerialName("panel_version") val panelVersion: String = "",
    @SerialName("kernel_version") val kernelVersion: String = "",
    @SerialName("kernel_arch") val kernelArch: String = "",
    @SerialName("os_name") val osName: String = "",
    @SerialName("os_supported") val osSupported: Boolean = true,
    @SerialName("os_eol") val osEol: Boolean = false,
    @SerialName("boot_time") val bootTime: Long = 0,
    val uptime: Long = 0,
    val nets: List<Lv> = emptyList(),
    val disks: List<Lv> = emptyList(),
    @SerialName("go_version") val goVersion: String = "",
)

@Serializable
data class CountInfo(
    val website: Int = 0,
    val database: Int = 0,
    val project: Int = 0,
    val cron: Int = 0,
    val container: Int = 0,
)

@Serializable
data class CpuInfo(
    val modelName: String = "",
    val cores: Int = 0,
    val mhz: Double = 0.0,
)

@Serializable
data class LoadStat(
    val load1: Double = 0.0,
    val load5: Double = 0.0,
    val load15: Double = 0.0,
)

@Serializable
data class MemStat(
    val total: Long = 0,
    val available: Long = 0,
    val used: Long = 0,
    @SerialName("used_percent") val usedPercent: Double = 0.0,
    val free: Long = 0,
    val cached: Long = 0,
    val buffers: Long = 0,
)

@Serializable
data class SwapStat(
    val total: Long = 0,
    val used: Long = 0,
    val free: Long = 0,
    @SerialName("used_percent") val usedPercent: Double = 0.0,
)

@Serializable
data class NetIO(
    val name: String = "",
    @SerialName("bytes_sent") val bytesSent: Long = 0,
    @SerialName("bytes_recv") val bytesRecv: Long = 0,
    @SerialName("packets_sent") val packetsSent: Long = 0,
    @SerialName("packets_recv") val packetsRecv: Long = 0,
    @SerialName("err_in") val errIn: Long = 0,
    @SerialName("err_out") val errOut: Long = 0,
)

@Serializable
data class DiskUsage(
    val path: String = "",
    val fstype: String = "",
    val total: Long = 0,
    val free: Long = 0,
    val used: Long = 0,
    @SerialName("used_percent") val usedPercent: Double = 0.0,
)

@Serializable
data class DiskPartition(
    val device: String = "",
    val mountpoint: String = "",
    val fstype: String = "",
)

@Serializable
data class HostStat(
    val hostname: String = "",
    val uptime: Long = 0,
    val platform: String = "",
    @SerialName("platform_version") val platformVersion: String = "",
)

@Serializable
data class CurrentInfo(
    val cpus: List<CpuInfo> = emptyList(),
    val percent: Double = 0.0,
    val percents: List<Double> = emptyList(),
    val load: LoadStat? = null,
    val host: HostStat? = null,
    val mem: MemStat? = null,
    val swap: SwapStat? = null,
    val net: List<NetIO> = emptyList(),
    @SerialName("disk_io") val diskIo: List<JsonObject> = emptyList(),
    val disk: List<DiskPartition> = emptyList(),
    @SerialName("disk_usage") val diskUsage: List<DiskUsage> = emptyList(),
    val time: String = "",
    @SerialName("top_processes") val topProcesses: TopProcesses? = null,
)

@Serializable
data class TopProcesses(
    val cpu: List<ProcessStat> = emptyList(),
    val memory: List<ProcessStat> = emptyList(),
    @SerialName("disk_io") val diskIo: List<ProcessStat> = emptyList(),
)

@Serializable
data class ProcessStat(
    val pid: Int = 0,
    val name: String = "",
    val username: String = "",
    val command: String = "",
    val value: Double = 0.0,
)

@Serializable
data class HomeApp(
    val name: String = "",
    val description: String = "",
    val slug: String = "",
    val version: String = "",
    val categories: List<String> = emptyList(),
    val installed: Boolean = false,
    @SerialName("installed_version") val installedVersion: String = "",
    @SerialName("update_exist") val updateExist: Boolean = false,
    val status: String = "",
    val channels: List<AppChannel> = emptyList(),
    @SerialName("custom_supported") val customSupported: Boolean = false,
)

@Serializable
data class AppChannel(
    val slug: String = "",
    val name: String = "",
    val version: String = "",
)

@Serializable
data class Alert(
    val id: Long = 0,
    @SerialName("rule_name") val ruleName: String = "",
    val type: String = "",
    val target: String = "",
    val value: Double = 0.0,
    val message: String = "",
    val notified: Boolean = false,
    @SerialName("created_at") val createdAt: String = "",
    val extra: JsonObject? = null,
)

/**
 * 监控曲线数据。
 *
 * 注意：面板响应里还有 `top_processes`（每条记录 3 组进程明细），
 * 7 天 ≈ 1 万条记录时它占了整包体积的 80% 以上，客户端并不使用，
 * 因此这里故意不声明该字段，流式解码会直接跳过整棵子树。
 */
@Serializable
data class MonitorDetail(
    val times: List<String> = emptyList(),
    val load: MonitorLoad = MonitorLoad(),
    val cpu: MonitorCpu = MonitorCpu(),
    val mem: MonitorMem = MonitorMem(),
    val swap: MonitorSwap = MonitorSwap(),
    val net: List<MonitorNet> = emptyList(),
    @SerialName("disk_io") val diskIo: List<MonitorDiskIo> = emptyList(),
)

@Serializable
data class MonitorLoad(
    val load1: List<Double> = emptyList(),
    val load5: List<Double> = emptyList(),
    val load15: List<Double> = emptyList(),
)

@Serializable
data class MonitorCpu(val percent: List<String> = emptyList())

@Serializable
data class MonitorMem(
    val total: String = "",
    val available: List<String> = emptyList(),
    val used: List<String> = emptyList(),
)

@Serializable
data class MonitorSwap(
    val total: String = "",
    val used: List<String> = emptyList(),
    val free: List<String> = emptyList(),
)

@Serializable
data class MonitorNet(
    val name: String = "",
    val sent: List<String> = emptyList(),
    val recv: List<String> = emptyList(),
    val tx: List<String> = emptyList(),
    val rx: List<String> = emptyList(),
)

@Serializable
data class MonitorDiskIo(
    val name: String = "",
    @SerialName("read_bytes") val readBytes: List<String> = emptyList(),
    @SerialName("write_bytes") val writeBytes: List<String> = emptyList(),
    @SerialName("read_speed") val readSpeed: List<String> = emptyList(),
    @SerialName("write_speed") val writeSpeed: List<String> = emptyList(),
)

@Serializable
data class UserInfo(
    val id: Long = 0,
    val role: List<String> = emptyList(),
    val username: String = "",
    val email: String = "",
)

@Serializable
data class CaptchaInfo(
    val required: Boolean = false,
    val image: String = "",
)

@Serializable
data class SshHost(
    val id: Long = 0,
    val name: String = "",
    val host: String = "",
    val port: Int = 22,
    val config: SshConfig = SshConfig(),
    val remark: String = "",
)

@Serializable
data class SshConfig(
    val user: String = "",
    @SerialName("auth_method") val authMethod: String = "password",
)

@Serializable
data class Website(
    val id: Long = 0,
    val name: String = "",
    val type: String = "static",
    val status: Boolean = true,
    val path: String = "",
    val ssl: Boolean = false,
    @SerialName("cert_id") val certId: Long = 0,
    val remark: String = "",
    @SerialName("expire_at") val expireAt: String? = null,
    @SerialName("cert_expire") val certExpire: String = "",
    val php: Int = 0,
    val domains: List<String> = emptyList(),
    @SerialName("created_at") val createdAt: String = "",
) {
    val entryDomain: String get() = domains.firstOrNull() ?: name
    val typeLabel: String
        get() = when (type) {
            "php" -> if (php > 0) {
                val raw = php.toString()
                "PHP " + if (raw.length >= 2) raw.substring(0, 1) + "." + raw.substring(1) else raw
            } else "PHP"
            "proxy" -> "反向代理"
            else -> "静态站点"
        }
}

@Serializable
data class DatabaseItem(
    val type: String = "",
    val name: String = "",
    val server: String = "",
    @SerialName("server_id") val serverId: Long = 0,
    val encoding: String = "",
    val comment: String = "",
)

@Serializable
data class DatabaseServer(
    val id: Long = 0,
    val name: String = "",
    val type: String = "",
    val host: String = "",
    val port: Int = 0,
    val username: String = "",
    val status: String = "",
    val remark: String = "",
    @SerialName("created_at") val createdAt: String = "",
)

@Serializable
data class DatabaseUser(
    val id: Long = 0,
    @SerialName("server_id") val serverId: Long = 0,
    val username: String = "",
    val host: String = "",
    val status: String = "",
    val remark: String = "",
)

@Serializable
data class FileEntry(
    val name: String = "",
    val full: String = "",
    val size: String = "",
    @SerialName("mode_str") val modeStr: String = "",
    val mode: String = "",
    val owner: String = "",
    val group: String = "",
    val dir: Boolean = false,
    val hidden: Boolean = false,
    val symlink: Boolean = false,
    val link: String = "",
    val modify: String = "",
    val immutable: Boolean = false,
)

@Serializable
data class ContainerItem(
    val id: String = "",
    val name: String = "",
    val image: String = "",
    @SerialName("image_id") val imageId: String = "",
    val command: String = "",
    val state: String = "",
    val status: String = "",
    @SerialName("created_at") val createdAt: String = "",
    val ports: List<ContainerPort> = emptyList(),
) {
    val running: Boolean get() = state.equals("running", true)
    val shortId: String get() = id.take(12)
}

@Serializable
data class ContainerPort(
    @SerialName("container_start") val containerStart: Int = 0,
    @SerialName("container_end") val containerEnd: Int = 0,
    val host: String = "",
    @SerialName("host_start") val hostStart: Int = 0,
    @SerialName("host_end") val hostEnd: Int = 0,
    val protocol: String = "",
)

@Serializable
data class FirewallStatus(
    val status: Boolean = false,
    val version: String = "",
)

@Serializable
data class FirewallRule(
    val type: String = "",
    val family: String = "",
    @SerialName("port_start") val portStart: Int = 0,
    @SerialName("port_end") val portEnd: Int = 0,
    val protocol: String = "",
    val address: String = "",
    val strategy: String = "",
    val direction: String = "",
    @SerialName("in_use") val inUse: Boolean = false,
)

@Serializable
data class IpRule(
    val family: String = "",
    val protocol: String = "",
    val address: String = "",
    val strategy: String = "",
    val direction: String = "",
)

@Serializable
data class TamperStatus(
    val enabled: Boolean = false,
)

@Serializable
data class CronTask(
    val id: Long = 0,
    val name: String = "",
    val status: Boolean = false,
    val type: String = "",
    val time: String = "",
    val shell: String = "",
    val log: String = "",
    @SerialName("created_at") val createdAt: String = "",
)

@Serializable
data class Project(
    val id: Long = 0,
    val name: String = "",
    val type: String = "",
    val description: String = "",
    @SerialName("root_dir") val rootDir: String = "",
    @SerialName("working_dir") val workingDir: String = "",
    @SerialName("exec_start") val execStart: String = "",
    @SerialName("created_at") val createdAt: String = "",
    val status: String = "",
    val enabled: Boolean = false,
    @SerialName("pid") val pid: Int = 0,
    val memory: Long = 0,
    val cpu: Double = 0.0,
    val uptime: String = "",
)

@Serializable
data class BackupFile(
    val name: String = "",
    val path: String = "",
    val size: String = "",
    val time: String = "",
)

@Serializable
data class BackupStorage(
    val id: Long = 0,
    val name: String = "",
    val type: String = "",
    val remark: String = "",
    @SerialName("created_at") val createdAt: String = "",
)

@Serializable
data class Cert(
    val id: Long = 0,
    @SerialName("account_id") val accountId: Long = 0,
    @SerialName("dns_id") val dnsId: Long = 0,
    val type: String = "",
    val domains: List<String> = emptyList(),
    @SerialName("auto_renewal") val autoRenewal: Boolean = false,
    val script: String = "",
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("not_after") val notAfter: String = "",
    @SerialName("not_before") val notBefore: String = "",
    @SerialName("next_renewal") val nextRenewal: String = "",
    @SerialName("cert_url") val certUrl: String = "",
    val issuer: String = "",
) {
    val title: String get() = domains.firstOrNull() ?: "未命名证书"
}

@Serializable
data class CertAccount(
    val id: Long = 0,
    val email: String = "",
    val type: String = "",
    val remark: String = "",
    val ca: String = "",
    @SerialName("key_type") val keyType: String = "",
)

@Serializable
data class CertDns(
    val id: Long = 0,
    val name: String = "",
    val type: String = "",
    val remark: String = "",
)

@Serializable
data class EnvironmentItem(
    val type: String = "",
    val slug: String = "",
    val name: String = "",
    val version: String = "",
    @SerialName("installed_version") val installedVersion: String = "",
    val description: String = "",
    val installed: Boolean = false,
    @SerialName("has_update") val hasUpdate: Boolean = false,
)

@Serializable
data class InstalledEnvironment(
    val webserver: String = "",
    val go: List<Lv> = emptyList(),
    val java: List<Lv> = emptyList(),
    val nodejs: List<Lv> = emptyList(),
    val php: List<Lv> = emptyList(),
    val python: List<Lv> = emptyList(),
    val dotnet: List<Lv> = emptyList(),
    val db: List<Lv> = emptyList(),
    val rsync: Boolean = false,
)

@Serializable
data class LogEntry(
    val time: String = "",
    val level: String = "",
    val msg: String = "",
    val type: String = "",
    @SerialName("operator_id") val operatorId: Long = 0,
    @SerialName("operator_name") val operatorName: String = "",
    val extra: JsonObject? = null,
)

@Serializable
data class SettingPanel(
    val server_name: String = "",
    val server_ip: String = "",
    val panel_version: String = "",
    val panel_port: Int = 0,
    val panel_ssl: Boolean = false,
    val panel_entrance: String = "",
    val panel_ip: String = "",
    val panel_domain: String = "",
    val timezone: String = "",
    val mfa: Boolean = false,
    val captcha: Boolean = false,
)

@Serializable
data class AlertRule(
    val id: Long = 0,
    val name: String = "",
    val type: String = "",
    val target: String = "",
    val operator: String = "gt",
    val threshold: Double = 0.0,
    val duration: Int = 1,
    val silence: Int = 30,
    val enabled: Boolean = true,
)

@Serializable
data class TaskInfo(
    val id: Long = 0,
    val name: String = "",
    val status: String = "",
    val progress: Int = 0,
    @SerialName("created_at") val createdAt: String = "",
)

@Serializable
data class PageResult<T>(
    val total: Long = 0,
    val items: List<T> = emptyList(),
)

fun JsonElement?.obj(): JsonObject? = this as? JsonObject
fun JsonElement?.arr(): JsonArray? = this as? JsonArray
fun JsonElement?.asString(): String = when (this) {
    is JsonPrimitive -> content
    else -> ""
}

fun JsonObject.string(key: String): String = this[key]?.asString() ?: ""
fun JsonObject.long(key: String): Long = this[key]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L
fun JsonObject.int(key: String): Int = this[key]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
fun JsonObject.bool(key: String): Boolean = this[key]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false
fun JsonObject.boolean(key: String): Boolean = bool(key)
fun JsonObject.objOrNull(key: String): JsonObject? = this[key] as? JsonObject
fun JsonObject.array(key: String): JsonArray? = this[key] as? JsonArray

fun JsonElement?.isNullish(): Boolean = this == null || this is JsonNull
