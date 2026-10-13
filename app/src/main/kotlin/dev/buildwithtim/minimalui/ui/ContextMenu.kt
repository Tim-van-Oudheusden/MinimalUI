package dev.buildwithtim.minimalui.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import dev.buildwithtim.minimalui.R
import dev.buildwithtim.minimalui.core.MenuAction
import dev.buildwithtim.minimalui.core.MenuItem
import dev.buildwithtim.minimalui.core.StatusPill
import dev.buildwithtim.minimalui.core.listRhythm

/**
 * The context menu of one app, a second list in the app list's style that takes its place on screen (#67). The
 * [title] (the app's name) sits in a dark-grey pill in the row below the [status] pill, not selectable; the [items] follow, the first
 * one selected. Choosing an enabled item calls [onChoose]; Back or Esc calls [onClose].
 */
@Composable
fun ContextMenu(
    title: String,
    items: List<MenuItem>,
    onChoose: (MenuAction) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    status: StatusPill? = null,
) {
    val state = remember { PillListState() }
    val labels = MenuAction.entries.associateWith { stringResource(it.label) }
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
            PillList(
                state = state,
                items = items,
                key = { it.action },
                label = { labels.getValue(it.action) },
                rowHeight = rowHeight,
                rowsPerPage = maxOf(1, rhythm.rows - reserved),
                onConfirm = { onChoose(it.action) },
                enabled = { it.enabled },
                onKey = { key, _ -> (key == Key.Escape).also { if (it) onClose() } },
            )
        }
    }
}

private val MenuAction.label: Int
    get() = when (this) {
        MenuAction.AppInfo -> R.string.menu_app_info
        MenuAction.Uninstall -> R.string.menu_uninstall
        MenuAction.RemoveFromRecents -> R.string.menu_remove_recent
        MenuAction.ClearRecents -> R.string.menu_clear_recents
    }
