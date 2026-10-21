package dev.buildwithtim.minimalui

import dev.buildwithtim.minimalui.core.LauncherApp
import dev.buildwithtim.minimalui.core.LauncherSetup
import dev.buildwithtim.minimalui.core.MenuItem
import dev.buildwithtim.minimalui.core.ScreenStack
import dev.buildwithtim.minimalui.core.SettingsLink
import dev.buildwithtim.minimalui.ui.CollectionState
import dev.buildwithtim.minimalui.ui.PillListState

/**
 * A list on [ScreenStack], each holding what it needs to show again as it was left. The screens come in families,
 * each drawn by its own composable in LauncherScreens.kt.
 */
internal sealed interface Screen {
    /** The root list, an app's menu and the editor renaming the app. */
    sealed interface OfApp : Screen

    /** The collections (#68), one collection's apps, and choosing or naming a collection for an app. */
    sealed interface OfCollection : Screen

    /** The groups (#69), one group's apps and a group's menu. */
    sealed interface OfGroup : Screen

    /** Choosing or naming a user group for an app, and renaming a user group. */
    sealed interface OfGroupEdit : Screen

    /** MinimalUI's settings, the lists their links open, and the question an imported setup asks. */
    sealed interface OfSettings : Screen

    /** The app list (its selection is kept by the activity's content, which outlives every other screen). */
    data object Root : OfApp

    /**
     * The context menu of [app], with its [items] as they were when it opened, and the [collection] or [group] it was
     * opened in; its selection is kept while a list opened from it is up.
     */
    class Menu(
        val app: LauncherApp,
        val items: List<MenuItem>,
        val collection: String?,
        val group: String?,
    ) : OfApp {
        val list = PillListState()
    }

    /** A new name for [app] (#72), in the text editor, which starts with the name it is listed under. */
    class Rename(
        val app: LauncherApp,
    ) : OfApp

    /** The names of the collections, from the root's Collections row (#68). */
    class CollectionsList : OfCollection {
        val list = PillListState()
    }

    /** The apps of the collection called [name], in the user's order. */
    class Collection(
        val name: String,
    ) : OfCollection {
        val state = CollectionState()
    }

    /** The collections [app] can be added to, and a new one. */
    class CollectionChoice(
        val app: LauncherApp,
    ) : OfCollection {
        val list = PillListState()
    }

    /** A new collection's name for [app], in the text editor. */
    class NewCollection(
        val app: LauncherApp,
    ) : OfCollection

    /** The groups (#69), automatic and the user's, from the root's Groups row. */
    class GroupsList : OfGroup {
        val list = PillListState()
    }

    /** The apps of the group called [name], in the main list's order. */
    class Group(
        val name: String,
    ) : OfGroup {
        val state = CollectionState()
    }

    /** The menu of the group called [name]: its [items] rename or delete it. */
    class GroupMenu(
        val name: String,
        val items: List<MenuItem>,
    ) : OfGroup {
        val list = PillListState()
    }

    /** The user groups [app] can be added to, and a new one. */
    class GroupChoice(
        val app: LauncherApp,
    ) : OfGroupEdit {
        val list = PillListState()
    }

    /** A new group's name for [app], in the text editor. */
    class NewGroup(
        val app: LauncherApp,
    ) : OfGroupEdit

    /** A new name for the user group called [name], in the text editor, which starts with it. */
    class RenameGroup(
        val name: String,
    ) : OfGroupEdit

    /** MinimalUI's settings, opened fresh on their first row each time, kept while a link's list is up. */
    class SettingsList : OfSettings {
        val list = PillListState()
    }

    /** The list a settings link opens, on its first row each time it opens. */
    class Link(
        val link: SettingsLink,
    ) : OfSettings {
        val list = PillListState()
    }

    /** The question a [setup] read from a file asks before it replaces the current one (#208). */
    class ImportSetup(
        val setup: LauncherSetup,
    ) : OfSettings {
        val list = PillListState()
    }
}
