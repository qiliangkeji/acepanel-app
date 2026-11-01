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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.acepanel.client.data.AceStore
import com.acepanel.client.data.RootTab
import com.acepanel.client.data.boolean
import com.acepanel.client.data.int
import com.acepanel.client.data.obj
import com.acepanel.client.data.string
import com.acepanel.client.ui.components.AceCard
import com.acepanel.client.ui.components.AceDialog
import com.acepanel.client.ui.components.AceIcon
import com.acepanel.client.ui.components.AceSwitch
import com.acepanel.client.ui.components.AceText
import com.acepanel.client.ui.components.ErrorState
import com.acepanel.client.ui.components.InlineNotice
import com.acepanel.client.ui.components.LoadingState
import com.acepanel.client.ui.components.MobilePageHeader
import com.acepanel.client.ui.components.RowChevron
import com.acepanel.client.ui.components.SoftPillButton
import com.acepanel.client.ui.components.rememberScreenScrollState
import com.acepanel.client.ui.theme.AceTheme
import com.acepanel.client.ui.theme.AceType
import com.composables.icons.lucide.CircleAlert
import com.composables.icons.lucide.Globe
import com.composables.icons.lucide.Lock
import com.composables.icons.lucide.LogOut
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.RefreshCw
import com.composables.icons.lucide.Server
import com.composables.icons.lucide.ShieldCheck
import com.composables.icons.lucide.SlidersHorizontal
import com.composables.icons.lucide.Clock
import com.composables.icons.lucide.Trash2
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import com.acepanel.client.data.runSafely

@Composable
fun SettingsScreen(store: AceStore) {
    val colors = AceTheme.colors
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    var setting by remember { mutableStateOf<JsonObject?>(null) }
    var timezone by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var showLogout by remember { mutableStateOf(false) }
    var showBindIp by remember { mutableStateOf(false) }
    var updating by remember { mutableStateOf(false) }
    var updateStatus by remember { mutableStateOf("") }

    suspend fun load() {
        loading = true
        error = null
        val client = store.api
        if (client == null) {
            error = "面板连接已断开"
            loading = false
            return
        }
        runSafely {
            setting = client.setting()
            timezone = client.getData("/api/toolbox_system/timezone").obj()?.string("timezone") ?: ""
        }.onFailure {
            error = store.friendlyError(it)
            if (it is com.acepanel.client.data.SessionExpiredException) store.handleSessionExpired()
        }
        loading = false
    }

    LaunchedEffect(store.api) { load() }

    fun updateBool(key: String, value: Boolean) {
        val current = setting ?: return
        if (updating) return
        val updated = JsonObject(current.toMutableMap().apply { put(key, JsonPrimitive(value)) })
        setting = updated
        updating = true
        scope.launch {
            runSafely { store.api?.postData("/api/setting", updated) }
                .onSuccess { store.toast("设置已更新") }
                .onFailure {
                    setting = current
                    store.toast(store.friendlyError(it), true)
                }
            updating = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        MobilePageHeader(
            title = "设置",
            subtitle = "AcePanel v${store.systemInfo?.panelVersion ?: "-"} · ${if (store.user?.role?.contains("admin") == true) "超级管理员" else "面板用户"}",
            onBack = store::closePage,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScreenScrollState())
                .padding(start = 26.dp, end = 26.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (loading) {
                LoadingState(rows = 3)
            } else if (error != null) {
                ErrorState(error!!, onRetry = { scope.launch { load() } })
            } else {
                val s = setting ?: JsonObject(emptyMap())
                AceCard {
                    SettingRow(Lucide.Globe, "面板地址", store.activeProfile?.baseUrl?.removePrefix("https://")?.removePrefix("http://") ?: "—") {
                        clipboard.setText(AnnotatedString(store.activeProfile?.baseUrl ?: ""))
                        store.toast("面板地址已复制")
                    }
                    RowDivider()
                    SettingRow(Lucide.Server, "监听端口", s.int("port").toString()) {
                        clipboard.setText(AnnotatedString(s.int("port").toString()))
                        store.toast("端口已复制")
                    }
                    RowDivider()
                    SettingRow(Lucide.Lock, "安全入口", s.string("entrance").ifBlank { "未设置" }) {
                        clipboard.setText(AnnotatedString(s.string("entrance")))
                        store.toast("安全入口已复制")
                    }
                    RowDivider()
                    SettingRow(Lucide.Clock, "默认时区", timezone.ifBlank { "系统默认" }) {
                        clipboard.setText(AnnotatedString(timezone))
                        store.toast("时区已复制")
                    }
                }

                AceCard {
                    ToggleRow(
                        title = "登录验证码",
                        subtitle = if (s.boolean("login_captcha")) "连续登录失败后启用图形验证码" else "当前仅校验账号密码",
                        checked = s.boolean("login_captcha"),
                        enabled = !updating,
                    ) { updateBool("login_captcha", it) }
                    RowDivider()
                    ToggleRow(
                        title = "双因素认证",
                        subtitle = if (s.boolean("two_fa")) "面板已启用两步验证" else "未启用两步验证",
                        checked = s.boolean("two_fa"),
                        enabled = !updating,
                    ) { updateBool("two_fa", it) }
                    RowDivider()
                    ToggleRow(
                        title = "仅允许指定 IP",
                        subtitle = if ((s["bind_ip"] as? JsonArray)?.isNotEmpty() == true) "已配置 IP 白名单" else "未配置白名单",
                        checked = (s["bind_ip"] as? JsonArray)?.isNotEmpty() == true,
                        enabled = true,
                    ) { showBindIp = true }
                }

                AceCard {
                    ActionRow(Lucide.RefreshCw, "检查更新", updateStatus.ifBlank { "已是最新 v${store.systemInfo?.panelVersion ?: "-"}" }) {
                        scope.launch {
                            runSafely { store.api?.checkUpdate() }
                                .onSuccess { data ->
                                    val update = data.obj()?.boolean("update") ?: false
                                    updateStatus = if (update) "发现新版本" else "已是最新"
                                    store.toast(if (update) "面板有新版本可用" else "当前已是最新版本")
                                }
                                .onFailure { store.toast(store.friendlyError(it), true) }
                        }
                    }
                    RowDivider()
                    ActionRow(Lucide.Trash2, "清除缓存", "本地临时数据") { store.clearLocalCache() }
                    RowDivider()
                    ActionRow(Lucide.LogOut, "退出登录", null, danger = true) { showLogout = true }
                }

                InlineNotice(
                    "设置项直接写入面板配置。修改端口、TLS 或安全入口可能导致面板重启，客户端需要重新连接。",
                    color = colors.accent,
                    background = colors.accentSoft,
                    icon = Lucide.CircleAlert,
                )
            }
        }
    }

    if (showLogout) {
        AceDialog(title = "退出登录", onDismiss = { showLogout = false }) {
            AceText("退出后将清除本机会话，需要重新登录面板。", style = AceType.bodySmall, color = colors.text2)
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SoftPillButton("取消", { showLogout = false }, modifier = Modifier.weight(1f), height = 44.dp, background = colors.fill)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(colors.error)
                        .clickable {
                            showLogout = false
                            store.signOut()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    AceText("退出登录", style = AceType.button.copy(fontSize = 14.sp), color = Color.White)
                }
            }
        }
    }
    if (showBindIp) {
        AceDialog(title = "IP 访问白名单", onDismiss = { showBindIp = false }) {
            AceText(
                "为保证连接可靠，客户端不直接修改白名单。请登录面板 Web 控制台，在「设置 → 安全」中配置允许访问面板的 IP。修改后当前连接可能需要重新登录。",
                style = AceType.bodySmall,
                color = colors.text2,
            )
            Spacer(Modifier.height(18.dp))
            PrimaryCloseButton("知道了") { showBindIp = false }
        }
    }
}

@Composable
private fun PrimaryCloseButton(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(AceTheme.colors.accent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        AceText(text, style = AceType.button.copy(fontSize = 14.sp), color = Color.White)
    }
}

@Composable
private fun SettingRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    value: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        AceIcon(icon, tint = AceTheme.colors.text3, size = 19.dp)
        AceText(title, style = AceType.listPrimary, color = AceTheme.colors.text1, modifier = Modifier.weight(1f))
        // 值不加宽上限时（面板地址是整条 URL、时区是一长串）会先于 weight(1f) 的标题完成测量，把标题挤到折行
        AceText(
            value,
            style = AceType.secondary,
            color = AceTheme.colors.text3,
            maxLines = 1,
            modifier = Modifier.widthIn(max = 126.dp),
        )
        RowChevron()
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            AceText(title, style = AceType.listPrimary, color = AceTheme.colors.text1)
            AceText(subtitle, style = AceType.caption, color = AceTheme.colors.text3)
        }
        AceSwitch(checked, enabled = enabled, onCheckedChange = onChange)
    }
}

@Composable
private fun ActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    value: String?,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = AceTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        AceIcon(icon, tint = if (danger) colors.error else colors.text3, size = 19.dp)
        AceText(title, style = AceType.listPrimary, color = if (danger) colors.error else colors.text1, modifier = Modifier.weight(1f))
        if (value != null) {
            AceText(
                value,
                style = AceType.secondary,
                color = colors.text3,
                maxLines = 1,
                modifier = Modifier.widthIn(max = 126.dp),
            )
        }
        RowChevron(if (danger) colors.error.copy(alpha = 0.5f) else colors.text3)
    }
}

@Composable
private fun RowDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = 50.dp)
            .height(1.dp)
            .background(AceTheme.colors.divider),
    )
}
