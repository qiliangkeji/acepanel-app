package com.acepanel.client.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.acepanel.client.data.AceStore
import com.acepanel.client.resources.Res
import com.acepanel.client.resources.login_illustration
import com.acepanel.client.resources.login_illustration_desktop
import com.acepanel.client.ui.components.AceCheckBox
import com.acepanel.client.ui.components.AceDialog
import com.acepanel.client.ui.components.AceIcon
import com.acepanel.client.ui.components.AceLogoMark
import com.acepanel.client.ui.components.AceLogoWordmark
import com.acepanel.client.ui.components.AceSegmented
import com.acepanel.client.ui.components.AceText
import com.acepanel.client.ui.components.AceTextField
import com.acepanel.client.ui.components.CircleAction
import com.acepanel.client.ui.components.InlineNotice
import com.acepanel.client.ui.components.PrimaryPillButton
import com.acepanel.client.ui.components.SoftPillButton
import com.acepanel.client.ui.components.rememberScreenScrollState
import com.acepanel.client.ui.theme.AceDimens
import com.acepanel.client.ui.theme.AceTheme
import com.acepanel.client.ui.theme.AceType
import com.composables.icons.lucide.Activity
import com.composables.icons.lucide.Globe
import com.composables.icons.lucide.LayoutGrid
import com.composables.icons.lucide.Key
import com.composables.icons.lucide.Lock
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.RefreshCw
import com.composables.icons.lucide.ShieldCheck
import com.composables.icons.lucide.SquareTerminal
import com.composables.icons.lucide.Smartphone
import com.composables.icons.lucide.Eye
import com.composables.icons.lucide.EyeOff
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.jetbrains.compose.resources.painterResource
import com.acepanel.client.data.runSafely

@Composable
fun LoginScreen(store: AceStore, desktop: Boolean) {
    if (desktop) DesktopLogin(store) else MobileLogin(store)
}

@Composable
private fun MobileLogin(store: AceStore) {
    val colors = AceTheme.colors
    var attempted by remember { mutableStateOf(false) }
    var passwordVisible by remember { mutableStateOf(false) }
    var showForgot by remember { mutableStateOf(false) }
    var showTerms by remember { mutableStateOf(false) }
    var addressLocal by remember { mutableStateOf(store.loginAddress) }
    var usernameLocal by remember { mutableStateOf(store.loginUsername) }
    var passwordLocal by remember { mutableStateOf(store.loginPassword) }

    val addressBlank = addressLocal.isBlank()
    val usernameBlank = usernameLocal.isBlank()
    val passwordBlank = passwordLocal.isBlank()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg)
            .windowInsetsPadding(WindowInsets.statusBars)
            .verticalScroll(rememberScreenScrollState()),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 26.dp, end = 26.dp, top = 8.dp, bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Image(
                painter = painterResource(Res.drawable.login_illustration),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp),
                contentScale = ContentScale.Fit,
            )
            Column {
                AceText("登录 AcePanel", style = AceType.loginTitle, color = colors.text1)
                Spacer(Modifier.height(9.dp))
                AceText("一个客户端，管理全部服务器", style = AceType.body, color = colors.text2)
                Spacer(Modifier.height(6.dp))
                AceText("面板运维 · SSH 终端 · 实时监控", style = AceType.secondary, color = colors.text3)
            }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AceTextField(
                    value = addressLocal,
                    onValueChange = {
                        addressLocal = it
                        store.loginAddress = it
                    },
                    placeholder = "面板地址（可带安全入口，如 /0a2vc7）",
                    leading = { AceIcon(Lucide.Globe, tint = colors.text3, size = 19.dp) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                )
                if (attempted && addressBlank) FieldHint("请输入面板地址")
                AceTextField(
                    value = usernameLocal,
                    onValueChange = {
                        usernameLocal = it
                        store.loginUsername = it
                    },
                    placeholder = "账号 / 手机号",
                    leading = { AceIcon(Lucide.Smartphone, tint = colors.text3, size = 19.dp) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                )
                if (attempted && usernameBlank) FieldHint("请输入面板账号")
                AceTextField(
                    value = passwordLocal,
                    onValueChange = {
                        passwordLocal = it
                        store.loginPassword = it
                    },
                    placeholder = "密码",
                    leading = { AceIcon(Lucide.Lock, tint = colors.text3, size = 19.dp) },
                    trailing = {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clickable { passwordVisible = !passwordVisible },
                            contentAlignment = Alignment.Center,
                        ) {
                            AceIcon(
                                if (passwordVisible) Lucide.EyeOff else Lucide.Eye,
                                tint = colors.text3,
                                size = 19.dp,
                            )
                        }
                    },
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                )
                if (attempted && passwordBlank) FieldHint("请输入密码")

                if (store.loginTwoFaRequired) {
                    AceTextField(
                        value = store.loginPassCode,
                        onValueChange = { store.loginPassCode = it.filter { ch -> ch.isDigit() }.take(6) },
                        placeholder = "两步验证码",
                        leading = { AceIcon(Lucide.ShieldCheck, tint = colors.text3, size = 19.dp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    )
                }
                if (store.loginCaptchaRequired) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        AceTextField(
                            value = store.loginCaptchaCode,
                            onValueChange = { store.loginCaptchaCode = it },
                            placeholder = "验证码",
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        )
                        CaptchaImage(store.loginCaptchaImage) { store.refreshCaptcha() }
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { store.loginRemember = !store.loginRemember },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    AceCheckBox(store.loginRemember, { store.loginRemember = it })
                    AceText("记住登录状态", style = AceType.bodySmall, color = colors.text2)
                }
                AceText(
                    "忘记密码？",
                    style = AceType.bodySmall.copy(fontWeight = FontWeight.Medium),
                    color = colors.accent,
                    modifier = Modifier.clickable { showForgot = true },
                )
            }

            store.loginError?.let { error ->
                InlineNotice(error, color = colors.error, background = colors.errorSoft)
            }

            PrimaryPillButton(
                text = "登 录",
                onClick = {
                    attempted = true
                    if (!addressBlank && !usernameBlank && !passwordBlank) {
                        store.login(
                            address = addressLocal,
                            username = usernameLocal,
                            password = passwordLocal,
                        )
                    }
                },
                loading = store.loginLoading,
                enabled = !store.loginLoading,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(Modifier.weight(1f).height(1.dp).background(colors.divider))
                AceText("或", style = AceType.caption, color = colors.text3)
                Box(Modifier.weight(1f).height(1.dp).background(colors.divider))
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(colors.fill)
                    .clickable { store.openDirectSshForm() },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AceIcon(Lucide.Key, tint = colors.text1, size = 19.dp)
                Spacer(Modifier.width(9.dp))
                AceText("使用 SSH 密钥登录", style = AceType.button, color = colors.text1)
            }
            AceText(
                "登录即代表同意《用户协议》与《隐私政策》",
                style = AceType.caption,
                color = colors.text3,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showTerms = true },
            )
        }
    }

    if (showForgot) {
        InfoDialog(
            title = "找回面板密码",
            body = "AcePanel 的登录密码由服务器本地面板管理。请在服务器上执行 `acepanel user reset`，或联系面板管理员重置密码。SSH 密钥仅用于连接主机，不能代替面板密码。",
            onDismiss = { showForgot = false },
        )
    }
    if (showTerms) {
        InfoDialog(
            title = "用户协议与隐私政策",
            body = "AcePanel Client 仅在你主动添加的面板与本机之间通信，登录凭据只保存在本机。客户端不会收集面板数据或上传到第三方服务。请妥善保管服务器登录信息。",
            onDismiss = { showTerms = false },
        )
    }
}

@Composable
private fun DesktopLogin(store: AceStore) {
    val colors = AceTheme.colors
    var attempted by remember { mutableStateOf(false) }
    var passwordVisible by remember { mutableStateOf(false) }
    var mode by remember { mutableStateOf(0) }
    var showInfo by remember { mutableStateOf(false) }
    var showTerms by remember { mutableStateOf(false) }
    var addressLocal by remember { mutableStateOf(store.loginAddress) }
    var usernameLocal by remember { mutableStateOf(store.loginUsername) }
    var passwordLocal by remember { mutableStateOf(store.loginPassword) }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surface2),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(colors.surface)
                .padding(horizontal = 72.dp, vertical = 56.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AceLogoWordmark(iconSize = 34.dp)
                Spacer(Modifier.width(12.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(colors.accentSoft)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    AceText("Client 1.0", style = AceType.tag, color = colors.accent)
                }
            }
            Spacer(Modifier.weight(0.75f))
            AceText(
                "把面板和服务器，装进同一个客户端",
                style = AceType.loginTitle.copy(fontSize = 40.sp, lineHeight = 52.sp),
                color = colors.text1,
            )
            Spacer(Modifier.height(18.dp))
            AceText(
                "连接你的 AcePanel 面板，统一管理网站、数据库、容器与文件；内置 SSH 终端、SFTP 互传与实时监控，桌面端与移动端数据同步。",
                style = AceType.body.copy(lineHeight = 24.sp),
                color = colors.text2,
                modifier = Modifier.widthIn(max = 520.dp),
            )
            Spacer(Modifier.height(20.dp))
            Image(
                painter = painterResource(Res.drawable.login_illustration_desktop),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp),
                contentScale = ContentScale.Fit,
            )
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(70.dp)) {
                DesktopFeature(Lucide.LayoutGrid, "面板运维", "网站 · 数据库 · 容器 · 文件")
                DesktopFeature(Lucide.SquareTerminal, "SSH 终端", "多标签会话 · SFTP 互传")
                DesktopFeature(Lucide.Activity, "实时监控", "资源曲线 · 告警推送")
            }
        }
        Box(
            modifier = Modifier
                .width(520.dp)
                .fillMaxHeight()
                .padding(horizontal = 28.dp, vertical = 40.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(colors.surface)
                    .padding(horizontal = 30.dp, vertical = 30.dp),
            ) {
                AceText("登录到控制台", style = AceType.loginTitle.copy(fontSize = 25.sp), color = colors.text1)
                Spacer(Modifier.height(7.dp))
                AceText("使用面板账户登录，或切换到 SSH 密钥", style = AceType.secondary, color = colors.text3)
                Spacer(Modifier.height(22.dp))
                AceSegmented(listOf("密码登录", "SSH 密钥"), mode, onSelect = { mode = it })
                Spacer(Modifier.height(20.dp))
                if (mode == 0) {
                    DesktopFieldLabel("面板地址")
                    AceTextField(
                        value = addressLocal,
                        onValueChange = {
                            addressLocal = it
                            store.loginAddress = it
                        },
                        placeholder = "https://acepanel.net:8443/0a2vc7",
                        leading = { AceIcon(Lucide.Globe, tint = colors.text3, size = 18.dp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                    )
                    Spacer(Modifier.height(14.dp))
                    DesktopFieldLabel("账号 / 手机号")
                    AceTextField(
                        value = usernameLocal,
                        onValueChange = {
                            usernameLocal = it
                            store.loginUsername = it
                        },
                        placeholder = "请输入面板账号",
                        leading = { AceIcon(Lucide.Smartphone, tint = colors.text3, size = 18.dp) },
                    )
                    Spacer(Modifier.height(14.dp))
                    DesktopFieldLabel("密码")
                    AceTextField(
                        value = passwordLocal,
                        onValueChange = {
                            passwordLocal = it
                            store.loginPassword = it
                        },
                        placeholder = "请输入密码",
                        leading = { AceIcon(Lucide.Lock, tint = colors.text3, size = 18.dp) },
                        trailing = {
                            Box(
                                Modifier.size(34.dp).clickable { passwordVisible = !passwordVisible },
                                contentAlignment = Alignment.Center,
                            ) {
                                AceIcon(if (passwordVisible) Lucide.EyeOff else Lucide.Eye, tint = colors.text3, size = 18.dp)
                            }
                        },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    )
                    if (store.loginTwoFaRequired) {
                        Spacer(Modifier.height(14.dp))
                        DesktopFieldLabel("两步验证码")
                        AceTextField(
                            value = store.loginPassCode,
                            onValueChange = { store.loginPassCode = it.filter { c -> c.isDigit() }.take(6) },
                            placeholder = "6 位动态验证码",
                            leading = { AceIcon(Lucide.ShieldCheck, tint = colors.text3, size = 18.dp) },
                        )
                    }
                    if (store.loginCaptchaRequired) {
                        Spacer(Modifier.height(14.dp))
                        DesktopFieldLabel("图形验证码")
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            AceTextField(
                                value = store.loginCaptchaCode,
                                onValueChange = { store.loginCaptchaCode = it },
                                placeholder = "请输入验证码",
                                modifier = Modifier.weight(1f),
                            )
                            CaptchaImage(store.loginCaptchaImage) { store.refreshCaptcha() }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { store.loginRemember = !store.loginRemember },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(9.dp),
                        ) {
                            AceCheckBox(store.loginRemember, { store.loginRemember = it })
                            AceText("记住登录状态", style = AceType.bodySmall, color = colors.text2)
                        }
                        AceText(
                            "忘记密码？",
                            style = AceType.bodySmall,
                            color = colors.accent,
                            modifier = Modifier.clickable { showInfo = true },
                        )
                    }
                    store.loginError?.let {
                        Spacer(Modifier.height(12.dp))
                        InlineNotice(it, color = colors.error, background = colors.errorSoft)
                    }
                    Spacer(Modifier.height(16.dp))
                    PrimaryPillButton(
                        text = "登 录",
                        onClick = {
                            attempted = true
                            if (addressLocal.isNotBlank() && usernameLocal.isNotBlank() && passwordLocal.isNotBlank()) {
                                store.login(addressLocal, usernameLocal, passwordLocal)
                            }
                        },
                        loading = store.loginLoading,
                        enabled = !store.loginLoading,
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Box(Modifier.weight(1f).height(1.dp).background(colors.divider))
                        AceText("或", style = AceType.caption, color = colors.text3)
                        Box(Modifier.weight(1f).height(1.dp).background(colors.divider))
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(colors.fill)
                            .clickable { mode = 1 },
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AceIcon(Lucide.Key, tint = colors.text1, size = 18.dp)
                        Spacer(Modifier.width(8.dp))
                        AceText("使用 SSH 密钥登录", style = AceType.bodySmall.copy(fontWeight = FontWeight.Medium), color = colors.text1)
                    }
                } else {
                    InlineNotice(
                        "使用服务器 IP + 密码或 SSH 私钥直连，无需预先安装面板；连接后可在终端中一键安装 AcePanel。",
                        color = colors.accent,
                        background = colors.accentSoft,
                    )
                    Spacer(Modifier.height(16.dp))
                    PrimaryPillButton("直连 SSH", { store.openDirectSshForm() })
                    Spacer(Modifier.height(10.dp))
                    SoftPillButton("返回密码登录", { mode = 0 }, height = 46.dp, background = colors.fill)
                }
                Spacer(Modifier.height(16.dp))
                AceText(
                    "登录即代表同意《用户协议》与《隐私政策》",
                    style = AceType.caption,
                    color = colors.text3,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showTerms = true },
                )
            }
        }
    }

    if (showInfo) {
        InfoDialog("找回面板密码", "AcePanel 的登录密码由服务器本地面板管理。请在服务器上执行 `acepanel user reset`，或联系面板管理员重置密码。") { showInfo = false }
    }
    if (showTerms) {
        InfoDialog("用户协议与隐私政策", "AcePanel Client 仅在你主动添加的面板与本机之间通信，登录凭据只保存在本机。客户端不会收集面板数据或上传到第三方服务。请妥善保管服务器登录信息。") { showTerms = false }
    }
}

@Composable
private fun DesktopFeature(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String) {
    val colors = AceTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AceIcon(icon, tint = colors.accent, size = 24.dp)
        AceText(title, style = AceType.bodySmall.copy(fontWeight = FontWeight.SemiBold), color = colors.text1)
        AceText(subtitle, style = AceType.caption, color = colors.text3)
    }
}

@Composable
private fun DesktopFieldLabel(text: String) {
    AceText(text, style = AceType.caption, color = AceTheme.colors.text3)
    Spacer(Modifier.height(7.dp))
}

@Composable
private fun FieldHint(text: String) {
    AceText(
        text,
        style = AceType.caption,
        color = AceTheme.colors.error,
        modifier = Modifier.padding(start = 18.dp),
    )
}

@OptIn(ExperimentalEncodingApi::class)
@Composable
private fun CaptchaImage(base64: String, onRefresh: () -> Unit) {
    val colors = AceTheme.colors
    val bitmap = remember(base64) {
        runSafely {
            if (base64.isBlank()) null else Base64.decode(base64).decodeToImageBitmap()
        }.getOrNull()
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        Box(
            modifier = Modifier
                .height(48.dp)
                .width(116.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White),
            contentAlignment = Alignment.Center,
        ) {
            if (bitmap != null) {
                Image(bitmap, contentDescription = "验证码", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            } else {
                AceText("点击刷新", style = AceType.caption, color = colors.text3)
            }
        }
        CircleAction(Lucide.RefreshCw, onRefresh, background = colors.fill, tint = colors.text2, size = 40.dp, iconSize = 18.dp)
    }
}

@Composable
private fun InfoDialog(title: String, body: String, onDismiss: () -> Unit) {
    AceDialog(title = title, onDismiss = onDismiss) {
        AceText(body, style = AceType.bodySmall.copy(lineHeight = 21.sp), color = AceTheme.colors.text2)
        Spacer(Modifier.height(18.dp))
        PrimaryPillButton("我知道了", onDismiss, height = 44.dp)
    }
}
