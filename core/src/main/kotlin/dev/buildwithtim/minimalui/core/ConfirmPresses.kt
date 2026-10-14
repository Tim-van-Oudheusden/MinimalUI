package dev.buildwithtim.minimalui.core

/** What a list does with a confirm key's press or release (Enter, D-pad centre). */
enum class ConfirmKeyAction {
    /** Lets the row see it, so a press and the release that ends it confirm the row. */
    Pass,

    /** Takes it and does nothing: a repeat, or a release whose press the list never saw. */
    Drop,

    /** Takes it and long-presses the selected row. */
    LongPress,
}

/**
 * The confirm keys ([K]) pressed down while a list was on screen. A release counts only after such a press, so the end
 * of a long press that opened another list never confirms a row there. The first repeat of such a press is a long
 * press, where there is one; other repeats are dropped, so holding a key never confirms more than once.
 */
class ConfirmPresses<K> {
    private val pressed = mutableSetOf<K>()

    /** A press of [key]: a fresh one, or a [held] key's auto-repeat, long-pressing when the list [canLongPress]. */
    fun pressed(
        key: K,
        held: Boolean,
        canLongPress: Boolean,
    ): ConfirmKeyAction =
        when {
            !held -> {
                pressed += key
                ConfirmKeyAction.Pass
            }

            canLongPress && pressed.remove(key) -> {
                ConfirmKeyAction.LongPress
            }

            else -> {
                ConfirmKeyAction.Drop
            }
        }

    /** The release of [key]. */
    fun released(key: K): ConfirmKeyAction = if (pressed.remove(key)) ConfirmKeyAction.Pass else ConfirmKeyAction.Drop
}
