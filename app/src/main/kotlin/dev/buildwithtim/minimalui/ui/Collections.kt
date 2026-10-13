package dev.buildwithtim.minimalui.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.ui.input.key.Key
import dev.buildwithtim.minimalui.core.HintedScreen
import dev.buildwithtim.minimalui.core.LauncherApp
import dev.buildwithtim.minimalui.core.ListPosition
import dev.buildwithtim.minimalui.core.StatusPill
import dev.buildwithtim.minimalui.core.component
import dev.buildwithtim.minimalui.core.selecting

/**
 * The user's collections (#68), opened from the root's Collections row, or the groups (#69), from its Groups row: their
 * [names] under the [title] pill, Enter opening one ([onOpen]); with [onMenu], the Menu key, holding Enter or a
 * long-press asks for one's menu. Back or Esc calls [onClose]. The selection lives in [state], so the list comes back
 * on its row from a collection or group.
 */
@Composable
internal fun CollectionsList(
    title: String,
    names: List<String>,
    onOpen: (String) -> Unit,
    onClose: () -> Unit,
    state: PillListState,
    status: StatusPill? = null,
    onMenu: ((String) -> Unit)? = null,
) {
    val hints = HintedScreen.SubList(hasMenu = onMenu != null, isEmpty = names.isEmpty())
    TitledList(title, status, hints, onClose) { rowHeight, rowsPerPage ->
        PillList(
            state = state,
            items = names,
            key = { it },
            label = { it },
            rowHeight = rowHeight,
            rowsPerPage = rowsPerPage,
            onConfirm = onOpen,
            onLongPress = onMenu,
            onKey = { key, name ->
                if (key == Key.Menu && onMenu != null) {
                    onMenu(name)
                    true
                } else {
                    closesOnEscape(key, onClose)
                }
            },
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
 * One collection called [name]: its [apps] in the user's order (or one group, #69, its apps in the main list's order),
 * a favourite with its `★` ([isFavourite]), an app whose name another shares with its [suffix] (#72), a [paused] app,
 * of a work profile whose apps are turned off (#78), dim. Enter opens an app ([onOpen]); the Menu key, holding Enter
 * or a long-press asks for its menu ([onMenu]); Back or Esc calls [onClose]. When an app moves, the selection moves
 * with it.
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
    paused: (LauncherApp) -> Boolean = { false },
) {
    val hints = HintedScreen.SubList(hasMenu = true, isEmpty = apps.isEmpty())
    TitledList(name, status, hints, onClose) { rowHeight, rowsPerPage ->
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
            key = { it.component },
            label = { shownLabel(it, isFavourite(it)) },
            suffix = suffix,
            dim = paused,
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
 * Where to add an app (#68): the collections (or user groups, #69) that do not hold it yet ([names]), Enter adding it
 * to one ([onChoose]), then a last row, [newLabel], that makes a new one ([onNew]). Back or Esc calls [onClose]. The
 * selection lives in [state], so the list comes back on its row from the new one's name.
 */
@Composable
internal fun CollectionChoice(
    title: String,
    names: List<String>,
    newLabel: String,
    onChoose: (String) -> Unit,
    onNew: () -> Unit,
    onClose: () -> Unit,
    state: PillListState,
    status: StatusPill? = null,
) {
    // The last row, New collection, is the one without a name.
    val rows: List<String?> = names + null

    TitledList(title, status, HintedScreen.Choice, onClose) { rowHeight, rowsPerPage ->
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
