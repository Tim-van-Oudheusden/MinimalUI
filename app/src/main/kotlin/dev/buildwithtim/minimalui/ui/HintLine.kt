package dev.buildwithtim.minimalui.ui

import android.app.Activity
import android.view.KeyEvent
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.buildwithtim.minimalui.R
import dev.buildwithtim.minimalui.core.Hint
import dev.buildwithtim.minimalui.core.HintAction
import dev.buildwithtim.minimalui.core.HintKey
import dev.buildwithtim.minimalui.core.Hints
import dev.buildwithtim.minimalui.core.fitting

/** A hint's label: smaller than a row's, on the same line height, so a hint pill is as tall as the selection pill. */
private val LabelStyle = RowMetrics.TextStyle.copy(fontSize = 18.sp)

/** The key's glyph inside its circle. */
private val GlyphStyle = RowMetrics.TextStyle.copy(fontSize = 16.sp, lineHeight = 16.sp, textAlign = TextAlign.Center)

/**
 * The button-hint row (#74, MinUI's): one row, [height] tall, at the bottom of every screen. The [hints]' left group
 * sits in a dark-grey pill at the left edge and the right (main) group in one at the right edge; a screen narrower than
 * 320 dp keeps only the right group. In a pill, each key's glyph sits in a small white circle, followed by what it does
 * in white upper case. A tap on a hint presses the key it names (#110), so touch does exactly what the key does on that
 * screen; a hint never takes focus, so the selection stays where it was. With no hints, the row stays empty.
 */
@Composable
internal fun HintLine(hints: Hints, height: Dp, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxWidth().height(height)) {
        val shown = hints.fitting(maxWidth.value.toInt())
        Box(Modifier.fillMaxSize().padding(horizontal = RowMetrics.ListPaddingHorizontal)) {
            if (shown.left.isNotEmpty()) HintPill(shown.left, Modifier.align(Alignment.CenterStart))
            if (shown.right.isNotEmpty()) HintPill(shown.right, Modifier.align(Alignment.CenterEnd))
        }
    }
}

/** One group of hints in a dark-grey pill, as tall as the selection pill. */
@Composable
private fun HintPill(hints: List<Hint>, modifier: Modifier) {
    val line = with(LocalDensity.current) { RowMetrics.LineHeight.toDp() }
    // The circle sits as far from the pill's top, bottom and left edges as can be: MinUI's button in its pill.
    val circle = line - 4.dp
    val edge = RowMetrics.PillPaddingVertical + (line - circle) / 2
    val activity = LocalActivity.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RowMetrics.PillPaddingHorizontal),
        modifier = modifier
            .background(PillColor, RowMetrics.PillShape)
            .padding(start = edge, end = RowMetrics.PillPaddingHorizontal),
    ) {
        val colors = MaterialTheme.colorScheme
        val locale = LocalConfiguration.current.locales[0]
        for (hint in hints) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                // The pill's whole height is the touch target. No ripple, as on the rows.
                modifier = Modifier
                    .focusProperties { canFocus = false }
                    .clickable(interactionSource = null, indication = null) { activity?.press(hint.key) }
                    .padding(vertical = RowMetrics.PillPaddingVertical),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(circle).background(colors.onBackground, CircleShape),
                ) {
                    Text(text = hint.key.glyph, color = colors.background, style = GlyphStyle, maxLines = 1)
                }
                Text(
                    // Upper case is the hint row's look (MinUI's), not the word's: the string stays translatable.
                    text = stringResource(hint.action.label).uppercase(locale),
                    color = colors.onBackground,
                    style = LabelStyle,
                    maxLines = 1,
                )
            }
        }
    }
}

/** Sends [key] down and up through the activity, the path a hardware key takes. */
private fun Activity.press(key: HintKey) {
    dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, key.keyCode))
    dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, key.keyCode))
}

/** The Android key a hint names: `⎋` is Esc, which every screen with a Back hint takes as Back. */
private val HintKey.keyCode: Int
    get() = when (this) {
        HintKey.Menu -> KeyEvent.KEYCODE_MENU
        HintKey.Delete -> KeyEvent.KEYCODE_DEL
        HintKey.Back -> KeyEvent.KEYCODE_ESCAPE
        HintKey.Enter -> KeyEvent.KEYCODE_ENTER
    }

private val HintAction.label: Int
    get() = when (this) {
        HintAction.Menu -> R.string.hint_menu
        HintAction.Delete -> R.string.hint_delete
        HintAction.Back -> R.string.hint_back
        HintAction.Cancel -> R.string.hint_cancel
        HintAction.Open -> R.string.hint_open
        HintAction.Select -> R.string.hint_select
        HintAction.Change -> R.string.hint_change
        HintAction.Unhide -> R.string.hint_unhide
        HintAction.Save -> R.string.hint_save
    }
