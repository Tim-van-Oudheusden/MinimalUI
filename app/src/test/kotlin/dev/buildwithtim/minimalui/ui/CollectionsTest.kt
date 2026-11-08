package dev.buildwithtim.minimalui.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.swipe
import dev.buildwithtim.minimalui.core.LauncherApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

/**
 * [CollectionApps] on its own. Touch scrolling doesn't move [PillListState.position], so the list's
 * [PillListState.positionAt] has to read the live scroll, or a list that follows its selection jumps back to where the
 * keys last left it.
 */
@RunWith(RobolectricTestRunner::class)
class CollectionsTest {
    @get:Rule
    val rule = createComposeRule()

    private fun app(label: String) = LauncherApp(label, "pkg.$label", "pkg.$label.Main")

    // Enough apps for several pages, named so they sort and read in list order.
    private var apps by mutableStateOf((0 until 40).map { app(rowLabel(it)) })

    private fun rowLabel(row: Int) = "App %02d".format(Locale.ROOT, row)

    private fun show() =
        rule.setContent {
            val state = remember { CollectionState() }
            MinimalTheme {
                CollectionApps(
                    name = "games",
                    apps = apps,
                    isFavourite = { false },
                    onOpen = {},
                    onMenu = {},
                    onClose = {},
                    state = state,
                )
            }
        }

    private fun press(key: Key) = rule.onRoot().performKeyInput { pressKey(key) }

    /** Labels of the list's rows on screen, top to bottom. */
    private fun rowsOnScreen(): List<String> {
        val rows = rule.onAllNodes(hasClickAction() and hasAnyAncestor(hasScrollAction()))
        return List(rows.fetchSemanticsNodes().size) { rows[it] }
            .filter { it.isDisplayed() }
            .map { it.fetchSemanticsNode().config[SemanticsProperties.Text].joinToString() }
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp")
    fun `an app installed above the selected one leaves a touch scrolled list where it was`() {
        show()
        repeat(3) { press(Key.DirectionDown) }
        val selected = rowLabel(3)
        rule.onNodeWithText(selected).assertIsFocused()

        // Slowly, so it doesn't fling; touch scrolling doesn't move the selection.
        val row =
            rule
                .onNodeWithText(rowLabel(0))
                .fetchSemanticsNode()
                .size.height
        rule.onNode(hasScrollAction()).performTouchInput {
            swipe(center, center - Offset(0f, row * 2f), durationMillis = 2000)
        }
        rule.waitForIdle()
        val page = rowsOnScreen()
        assertTrue(page.first() != rowLabel(0) && selected in page)

        // An app installed that sorts above the selected one (a group lists its apps in the main list's order).
        apps = listOf(app("App 00a")) + apps
        rule.waitForIdle()

        rule.onNodeWithText(selected).assertIsFocused()
        assertEquals(page.first(), rowsOnScreen().first())
    }
}
