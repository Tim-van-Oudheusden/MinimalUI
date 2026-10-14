package dev.buildwithtim.minimalui.core

/**
 * The one-list screens on screen, MinUI-style (#70): the [root] list at the bottom, and each list opened from it (a
 * context menu, the settings, a link's list from those, later a collection or a group) on top of the one it was
 * opened from. Only the [top] one shows. Each screen keeps its own selection, so going back shows the parent as it
 * was left.
 */
data class ScreenStack<S>(
    val root: S,
    private val above: List<S> = emptyList(),
) {
    val top: S get() = above.lastOrNull() ?: root

    val isRoot: Boolean get() = above.isEmpty()

    /** [screen] opened from the top one. */
    fun pushed(screen: S): ScreenStack<S> = copy(above = above + screen)

    /** Back or Esc: the top screen closes and the one below shows again. The root has nothing below and stays. */
    fun popped(): ScreenStack<S> = copy(above = above.dropLast(1))

    /** Home: every screen above the root closes. */
    fun home(): ScreenStack<S> = ScreenStack(root)
}
