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
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performKeyPress
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import dev.buildwithtim.minimalui.core.Battery
import dev.buildwithtim.minimalui.core.LauncherApp
import dev.buildwithtim.minimalui.core.ListContent
import dev.buildwithtim.minimalui.core.StatusPill
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
    private var status by mutableStateOf<StatusPill?>(null)

    private fun show(list: List<LauncherApp> = apps) = show(ListContent.Apps(list))

    private fun show(content: ListContent) {
        rule.setContent {
            MinimalTheme {
                AppList(
                    content = content,
                    query = query,
                    homePresses = homePresses,
                    onOpen = { opened += it },
                    status = status,
                )
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

    @Test
    fun `while the apps load the screen stays blank`() {
        show(ListContent.Loading)

        assertEquals(0, rule.onAllNodes(hasText("", substring = true)).fetchSemanticsNodes().size)
    }

    @Test
    fun `no apps at all shows a centred message`() {
        show(ListContent.NoApps)

        assertCentred(rule.onNodeWithText("No apps").getUnclippedBoundsInRoot())
    }

    @Test
    fun `a query with no match keeps the query line and shows a centred message`() {
        query = "x"
        show(ListContent.NoMatch)

        val queryLine = rule.onNodeWithText("x_").getUnclippedBoundsInRoot()
        val message = rule.onNodeWithText("No match").getUnclippedBoundsInRoot()
        assertCentred(message)
        assertTrue("the message covers the query line", queryLine.bottom <= message.top)
    }

    private val pill = StatusPill(wifi = false, battery = Battery(57, charging = false), clock = "9:41")

    @Test
    fun `the status pill shows the battery percentage and the clock in the top-right corner`() {
        status = pill
        show()

        val shown = rule.onNodeWithText("57%").assertIsDisplayed().getUnclippedBoundsInRoot()
        rule.onNodeWithText("9:41").assertIsDisplayed()
        val root = rule.onRoot().getUnclippedBoundsInRoot()
        assertTrue("the pill is not at the right edge", root.right - shown.right <= RowMetrics.ListPaddingHorizontal + Slack)
        assertTrue("the pill is not at the top", shown.top - root.top < shown.bottom - shown.top)
    }

    @Test
    fun `the Wi-Fi glyph shows only while connected`() {
        status = pill
        show()
        rule.onNodeWithContentDescription("Wi-Fi", useUnmergedTree = true).assertDoesNotExist()

        status = pill.copy(wifi = true)

        rule.onNodeWithContentDescription("Wi-Fi", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `a charging battery says so`() {
        status = pill.copy(battery = Battery(40, charging = true))
        show()

        rule.onNodeWithContentDescription("Charging", useUnmergedTree = true).assertExists()
        rule.onNodeWithText("40%").assertExists()
    }

    @Test
    @Config(qualifiers = "w360dp-h360dp")
    fun `the status pill takes the top row, no row overlaps it, and the rest stay whole`() {
        show(manyApps)
        val rows = rowsOnScreen().size

        status = pill

        assertEquals(rows - 1, rowsOnScreen().size)
        val shown = rule.onNodeWithText("57%").getUnclippedBoundsInRoot()
        val list = rule.onNode(hasScrollAction()).getUnclippedBoundsInRoot()
        assertTrue("a row overlaps the status pill", list.top >= shown.bottom - Slack)
        assertFillsWithWholeRows()
        rule.onNodeWithText("App 00").assertIsFocused()
    }

    @Test
    @Config(qualifiers = "w360dp-h360dp")
    fun `the query line sits below the status pill`() {
        status = pill
        query = "app"
        show(manyApps)

        val shown = rule.onNodeWithText("57%").getUnclippedBoundsInRoot()
        val queryLine = rule.onNodeWithText("app_").getUnclippedBoundsInRoot()
        assertTrue("the query line overlaps the status pill", queryLine.top >= shown.bottom - Slack)
        assertFillsWithWholeRows()
    }

    @Test
    fun `while the apps load the status pill stays hidden too`() {
        status = pill
        show(ListContent.Loading)

        assertEquals(0, rule.onAllNodes(hasText("", substring = true)).fetchSemanticsNodes().size)
    }

    /** [message] sits in the middle of the screen. */
    private fun assertCentred(message: DpRect) {
        val root = rule.onRoot().getUnclippedBoundsInRoot()
        assertEquals(0f, (message.left + message.right - root.left - root.right).value / 2, Slack.value)
        assertEquals(0f, (message.top + message.bottom - root.top - root.bottom).value / 2, Slack.value)
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
