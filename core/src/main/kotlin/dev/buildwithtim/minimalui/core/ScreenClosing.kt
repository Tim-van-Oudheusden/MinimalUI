package dev.buildwithtim.minimalui.core

/**
 * Whether choosing this context-menu action keeps the menu on screen, below the list or text editor it opens on top:
 * Add to collection, Add to group and Rename, so Back returns to the menu. Every other action closes the menu. A
 * group's menu keeps itself below the editor Rename group opens (ListEdits.chooseForGroup).
 */
val MenuAction.keepsMenu: Boolean
    get() = this in KeptMenuActions

private val KeptMenuActions = setOf(MenuAction.AddToCollection, MenuAction.AddToGroup, MenuAction.Rename)

/**
 * How many lists close after a change to the collection or group called [name], given the [shown] names left: its
 * list closes once it shows no app, and the list of names too once none shows any.
 */
fun listsClosing(
    name: String,
    shown: List<String>,
): Int = (if (name in shown) 0 else 1) + (if (shown.isEmpty()) 1 else 0)
