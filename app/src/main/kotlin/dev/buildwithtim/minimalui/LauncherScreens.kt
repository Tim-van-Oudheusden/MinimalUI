package dev.buildwithtim.minimalui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.buildwithtim.minimalui.core.MenuAction
import dev.buildwithtim.minimalui.core.SettingsLink
import dev.buildwithtim.minimalui.core.StatusPill
import dev.buildwithtim.minimalui.core.importSetupMenu
import dev.buildwithtim.minimalui.ui.AboutList
import dev.buildwithtim.minimalui.ui.AppList
import dev.buildwithtim.minimalui.ui.AppListState
import dev.buildwithtim.minimalui.ui.CollectionApps
import dev.buildwithtim.minimalui.ui.CollectionChoice
import dev.buildwithtim.minimalui.ui.CollectionsList
import dev.buildwithtim.minimalui.ui.ContextMenu
import dev.buildwithtim.minimalui.ui.HiddenAppsList
import dev.buildwithtim.minimalui.ui.SettingsList
import dev.buildwithtim.minimalui.ui.TextEntry

/**
 * The list on top of the launcher's [state], which alone shows, doing what [actions] and [edits] do, and exporting and
 * importing the setup through [files]. [listState] keeps the root list's selection and scroll while another list is
 * up, so it comes back with the same row selected and the same rows on screen.
 */
@Composable
internal fun LauncherScreens(
    state: LauncherState,
    actions: AppActions,
    edits: ListEdits,
    listState: AppListState,
    files: SetupFiles,
) {
    val status = state.status?.shownWith(state.stores.settings)
    when (val screen = state.screens.top) {
        is Screen.OfApp -> AppScreen(screen, state, actions, edits, listState, status)
        is Screen.OfCollection -> CollectionScreen(screen, state, actions, edits, status)
        is Screen.OfGroup -> GroupScreen(screen, state, actions, edits, status)
        is Screen.OfGroupEdit -> GroupEditScreen(screen, state, edits, status)
        is Screen.OfSettings -> SettingsScreen(screen, state, files, status)
    }
}

@Composable
private fun AppScreen(
    screen: Screen.OfApp,
    state: LauncherState,
    actions: AppActions,
    edits: ListEdits,
    listState: AppListState,
    status: StatusPill?,
) {
    when (screen) {
        Screen.Root -> {
            AppList(
                content = state.shownList(),
                query = state.query,
                homePresses = state.homePresses,
                onOpen = actions::open,
                onMenu = { actions.openMenu(it) },
                onCollections = { state.push(Screen.CollectionsList()) },
                onGroups = { state.push(Screen.GroupsList()) },
                state = listState,
                returned = state.returned,
                status = status,
                suffix = state.suffixes::get,
                paused = state::isPaused,
            )
        }

        is Screen.Menu -> {
            ContextMenu(
                title = screen.app.label,
                items = screen.items,
                onChoose = { actions.choose(screen, it) },
                onClose = { state.back() },
                status = status,
                state = screen.list,
            )
        }

        is Screen.Rename -> {
            TextEntry(
                title = stringResource(R.string.menu_rename),
                initial = screen.app.label,
                onDone = { edits.rename(screen.app, it) },
                onClose = { state.back() },
                status = status,
            )
        }
    }
}

@Composable
private fun CollectionScreen(
    screen: Screen.OfCollection,
    state: LauncherState,
    actions: AppActions,
    edits: ListEdits,
    status: StatusPill?,
) {
    val collections = state.stores.collections
    when (screen) {
        is Screen.CollectionsList -> {
            CollectionsList(
                title = stringResource(R.string.collections),
                names = collections.names(state.visibleApps),
                onOpen = { state.push(Screen.Collection(it)) },
                onClose = { state.back() },
                state = screen.list,
                status = status,
            )
        }

        is Screen.Collection -> {
            CollectionApps(
                name = screen.name,
                apps = collections.appsIn(screen.name, state.visibleApps),
                isFavourite = { it in state.stores.favourites },
                onOpen = actions::open,
                onMenu = { actions.openMenu(it, screen.name) },
                onClose = { state.back() },
                state = screen.state,
                status = status,
                suffix = state.suffixes::get,
                paused = state::isPaused,
            )
        }

        is Screen.CollectionChoice -> {
            CollectionChoice(
                title = stringResource(R.string.menu_add_collection),
                names = collections.namesWithout(screen.app, state.visibleApps),
                newLabel = stringResource(R.string.new_collection),
                onChoose = { edits.addToCollection(it, screen.app) },
                onNew = { state.push(Screen.NewCollection(screen.app)) },
                onClose = { state.back() },
                state = screen.list,
                status = status,
            )
        }

        is Screen.NewCollection -> {
            TextEntry(
                title = stringResource(R.string.new_collection),
                initial = "",
                // A blank name makes nothing.
                onDone = { name -> if (name.isNotBlank()) edits.addToCollection(name, screen.app) },
                onClose = { state.back() },
                status = status,
            )
        }
    }
}

@Composable
private fun GroupScreen(
    screen: Screen.OfGroup,
    state: LauncherState,
    actions: AppActions,
    edits: ListEdits,
    status: StatusPill?,
) {
    when (screen) {
        is Screen.GroupsList -> {
            CollectionsList(
                title = stringResource(R.string.groups),
                names = state.shownGroups.map { it.name },
                onOpen = { state.push(Screen.Group(it)) },
                onClose = { state.back() },
                state = screen.list,
                status = status,
                onMenu = edits::openGroupMenu,
            )
        }

        is Screen.Group -> {
            CollectionApps(
                name = screen.name,
                apps =
                    state.shownGroups
                        .firstOrNull { it.name == screen.name }
                        ?.apps
                        .orEmpty(),
                isFavourite = { it in state.stores.favourites },
                onOpen = actions::open,
                onMenu = { actions.openMenu(it, group = screen.name) },
                onClose = { state.back() },
                state = screen.state,
                status = status,
                suffix = state.suffixes::get,
                paused = state::isPaused,
            )
        }

        is Screen.GroupMenu -> {
            ContextMenu(
                title = screen.name,
                items = screen.items,
                onChoose = { edits.chooseForGroup(screen, it) },
                onClose = { state.back() },
                status = status,
                state = screen.list,
            )
        }
    }
}

@Composable
private fun GroupEditScreen(
    screen: Screen.OfGroupEdit,
    state: LauncherState,
    edits: ListEdits,
    status: StatusPill?,
) {
    when (screen) {
        is Screen.GroupChoice -> {
            CollectionChoice(
                title = stringResource(R.string.menu_add_group),
                names = state.stores.userGroups.namesWithout(screen.app),
                newLabel = stringResource(R.string.new_group),
                onChoose = { edits.addToGroup(it, screen.app) },
                onNew = { state.push(Screen.NewGroup(screen.app)) },
                onClose = { state.back() },
                state = screen.list,
                status = status,
            )
        }

        is Screen.NewGroup -> {
            TextEntry(
                title = stringResource(R.string.new_group),
                initial = "",
                // A blank name makes nothing.
                onDone = { name -> if (name.isNotBlank()) edits.addToGroup(name, screen.app) },
                onClose = { state.back() },
                status = status,
            )
        }

        is Screen.RenameGroup -> {
            TextEntry(
                title = stringResource(R.string.menu_rename_group),
                initial = screen.name,
                onDone = { edits.renameGroup(screen.name, it) },
                onClose = { state.back() },
                status = status,
            )
        }
    }
}

@Composable
private fun SettingsScreen(
    screen: Screen.OfSettings,
    state: LauncherState,
    files: SetupFiles,
    status: StatusPill?,
) {
    val stores = state.stores
    when (screen) {
        is Screen.SettingsList -> {
            SettingsList(
                title = stringResource(R.string.settings_entry),
                settings = stores.settings,
                onCycle = { setting, step -> stores.save(stores.settings.cycled(setting, step)) },
                onOpen = { link ->
                    when (link) {
                        SettingsLink.ExportSetup -> files.export()
                        SettingsLink.ImportSetup -> files.import()
                        SettingsLink.HiddenApps, SettingsLink.About -> state.push(Screen.Link(link))
                    }
                },
                onClose = { state.back() },
                state = screen.list,
                status = status,
            )
        }

        is Screen.Link -> {
            LinkScreen(screen, state, status)
        }

        is Screen.ImportSetup -> {
            ContextMenu(
                title = stringResource(R.string.settings_import_setup),
                items = importSetupMenu,
                onChoose = { action ->
                    state.back()
                    if (action == MenuAction.ReplaceSetup) files.replace(screen.setup)
                },
                onClose = { state.back() },
                status = status,
                state = screen.list,
            )
        }
    }
}

@Composable
private fun LinkScreen(
    screen: Screen.Link,
    state: LauncherState,
    status: StatusPill?,
) {
    val stores = state.stores
    when (screen.link) {
        SettingsLink.HiddenApps -> {
            HiddenAppsList(
                title = stringResource(R.string.settings_hidden_apps),
                apps = stores.hidden.shownFrom(state.apps.orEmpty()),
                onUnhide = { stores.save(stores.hidden.unhidden(it)) },
                onClose = { state.back() },
                state = screen.list,
                status = status,
                suffix = state.suffixes::get,
            )
        }

        SettingsLink.About -> {
            AboutList(
                version = BuildConfig.VERSION_NAME,
                commit = BuildConfig.BUILD_COMMIT,
                onClose = { state.back() },
                status = status,
            )
        }

        // These open Android's file picker from the settings list, never a list of their own.
        SettingsLink.ExportSetup, SettingsLink.ImportSetup -> {}
    }
}
