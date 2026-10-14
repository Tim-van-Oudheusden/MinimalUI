package dev.buildwithtim.minimalui.core

import org.junit.Assert.assertEquals
import org.junit.Test

class ConfirmPressesTest {
    private val presses = ConfirmPresses<String>()

    @Test
    fun `a press and its release pass through, so the row confirms on release`() {
        assertEquals(ConfirmKeyAction.Pass, presses.pressed("Enter", held = false, canLongPress = true))
        assertEquals(ConfirmKeyAction.Pass, presses.released("Enter"))
    }

    @Test
    fun `a release whose press the list never saw is dropped`() {
        assertEquals(ConfirmKeyAction.Drop, presses.released("Enter"))
    }

    @Test
    fun `a release is dropped once its press has been released`() {
        presses.pressed("Enter", held = false, canLongPress = true)
        presses.released("Enter")
        assertEquals(ConfirmKeyAction.Drop, presses.released("Enter"))
    }

    @Test
    fun `the first repeat of a press is a long press, and the repeats and release after it are dropped`() {
        presses.pressed("Enter", held = false, canLongPress = true)
        assertEquals(ConfirmKeyAction.LongPress, presses.pressed("Enter", held = true, canLongPress = true))
        assertEquals(ConfirmKeyAction.Drop, presses.pressed("Enter", held = true, canLongPress = true))
        assertEquals(ConfirmKeyAction.Drop, presses.released("Enter"))
    }

    @Test
    fun `a repeat whose press the list never saw is no long press`() {
        assertEquals(ConfirmKeyAction.Drop, presses.pressed("Enter", held = true, canLongPress = true))
    }

    @Test
    fun `without a long press, repeats are dropped and the release still confirms`() {
        presses.pressed("Enter", held = false, canLongPress = false)
        assertEquals(ConfirmKeyAction.Drop, presses.pressed("Enter", held = true, canLongPress = false))
        assertEquals(ConfirmKeyAction.Pass, presses.released("Enter"))
    }

    @Test
    fun `each confirm key is tracked on its own`() {
        presses.pressed("Enter", held = false, canLongPress = true)
        assertEquals(ConfirmKeyAction.Drop, presses.released("DirectionCenter"))
        assertEquals(ConfirmKeyAction.Pass, presses.released("Enter"))
    }
}
