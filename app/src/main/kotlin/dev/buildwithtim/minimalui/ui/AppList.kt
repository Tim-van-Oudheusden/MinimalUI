package dev.buildwithtim.minimalui.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import dev.buildwithtim.minimalui.core.LauncherApp

/**
 * One row per app. Arrow keys / D-pad move focus, Enter or D-pad centre opens the focused app; touch works too. The
 * focused app's label sits in an inverted pill that hugs it (see BRANDING.md); every row is [RowMetrics.rowHeight] tall.
 * The first row takes focus when the apps first arrive, so the keyboard works without touching the screen, and again
 * whenever [homePresses] or the type-to-filter [query] changes, scrolled back into view. A non-empty [query] is shown
 * above the list, so the user never filters blind.
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
    val firstRow = remember { FocusRequester() }
    val hasApps = apps.isNotEmpty()

    LaunchedEffect(hasApps, homePresses, query) {
        if (hasApps) {
            // Scrolling composes row 0 again if it was scrolled away, attaching its requester before the request.
            listState.scrollToItem(0)
            firstRow.requestFocus()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        if (query.isNotEmpty()) {
            // The trailing underscore marks it as typed text rather than another app.
            // One row tall, its text aligned with the app labels below.
            Text(
                text = "${query}_",
                color = MaterialTheme.colorScheme.onBackground,
                style = RowMetrics.TextStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(RowMetrics.rowHeight())
                    .padding(horizontal = RowMetrics.ListPaddingHorizontal + RowMetrics.PillPaddingHorizontal)
                    .padding(vertical = RowMetrics.PillPaddingVertical),
            )
        }
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            itemsIndexed(apps, key = { _, app -> "${app.packageName}/${app.activityName}" }) { index, app ->
                AppRow(
                    app = app,
                    onOpen = onOpen,
                    modifier = if (index == 0) Modifier.focusRequester(firstRow) else Modifier,
                )
            }
        }
    }
}

@Composable
private fun AppRow(app: LauncherApp, onOpen: (LauncherApp) -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme

    // The whole row is the focus and touch target; only the drawn pill hugs the label.
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(RowMetrics.rowHeight())
            .onFocusChanged { focused = it.isFocused }
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
