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
import dev.buildwithtim.minimalui.core.StatusPill
import dev.buildwithtim.minimalui.core.listRhythm
import dev.buildwithtim.minimalui.core.selecting

/**
 * One row per app, in a [PillList]: Enter, D-pad centre or a tap opens the selected app ([onOpen]); the Menu key,
 * holding Enter or D-pad centre, or a touch long-press asks for its context menu ([onMenu]).
 *
 * The first row is selected when the apps first arrive, so the keyboard works without touching the screen, and again
 * whenever [homePresses] or the type-to-filter [query] changes. Each new [returned] selects its row instead, keeping
 * the screen where it was if that row is on it. The selection and the screen live in [state], so a list that another
 * screen replaced for a while comes back exactly as it was. The [status] pill, when given, takes the top row, so no
 * app row ever runs under it. A non-empty [query] is shown in the row below it, so the user never filters blind.
 * Without apps to list, the rest of the screen stays blank while they load (the status pill too), or shows a centred
 * message when there are none or none match the query.
 */
@Composable
fun AppList(
    content: ListContent,
    query: String,
    homePresses: Int,
    onOpen: (LauncherApp) -> Unit,
    modifier: Modifier = Modifier,
    onMenu: (LauncherApp) -> Unit = {},
    state: AppListState = rememberAppListState(),
    returned: Returned? = null,
    status: StatusPill? = null,
) {
    val apps = (content as? ListContent.Apps)?.apps.orEmpty()
    val list = state.list
    val hasApps = apps.isNotEmpty()

    LaunchedEffect(hasApps, homePresses, query) {
        // Only for a change since the list was last on screen, so coming back from another screen keeps the selection.
        val reason = Triple(hasApps, homePresses, query)
        if (reason != state.topFor) {
            state.topFor = reason
            if (hasApps) list.select(ListPosition(0, 0))
        }
    }

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

        LaunchedEffect(returned) {
            // Start from the screen as it is now, which touch scrolling may have moved.
            if (returned != null && returned != state.returned && hasApps) {
                state.returned = returned
                val now = ListPosition(returned.row, list.scroll.firstVisibleItemIndex)
                list.select(now.selecting(apps.size, rowsPerPage))
            }
        }

        Column {
            if (shownStatus != null) StatusLine(shownStatus, rowHeight)
            // The trailing underscore marks it as typed text rather than another app.
            if (query.isNotEmpty()) HeaderLine("${query}_", rowHeight)
            if (hasApps) PillList(
                state = list,
                items = apps,
                key = { "${it.packageName}/${it.activityName}" },
                label = { it.label },
                rowHeight = rowHeight,
                rowsPerPage = rowsPerPage,
                onConfirm = onOpen,
                onLongPress = onMenu,
                onKey = { key, app -> (key == Key.Menu).also { if (it) onMenu(app) } },
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
}

@Composable
fun rememberAppListState(): AppListState = remember { AppListState() }

/** The user came back to the launcher by a route other than Home, for the [count]th time: the list selects [row]. */
data class Returned(val row: Int, val count: Int)

/** Centred on the screen, in the rows' text style. */
@Composable
private fun Message(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = text, color = MaterialTheme.colorScheme.onBackground, style = RowMetrics.TextStyle)
    }
}
