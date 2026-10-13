package dev.buildwithtim.minimalui.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.res.stringResource
import dev.buildwithtim.minimalui.R
import dev.buildwithtim.minimalui.core.HintedScreen
import dev.buildwithtim.minimalui.core.MenuAction
import dev.buildwithtim.minimalui.core.MenuItem
import dev.buildwithtim.minimalui.core.StatusPill

/**
 * A menu, a second list in the app list's style that takes its place on screen (#67): one app's context menu, or a
 * group's (#69). The [title] (the app's or group's name) sits in a dark-grey pill in the row below the [status] pill,
 * not selectable; the [items] follow, the first one selected. Choosing an enabled item calls [onChoose]; Back or Esc
 * calls [onClose]. The selection lives in [state], so the menu comes back on its row from a list opened from it.
 */
@Composable
fun ContextMenu(
    title: String,
    items: List<MenuItem>,
    onChoose: (MenuAction) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    status: StatusPill? = null,
    state: PillListState = remember { PillListState() },
) {
    val labels = MenuAction.entries.associateWith { stringResource(it.label) }

    TitledList(title, status, HintedScreen.Menu, onClose, modifier) { rowHeight, rowsPerPage ->
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
        MenuAction.AddToFavourites -> R.string.menu_add_favourite
        MenuAction.RemoveFromFavourites -> R.string.menu_remove_favourite
        MenuAction.AddToCollection -> R.string.menu_add_collection
        MenuAction.RemoveFromCollection -> R.string.menu_remove_collection
        MenuAction.MoveUp -> R.string.menu_move_up
        MenuAction.MoveDown -> R.string.menu_move_down
        MenuAction.AddToGroup -> R.string.menu_add_group
        MenuAction.RemoveFromGroup -> R.string.menu_remove_group
        MenuAction.Rename -> R.string.menu_rename
        MenuAction.Hide -> R.string.menu_hide
        MenuAction.Unhide -> R.string.menu_unhide
        MenuAction.RemoveFromRecents -> R.string.menu_remove_recent
        MenuAction.ClearRecents -> R.string.menu_clear_recents
        MenuAction.RenameGroup -> R.string.menu_rename_group
        MenuAction.DeleteGroup -> R.string.menu_delete_group
    }
