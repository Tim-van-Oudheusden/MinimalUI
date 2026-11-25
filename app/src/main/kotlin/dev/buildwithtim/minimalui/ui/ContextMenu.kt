package dev.buildwithtim.minimalui.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import dev.buildwithtim.minimalui.R
import dev.buildwithtim.minimalui.core.GroupMenuAction
import dev.buildwithtim.minimalui.core.HintedScreen
import dev.buildwithtim.minimalui.core.MenuAction
import dev.buildwithtim.minimalui.core.MenuItem
import dev.buildwithtim.minimalui.core.SetupChoice
import dev.buildwithtim.minimalui.core.StatusPill

/**
 * A menu, a second list in the app list's style that takes its place on screen (#67): one app's context menu, a
 * group's (#69), or the question an imported setup asks (#208). The [title] (the app's or group's name, or the
 * question's) sits in a dark-grey pill in the row below the [status] pill, not selectable; the [items] follow, the
 * first one selected, each shown with the string resource [label] gives its action (the `label` of a [MenuAction],
 * [GroupMenuAction] or [SetupChoice]). Choosing an enabled item calls [onChoose]; Back or Esc calls [onClose]. The
 * selection lives in [state], so the menu comes back on its row from a list opened from it.
 */
@Composable
fun <A : Enum<A>> ContextMenu(
    title: String,
    items: List<MenuItem<A>>,
    label: (A) -> Int,
    onChoose: (A) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    status: StatusPill? = null,
    state: PillListState = remember { PillListState() },
) {
    val labels = items.associate { it.action to stringResource(label(it.action)) }

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
            onKey = { key, _ -> closesOnEscape(key, onClose) },
        )
    }
}

/** The string resource of a context-menu row. */
internal val MenuAction.label: Int
    get() =
        when (this) {
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
        }

/** The string resource of a group-menu row. */
internal val GroupMenuAction.label: Int
    get() =
        when (this) {
            GroupMenuAction.RenameGroup -> R.string.menu_rename_group
            GroupMenuAction.DeleteGroup -> R.string.menu_delete_group
        }

/** The string resource of an answer to the import-setup question. */
internal val SetupChoice.label: Int
    get() =
        when (this) {
            SetupChoice.ReplaceSetup -> R.string.import_replace_setup
            SetupChoice.KeepSetup -> R.string.import_keep_setup
        }
