package dev.buildwithtim.minimalui.core

/** A key the hint row names (#74), drawn as its [glyph]: the keyboard's own symbols, since pad letters mean nothing. */
enum class HintKey(val glyph: String) { Menu("☰"), Delete("⌫"), Back("⎋"), Enter("⏎") }

/** What a hinted key does on the screen it is hinted on, drawn as an upper-case word after the key. */
enum class HintAction { Menu, Delete, Back, Cancel, Open, Select, Change, Unhide, Save }

/** One key and what it does. */
data class Hint(val key: HintKey, val action: HintAction)

/**
 * The hint row at the bottom of every screen (MinUI's): a [left] group at the left edge and the [right] (main) group
 * at the right edge, each in order. Either may be empty.
 */
data class Hints(val left: List<Hint> = emptyList(), val right: List<Hint> = emptyList())

/** The hints that fit a screen [widthDp] wide: under 320 dp only the right group, the main one, stays (as in MinUI). */
fun Hints.fitting(widthDp: Int): Hints = if (widthDp < NarrowWidthDp) copy(left = emptyList()) else this

private const val NarrowWidthDp = 320

/** What a screen shows that the hints depend on. */
sealed interface HintedScreen {
    /** The root list: what it shows ([content]), the typed [query] and the [selected] row, if any. */
    data class Root(val content: ListContent, val query: String, val selected: RootEntry?) : HintedScreen

    /** A list opened from the root: a collection's or a group's apps ([hasMenu]), the collections, the groups. */
    data class SubList(val hasMenu: Boolean, val isEmpty: Boolean) : HintedScreen

    /** An app's or a group's menu. */
    data object Menu : HintedScreen

    /** Where to add an app: the collections or groups, and a new one. */
    data object Choice : HintedScreen

    /** MinimalUI's settings, with the [row] selected. */
    data class Settings(val row: SettingsRow) : HintedScreen

    /** The About list, with nothing to choose. */
    data object About : HintedScreen

    /** The hidden apps, Enter showing one again. */
    data class HiddenApps(val isEmpty: Boolean) : HintedScreen

    /** The text editor, for a new name. */
    data object Editor : HintedScreen
}

private val MenuHint = Hint(HintKey.Menu, HintAction.Menu)
private val BackHint = Hint(HintKey.Back, HintAction.Back)

private fun enter(action: HintAction) = Hint(HintKey.Enter, action)

/**
 * The keys to hint on [screen], each one bound there. The root hints Menu only on an app's row and Backspace only
 * while there is a query; nothing goes back from the root, and nothing at all is hinted while the apps load. A list
 * opened from it adds Esc (or Back); with no row, Enter and Menu do nothing, so they go unhinted.
 */
fun buttonHints(screen: HintedScreen): Hints = when (screen) {
    is HintedScreen.Root -> rootHints(screen)
    is HintedScreen.SubList -> Hints(
        left = listOfNotNull(MenuHint.takeIf { screen.hasMenu && !screen.isEmpty }),
        right = listOfNotNull(BackHint, enter(HintAction.Open).takeUnless { screen.isEmpty }),
    )
    HintedScreen.Menu, HintedScreen.Choice -> Hints(right = listOf(BackHint, enter(HintAction.Select)))
    is HintedScreen.Settings -> Hints(
        right = listOf(
            BackHint,
            enter(if (screen.row is SettingsRow.Value) HintAction.Change else HintAction.Open),
        ),
    )
    HintedScreen.About -> Hints(right = listOf(BackHint))
    is HintedScreen.HiddenApps -> Hints(
        right = listOfNotNull(BackHint, enter(HintAction.Unhide).takeUnless { screen.isEmpty }),
    )
    HintedScreen.Editor -> Hints(right = listOf(Hint(HintKey.Back, HintAction.Cancel), enter(HintAction.Save)))
}

private fun rootHints(root: HintedScreen.Root): Hints {
    if (root.content == ListContent.Loading) return Hints()
    val left = when {
        root.query.isNotEmpty() -> Hint(HintKey.Delete, HintAction.Delete)
        root.selected?.app != null -> MenuHint
        else -> null
    }
    val right = enter(HintAction.Open).takeIf { root.content is ListContent.Apps }
    return Hints(left = listOfNotNull(left), right = listOfNotNull(right))
}
