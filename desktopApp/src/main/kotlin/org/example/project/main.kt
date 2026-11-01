package org.example.project

import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.acepanel.client.App
import java.awt.GraphicsEnvironment
import kotlin.math.min

fun main() = application {
    val config = GraphicsEnvironment.getLocalGraphicsEnvironment()
        .defaultScreenDevice
        .defaultConfiguration
    val scale = config.defaultTransform.scaleX.takeIf { it > 0.0 } ?: 1.0
    val screenWidthDp = (config.bounds.width / scale).toFloat()
    val screenHeightDp = (config.bounds.height / scale).toFloat()
    val forced = System.getProperty("acepanel.size")?.split("x")?.takeIf { it.size == 2 }
    val initialSize = if (forced != null) {
        val w = forced[0].toFloatOrNull() ?: 390f
        val h = forced[1].toFloatOrNull() ?: 844f
        DpSize(w.dp, h.dp)
    } else {
        DpSize(
            min(1400f, screenWidthDp - 40f).coerceAtLeast(980f).dp,
            min(860f, screenHeightDp - 72f).coerceAtLeast(620f).dp,
        )
    }
    val state = rememberWindowState(
        size = initialSize,
        position = WindowPosition(Alignment.Center),
    )
    Window(
        onCloseRequest = ::exitApplication,
        title = "AcePanel Client",
        state = state,
    ) {
        window.minimumSize = java.awt.Dimension(320, 480)
        App()
    }
}
