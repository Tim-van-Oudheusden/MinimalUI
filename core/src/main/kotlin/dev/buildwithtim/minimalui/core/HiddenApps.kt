package dev.buildwithtim.minimalui.core

/**
 * The apps the user hid (#71): they leave the root list (its favourites and recently used block too) and every
 * collection's list, yet still match type-to-filter and are listed under Hidden apps in the settings, the two ways
 * back. Kept by component, like [Favourites], so an app stays hidden when it is relabelled. Hiding stores nothing
 * else: an app's favourite, recent and collection places stay as they were, so unhiding brings it back where it was.
 *
 * An uninstalled app is never dropped from here, so reinstalling a hidden app keeps it hidden: the user hid it on
 * purpose, and an app update that reinstalls should not undo that.
 */
data class HiddenApps(
    val components: Set<String> = emptySet(),
) {
    operator fun contains(app: LauncherApp): Boolean = app.component in components

    /**
     * [app] hidden, unless it is the guaranteed Settings entry (any activity of [settingsPackage], the system
     * settings app) or MinimalUI's own settings row: those keep the way back to another home app and to these
     * settings, so they are never hidden.
     */
    fun hidden(
        app: LauncherApp,
        settingsPackage: String?,
    ): HiddenApps =
        if (app.isLauncherSettings ||
            app.packageName == settingsPackage
        ) {
            this
        } else {
            HiddenApps(components + app.component)
        }

    /** [app] shown again. */
    fun unhidden(app: LauncherApp): HiddenApps = HiddenApps(components - app.component)

    /** The apps of [installed] that are not hidden, in its order. */
    fun visibleFrom(installed: List<LauncherApp>): List<LauncherApp> = installed.filterNot { it in this }

    /** The hidden apps among [installed], alphabetical by their label as listed, for the Hidden apps list. */
    fun shownFrom(installed: List<LauncherApp>): List<LauncherApp> = installed.filter { it in this }.sortedForDisplay()

    /** One component per line, for app-private storage. */
    fun stored(): String = components.joinToString("\n")

    companion object {
        /** The hidden apps [stored] wrote; lines that are not a component are skipped. */
        fun fromStored(text: String?): HiddenApps =
            HiddenApps(
                text
                    .orEmpty()
                    .lines()
                    .filter(::isComponent)
                    .toSet(),
            )
    }
}
