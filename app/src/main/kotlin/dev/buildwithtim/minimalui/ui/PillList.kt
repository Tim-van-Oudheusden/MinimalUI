package dev.buildwithtim.minimalui.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import dev.buildwithtim.minimalui.core.ListPosition
import dev.buildwithtim.minimalui.core.StatusPill
import dev.buildwithtim.minimalui.core.listRhythm
import dev.buildwithtim.minimalui.core.paged
import dev.buildwithtim.minimalui.core.selecting
import dev.buildwithtim.minimalui.core.stepped

/** Grey for a row that is listed but cannot be chosen. */
internal val DimText = Color(0xFF808080)

/**
 * Where a [PillList] is: the selected row, the row at the top of the screen, and the scroll. Held by the caller, so a
 * list that leaves the screen for another one comes back as it was.
 */
@Stable
class PillListState {
    internal val scroll = LazyListState()

    internal var position by mutableStateOf(ListPosition(0, 0))

    // Bumped on every move, even to the same position, so the list scrolls and the selected row takes focus each time.
    internal var moves by mutableIntStateOf(0)
        private set

    internal fun select(next: ListPosition) {
        position = next
        moves++
    }
}

/**
 * The list every screen uses: one row per item, paged a whole screenful of [rowsPerPage] rows at a time, the selected
 * row's label in an inverted pill that hugs it (see BRANDING.md) and holds focus; a row's [value], when it has one,
 * sits at its right end. Up/Down move one row and wrap around at the ends, except while held. Left/Right turn a page,
 * stopping at the ends. Enter, D-pad centre or a tap confirms a row; a disabled row is drawn dim and confirms nothing.
 * Holding Enter or D-pad centre until it repeats, or a touch long-press, calls [onLongPress] instead, and the release
 * that ends it confirms nothing. [onKey] sees every other key press first, arrows included, with the selected item; a
 * key it takes does nothing else. Touch scrolling snaps to whole rows.
 */
@Composable
internal fun <T> PillList(
    state: PillListState,
    items: List<T>,
    key: (T) -> Any,
    label: (T) -> String,
    rowHeight: Dp,
    rowsPerPage: Int,
    onConfirm: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: (T) -> Boolean = { true },
    onLongPress: ((T) -> Unit)? = null,
    onKey: (Key, T) -> Boolean = { _, _ -> false },
    value: (T) -> String? = { null },
) {
    val count = items.size
    // Confirm keys pressed down while this list was on screen. A release counts only after such a press, so the end
    // of a long press that opened another list never confirms a row there.
    val pressed = remember { mutableSetOf<Key>() }

    LaunchedEffect(state.moves) {
        // Scrolling composes the selected row if it was off screen; the row then takes focus itself.
        state.scroll.scrollToItem(state.position.first)
    }
    LaunchedEffect(count) {
        // A row removed under the selection (an uninstalled app, say) hands it to the row now last.
        if (state.position.selected >= count) state.select(state.position.selecting(count, rowsPerPage))
    }

    LazyColumn(
        state = state.scroll,
        flingBehavior = rememberSnapFlingBehavior(state.scroll),
        modifier = modifier
            .fillMaxWidth()
            .height(rowHeight * rowsPerPage)
            .onPreviewKeyEvent { event ->
                val item = items.getOrNull(state.position.selected) ?: return@onPreviewKeyEvent false
                val held = event.nativeKeyEvent.repeatCount > 0
                if (event.key in ConfirmKeys) {
                    return@onPreviewKeyEvent when {
                        event.type == KeyEventType.KeyUp -> !pressed.remove(event.key)
                        !held -> {
                            pressed += event.key
                            false
                        }
                        // The first repeat of a key pressed down in this list is a long press, where there is one;
                        // other repeats are dropped, so holding a key never confirms more than once.
                        onLongPress != null && pressed.remove(event.key) -> {
                            onLongPress(item)
                            true
                        }
                        else -> true
                    }
                }
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                if (onKey(event.key, item)) return@onPreviewKeyEvent true
                // Start from the screen as it is now, which touch scrolling may have moved.
                val now = state.position.copy(first = state.scroll.firstVisibleItemIndex)
                val next = when (event.key) {
                    Key.DirectionUp -> now.stepped(-1, held, count, rowsPerPage)
                    Key.DirectionDown -> now.stepped(1, held, count, rowsPerPage)
                    Key.DirectionLeft -> now.paged(-1, count, rowsPerPage)
                    Key.DirectionRight -> now.paged(1, count, rowsPerPage)
                    else -> return@onPreviewKeyEvent false
                }
                state.select(next)
                true
            },
    ) {
        itemsIndexed(items, key = { _, item -> key(item) }) { index, item ->
            PillRow(
                label = label(item),
                value = value(item),
                height = rowHeight,
                enabled = enabled(item),
                onConfirm = { onConfirm(item) },
                onLongPress = onLongPress?.let { { it(item) } },
                // Focus that moves some other way (Tab, say) moves the selection with it.
                onFocused = {
                    if (state.position.selected != index) state.position = state.position.copy(selected = index)
                },
                focusRequest = if (index == state.position.selected) state.moves else null,
                isSelected = { index == state.position.selected },
            )
        }
    }
}

/** The keys that confirm a row, as Compose's clickable reads them. */
private val ConfirmKeys = setOf(Key.Enter, Key.NumPadEnter, Key.DirectionCenter)

/**
 * A list screen that takes the app list's place for a while: the [status] pill on top, the [title] in a dark-grey
 * pill below it, then the [list], given the row height and the rows left for it per page. Back calls [onClose]; the
 * list handles Esc itself, through its `onKey`.
 */
@Composable
internal fun TitledList(
    title: String,
    status: StatusPill?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    list: @Composable (rowHeight: Dp, rowsPerPage: Int) -> Unit,
) {
    BackHandler(onBack = onClose)
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        val density = LocalDensity.current
        val rhythm = listRhythm(constraints.maxHeight, with(density) { RowMetrics.rowHeight().roundToPx() })
        val rowHeight = with(density) { rhythm.rowHeight.toDp() }
        val reserved = (if (status != null) 1 else 0) + 1
        Column {
            if (status != null) StatusLine(status, rowHeight)
            HeaderLine(title, rowHeight, pill = true)
            list(rowHeight, maxOf(1, rhythm.rows - reserved))
        }
    }
}

/**
 * One row of text above a list, not selectable, aligned with the labels of the rows below it: the typed query, or a
 * menu's title, which sits in a dark-grey pill like the status pill (MinUI marks its in-game menu's title that way).
 */
@Composable
internal fun HeaderLine(text: String, height: Dp, pill: Boolean = false) {
    Box(
        contentAlignment = Alignment.CenterStart,
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .padding(horizontal = RowMetrics.ListPaddingHorizontal),
    ) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onBackground,
            style = RowMetrics.TextStyle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .background(if (pill) PillColor else Color.Transparent, RowMetrics.PillShape)
                .padding(horizontal = RowMetrics.PillPaddingHorizontal, vertical = RowMetrics.PillPaddingVertical),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PillRow(
    label: String,
    value: String?,
    height: Dp,
    enabled: Boolean,
    onConfirm: () -> Unit,
    onLongPress: (() -> Unit)?,
    onFocused: () -> Unit,
    focusRequest: Int?,
    isSelected: () -> Boolean,
) {
    var focused by remember { mutableStateOf(false) }
    val requester = remember { FocusRequester() }
    val colors = MaterialTheme.colorScheme

    // Runs once this row has composed as the selected one, so its requester is attached. A later move (or Home) may
    // have selected another row by then; that row asks instead, so focus never lands on a stale selection.
    LaunchedEffect(focusRequest) {
        if (focusRequest != null && isSelected()) requester.requestFocus()
    }

    // The whole row is the focus and touch target; only the drawn pill hugs the label. A value sits at the right end,
    // outside the pill, as in MinUI's and Cannoli's settings.
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .focusRequester(requester)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            // Clickables refuse focus in touch mode; a keyboard launcher needs a focused row from the start.
            .focusProperties { canFocus = true }
            // A disabled row stays clickable, so it can still be selected and the pill can rest on it; it just does
            // nothing. No ripple: its full-width focus layer would blur the pill, the one selection mark.
            .combinedClickable(
                interactionSource = null,
                indication = null,
                onLongClick = onLongPress,
                onClick = { if (enabled) onConfirm() },
            )
            .semantics { if (!enabled) disabled() }
            .padding(horizontal = RowMetrics.ListPaddingHorizontal),
    ) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            Text(
                text = label,
                color = when {
                    !enabled -> DimText
                    focused -> colors.onPrimary
                    else -> colors.onBackground
                },
                style = RowMetrics.TextStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .background(if (focused) colors.primary else Color.Transparent, RowMetrics.PillShape)
                    .padding(horizontal = RowMetrics.PillPaddingHorizontal, vertical = RowMetrics.PillPaddingVertical),
            )
        }
        if (value != null) {
            Text(
                text = value,
                color = if (enabled) colors.onBackground else DimText,
                style = RowMetrics.TextStyle,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = RowMetrics.PillPaddingHorizontal),
            )
        }
    }
}
