package dev.buildwithtim.minimalui.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.buildwithtim.minimalui.R
import dev.buildwithtim.minimalui.core.Battery
import dev.buildwithtim.minimalui.core.StatusPill

/** MinUI's dark-grey status pill (research §4) and the red the battery turns when low. */
private val PillColor = Color(0xFF262626)
private val LowColor = Color(0xFFFF5A4F)

/** Smaller than a row's label, on the same line height, so the pill is exactly as tall as the selection pill. */
private val StatusTextStyle = RowMetrics.TextStyle.copy(fontSize = 20.sp)

/**
 * The status line: one row, [height] tall, with the [pill] in its top-right corner (the system status bar is hidden
 * on the launcher). The pill holds a Wi-Fi glyph while connected, the battery glyph and percentage, and the clock;
 * it reads as one item to accessibility services.
 */
@Composable
fun StatusLine(pill: StatusPill, height: Dp, modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.CenterEnd,
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .padding(horizontal = RowMetrics.ListPaddingHorizontal),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .semantics(mergeDescendants = true) {}
                .background(PillColor, RowMetrics.PillShape)
                .padding(horizontal = RowMetrics.PillPaddingHorizontal, vertical = RowMetrics.PillPaddingVertical),
        ) {
            val color = MaterialTheme.colorScheme.onBackground
            if (pill.wifi) WifiGlyph(color)
            pill.battery?.let { battery ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    BatteryGlyph(battery, color)
                    Text(text = battery.text, color = color, style = StatusTextStyle, maxLines = 1)
                }
            }
            Text(text = pill.clock, color = color, style = StatusTextStyle, maxLines = 1)
        }
    }
}

/** Three arcs fanning up from a dot. */
@Composable
private fun WifiGlyph(color: Color) {
    val description = stringResource(R.string.status_wifi)
    Canvas(Modifier.size(20.dp, 16.dp).semantics { contentDescription = description }) {
        val stroke = 2.dp.toPx()
        val dot = 1.75.dp.toPx()
        val centre = Offset(size.width / 2, size.height - dot)
        drawCircle(color, dot, centre)
        val outer = centre.y - stroke / 2
        for (radius in listOf(outer * 0.4f, outer * 0.7f, outer)) {
            drawArc(
                color = color,
                startAngle = 225f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = centre - Offset(radius, radius),
                size = Size(radius * 2, radius * 2),
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
    }
}

/**
 * A battery outline with a terminal on the right. The body fills in proportion to the charge, or holds a bolt while
 * charging; the fill turns red at 20% and the outline too at 10% (MinUI's thresholds, decided in core).
 */
@Composable
private fun BatteryGlyph(battery: Battery, color: Color) {
    val description = if (battery.bolt) stringResource(R.string.status_charging) else null
    Canvas(Modifier.size(26.dp, 14.dp).semantics { if (description != null) contentDescription = description }) {
        val stroke = 1.5.dp.toPx()
        val terminal = 2.5.dp.toPx()
        val body = Size(size.width - terminal, size.height)
        val outline = if (battery.lowOutline) LowColor else color
        drawRoundRect(
            color = outline,
            topLeft = Offset(stroke / 2, stroke / 2),
            size = Size(body.width - stroke, body.height - stroke),
            cornerRadius = CornerRadius(3.dp.toPx()),
            style = Stroke(stroke),
        )
        drawRoundRect(
            color = outline,
            topLeft = Offset(body.width, size.height * 0.3f),
            size = Size(terminal, size.height * 0.4f),
            cornerRadius = CornerRadius(terminal / 2),
        )
        val inset = stroke + 1.5.dp.toPx()
        val inner = Size(body.width - inset * 2, body.height - inset * 2)
        if (battery.bolt) {
            val bolt = Path().apply {
                fun point(x: Float, y: Float) = Offset(inset + inner.width * x, inset + inner.height * y)
                point(0.62f, -0.1f).let { moveTo(it.x, it.y) }
                listOf(0.28f to 0.58f, 0.5f to 0.58f, 0.4f to 1.1f, 0.74f to 0.4f, 0.52f to 0.4f)
                    .map { (x, y) -> point(x, y) }
                    .forEach { lineTo(it.x, it.y) }
                close()
            }
            drawPath(bolt, color)
        } else if (battery.fill > 0f) {
            drawRoundRect(
                color = if (battery.lowFill) LowColor else color,
                topLeft = Offset(inset, inset),
                size = Size(inner.width * battery.fill, inner.height),
                cornerRadius = CornerRadius(1.5.dp.toPx()),
            )
        }
    }
}
