package dev.buildwithtim.minimalui.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performKeyPress
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import dev.buildwithtim.minimalui.core.Battery
import dev.buildwithtim.minimalui.core.MenuAction
import dev.buildwithtim.minimalui.core.MenuItem
import dev.buildwithtim.minimalui.core.StatusPill
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import android.view.KeyEvent as AndroidKeyEvent

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class ContextMenuTest {
    @get:Rule
    val rule = createComposeRule()

    private val chosen = mutableListOf<MenuAction>()
    private var closed = 0

    private val all =
        listOf(
            MenuItem(MenuAction.AppInfo),
            MenuItem(MenuAction.Uninstall),
            MenuItem(MenuAction.RemoveFromRecents),
            MenuItem(MenuAction.ClearRecents),
        )

    private fun show(
        items: List<MenuItem> = all,
        status: StatusPill? = null,
    ) {
        rule.setContent {
            MinimalTheme {
                ContextMenu(
                    title = "Camera",
                    items = items,
                    onChoose = { chosen += it },
                    onClose = { closed++ },
                    status = status,
                )
            }
        }
    }

    private fun press(key: Key) = rule.onRoot().performKeyInput { pressKey(key) }

    private fun send(
        action: Int,
        keyCode: Int,
        repeat: Int = 0,
    ) = rule.onRoot().performKeyPress(KeyEvent(AndroidKeyEvent(0, 0, action, keyCode, repeat)))

    @Test
    fun `the app's name heads the menu, and the first action takes focus`() {
        show()

        val title = rule.onNodeWithText("Camera").getUnclippedBoundsInRoot()
        val first = rule.onNodeWithText("App info").assertIsFocused().getUnclippedBoundsInRoot()
        assertTrue("the title is not above the actions", title.bottom <= first.top)
        val rows = rule.onAllNodes(hasClickAction() and hasAnyAncestor(hasScrollAction()))
        assertEquals(all.size, rows.fetchSemanticsNodes().size)
    }

    @Test
    fun `every action is listed by name`() {
        show()

        listOf("App info", "Uninstall", "Remove from recently used", "Clear recently used").forEach {
            rule.onNodeWithText(it).assertIsEnabled()
        }
    }

    @Test
    fun `the arrow keys move between actions and Enter chooses one`() {
        show()

        press(Key.DirectionDown)
        press(Key.DirectionDown)
        rule.onNodeWithText("Remove from recently used").assertIsFocused()
        press(Key.Enter)
        press(Key.DirectionUp)
        press(Key.DirectionCenter)

        assertEquals(listOf(MenuAction.RemoveFromRecents, MenuAction.Uninstall), chosen)
    }

    @Test
    fun `touch chooses an action too`() {
        show()

        rule.onNodeWithText("Clear recently used").performClick()

        assertEquals(listOf(MenuAction.ClearRecents), chosen)
    }

    @Test
    fun `a disabled action is listed but does nothing`() {
        show(listOf(MenuItem(MenuAction.AppInfo), MenuItem(MenuAction.Uninstall, enabled = false)))

        press(Key.DirectionDown)
        rule.onNodeWithText("Uninstall").assertIsFocused().assertIsNotEnabled()
        press(Key.Enter)
        rule.onNodeWithText("Uninstall").performClick()

        assertEquals(emptyList<MenuAction>(), chosen)
    }

    @Test
    fun `Esc closes the menu`() {
        show()

        press(Key.Escape)

        assertEquals(1, closed)
        assertEquals(emptyList<MenuAction>(), chosen)
    }

    @Test
    fun `the Enter still held from the long press that opened the menu chooses nothing`() {
        show()

        send(AndroidKeyEvent.ACTION_DOWN, AndroidKeyEvent.KEYCODE_ENTER, repeat = 2)
        send(AndroidKeyEvent.ACTION_UP, AndroidKeyEvent.KEYCODE_ENTER)

        assertEquals(emptyList<MenuAction>(), chosen)
        rule.onNodeWithText("App info").assertIsFocused()
    }

    @Test
    fun `the status pill stays at the top`() {
        show(status = StatusPill(wifi = false, battery = Battery(57, charging = false), clock = "9:41"))

        val pill = rule.onNodeWithText("57%").getUnclippedBoundsInRoot()
        val title = rule.onNodeWithText("Camera").getUnclippedBoundsInRoot()
        assertTrue("the title overlaps the status pill", title.top >= pill.bottom - Slack)
    }

    private companion object {
        /** Rounding between pixels and dp. */
        val Slack = 0.5.dp
    }
}
