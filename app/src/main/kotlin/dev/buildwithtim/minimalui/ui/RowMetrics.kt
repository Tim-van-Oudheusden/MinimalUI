package dev.buildwithtim.minimalui.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Size of every list row, in one place so rows per screen can be computed from it. Rows are contiguous: a row is
 * exactly [rowHeight] tall, the label's line plus the pill's vertical padding, with no gap between rows.
 */
object RowMetrics {
    /** Label size, Cannoli's default; the system font, per BRANDING.md. */
    val TextSize = 24.sp

    /** One label line. Fixed rather than font-derived, so every row has the same height. */
    val LineHeight = 32.sp

    /** Space between the selection pill's edge and its label. */
    val PillPaddingHorizontal = 14.dp
    val PillPaddingVertical = 6.dp

    /** Space between the screen edges and a row's pill. */
    val ListPaddingHorizontal = 8.dp

    /** Fully rounded ends, as MinUI and Cannoli draw the selection. */
    val PillShape = RoundedCornerShape(percent = 50)

    val TextStyle =
        TextStyle(
            fontSize = TextSize,
            lineHeight = LineHeight,
            // Keep the whole line height, so the drawn row matches rowHeight() exactly.
            lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
        )

    /** Height of one row, which follows the user's font scale through [LineHeight]. */
    @Composable
    @ReadOnlyComposable
    fun rowHeight(): Dp = with(LocalDensity.current) { LineHeight.toDp() } + PillPaddingVertical * 2
}
