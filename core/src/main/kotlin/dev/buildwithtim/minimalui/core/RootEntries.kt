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

    /** An app's row; [isFavourite] wherever a favourite is listed, so it shows with its `★` (#68). */
    data class App(
        override val app: LauncherApp,
        val section: RootSection = RootSection.All,
        val isFavourite: Boolean = false,
    ) : RootEntry {
        override val key: String get() = "$section/${app.packageName}/${app.activityName}"
        override val selectable: Boolean get() = true
    }

    /** The one row that opens the list of collections (#68), at the end of the block. */
    data object Collections : RootEntry {
        override val key: String get() = "collections"
        override val selectable: Boolean get() = true
        override val app: LauncherApp? get() = null
    }

    /** One empty row between the block and the full list; it still takes a row on every page. */
    data object Blank : RootEntry {
        override val key: String get() = "blank"
        override val selectable: Boolean get() = false
        override val app: LauncherApp? get() = null
    }
}

/**
 * The root list, top to bottom (#70, the hybrid of MinUI's root and a flat list): the [favourites] among [apps] in
 * their order (#68), then the newest of the [recents] that are not favourites, as many as the Recently used setting
 * allows, then a Collections row when any of the [collections] is shown, then one blank row, then all [apps] in their
 * order, which still lists those apps too. With none of these at the top there is no block and no blank row.
 */
fun rootEntries(
    apps: List<LauncherApp>,
    recents: RecentApps,
    settings: Settings,
    favourites: Favourites = Favourites(),
    collections: AppCollections = AppCollections(),
): List<RootEntry> {
    val all = apps.map { RootEntry.App(it, isFavourite = it in favourites) }
    val recent = recents.shownFrom(apps).filterNot { it in favourites }.take(settings.recentlyUsed)
    val block = favourites.shownFrom(apps).map { RootEntry.App(it, RootSection.Favourite, isFavourite = true) } +
        recent.map { RootEntry.App(it, RootSection.Recent) } +
        listOfNotNull(RootEntry.Collections.takeIf { collections.names(apps).isNotEmpty() })
    if (block.isEmpty()) return all
    return block + RootEntry.Blank + all
}

/**
 * The row of [now] to select so the selection stays on what row [selected] of [before] showed when the list changes
 * under it (a block growing or shrinking above it): the same entry, else the same app in another row (a recent
 * dropped from the block keeps it in the full list), else the same row number.
 */
fun followedRow(before: List<RootEntry>, selected: Int, now: List<RootEntry>): Int {
    val entry = before.getOrNull(selected) ?: return selected
    val same = now.indexOfFirst { it.key == entry.key }
    if (same >= 0) return same
    val app = entry.app ?: return selected
    val sameApp = now.indexOfFirst { row ->
        row.app.let { it != null && it.packageName == app.packageName && it.activityName == app.activityName }
    }
    return if (sameApp >= 0) sameApp else selected
}
