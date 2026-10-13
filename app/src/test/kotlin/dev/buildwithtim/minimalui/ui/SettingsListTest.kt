package dev.buildwithtim.minimalui.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import dev.buildwithtim.minimalui.core.Battery
import dev.buildwithtim.minimalui.core.Setting
import dev.buildwithtim.minimalui.core.Settings
import dev.buildwithtim.minimalui.core.SettingsLink
import dev.buildwithtim.minimalui.core.StatusPill
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class SettingsListTest {
    @get:Rule
    val rule = createComposeRule()

    private val cycled = mutableListOf<Pair<Setting, Int>>()
    private val opened = mutableListOf<SettingsLink>()
    private var closed = 0

    private fun show(settings: Settings = Settings(), status: StatusPill? = null) {
        rule.setContent {
            MinimalTheme {
                SettingsList(
                    title = "MinimalUI settings",
                    settings = settings,
                    onCycle = { setting, step -> cycled += setting to step },
                    onOpen = { opened += it },
                    onClose = { closed++ },
                    state = PillListState(),
                    status = status,
                )
            }
        }
    }

    private fun press(key: Key) = rule.onRoot().performKeyInput { pressKey(key) }

    @Test
    fun `each setting shows its value at the right end of its row`() {
        show(Settings().cycled(Setting.BatteryPercentage, 1))

        val label = rule.onNodeWithText("Battery percentage", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val value = rule.onNodeWithText("Off", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val root = rule.onRoot().getUnclippedBoundsInRoot()
        assertTrue(value.left > label.right)
        assertTrue(root.right - value.right < root.right / 10)
        assertEquals(label.top, value.top)
        rule.onNodeWithText("About", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `Left and Right on a link row neither cycle anything nor open it, and Enter opens it`() {
        show()
        press(Key.DirectionUp)
        rule.onNodeWithText("About").assertIsFocused()

        press(Key.DirectionLeft)
        press(Key.DirectionRight)
        rule.onNodeWithText("About").assertIsFocused()
        assertEquals(emptyList<Pair<Setting, Int>>(), cycled)
        assertEquals(emptyList<SettingsLink>(), opened)

        press(Key.Enter)
        assertEquals(listOf(SettingsLink.About), opened)
    }

    @Test
    fun `Left cycles back, Right and Enter forward, and Esc closes`() {
        show()

        press(Key.DirectionLeft)
        press(Key.DirectionRight)
        press(Key.Enter)
        press(Key.Escape)

        assertEquals(listOf(Setting.Clock to -1, Setting.Clock to 1, Setting.Clock to 1), cycled)
        assertEquals(1, closed)
    }

    @Test
    fun `the status pill stays at the top, above the title`() {
        show(status = StatusPill(wifi = false, battery = Battery(57, charging = false), clock = "9:41"))

        val pill = rule.onNodeWithText("57%").getUnclippedBoundsInRoot()
        val title = rule.onNodeWithText("MinimalUI settings").getUnclippedBoundsInRoot()
        assertTrue(pill.bottom <= title.top)
    }
}
