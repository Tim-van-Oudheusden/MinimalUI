package dev.buildwithtim.minimalui.core

import org.junit.Assert.assertEquals
import org.junit.Test

class ListNavigationTest {

    // Pages of 8 rows; lists of 20 apps unless a test says otherwise.
    private fun up(from: ListPosition, held: Boolean = false, count: Int = 20) = from.stepped(-1, held, count, 8)
    private fun down(from: ListPosition, held: Boolean = false, count: Int = 20) = from.stepped(1, held, count, 8)
    private fun left(from: ListPosition, count: Int = 20) = from.paged(-1, count, 8)
    private fun right(from: ListPosition, count: Int = 20) = from.paged(1, count, 8)

    private fun at(selected: Int, first: Int) = ListPosition(selected, first)

    @Test
    fun `only whole rows fit and the leftover height is shared between them`() {
        // A 720 px square screen and a tall one, with rows at least 88 px tall.
        assertEquals(ListRhythm(rows = 8, rowHeight = 90), listRhythm(availableHeight = 720, minRowHeight = 88))
        assertEquals(ListRhythm(rows = 22, rowHeight = 90), listRhythm(availableHeight = 2000, minRowHeight = 88))
    }

    @Test
    fun `rows are never shorter than their content, and at least one row is shown`() {
        assertEquals(ListRhythm(rows = 1, rowHeight = 88), listRhythm(availableHeight = 50, minRowHeight = 88))
        assertEquals(ListRhythm(rows = 1, rowHeight = 88), listRhythm(availableHeight = 0, minRowHeight = 88))
    }

    @Test
    fun `Up and Down move one row and scroll just enough to keep it on screen`() {
        assertEquals(at(4, 0), down(at(3, 0)))
        assertEquals(at(8, 1), down(at(7, 0)))
        assertEquals(at(4, 4), up(at(5, 5), held = true))
    }

    @Test
    fun `a fresh press past either end wraps around to the other end`() {
        assertEquals(at(0, 0), down(at(19, 12)))
        assertEquals(at(19, 12), up(at(0, 0)))
    }

    @Test
    fun `a held key stops at either end instead of wrapping`() {
        assertEquals(at(19, 12), down(at(19, 12), held = true))
        assertEquals(at(0, 0), up(at(0, 0), held = true))
    }

    @Test
    fun `a page jump moves the selection and the screen by a whole page`() {
        assertEquals(at(11, 8), right(at(3, 0), count = 30))
        assertEquals(at(3, 0), left(at(11, 8), count = 30))
    }

    @Test
    fun `page jumps stop at the first and last rows without wrapping`() {
        assertEquals(at(19, 12), right(at(11, 8)))
        assertEquals(at(19, 12), right(at(19, 12)))
        assertEquals(at(0, 0), left(at(2, 0)))
        assertEquals(at(0, 0), left(at(0, 0)))
    }

    @Test
    fun `in a list shorter than one page, page jumps go to the first and last rows`() {
        assertEquals(at(2, 0), right(at(0, 0), count = 3))
        assertEquals(at(0, 0), left(at(2, 0), count = 3))
        assertEquals(at(0, 0), down(at(2, 0), count = 3))
    }

    @Test
    fun `a selection left past the end of a shrunken list moves on from its last row`() {
        assertEquals(at(3, 0), up(at(9, 2), count = 5))
        assertEquals(at(0, 0), down(at(9, 2), count = 5))
    }

    @Test
    fun `an empty list has nothing to move to`() {
        assertEquals(at(0, 0), down(at(4, 1), count = 0))
        assertEquals(at(0, 0), right(at(4, 1), count = 0))
    }
}
