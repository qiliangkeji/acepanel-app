package com.acepanel.client.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * 桌面端接收鼠标悬停，移动端接收按压/拖动位置。
 * 传 null 表示指针离开图表。
 */
@Composable
expect fun Modifier.chartHoverInput(onMove: (Float?) -> Unit): Modifier
