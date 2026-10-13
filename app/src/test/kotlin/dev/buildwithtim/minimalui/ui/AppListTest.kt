package dev.buildwithtim.minimalui.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performKeyPress
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import dev.buildwithtim.minimalui.core.LauncherApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import android.view.KeyEvent as AndroidKeyEvent

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class AppListTest {
    @get:Rule
    val rule = createComposeRule()

    private val apps = listOf("Calculator", "Calendar", "Camera").map { LauncherApp(it, "pkg.$it", "pkg.$it.Main") }

    private val opened = mutableListOf<LauncherApp>()
    private var homePresses by mutableIntStateOf(0)
    private var query by mutableStateOf("")

    private fun show(list: List<LauncherApp> = apps) {
        rule.setContent {
            MinimalTheme {
                AppList(apps = list, query = query, homePresses = homePresses, onOpen = { opened += it })
            }
        }
    }

    // Enough apps for several pages, named so they sort and read in list order.
    private val manyApps = (0 until 40).map { "App %02d".format(it) }.map { LauncherApp(it, "pkg.$it", "pkg.$it.Main") }

    private fun press(key: Key) = rule.onRoot().performKeyInput { pressKey(key) }

    /** The auto-repeat a key sends while it is held down. */
    private fun holdDown() = rule.onRoot().performKeyPress(
        KeyEvent(AndroidKeyEvent(0, 0, AndroidKeyEvent.ACTION_DOWN, AndroidKeyEvent.KEYCODE_DPAD_DOWN, 1)),
    )

    /** Labels of the rows on screen, top to bottom, after checking that each one is drawn whole. */
    private fun rowsOnScreen(): List<String> {
        val list = rule.onNode(hasScrollAction()).getUnclippedBoundsInRoot()
        val rows = rule.onAllNodes(hasClickAction())
        return List(rows.fetchSemanticsNodes().size) { rows[it] }
            .map { it.fetchSemanticsNode().config[SemanticsProperties.Text].joinToString() to it.getUnclippedBoundsInRoot() }
            .onEach { (label, row) ->
                assertTrue("$label is cut off", row.top >= list.top - Slack && row.bottom <= list.bottom + Slack)
            }
            .sortedBy { (_, row) -> row.top }
            .map { (label, _) -> label }
    }

    @Test
    fun `the first row takes focus when the apps arrive`() {
        show()

        rule.onNodeWithText("Calculator").assertIsFocused()
    }

    @Test
    fun `the arrow keys move focus between rows`() {
        show()

        press(Key.DirectionDown)
        press(Key.DirectionDown)
        rule.onNodeWithText("Camera").assertIsFocused()

        press(Key.DirectionUp)
        rule.onNodeWithText("Calendar").assertIsFocused()
    }

    @Test
    fun `Enter and D-pad centre open the focused app`() {
        show()

        press(Key.DirectionDown)
        press(Key.Enter)
        press(Key.DirectionDown)
        press(Key.DirectionCenter)

        assertEquals(listOf("Calendar", "Camera"), opened.map { it.label })
    }

    @Test
    fun `Home puts focus back on the first row`() {
        show()
        press(Key.DirectionDown)
        press(Key.DirectionDown)

        homePresses++

        rule.onNodeWithText("Calculator").assertIsFocused()
    }

    @Test
    fun `a new filter query puts focus on the first row and shows the query`() {
        show()
        press(Key.DirectionDown)

        query = "ca"

        rule.onNodeWithText("Calculator").assertIsFocused()
        rule.onNodeWithText("ca_").assertExists()
    }

    @Test
    fun `no query line shows while the query is empty`() {
        show()

        rule.onNodeWithText("_", substring = true).assertDoesNotExist()
    }

    @Test
    fun `Down past the last row wraps to the first, and Up past the first wraps to the last`() {
        show()

        press(Key.DirectionDown)
        press(Key.DirectionDown)
        press(Key.DirectionDown)
        rule.onNodeWithText("Calculator").assertIsFocused()

        press(Key.DirectionUp)
        rule.onNodeWithText("Camera").assertIsFocused()
    }

    @Test
    fun `a held Down stops at the last row`() {
        show()
        press(Key.DirectionDown)
        press(Key.DirectionDown)

        holdDown()

        rule.onNodeWithText("Camera").assertIsFocused()
    }

    @Test
    @Config(qualifiers = "w360dp-h360dp")
    fun `Right and Left turn whole pages and stop at the ends`() {
        show(manyApps)
        val page = rowsOnScreen()
        val rows = page.size
        assertEquals((0 until rows).map { "App %02d".format(it) }, page)

        press(Key.DirectionDown)
        press(Key.DirectionRight)

        rule.onNodeWithText("App %02d".format(rows + 1)).assertIsFocused()
        assertEquals((rows until 2 * rows).map { "App %02d".format(it) }, rowsOnScreen())

        repeat(manyApps.size / rows + 1) { press(Key.DirectionRight) }
        rule.onNodeWithText("App 39").assertIsFocused()
        assertEquals((40 - rows until 40).map { "App %02d".format(it) }, rowsOnScreen())

        repeat(manyApps.size / rows + 1) { press(Key.DirectionLeft) }
        rule.onNodeWithText("App 00").assertIsFocused()
        rule.onNodeWithText("App 00").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w360dp-h360dp")
    fun `Down from the bottom row scrolls one row and Up past the top wraps to the last page`() {
        show(manyApps)
        val rows = rowsOnScreen().size

        repeat(rows) { press(Key.DirectionDown) }
        rule.onNodeWithText("App %02d".format(rows)).assertIsFocused()
        rule.onNodeWithText("App 00").assertIsNotDisplayed()
        assertEquals((1..rows).map { "App %02d".format(it) }, rowsOnScreen())

        repeat(rows) { press(Key.DirectionUp) }
        rule.onNodeWithText("App 00").assertIsFocused()
        press(Key.DirectionUp)
        rule.onNodeWithText("App 39").assertIsFocused()
        rule.onNodeWithText("App 39").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w360dp-h360dp")
    fun `a square screen shows only whole rows and fills the page`() {
        show(manyApps)

        assertFillsWithWholeRows()
    }

    @Test
    @Config(qualifiers = "w360dp-h780dp")
    fun `a tall screen shows only whole rows and fills the page`() {
        show(manyApps)

        assertFillsWithWholeRows()
    }

    @Test
    @Config(qualifiers = "w360dp-h360dp")
    fun `a filter query takes one row and the rest stay whole`() {
        show(manyApps)
        val rows = rowsOnScreen().size

        query = "app"

        assertEquals(rows - 1, rowsOnScreen().size)
        rule.onNodeWithText("App 00").assertIsFocused()
    }

    private fun assertFillsWithWholeRows() {
        val list = rule.onNode(hasScrollAction()).getUnclippedBoundsInRoot()
        val rows = rowsOnScreen().size
        // Rows are at least one label line plus the pill's padding tall; as many as fit are shown.
        val minRowHeight = RowMetrics.LineHeight.value.dp + RowMetrics.PillPaddingVertical * 2
        assertEquals(((list.bottom - list.top) / minRowHeight).toInt(), rows)
        val root = rule.onRoot().getUnclippedBoundsInRoot()
        assertTrue("a whole row's worth of space is left unused", root.bottom - list.bottom < minRowHeight)
    }

    private companion object {
        /** Rounding between pixels and dp. */
        val Slack = 0.5.dp
    }
}
