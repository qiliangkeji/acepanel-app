package com.acepanel.client

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.acepanel.client.data.AceStore
import com.acepanel.client.data.ThemeMode
import com.acepanel.client.mcp.McpBridgeHost
import com.acepanel.client.platform.PlatformSystemBars
import com.acepanel.client.ui.nav.AceApp
import com.acepanel.client.ui.theme.AceTheme

@Composable
fun App() {
    val store = remember {
        AceStore().also { McpBridgeHost.store = it }
    }
    val systemDark = isSystemInDarkTheme()
    val dark = when (store.themeMode) {
        ThemeMode.System -> systemDark
        ThemeMode.Dark -> true
        ThemeMode.Light -> false
    }
    PlatformSystemBars(darkTheme = dark)
    AceTheme(darkTheme = dark) {
        AceApp(store)
    }
}
