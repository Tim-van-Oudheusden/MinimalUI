package dev.buildwithtim.minimalui.core

import org.junit.Assert.assertEquals
import org.junit.Test

class ListMoveTest {
    // Pages of 8 rows; lists of 20 rows.
    private fun ListPosition.move(
        move: ListMove,
        held: Boolean = false,
        blank: (Int) -> Boolean = { false },
    ) = moved(move, held, count = 20, rowsPerPage = 8, blank = blank)

    @Test
    fun `Up and Down step one row, wrapping around on a fresh press`() {
        assertEquals(ListPosition(4, 0), ListPosition(5, 0).move(ListMove.Up))
        assertEquals(ListPosition(6, 0), ListPosition(5, 0).move(ListMove.Down))
        assertEquals(ListPosition(19, 12), ListPosition(0, 0).move(ListMove.Up))
        assertEquals(ListPosition(0, 0), ListPosition(19, 12).move(ListMove.Down))
    }

    @Test
    fun `a held Up or Down stops at the ends`() {
        assertEquals(ListPosition(0, 0), ListPosition(0, 0).move(ListMove.Up, held = true))
        assertEquals(ListPosition(19, 12), ListPosition(19, 12).move(ListMove.Down, held = true))
    }

    @Test
    fun `Page up and Page down turn a whole page and stop at the ends`() {
        assertEquals(ListPosition(10, 8), ListPosition(2, 0).move(ListMove.PageDown))
        assertEquals(ListPosition(2, 0), ListPosition(10, 8).move(ListMove.PageUp))
        assertEquals(ListPosition(0, 0), ListPosition(2, 0).move(ListMove.PageUp))
    }

    @Test
    fun `every move skips a blank row`() {
        val blank = { row: Int -> row == 4 }
        assertEquals(ListPosition(3, 0), ListPosition(5, 0).move(ListMove.Up, blank = blank))
        assertEquals(ListPosition(5, 0), ListPosition(3, 0).move(ListMove.Down, blank = blank))
    }
}
