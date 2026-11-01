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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.acepanel.client.data.AceStore
import com.acepanel.client.data.PageKey
import com.acepanel.client.data.RootTab
import com.acepanel.client.data.ThemeMode
import com.acepanel.client.ui.components.AceCard
import com.acepanel.client.ui.components.AceDialog
import com.acepanel.client.ui.components.AceIcon
import com.acepanel.client.ui.components.AceSwitch
import com.acepanel.client.ui.components.AceText
import com.acepanel.client.ui.components.CircleAction
import com.acepanel.client.ui.components.RowChevron
import com.acepanel.client.ui.components.SoftPillButton
import com.acepanel.client.ui.components.StatusPill
import com.acepanel.client.ui.components.rememberScreenScrollState
import com.acepanel.client.ui.theme.AceTheme
import com.acepanel.client.ui.theme.AceType
import com.composables.icons.lucide.Bell
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Globe
import com.composables.icons.lucide.Inbox
import com.composables.icons.lucide.Key
import com.composables.icons.lucide.LayoutGrid
import com.composables.icons.lucide.LogOut
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Moon
import com.composables.icons.lucide.RotateCw
import com.composables.icons.lucide.Server
import com.composables.icons.lucide.Settings
import com.composables.icons.lucide.ShieldCheck
import com.composables.icons.lucide.Wifi
import com.composables.icons.lucide.Workflow
import com.composables.icons.lucide.UserRound

@Composable
fun ProfileScreen(store: AceStore) {
    val colors = AceTheme.colors
    val user = store.user
    val profile = store.activeProfile
    var showLogout by remember { mutableStateOf(false) }
    val username = user?.username ?: profile?.username ?: "未登录"
    val initial = username.take(2).uppercase()
    val isAdmin = user?.role?.contains("admin") == true
    val keyCount = store.sshHosts.count { it.config.authMethod == "publickey" }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg)
            .windowInsetsPadding(WindowInsets.statusBars)
            .verticalScroll(rememberScreenScrollState()),
    ) {
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 26.dp, end = 26.dp, top = 4.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AceText("我的", style = AceType.pageTitle, color = colors.text1, modifier = Modifier.weight(1f))
            CircleAction(Lucide.Settings, { store.openPage(PageKey.SETTINGS) }, contentDescription = "设置")
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 26.dp, end = 26.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AceCard(padding = 18.dp) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(
                        modifier = Modifier
                            .size(58.dp)
                            .clip(CircleShape)
                            .background(colors.accentSoft),
                        contentAlignment = Alignment.Center,
                    ) {
                        AceText(initial.ifBlank { "AP" }, style = AceType.section.copy(fontSize = 19.sp), color = colors.accent)
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AceText(
                                username,
                                style = AceType.listStrong.copy(fontSize = 17.sp),
                                color = colors.text1,
                                maxLines = 1,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            StatusPill(
                                if (isAdmin) "超级管理员" else "面板用户",
                                colors.accent,
                                colors.accentSoft,
                                dot = false,
                            )
                        }
                        AceText(
                            user?.email?.takeIf { it.isNotBlank() } ?: profile?.baseUrl?.removePrefix("https://")?.removePrefix("http://") ?: "—",
                            style = AceType.secondary,
                            color = colors.text3,
                            maxLines = 1,
                        )
                    }
                    RowChevron()
                }
            }

            SectionLabel("面板")
            AceCard {
                ProfileRow(Lucide.Globe, "面板地址", profile?.baseUrl?.removePrefix("https://")?.removePrefix("http://") ?: "未连接") {
                    store.openPage(PageKey.SETTINGS)
                }
                RowDivider()
                ProfileRow(Lucide.Server, "面板版本", "v${store.systemInfo?.panelVersion ?: profile?.panelVersion ?: "-"}") {
                    store.openPage(PageKey.SETTINGS)
                }
                RowDivider()
                ProfileRow(Lucide.ShieldCheck, "账户与安全", null) { store.openPage(PageKey.SETTINGS) }
                RowDivider()
                ProfileRow(Lucide.LayoutGrid, "全部功能", "应用 · 备份 · 证书 · 工具", accent = true) {
                    store.openPage(PageKey.FEATURE_CENTER)
                }
            }

            SectionLabel("SSH 连接")
            AceCard {
                ProfileRow(Lucide.Key, "密钥管理", "$keyCount 个密钥") { store.selectedTab = RootTab.SERVERS }
                RowDivider()
                ProfileRow(Lucide.Server, "已添加主机", "${store.sshHosts.size} 条记录") { store.selectedTab = RootTab.SERVERS }
                RowDivider()
                ProfileRow(Lucide.Workflow, "端口转发", "面板侧管理") { store.openPage(PageKey.SECURITY) }
            }

            SectionLabel("偏好设置")
            AceCard {
                PreferenceRow(Lucide.Bell, "消息通知") {
                    AceSwitch(store.notificationsEnabled) { store.setNotifications(it) }
                }
                RowDivider()
                PreferenceRow(Lucide.Wifi, "断线自动重连") {
                    AceSwitch(store.autoReconnect) { store.updateAutoReconnect(it) }
                }
                RowDivider()
                PreferenceRow(Lucide.Moon, "深色模式") {
                    AceSwitch(colors.isDark) {
                        store.setTheme(if (it) ThemeMode.Dark else ThemeMode.Light)
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(999.dp))
                    .background(colors.surface)
                    .clickable { showLogout = true },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AceIcon(Lucide.LogOut, tint = colors.error, size = 18.dp)
                Spacer(Modifier.size(8.dp))
                AceText("退出登录", style = AceType.button.copy(fontWeight = FontWeight.Medium), color = colors.error)
            }
            AceText(
                "AcePanel Client 1.0.0 · build 2026.11.01",
                style = AceType.caption,
                color = colors.text3,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
        }
    }

    if (showLogout) {
        AceDialog(title = "退出登录", onDismiss = { showLogout = false }) {
            AceText(
                "退出后将清除本机的面板会话，需要重新输入账号密码。服务器上的数据不会受到影响。",
                style = AceType.bodySmall,
                color = colors.text2,
            )
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SoftPillButton("取消", { showLogout = false }, modifier = Modifier.weight(1f), height = 44.dp, background = colors.fill)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(999.dp))
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
}

@Composable
private fun SectionLabel(text: String) {
    AceText(text, style = AceType.caption, color = AceTheme.colors.text3, modifier = Modifier.padding(start = 4.dp, top = 2.dp))
}

@Composable
private fun ProfileRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    value: String?,
    accent: Boolean = false,
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
        AceIcon(icon, tint = if (accent) colors.accent else colors.text1, size = 20.dp)
        AceText(
            title,
            style = AceType.listPrimary,
            color = if (accent) colors.accent else colors.text1,
            modifier = Modifier.weight(1f),
        )
        if (value != null) {
            // Row 里“不带 weight 的子项先测量”，值不加宽上限时（面板地址是整条 URL）
            // 会把左边 weight(1f) 的标题挤到折行，“全部功能”变成“全部功/能”。
            AceText(
                value,
                style = AceType.secondary,
                color = colors.text3,
                maxLines = 1,
                modifier = Modifier.widthIn(max = 126.dp),
            )
        }
        RowChevron()
    }
}

@Composable
private fun PreferenceRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    trailing: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        AceIcon(icon, tint = AceTheme.colors.text1, size = 20.dp)
        AceText(title, style = AceType.listPrimary, color = AceTheme.colors.text1, modifier = Modifier.weight(1f))
        trailing()
    }
}

@Composable
private fun RowDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = 51.dp)
            .height(1.dp)
            .background(AceTheme.colors.divider),
    )
}
