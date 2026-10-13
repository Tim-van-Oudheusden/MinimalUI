package dev.buildwithtim.minimalui.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import dev.buildwithtim.minimalui.core.LauncherApp
import dev.buildwithtim.minimalui.core.ListPosition
import dev.buildwithtim.minimalui.core.listRhythm
import dev.buildwithtim.minimalui.core.paged
import dev.buildwithtim.minimalui.core.stepped

/**
 * One row per app, paged a whole screenful at a time: only whole rows are drawn, and the height left over is shared
 * between them. Up/Down move one row and wrap around at the ends, except while held. Left/Right turn a page, stopping
 * at the ends. Enter or D-pad centre opens the selected app; touch works too, and touch scrolling snaps to whole rows.
 * The selected app's label sits in an inverted pill that hugs it (see BRANDING.md) and holds focus.
 *
 * The first row is selected when the apps first arrive, so the keyboard works without touching the screen, and again
 * whenever [homePresses] or the type-to-filter [query] changes. A non-empty [query] is shown in the top row, so the
 * user never filters blind.
 */
@Composable
fun AppList(
    apps: List<LauncherApp>,
    query: String,
    homePresses: Int,
    onOpen: (LauncherApp) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    var position by remember { mutableStateOf(ListPosition(0, 0)) }
    // Bumped on every move, even to the same position, so the list scrolls and the selected row takes focus each time.
    var moves by remember { mutableIntStateOf(0) }
    val hasApps = apps.isNotEmpty()

    fun select(next: ListPosition) {
        position = next
        moves++
    }

    LaunchedEffect(hasApps, homePresses, query) {
        if (hasApps) select(ListPosition(0, 0))
    }
    LaunchedEffect(moves) {
        // Scrolling composes the selected row if it was off screen; the row then takes focus itself.
        if (hasApps) listState.scrollToItem(position.first)
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        val density = LocalDensity.current
        val rhythm = listRhythm(constraints.maxHeight, with(density) { RowMetrics.rowHeight().roundToPx() })
        val rowHeight = with(density) { rhythm.rowHeight.toDp() }
        // The query line takes the top row; the list gets the rest.
        val rowsPerPage = maxOf(1, if (query.isEmpty()) rhythm.rows else rhythm.rows - 1)

        Column {
            if (query.isNotEmpty()) {
                // The trailing underscore marks it as typed text rather than another app.
                // One row tall, its text aligned with the app labels below.
                Box(Modifier.fillMaxWidth().height(rowHeight), contentAlignment = Alignment.CenterStart) {
                    Text(
                        text = "${query}_",
                        color = MaterialTheme.colorScheme.onBackground,
                        style = RowMetrics.TextStyle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .padding(horizontal = RowMetrics.ListPaddingHorizontal + RowMetrics.PillPaddingHorizontal)
                            .padding(vertical = RowMetrics.PillPaddingVertical),
                    )
                }
            }
            LazyColumn(
                state = listState,
                flingBehavior = rememberSnapFlingBehavior(listState),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(rowHeight * rowsPerPage)
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown || !hasApps) return@onPreviewKeyEvent false
                        // Start from the screen as it is now, which touch scrolling may have moved.
                        val now = position.copy(first = listState.firstVisibleItemIndex)
                        val held = event.nativeKeyEvent.repeatCount > 0
                        val next = when (event.key) {
                            Key.DirectionUp -> now.stepped(-1, held, apps.size, rowsPerPage)
                            Key.DirectionDown -> now.stepped(1, held, apps.size, rowsPerPage)
                            Key.DirectionLeft -> now.paged(-1, apps.size, rowsPerPage)
                            Key.DirectionRight -> now.paged(1, apps.size, rowsPerPage)
                            else -> return@onPreviewKeyEvent false
                        }
                        select(next)
                        true
                    },
            ) {
                itemsIndexed(apps, key = { _, app -> "${app.packageName}/${app.activityName}" }) { index, app ->
                    AppRow(
                        app = app,
                        height = rowHeight,
                        onOpen = onOpen,
                        // Focus that moves some other way (Tab, say) moves the selection with it.
                        onFocused = { if (position.selected != index) position = position.copy(selected = index) },
                        focusRequest = if (index == position.selected) moves else null,
                        isSelected = { index == position.selected },
                    )
                }
            }
        }
    }
}

@Composable
private fun AppRow(
    app: LauncherApp,
    height: Dp,
    onOpen: (LauncherApp) -> Unit,
    onFocused: () -> Unit,
    focusRequest: Int?,
    isSelected: () -> Boolean,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val requester = remember { FocusRequester() }
    val colors = MaterialTheme.colorScheme

    // Runs once this row has composed as the selected one, so its requester is attached. A later move (or Home) may
    // have selected another row by then; that row asks instead, so focus never lands on a stale selection.
    LaunchedEffect(focusRequest) {
        if (focusRequest != null && isSelected()) requester.requestFocus()
    }

    // The whole row is the focus and touch target; only the drawn pill hugs the label.
    Box(
        contentAlignment = Alignment.CenterStart,
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .focusRequester(requester)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            // Clickables refuse focus in touch mode; a keyboard launcher needs a focused row from the start.
            .focusProperties { canFocus = true }
            // No ripple: its full-width focus layer would blur the pill, the one selection mark.
            .clickable(interactionSource = null, indication = null) { onOpen(app) }
            .padding(horizontal = RowMetrics.ListPaddingHorizontal),
    ) {
        Text(
            text = app.label,
            color = if (focused) colors.onPrimary else colors.onBackground,
            style = RowMetrics.TextStyle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .background(if (focused) colors.primary else Color.Transparent, RowMetrics.PillShape)
                .padding(horizontal = RowMetrics.PillPaddingHorizontal, vertical = RowMetrics.PillPaddingVertical),
        )
    }
}
