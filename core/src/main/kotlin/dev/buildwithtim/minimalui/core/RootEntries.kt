package dev.buildwithtim.minimalui.core

/**
 * Where an app row sits on the root list (#70): in the favourites or the recently used block at the top (#68), or in
 * the full alphabetical list below them.
 */
enum class RootSection { Favourite, Recent, All }

/**
 * One row of the root list. An app's row opens it; the blank row only separates the block at the top from the full
 * list. A row that opens a list of its own (a collection or a group) is one more kind.
 */
sealed interface RootEntry {
    /** Unique within one list (an app in the block and in the full list has two), and fit for a Bundle. */
    val key: String

    /** Whether the selection can rest on the row: Up and Down skip a row that is not. */
    val selectable: Boolean

    /** The app the row opens, if it is an app's row. */
    val app: LauncherApp?

    /**
     * An app's row; [isFavourite] wherever a favourite is listed, so it shows with its `★` (#68), and [isHidden] for a
     * hidden app that type-to-filter found (#71), drawn dim.
     */
    data class App(
        override val app: LauncherApp,
        val section: RootSection = RootSection.All,
        val isFavourite: Boolean = false,
        val isHidden: Boolean = false,
    ) : RootEntry {
        override val key: String get() = "$section/${app.component}"
        override val selectable: Boolean get() = true
    }

    /** The one row that opens the list of collections (#68), at the end of the block. */
    data object Collections : RootEntry {
        override val key: String get() = "collections"
        override val selectable: Boolean get() = true
        override val app: LauncherApp? get() = null
    }

    /** The one row that opens the list of groups (#69), last on the root, as MinUI lists Tools last. */
    data object Groups : RootEntry {
        override val key: String get() = "groups"
        override val selectable: Boolean get() = true
        override val app: LauncherApp? get() = null
    }

    /**
     * One empty row that sets a part apart: the block from the full list, or the full list from the Groups row (the
     * two differ by [key]). It still takes a row on every page.
     */
    data class Blank(
        override val key: String = "blank",
    ) : RootEntry {
        override val selectable: Boolean get() = false
        override val app: LauncherApp? get() = null
    }
}

/**
 * The root list, top to bottom (#70, the hybrid of MinUI's root and a flat list): the [favourites] among [apps] in
 * their order (#68), then the newest of the [recents] that are not favourites, as many as the Recently used setting
 * allows, then a Collections row when any of the [collections] is shown, then one blank row, then all [apps] in their
 * order, which still lists those apps too, but for the work apps (#78): they show in the block when the user made one
 * a favourite or opened it, and else only in their Work group. With none of these at the top there is no block and no
 * blank row. When any group is shown ([appGroups] of [apps] and the user's [groups], #69), one more blank row and the
 * Groups row end it, so the first row stays an app and Up from it reaches the groups at once; with nothing above it,
 * the Groups row stands alone.
 */
fun rootEntries(
    apps: List<LauncherApp>,
    recents: RecentApps,
    settings: Settings,
    favourites: Favourites = Favourites(),
    collections: AppCollections = AppCollections(),
    groups: UserGroups = UserGroups(),
): List<RootEntry> {
    val all = apps.filterNot { it.isWorkApp }.map { RootEntry.App(it, isFavourite = it in favourites) }
    val recent = recents.shownFrom(apps).filterNot { it in favourites }.take(settings.recentlyUsed)
    val block =
        favourites.shownFrom(apps).map { RootEntry.App(it, RootSection.Favourite, isFavourite = true) } +
            recent.map { RootEntry.App(it, RootSection.Recent) } +
            listOfNotNull(RootEntry.Collections.takeIf { collections.names(apps).isNotEmpty() })
    val top = if (block.isEmpty()) all else block + RootEntry.Blank() + all
    return when {
        appGroups(apps, groups).isEmpty() -> top
        top.isEmpty() -> listOf(RootEntry.Groups)
        else -> top + RootEntry.Blank(GROUPS_BLANK) + RootEntry.Groups
    }
}

private const val GROUPS_BLANK = "groups-blank"

/**
 * The row of [now] to select so the selection stays on what row [selected] of [before] showed when the list changes
 * under it (a block growing or shrinking above it): the same entry, else the same app in another row (a recent
 * dropped from the block keeps it in the full list), else the same row number.
 */
fun followedRow(
    before: List<RootEntry>,
    selected: Int,
    now: List<RootEntry>,
): Int {
    val entry = before.getOrNull(selected) ?: return selected
    return now.indexOfFirst { it.key == entry.key }.takeIf { it >= 0 }
        ?: entry.app?.let { app -> now.indexOfFirst { it.app?.component == app.component } }?.takeIf { it >= 0 }
        ?: selected
}
