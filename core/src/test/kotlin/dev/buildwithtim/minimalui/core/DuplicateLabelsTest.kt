package dev.buildwithtim.minimalui.core

import org.junit.Assert.assertEquals
import org.junit.Test

class DuplicateLabelsTest {

    private val phone = LauncherApp("Phone", "com.android.dialer", "com.android.dialer.Main")
    private val otherPhone = LauncherApp("PHONE", "org.fossify.phone", "org.fossify.phone.Main")
    private val camera = LauncherApp("Camera", "com.android.camera", "com.android.camera.Main")

    @Test
    fun `unique labels get no suffix, and an accent makes a label unique, as the sort tells them apart`() {
        val accented = LauncherApp("Phoné", "com.example.phone", "com.example.phone.Main")

        assertEquals(emptyMap<LauncherApp, String>(), duplicateSuffixes(listOf(phone, camera, accented)))
    }

    @Test
    fun `labels equal under the list's collator, ignoring case, each get their package name`() {
        assertEquals(
            mapOf(phone to "com.android.dialer", otherPhone to "org.fossify.phone"),
            duplicateSuffixes(listOf(camera, phone, otherPhone)),
        )
    }

    @Test
    fun `duplicates within one package get their activity's short class name`() {
        val contacts = LauncherApp("Phone", "com.android.dialer", "com.android.dialer.ContactsActivity")

        assertEquals(
            mapOf(phone to "Main", contacts to "ContactsActivity", otherPhone to "org.fossify.phone"),
            duplicateSuffixes(listOf(phone, contacts, otherPhone)),
        )
    }

    @Test
    fun `duplicates are detected on the displayed label, so an alias can make or end one`() {
        val aliases = Aliases().renamed(camera, "Phone").renamed(otherPhone, "Dialer")

        val shown = aliases.labelled(listOf(camera, phone, otherPhone), settingsPackage = null)

        assertEquals(
            mapOf(phone to "com.android.dialer", camera.copy(label = "Phone") to "com.android.camera"),
            duplicateSuffixes(shown),
        )
    }

    @Test
    fun `the suffix is never matched by the filter`() {
        val shown = listOf(phone, otherPhone)

        assertEquals(mapOf(phone to "com.android.dialer", otherPhone to "org.fossify.phone"), duplicateSuffixes(shown))
        assertEquals(emptyList<LauncherApp>(), shown.filteredBy("fossify"))
    }
}
