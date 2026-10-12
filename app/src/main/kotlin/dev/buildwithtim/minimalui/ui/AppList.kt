package dev.buildwithtim.minimalui.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.buildwithtim.minimalui.core.LauncherApp

/**
 * One row per app. Arrow keys / D-pad move focus, Enter or D-pad centre opens the focused app; touch works too.
 * The first row takes focus when the apps first arrive, so the keyboard works without touching the screen, and again
 * whenever [homePresses] changes, scrolled back into view.
 */
@Composable
fun AppList(apps: List<LauncherApp>, homePresses: Int, onOpen: (LauncherApp) -> Unit, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    val firstRow = remember { FocusRequester() }
    val hasApps = apps.isNotEmpty()

    LaunchedEffect(hasApps, homePresses) {
        if (hasApps) {
            // Scrolling composes row 0 again if it was scrolled away, attaching its requester before the request.
            listState.scrollToItem(0)
            firstRow.requestFocus()
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        itemsIndexed(apps, key = { _, app -> "${app.packageName}/${app.activityName}" }) { index, app ->
            AppRow(
                app = app,
                onOpen = onOpen,
                modifier = if (index == 0) Modifier.focusRequester(firstRow) else Modifier,
            )
        }
    }
}

@Composable
private fun AppRow(app: LauncherApp, onOpen: (LauncherApp) -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme

    Text(
        text = app.label,
        color = if (focused) colors.onPrimary else colors.onBackground,
        style = MaterialTheme.typography.titleLarge,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            // Clickables refuse focus in touch mode; a keyboard launcher needs a focused row from the start.
            .focusProperties { canFocus = true }
            .clickable { onOpen(app) }
            .background(if (focused) colors.primary else colors.background)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    )
}
