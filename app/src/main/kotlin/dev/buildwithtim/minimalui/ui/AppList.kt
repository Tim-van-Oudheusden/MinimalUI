package dev.buildwithtim.minimalui.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import dev.buildwithtim.minimalui.R
import dev.buildwithtim.minimalui.core.LauncherApp
import dev.buildwithtim.minimalui.core.ListContent
import dev.buildwithtim.minimalui.core.ListPosition
import dev.buildwithtim.minimalui.core.RootEntry
import dev.buildwithtim.minimalui.core.StatusPill
import dev.buildwithtim.minimalui.core.followedRow
import dev.buildwithtim.minimalui.core.listRhythm
import dev.buildwithtim.minimalui.core.selecting

/**
 * The root list (#70), in a [PillList]: one row per entry of [content], an app's row opening it ([onOpen]) on Enter,
 * D-pad centre or a tap; the Menu key, holding Enter or D-pad centre, or a touch long-press asks for its context menu
 * ([onMenu]). A favourite's name has a `★` before it (#68); a hidden app, listed only when the query finds it (#71), is
 * drawn dim. An app whose name another app shares has its [suffix] after it, small and dim (#72). The Collections row
 * opens the collections ([onCollections]) and the Groups row at the end the groups ([onGroups], #69); neither has a
 * menu. The blank rows below the block at the top and above the Groups row are empty, and the selection skips them.
 *
 * The first row is selected when the apps first arrive, so the keyboard works without touching the screen, and again
 * whenever [homePresses] or the type-to-filter [query] changes. Each new [returned] selects its row instead, keeping
 * the screen where it was if that row is on it. When the rows change otherwise (the block above grows or shrinks), the
 * selection stays on its entry. The selection and the screen live in [state], so a list that another screen replaced
 * for a while comes back exactly as it was. The [status] pill, when given, takes the top row, so no row ever runs
 * under it. A non-empty [query] is shown in the row below it, so the user never filters blind. Without apps to list,
 * the rest of the screen stays blank while they load (the status pill too), or shows a centred message when there are
 * none or none match the query.
 */
@Composable
fun AppList(
    content: ListContent,
    query: String,
    homePresses: Int,
    onOpen: (LauncherApp) -> Unit,
    modifier: Modifier = Modifier,
    onMenu: (LauncherApp) -> Unit = {},
    onCollections: () -> Unit = {},
    onGroups: () -> Unit = {},
    state: AppListState = rememberAppListState(),
    returned: Returned? = null,
    status: StatusPill? = null,
    suffix: (LauncherApp) -> String? = { null },
) {
    val entries = (content as? ListContent.Apps)?.entries.orEmpty()
    val list = state.list
    val hasApps = entries.isNotEmpty()
    val blank = { row: Int -> !entries[row].selectable }
    val collections = stringResource(R.string.collections)
    val groups = stringResource(R.string.groups)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        val density = LocalDensity.current
        val rhythm = listRhythm(constraints.maxHeight, with(density) { RowMetrics.rowHeight().roundToPx() })
        val rowHeight = with(density) { rhythm.rowHeight.toDp() }
        // The status line and the query line each take a whole row at the top; the list gets the rest. A reserved row,
        // rather than MinUI's shortened first row, because the first row on screen changes as the list scrolls, and
        // every page keeps the same rhythm.
        val shownStatus = status?.takeUnless { content == ListContent.Loading }
        val reserved = (if (shownStatus != null) 1 else 0) + (if (query.isEmpty()) 0 else 1)
        val rowsPerPage = maxOf(1, rhythm.rows - reserved)

        LaunchedEffect(entries, homePresses, query, returned) {
            // Only for a change since the list was last on screen, so coming back from another screen keeps the
            // selection. Start from the screen as it is now, which touch scrolling may have moved.
            val reason = Triple(hasApps, homePresses, query)
            val select = { row: Int ->
                val position = ListPosition(row, list.scroll.firstVisibleItemIndex)
                list.select(position.selecting(entries.size, rowsPerPage, blank))
            }
            val before = state.shown
            state.shown = entries
            when {
                reason != state.topFor -> {
                    state.topFor = reason
                    state.returned = returned
                    if (hasApps) list.select(ListPosition(0, 0))
                }
                returned != null && returned != state.returned && hasApps -> {
                    state.returned = returned
                    select(returned.row)
                }
                // The block above grew or shrank: the selection stays on what it was on. When that row is gone and
                // the blank row now takes its number (the Collections row's last collection removed), it moves on.
                hasApps && entries != before -> {
                    val row = followedRow(before, list.position.selected, entries)
                    if (row != list.position.selected || entries.getOrNull(row)?.selectable == false) select(row)
                }
            }
        }

        Column {
            if (shownStatus != null) StatusLine(shownStatus, rowHeight)
            // The trailing underscore marks it as typed text rather than another app.
            if (query.isNotEmpty()) HeaderLine("${query}_", rowHeight)
            if (hasApps) PillList(
                state = list,
                items = entries,
                key = { it.key },
                label = { entry ->
                    when (entry) {
                        is RootEntry.App -> shownLabel(entry.app, entry.isFavourite)
                        RootEntry.Collections -> collections
                        RootEntry.Groups -> groups
                        is RootEntry.Blank -> ""
                    }
                },
                rowHeight = rowHeight,
                rowsPerPage = rowsPerPage,
                onConfirm = { entry ->
                    when (entry) {
                        is RootEntry.App -> onOpen(entry.app)
                        RootEntry.Collections -> onCollections()
                        RootEntry.Groups -> onGroups()
                        is RootEntry.Blank -> Unit
                    }
                },
                selectable = { it.selectable },
                onLongPress = { it.app?.let(onMenu) },
                onKey = { key, entry ->
                    val app = entry.app
                    if (key == Key.Menu && app != null) {
                        onMenu(app)
                        true
                    } else {
                        false
                    }
                },
                dim = { (it as? RootEntry.App)?.isHidden == true },
                suffix = { entry -> entry.app?.let(suffix) },
            )
        }
        // Centred on the whole screen, as MinUI does, whether or not the query line is showing above it.
        val message = when (content) {
            ListContent.Loading, is ListContent.Apps -> null
            ListContent.NoApps -> R.string.no_apps
            ListContent.NoMatch -> R.string.no_match
        }
        if (message != null) Message(stringResource(message))
    }
}

/** What the app list keeps while another screen takes its place: [AppList] comes back as it was. */
@Stable
class AppListState {
    internal val list = PillListState()

    // The apps-present flag, Home count and query the top row was last selected for.
    internal var topFor: Any? = null

    // The last [Returned] acted on.
    internal var returned: Returned? = null

    // The rows last on screen, so the selection can follow its entry when they change.
    internal var shown: List<RootEntry> = emptyList()
}

@Composable
fun rememberAppListState(): AppListState = remember { AppListState() }

/** The user came back to the launcher by a route other than Home, for the [count]th time: the list selects [row]. */
data class Returned(val row: Int, val count: Int)

/** [app]'s name as listed: a favourite's with Cannoli's `★` before it (#68). Drawn only; filtering never sees it. */
internal fun shownLabel(app: LauncherApp, isFavourite: Boolean): String =
    if (isFavourite) "★ ${app.label}" else app.label

/** Centred on the space it is given (the whole screen for the root list), in the rows' text style. */
@Composable
internal fun Message(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = text, color = MaterialTheme.colorScheme.onBackground, style = RowMetrics.TextStyle)
    }
}
