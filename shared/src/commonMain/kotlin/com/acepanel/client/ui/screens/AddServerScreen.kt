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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.acepanel.client.data.AceApi
import com.acepanel.client.data.AceStore
import com.acepanel.client.data.RootTab
import com.acepanel.client.ui.components.AceIcon
import com.acepanel.client.ui.components.AceSegmented
import com.acepanel.client.ui.components.AceText
import com.acepanel.client.ui.components.AceTextField
import com.acepanel.client.ui.components.InlineNotice
import com.acepanel.client.ui.components.MobilePageHeader
import com.acepanel.client.ui.components.rememberScreenScrollState
import com.acepanel.client.ui.theme.AceTheme
import com.acepanel.client.ui.theme.AceType
import com.composables.icons.lucide.Eye
import com.composables.icons.lucide.EyeOff
import com.composables.icons.lucide.Key
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.PlugZap
import com.composables.icons.lucide.ShieldCheck
import com.composables.icons.lucide.SquareTerminal
import kotlinx.coroutines.launch

@Composable
fun AddServerScreen(store: AceStore) {
    val colors = AceTheme.colors
    val scope = rememberCoroutineScope()
    var mode by remember { mutableStateOf(1) }
    var name by remember { mutableStateOf("") }
    var address by remember { mutableStateOf(store.loginAddress) }
    var username by remember { mutableStateOf(store.loginUsername) }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var group by remember { mutableStateOf("") }
    var remark by remember { mutableStateOf("") }

    var sshHost by remember { mutableStateOf("") }
    var sshPort by remember { mutableStateOf("22") }
    var sshUser by remember { mutableStateOf("root") }
    var sshAuth by remember { mutableStateOf(0) }
    var sshPassword by remember { mutableStateOf("") }
    var sshKey by remember { mutableStateOf("") }
    var sshPassphrase by remember { mutableStateOf("") }
    var sshPasswordVisible by remember { mutableStateOf(false) }

    var busy by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempted by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg)
            .windowInsetsPadding(WindowInsets.statusBars)
            .imePadding(),
    ) {
        MobilePageHeader(
            title = "添加服务器",
            subtitle = if (mode == 0) "登录 AcePanel 面板" else "通过 SSH 接入服务器",
            onBack = store::closeAddServer,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScreenScrollState())
                .padding(start = 26.dp, end = 26.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(15.dp),
        ) {
            AceSegmented(listOf("面板连接", "SSH 主机"), mode, onSelect = { mode = it; error = null })

            if (mode == 0) {
                LabeledField("服务器名称") {
                    AceTextField(name, { name = it }, placeholder = "例如：阿里云 · 香港")
                }
                LabeledField("面板地址") {
                    AceTextField(
                        address,
                        { address = it },
                        placeholder = "https://1.2.3.4:8443/安全入口",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                    )
                    AceText(
                        "面板地址可包含安全入口，例如 https://1.2.3.4:8443/0a2vc7",
                        style = AceType.caption,
                        color = colors.text3,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                    if (attempted && address.isBlank()) FieldError("请输入面板地址")
                }
                LabeledField("登录用户") {
                    AceTextField(username, { username = it }, placeholder = "面板账号", keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next))
                    if (attempted && username.isBlank()) FieldError("请输入面板账号")
                }
                LabeledField("登录密码") {
                    AceTextField(
                        password,
                        { password = it },
                        placeholder = "面板密码",
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailing = {
                            Box(Modifier.size(34.dp).clickable { passwordVisible = !passwordVisible }, contentAlignment = Alignment.Center) {
                                AceIcon(if (passwordVisible) Lucide.EyeOff else Lucide.Eye, tint = colors.text3, size = 19.dp)
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    )
                    if (attempted && password.isBlank()) FieldError("请输入密码")
                }
                LabeledField("分组") {
                    AceTextField(group, { group = it }, placeholder = "例如：生产环境（选填）")
                }
                LabeledField("备注") {
                    AceTextField(remark, { remark = it }, placeholder = "选填，例如机房、用途")
                }
                InlineNotice(
                    "面板密码仅用于本次登录换取会话，不会保存在本机。若面板启用了安全入口，请把入口一起填在地址中，例如 https://1.2.3.4:8443/0a2vc7。",
                    color = colors.accent,
                    background = colors.accentSoft,
                    icon = Lucide.ShieldCheck,
                )
            } else {
                LabeledField("服务器名称") {
                    AceTextField(name, { name = it }, placeholder = "例如：腾讯云 · 上海")
                }
                LabeledField("主机地址") {
                    AceTextField(sshHost, { sshHost = it }, placeholder = "IP 或域名", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next))
                    if (attempted && sshHost.isBlank()) FieldError("请输入主机地址")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LabeledField("SSH 端口", Modifier.weight(1f)) {
                        AceTextField(
                            sshPort,
                            { sshPort = it.filter { c -> c.isDigit() }.take(5) },
                            placeholder = "22",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                        )
                    }
                    LabeledField("登录用户", Modifier.weight(1f)) {
                        AceTextField(sshUser, { sshUser = it }, placeholder = "root", keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next))
                    }
                }
                LabeledField("认证方式") {
                    AceSegmented(listOf("密码", "SSH 密钥"), sshAuth, onSelect = { sshAuth = it })
                }
                if (sshAuth == 0) {
                    LabeledField("登录密码") {
                        AceTextField(
                            sshPassword,
                            { sshPassword = it },
                            placeholder = "SSH 登录密码",
                            visualTransformation = if (sshPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailing = {
                                Box(Modifier.size(34.dp).clickable { sshPasswordVisible = !sshPasswordVisible }, contentAlignment = Alignment.Center) {
                                    AceIcon(if (sshPasswordVisible) Lucide.EyeOff else Lucide.Eye, tint = colors.text3, size = 19.dp)
                                }
                            },
                        )
                    }
                } else {
                    LabeledField("SSH 私钥") {
                        AceTextField(
                            sshKey,
                            { sshKey = it },
                            placeholder = "-----BEGIN OPENSSH PRIVATE KEY-----",
                            singleLine = false,
                            height = 120.dp,
                        )
                    }
                    LabeledField("私钥口令") {
                        AceTextField(
                            sshPassphrase,
                            { sshPassphrase = it },
                            placeholder = "私钥无口令可留空",
                            visualTransformation = PasswordVisualTransformation(),
                        )
                    }
                }
                LabeledField("分组") {
                    AceTextField(group, { group = it }, placeholder = "例如：生产环境（选填）")
                }
                LabeledField("备注") {
                    AceTextField(remark, { remark = it }, placeholder = "选填，例如机房、用途")
                }
                InlineNotice(
                    "凭据使用面板主密钥加密后保存在面板服务器，客户端不会留存 SSH 密码或私钥。",
                    color = colors.accent,
                    background = colors.accentSoft,
                    icon = Lucide.Key,
                )
            }

            if (mode == 0) {
                com.acepanel.client.ui.components.SoftPillButton(
                    "还没有面板？直接 SSH 连接安装",
                    onClick = {
                        store.closeAddServer()
                        store.openDirectSshForm()
                    },
                    height = 42.dp,
                    background = colors.fill,
                )
            }
            error?.let { InlineNotice(it, color = colors.error, background = colors.errorSoft) }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.bg)
                .padding(start = 26.dp, end = 26.dp, top = 10.dp, bottom = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(colors.surface)
                    .clickable(enabled = !busy && !testing) {
                        if (mode == 0) {
                            attempted = true
                            if (address.isBlank() || username.isBlank() || password.isBlank()) return@clickable
                            testing = true
                            error = null
                            scope.launch {
                                val (base, entrance) = AceStore.splitPanelAddress(address)
                                val client = AceApi(base, entrance = entrance)
                                try {
                                    val start = com.acepanel.client.data.nowMillis()
                                    client.visitEntrance()
                                    client.fetchPublicKey()
                                    val elapsed = com.acepanel.client.data.nowMillis() - start
                                    store.toast("连接成功 · ${elapsed}ms")
                                } catch (t: Throwable) {
                                    error = store.friendlyError(t)
                                } finally {
                                    client.close()
                                    testing = false
                                }
                            }
                        } else {
                            attempted = true
                            error = if (sshHost.isBlank()) "请输入主机地址" else null
                            if (error == null) {
                                store.toast("字段校验通过，保存时由面板建立并加密保存连接")
                            }
                        }
                    },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AceIcon(Lucide.PlugZap, tint = colors.text1, size = 17.dp)
                Spacer(Modifier.width(8.dp))
                AceText(if (testing) "测试中…" else "测试连接", style = AceType.button.copy(fontSize = 14.sp), color = colors.text1)
            }
            Row(
                modifier = Modifier
                    .weight(1.1f)
                    .height(52.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (busy) colors.fill else colors.accent)
                    .clickable(enabled = !busy) {
                        attempted = true
                        if (mode == 0) {
                            if (address.isBlank() || username.isBlank() || password.isBlank()) {
                                error = "请填写面板地址、账号与密码"
                                return@clickable
                            }
                            busy = true
                            error = null
                            store.login(address, username, password, remember = true) { ok, msg ->
                                busy = false
                                if (!ok) {
                                    error = msg
                                    if (msg?.contains("两步验证") == true) {
                                        store.prepareLogin(address)
                                        store.closeAddServer()
                                    }
                                } else {
                                    store.closeAddServer()
                                }
                            }
                        } else {
                            if (sshHost.isBlank() || sshUser.isBlank() || sshPort.toIntOrNull() == null) {
                                error = "请填写有效的主机地址、端口与用户"
                                return@clickable
                            }
                            if (sshAuth == 0 && sshPassword.isBlank()) {
                                error = "请输入 SSH 登录密码"
                                return@clickable
                            }
                            if (sshAuth == 1 && sshKey.isBlank()) {
                                error = "请粘贴 SSH 私钥"
                                return@clickable
                            }
                            busy = true
                            error = null
                            store.addSshHost(
                                name = name.ifBlank { sshHost },
                                host = sshHost,
                                port = sshPort.toInt(),
                                user = sshUser,
                                authMethod = if (sshAuth == 0) "password" else "publickey",
                                password = if (sshAuth == 0) sshPassword else "",
                                key = if (sshAuth == 1) sshKey else "",
                                passphrase = sshPassphrase,
                                remark = listOf(remark, group).filter { it.isNotBlank() }.joinToString(" · "),
                            ) { ok, msg ->
                                busy = false
                                if (ok) {
                                    store.closeAddServer()
                                    store.selectedTab = RootTab.SERVERS
                                } else {
                                    error = msg
                                }
                            }
                        }
                    },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AceIcon(if (mode == 0) Lucide.ShieldCheck else Lucide.SquareTerminal, tint = if (busy) colors.text3 else Color.White, size = 17.dp)
                Spacer(Modifier.width(8.dp))
                AceText(
                    when {
                        busy -> "处理中…"
                        mode == 0 -> "保存并登录"
                        else -> "保存并连接"
                    },
                    style = AceType.button.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium),
                    color = if (busy) colors.text3 else Color.White,
                )
            }
        }
    }
}

@Composable
private fun LabeledField(label: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(7.dp)) {
        AceText(label, style = AceType.caption, color = AceTheme.colors.text3)
        content()
    }
}

@Composable
private fun FieldError(text: String) {
    AceText(text, style = AceType.caption, color = AceTheme.colors.error, modifier = Modifier.padding(start = 18.dp))
}
