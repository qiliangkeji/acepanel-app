package com.acepanel.client.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent

@OptIn(ExperimentalComposeUiApi::class)
@Composable
actual fun Modifier.chartHoverInput(onMove: (Float?) -> Unit): Modifier =
    this
        .onPointerEvent(PointerEventType.Move) { event ->
            onMove(event.changes.firstOrNull()?.position?.x)
        }
        .onPointerEvent(PointerEventType.Exit) { onMove(null) }
