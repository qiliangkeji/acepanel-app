package com.acepanel.client.platform

import androidx.compose.runtime.Composable

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) = Unit


@Composable
actual fun PlatformSystemBars(darkTheme: Boolean) = Unit
