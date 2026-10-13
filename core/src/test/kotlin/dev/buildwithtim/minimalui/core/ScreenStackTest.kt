package dev.buildwithtim.minimalui.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenStackTest {

    // A screen that keeps its own selection, as each list on screen does.
    private class Screen(val name: String, var selected: Int = 0)

    private val root = Screen("root")

    @Test
    fun `the launcher starts on the root`() {
        val stack = ScreenStack(root)

        assertSame(root, stack.top)
        assertTrue(stack.isRoot)
    }

    @Test
    fun `a pushed screen takes the parent's place on screen`() {
        val menu = Screen("menu")
        val stack = ScreenStack(root).pushed(menu)

        assertSame(menu, stack.top)
        assertFalse(stack.isRoot)
    }

    @Test
    fun `going back shows the parent again, with the selection it had`() {
        val settings = Screen("settings", selected = 3)
        val stack = ScreenStack(root).pushed(settings).pushed(Screen("about"))

        val back = stack.popped()

        assertSame(settings, back.top)
        assertEquals(3, back.top.selected)
        assertSame(root, back.popped().top)
    }

    @Test
    fun `going back on the root stays on the root`() {
        val stack = ScreenStack(root)

        assertEquals(stack, stack.popped())
    }

    @Test
    fun `Home closes every screen above the root`() {
        val home = ScreenStack(root).pushed(Screen("settings")).pushed(Screen("about")).home()

        assertEquals(ScreenStack(root), home)
    }
}
