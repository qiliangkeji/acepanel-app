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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.acepanel.client.data.AceStore
import com.acepanel.client.data.Page
import com.acepanel.client.data.dateTimePart
import com.acepanel.client.data.long
import com.acepanel.client.data.string
import com.acepanel.client.platform.currentTimeMillis
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
import com.acepanel.client.ui.theme.AceTheme
import com.acepanel.client.ui.theme.AceType
import com.composables.icons.lucide.Fingerprint
import com.composables.icons.lucide.Key
import com.composables.icons.lucide.KeyRound
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.UserRound
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import com.acepanel.client.data.runSafely

private fun JsonObject.text(key: String): String = when (val value = this[key]) {
    null, JsonNull -> ""
    is JsonPrimitive -> if (value.isString) value.content else if (value.content == "null") "" else value.content
    else -> value.toString()
}

private fun JsonObject.ipList(): List<String> =
    (this["ips"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content } ?: emptyList()

@Composable
fun AccountCenterScreen(store: AceStore) {
    val colors = AceTheme.colors
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    var tab by remember { mutableStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var users by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var tokens by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var passkeys by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var passkeySupported by remember { mutableStateOf(true) }
    var createUser by remember { mutableStateOf(false) }
    var editUser by remember { mutableStateOf<Pair<JsonObject, String>?>(null) }
    var deleteUser by remember { mutableStateOf<JsonObject?>(null) }
    var createToken by remember { mutableStateOf(false) }
    var newToken by remember { mutableStateOf<String?>(null) }
    var deletePasskey by remember { mutableStateOf<JsonObject?>(null) }
    val currentUserId = store.user?.id ?: 1L

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
            val userPage: Page<JsonObject> = client.users(1, 100)
            val tokenPage: Page<JsonObject> = client.userTokens(currentUserId, 1, 100)
            val keyPage: Page<JsonObject> = runSafely { client.passkeys(currentUserId) }.getOrDefault(Page())
            userPage to tokenPage to keyPage
        }.onSuccess { (pair, keyPage) ->
            users = pair.first.items
            tokens = pair.second.items
            passkeys = keyPage.items
            passkeySupported = runSafely { client.passkeysSupported() }.getOrDefault(true)
        }.onFailure { error = store.friendlyError(it) }
        loading = false
    }

    LaunchedEffect(store.api) { load() }

    fun refresh() {
        scope.launch { load() }
    }

    fun runAction(success: String, block: suspend () -> Unit) {
        scope.launch {
            runSafely { block() }
                .onSuccess {
                    store.toast(success)
                    load()
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
            title = "用户与安全",
            subtitle = "账号 · 通行密钥 · 访问令牌",
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
                options = listOf("用户", "通行密钥", "访问令牌"),
                selectedIndex = tab,
                onSelect = { tab = it },
            )
            when {
                loading -> LoadingState(rows = 4)
                error != null -> ErrorState(error!!) { refresh() }
                tab == 0 -> UsersSection(
                    users = users,
                    currentUserId = currentUserId,
                    colors = colors,
                    onCreate = { createUser = true },
                    onEdit = { user, mode -> editUser = user to mode },
                    onDelete = { deleteUser = it },
                )
                tab == 1 -> PasskeysSection(
                    passkeys = passkeys,
                    supported = passkeySupported,
                    colors = colors,
                    onDelete = { deletePasskey = it },
                    onRefresh = { refresh() },
                )
                else -> TokensSection(
                    tokens = tokens,
                    currentUserId = currentUserId,
                    colors = colors,
                    onCreate = { createToken = true },
                    onDelete = { token ->
                        runAction("令牌已删除") { store.api?.deleteUserToken(token.long("id")) }
                    },
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (createUser) {
        AceFormDialog(
            title = "新建面板用户",
            fields = listOf(
                AceFormField("用户名"),
                AceFormField("初始密码", password = true),
                AceFormField("邮箱（可选）"),
            ),
            confirmText = "创建",
            onDismiss = { createUser = false },
        ) { values ->
            scope.launch {
                runSafely {
                    store.api?.createUser(values[0].trim(), values[1], values[2].trim())
                }.onSuccess {
                    createUser = false
                    store.toast("用户已创建")
                    load()
                }.onFailure { store.toast(store.friendlyError(it), true) }
            }
        }
    }

    editUser?.let { (user, mode) ->
        val id = user.long("id")
        val (title, label, initial, password) = when (mode) {
            "username" -> listOf("修改用户名", "新用户名", user.text("username"), "false")
            "email" -> listOf("修改邮箱", "新邮箱", user.text("email"), "false")
            else -> listOf("重置密码", "新密码", "", "true")
        }
        AceFormDialog(
            title = title,
            fields = listOf(AceFormField(label, initial, password = password == "true")),
            confirmText = "保存",
            onDismiss = { editUser = null },
        ) { values ->
            scope.launch {
                val value = values.first()
                runSafely {
                    when (mode) {
                        "username" -> store.api?.updateUserUsername(id, value.trim())
                        "email" -> store.api?.updateUserEmail(id, value.trim())
                        else -> store.api?.updateUserPassword(id, value)
                    }
                }.onSuccess {
                    editUser = null
                    store.toast("已保存")
                    load()
                }.onFailure { store.toast(store.friendlyError(it), true) }
            }
        }
    }

    deleteUser?.let { user ->
        AceConfirmDialog(
            title = "删除用户",
            message = "确认删除用户「${user.text("username")}」？该用户创建的资源不会被自动删除。",
            confirmText = "删除",
            danger = true,
            onDismiss = { deleteUser = null },
        ) {
            val id = user.long("id")
            deleteUser = null
            runAction("用户已删除") { store.api?.deleteUser(id) }
        }
    }

    if (createToken) {
        AceFormDialog(
            title = "新建访问令牌",
            fields = listOf(
                AceFormField("允许的 IP / CIDR（逗号分隔，留空不限）"),
                AceFormField("有效期（天）", "30", numeric = true),
            ),
            confirmText = "生成",
            onDismiss = { createToken = false },
        ) { values ->
            val ips = values[0].split(',', '\n', ' ').map { it.trim() }.filter { it.isNotEmpty() }
            val days = values[1].toIntOrNull()?.coerceIn(1, 3650) ?: 30
            val expiry = currentTimeMillis() + days * 86_400_000L
            scope.launch {
                runSafely {
                    store.api?.createUserToken(currentUserId, ips, expiry)
                }.onSuccess { created ->
                    createToken = false
                    newToken = created?.text("token")?.takeIf { it.isNotBlank() }
                    store.toast("令牌已创建")
                    load()
                }.onFailure { store.toast(store.friendlyError(it), true) }
            }
        }
    }

    newToken?.let { token ->
        AceDialog(title = "令牌已生成", onDismiss = { newToken = null }) {
            AceText(
                "令牌只显示一次，请立即复制并妥善保存。",
                style = AceType.caption,
                color = colors.warning,
            )
            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.fill)
                    .padding(12.dp),
            ) {
                AceText(
                    token,
                    style = AceType.caption.copy(fontFamily = FontFamily.Monospace),
                    color = colors.text1,
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SoftPillButton("关闭", { newToken = null }, modifier = Modifier.weight(1f), height = 42.dp)
                SoftPillButton(
                    "复制",
                    {
                        clipboard.setText(AnnotatedString(token))
                        store.toast("令牌已复制")
                    },
                    modifier = Modifier.weight(1f),
                    height = 42.dp,
                    tint = Color.White,
                    background = colors.accent,
                )
            }
        }
    }

    deletePasskey?.let { key ->
        AceConfirmDialog(
            title = "删除通行密钥",
            message = "删除后该设备将无法使用通行密钥登录，可重新在 Web 控制台注册。",
            confirmText = "删除",
            danger = true,
            onDismiss = { deletePasskey = null },
        ) {
            val id = key.long("id")
            deletePasskey = null
            runAction("通行密钥已删除") { store.api?.deletePasskey(id, currentUserId) }
        }
    }
}

@Composable
private fun UsersSection(
    users: List<JsonObject>,
    currentUserId: Long,
    colors: com.acepanel.client.ui.theme.AceColors,
    onCreate: () -> Unit,
    onEdit: (JsonObject, String) -> Unit,
    onDelete: (JsonObject) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        AceText("面板用户", style = AceType.section.copy(fontSize = 14.sp), color = colors.text1, modifier = Modifier.weight(1f))
        SoftPillButton("新建用户", onCreate, height = 34.dp, tint = colors.accent, background = colors.accentSoft)
    }
    if (users.isEmpty()) {
        EmptyState(title = "暂无用户", subtitle = "创建用户后可分配独立的登录凭据", icon = Lucide.UserRound)
    }
    users.forEach { user ->
        val id = user.long("id")
        val username = user.text("username").ifBlank { "未命名" }
        val email = user.text("email")
        val twoFa = user.text("two_fa").isNotBlank()
        AceCard(padding = 14.dp) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier.size(36.dp).clip(CircleShape).background(colors.accentSoft),
                    contentAlignment = Alignment.Center,
                ) {
                    AceText(username.take(2).uppercase(), style = AceType.caption.copy(fontWeight = FontWeight.SemiBold), color = colors.accent)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AceText(
                            username,
                            style = AceType.listPrimary,
                            color = colors.text1,
                            maxLines = 1,
                            // 用户名不限宽会把后面的“当前 / 2FA”徽标挤出卡片右边缘
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (id == currentUserId) {
                            StatusPill("当前", colors.accent, colors.accentSoft, dot = false)
                        }
                        if (twoFa) {
                            StatusPill("2FA", colors.success, colors.successSoft, dot = false)
                        }
                    }
                    AceText(
                        email.ifBlank { "ID $id · 未绑定邮箱" },
                        style = AceType.caption,
                        color = colors.text3,
                        maxLines = 1,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MiniAction("改名", colors) { onEdit(user, "username") }
                MiniAction("邮箱", colors) { onEdit(user, "email") }
                MiniAction("密码", colors) { onEdit(user, "password") }
                Spacer(Modifier.weight(1f))
                MiniAction("删除", colors, danger = true) { onDelete(user) }
            }
        }
    }
}

@Composable
private fun PasskeysSection(
    passkeys: List<JsonObject>,
    supported: Boolean,
    colors: com.acepanel.client.ui.theme.AceColors,
    onDelete: (JsonObject) -> Unit,
    onRefresh: () -> Unit,
) {
    if (!supported) {
        EmptyState(
            title = "当前面板未启用通行密钥",
            subtitle = "面板需要 HTTPS 且已开启 WebAuthn 支持",
            icon = Lucide.KeyRound,
        )
        return
    }
    AceCard(padding = 14.dp) {
        AceText("通行密钥", style = AceType.section.copy(fontSize = 14.sp), color = colors.text1)
        Spacer(Modifier.height(6.dp))
        AceText(
            "注册新的通行密钥需要设备指纹 / 生物识别验证，请在面板 Web 控制台完成注册；客户端支持查看与删除。",
            style = AceType.caption,
            color = colors.text3,
        )
    }
    if (passkeys.isEmpty()) {
        EmptyState(
            title = "尚未注册通行密钥",
            subtitle = "注册后可使用指纹、面容或安全密钥免密登录",
            icon = Lucide.Fingerprint,
        )
    }
    passkeys.forEach { key ->
        AceCard(padding = 14.dp) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(colors.fill),
                    contentAlignment = Alignment.Center,
                ) {
                    AceIcon(Lucide.Fingerprint, tint = colors.accent, size = 18.dp)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    AceText(key.text("name").ifBlank { "通行密钥 #${key.long("id")}" }, style = AceType.listPrimary, color = colors.text1)
                    AceText(
                        buildString {
                            append("添加于 ${key.text("created_at").dateTimePart().ifBlank { "—" }}")
                            val used = key.text("last_used_at")
                            if (used.isNotBlank()) append(" · 最近使用 ${used.dateTimePart()}")
                        },
                        style = AceType.caption,
                        color = colors.text3,
                    )
                    key.text("transports").takeIf { it.isNotBlank() }?.let {
                        AceText(it, style = AceType.caption.copy(fontFamily = FontFamily.Monospace), color = colors.text3)
                    }
                }
                MiniAction("删除", colors, danger = true) { onDelete(key) }
            }
        }
    }
}

@Composable
private fun TokensSection(
    tokens: List<JsonObject>,
    currentUserId: Long,
    colors: com.acepanel.client.ui.theme.AceColors,
    onCreate: () -> Unit,
    onDelete: (JsonObject) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            AceText("访问令牌", style = AceType.section.copy(fontSize = 14.sp), color = colors.text1)
            AceText("用于 API 自动化，用户 ID $currentUserId", style = AceType.caption, color = colors.text3)
        }
        SoftPillButton("新建令牌", onCreate, height = 34.dp, tint = colors.accent, background = colors.accentSoft)
    }
    if (tokens.isEmpty()) {
        EmptyState(
            title = "暂无访问令牌",
            subtitle = "令牌可限制来源 IP 与有效期，适合脚本和 CI 使用",
            icon = Lucide.Key,
        )
    }
    tokens.forEach { token ->
        val ips = token.ipList()
        AceCard(padding = 14.dp) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    AceText("令牌 #${token.long("id")}", style = AceType.listPrimary, color = colors.text1)
                    AceText(
                        if (ips.isEmpty()) "来源 IP：不限" else "来源 IP：${ips.joinToString(", ")}",
                        style = AceType.caption,
                        color = colors.text3,
                    )
                    AceText(
                        "到期：${token.text("expired_at").dateTimePart().ifBlank { "—" }} · 创建于 ${token.text("created_at").dateTimePart().ifBlank { "—" }}",
                        style = AceType.caption,
                        color = colors.text3,
                    )
                }
                MiniAction("删除", colors, danger = true) { onDelete(token) }
            }
        }
    }
}

@Composable
private fun MiniAction(
    label: String,
    colors: com.acepanel.client.ui.theme.AceColors,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
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
