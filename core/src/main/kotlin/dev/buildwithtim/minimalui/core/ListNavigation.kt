package dev.buildwithtim.minimalui.core

/** How a list screen is cut into rows: [rows] whole rows, each [rowHeight] tall. */
data class ListRhythm(val rows: Int, val rowHeight: Int)

/**
 * As many whole rows of at least [minRowHeight] as fit in [availableHeight] (at least one), sharing out the leftover
 * height so no partial row is drawn and every page holds exactly [ListRhythm.rows] rows. The few pixels the division
 * leaves over stay below the last row.
 */
fun listRhythm(availableHeight: Int, minRowHeight: Int): ListRhythm {
    val rows = maxOf(1, availableHeight / minRowHeight)
    return ListRhythm(rows, maxOf(minRowHeight, availableHeight / rows))
}

/** The [selected] row and the [first] row on screen, in a list paged [ListRhythm.rows] rows at a time. */
data class ListPosition(val selected: Int, val first: Int)

/**
 * Up ([step] -1) or Down (+1). A fresh press past either end wraps around to the other end; a [held] key's auto-repeat
 * stops there instead, so holding a key never overshoots (MinUI's behaviour). The screen scrolls just enough to keep
 * the selection on it.
 */
fun ListPosition.stepped(step: Int, held: Boolean, count: Int, rowsPerPage: Int): ListPosition {
    if (count == 0) return ListPosition(0, 0)
    val target = selected.coerceAtMost(count - 1) + step
    val next = when {
        target in 0 until count -> target
        held -> target.coerceIn(0, count - 1)
        else -> target.mod(count)
    }
    return ListPosition(next, first).showingSelected(count, rowsPerPage)
}

/**
 * Left ([direction] -1) or Right (+1): the selection and the screen both move by a whole page, so the selection keeps
 * its place on screen. Stops at the first and last rows, with no wrap.
 */
fun ListPosition.paged(direction: Int, count: Int, rowsPerPage: Int): ListPosition {
    if (count == 0) return ListPosition(0, 0)
    val shift = direction * rowsPerPage
    return ListPosition((selected + shift).coerceIn(0, count - 1), first + shift).showingSelected(count, rowsPerPage)
}

/**
 * Selects [ListPosition.selected] (the last row if the list has shrunk past it). The screen stays where it is when
 * the row is on it, and otherwise scrolls just enough to show it.
 */
fun ListPosition.selecting(count: Int, rowsPerPage: Int): ListPosition {
    if (count == 0) return ListPosition(0, 0)
    return ListPosition(selected.coerceAtMost(count - 1), first).showingSelected(count, rowsPerPage)
}

/** The nearest screen that shows the selection and no blank rows past the end of the list. */
private fun ListPosition.showingSelected(count: Int, rowsPerPage: Int): ListPosition {
    val lastFirst = maxOf(0, count - rowsPerPage)
    return copy(first = first.coerceIn(selected - rowsPerPage + 1, selected).coerceIn(0, lastFirst))
}
