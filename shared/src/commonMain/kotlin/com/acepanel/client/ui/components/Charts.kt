package com.acepanel.client.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import com.acepanel.client.data.asPercentInt
import com.acepanel.client.ui.theme.AceTheme
import com.acepanel.client.ui.theme.AceType

@Composable
fun LineChart(
    values: List<Float>,
    modifier: Modifier = Modifier,
    color: Color = AceTheme.colors.accent,
    secondaryValues: List<Float>? = null,
    secondaryColor: Color = AceTheme.colors.text3,
    showGrid: Boolean = true,
    showEndDot: Boolean = true,
    height: Dp = 118.dp,
    hoverLabels: List<String>? = null,
    primaryLabel: String = "数值",
    secondaryLabel: String? = null,
    valueFormatter: (Float) -> String = { it.toString() },
    secondaryFormatter: (Float) -> String = valueFormatter,
) {
    val gridColor = AceTheme.colors.divider
    val fillTop = color.copy(alpha = 0.16f)
    val fillBottom = color.copy(alpha = 0.0f)
    var hoverIndex by remember(values.size, secondaryValues?.size) { mutableStateOf<Int?>(null) }
    var chartWidth by remember { mutableFloatStateOf(1f) }
    Box(modifier = modifier.fillMaxWidth().height(height)) {
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { chartWidth = it.width.toFloat().coerceAtLeast(1f) }
            .chartHoverInput { x ->
                if (x != null && values.isNotEmpty()) {
                    val fraction = (x / chartWidth).coerceIn(0f, 1f)
                    hoverIndex = (fraction * values.lastIndex).roundToInt().coerceIn(values.indices)
                } else {
                    hoverIndex = null
                }
            },
    ) {
        val w = size.width
        val h = size.height
        if (showGrid) {
            repeat(3) { i ->
                val y = h * (i + 1) / 4f
                drawLine(gridColor, Offset(0f, y), Offset(w, y), strokeWidth = 1f)
            }
        }
        fun smooth(list: List<Float>): List<Float> {
            if (list.size < 3) return list
            return list.mapIndexed { index, value ->
                when (index) {
                    0 -> value
                    list.lastIndex -> value
                    else -> (list[index - 1] + value + list[index + 1]) / 3f
                }
            }
        }
        val smoothedPrimary = smooth(values)
        val smoothedSecondary = secondaryValues?.let { smooth(it) }
        val globalMax = maxOf(
            smoothedPrimary.maxOrNull() ?: 0f,
            smoothedSecondary?.maxOrNull() ?: 0f,
        ).coerceAtLeast(0.0001f)
        fun points(list: List<Float>): List<Offset> {
            if (list.isEmpty()) return emptyList()
            val pad = 6f
            val step = if (list.size > 1) w / (list.size - 1) else w
            return list.mapIndexed { index, value ->
                val norm = (value / globalMax).coerceIn(0f, 1f)
                Offset(index * step, h - pad - norm * (h - pad * 2))
            }
        }

        fun smoothPath(pts: List<Offset>): Path {
            val path = Path()
            if (pts.isEmpty()) return path
            path.moveTo(pts.first().x, pts.first().y)
            if (pts.size == 1) return path
            for (i in 0 until pts.size - 1) {
                val current = pts[i]
                val next = pts[i + 1]
                val midX = (current.x + next.x) / 2f
                path.cubicTo(midX, current.y, midX, next.y, next.x, next.y)
            }
            return path
        }

        smoothedSecondary?.let { secondary ->
            val pts = points(secondary)
            if (pts.size > 1) {
                drawPath(
                    smoothPath(pts),
                    secondaryColor,
                    style = Stroke(width = 2.2f, cap = StrokeCap.Round),
                )
            }
        }

        val pts = points(smoothedPrimary)
        if (pts.size > 1) {
            val path = smoothPath(pts)
            val fillPath = Path().apply {
                addPath(path)
                lineTo(pts.last().x, h)
                lineTo(pts.first().x, h)
                close()
            }
            drawPath(
                fillPath,
                Brush.verticalGradient(listOf(fillTop, fillBottom)),
            )
            drawPath(
                path,
                color,
                style = Stroke(width = 2.4f, cap = StrokeCap.Round),
            )
        }
        if (showEndDot && pts.isNotEmpty()) {
            drawCircle(color, radius = 4.5f, center = pts.last())
            drawCircle(Color.White, radius = 2f, center = pts.last())
        }
        hoverIndex?.let { rawIndex ->
            if (pts.isNotEmpty()) {
                val index = rawIndex.coerceIn(pts.indices)
                val point = pts[index]
                drawLine(
                    color = color.copy(alpha = 0.35f),
                    start = Offset(point.x, 0f),
                    end = Offset(point.x, h),
                    strokeWidth = 1f,
                )
                drawCircle(Color.White, radius = 4f, center = point)
                drawCircle(color, radius = 2.5f, center = point)
                smoothedSecondary?.let { secondary ->
                    points(secondary).getOrNull(index)?.let { secondaryPoint ->
                        drawCircle(Color.White, radius = 3.5f, center = secondaryPoint)
                        drawCircle(secondaryColor, radius = 2.2f, center = secondaryPoint)
                    }
                }
            }
        }
    }
    hoverIndex?.let { rawIndex ->
        val index = rawIndex.coerceIn(values.indices)
        val labels = hoverLabels
        val labelIndex = if (labels.isNullOrEmpty()) null else {
            if (values.size <= 1) 0
            else (index.toFloat() / values.lastIndex * (labels.lastIndex.coerceAtLeast(0))).roundToInt()
                .coerceIn(labels.indices)
        }
        val primary = values.getOrNull(index)
        val secondary = secondaryValues?.getOrNull(index)
        val text = buildString {
            val hoverLabel = if (labelIndex != null) labels?.get(labelIndex) else null
            if (hoverLabel != null) append(hoverLabel).append("  ·  ")
            else append("第 ").append(index + 1).append(" 点  ·  ")
            if (primary != null) append(primaryLabel).append(" ").append(valueFormatter(primary))
            if (secondary != null && secondaryLabel != null) {
                append("  ·  ").append(secondaryLabel).append(" ").append(secondaryFormatter(secondary))
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 6.dp, start = 8.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(AceTheme.colors.surface.copy(alpha = 0.94f))
                .padding(horizontal = 9.dp, vertical = 5.dp),
        ) {
            AceText(text, style = AceType.caption.copy(fontSize = 10.5.sp), color = AceTheme.colors.text1)
        }
    }
    }
}

@Composable
fun DonutChart(
    percent: Double,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 104.dp,
    stroke: Dp = 9.dp,
    label: String? = null,
    labelColor: Color = AceTheme.colors.text1,
    caption: String? = null,
    captionColor: Color = AceTheme.colors.text3,
    trackColor: Color = AceTheme.colors.divider,
) {
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokePx = stroke.toPx()
            val inset = strokePx / 2
            val arcSize = Size(this.size.width - strokePx, this.size.height - strokePx)
            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = strokePx),
            )
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = (percent / 100.0 * 360.0).coerceIn(0.0, 360.0).toFloat(),
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round),
            )
        }
        androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.CenterHorizontally) {
            AceText(
                label ?: "${percent.asPercentInt()}%",
                style = AceType.number.copy(fontSize = 22.sp),
                color = labelColor,
            )
            if (caption != null) {
                AceText(caption, style = AceType.caption, color = captionColor)
            }
        }
    }
}

@Composable
fun NetworkBar(
    percent: Double,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(5.dp)
            .clip(CircleShape)
            .background(AceTheme.colors.divider),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth((percent / 100.0).coerceIn(0.0, 1.0).toFloat())
                .height(5.dp)
                .clip(CircleShape)
                .background(color),
        )
    }
}
