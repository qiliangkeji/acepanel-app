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
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.acepanel.client.data.AceStore
import com.acepanel.client.data.Page
import com.acepanel.client.data.formatBytes
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
import com.acepanel.client.ui.components.LoadingState
import com.acepanel.client.ui.components.MobilePageHeader
import com.acepanel.client.ui.components.PrimaryPillButton
import com.acepanel.client.ui.components.SoftPillButton
import com.acepanel.client.ui.components.StatusPill
import com.acepanel.client.ui.components.rememberScreenScrollState
import com.acepanel.client.ui.theme.AceColors
import com.acepanel.client.ui.theme.AceTheme
import com.acepanel.client.ui.theme.AceType
import com.composables.icons.lucide.Box
import com.composables.icons.lucide.Database
import com.composables.icons.lucide.Key
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.RefreshCw
import com.composables.icons.lucide.Search
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import com.acepanel.client.data.runSafely

private val databaseTypeOptions = listOf(
    "mysql" to "MySQL",
    "postgresql" to "PostgreSQL",
    "redis" to "Redis",
    "mongodb" to "MongoDB",
    "clickhouse" to "ClickHouse",
    "sqlite" to "SQLite",
    "elasticsearch" to "Elasticsearch",
)

private fun JsonObject.text(key: String): String = when (val value = this[key]) {
    null, JsonNull -> ""
    is JsonPrimitive -> if (value.isString) value.content else if (value.content == "null") "" else value.content
    else -> value.toString()
}

private fun JsonObject.boolValue(key: String, default: Boolean = false): Boolean =
    (this[key] as? JsonPrimitive)?.booleanOrNull ?: default

private fun JsonObject.strings(key: String): List<String> =
    (this[key] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content } ?: emptyList()

private fun typeLabel(type: String): String =
    databaseTypeOptions.firstOrNull { it.first == type }?.second ?: type.ifBlank { "未知" }

@Composable
fun DataCenterScreen(store: AceStore) {
    val colors = AceTheme.colors
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var servers by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var users by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var serverEditor by remember { mutableStateOf<JsonObject?>(null) }
    var userEditor by remember { mutableStateOf<JsonObject?>(null) }
    var createUserForServer by remember { mutableStateOf(0L) }
    var deleteServer by remember { mutableStateOf<JsonObject?>(null) }
    var deleteDatabaseUser by remember { mutableStateOf<JsonObject?>(null) }
    var reload by remember { mutableStateOf(0) }

    // Redis
    var redisServerId by remember { mutableStateOf(0L) }
    var redisDb by remember { mutableStateOf("0") }
    var redisSearch by remember { mutableStateOf("") }
    var redisKeys by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var redisLoading by remember { mutableStateOf(false) }
    var redisError by remember { mutableStateOf<String?>(null) }
    var redisTotal by remember { mutableStateOf(0L) }
    var showAddKey by remember { mutableStateOf(false) }
    var keyDetail by remember { mutableStateOf<JsonObject?>(null) }
    var clearRedis by remember { mutableStateOf(false) }

    // Elasticsearch
    var esServerId by remember { mutableStateOf(0L) }
    var esIndices by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var esIndex by remember { mutableStateOf("") }
    var esSearch by remember { mutableStateOf("") }
    var esDocs by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var esLoading by remember { mutableStateOf(false) }
    var esError by remember { mutableStateOf<String?>(null) }
    var showCreateIndex by remember { mutableStateOf(false) }
    var showCreateDoc by remember { mutableStateOf(false) }
    var docDetail by remember { mutableStateOf<JsonObject?>(null) }
    var deleteIndex by remember { mutableStateOf<String?>(null) }

    val redisServers = servers.filter { it.text("type") == "redis" }
    val esServers = servers.filter { it.text("type") == "elasticsearch" }
    val mysqlServers = servers.filter { it.text("type") != "redis" && it.text("type") != "elasticsearch" }

    suspend fun load() {
        val client = store.api
        if (client == null) {
            error = "面板未连接"
            loading = false
            return
        }
        loading = true
        error = null
        runSafely {
            val serverPage: Page<JsonObject> = client.databaseServers(page = 1, limit = 200)
            val userPage: Page<JsonObject> = client.databaseUsers(page = 1, limit = 300)
            serverPage to userPage
        }.onSuccess { (serverPage, userPage) ->
            servers = serverPage.items
            users = userPage.items
            if (redisServerId == 0L) redisServerId = servers.firstOrNull { it.text("type") == "redis" }?.long("id") ?: 0L
            if (esServerId == 0L) esServerId = servers.firstOrNull { it.text("type") == "elasticsearch" }?.long("id") ?: 0L
        }.onFailure { error = store.friendlyError(it) }
        loading = false
    }

    suspend fun loadRedis() {
        val client = store.api ?: return
        if (redisServerId == 0L) return
        redisLoading = true
        redisError = null
        runSafely {
            client.redisKeys(redisServerId, redisDb.toIntOrNull() ?: 0, redisSearch, 1, 200)
        }.onSuccess {
            redisKeys = it.items
            redisTotal = it.total
        }.onFailure { redisError = store.friendlyError(it) }
        redisLoading = false
    }

    suspend fun loadEsDocs() {
        val client = store.api ?: return
        if (esServerId == 0L || esIndex.isBlank()) {
            esDocs = emptyList()
            return
        }
        esLoading = true
        esError = null
        runSafely { client.esDocuments(esServerId, esIndex, esSearch, 1, 100) }
            .onSuccess { esDocs = it.items }
            .onFailure { esError = store.friendlyError(it) }
        esLoading = false
    }

    suspend fun loadEsIndices() {
        val client = store.api ?: return
        if (esServerId == 0L) return
        esLoading = true
        esError = null
        runSafely { client.esIndices(esServerId) }
            .onSuccess {
                esIndices = it
                if (esIndex.isBlank() || it.none { index -> index.text("name") == esIndex }) {
                    esIndex = it.firstOrNull()?.text("name") ?: ""
                }
            }
            .onFailure { esError = store.friendlyError(it) }
        esLoading = false
        if (esIndex.isNotBlank()) loadEsDocs()
    }


    LaunchedEffect(store.api, reload) { load() }
    LaunchedEffect(tab, redisServerId, redisDb) {
        if (tab == 2 && redisServerId != 0L) loadRedis()
    }
    LaunchedEffect(tab, esServerId) {
        if (tab == 3 && esServerId != 0L) loadEsIndices()
    }

    fun refresh() {
        reload += 1
    }

    fun action(success: String, after: () -> Unit = {}, block: suspend () -> Unit) {
        scope.launch {
            runSafely { block() }
                .onSuccess {
                    store.toast(success)
                    after()
                }
                .onFailure { store.toast(store.friendlyError(it), true) }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        MobilePageHeader(
            title = "数据服务",
            subtitle = "数据库服务器 · 用户 · Redis · Elasticsearch",
            onBack = store::closePage,
            actions = {
                SoftPillButton("刷新", { refresh() }, height = 36.dp, tint = colors.text2, background = colors.fill)
            },
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScreenScrollState())
                .padding(horizontal = 26.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AceSegmented(
                options = listOf("服务器", "用户", "Redis", "ES"),
                selectedIndex = tab,
                onSelect = { tab = it },
            )
            when {
                loading -> LoadingState(rows = 4)
                error != null -> ErrorState(error!!) { refresh() }
                tab == 0 -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            AceText("数据库服务器", style = AceType.section.copy(fontSize = 14.sp), color = colors.text1)
                            AceText("${servers.size} 个实例 · 密码由面板加密存储", style = AceType.caption, color = colors.text3)
                        }
                        SoftPillButton(
                            "添加服务器",
                            { serverEditor = JsonObject(emptyMap()) },
                            height = 34.dp,
                            tint = colors.accent,
                            background = colors.accentSoft,
                        )
                    }
                    if (servers.isEmpty()) {
                        EmptyState(
                            title = "暂无数据库服务器",
                            subtitle = "添加 MySQL、PostgreSQL、Redis 或 Elasticsearch 实例后即可托管",
                            icon = Lucide.Database,
                        )
                    }
                    servers.forEach { server ->
                        ServerCard(
                            server = server,
                            colors = colors,
                            onSync = {
                                action("同步完成，正在刷新") {
                                    store.api?.syncDatabaseServer(server.long("id"))
                                }
                            },
                            onEdit = { serverEditor = server },
                            onDelete = { deleteServer = server },
                        )
                    }
                }
                tab == 1 -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            AceText("数据库用户", style = AceType.section.copy(fontSize = 14.sp), color = colors.text1)
                            AceText("${users.size} 个用户 · 支持 MySQL / PostgreSQL 等", style = AceType.caption, color = colors.text3)
                        }
                        SoftPillButton(
                            "新建用户",
                            {
                                createUserForServer = mysqlServers.firstOrNull()?.long("id") ?: 0L
                                userEditor = JsonObject(emptyMap())
                            },
                            height = 34.dp,
                            tint = colors.accent,
                            background = colors.accentSoft,
                        )
                    }
                    if (mysqlServers.isEmpty()) {
                        EmptyState(
                            title = "请先添加数据库服务器",
                            subtitle = "Redis 与 Elasticsearch 使用独立的键值 / 索引浏览",
                            icon = Lucide.Database,
                        )
                    } else if (users.isEmpty()) {
                        EmptyState(
                            title = "暂无数据库用户",
                            subtitle = "创建用户并分配数据库权限",
                            icon = Lucide.Key,
                        )
                    }
                    users.forEach { user ->
                        DatabaseUserCard(
                            user = user,
                            servers = servers,
                            colors = colors,
                            onEdit = { userEditor = user },
                            onDelete = { deleteDatabaseUser = user },
                        )
                    }
                }
                tab == 2 -> RedisSection(
                    colors = colors,
                    servers = redisServers,
                    serverId = redisServerId,
                    onServerChange = { redisServerId = it },
                    db = redisDb,
                    onDbChange = { redisDb = it },
                    search = redisSearch,
                    onSearchChange = { redisSearch = it },
                    keys = redisKeys,
                    total = redisTotal,
                    loading = redisLoading,
                    error = redisError,
                    onSearch = { scope.launch { loadRedis() } },
                    onAdd = { showAddKey = true },
                    onClear = { clearRedis = true },
                    onOpen = { keyDetail = it },
                    onDelete = { key ->
                        action("键已删除", after = { scope.launch { loadRedis() } }) {
                            store.api?.redisDeleteKey(redisServerId, redisDb.toIntOrNull() ?: 0, key.text("key"))
                        }
                    },
                )
                else -> ElasticsearchSection(
                    colors = colors,
                    servers = esServers,
                    serverId = esServerId,
                    onServerChange = { esServerId = it },
                    indices = esIndices,
                    index = esIndex,
                    onIndexChange = { index ->
                        esIndex = index
                        scope.launch { loadEsDocs() }
                    },
                    search = esSearch,
                    onSearchChange = { esSearch = it },
                    docs = esDocs,
                    loading = esLoading,
                    error = esError,
                    onRefresh = { scope.launch { loadEsIndices() } },
                    onCreateIndex = { showCreateIndex = true },
                    onDeleteIndex = { deleteIndex = it },
                    onCreateDoc = { showCreateDoc = true },
                    onOpenDoc = { docDetail = it },
                    onDeleteDoc = { doc ->
                        action("文档已删除", after = { scope.launch { loadEsDocs() } }) {
                            store.api?.esDeleteDocument(esServerId, esIndex, doc.text("id"))
                        }
                    },
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    serverEditor?.let { server ->
        val isNew = server.isEmpty()
        ServerDialog(
            server = server.takeIf { !isNew },
            onDismiss = { serverEditor = null },
            onSave = { name, type, host, port, username, password, remark ->
                scope.launch {
                    runSafely {
                        val client = store.api
                        if (isNew) {
                            client?.createDatabaseServer(name, type, host, port, username, password, remark)
                        } else {
                            client?.updateDatabaseServer(server.long("id"), name, host, port, username, password, remark)
                        }
                    }.onSuccess {
                        serverEditor = null
                        store.toast("服务器已保存")
                        load()
                    }.onFailure { store.toast(store.friendlyError(it), true) }
                }
            },
        )
    }

    userEditor?.let { user ->
        val isNew = user.isEmpty()
        DatabaseUserDialog(
            user = user.takeIf { !isNew },
            servers = mysqlServers,
            defaultServerId = createUserForServer,
            onDismiss = { userEditor = null },
            onSave = { serverId, username, password, host, privileges, remark ->
                scope.launch {
                    runSafely {
                        val client = store.api
                        if (isNew) {
                            client?.createDatabaseUser(serverId, username, password, host, privileges, remark)
                        } else {
                            client?.updateDatabaseUser(user.long("id"), password.ifBlank { null }, privileges, remark)
                        }
                    }.onSuccess {
                        userEditor = null
                        store.toast("用户已保存")
                        load()
                    }.onFailure { store.toast(store.friendlyError(it), true) }
                }
            },
        )
    }

    deleteServer?.let { server ->
        AceConfirmDialog(
            title = "删除数据库服务器",
            message = "确认删除「${server.text("name")}」？面板只会移除记录，不会卸载远端数据库。",
            confirmText = "删除",
            danger = true,
            onDismiss = { deleteServer = null },
        ) {
            val id = server.long("id")
            deleteServer = null
            action("服务器已删除") { store.api?.deleteDatabaseServer(id) }
        }
    }

    deleteDatabaseUser?.let { user ->
        AceConfirmDialog(
            title = "删除数据库用户",
            message = "确认删除用户「${user.text("username")}」？该操作会同步到数据库实例。",
            confirmText = "删除",
            danger = true,
            onDismiss = { deleteDatabaseUser = null },
        ) {
            val id = user.long("id")
            deleteDatabaseUser = null
            action("用户已删除") { store.api?.deleteDatabaseUser(id) }
        }
    }

    if (showAddKey) {
        AceFormDialog(
            title = "新建 Redis 键",
            fields = listOf(
                AceFormField("键名"),
                AceFormField("类型（string / list / set / zset / hash）", "string"),
                AceFormField("值", multiline = true),
                AceFormField("TTL（秒，0 表示不过期）", "0", numeric = true),
            ),
            confirmText = "保存",
            onDismiss = { showAddKey = false },
        ) { values ->
            val key = values[0].trim()
            val type = values[1].trim().ifBlank { "string" }
            if (key.isBlank()) {
                store.toast("请填写键名", true)
            } else {
                scope.launch {
                    runSafely {
                        store.api?.redisSetKey(
                            redisServerId,
                            redisDb.toIntOrNull() ?: 0,
                            key,
                            values[2],
                            type,
                            values[3].toLongOrNull() ?: 0,
                        )
                    }.onSuccess {
                        showAddKey = false
                        store.toast("键已保存")
                        loadRedis()
                    }.onFailure { store.toast(store.friendlyError(it), true) }
                }
            }
        }
    }

    keyDetail?.let { key ->
        KeyDetailDialog(
            key = key,
            colors = colors,
            onDismiss = { keyDetail = null },
            onDelete = {
                action("键已删除", after = {
                    keyDetail = null
                    scope.launch { loadRedis() }
                }) {
                    store.api?.redisDeleteKey(redisServerId, redisDb.toIntOrNull() ?: 0, key.text("key"))
                }
            },
            onTtl = { ttl ->
                action("TTL 已更新", after = { scope.launch { loadRedis() } }) {
                    store.api?.redisKeyTtl(redisServerId, redisDb.toIntOrNull() ?: 0, key.text("key"), ttl)
                }
            },
            onRename = { newName ->
                action("键已重命名", after = {
                    keyDetail = null
                    scope.launch { loadRedis() }
                }) {
                    store.api?.redisRenameKey(redisServerId, redisDb.toIntOrNull() ?: 0, key.text("key"), newName)
                }
            },
        )
    }

    if (clearRedis) {
        AceConfirmDialog(
            title = "清空 Redis 数据库",
            message = "确认清空 DB ${redisDb.ifBlank { "0" }}？该操作不可恢复。",
            confirmText = "清空",
            danger = true,
            onDismiss = { clearRedis = false },
        ) {
            clearRedis = false
            action("Redis 数据库已清空", after = { scope.launch { loadRedis() } }) {
                store.api?.redisClear(redisServerId, redisDb.toIntOrNull() ?: 0)
            }
        }
    }

    if (showCreateIndex) {
        AceFormDialog(
            title = "新建 Elasticsearch 索引",
            fields = listOf(AceFormField("索引名称")),
            confirmText = "创建",
            onDismiss = { showCreateIndex = false },
        ) { values ->
            val name = values.first().trim()
            if (name.isBlank()) {
                store.toast("请填写索引名称", true)
            } else {
                scope.launch {
                    runSafely { store.api?.esCreateIndex(esServerId, name) }
                        .onSuccess {
                            showCreateIndex = false
                            store.toast("索引已创建")
                            loadEsIndices()
                        }
                        .onFailure { store.toast(store.friendlyError(it), true) }
                }
            }
        }
    }

    deleteIndex?.let { index ->
        AceConfirmDialog(
            title = "删除 Elasticsearch 索引",
            message = "确认删除索引「$index」及其全部文档？该操作不可恢复。",
            confirmText = "删除",
            danger = true,
            onDismiss = { deleteIndex = null },
        ) {
            val name = index
            deleteIndex = null
            action("索引已删除", after = { scope.launch { loadEsIndices() } }) {
                store.api?.esDeleteIndex(esServerId, name)
            }
        }
    }

    if (showCreateDoc) {
        AceFormDialog(
            title = "新建文档",
            fields = listOf(
                AceFormField("文档 ID（留空自动生成）"),
                AceFormField("文档 JSON", multiline = true),
            ),
            confirmText = "保存",
            onDismiss = { showCreateDoc = false },
        ) { values ->
            val body = values[1].trim()
            scope.launch {
                runSafely { store.api?.esSetDocument(esServerId, esIndex, values[0].trim(), body) }
                    .onSuccess {
                        showCreateDoc = false
                        store.toast("文档已保存")
                        loadEsDocs()
                    }
                    .onFailure { store.toast(store.friendlyError(it), true) }
            }
        }
    }

    docDetail?.let { doc ->
        AceDialog(title = "文档 ${doc.text("id")}", onDismiss = { docDetail = null }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp, max = 360.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.terminal)
                    .padding(12.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                AceText(
                    prettyJsonText(doc.text("source")),
                    style = AceType.caption.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 16.sp),
                    color = colors.terminalText,
                )
            }
            Spacer(Modifier.height(12.dp))
            PrimaryPillButton("关闭", { docDetail = null }, height = 44.dp)
        }
    }
}

private fun prettyJsonText(text: String): String = runSafely {
    val element = com.acepanel.client.data.ApiJson.parseToJsonElement(text)
    kotlinx.serialization.json.Json { prettyPrint = true }.encodeToString(
        kotlinx.serialization.json.JsonElement.serializer(),
        element,
    )
}.getOrDefault(text)

@Composable
private fun ServerCard(
    server: JsonObject,
    colors: AceColors,
    onSync: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val status = server.text("status")
    val running = status.equals("running", true) || status.equals("valid", true) || status.equals("online", true)
    AceCard(padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).background(colors.accentSoft),
                contentAlignment = Alignment.Center,
            ) {
                AceIcon(Lucide.Database, tint = colors.accent, size = 18.dp)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AceText(
                        server.text("name"),
                        style = AceType.listPrimary,
                        color = colors.text1,
                        maxLines = 1,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    StatusPill(typeLabel(server.text("type")), colors.accent, colors.accentSoft, dot = false)
                    if (status.isNotBlank()) {
                        StatusPill(
                            if (running) "正常" else status,
                            if (running) colors.success else colors.error,
                            if (running) colors.successSoft else colors.errorSoft,
                            dot = false,
                        )
                    }
                }
                AceText(
                    "${server.text("host")}:${server.text("port")}",
                    style = AceType.caption.copy(fontFamily = FontFamily.Monospace),
                    color = colors.text3,
                )
                if (server.text("remark").isNotBlank()) {
                    AceText(server.text("remark"), style = AceType.caption, color = colors.text3, maxLines = 1)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            MiniChip("同步用户", colors, onClick = onSync)
            MiniChip("编辑", colors, onClick = onEdit)
            Spacer(Modifier.weight(1f))
            MiniChip("删除", colors, danger = true, onClick = onDelete)
        }
    }
}

@Composable
private fun DatabaseUserCard(
    user: JsonObject,
    servers: List<JsonObject>,
    colors: AceColors,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val serverId = user.long("server_id")
    val server = servers.firstOrNull { it.long("id") == serverId }
    val privileges = user.strings("privileges")
    val status = user.text("status")
    AceCard(padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).background(colors.fill),
                contentAlignment = Alignment.Center,
            ) {
                AceIcon(Lucide.Key, tint = colors.text2, size = 18.dp)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AceText(
                        user.text("username"),
                        style = AceType.listPrimary,
                        color = colors.text1,
                        maxLines = 1,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (server != null) {
                        StatusPill(
                            "${server.text("name")} · ${typeLabel(server.text("type"))}",
                            colors.accent,
                            colors.accentSoft,
                            dot = false,
                        )
                    } else if (status.isNotBlank()) {
                        StatusPill(status, colors.text3, colors.fill, dot = false)
                    }
                }
                AceText(
                    "主机：${user.text("host").ifBlank { "%" }} · 权限：${privileges.size} 项",
                    style = AceType.caption,
                    color = colors.text3,
                )
                if (user.text("remark").isNotBlank()) {
                    AceText(user.text("remark"), style = AceType.caption, color = colors.text3, maxLines = 1)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            MiniChip("编辑", colors, onClick = onEdit)
            Spacer(Modifier.weight(1f))
            MiniChip("删除", colors, danger = true, onClick = onDelete)
        }
    }
}

@Composable
private fun RedisSection(
    colors: AceColors,
    servers: List<JsonObject>,
    serverId: Long,
    onServerChange: (Long) -> Unit,
    db: String,
    onDbChange: (String) -> Unit,
    search: String,
    onSearchChange: (String) -> Unit,
    keys: List<JsonObject>,
    total: Long,
    loading: Boolean,
    error: String?,
    onSearch: () -> Unit,
    onAdd: () -> Unit,
    onClear: () -> Unit,
    onOpen: (JsonObject) -> Unit,
    onDelete: (JsonObject) -> Unit,
) {
    if (servers.isEmpty()) {
        EmptyState(
            title = "暂无 Redis 服务器",
            subtitle = "请先在「服务器」中添加类型为 Redis 的实例",
            icon = Lucide.Database,
        )
        return
    }
    Text("选择 Redis 实例", style = AceType.caption, color = colors.text3, modifier = Modifier.padding(start = 4.dp))
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        servers.forEach { server ->
            val id = server.long("id")
            SegmentChip(
                "${server.text("name")} · ${server.text("host")}:${server.text("port")}",
                id == serverId,
                colors,
            ) { onServerChange(id) }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            AceText("DB", style = AceType.caption, color = colors.text3)
            // 固定 86dp 减去输入框自身 18+18 内边距只剩 ~50dp，多位数字库编号会被吃掉
            Box(Modifier.widthIn(min = 104.dp)) {
                AceTextField(
                    db,
                    onDbChange,
                    height = 38.dp,
                    textStyle = AceType.caption.copy(fontFamily = FontFamily.Monospace),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
        }
        Box(Modifier.weight(1f)) {
            AceTextField(
                search,
                onSearchChange,
                height = 38.dp,
                placeholder = "搜索键名",
                textStyle = AceType.caption,
            )
        }
        SoftPillButton("查询", onSearch, height = 38.dp, tint = colors.accent, background = colors.accentSoft)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        MiniChip("新增键", colors, onClick = onAdd)
        Spacer(Modifier.weight(1f))
        AceText("共 $total 个键", style = AceType.caption, color = colors.text3, modifier = Modifier.align(Alignment.CenterVertically))
        MiniChip("清空 DB", colors, danger = true, onClick = onClear)
    }
    when {
        loading -> LoadingState(rows = 3)
        error != null -> ErrorState(error) {}
        keys.isEmpty() -> EmptyState(
            title = "当前 DB 没有键",
            subtitle = "调整 DB 编号或搜索条件后重试",
            icon = Lucide.Key,
        )
        else -> keys.forEach { key ->
            RedisKeyRow(key, colors, onOpen = { onOpen(key) }, onDelete = { onDelete(key) })
        }
    }
}

@Composable
private fun RedisKeyRow(key: JsonObject, colors: AceColors, onOpen: () -> Unit, onDelete: () -> Unit) {
    AceCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen), padding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AceText(
                        key.text("key"),
                        style = AceType.bodySmall.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium),
                        color = colors.text1,
                        maxLines = 1,
                    )
                    StatusPill(key.text("type").ifBlank { "string" }, colors.accent, colors.accentSoft, dot = false)
                }
                AceText(
                    key.text("value").replace('\n', ' ').take(90),
                    style = AceType.caption.copy(fontFamily = FontFamily.Monospace),
                    color = colors.text3,
                    maxLines = 1,
                )
                val ttl = key.long("ttl")
                AceText(
                    "TTL ${if (ttl > 0) "${ttl}s" else "永久"} · ${formatBytes(key.long("size"))}",
                    style = AceType.caption,
                    color = colors.text3,
                )
            }
            MiniChip("删除", colors, danger = true, onClick = onDelete)
        }
    }
}

@Composable
private fun KeyDetailDialog(
    key: JsonObject,
    colors: AceColors,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onTtl: (Long) -> Unit,
    onRename: (String) -> Unit,
) {
    var mode by remember { mutableStateOf<String?>(null) }
    var input by remember { mutableStateOf("") }
    AceDialog(title = key.text("key"), onDismiss = onDismiss) {
        if (mode == null) {
            AceText(
                "${key.text("type")} · TTL ${key.long("ttl")}s · ${formatBytes(key.long("size"))}",
                style = AceType.caption,
                color = colors.text3,
            )
            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 100.dp, max = 300.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.terminal)
                    .padding(12.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                AceText(
                    key.text("value").ifBlank { "<空值>" },
                    style = AceType.caption.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 16.sp),
                    color = colors.terminalText,
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MiniChip("TTL", colors) { mode = "ttl"; input = key.long("ttl").toString() }
                MiniChip("重命名", colors) { mode = "rename"; input = key.text("key") }
                Spacer(Modifier.weight(1f))
                MiniChip("删除", colors, danger = true, onClick = onDelete)
            }
        } else {
            AceTextField(
                input,
                { input = it },
                placeholder = if (mode == "ttl") "TTL（秒）" else "新键名",
                height = 44.dp,
                textStyle = AceType.bodySmall,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SoftPillButton("取消", { mode = null }, modifier = Modifier.weight(1f), height = 42.dp)
                Box(Modifier.weight(1f)) {
                    PrimaryPillButton(
                        "保存",
                        {
                            if (mode == "ttl") {
                                onTtl(input.toLongOrNull() ?: 0)
                            } else if (input.isNotBlank()) {
                                onRename(input.trim())
                            }
                            onDismiss()
                        },
                        height = 42.dp,
                    )
                }
            }
        }
    }
}

@Composable
private fun ElasticsearchSection(
    colors: AceColors,
    servers: List<JsonObject>,
    serverId: Long,
    onServerChange: (Long) -> Unit,
    indices: List<JsonObject>,
    index: String,
    onIndexChange: (String) -> Unit,
    search: String,
    onSearchChange: (String) -> Unit,
    docs: List<JsonObject>,
    loading: Boolean,
    error: String?,
    onRefresh: () -> Unit,
    onCreateIndex: () -> Unit,
    onDeleteIndex: (String) -> Unit,
    onCreateDoc: () -> Unit,
    onOpenDoc: (JsonObject) -> Unit,
    onDeleteDoc: (JsonObject) -> Unit,
) {
    if (servers.isEmpty()) {
        EmptyState(
            title = "暂无 Elasticsearch 服务器",
            subtitle = "请先在「服务器」中添加类型为 Elasticsearch 的实例",
            icon = Lucide.Database,
        )
        return
    }
    Text("选择 ES 实例", style = AceType.caption, color = colors.text3, modifier = Modifier.padding(start = 4.dp))
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        servers.forEach { server ->
            val id = server.long("id")
            SegmentChip(
                "${server.text("name")} · ${server.text("host")}:${server.text("port")}",
                id == serverId,
                colors,
            ) { onServerChange(id) }
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        AceText("索引", style = AceType.section.copy(fontSize = 14.sp), color = colors.text1, modifier = Modifier.weight(1f))
        SoftPillButton("刷新", onRefresh, height = 34.dp, tint = colors.text2, background = colors.fill)
        Spacer(Modifier.width(6.dp))
        SoftPillButton("新建索引", onCreateIndex, height = 34.dp, tint = colors.accent, background = colors.accentSoft)
    }
    if (indices.isEmpty()) {
        EmptyState(title = "暂无索引", subtitle = "创建索引后即可浏览和写入文档", icon = Lucide.Box)
    } else {
        indices.forEach { item ->
            val name = item.text("name")
            AceCard(
                modifier = Modifier.fillMaxWidth().clickable { onIndexChange(name) },
                padding = 12.dp,
                color = if (name == index) colors.accentSoft else colors.surface,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AceIcon(Lucide.Box, tint = if (name == index) colors.accent else colors.text2, size = 17.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        AceText(name, style = AceType.bodySmall.copy(fontFamily = FontFamily.Monospace), color = colors.text1)
                        AceText(
                            "文档 ${item.text("docs_count")} · 大小 ${item.text("store_size")} · 健康 ${item.text("health")}",
                            style = AceType.caption,
                            color = colors.text3,
                        )
                    }
                    MiniChip("删除", colors, danger = true) { onDeleteIndex(name) }
                }
            }
        }
    }
    if (index.isNotBlank()) {
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) {
                AceTextField(
                    search,
                    onSearchChange,
                    height = 38.dp,
                    placeholder = "搜索文档",
                    textStyle = AceType.caption,
                )
            }
            SoftPillButton("搜索", { onIndexChange(index) }, height = 38.dp, tint = colors.accent, background = colors.accentSoft)
            MiniChip("新建文档", colors, onClick = onCreateDoc)
        }
        when {
            loading -> LoadingState(rows = 3)
            error != null -> ErrorState(error) {}
            docs.isEmpty() -> EmptyState(title = "索引暂无文档", subtitle = "可以创建一个 JSON 文档", icon = Lucide.Box)
            else -> docs.forEach { doc ->
                AceCard(padding = 12.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            AceText(doc.text("id"), style = AceType.bodySmall.copy(fontFamily = FontFamily.Monospace), color = colors.text1)
                            AceText(
                                doc.text("source").replace('\n', ' ').take(100),
                                style = AceType.caption.copy(fontFamily = FontFamily.Monospace),
                                color = colors.text3,
                                maxLines = 2,
                            )
                        }
                        MiniChip("查看", colors) { onOpenDoc(doc) }
                        MiniChip("删除", colors, danger = true) { onDeleteDoc(doc) }
                    }
                }
            }
        }
    }
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun SegmentChip(label: String, selected: Boolean, colors: AceColors, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) colors.accentSoft else colors.fill)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        AceText(label, style = AceType.tag, color = if (selected) colors.accent else colors.text2, maxLines = 1)
    }
}

@Composable
private fun MiniChip(label: String, colors: AceColors, danger: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (danger) colors.errorSoft else colors.fill)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        AceText(label, style = AceType.tag, color = if (danger) colors.error else colors.text2)
    }
}

@Composable
private fun Text(text: String, style: androidx.compose.ui.text.TextStyle, color: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    AceText(text, modifier = modifier, style = style, color = color)
}

@Composable
private fun ServerDialog(
    server: JsonObject?,
    onDismiss: () -> Unit,
    onSave: (String, String, String, Int, String, String, String) -> Unit,
) {
    val colors = AceTheme.colors
    var name by remember { mutableStateOf(server?.text("name").orEmpty()) }
    var type by remember { mutableStateOf(server?.text("type")?.ifBlank { "mysql" } ?: "mysql") }
    var host by remember { mutableStateOf(server?.text("host")?.ifBlank { "127.0.0.1" } ?: "127.0.0.1") }
    var port by remember { mutableStateOf(server?.text("port")?.ifBlank { "3306" } ?: "3306") }
    var username by remember { mutableStateOf(server?.text("username").orEmpty()) }
    var password by remember { mutableStateOf("") }
    var remark by remember { mutableStateOf(server?.text("remark").orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }

    fun defaultPort(value: String): String = when (value) {
        "mysql", "mariadb" -> "3306"
        "postgresql" -> "5432"
        "redis" -> "6379"
        "mongodb" -> "27017"
        "clickhouse" -> "8123"
        "elasticsearch" -> "9200"
        else -> "0"
    }

    AceDialog(title = if (server == null) "添加数据库服务器" else "编辑数据库服务器", onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 430.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AceTextField(name, { name = it }, placeholder = "实例名称（字母 / 数字 / 下划线）", height = 44.dp, textStyle = AceType.bodySmall)
            AceText("类型", style = AceType.caption, color = colors.text3)
            // 3 列时每格只有 ~59dp，"Elasticsearch"/"PostgreSQL" 会被切成半截
            databaseTypeOptions.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    row.forEach { (value, label) ->
                        Box(Modifier.weight(1f)) {
                            SegmentChip(label, type == value, colors) {
                                type = value
                                port = defaultPort(value)
                            }
                        }
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            AceTextField(host, { host = it }, placeholder = "主机地址", height = 44.dp, textStyle = AceType.bodySmall)
            AceTextField(port, { port = it }, placeholder = "端口", height = 44.dp, textStyle = AceType.bodySmall, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            AceTextField(username, { username = it }, placeholder = "用户名", height = 44.dp, textStyle = AceType.bodySmall)
            AceTextField(
                password,
                { password = it },
                placeholder = if (server == null) "密码" else "密码（留空保持原密码）",
                height = 44.dp,
                textStyle = AceType.bodySmall,
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
            )
            AceTextField(remark, { remark = it }, placeholder = "备注（可选）", height = 44.dp, textStyle = AceType.bodySmall)
            error?.let { AceText(it, style = AceType.caption, color = colors.error) }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SoftPillButton("取消", onDismiss, modifier = Modifier.weight(1f), height = 44.dp)
            Box(Modifier.weight(1f)) {
                PrimaryPillButton("保存", {
                    if (name.isBlank() || host.isBlank()) {
                        error = "请填写实例名称和主机地址"
                    } else if ((port.toIntOrNull() ?: 0) !in 1..65535) {
                        error = "端口需要在 1 - 65535 之间"
                    } else if (password.isBlank()) {
                        error = "请重新输入数据库密码以校验连接"
                    } else {
                        onSave(
                            name.trim(),
                            type,
                            host.trim(),
                            port.toIntOrNull() ?: 0,
                            username.trim(),
                            password,
                            remark.trim(),
                        )
                    }
                }, height = 44.dp)
            }
        }
    }
}

@Composable
private fun DatabaseUserDialog(
    user: JsonObject?,
    servers: List<JsonObject>,
    defaultServerId: Long,
    onDismiss: () -> Unit,
    onSave: (Long, String, String, String, List<String>, String) -> Unit,
) {
    val colors = AceTheme.colors
    var serverId by remember { mutableStateOf(user?.long("server_id")?.takeIf { it != 0L } ?: defaultServerId) }
    var username by remember { mutableStateOf(user?.text("username").orEmpty()) }
    var password by remember { mutableStateOf("") }
    var host by remember { mutableStateOf(user?.text("host")?.ifBlank { "%" } ?: "%") }
    var privileges by remember { mutableStateOf(user?.strings("privileges")?.joinToString(", ").orEmpty()) }
    var remark by remember { mutableStateOf(user?.text("remark").orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }

    AceDialog(title = if (user == null) "新建数据库用户" else "编辑数据库用户", onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 430.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (user == null) {
                AceText("所属服务器", style = AceType.caption, color = colors.text3)
                if (servers.isEmpty()) {
                    AceText("暂无可用数据库服务器", style = AceType.bodySmall, color = colors.warning)
                }
                servers.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        row.forEach { server ->
                            val id = server.long("id")
                            Box(Modifier.weight(1f)) {
                                SegmentChip(
                                    "${server.text("name")} · ${typeLabel(server.text("type"))}",
                                    serverId == id,
                                    colors,
                                ) { serverId = id }
                            }
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
                AceTextField(username, { username = it }, placeholder = "用户名", height = 44.dp, textStyle = AceType.bodySmall)
                AceTextField(
                    password,
                    { password = it },
                    placeholder = "密码",
                    height = 44.dp,
                    textStyle = AceType.bodySmall,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                )
                AceTextField(host, { host = it }, placeholder = "允许主机（MySQL，默认 %）", height = 44.dp, textStyle = AceType.bodySmall)
                AceTextField(privileges, { privileges = it }, placeholder = "权限，逗号分隔，如 db1.*,db2.*", height = 44.dp, textStyle = AceType.bodySmall)
                AceTextField(remark, { remark = it }, placeholder = "备注（可选）", height = 44.dp, textStyle = AceType.bodySmall)
            } else {
                AceText(
                    "${user.text("username")} @ ${servers.firstOrNull { it.long("id") == serverId }?.text("name") ?: "服务器 #$serverId"}",
                    style = AceType.listPrimary,
                    color = colors.text1,
                )
                AceTextField(
                    password,
                    { password = it },
                    placeholder = "新密码（留空不修改）",
                    height = 44.dp,
                    textStyle = AceType.bodySmall,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                )
                AceTextField(privileges, { privileges = it }, placeholder = "权限，逗号分隔", height = 44.dp, textStyle = AceType.bodySmall)
                AceTextField(remark, { remark = it }, placeholder = "备注", height = 44.dp, textStyle = AceType.bodySmall)
            }
            error?.let { AceText(it, style = AceType.caption, color = colors.error) }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SoftPillButton("取消", onDismiss, modifier = Modifier.weight(1f), height = 44.dp)
            Box(Modifier.weight(1f)) {
                PrimaryPillButton("保存", {
                    if (user == null && (serverId == 0L || username.isBlank() || password.isBlank())) {
                        error = "请选择服务器并填写用户名和密码"
                    } else {
                        onSave(
                            serverId,
                            username.trim(),
                            password,
                            host.trim().ifBlank { "%" },
                            privileges.split(',', '\n').map { it.trim() }.filter { it.isNotEmpty() },
                            remark.trim(),
                        )
                    }
                }, height = 44.dp)
            }
        }
    }
}
