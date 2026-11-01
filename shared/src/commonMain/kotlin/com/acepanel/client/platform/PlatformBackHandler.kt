package com.acepanel.client.platform

import androidx.compose.runtime.Composable

@Composable
expect fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit)


@Composable
expect fun PlatformSystemBars(darkTheme: Boolean)
