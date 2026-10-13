package dev.buildwithtim.minimalui.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FilterQueryTest {

    @Test
    fun `typing a letter or digit appends it to the query`() {
        assertEquals("map", "ma".plusTyped('p'.code))
        assertEquals("k9", "k".plusTyped('9'.code))
        assertEquals("é", "".plusTyped('é'.code))
    }

    @Test
    fun `keys that cannot be part of a query leave it to their usual job`() {
        assertNull("ma".plusTyped(' '.code))
        assertNull("ma".plusTyped('\n'.code))
        assertNull("ma".plusTyped('.'.code))
        assertNull("ma".plusTyped(0))
    }

    @Test
    fun `backspace removes the last typed character`() {
        assertEquals("ma", "map".minusLastTyped())
        assertEquals("", "m".minusLastTyped())
    }

    @Test
    fun `backspace on an empty query is left to its usual job`() {
        assertNull("".minusLastTyped())
    }
}
