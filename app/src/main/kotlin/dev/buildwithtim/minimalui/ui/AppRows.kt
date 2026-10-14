package dev.buildwithtim.minimalui.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import dev.buildwithtim.minimalui.R
import dev.buildwithtim.minimalui.core.LauncherApp
import dev.buildwithtim.minimalui.core.RootEntry

/**
 * The rows of [AppList], in a [PillList] in [state]: each of [entries] under its label, an app's row opening it
 * ([onOpen]) and the Menu key, a held confirm key or a long-press asking for its context menu ([onMenu]); the
 * Collections and Groups rows open their lists ([onCollections], [onGroups]). A hidden or [paused] app is drawn dim,
 * and an app's [suffix] follows its name.
 */
@Composable
internal fun AppRows(
    state: PillListState,
    entries: List<RootEntry>,
    rowHeight: Dp,
    rowsPerPage: Int,
    onOpen: (LauncherApp) -> Unit,
    modifier: Modifier = Modifier,
    onMenu: (LauncherApp) -> Unit = {},
    onCollections: () -> Unit = {},
    onGroups: () -> Unit = {},
    suffix: (LauncherApp) -> String? = { null },
    paused: (LauncherApp) -> Boolean = { false },
) {
    val collections = stringResource(R.string.collections)
    val groups = stringResource(R.string.groups)
    PillList(
        state = state,
        items = entries,
        key = { it.key },
        label = { it.label(collections, groups) },
        rowHeight = rowHeight,
        rowsPerPage = rowsPerPage,
        onConfirm = { it.confirm(onOpen, onCollections, onGroups) },
        modifier = modifier,
        selectable = { it.selectable },
        onLongPress = { it.app?.let(onMenu) },
        onKey = { key, entry -> entry.menuKey(key, onMenu) },
        dim = { entry -> (entry as? RootEntry.App)?.isHidden == true || entry.app?.let(paused) == true },
        suffix = { entry -> entry.app?.let(suffix) },
    )
}

/** The row's label: an app's name as listed, the [collections] or [groups] row's name, or nothing for a blank row. */
private fun RootEntry.label(
    collections: String,
    groups: String,
): String =
    when (this) {
        is RootEntry.App -> shownLabel(app, isFavourite)
        RootEntry.Collections -> collections
        RootEntry.Groups -> groups
        is RootEntry.Blank -> ""
    }

/** Opens what the row stands for: its app ([onOpen]), the collections or the groups; a blank row does nothing. */
private fun RootEntry.confirm(
    onOpen: (LauncherApp) -> Unit,
    onCollections: () -> Unit,
    onGroups: () -> Unit,
) = when (this) {
    is RootEntry.App -> onOpen(app)
    RootEntry.Collections -> onCollections()
    RootEntry.Groups -> onGroups()
    is RootEntry.Blank -> Unit
}

/** The Menu key on an app's row asks for its context menu ([onMenu]) and is taken; any other key is left alone. */
private fun RootEntry.menuKey(
    key: Key,
    onMenu: (LauncherApp) -> Unit,
): Boolean {
    val app = app
    if (key != Key.Menu || app == null) return false
    onMenu(app)
    return true
}
