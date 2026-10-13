package dev.buildwithtim.minimalui.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.res.stringResource
import dev.buildwithtim.minimalui.R
import dev.buildwithtim.minimalui.core.MenuAction
import dev.buildwithtim.minimalui.core.MenuItem
import dev.buildwithtim.minimalui.core.StatusPill

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

    TitledList(title, status, onClose, modifier) { rowHeight, rowsPerPage ->
        PillList(
            state = state,
            items = items,
            key = { it.action },
            label = { labels.getValue(it.action) },
            rowHeight = rowHeight,
            rowsPerPage = rowsPerPage,
            onConfirm = { onChoose(it.action) },
            enabled = { it.enabled },
            onKey = { key, _ -> (key == Key.Escape).also { if (it) onClose() } },
        )
    }
}

private val MenuAction.label: Int
    get() = when (this) {
        MenuAction.AppInfo -> R.string.menu_app_info
        MenuAction.Uninstall -> R.string.menu_uninstall
        MenuAction.RemoveFromRecents -> R.string.menu_remove_recent
        MenuAction.ClearRecents -> R.string.menu_clear_recents
    }
