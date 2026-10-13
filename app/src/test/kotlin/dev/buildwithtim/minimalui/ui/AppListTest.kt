package dev.buildwithtim.minimalui.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import dev.buildwithtim.minimalui.core.LauncherApp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

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

    private fun press(key: Key) = rule.onRoot().performKeyInput { pressKey(key) }

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
}
