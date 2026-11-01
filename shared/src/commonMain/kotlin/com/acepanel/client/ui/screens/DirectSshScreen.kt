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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.acepanel.client.data.AceStore
import com.acepanel.client.data.DirectSshConfig
import com.acepanel.client.platform.platformName
import com.acepanel.client.platform.rememberFilePicker
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
import com.composables.icons.lucide.FileKey
import com.composables.icons.lucide.KeyRound
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Server
import com.composables.icons.lucide.ShieldCheck
import com.composables.icons.lucide.Terminal
import com.acepanel.client.data.runSafely

@Composable
fun DirectSshScreen(store: AceStore) {
    val colors = AceTheme.colors
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("22") }
    var user by remember { mutableStateOf("root") }
    var name by remember { mutableStateOf("") }
    var auth by remember { mutableStateOf(0) }
    var password by remember { mutableStateOf("") }
    var privateKey by remember { mutableStateOf("") }
    var passphrase by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var attempted by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val keyPicker = rememberFilePicker { picked ->
        if (picked != null) {
            privateKey = runSafely { picked.bytes.decodeToString() }.getOrDefault("")
            auth = 1
            error = null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg)
            .windowInsetsPadding(WindowInsets.statusBars)
            .imePadding(),
    ) {
        MobilePageHeader(
            title = "SSH 直连",
            subtitle = "通过服务器 IP + 密码 / 私钥连接",
            onBack = store::closeDirectSshForm,
        )
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScreenScrollState())
                .padding(start = 26.dp, end = 26.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            InlineNotice(
                "适用于还没有安装 AcePanel 的服务器。连接后可直接在终端执行命令或点击“安装面板”一键运行官方安装脚本。",
                color = colors.accent,
                background = colors.accentSoft,
                icon = Lucide.Server,
            )
            if (platformName() == "iOS") {
                InlineNotice(
                    "当前 iOS 版本暂不支持客户端直连 SSH（需要额外集成 libssh2）。请使用 Android / 桌面端完成安装，或改用已安装面板的 SSH 主机。",
                    color = colors.warning,
                    background = colors.warningSoft,
                )
            }
            FieldLabel("服务器地址")
            AceTextField(
                host,
                { host = it },
                placeholder = "IP 或域名，例如 110.42.70.40",
                leading = { AceIcon(Lucide.Server, tint = colors.text3, size = 18.dp) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
            )
            if (attempted && host.isBlank()) FieldError("请输入服务器 IP 或域名")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    FieldLabel("SSH 端口")
                    AceTextField(
                        port,
                        { port = it.filter { c -> c.isDigit() }.take(5) },
                        placeholder = "22",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                    )
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    FieldLabel("登录用户")
                    AceTextField(user, { user = it }, placeholder = "root", keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next))
                }
            }
            if (attempted && (port.toIntOrNull() == null || port.toInt() !in 1..65535)) FieldError("请输入有效 SSH 端口")
            FieldLabel("连接名称（选填）")
            AceTextField(name, { name = it }, placeholder = "例如 腾讯云 · 上海")
            FieldLabel("认证方式")
            AceSegmented(listOf("密码", "SSH 私钥"), auth, onSelect = { auth = it; error = null })
            if (auth == 0) {
                FieldLabel("登录密码")
                AceTextField(
                    password,
                    { password = it },
                    placeholder = "服务器 SSH 密码",
                    leading = { AceIcon(Lucide.KeyRound, tint = colors.text3, size = 18.dp) },
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailing = {
                        Box(
                            Modifier.size(34.dp).clickable { passwordVisible = !passwordVisible },
                            contentAlignment = Alignment.Center,
                        ) {
                            AceIcon(if (passwordVisible) Lucide.EyeOff else Lucide.Eye, tint = colors.text3, size = 19.dp)
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                )
                if (attempted && password.isBlank()) FieldError("请输入 SSH 登录密码")
            } else {
                FieldLabel("SSH 私钥")
                AceTextField(
                    privateKey,
                    { privateKey = it },
                    placeholder = "-----BEGIN OPENSSH PRIVATE KEY-----",
                    singleLine = false,
                    height = 130.dp,
                    textStyle = AceType.caption.copy(fontFamily = FontFamily.Monospace, fontSize = 11.5.sp),
                )
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(colors.fill)
                        .clickable { keyPicker.launch() }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    AceIcon(Lucide.FileKey, tint = colors.text2, size = 16.dp)
                    AceText("从本地文件选择私钥", style = AceType.caption, color = colors.text2)
                }
                FieldLabel("私钥口令（无口令留空）")
                AceTextField(
                    passphrase,
                    { passphrase = it },
                    placeholder = "可选",
                    visualTransformation = PasswordVisualTransformation(),
                )
                if (attempted && privateKey.isBlank()) FieldError("请粘贴或选择 SSH 私钥")
            }
            error?.let { InlineNotice(it, color = colors.error, background = colors.errorSoft) }
            InlineNotice(
                "连接使用 SSH 协议直连服务器，不经过面板。私钥与密码只保存在当前内存会话中。",
                color = colors.accent,
                background = colors.accentSoft,
                icon = Lucide.ShieldCheck,
            )
        }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 26.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            Row(
                modifier = Modifier
                    .widthIn(max = 708.dp)
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(colors.accent)
                    .clickable(enabled = platformName() != "iOS") {
                        attempted = true
                        if (host.isBlank()) {
                            error = "请输入服务器 IP 或域名"
                            return@clickable
                        }
                        val portValue = port.toIntOrNull()
                        if (portValue == null || portValue !in 1..65535) {
                            error = "请输入有效 SSH 端口"
                            return@clickable
                        }
                        if (user.isBlank()) {
                            error = "请输入登录用户"
                            return@clickable
                        }
                        if (auth == 0 && password.isBlank()) {
                            error = "请输入 SSH 登录密码"
                            return@clickable
                        }
                        if (auth == 1 && privateKey.isBlank()) {
                            error = "请粘贴或选择 SSH 私钥"
                            return@clickable
                        }
                        store.openDirectSshTerminal(
                            DirectSshConfig(
                                name = name.trim(),
                                host = host.trim(),
                                port = portValue,
                                username = user.trim(),
                                authMethod = if (auth == 0) "password" else "publickey",
                                password = password,
                                privateKey = privateKey,
                                passphrase = passphrase,
                            ),
                        )
                    },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AceIcon(Lucide.Terminal, tint = Color.White, size = 18.dp)
                Spacer(Modifier.size(8.dp))
                AceText("连接 SSH", style = AceType.button.copy(fontWeight = FontWeight.Medium), color = Color.White)
            }
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    AceText(text, style = AceType.caption, color = AceTheme.colors.text3)
}

@Composable
private fun FieldError(text: String) {
    AceText(text, style = AceType.caption, color = AceTheme.colors.error, modifier = Modifier.padding(start = 18.dp))
}
