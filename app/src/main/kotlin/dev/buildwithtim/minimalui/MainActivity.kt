package dev.buildwithtim.minimalui

import android.app.ActivityOptions
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.LauncherApps
import android.graphics.Rect
import android.os.Bundle
import android.os.UserHandle
import android.os.UserManager
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import dev.buildwithtim.minimalui.core.Aliases
import dev.buildwithtim.minimalui.core.AppCollections
import dev.buildwithtim.minimalui.core.AppFacts
import dev.buildwithtim.minimalui.core.AppGroup
import dev.buildwithtim.minimalui.core.CollectionPlace
import dev.buildwithtim.minimalui.core.Favourites
import dev.buildwithtim.minimalui.core.HiddenApps
import dev.buildwithtim.minimalui.core.LatestLoader
import dev.buildwithtim.minimalui.core.LauncherApp
import dev.buildwithtim.minimalui.core.ListContent
import dev.buildwithtim.minimalui.core.MenuAction
import dev.buildwithtim.minimalui.core.MenuItem
import dev.buildwithtim.minimalui.core.RecentApps
import dev.buildwithtim.minimalui.core.ScreenStack
import dev.buildwithtim.minimalui.core.Setting
import dev.buildwithtim.minimalui.core.Settings
import dev.buildwithtim.minimalui.core.SettingsLink
import dev.buildwithtim.minimalui.core.StatusPill
import dev.buildwithtim.minimalui.core.UserGroups
import dev.buildwithtim.minimalui.core.appGroups
import dev.buildwithtim.minimalui.core.component
import dev.buildwithtim.minimalui.core.contextMenu
import dev.buildwithtim.minimalui.core.duplicateSuffixes
import dev.buildwithtim.minimalui.core.groupMenu
import dev.buildwithtim.minimalui.core.isLauncherSettings
import dev.buildwithtim.minimalui.core.isWorkApp
import dev.buildwithtim.minimalui.core.launcherSettings
import dev.buildwithtim.minimalui.core.listContent
import dev.buildwithtim.minimalui.core.minusLastTyped
import dev.buildwithtim.minimalui.core.plusTyped
import dev.buildwithtim.minimalui.core.rowApps
import dev.buildwithtim.minimalui.core.withSystemSettings
import dev.buildwithtim.minimalui.ui.AboutList
import dev.buildwithtim.minimalui.ui.AppList
import dev.buildwithtim.minimalui.ui.CollectionApps
import dev.buildwithtim.minimalui.ui.CollectionChoice
import dev.buildwithtim.minimalui.ui.CollectionState
import dev.buildwithtim.minimalui.ui.CollectionsList
import dev.buildwithtim.minimalui.ui.ContextMenu
import dev.buildwithtim.minimalui.ui.HiddenAppsList
import dev.buildwithtim.minimalui.ui.MinimalTheme
import dev.buildwithtim.minimalui.ui.PillListState
import dev.buildwithtim.minimalui.ui.Returned
import dev.buildwithtim.minimalui.ui.SettingsList
import dev.buildwithtim.minimalui.ui.TextEntry
import dev.buildwithtim.minimalui.ui.rememberAppListState
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    // The installed apps with their system labels, null until the first load arrives, so loading (a blank screen) is
    // told apart from having no apps.
    private var loaded by mutableStateOf<Loaded?>(null)

    // The names the user gave apps (#72).
    private val aliasesStore by lazy { AliasesStore(this) }
    private var aliases by mutableStateOf(Aliases())

    /**
     * The installed apps as every list shows them: renamed ones by their alias, sorted by it (#72). Every list, the
     * filter, the recents, favourites, collections and hidden apps take their apps from here.
     */
    private val apps: List<LauncherApp>? by derivedStateOf {
        loaded?.let { aliases.labelled(it.apps, it.settingsPackage) }
    }

    /** What tells apart apps listed under the same name (#72): a dim suffix after the name. */
    private val suffixes: Map<LauncherApp, String> by derivedStateOf { duplicateSuffixes(apps.orEmpty()) }

    private var homePresses by mutableIntStateOf(0)
    private var query by mutableStateOf("")
    private var returned by mutableStateOf<Returned?>(null)
    private var status by mutableStateOf<StatusPill?>(null)

    // The lists on screen (#70): the app list at the root, and the one opened from it on top, which alone shows.
    private var screens by mutableStateOf(ScreenStack<Screen>(Screen.Root))

    private val settingsStore by lazy { SettingsStore(this) }
    private var settings by mutableStateOf(Settings())

    // Lazy: the system services exist only once the activity is attached.
    private val statusWatcher by lazy { StatusWatcher(this) { status = it } }
    private val userManager by lazy { getSystemService(UserManager::class.java) }
    private val launcherApps by lazy { getSystemService(LauncherApps::class.java) }

    // Set once an app opens from here and cleared by Home, so the next resume is a return by Back, Recents or the app
    // exiting, which selects that app again.
    private var awayInApp = false

    private val recentStore by lazy { RecentAppsStore(this) }
    private var recents by mutableStateOf(RecentApps())

    // The favourites pinned to the top of the root list and the user's collections (#68).
    private val favouritesStore by lazy { FavouritesStore(this) }
    private var favourites by mutableStateOf(Favourites())
    private var collections by mutableStateOf(AppCollections())

    // The apps the user hid (#71): off the root list and the collections, still found by typing.
    private val hiddenStore by lazy { HiddenAppsStore(this) }
    private var hidden by mutableStateOf(HiddenApps())

    /** The installed apps the user did not hide, as the root list, the collections and the groups show them. */
    private val visibleApps: List<LauncherApp> get() = hidden.visibleFrom(apps.orEmpty())

    // The groups the user made (#69), shown merged with the automatic ones from the apps' categories.
    private val groupsStore by lazy { GroupsStore(this) }
    private var userGroups by mutableStateOf(UserGroups())

    /** The groups the Groups list shows: each holds at least one visible app. */
    private val shownGroups: List<AppGroup> get() = appGroups(visibleApps, userGroups)

    /**
     * The apps last opened from here, newest first, for the root screen (#70). Derived from the installed list, so an
     * uninstalled app leaves it as soon as the package-change receiver reloads the apps.
     */
    internal val recentApps: List<LauncherApp> get() = recents.shownFrom(apps.orEmpty())

    /** Whether [app] belongs to a work profile whose apps are turned off (#78): it stays listed, dim. */
    private fun isPaused(app: LauncherApp): Boolean = app.profile in loaded?.paused.orEmpty()

    // One worker thread: loads never run side by side on a slow device, and superseded ones are skipped.
    private val worker = Executors.newSingleThreadExecutor()

    // Lazy: the package manager and main executor exist only once the activity is attached.
    private val loader by lazy {
        LatestLoader(
            background = worker,
            main = mainExecutor,
            load = ::installedApps,
            publish = { loaded = it },
        )
    }

    private val listChanges =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context,
                intent: Intent,
            ) = loader.refresh()
        }

    // The other profiles' package changes (#78), which the package broadcasts only report for the launcher's own user.
    private val profilePackageChanges =
        object : LauncherApps.Callback() {
            override fun onPackageAdded(
                packageName: String,
                user: UserHandle,
            ) = loader.refresh()

            override fun onPackageChanged(
                packageName: String,
                user: UserHandle,
            ) = loader.refresh()

            override fun onPackageRemoved(
                packageName: String,
                user: UserHandle,
            ) = loader.refresh()

            override fun onPackagesAvailable(
                packageNames: Array<out String>,
                user: UserHandle,
                replacing: Boolean,
            ) = loader.refresh()

            override fun onPackagesUnavailable(
                packageNames: Array<out String>,
                user: UserHandle,
                replacing: Boolean,
            ) = loader.refresh()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hideStatusBar()
        recents = recentStore.load()
        settings = settingsStore.load()
        favourites = favouritesStore.loadFavourites()
        collections = favouritesStore.loadCollections()
        hidden = hiddenStore.load()
        aliases = aliasesStore.load()
        userGroups = groupsStore.load()
        // Registered for the activity's whole life, not only while resumed, so the list is current before a return.
        registerReceiver(
            listChanges,
            IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addAction(Intent.ACTION_PACKAGE_CHANGED)
                addDataScheme("package")
            },
        )
        // A work profile added or removed, or its apps turned off or on again (#78); these carry no package.
        registerReceiver(
            listChanges,
            IntentFilter().apply {
                addAction(Intent.ACTION_MANAGED_PROFILE_ADDED)
                addAction(Intent.ACTION_MANAGED_PROFILE_REMOVED)
                addAction(Intent.ACTION_MANAGED_PROFILE_AVAILABLE)
                addAction(Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE)
                addAction(Intent.ACTION_MANAGED_PROFILE_UNLOCKED)
            },
        )
        launcherApps.registerCallback(profilePackageChanges)
        // The home screen has nothing to go back to: finishing it would surface whichever launcher ran before.
        onBackPressedDispatcher.addCallback(this) {}
        setContent {
            // Outside the list, so the list comes back from another screen with the same row selected and the same
            // rows on screen.
            val listState = rememberAppListState()
            val shownStatus = status?.shownWith(settings)
            MinimalTheme {
                when (val screen = screens.top) {
                    Screen.Root -> {
                        AppList(
                            content = shownList(),
                            query = query,
                            homePresses = homePresses,
                            onOpen = ::open,
                            onMenu = { openMenu(it) },
                            onCollections = { screens = screens.pushed(Screen.CollectionsList()) },
                            onGroups = { screens = screens.pushed(Screen.GroupsList()) },
                            state = listState,
                            returned = returned,
                            status = shownStatus,
                            suffix = suffixes::get,
                            paused = ::isPaused,
                        )
                    }

                    is Screen.Menu -> {
                        ContextMenu(
                            title = screen.app.label,
                            items = screen.items,
                            onChoose = { choose(screen, it) },
                            onClose = ::back,
                            status = shownStatus,
                            state = screen.list,
                        )
                    }

                    is Screen.CollectionsList -> {
                        CollectionsList(
                            title = stringResource(R.string.collections),
                            names = collections.names(visibleApps),
                            onOpen = { screens = screens.pushed(Screen.Collection(it)) },
                            onClose = ::back,
                            state = screen.list,
                            status = shownStatus,
                        )
                    }

                    is Screen.Collection -> {
                        CollectionApps(
                            name = screen.name,
                            apps = collections.appsIn(screen.name, visibleApps),
                            isFavourite = { it in favourites },
                            onOpen = ::open,
                            onMenu = { openMenu(it, screen.name) },
                            onClose = ::back,
                            state = screen.state,
                            status = shownStatus,
                            suffix = suffixes::get,
                            paused = ::isPaused,
                        )
                    }

                    is Screen.CollectionChoice -> {
                        CollectionChoice(
                            title = stringResource(R.string.menu_add_collection),
                            names = collections.namesWithout(screen.app, visibleApps),
                            newLabel = stringResource(R.string.new_collection),
                            // Back down past this list and the menu, to the list the menu was opened from.
                            onChoose = { addToCollection(it, screen.app, closing = 2) },
                            onNew = { screens = screens.pushed(Screen.NewCollection(screen.app)) },
                            onClose = ::back,
                            state = screen.list,
                            status = shownStatus,
                        )
                    }

                    is Screen.NewCollection -> {
                        TextEntry(
                            title = stringResource(R.string.new_collection),
                            initial = "",
                            // Back down past the name, the list of collections and the menu.
                            // A blank name makes nothing.
                            onDone = { name -> if (name.isNotBlank()) addToCollection(name, screen.app, closing = 3) },
                            onClose = ::back,
                            status = shownStatus,
                        )
                    }

                    is Screen.GroupsList -> {
                        CollectionsList(
                            title = stringResource(R.string.groups),
                            names = shownGroups.map { it.name },
                            onOpen = { screens = screens.pushed(Screen.Group(it)) },
                            onClose = ::back,
                            state = screen.list,
                            status = shownStatus,
                            onMenu = ::openGroupMenu,
                        )
                    }

                    is Screen.Group -> {
                        CollectionApps(
                            name = screen.name,
                            apps = shownGroups.firstOrNull { it.name == screen.name }?.apps.orEmpty(),
                            isFavourite = { it in favourites },
                            onOpen = ::open,
                            onMenu = { openMenu(it, group = screen.name) },
                            onClose = ::back,
                            state = screen.state,
                            status = shownStatus,
                            suffix = suffixes::get,
                            paused = ::isPaused,
                        )
                    }

                    is Screen.GroupMenu -> {
                        ContextMenu(
                            title = screen.name,
                            items = screen.items,
                            onChoose = { chooseForGroup(screen, it) },
                            onClose = ::back,
                            status = shownStatus,
                            state = screen.list,
                        )
                    }

                    is Screen.GroupChoice -> {
                        CollectionChoice(
                            title = stringResource(R.string.menu_add_group),
                            names = userGroups.namesWithout(screen.app),
                            newLabel = stringResource(R.string.new_group),
                            // Back down past this list and the menu, to the list the menu was opened from.
                            onChoose = { addToGroup(it, screen.app, closing = 2) },
                            onNew = { screens = screens.pushed(Screen.NewGroup(screen.app)) },
                            onClose = ::back,
                            state = screen.list,
                            status = shownStatus,
                        )
                    }

                    is Screen.NewGroup -> {
                        TextEntry(
                            title = stringResource(R.string.new_group),
                            initial = "",
                            // Back down past the name, the list of groups and the menu. A blank name makes nothing.
                            onDone = { name -> if (name.isNotBlank()) addToGroup(name, screen.app, closing = 3) },
                            onClose = ::back,
                            status = shownStatus,
                        )
                    }

                    is Screen.RenameGroup -> {
                        TextEntry(
                            title = stringResource(R.string.menu_rename_group),
                            initial = screen.name,
                            onDone = { renameGroup(screen.name, it) },
                            onClose = ::back,
                            status = shownStatus,
                        )
                    }

                    is Screen.Rename -> {
                        TextEntry(
                            title = stringResource(R.string.menu_rename),
                            initial = screen.app.label,
                            onDone = { rename(screen.app, it) },
                            onClose = ::back,
                            status = shownStatus,
                        )
                    }

                    is Screen.SettingsList -> {
                        SettingsList(
                            title = stringResource(R.string.settings_entry),
                            settings = settings,
                            onCycle = ::cycle,
                            onOpen = { screens = screens.pushed(Screen.Link(it)) },
                            onClose = ::back,
                            state = screen.list,
                            status = shownStatus,
                        )
                    }

                    is Screen.Link -> {
                        when (screen.link) {
                            SettingsLink.HiddenApps -> {
                                HiddenAppsList(
                                    title = stringResource(R.string.settings_hidden_apps),
                                    apps = hidden.shownFrom(apps.orEmpty()),
                                    onUnhide = { saveHidden(hidden.unhidden(it)) },
                                    onClose = ::back,
                                    state = screen.list,
                                    status = shownStatus,
                                    suffix = suffixes::get,
                                )
                            }

                            SettingsLink.About -> {
                                AboutList(
                                    version = BuildConfig.VERSION_NAME,
                                    commit = BuildConfig.BUILD_COMMIT,
                                    onClose = ::back,
                                    status = shownStatus,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * What the root list shows: the full list under its block of favourites, recently used apps and the Collections
     * row, and above the Groups row, without the hidden apps, or the apps the query keeps, the hidden ones last.
     */
    private fun shownList(): ListContent =
        listContent(apps, query, recents, settings, favourites, collections, hidden, userGroups)

    /** Back or Esc on a list opened from the root: the list below comes back as it was. */
    private fun back() {
        screens = screens.popped()
    }

    /**
     * The status pill stands in for Android's status bar (#65), so the launcher's window hides it; a swipe from the top
     * edge brings it back for a moment. It is this window's setting only, so the bar shows again in any other app.
     */
    private fun hideStatusBar() {
        enableEdgeToEdge()
        WindowCompat.getInsetsController(window, window.decorView).run {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.statusBars())
        }
    }

    // The pill's sources are watched only while the launcher can be seen.
    override fun onStart() {
        super.onStart()
        statusWatcher.start()
    }

    override fun onStop() {
        statusWatcher.stop()
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // singleTask: the Home key (or a launcher icon) reaches the running instance here, never a second one.
        awayInApp = false
        screens = screens.home()
        homePresses++
        query = ""
    }

    // Typing filters the list. Rows only handle arrows, Enter and Space, so typed keys fall through to here, and still
    // arrive when the filter leaves no row to hold focus. Other keys, and Ctrl or Meta shortcuts, keep their usual job,
    // and so does every key while another list (a context menu, the settings) or the text editor is open: the editor
    // takes its own typing.
    override fun onKeyDown(
        keyCode: Int,
        event: KeyEvent,
    ): Boolean {
        val typed = query.takeIf { screens.isRoot && !event.isCtrlPressed && !event.isMetaPressed }
        val edited =
            when {
                typed == null -> null
                keyCode == KeyEvent.KEYCODE_DEL -> typed.minusLastTyped()
                else -> typed.plusTyped(event.unicodeChar)
            }
        if (edited == null) return super.onKeyDown(keyCode, event)
        query = edited
        return true
    }

    override fun onResume() {
        super.onResume()
        // Reload on every return too, off the main thread; the current list stays up until the new one is ready.
        loader.refresh()
        // Home arrives through onNewIntent, always before this, so whatever is left here is a return from the app.
        if (awayInApp) {
            awayInApp = false
            // Against the list as it shows now: on the root, the app's row in the recently used block. A collection
            // the app was opened from keeps its own selection, on that app.
            if (screens.isRoot) {
                returned = Returned(recents.returnRow(shownList().rowApps), (returned?.count ?: 0) + 1)
            }
        }
    }

    override fun onDestroy() {
        unregisterReceiver(listChanges)
        launcherApps.unregisterCallback(profilePackageChanges)
        worker.shutdown()
        super.onDestroy()
    }

    private fun open(app: LauncherApp) {
        // MinimalUI's own settings open in place; they are not an app to start, nor a recent one.
        if (app.isLauncherSettings) {
            screens = screens.pushed(Screen.SettingsList())
            return
        }
        val started =
            if (app.isWorkApp) {
                startInProfile(
                    app,
                    LauncherApps::startMainActivity,
                )
            } else {
                start(app.launchIntent())
            }
        if (started) {
            saveRecents(recents.opened(app, apps.orEmpty()))
            awayInApp = true
        }
    }

    /** Whether [intent] started. */
    private fun start(intent: Intent): Boolean = starting { startActivity(intent, noAnimation()) }

    /**
     * Whether [app], of another profile (#78), started there through [LauncherApps] by [how]: its main activity, or its
     * App info. While its profile's apps are turned off, Android offers to turn them back on. A profile removed while
     * the list was on screen starts nothing and drops its apps.
     */
    private fun startInProfile(
        app: LauncherApp,
        how: LauncherApps.(ComponentName, UserHandle, Rect?, Bundle?) -> Unit,
    ): Boolean {
        val user = app.profile?.let(userManager::getUserForSerialNumber)
        if (user == null) {
            loader.refresh()
            return false
        }
        return starting {
            launcherApps.how(
                ComponentName(app.packageName, app.activityName),
                user,
                null,
                noAnimation(),
            )
        }
    }

    // No transition, as Cannoli: nothing animates between a key press and the app (BRANDING.md).
    private fun noAnimation(): Bundle = ActivityOptions.makeCustomAnimation(this, 0, 0).toBundle()

    /** Whether [start] started what it starts, without crashing the launcher when it cannot. */
    private fun starting(start: () -> Unit): Boolean =
        try {
            start()
            true
        } catch (_: ActivityNotFoundException) {
            // Uninstalled or disabled while the list was on screen: drop it rather than crash the launcher.
            loader.refresh()
            false
        } catch (_: SecurityException) {
            // Listed but not startable by us (not exported, or guarded by a permission we lack): ignore the press
            // rather than crash the launcher; a refresh would list it again.
            false
        }

    // The facts are read once, as the menu opens; every action closes it again. A menu opened inside the [collection]
    // can also take the app out of it or move it there; one opened inside a [group] the user put it in can take it out.
    private fun openMenu(
        app: LauncherApp,
        collection: String? = null,
        group: String? = null,
    ) {
        val recent = recentApps
        val inCollection = collection?.let { collections.appsIn(it, visibleApps) }
        val facts =
            AppFacts(
                isSystem = packageManager.isSystemApp(app.packageName),
                // Android's Settings keeps the way back to another home app;
                // MinimalUI's row stands for the launcher itself.
                isSettingsEntry =
                    app.isLauncherSettings || app.packageName == packageManager.systemSettings()?.packageName,
                isRecent = app in recent,
                hasRecents = recent.isNotEmpty(),
                isFavourite = app in favourites,
                place = inCollection?.let { CollectionPlace(it.indexOf(app), it.size) },
                isHidden = app in hidden,
                isLauncherSettings = app.isLauncherSettings,
                isGroupMember = group != null && userGroups.holds(group, app),
                isWorkApp = app.isWorkApp,
            )
        screens = screens.pushed(Screen.Menu(app, contextMenu(facts), collection, group))
    }

    // A purely automatic group's menu still opens, its rows disabled, so the key never does nothing.
    private fun openGroupMenu(name: String) {
        val isUserGroup = shownGroups.firstOrNull { it.name == name }?.isUserGroup == true
        screens = screens.pushed(Screen.GroupMenu(name, groupMenu(isUserGroup)))
    }

    // The list comes back on the same app: its selection was kept while the menu was up. Add to collection, Add to
    // group and Rename keep the menu below the list or editor they open, so Back returns to it.
    private fun choose(
        menu: Screen.Menu,
        action: MenuAction,
    ) {
        val app = menu.app
        if (action !in KeptMenuActions) back()
        when (action) {
            MenuAction.AppInfo -> {
                if (app.isWorkApp) {
                    startInProfile(
                        app,
                        LauncherApps::startAppDetailsActivity,
                    )
                } else {
                    start(app.appInfoIntent())
                }
            }

            MenuAction.Uninstall -> {
                start(app.uninstallIntent())
            }

            MenuAction.AddToFavourites, MenuAction.RemoveFromFavourites -> {
                saveFavourites(favourites.toggled(app))
            }

            MenuAction.AddToCollection -> {
                screens = screens.pushed(Screen.CollectionChoice(app))
            }

            MenuAction.RemoveFromCollection -> {
                menu.collection?.let { removeFromCollection(it, app) }
            }

            MenuAction.MoveUp -> {
                menu.collection?.let { moveInCollection(it, app, -1) }
            }

            MenuAction.MoveDown -> {
                menu.collection?.let { moveInCollection(it, app, 1) }
            }

            MenuAction.AddToGroup -> {
                screens = screens.pushed(Screen.GroupChoice(app))
            }

            MenuAction.RemoveFromGroup -> {
                menu.group?.let { removeFromGroup(it, app) }
            }

            MenuAction.Rename -> {
                screens = screens.pushed(Screen.Rename(app))
            }

            MenuAction.Hide -> {
                hide(menu)
            }

            MenuAction.Unhide -> {
                saveHidden(hidden.unhidden(app))
            }

            MenuAction.RemoveFromRecents -> {
                saveRecents(recents.without(app))
            }

            MenuAction.ClearRecents -> {
                saveRecents(recents.cleared())
            }

            // Only a group's menu lists these, and it has its own handler.
            MenuAction.RenameGroup, MenuAction.DeleteGroup -> {
                Unit
            }
        }
    }

    // Rename group keeps the menu below the editor, so Back returns to it; Delete group closes it, back on the groups,
    // and those close too when no group is left to show. A merged automatic group of that name stays.
    private fun chooseForGroup(
        menu: Screen.GroupMenu,
        action: MenuAction,
    ) {
        if (action == MenuAction.RenameGroup) {
            screens = screens.pushed(Screen.RenameGroup(menu.name))
            return
        }
        back()
        saveGroups(userGroups.deleted(menu.name))
        if (shownGroups.isEmpty()) back()
    }

    /**
     * The user group called [from] renamed [to] (merged into a group of that name), then the editor and menu closed.
     */
    private fun renameGroup(
        from: String,
        to: String,
    ) {
        saveGroups(userGroups.renamed(from, to))
        repeat(2) { back() }
    }

    /** [app] added to the user group called [name], then [closing] lists closed, back to where the menu was opened. */
    private fun addToGroup(
        name: String,
        app: LauncherApp,
        closing: Int,
    ) {
        saveGroups(userGroups.added(name, app))
        repeat(closing) { back() }
    }

    private fun removeFromGroup(
        name: String,
        app: LauncherApp,
    ) {
        saveGroups(userGroups.removed(name, app))
        leaveGroupIfGone(name)
    }

    // The group's list closes once it shows no app, and the list of groups once no group shows any.
    private fun leaveGroupIfGone(name: String) {
        val names = shownGroups.map { it.name }
        if (name !in names) back()
        if (names.isEmpty()) back()
    }

    /**
     * [app], as listed, renamed [name] (a blank name, or its own label, gives it back its system label), then the
     * editor and the menu below it closed: the list it was opened from shows it at its new place, selected.
     */
    private fun rename(
        app: LauncherApp,
        name: String,
    ) {
        val installed = loaded?.apps.orEmpty().firstOrNull { it.component == app.component }
        if (installed != null) saveAliases(aliases.renamed(installed, name))
        repeat(2) { back() }
    }

    /** [app] added to the collection called [name], then [closing] lists closed, back to where the menu was opened. */
    private fun addToCollection(
        name: String,
        app: LauncherApp,
        closing: Int,
    ) {
        saveCollections(collections.added(name, app))
        repeat(closing) { back() }
    }

    private fun removeFromCollection(
        name: String,
        app: LauncherApp,
    ) {
        saveCollections(collections.removed(name, app))
        leaveIfGone(name)
    }

    // Hidden from inside a collection, the app leaves its list as if removed, though the collection still holds it.
    private fun hide(menu: Screen.Menu) {
        saveHidden(hidden.hidden(menu.app, packageManager.systemSettings()?.packageName))
        menu.collection?.let(::leaveIfGone)
        menu.group?.let(::leaveGroupIfGone)
    }

    // The collection's list closes with its last app shown, and the list of collections with the last collection that
    // shows any.
    private fun leaveIfGone(name: String) {
        val names = collections.names(visibleApps)
        if (name !in names) back()
        if (names.isEmpty()) back()
    }

    // The collection's list follows the app to its new row.
    private fun moveInCollection(
        name: String,
        app: LauncherApp,
        step: Int,
    ) = saveCollections(collections.moved(name, app, step, visibleApps))

    private fun saveFavourites(changed: Favourites) {
        favourites = changed
        favouritesStore.save(changed)
    }

    private fun saveCollections(changed: AppCollections) {
        collections = changed
        favouritesStore.save(changed)
    }

    private fun saveGroups(changed: UserGroups) {
        userGroups = changed
        groupsStore.save(changed)
    }

    private fun saveHidden(changed: HiddenApps) {
        hidden = changed
        hiddenStore.save(changed)
    }

    private fun saveRecents(changed: RecentApps) {
        recents = changed
        recentStore.save(changed)
    }

    private fun saveAliases(changed: Aliases) {
        aliases = changed
        aliasesStore.save(changed)
    }

    private fun cycle(
        setting: Setting,
        step: Int,
    ) {
        settings = settings.cycled(setting, step)
        settingsStore.save(settings)
    }

    // Runs on the worker thread: the package queries and every label load stay off the main thread. Sorting waits for
    // the aliases, in [apps]. The other profiles' apps (#78) come from LauncherApps, after the Settings check, so a
    // work profile's Settings never stands in for the launcher's own.
    private fun installedApps(): Loaded {
        val installed = packageManager.launchableApps()
        val system = packageManager.systemSettings()
        val entry = launcherSettings(getString(R.string.settings_entry), packageName)
        val own = system?.let(installed::withSystemSettings) ?: installed
        val others = launcherApps.otherProfileApps(userManager)
        return Loaded(own + others + entry, system?.packageName, userManager.pausedProfiles())
    }
}

/**
 * The launchable [apps] of every profile with their system labels and MinimalUI's settings row, unsorted, the package
 * of Android's Settings, which that row follows however the apps are named, and the [paused] profiles, whose apps are
 * turned off (#78).
 */
private class Loaded(
    val apps: List<LauncherApp>,
    val settingsPackage: String?,
    val paused: Set<Long>,
)

/** The app's menu actions that open a list or editor on top of the menu, which stays below it. */
private val KeptMenuActions = setOf(MenuAction.AddToCollection, MenuAction.AddToGroup, MenuAction.Rename)

/** A list on [ScreenStack], each holding what it needs to show again as it was left. */
private sealed interface Screen {
    /** The app list (its selection is kept by the activity's content, which outlives every other screen). */
    data object Root : Screen

    /**
     * The context menu of [app], with its [items] as they were when it opened, and the [collection] or [group] it was
     * opened in; its selection is kept while a list opened from it is up.
     */
    class Menu(
        val app: LauncherApp,
        val items: List<MenuItem>,
        val collection: String?,
        val group: String?,
    ) : Screen {
        val list = PillListState()
    }

    /** The names of the collections, from the root's Collections row (#68). */
    class CollectionsList : Screen {
        val list = PillListState()
    }

    /** The apps of the collection called [name], in the user's order. */
    class Collection(
        val name: String,
    ) : Screen {
        val state = CollectionState()
    }

    /** The collections [app] can be added to, and a new one. */
    class CollectionChoice(
        val app: LauncherApp,
    ) : Screen {
        val list = PillListState()
    }

    /** A new collection's name for [app], in the text editor. */
    class NewCollection(
        val app: LauncherApp,
    ) : Screen

    /** The groups (#69), automatic and the user's, from the root's Groups row. */
    class GroupsList : Screen {
        val list = PillListState()
    }

    /** The apps of the group called [name], in the main list's order. */
    class Group(
        val name: String,
    ) : Screen {
        val state = CollectionState()
    }

    /** The menu of the group called [name]: its [items] rename or delete it. */
    class GroupMenu(
        val name: String,
        val items: List<MenuItem>,
    ) : Screen {
        val list = PillListState()
    }

    /** The user groups [app] can be added to, and a new one. */
    class GroupChoice(
        val app: LauncherApp,
    ) : Screen {
        val list = PillListState()
    }

    /** A new group's name for [app], in the text editor. */
    class NewGroup(
        val app: LauncherApp,
    ) : Screen

    /** A new name for the user group called [name], in the text editor, which starts with it. */
    class RenameGroup(
        val name: String,
    ) : Screen

    /** A new name for [app] (#72), in the text editor, which starts with the name it is listed under. */
    class Rename(
        val app: LauncherApp,
    ) : Screen

    /** MinimalUI's settings, opened fresh on their first row each time, kept while a link's list is up. */
    class SettingsList : Screen {
        val list = PillListState()
    }

    /** The list a settings link opens, on its first row each time it opens. */
    class Link(
        val link: SettingsLink,
    ) : Screen {
        val list = PillListState()
    }
}
