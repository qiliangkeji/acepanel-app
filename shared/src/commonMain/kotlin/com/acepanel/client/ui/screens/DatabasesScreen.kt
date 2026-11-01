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
import com.acepanel.client.data.DatabaseItem
import com.acepanel.client.data.DatabaseServer
import com.acepanel.client.data.DatabaseUser
import com.acepanel.client.ui.components.AceCard
import com.acepanel.client.ui.components.AceDialog
import com.acepanel.client.ui.components.AceIcon
import com.acepanel.client.ui.components.AceText
import com.acepanel.client.ui.components.AceTextField
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
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Database
import com.composables.icons.lucide.HardDrive
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Server
import com.composables.icons.lucide.TriangleAlert
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import com.acepanel.client.data.runSafely

@Composable
fun DatabasesScreen(store: AceStore) {
    val colors = AceTheme.colors
    val scope = rememberCoroutineScope()
    var servers by remember { mutableStateOf<List<DatabaseServer>>(emptyList()) }
    var databases by remember { mutableStateOf<List<DatabaseItem>>(emptyList()) }
    var users by remember { mutableStateOf<List<DatabaseUser>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var showCreate by remember { mutableStateOf(false) }
    var manage by remember { mutableStateOf<DatabaseItem?>(null) }
    var busy by remember { mutableStateOf(false) }

    suspend fun load() {
        loading = true
        error = null
        runSafely {
            servers = store.api?.databaseServers()?.items ?: emptyList()
            databases = store.api?.databases()?.items ?: emptyList()
            users = store.api?.databaseUsers()?.items ?: emptyList()
        }.onFailure { error = store.friendlyError(it) }
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
            title = "数据库",
            subtitle = "${servers.size} 个实例 · ${databases.size} 个库",
            onBack = store::closePage,
            actions = {
                CircleAction(Lucide.Plus, { showCreate = true }, background = colors.accent, tint = Color.White, contentDescription = "新建数据库")
            },
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScreenScrollState())
                .padding(start = 26.dp, end = 26.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (loading) {
                LoadingState(rows = 4)
            } else if (error != null) {
                ErrorState(error!!, onRetry = { scope.launch { load() } })
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    SummaryCard("数据库实例", servers.size.toString(), "个", Modifier.weight(1f))
                    SummaryCard("数据库", databases.size.toString(), "个", Modifier.weight(1f))
                }
                AceCard(padding = 0.dp) {
                    Column {
                        Row(
                            Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AceText("实例", style = AceType.section, color = colors.text1, modifier = Modifier.weight(1f))
                            AceText("${users.size} 个用户", style = AceType.caption, color = colors.text3)
                        }
                        if (servers.isEmpty()) {
                            AceText(
                                "还没有数据库实例，请先在应用商店安装 MySQL、PostgreSQL 或 Redis",
                                style = AceType.secondary,
                                color = colors.text3,
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                            )
                        } else {
                            servers.forEachIndexed { index, server ->
                                ServerRow(server)
                                if (index != servers.lastIndex) RowDivider()
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
                AceCard(padding = 0.dp) {
                    Column {
                        Row(
                            Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AceText("数据库", style = AceType.section, color = colors.text1, modifier = Modifier.weight(1f))
                            AceText("${databases.size} 个", style = AceType.caption, color = colors.text3)
                        }
                        if (databases.isEmpty()) {
                            EmptyState(
                                title = "还没有数据库",
                                subtitle = "创建数据库用于网站、项目或应用",
                                icon = Lucide.Database,
                                actionText = "新建数据库",
                                onAction = { showCreate = true },
                            )
                        } else {
                            databases.forEachIndexed { index, db ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { manage = db }
                                        .padding(horizontal = 18.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    AceIcon(Lucide.Database, tint = colors.text2, size = 20.dp)
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                        AceText(db.name, style = AceType.listPrimary, color = colors.text1, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        AceText(
                                            listOfNotNull(
                                                db.type.uppercase().ifBlank { null },
                                                db.encoding.ifBlank { null },
                                                db.comment.ifBlank { null },
                                            ).joinToString(" · "),
                                            style = AceType.caption.copy(fontFamily = FontFamily.Monospace),
                                            color = colors.text3,
                                            maxLines = 1,
                                        )
                                    }
                                    AceIcon(Lucide.ChevronRight, tint = colors.text3, size = 15.dp)
                                }
                                if (index != databases.lastIndex) RowDivider()
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
                if (databases.isEmpty() && servers.isNotEmpty()) {
                    InlineNotice(
                        "数据库列表由面板通过本地数据库连接实时读取。若看不到数据，请检查实例状态与 root 凭据。",
                        color = colors.accent,
                        background = colors.accentSoft,
                    )
                }
            }
        }
        Box(Modifier.fillMaxWidth().padding(horizontal = 26.dp, vertical = 14.dp)) {
            PrimaryPillButton("新建数据库", { showCreate = true }, height = 50.dp, leadingIcon = Lucide.Plus)
        }
    }

    if (showCreate) {
        CreateDatabaseDialog(store, servers, { showCreate = false }, {
            showCreate = false
            scope.launch { load() }
        })
    }
    manage?.let { db ->
        AceDialog(title = db.name, onDismiss = { manage = null }) {
            AceText(
                "${db.type.uppercase()} · ${db.encoding} · 实例 ${db.server.ifBlank { db.serverId.toString() }}",
                style = AceType.caption.copy(fontFamily = FontFamily.Monospace),
                color = colors.text3,
            )
            if (db.comment.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                AceText(db.comment, style = AceType.bodySmall, color = colors.text2)
            }
            Spacer(Modifier.height(16.dp))
            SoftPillButton(
                if (busy) "处理中…" else "删除数据库",
                onClick = {
                    if (busy) return@SoftPillButton
                    busy = true
                    scope.launch {
                        runSafely { store.api?.deleteDatabase(db.serverId, db.name) }
                            .onSuccess {
                                store.toast("数据库已删除")
                                manage = null
                                load()
                            }
                            .onFailure { store.toast(store.friendlyError(it), true) }
                        busy = false
                    }
                },
                height = 46.dp,
                tint = colors.error,
                background = colors.errorSoft,
            )
        }
    }
}

@Composable
private fun SummaryCard(label: String, value: String, unit: String, modifier: Modifier = Modifier) {
    val colors = AceTheme.colors
    AceCard(modifier = modifier, padding = 16.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            AceText(label, style = AceType.caption, color = colors.text3)
            Row(verticalAlignment = Alignment.Bottom) {
                AceText(value, style = AceType.bigNumber.copy(fontSize = 24.sp), color = colors.text1)
                AceText(unit, style = AceType.caption, color = colors.text3, modifier = Modifier.padding(start = 4.dp, bottom = 3.dp))
            }
        }
    }
}

@Composable
private fun ServerRow(server: DatabaseServer) {
    val colors = AceTheme.colors
    val online = server.status.contains("running", true) || server.status.contains("active", true) ||
        server.status.contains("valid", true) || server.status.contains("正常", true)
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AceIcon(if (server.type.contains("redis", true)) Lucide.HardDrive else Lucide.Database, tint = colors.text2, size = 20.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            AceText(server.name, style = AceType.listPrimary, color = colors.text1, maxLines = 1)
            AceText(
                "${server.type.uppercase()} · ${server.host}:${server.port}",
                style = AceType.caption.copy(fontFamily = FontFamily.Monospace),
                color = colors.text3,
                maxLines = 1,
            )
        }
        StatusPill(
            if (online) "运行中" else "已停止",
            if (online) colors.success else colors.text3,
            if (online) colors.successSoft else colors.fill,
        )
        AceIcon(Lucide.ChevronRight, tint = colors.text3, size = 15.dp)
    }
}

@Composable
private fun CreateDatabaseDialog(
    store: AceStore,
    servers: List<DatabaseServer>,
    onDismiss: () -> Unit,
    onCreated: () -> Unit,
) {
    val colors = AceTheme.colors
    val scope = rememberCoroutineScope()
    var selected by remember { mutableStateOf(servers.firstOrNull()?.id ?: 0L) }
    var name by remember { mutableStateOf("") }
    var comment by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var serverPicker by remember { mutableStateOf(false) }

    AceDialog(title = "新建数据库", onDismiss = onDismiss) {
        if (servers.isEmpty()) {
            InlineNotice("请先安装并启动数据库实例（MySQL / PostgreSQL / Redis）", color = colors.warning, background = colors.warningSoft)
            Spacer(Modifier.height(14.dp))
            PrimaryPillButton("知道了", onDismiss, height = 44.dp)
            return@AceDialog
        }
        val server = servers.firstOrNull { it.id == selected } ?: servers.first()
        AceText("数据库实例", style = AceType.caption, color = colors.text3)
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(colors.fill)
                .clickable { serverPicker = !serverPicker }
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AceText("${server.name} · ${server.type.uppercase()}", style = AceType.bodySmall, color = colors.text1, modifier = Modifier.weight(1f))
            AceIcon(Lucide.ChevronRight, tint = colors.text3, size = 15.dp)
        }
        if (serverPicker) {
            Column(modifier = Modifier.padding(top = 6.dp)) {
                servers.forEach { s ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                selected = s.id
                                serverPicker = false
                            }
                            .padding(horizontal = 10.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AceText(s.name, style = AceType.bodySmall, color = if (s.id == selected) colors.accent else colors.text1, modifier = Modifier.weight(1f))
                        AceText(s.type.uppercase(), style = AceType.caption, color = colors.text3)
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        AceText("数据库名称", style = AceType.caption, color = colors.text3)
        Spacer(Modifier.height(6.dp))
        AceTextField(name, { name = it }, placeholder = "例如 blog_prod")
        Spacer(Modifier.height(12.dp))
        AceText("备注", style = AceType.caption, color = colors.text3)
        Spacer(Modifier.height(6.dp))
        AceTextField(comment, { comment = it }, placeholder = "选填")
        error?.let {
            Spacer(Modifier.height(10.dp))
            InlineNotice(it, color = colors.error, background = colors.errorSoft)
        }
        Spacer(Modifier.height(16.dp))
        PrimaryPillButton(
            if (busy) "创建中…" else "创建数据库",
            onClick = {
                if (busy) return@PrimaryPillButton
                if (name.isBlank()) {
                    error = "请输入数据库名称"
                    return@PrimaryPillButton
                }
                busy = true
                scope.launch {
                    runSafely {
                        store.api?.createDatabase(
                            buildJsonObject {
                                put("server_id", selected)
                                put("name", name.trim())
                                put("create_user", false)
                                put("comment", comment.trim())
                            },
                        )
                    }.onSuccess {
                        store.toast("数据库已创建")
                        onCreated()
                    }.onFailure { error = store.friendlyError(it) }
                    busy = false
                }
            },
            enabled = !busy,
            height = 48.dp,
        )
    }
}

@Composable
private fun RowDivider() {
    Box(Modifier.fillMaxWidth().padding(start = 50.dp).height(1.dp).background(AceTheme.colors.divider))
}
