package dev.buildwithtim.minimalui.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.res.stringResource
import dev.buildwithtim.minimalui.R
import dev.buildwithtim.minimalui.core.LauncherApp
import dev.buildwithtim.minimalui.core.ListPosition
import dev.buildwithtim.minimalui.core.StatusPill
import dev.buildwithtim.minimalui.core.selecting

/**
 * The user's collections (#68), opened from the root's Collections row: their [names] under the [title] pill, Enter
 * opening one ([onOpen]); Back or Esc calls [onClose]. The selection lives in [state], so the list comes back on its
 * row from a collection.
 */
@Composable
internal fun CollectionsList(
    title: String,
    names: List<String>,
    onOpen: (String) -> Unit,
    onClose: () -> Unit,
    state: PillListState,
    status: StatusPill? = null,
) {
    TitledList(title, status, onClose) { rowHeight, rowsPerPage ->
        PillList(
            state = state,
            items = names,
            key = { it },
            label = { it },
            rowHeight = rowHeight,
            rowsPerPage = rowsPerPage,
            onConfirm = onOpen,
            onKey = { key, _ -> closesOnEscape(key, onClose) },
        )
    }
}

/** What a collection's list keeps while a menu or an app takes its place: [CollectionApps] comes back as it was. */
@Stable
class CollectionState {
    internal val list = PillListState()

    // The apps last on screen, so the selection can follow its app when one moves.
    internal var shown: List<LauncherApp> = emptyList()
}

/**
 * One collection called [name]: its [apps] in the user's order, a favourite with its `★` ([isFavourite]), an app whose
 * name another shares with its [suffix] (#72). Enter opens an app ([onOpen]); the Menu key, holding Enter or a
 * long-press asks for its menu ([onMenu]); Back or Esc calls [onClose]. When an app moves, the selection moves with it.
 */
@Composable
internal fun CollectionApps(
    name: String,
    apps: List<LauncherApp>,
    isFavourite: (LauncherApp) -> Boolean,
    onOpen: (LauncherApp) -> Unit,
    onMenu: (LauncherApp) -> Unit,
    onClose: () -> Unit,
    state: CollectionState,
    status: StatusPill? = null,
    suffix: (LauncherApp) -> String? = { null },
) {
    TitledList(name, status, onClose) { rowHeight, rowsPerPage ->
        LaunchedEffect(apps) {
            val before = state.shown
            state.shown = apps
            val selected = state.list.position.selected
            val row = before.getOrNull(selected)?.let(apps::indexOf) ?: return@LaunchedEffect
            if (row >= 0 && row != selected) {
                val position = ListPosition(row, state.list.scroll.firstVisibleItemIndex)
                state.list.select(position.selecting(apps.size, rowsPerPage))
            }
        }
        PillList(
            state = state.list,
            items = apps,
            key = { "${it.packageName}/${it.activityName}" },
            label = { shownLabel(it, isFavourite(it)) },
            suffix = suffix,
            rowHeight = rowHeight,
            rowsPerPage = rowsPerPage,
            onConfirm = onOpen,
            onLongPress = onMenu,
            onKey = { key, app ->
                if (key == Key.Menu) {
                    onMenu(app)
                    true
                } else {
                    closesOnEscape(key, onClose)
                }
            },
        )
    }
}

/**
 * Where to add an app (#68): the collections that do not hold it yet ([names]), Enter adding it to one ([onChoose]),
 * then a last row that makes a new one ([onNew]). Back or Esc calls [onClose]. The selection lives in [state], so the
 * list comes back on its row from the new collection's name.
 */
@Composable
internal fun CollectionChoice(
    title: String,
    names: List<String>,
    onChoose: (String) -> Unit,
    onNew: () -> Unit,
    onClose: () -> Unit,
    state: PillListState,
    status: StatusPill? = null,
) {
    val newLabel = stringResource(R.string.new_collection)
    // The last row, New collection, is the one without a name.
    val rows: List<String?> = names + null

    TitledList(title, status, onClose) { rowHeight, rowsPerPage ->
        PillList(
            state = state,
            items = rows,
            key = { row -> row?.let { "name:$it" } ?: "new" },
            label = { row -> row ?: newLabel },
            rowHeight = rowHeight,
            rowsPerPage = rowsPerPage,
            onConfirm = { row -> if (row == null) onNew() else onChoose(row) },
            onKey = { key, _ -> closesOnEscape(key, onClose) },
        )
    }
}

/** Esc closes a list, as Back does; whether [key] was it. */
internal fun closesOnEscape(key: Key, onClose: () -> Unit): Boolean = (key == Key.Escape).also { if (it) onClose() }
