package com.acepanel.client.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.acepanel.client.ui.theme.AceTheme

@Composable
fun AceLogoMark(
    size: Dp = 40.dp,
    modifier: Modifier = Modifier,
    color: Color = AceTheme.colors.accent,
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val barH = h * 0.18f
        val gap = h * 0.12f
        val radius = CornerRadius(barH / 2f, barH / 2f)
        repeat(3) { i ->
            val top = h * 0.16f + i * (barH + gap)
            drawRoundRect(
                color = color,
                topLeft = Offset(w * 0.08f, top),
                size = Size(w * 0.84f, barH),
                cornerRadius = radius,
            )
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(w * 0.19f, top + barH * 0.18f),
                size = Size(w * 0.62f, barH * 0.64f),
                cornerRadius = CornerRadius(barH * 0.32f, barH * 0.32f),
            )
        }
    }
}

@Composable
fun AceLogoWordmark(
    modifier: Modifier = Modifier,
    iconSize: Dp = 32.dp,
    textColor: Color = AceTheme.colors.text1,
) {
    androidx.compose.foundation.layout.Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AceLogoMark(size = iconSize)
        androidx.compose.foundation.layout.Spacer(Modifier.size(10.dp))
        AceText("AcePanel", style = com.acepanel.client.ui.theme.AceType.section.copy(fontSize = 20.sp), color = textColor)
    }
}
