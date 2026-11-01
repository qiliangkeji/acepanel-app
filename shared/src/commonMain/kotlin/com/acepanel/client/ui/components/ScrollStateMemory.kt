package com.acepanel.client.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.currentCompositeKeyHash
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow

/**
 * 记住每个页面根滚动容器的位置。
 *
 * 页面/底部 Tab 在切换时会被移出组合，普通 rememberScrollState 会归零。
 * 这里用稳定的组合键把偏移量存到进程内缓存，返回页面时恢复到离开时的位置。
 */
private object ScreenScrollMemory {
    val vertical = mutableMapOf<Int, Int>()
    val lazyLists = mutableMapOf<Int, Pair<Int, Int>>()
}

@Composable
fun rememberScreenScrollState(): ScrollState {
    val key = currentCompositeKeyHash
    val state = remember(key) { ScrollState(ScreenScrollMemory.vertical[key] ?: 0) }
    LaunchedEffect(key, state) {
        snapshotFlow { state.value }.collect { ScreenScrollMemory.vertical[key] = it }
    }
    return state
}

@Composable
fun rememberScreenLazyListState(): LazyListState {
    val key = currentCompositeKeyHash
    val saved = ScreenScrollMemory.lazyLists[key]
    val state = remember(key) {
        LazyListState(
            firstVisibleItemIndex = saved?.first ?: 0,
            firstVisibleItemScrollOffset = saved?.second ?: 0,
        )
    }
    LaunchedEffect(key, state) {
        snapshotFlow { state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset }
            .collect { ScreenScrollMemory.lazyLists[key] = it }
    }
    return state
}
