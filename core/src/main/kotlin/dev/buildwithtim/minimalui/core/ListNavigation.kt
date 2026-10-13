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
 * stops there instead, so holding a key never overshoots (MinUI's behaviour). A [blank] row (the root list's separator)
 * is skipped; a held key stops on the last row before one at an end. The screen scrolls just enough to keep the
 * selection on it.
 */
fun ListPosition.stepped(
    step: Int,
    held: Boolean,
    count: Int,
    rowsPerPage: Int,
    blank: (Int) -> Boolean = { false },
): ListPosition {
    if (count == 0) return ListPosition(0, 0)
    val from = selected.coerceAtMost(count - 1)
    var next = from
    do {
        val target = next + step
        next = when {
            target in 0 until count -> target
            held -> from
            else -> target.mod(count)
        }
    } while (blank(next) && next != from)
    return ListPosition(next, first).showingSelected(count, rowsPerPage)
}

/**
 * Left ([direction] -1) or Right (+1): the selection and the screen both move by a whole page, so the selection keeps
 * its place on screen. Stops at the first and last rows, with no wrap. A [blank] row counts as a row; a jump that lands
 * on one goes on to the next row in its direction, or back to the nearest one at an end.
 */
fun ListPosition.paged(
    direction: Int,
    count: Int,
    rowsPerPage: Int,
    blank: (Int) -> Boolean = { false },
): ListPosition {
    if (count == 0) return ListPosition(0, 0)
    val shift = direction * rowsPerPage
    val target = (selected + shift).coerceIn(0, count - 1)
    return ListPosition(target.nearestSelectable(direction, count, blank), first + shift)
        .showingSelected(count, rowsPerPage)
}

/**
 * Selects [ListPosition.selected] (the last row if the list has shrunk past it; the next row that is not [blank] if it
 * is). The screen stays where it is when the row is on it, and otherwise scrolls just enough to show it.
 */
fun ListPosition.selecting(count: Int, rowsPerPage: Int, blank: (Int) -> Boolean = { false }): ListPosition {
    if (count == 0) return ListPosition(0, 0)
    val row = selected.coerceAtMost(count - 1).nearestSelectable(1, count, blank)
    return ListPosition(row, first).showingSelected(count, rowsPerPage)
}

/** This row if it is not [blank], else the nearest one that is not in [direction], else the other way, else itself. */
private fun Int.nearestSelectable(direction: Int, count: Int, blank: (Int) -> Boolean): Int {
    val rows = 0 until count
    fun firstFrom(step: Int) = generateSequence(this) { it + step }.takeWhile { it in rows }.firstOrNull { !blank(it) }
    return firstFrom(direction) ?: firstFrom(-direction) ?: this
}

/** The nearest screen that shows the selection and no blank rows past the end of the list. */
private fun ListPosition.showingSelected(count: Int, rowsPerPage: Int): ListPosition {
    val lastFirst = maxOf(0, count - rowsPerPage)
    return copy(first = first.coerceIn(selected - rowsPerPage + 1, selected).coerceIn(0, lastFirst))
}
