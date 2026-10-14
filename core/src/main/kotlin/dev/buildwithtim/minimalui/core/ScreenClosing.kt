package dev.buildwithtim.minimalui.core

/**
 * Whether choosing this action keeps its menu on screen, below the list or text editor it opens on top: Add to
 * collection, Add to group, Rename and Rename group, so Back returns to the menu. Every other action closes the menu.
 */
val MenuAction.keepsMenu: Boolean
    get() = this in KeptMenuActions

private val KeptMenuActions =
    setOf(MenuAction.AddToCollection, MenuAction.AddToGroup, MenuAction.Rename, MenuAction.RenameGroup)

/**
 * How many lists close after a change to the collection or group called [name], given the [shown] names left: its
 * list closes once it shows no app, and the list of names too once none shows any.
 */
fun listsClosing(
    name: String,
    shown: List<String>,
): Int = (if (name in shown) 0 else 1) + (if (shown.isEmpty()) 1 else 0)
