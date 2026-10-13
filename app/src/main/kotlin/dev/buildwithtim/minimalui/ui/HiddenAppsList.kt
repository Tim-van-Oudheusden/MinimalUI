package dev.buildwithtim.minimalui.ui

import androidx.compose.foundation.focusable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import dev.buildwithtim.minimalui.R
import dev.buildwithtim.minimalui.core.LauncherApp
import dev.buildwithtim.minimalui.core.ListPosition
import dev.buildwithtim.minimalui.core.StatusPill
import dev.buildwithtim.minimalui.core.selecting

/**
 * The Hidden apps list (#71), opened from the settings: the hidden [apps] under the [title] pill, Enter or a tap
 * showing one again ([onUnhide]). The row then leaves the list and the selection stays on its row number, or the new
 * last row. With none hidden, a centred message says so. Back or Esc calls [onClose].
 */
@Composable
internal fun HiddenAppsList(
    title: String,
    apps: List<LauncherApp>,
    onUnhide: (LauncherApp) -> Unit,
    onClose: () -> Unit,
    state: PillListState,
    status: StatusPill? = null,
) {
    TitledList(title, status, onClose) { rowHeight, rowsPerPage ->
        if (apps.isEmpty()) {
            // Focused, so Esc still reaches the list once its last row is gone.
            val requester = remember { FocusRequester() }
            Message(
                stringResource(R.string.no_hidden_apps),
                Modifier
                    .focusRequester(requester)
                    .focusable()
                    .onPreviewKeyEvent { it.type == KeyEventType.KeyDown && closesOnEscape(it.key, onClose) },
            )
            LaunchedEffect(Unit) { requester.requestFocus() }
        } else {
            // The row number of the app just unhidden. Focus leaves its row as the row goes, and lands elsewhere, so
            // once the shorter list is on screen that row number is selected again: the next app, or the new last.
            val unhiddenRow = remember { mutableStateOf<Int?>(null) }
            LaunchedEffect(apps) {
                val row = unhiddenRow.value ?: return@LaunchedEffect
                unhiddenRow.value = null
                val position = ListPosition(minOf(row, apps.lastIndex), state.scroll.firstVisibleItemIndex)
                state.select(position.selecting(apps.size, rowsPerPage))
            }
            PillList(
                state = state,
                items = apps,
                key = { "${it.packageName}/${it.activityName}" },
                label = { it.label },
                rowHeight = rowHeight,
                rowsPerPage = rowsPerPage,
                onConfirm = { app ->
                    unhiddenRow.value = apps.indexOf(app)
                    onUnhide(app)
                },
                onKey = { key, _ -> closesOnEscape(key, onClose) },
            )
        }
    }
}
