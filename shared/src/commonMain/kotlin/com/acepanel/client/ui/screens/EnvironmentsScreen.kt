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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.acepanel.client.data.AceStore
import com.acepanel.client.data.EnvironmentItem
import com.acepanel.client.data.boolean
import com.acepanel.client.data.string
import com.acepanel.client.ui.components.AceCard
import com.acepanel.client.ui.components.AceDialog
import com.acepanel.client.ui.components.AceIcon
import com.acepanel.client.ui.components.AceText
import com.acepanel.client.ui.components.CircleAction
import com.acepanel.client.ui.components.EmptyState
import com.acepanel.client.ui.components.ErrorState
import com.acepanel.client.ui.components.InlineNotice
import com.acepanel.client.ui.components.LoadingState
import com.acepanel.client.ui.components.MobilePageHeader
import com.acepanel.client.ui.components.PrimaryPillButton
import com.acepanel.client.ui.components.SoftPillButton
import com.acepanel.client.ui.components.StatusPill
import com.acepanel.client.ui.components.rememberScreenScrollState
import com.acepanel.client.ui.theme.AceTheme
import com.acepanel.client.ui.theme.AceType
import com.composables.icons.lucide.Braces
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Code
import com.composables.icons.lucide.Coffee
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.RefreshCw
import com.composables.icons.lucide.Rocket
import com.composables.icons.lucide.Terminal
import com.composables.icons.lucide.Box
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import com.acepanel.client.data.runSafely

private data class RuntimeInfo(val type: String, val label: String, val icon: ImageVector)

private val runtimes = listOf(
    RuntimeInfo("php", "PHP", Lucide.Code),
    RuntimeInfo("nodejs", "Node.js", Lucide.Braces),
    RuntimeInfo("java", "Java", Lucide.Coffee),
    RuntimeInfo("go", "Go", Lucide.Box),
    RuntimeInfo("python", "Python", Lucide.Terminal),
    RuntimeInfo("dotnet", ".NET", Lucide.Rocket),
)

@Composable
fun EnvironmentsScreen(store: AceStore) {
    val colors = AceTheme.colors
    val scope = rememberCoroutineScope()
    var all by remember { mutableStateOf<List<EnvironmentItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var manageType by remember { mutableStateOf<RuntimeInfo?>(null) }
    var phpModules by remember { mutableStateOf<List<JsonObject>>(emptyList()) }

    suspend fun load() {
        loading = true
        error = null
        runSafely {
            all = store.api?.environments() ?: emptyList()
            val phpSlug = all.firstOrNull { it.type == "php" && it.installed }?.slug
            phpModules = if (phpSlug != null) store.api?.phpModules(phpSlug) ?: emptyList() else emptyList()
        }.onFailure { error = store.friendlyError(it) }
        loading = false
    }

    LaunchedEffect(store.api) { load() }

    val installedTypes = runtimes.filter { rt -> all.any { it.type == rt.type && it.installed } }
    val currentPhp = all.firstOrNull { it.type == "php" && it.installed }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        MobilePageHeader(
            title = "运行环境",
            subtitle = "${installedTypes.size} 种运行时${currentPhp?.let { " · PHP ${it.installedVersion.ifBlank { it.version }}" } ?: ""}",
            onBack = store::closePage,
            actions = {
                CircleAction(Lucide.RefreshCw, { scope.launch { load() } }, contentDescription = "刷新环境")
            },
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScreenScrollState())
                .padding(start = 26.dp, end = 26.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (loading) {
                LoadingState(rows = 5)
            } else if (error != null) {
                ErrorState(error!!, onRetry = { scope.launch { load() } })
            } else {
                if (installedTypes.isEmpty()) {
                    EmptyState(
                        title = "还没有安装运行环境",
                        subtitle = "在应用商店安装 PHP、Node.js、Java、Go 或 Python",
                        icon = Lucide.Rocket,
                    )
                } else {
                    AceCard(padding = 0.dp) {
                        Column {
                            runtimes.forEach { rt ->
                                val items = all.filter { it.type == rt.type }
                                val installed = items.filter { it.installed }
                                if (installed.isNotEmpty()) {
                                    val current = installed.first()
                                    RuntimeRow(
                                        runtime = rt,
                                        version = current.installedVersion.ifBlank { current.version },
                                        description = current.description,
                                        isPhp = rt.type == "php",
                                        onClick = { manageType = rt },
                                    )
                                    val lastInstalled = runtimes.lastOrNull { r -> all.any { it.type == r.type && it.installed } }
                                    if (lastInstalled != rt) {
                                        Box(Modifier.fillMaxWidth().padding(start = 58.dp).height(1.dp).background(colors.divider))
                                    }
                                }
                            }
                        }
                    }
                }
                if (phpModules.isNotEmpty()) {
                    AceCard(padding = 18.dp) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                AceText("PHP 扩展", style = AceType.section, color = colors.text1, modifier = Modifier.weight(1f))
                                AceText("${phpModules.count { it.boolean("installed") }} 个已启用", style = AceType.caption, color = colors.text3)
                            }
                            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                phpModules.filter { it.boolean("installed") }.take(12).forEach { module ->
                                    Box(
                                        Modifier.clip(RoundedCornerShape(999.dp)).background(colors.fill).padding(horizontal = 12.dp, vertical = 6.dp),
                                    ) {
                                        AceText(module.string("name").ifBlank { module.string("slug") }, style = AceType.caption, color = colors.text2)
                                    }
                                }
                            }
                        }
                    }
                }
                InlineNotice(
                    "切换 PHP 版本会重启所有 PHP 站点，建议在低峰期操作。",
                    color = colors.accent,
                    background = colors.accentSoft,
                )
            }
        }
    }

    manageType?.let { runtime ->
        val items = all.filter { it.type == runtime.type }
        ManageRuntimeDialog(store, runtime, items, { manageType = null }) { scope.launch { load() } }
    }
}

@Composable
private fun RuntimeRow(
    runtime: RuntimeInfo,
    version: String,
    description: String,
    isPhp: Boolean,
    onClick: () -> Unit,
) {
    val colors = AceTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        Box(
            modifier = Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).background(colors.fill),
            contentAlignment = Alignment.Center,
        ) {
            AceIcon(runtime.icon, tint = colors.text1, size = 19.dp)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                AceText(
                    runtime.label,
                    style = AceType.listPrimary,
                    color = colors.text1,
                    maxLines = 1,
                    modifier = Modifier.weight(1f, fill = false),
                )
                AceText(
                    version,
                    style = AceType.caption.copy(fontFamily = FontFamily.Monospace),
                    color = colors.text3,
                    maxLines = 1,
                    modifier = Modifier.widthIn(max = 88.dp),
                )
                if (isPhp) StatusPill("当前", colors.accent, colors.accentSoft, dot = false) else StatusPill("已安装", colors.text2, colors.fill, dot = false)
            }
            AceText(description.ifBlank { "已安装并可用" }, style = AceType.caption, color = colors.text3, maxLines = 1)
        }
        AceIcon(Lucide.ChevronRight, tint = colors.text3, size = 15.dp)
    }
}

@Composable
private fun ManageRuntimeDialog(
    store: AceStore,
    runtime: RuntimeInfo,
    items: List<EnvironmentItem>,
    onDismiss: () -> Unit,
    onChanged: () -> Unit,
) {
    val colors = AceTheme.colors
    val scope = rememberCoroutineScope()
    var busySlug by remember { mutableStateOf<String?>(null) }
    AceDialog(title = "${runtime.label} 版本", onDismiss = onDismiss) {
        Column(modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
            items.forEach { env ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AceText(
                        env.installedVersion.ifBlank { env.version },
                        style = AceType.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = colors.text1,
                        modifier = Modifier.width(80.dp),
                    )
                    AceText(env.slug, style = AceType.caption, color = colors.text3, modifier = Modifier.weight(1f))
                    if (env.installed) {
                        if (env.hasUpdate) {
                            Box(
                                Modifier.clip(RoundedCornerShape(999.dp)).background(colors.accent).clickable(enabled = busySlug == null) {
                                    busySlug = env.slug
                                    scope.launch {
                                        runSafely { store.api?.updateEnvironment(env.type, env.slug) }
                                            .onSuccess { store.toast("更新任务已提交"); onChanged() }
                                            .onFailure { store.toast(store.friendlyError(it), true) }
                                        busySlug = null
                                    }
                                }.padding(horizontal = 10.dp, vertical = 5.dp),
                            ) {
                                AceText("更新", style = AceType.caption, color = Color.White)
                            }
                        } else {
                            Box(
                                Modifier.clip(RoundedCornerShape(999.dp)).background(colors.errorSoft).clickable(enabled = busySlug == null) {
                                    busySlug = env.slug
                                    scope.launch {
                                        runSafely { store.api?.uninstallEnvironment(env.type, env.slug) }
                                            .onSuccess { store.toast("卸载任务已提交"); onChanged() }
                                            .onFailure { store.toast(store.friendlyError(it), true) }
                                        busySlug = null
                                    }
                                }.padding(horizontal = 10.dp, vertical = 5.dp),
                            ) {
                                AceText("卸载", style = AceType.caption, color = colors.error)
                            }
                        }
                    } else {
                        Box(
                            Modifier.clip(RoundedCornerShape(999.dp)).background(colors.accent).clickable(enabled = busySlug == null) {
                                busySlug = env.slug
                                scope.launch {
                                    runSafely { store.api?.installEnvironment(env.type, env.slug) }
                                        .onSuccess { store.toast("安装任务已提交"); onChanged() }
                                        .onFailure { store.toast(store.friendlyError(it), true) }
                                    busySlug = null
                                }
                            }.padding(horizontal = 10.dp, vertical = 5.dp),
                        ) {
                            AceText("安装", style = AceType.caption, color = Color.White)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        AceText("安装与卸载会在面板后台执行，可在任务列表查看进度。", style = AceType.caption, color = colors.text3)
    }
}
