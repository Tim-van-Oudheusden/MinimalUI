package dev.buildwithtim.minimalui.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import dev.buildwithtim.minimalui.core.Battery
import dev.buildwithtim.minimalui.core.StatusPill
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The status pill's glyphs, as drawn: Robolectric's native graphics render the Canvas, so the tests count the pixels
 * the glyphs leave in the white of the theme's text and in the warning red. The battery is shown on its own (no
 * percentage, no clock) so every white or red pixel is the glyph's.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "xxhdpi")
class StatusLineTest {
    @get:Rule
    val rule = createComposeRule()

    private var shownPill by mutableStateOf(StatusPill(wifi = false, battery = null, clock = null))
    private var shown = false

    /** How many pixels of the status line are white and how many red, for [pill]. */
    private fun colours(pill: StatusPill): Colours {
        shownPill = pill
        if (!shown) {
            rule.setContent {
                MinimalTheme {
                    // On the theme's background, as the launcher shows it.
                    Box(Modifier.background(MaterialTheme.colorScheme.background)) {
                        StatusLine(shownPill, height = 48.dp)
                    }
                }
            }
            shown = true
        }
        return Colours.of(rule.onRoot().captureToImage().asAndroidBitmap())
    }

    private fun battery(percent: Int, charging: Boolean = false) =
        colours(StatusPill(wifi = false, battery = Battery(percent, charging), clock = null, percentage = false))

    private data class Colours(val white: Int, val red: Int) {
        companion object {
            fun of(bitmap: android.graphics.Bitmap): Colours {
                val pixels = IntArray(bitmap.width * bitmap.height)
                bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                val rgb = pixels.map { Triple(it shr 16 and 0xFF, it shr 8 and 0xFF, it and 0xFF) }
                return Colours(
                    white = rgb.count { (r, g, b) -> r > 200 && g > 200 && b > 200 },
                    red = rgb.count { (r, g, b) -> r > 200 && g < 140 && b < 140 },
                )
            }
        }
    }

    @Test
    fun `the battery fills in proportion to its charge, in white while above 20 percent`() {
        val low = battery(30)
        val high = battery(90)

        assertEquals(0, low.red)
        assertEquals(0, high.red)
        assertTrue("$high should hold more white than $low", high.white > low.white)
    }

    @Test
    fun `the fill turns red at 20 percent, and the outline too at 10 percent`() {
        val above = battery(21)
        val fillLow = battery(20)
        val outlineLow = battery(10)

        assertEquals(0, above.red)
        assertTrue("the fill is red at 20%: $fillLow", fillLow.red > 0)
        assertTrue("the outline stays white at 20%: $fillLow", fillLow.white > 0)
        assertTrue("the outline is red at 10%: $outlineLow", outlineLow.red > fillLow.red)
        assertEquals("nothing stays white at 10%: $outlineLow", 0, outlineLow.white)
    }

    @Test
    fun `a charging battery shows the bolt instead of its fill, and never turns red`() {
        val nearlyEmpty = battery(5, charging = true)
        rule.onNodeWithContentDescription("Charging").assertExists()
        val full = battery(95, charging = true)
        val outlineOnly = battery(11)

        assertEquals(0, nearlyEmpty.red)
        assertEquals("the bolt does not follow the charge", nearlyEmpty, full)
        assertTrue("the bolt adds white inside the outline", nearlyEmpty.white > outlineOnly.white)
    }

    @Test
    fun `the Wi-Fi glyph is drawn while connected`() {
        colours(StatusPill(wifi = true, battery = null, clock = null))

        val glyph = Colours.of(rule.onNodeWithContentDescription("Wi-Fi").captureToImage().asAndroidBitmap())

        assertTrue("the arcs and dot are drawn: $glyph", glyph.white > 0)
        assertEquals(0, glyph.red)
    }
}
