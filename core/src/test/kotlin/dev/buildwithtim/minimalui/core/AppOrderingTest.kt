package dev.buildwithtim.minimalui.core

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Runs on the JDK collator, not the ICU collator a device uses (see [labelCollator]). Labels here stick to ASCII and
 * Latin-1 accents, where the two agree. A case such as `Ø` or `Ł` sorting next to its base letter is true on a device
 * but fails on the JVM.
 */
class AppOrderingTest {
    private fun app(
        label: String,
        packageName: String = "pkg.$label",
    ) = LauncherApp(label = label, packageName = packageName, activityName = "$packageName.Main")

    @Test
    fun `orders apps alphabetically regardless of letter case`() {
        val apps = listOf(app("bravo"), app("Charlie"), app("alpha"))

        val labels = apps.sortedForDisplay().map { it.label }

        assertEquals(listOf("alpha", "bravo", "Charlie"), labels)
    }

    @Test
    fun `orders accented labels next to their base letter`() {
        val apps = listOf(app("Fennec"), app("Éclair"), app("Duo"))

        val labels = apps.sortedForDisplay().map { it.label }

        assertEquals(listOf("Duo", "Éclair", "Fennec"), labels)
    }

    @Test
    fun `orders apps with the same label by package so the list does not reshuffle`() {
        val apps =
            listOf(
                app("Camera", packageName = "com.vendor.camera"),
                app("Camera", packageName = "com.android.camera"),
            )

        val packages = apps.sortedForDisplay().map { it.packageName }

        assertEquals(listOf("com.android.camera", "com.vendor.camera"), packages)
    }

    @Test
    fun `orders a personal app before its work copy`() {
        val personal = app("Camera")
        val apps = listOf(personal.copy(profile = 11L), personal.copy(profile = 10L), personal)

        assertEquals(listOf(null, 10L, 11L), apps.sortedForDisplay().map { it.profile })
    }
}
