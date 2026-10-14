package dev.buildwithtim.minimalui.core

/** What the root list last selected its first row for: whether it had apps, the Home count and the query. */
data class TopCause(
    val hasApps: Boolean,
    val homePresses: Int,
    val query: String,
)

/** What the root list does with its selection when its rows, the Home count, the query or a return change. */
sealed interface SelectionMove {
    /** The selection stays where it is. */
    data object Stay : SelectionMove

    /** A new [TopCause]: the first row is selected, with the screen at the top, and any waiting return is spent. */
    data object Top : SelectionMove

    /** The user came back by a route other than Home: [row] is selected, keeping the screen if it is on it. */
    data class Return(
        val row: Int,
    ) : SelectionMove

    /** The rows changed under the selection: [row] now holds what it was on, or follows the blank row it became. */
    data class Follow(
        val row: Int,
    ) : SelectionMove
}

/**
 * How the root list's selection moves when its rows change from [before] to [now], with row [selected] selected. A
 * [cause] other than the [lastCause] the top was selected for selects the top; else a return not yet acted on, to
 * [returnedRow], selects its row once there are apps; else rows that changed keep the selection on its entry
 * ([followedRow]), moving it only when that entry moved or its row is now blank.
 */
fun selectionMove(
    cause: TopCause,
    lastCause: TopCause?,
    returnedRow: Int?,
    before: List<RootEntry>,
    selected: Int,
    now: List<RootEntry>,
): SelectionMove {
    val hasApps = now.isNotEmpty()
    return when {
        cause != lastCause -> SelectionMove.Top
        returnedRow != null && hasApps -> SelectionMove.Return(returnedRow)
        hasApps && now != before -> followed(before, selected, now)
        else -> SelectionMove.Stay
    }
}

private fun followed(
    before: List<RootEntry>,
    selected: Int,
    now: List<RootEntry>,
): SelectionMove {
    val row = followedRow(before, selected, now)
    return if (row != selected ||
        now.getOrNull(row)?.selectable == false
    ) {
        SelectionMove.Follow(row)
    } else {
        SelectionMove.Stay
    }
}
