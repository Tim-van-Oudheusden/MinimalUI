package dev.buildwithtim.minimalui.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import dev.buildwithtim.minimalui.R
import dev.buildwithtim.minimalui.core.HintedScreen
import dev.buildwithtim.minimalui.core.LauncherApp
import dev.buildwithtim.minimalui.core.ListContent
import dev.buildwithtim.minimalui.core.ListPosition
import dev.buildwithtim.minimalui.core.RootEntry
import dev.buildwithtim.minimalui.core.SelectionMove
import dev.buildwithtim.minimalui.core.StatusPill
import dev.buildwithtim.minimalui.core.TopCause
import dev.buildwithtim.minimalui.core.buttonHints
import dev.buildwithtim.minimalui.core.listRhythm
import dev.buildwithtim.minimalui.core.selecting
import dev.buildwithtim.minimalui.core.selectionMove

/**
 * The root list (#70), in a [PillList]: one row per entry of [content], an app's row opening it ([onOpen]) on Enter,
 * D-pad centre or a tap; the Menu key, holding Enter or D-pad centre, or a touch long-press asks for its context menu
 * ([onMenu]). A favourite's name has a `★` before it (#68); a hidden app, listed only when the query finds it (#71), is
 * drawn dim, and so is a [paused] app, of a work profile whose apps are turned off (#78). An app whose name another app
 * shares has its [suffix] after it, small and dim (#72). The Collections row
 * opens the collections ([onCollections]) and the Groups row at the end the groups ([onGroups], #69); neither has a
 * menu. The blank rows below the block at the top and above the Groups row are empty, and the selection skips them.
 *
 * The first row is selected when the apps first arrive, so the keyboard works without touching the screen, and again
 * whenever [homePresses] or the type-to-filter [query] changes. Each new [returned] selects its row instead, keeping
 * the screen where it was if that row is on it. When the rows change otherwise (the block above grows or shrinks), the
 * selection stays on its entry. The selection and the screen live in [state], so a list that another screen replaced
 * for a while comes back exactly as it was. The [status] pill, when given, takes the top row, so no row ever runs
 * under it. A non-empty [query] is shown in the row below it, so the user never filters blind. The hint row (#74)
 * takes the bottom row, naming the keys that work on the selected row. Without apps to list, the rest of the screen
 * stays blank while they load (the status pill and the hints too), or shows a centred message when there are none or
 * none match the query.
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
    paused: (LauncherApp) -> Boolean = { false },
) {
    val entries = (content as? ListContent.Apps)?.entries.orEmpty()
    val list = state.list

    BoxWithConstraints(
        modifier =
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        val density = LocalDensity.current
        val rhythm = listRhythm(constraints.maxHeight, with(density) { RowMetrics.rowHeight().roundToPx() })
        val rowHeight = with(density) { rhythm.rowHeight.toDp() }
        // The status line and the query line each take a whole row at the top, and the hint row (#74) one at the
        // bottom; the list gets the rest. A reserved row, rather than MinUI's shortened first row, because the first
        // row on screen changes as the list scrolls, and every page keeps the same rhythm.
        val shownStatus = status?.takeUnless { content == ListContent.Loading }
        val reserved = (if (shownStatus != null) 1 else 0) + (if (query.isEmpty()) 0 else 1) + 1
        val rowsPerPage = maxOf(1, rhythm.rows - reserved)

        LaunchedEffect(entries, homePresses, query, returned) {
            // Only for a change since the list was last on screen, so coming back from another screen keeps the
            // selection. Start from the screen as it is now, which touch scrolling may have moved.
            state.follow(entries, TopCause(entries.isNotEmpty(), homePresses, query), returned, rowsPerPage)
        }

        Column(Modifier.fillMaxSize()) {
            if (shownStatus != null) StatusLine(shownStatus, rowHeight)
            // The trailing underscore marks it as typed text rather than another app.
            if (query.isNotEmpty()) HeaderLine("${query}_", rowHeight)
            if (entries.isNotEmpty()) {
                AppRows(
                    state = list,
                    entries = entries,
                    rowHeight = rowHeight,
                    rowsPerPage = rowsPerPage,
                    onOpen = onOpen,
                    onMenu = onMenu,
                    onCollections = onCollections,
                    onGroups = onGroups,
                    suffix = suffix,
                    paused = paused,
                )
            }
            Spacer(Modifier.weight(1f))
            val selected = entries.getOrNull(list.position.selected)
            HintLine(buttonHints(HintedScreen.Root(content, query, selected)), rowHeight)
        }
        // Centred on the whole screen, as MinUI does, whether or not the query line is showing above it.
        content.message?.let { Message(stringResource(it)) }
    }
}

/** The message shown instead of the rows: none while loading or with apps to list. */
private val ListContent.message: Int?
    get() =
        when (this) {
            ListContent.Loading, is ListContent.Apps -> null
            ListContent.NoApps -> R.string.no_apps
            ListContent.NoMatch -> R.string.no_match
        }

/**
 * Moves the selection as [selectionMove] decides for the rows now being [entries], the [cause] of the top row and a
 * [returned] not yet acted on, with [rowsPerPage] rows on a page, and remembers what it acted on.
 */
private fun AppListState.follow(
    entries: List<RootEntry>,
    cause: TopCause,
    returned: Returned?,
    rowsPerPage: Int,
) {
    val fresh = returned?.takeIf { it != this.returned }
    val move = selectionMove(cause, topFor, fresh?.row, shown, list.position.selected, entries)
    shown = entries
    when (move) {
        SelectionMove.Top -> {
            topFor = cause
            this.returned = returned
            if (entries.isNotEmpty()) list.select(ListPosition(0, 0))
        }

        is SelectionMove.Return -> {
            this.returned = returned
            select(move.row, entries, rowsPerPage)
        }

        is SelectionMove.Follow -> {
            select(move.row, entries, rowsPerPage)
        }

        SelectionMove.Stay -> {}
    }
}

/** Selects [row] of [entries], or the next row that is not blank, keeping the screen if the row is on it. */
private fun AppListState.select(
    row: Int,
    entries: List<RootEntry>,
    rowsPerPage: Int,
) {
    val position = ListPosition(row, list.scroll.firstVisibleItemIndex)
    list.select(position.selecting(entries.size, rowsPerPage) { !entries[it].selectable })
}

/** What the app list keeps while another screen takes its place: [AppList] comes back as it was. */
@Stable
class AppListState {
    internal val list = PillListState()

    // What the top row was last selected for: the apps-present flag, Home count and query.
    internal var topFor: TopCause? = null

    // The last [Returned] acted on.
    internal var returned: Returned? = null

    // The rows last on screen, so the selection can follow its entry when they change.
    internal var shown: List<RootEntry> = emptyList()
}

@Composable
fun rememberAppListState(): AppListState = remember { AppListState() }

/** The user came back to the launcher by a route other than Home, for the [count]th time: the list selects [row]. */
data class Returned(
    val row: Int,
    val count: Int,
)

/** [app]'s name as listed: a favourite's with Cannoli's `★` before it (#68). Drawn only; filtering never sees it. */
internal fun shownLabel(
    app: LauncherApp,
    isFavourite: Boolean,
): String = if (isFavourite) "★ ${app.label}" else app.label

/** Centred on the space it is given (the whole screen for the root list), in the rows' text style. */
@Composable
internal fun Message(
    text: String,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = text, color = MaterialTheme.colorScheme.onBackground, style = RowMetrics.TextStyle)
    }
}
