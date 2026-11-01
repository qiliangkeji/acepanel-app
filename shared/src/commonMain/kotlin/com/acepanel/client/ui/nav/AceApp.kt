package com.acepanel.client.ui.nav

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.acepanel.client.data.AceStore
import com.acepanel.client.data.AuthState
import com.acepanel.client.data.PageKey
import com.acepanel.client.data.RootTab
import com.acepanel.client.platform.PlatformBackHandler
import com.acepanel.client.platform.debugForceMobile
import com.acepanel.client.platform.debugStartRoute
import com.acepanel.client.platform.readDebugRouteFile
import com.acepanel.client.ui.components.AceLogoMark
import com.acepanel.client.ui.components.AceText
import com.acepanel.client.ui.components.MobileBottomNav
import com.acepanel.client.ui.components.PullRefreshBox
import com.acepanel.client.ui.components.ToastHost
import com.acepanel.client.ui.screens.AddServerScreen
import com.acepanel.client.ui.screens.AppStoreScreen
import com.acepanel.client.ui.screens.BackupsScreen
import com.acepanel.client.ui.screens.CertificatesScreen
import com.acepanel.client.ui.screens.ContainersScreen
import com.acepanel.client.ui.screens.CronScreen
import com.acepanel.client.ui.screens.DataCenterScreen
import com.acepanel.client.ui.screens.DirectSshScreen
import com.acepanel.client.ui.screens.DatabasesScreen
import com.acepanel.client.ui.screens.EnvironmentsScreen
import com.acepanel.client.ui.screens.FilesScreen
import com.acepanel.client.ui.screens.LogsScreen
import com.acepanel.client.ui.screens.ProjectsScreen
import com.acepanel.client.ui.screens.SecurityScreen
import com.acepanel.client.ui.screens.ToolboxScreen
import com.acepanel.client.ui.screens.WebsitesScreen
import com.acepanel.client.ui.screens.AccountCenterScreen
import com.acepanel.client.ui.screens.AlertsScreen
import com.acepanel.client.ui.screens.ApiCenterScreen
import com.acepanel.client.ui.screens.FeatureCenterScreen
import com.acepanel.client.ui.screens.LoginScreen
import com.acepanel.client.ui.screens.MonitorScreen
import com.acepanel.client.ui.screens.NotifyCenterScreen
import com.acepanel.client.ui.screens.OpsCenterScreen
import com.acepanel.client.ui.screens.SshTerminalScreen
import com.acepanel.client.ui.screens.TerminalTabScreen
import com.acepanel.client.ui.screens.OverviewScreen
import com.acepanel.client.ui.screens.ProfileScreen
import com.acepanel.client.ui.screens.ServersScreen
import com.acepanel.client.ui.screens.SettingsScreen
import com.acepanel.client.ui.theme.AceTheme
import com.acepanel.client.ui.theme.AceType

@Composable
fun AceApp(store: AceStore) {
    // 调试直达：未登录时也要能跳到 SSH 直连 / 添加服务器页，否则截图走查只能停在登录页。
    LaunchedEffect(Unit) {
        debugStartRoute()?.let(store::applyDebugRoute)
    }
    LaunchedEffect(Unit) {
        var last: String? = null
        while (true) {
            val route = readDebugRouteFile()
            if (route != null && route != last) {
                last = route
                store.applyDebugRoute(route)
            }
            kotlinx.coroutines.delay(600)
        }
    }
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().background(AceTheme.colors.bg),
    ) {
        val desktop = maxWidth >= 1024.dp && !debugForceMobile()
        val backEnabled = store.sshTerminal != null || store.showDirectSshForm ||
            store.showAddServer || store.pageStack.isNotEmpty() || store.selectedTab != RootTab.OVERVIEW ||
            store.pageBackHandler != null
        PlatformBackHandler(enabled = backEnabled) {
            // 页面自己还有内部层级（比如文件系统的目录）时，先交给页面消费，退无可退才出栈。
            if (store.pageBackHandler?.invoke() == true) return@PlatformBackHandler
            when {
                store.sshTerminal != null -> store.closeSshTerminal()
                store.showDirectSshForm -> store.closeDirectSshForm()
                store.showAddServer -> store.closeAddServer()
                store.pageStack.isNotEmpty() -> store.closePage()
                store.selectedTab != RootTab.OVERVIEW -> store.selectedTab = RootTab.OVERVIEW
            }
        }
        when {
            store.sshTerminal != null -> SshTerminalScreen(store, store.sshTerminal!!)
            store.showDirectSshForm -> DirectSshScreen(store)
            else -> Crossfade(targetState = store.authState, animationSpec = tween(260), label = "auth-state") { state ->
                when (state) {
                    AuthState.Booting -> SplashScreen(store.bootMessage)
                    AuthState.Login -> LoginScreen(store, desktop)
                    AuthState.Ready -> {
                        if (desktop) {
                            DesktopShell(store)
                        } else {
                            MobileShell(store)
                        }
                    }
                }
            }
        }
        ToastHost(store.toast) { store.clearToast() }
    }
}

@Composable
private fun SplashScreen(message: String) {
    val colors = AceTheme.colors
    val transition = rememberInfiniteTransition()
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
    )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg)
            .windowInsetsPadding(WindowInsets.statusBars),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        AceLogoMark(size = 64.dp)
        Spacer(Modifier.height(18.dp))
        AceText("AcePanel", style = AceType.loginTitle.copy(fontSize = 26.sp), color = colors.text1)
        Spacer(Modifier.height(8.dp))
        AceText("把面板和服务器，装进同一个客户端", style = AceType.secondary, color = colors.text3)
        Spacer(Modifier.height(36.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(3) { index ->
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .alpha(if (index == 1) alpha else 0.35f)
                        .background(colors.accent),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        AceText(message, style = AceType.caption, color = colors.text3, textAlign = TextAlign.Center)
    }
}

@Composable
private fun MobileShell(store: AceStore) {
    when {
        store.sshTerminal != null -> SshTerminalScreen(store, store.sshTerminal!!)
        store.showAddServer -> AddServerScreen(store)
        else -> {
            Column(Modifier.fillMaxSize().background(AceTheme.colors.bg)) {
                Box(Modifier.weight(1f)) {
                    PullRefreshBox(
                        onRefresh = store::requestGlobalRefresh,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        val destination = store.selectedTab to store.pageStack.lastOrNull()
                        AnimatedContent(
                            targetState = destination,
                            transitionSpec = {
                                val openingPage = targetState.second != null && initialState.second == null
                                (fadeIn(tween(200)) + slideInHorizontally { if (openingPage) it / 8 else 0 })
                                    .togetherWith(fadeOut(tween(130)) + slideOutHorizontally { if (openingPage) -it / 16 else 0 })
                            },
                            label = "mobile-screen",
                        ) { (tab, page) ->
                            key(store.refreshTick, page) {
                                if (page != null) {
                                    PageScreen(store, page) { store.closePage() }
                                } else {
                                    when (tab) {
                                        RootTab.OVERVIEW -> OverviewScreen(store)
                                        RootTab.SERVERS -> ServersScreen(store)
                                        RootTab.TERMINAL -> TerminalTabScreen(store)
                                        RootTab.MONITOR -> MonitorScreen(store)
                                        RootTab.PROFILE -> ProfileScreen(store)
                                    }
                                }
                            }
                        }
                    }
                }
                if (store.pageStack.isEmpty()) {
                    MobileBottomNav(store.selectedTab) { store.selectedTab = it }
                }
            }
        }
    }
}

@Composable
private fun PlaceholderTab(title: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(26.dp),
    ) {
        AceText(title, style = AceType.pageTitle, color = AceTheme.colors.text1)
        Spacer(Modifier.height(16.dp))
        AceText("正在加载…", style = AceType.body, color = AceTheme.colors.text3)
    }
}

@Composable
private fun PageScreen(store: AceStore, key: PageKey, onBack: () -> Unit) {
    when (key) {
        PageKey.FEATURE_CENTER -> FeatureCenterScreen(store)
        PageKey.ALERTS -> AlertsScreen(store)
        PageKey.SETTINGS -> SettingsScreen(store)
        PageKey.APPS -> AppStoreScreen(store)
        PageKey.WEBSITES -> WebsitesScreen(store)
        PageKey.DATABASES -> DatabasesScreen(store)
        PageKey.FILES -> FilesScreen(store)
        PageKey.CONTAINERS -> ContainersScreen(store)
        PageKey.SECURITY -> SecurityScreen(store)
        PageKey.CRON -> CronScreen(store)
        PageKey.BACKUPS -> BackupsScreen(store)
        PageKey.CERTS -> CertificatesScreen(store)
        PageKey.ENVIRONMENTS -> EnvironmentsScreen(store)
        PageKey.LOGS -> LogsScreen(store)
        PageKey.PROJECTS -> ProjectsScreen(store)
        PageKey.TOOLBOX -> ToolboxScreen(store)
        PageKey.API_CENTER -> ApiCenterScreen(store)
        PageKey.ACCOUNT_CENTER -> AccountCenterScreen(store)
        PageKey.NOTIFY_CENTER -> NotifyCenterScreen(store)
        PageKey.DATA_CENTER -> DataCenterScreen(store)
        PageKey.OPS_CENTER -> OpsCenterScreen(store)
    }
}

@Composable
private fun PlaceholderPage(key: PageKey, onBack: () -> Unit) {
    PlaceholderScreen(title = key.name, onBack = onBack)
}

@Composable
private fun PlaceholderScreen(title: String, onBack: (() -> Unit)?) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AceTheme.colors.bg)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(26.dp),
    ) {
        if (onBack != null) {
            Text(
                "← 返回",
                modifier = Modifier.padding(bottom = 16.dp),
                color = AceTheme.colors.accent,
                fontSize = 15.sp,
            )
        }
        AceText(title, style = AceType.pageTitle, color = AceTheme.colors.text1)
        Spacer(Modifier.height(16.dp))
        AceText("页面建设中", style = AceType.body, color = AceTheme.colors.text3)
    }
}
