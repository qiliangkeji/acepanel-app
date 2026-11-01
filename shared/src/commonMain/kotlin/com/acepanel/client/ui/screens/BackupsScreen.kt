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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.acepanel.client.data.AceStore
import com.acepanel.client.data.BackupFile
import com.acepanel.client.data.dateTimePart
import com.acepanel.client.data.long
import com.acepanel.client.data.string
import com.acepanel.client.ui.components.AceCard
import com.acepanel.client.ui.components.AceConfirmDialog
import com.acepanel.client.ui.components.AceDialog
import com.acepanel.client.ui.components.AceFormDialog
import com.acepanel.client.ui.components.AceFormField
import com.acepanel.client.ui.components.AceIcon
import com.acepanel.client.ui.components.AceSegmented
import com.acepanel.client.ui.components.AceText
import com.acepanel.client.ui.components.AceTextField
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
import com.composables.icons.lucide.Archive
import com.composables.icons.lucide.Cloud
import com.composables.icons.lucide.CloudUpload
import com.composables.icons.lucide.Database
import com.composables.icons.lucide.Globe
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.RotateCw
import com.composables.icons.lucide.Server
import com.composables.icons.lucide.Trash2
import kotlinx.coroutines.launch
import com.acepanel.client.data.runSafely

private enum class BackupFilter(val label: String) { ALL("全部"), DATABASE("数据库"), WEBSITE("网站"), PANEL("面板") }
private data class BackupRow(val type: String, val file: BackupFile)

@Composable
fun BackupsScreen(store: AceStore) {
    val colors = AceTheme.colors
    val scope = rememberCoroutineScope()
    var rows by remember { mutableStateOf<List<BackupRow>>(emptyList()) }
    var storageName by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var filter by remember { mutableStateOf(BackupFilter.ALL) }
    var showCreate by remember { mutableStateOf(false) }
    var showStorages by remember { mutableStateOf(false) }
    var showStorageForm by remember { mutableStateOf(false) }
    var storages by remember { mutableStateOf<List<kotlinx.serialization.json.JsonObject>>(emptyList()) }
    var deleteStorage by remember { mutableStateOf<kotlinx.serialization.json.JsonObject?>(null) }
    var restoreRow by remember { mutableStateOf<BackupRow?>(null) }
    var manage by remember { mutableStateOf<BackupRow?>(null) }
    var busy by remember { mutableStateOf(false) }

    suspend fun load() {
        loading = true
        error = null
        runSafely {
            val all = mutableListOf<BackupRow>()
            listOf("panel", "website", "mysql", "postgresql", "redis", "clickhouse").forEach { type ->
                runSafely { store.api?.backups(type)?.items ?: emptyList() }
                    .getOrDefault(emptyList()).forEach { all += BackupRow(type, it) }
            }
            rows = all.sortedByDescending { it.file.time }
            storages = store.api?.backupStorages()?.items ?: emptyList()
            storageName = storages.firstOrNull()?.string("name") ?: ""
        }.onFailure { error = store.friendlyError(it) }
        loading = false
    }

    LaunchedEffect(store.api) { load() }

    val filtered = rows.filter {
        when (filter) {
            BackupFilter.ALL -> true
            BackupFilter.DATABASE -> it.type in listOf("mysql", "postgresql", "redis", "clickhouse")
            BackupFilter.WEBSITE -> it.type == "website"
            BackupFilter.PANEL -> it.type == "panel"
        }
    }
    val dbCount = rows.count { it.type in listOf("mysql", "postgresql", "redis", "clickhouse") }
    val websiteCount = rows.count { it.type == "website" }
    val panelCount = rows.count { it.type == "panel" }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        MobilePageHeader(
            title = "备份",
            subtitle = "${rows.size} 份备份 · ${if (storageName.isBlank()) "面板本地存储" else storageName}",
            onBack = store::closePage,
            actions = {
                SoftPillButton("存储", { showStorages = true }, height = 36.dp, tint = colors.text2, background = colors.fill)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(colors.surface)
                        .clickable { scope.launch { load() } }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        AceIcon(Lucide.RotateCw, tint = colors.text2, size = 14.dp)
                        AceText("刷新", style = AceType.caption, color = colors.text2)
                    }
                }
            },
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScreenScrollState())
                .padding(start = 26.dp, end = 26.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (loading) {
                LoadingState(rows = 5)
            } else if (error != null) {
                ErrorState(error!!, onRetry = { scope.launch { load() } })
            } else {
                AceCard(padding = 20.dp) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                AceText("${rows.size} 份备份", style = AceType.bigNumber.copy(fontSize = 26.sp), color = colors.text1)
                                AceText("保留策略由面板备份设置决定", style = AceType.caption, color = colors.text3)
                            }
                            StatusPill(
                                if (storageName.isNotBlank()) "异地存储" else "本地存储",
                                if (storageName.isNotBlank()) colors.success else colors.text2,
                                if (storageName.isNotBlank()) colors.successSoft else colors.fill,
                                dot = false,
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            BackupStat("数据库", dbCount, Modifier.weight(1f))
                            BackupStat("网站", websiteCount, Modifier.weight(1f))
                            BackupStat("面板", panelCount, Modifier.weight(1f))
                        }
                        PrimaryPillButton("立即备份", { showCreate = true }, height = 46.dp, leadingIcon = Lucide.CloudUpload)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                    BackupTab(BackupFilter.ALL, rows.size, filter) { filter = it }
                    BackupTab(BackupFilter.DATABASE, dbCount, filter) { filter = it }
                    BackupTab(BackupFilter.WEBSITE, websiteCount, filter) { filter = it }
                    BackupTab(BackupFilter.PANEL, panelCount, filter) { filter = it }
                }
                if (filtered.isEmpty()) {
                    EmptyState(
                        title = "还没有备份",
                        subtitle = "为数据库、网站或面板创建第一份备份",
                        icon = Lucide.Archive,
                        actionText = "立即备份",
                        onAction = { showCreate = true },
                    )
                } else {
                    filtered.forEach { row ->
                        BackupCard(row) { manage = row }
                    }
                }
                InlineNotice(
                    if (storageName.isNotBlank()) "备份存储「$storageName」已启用，新备份会同步到该存储。" else "备份保存在面板本地目录。建议配置异地备份存储，防止服务器故障导致数据丢失。",
                    color = colors.accent,
                    background = colors.accentSoft,
                    icon = Lucide.Cloud,
                )
            }
        }
    }

    if (showCreate) {
        CreateBackupDialog(store, { showCreate = false }) {
            showCreate = false
            scope.launch { load() }
        }
    }
    if (showStorages) {
        AceDialog(title = "备份存储", onDismiss = { showStorages = false }) {
            if (storages.isEmpty()) {
                AceText("当前使用面板本地目录，建议添加 S3 / SFTP / WebDAV 异地存储。", style = AceType.caption, color = colors.text3)
            }
            storages.forEach { storage ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        AceText(storage.string("name").ifBlank { "存储 #${storage.long("id")}" }, style = AceType.bodySmall, color = colors.text1)
                        AceText(storage.string("type").uppercase(), style = AceType.caption.copy(fontFamily = FontFamily.Monospace), color = colors.text3)
                    }
                    SoftPillButton("删除", { deleteStorage = storage }, height = 30.dp, tint = colors.error, background = colors.errorSoft)
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SoftPillButton("关闭", { showStorages = false }, modifier = Modifier.weight(1f), height = 42.dp)
                Box(Modifier.weight(1f)) {
                    PrimaryPillButton("添加存储", { showStorageForm = true }, height = 42.dp)
                }
            }
        }
    }

    if (showStorageForm) {
        AceFormDialog(
            title = "添加备份存储",
            fields = listOf(
                AceFormField("类型（s3 / sftp / webdav）", "s3"),
                AceFormField("名称"),
                AceFormField("连接信息 JSON", multiline = true),
            ),
            confirmText = "添加",
            onDismiss = { showStorageForm = false },
        ) { values ->
            val name = values.getOrElse(1) { "" }.trim()
            val info = runSafely {
                com.acepanel.client.data.ApiJson.parseToJsonElement(
                    values.getOrElse(2) { "{}" }.ifBlank { "{}" },
                ) as kotlinx.serialization.json.JsonObject
            }.getOrNull()
            if (name.isBlank() || info == null) {
                store.toast("请填写名称和有效的连接信息 JSON", true)
            } else {
                scope.launch {
                    runSafely {
                        store.api?.createBackupStorage(values[0].trim().ifBlank { "s3" }, name, info)
                    }.onSuccess {
                        showStorageForm = false
                        store.toast("存储已添加")
                        load()
                    }.onFailure { store.toast(store.friendlyError(it), true) }
                }
            }
        }
    }

    deleteStorage?.let { storage ->
        AceConfirmDialog(
            title = "删除备份存储",
            message = "确认删除「${storage.string("name")}」？已上传的备份文件不会被删除。",
            confirmText = "删除",
            danger = true,
            onDismiss = { deleteStorage = null },
        ) {
            val id = storage.long("id")
            deleteStorage = null
            scope.launch {
                runSafely { store.api?.deleteBackupStorage(id) }
                    .onSuccess { store.toast("存储已删除"); load() }
                    .onFailure { store.toast(store.friendlyError(it), true) }
            }
        }
    }

    restoreRow?.let { row ->
        AceFormDialog(
            title = "恢复备份",
            fields = listOf(AceFormField("恢复目标（数据库名 / 网站名）", row.file.name.substringBeforeLast('.'))),
            confirmText = "开始恢复",
            onDismiss = { restoreRow = null },
        ) { values ->
            val target = values.first().trim()
            if (target.isBlank()) {
                store.toast("请填写恢复目标", true)
            } else {
                restoreRow = null
                manage = null
                scope.launch {
                    runSafely { store.api?.restoreBackup(row.type, row.file.name, target) }
                        .onSuccess { store.toast("恢复任务已提交") }
                        .onFailure { store.toast(store.friendlyError(it), true) }
                }
            }
        }
    }

    manage?.let { row ->
        AceDialog(title = row.file.name, onDismiss = { manage = null }) {
            AceText(row.file.path, style = AceType.caption.copy(fontFamily = FontFamily.Monospace), color = colors.text3)
            AceText("${row.file.size} · ${row.file.time.dateTimePart()}", style = AceType.caption, color = colors.text3)
            Spacer(Modifier.height(16.dp))
            SoftPillButton("恢复备份", { restoreRow = row }, height = 46.dp, tint = colors.accent, background = colors.accentSoft)
            Spacer(Modifier.height(10.dp))
            SoftPillButton(if (busy) "删除中…" else "删除备份", {
                if (busy) return@SoftPillButton
                busy = true
                scope.launch {
                    runSafely { store.api?.deleteBackup(row.type, row.file.name) }
                        .onSuccess {
                            store.toast("备份已删除")
                            manage = null
                            load()
                        }
                        .onFailure { store.toast(store.friendlyError(it), true) }
                    busy = false
                }
            }, height = 46.dp, tint = colors.error, background = colors.errorSoft)
        }
    }
}

@Composable
private fun BackupTab(filter: BackupFilter, count: Int, selected: BackupFilter, onSelect: (BackupFilter) -> Unit) {
    val colors = AceTheme.colors
    val active = selected == filter
    Column(modifier = Modifier.clickable { onSelect(filter) }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            AceText(filter.label, style = AceType.tab.copy(fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal), color = if (active) colors.text1 else colors.text2)
            AceText(count.toString(), style = AceType.caption, color = if (active) colors.accent else colors.text3)
        }
        Box(Modifier.width(if (active) 22.dp else 0.dp).height(3.dp).clip(RoundedCornerShape(999.dp)).background(colors.accent))
    }
}

@Composable
private fun BackupStat(label: String, count: Int, modifier: Modifier) {
    val colors = AceTheme.colors
    Box(modifier = modifier.clip(RoundedCornerShape(12.dp)).background(colors.surface2).padding(horizontal = 12.dp, vertical = 9.dp)) {
        Column {
            AceText(label, style = AceType.caption.copy(fontSize = 10.5.sp), color = colors.text3)
            AceText("$count 份", style = AceType.bodySmall.copy(fontWeight = FontWeight.SemiBold), color = colors.text1)
        }
    }
}

@Composable
private fun BackupCard(row: BackupRow, onClick: () -> Unit) {
    val colors = AceTheme.colors
    val typeLabel = when (row.type) {
        "website" -> "网站"
        "panel" -> "面板"
        "postgresql" -> "PostgreSQL"
        "redis" -> "Redis"
        "clickhouse" -> "ClickHouse"
        else -> "数据库"
    }
    AceCard(modifier = Modifier.clickable(onClick = onClick), padding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).background(colors.accentSoft),
                contentAlignment = Alignment.Center,
            ) {
                AceIcon(if (row.type == "panel") Lucide.Server else Lucide.Archive, tint = colors.accent, size = 19.dp)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                AceText(row.file.name, style = AceType.listPrimary, color = colors.text1, maxLines = 1, overflow = TextOverflow.Ellipsis)
                AceText(
                    "$typeLabel · ${row.file.time.dateTimePart()}",
                    style = AceType.caption.copy(fontFamily = FontFamily.Monospace),
                    color = colors.text3,
                    maxLines = 1,
                )
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                StatusPill("成功", colors.success, colors.successSoft)
                AceText(row.file.size, style = AceType.caption, color = colors.text3)
            }
        }
    }
}

@Composable
private fun CreateBackupDialog(store: AceStore, onDismiss: () -> Unit, onCreated: () -> Unit) {
    val colors = AceTheme.colors
    val scope = rememberCoroutineScope()
    var typeIndex by remember { mutableStateOf(0) }
    var target by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val types = listOf("website", "mysql")
    AceDialog(title = "立即备份", onDismiss = onDismiss) {
        AceSegmented(listOf("网站", "数据库"), typeIndex, onSelect = { typeIndex = it; target = "" })
        Spacer(Modifier.height(12.dp))
        AceText(if (typeIndex == 0) "网站名称" else "数据库名称", style = AceType.caption, color = colors.text3)
        Spacer(Modifier.height(6.dp))
        AceTextField(target, { target = it }, placeholder = if (typeIndex == 0) "例如 blog.example.com" else "例如 blog_prod")
        error?.let {
            Spacer(Modifier.height(10.dp))
            InlineNotice(it, color = colors.error, background = colors.errorSoft)
        }
        Spacer(Modifier.height(16.dp))
        PrimaryPillButton(if (busy) "提交中…" else "开始备份", {
            if (busy) return@PrimaryPillButton
            val type = types[typeIndex]
            val actualTarget = target.trim()
            if (actualTarget.isBlank()) {
                error = "请输入备份目标名称"
                return@PrimaryPillButton
            }
            busy = true
            scope.launch {
                runSafely { store.api?.createBackup(type, actualTarget) }
                    .onSuccess {
                        store.toast("备份任务已提交，可在备份列表查看进度")
                        onCreated()
                    }
                    .onFailure { error = store.friendlyError(it) }
                busy = false
            }
        }, height = 46.dp)
    }
}
