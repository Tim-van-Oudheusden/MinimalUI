package dev.buildwithtim.minimalui.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldDecorator
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import dev.buildwithtim.minimalui.core.HintedScreen
import dev.buildwithtim.minimalui.core.StatusPill

/**
 * The one text-entry screen (#72), for a new name: Rename's and a new collection's. Under the [title] pill, the text
 * sits on a line of its own, starting as [initial] with the cursor at its end, and focused as the screen opens, so a
 * hardware keyboard types into it at once; a tap on it brings up the on-screen keyboard. Enter, NumPadEnter, D-pad
 * centre or the on-screen keyboard's Done action calls [onDone] with the text; Esc or Back calls [onClose].
 */
@Composable
internal fun TextEntry(
    title: String,
    initial: String,
    onDone: (String) -> Unit,
    onClose: () -> Unit,
    status: StatusPill? = null,
) {
    val requester = remember { FocusRequester() }
    // The field's own state takes each key as it comes, so fast typing (or `adb shell input text`) loses none.
    val text = rememberTextFieldState(initial, TextRange(initial.length))
    val colors = MaterialTheme.colorScheme

    TitledList(title, status, HintedScreen.Editor, onClose) { rowHeight, _ ->
        BasicTextField(
            state = text,
            lineLimits = TextFieldLineLimits.SingleLine,
            textStyle = RowMetrics.TextStyle.copy(color = colors.onBackground),
            cursorBrush = SolidColor(colors.onBackground),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            onKeyboardAction = { onDone(text.text.toString()) },
            // Aligned with the title's text above it and the rows' labels on other screens.
            modifier = Modifier
                .fillMaxWidth()
                .height(rowHeight)
                .padding(horizontal = RowMetrics.ListPaddingHorizontal + RowMetrics.PillPaddingHorizontal)
                .focusRequester(requester)
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                            if (event.nativeKeyEvent.repeatCount == 0) onDone(text.text.toString())
                            true
                        }
                        else -> closesOnEscape(event.key, onClose)
                    }
                },
            decorator = TextFieldDecorator { field ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) { field() }
            },
        )
        LaunchedEffect(Unit) { requester.requestFocus() }
    }
}
