package com.acepanel.client.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput

@Composable
actual fun Modifier.chartHoverInput(onMove: (Float?) -> Unit): Modifier {
    val current by rememberUpdatedState(onMove)
    return this.pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                if (event.type == PointerEventType.Exit) {
                    current(null)
                } else {
                    current(event.changes.firstOrNull()?.position?.x)
                }
            }
        }
    }
}
