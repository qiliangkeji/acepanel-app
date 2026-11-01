package com.acepanel.client.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.sp
import com.acepanel.client.ui.theme.AceTheme
import com.acepanel.client.ui.theme.AceType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.min

/**
 * 手机端全局下拉刷新容器。
 * 触发后通过 store.refreshTick 重建当前页面，页面内已有的 LaunchedEffect 会重新加载数据。
 */
@Composable
fun PullRefreshBox(
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = AceTheme.colors
    val density = LocalDensity.current
    val thresholdPx = with(density) { 68.dp.toPx() }
    val currentRefresh by rememberUpdatedState(onRefresh)
    val scope = rememberCoroutineScope()
    var pull by remember { mutableFloatStateOf(0f) }
    var refreshing by remember { mutableStateOf(false) }

    val connection = remember(enabled, thresholdPx, scope) {
        object : NestedScrollConnection {
            var triggered = false

            override fun onPreScroll(available: Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): Offset {
                if (!enabled || refreshing) return Offset.Zero
                if (available.y < 0f && pull > 0f) {
                    val consumed = min(pull, -available.y)
                    pull -= consumed
                    if (pull <= 0f) triggered = false
                    return Offset(0f, -consumed)
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: androidx.compose.ui.input.nestedscroll.NestedScrollSource,
            ): Offset {
                if (!enabled || refreshing) return Offset.Zero
                if (available.y > 0f) {
                    pull = (pull + available.y * 0.45f).coerceAtMost(thresholdPx * 1.4f)
                    if (!triggered && pull >= thresholdPx) {
                        triggered = true
                        refreshing = true
                        currentRefresh()
                        scope.launch {
                            delay(750)
                            refreshing = false
                            pull = 0f
                            triggered = false
                        }
                    }
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }
        }
    }

    Box(modifier = modifier.nestedScroll(connection)) {
        content()
        AnimatedVisibility(
            visible = enabled && (refreshing || pull > with(density) { 8.dp.toPx() }),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.statusBars)
                .zIndex(4f)
                .offset(y = 8.dp),
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut(),
        ) {
            Row(
                modifier = Modifier
                    .background(colors.surface, CircleShape)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CircularDots(color = colors.accent)
                AceText(
                    if (refreshing) "正在刷新…" else "下拉刷新",
                    style = AceType.caption.copy(fontSize = 11.sp),
                    color = colors.text2,
                )
            }
        }
    }
}
