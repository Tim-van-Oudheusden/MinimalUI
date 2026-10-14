package dev.buildwithtim.minimalui.core

import org.junit.Assert.assertEquals
import org.junit.Test

class LauncherAppTest {
    private val mail = LauncherApp("Mail", "com.example.mail", "com.example.mail.Main")
    private val workMail = mail.copy(profile = 10L)

    @Test
    fun `an app of the launcher's own user keeps its plain component, so stored keys stay valid`() {
        assertEquals("com.example.mail/com.example.mail.Main", mail.component)
    }

    @Test
    fun `an app of another profile has the profile's serial number before its component`() {
        assertEquals("10:com.example.mail/com.example.mail.Main", workMail.component)
    }

    @Test
    fun `every store keeps a work copy and a personal copy apart, and reads both back`() {
        val favourites = Favourites().toggled(workMail)
        val recents = RecentApps().opened(workMail, listOf(mail, workMail))
        val collections = AppCollections().added("Mail", workMail)
        val hidden = HiddenApps().hidden(workMail, settingsPackage = null)
        val aliases = Aliases().renamed(workMail, "Office mail")
        val groups = UserGroups().added("Office", workMail)

        assertEquals(listOf(workMail), favourites.shownFrom(listOf(mail, workMail)))
        assertEquals(listOf(workMail), recents.shownFrom(listOf(mail, workMail)))
        assertEquals(listOf(workMail), collections.appsIn("Mail", listOf(mail, workMail)))
        assertEquals(listOf(mail), hidden.visibleFrom(listOf(mail, workMail)))
        assertEquals(listOf("Mail", "Office mail"), aliases.labelled(listOf(mail, workMail), null).map { it.label })
        assertEquals(listOf(false, true), listOf(mail, workMail).map { groups.holds("Office", it) })

        assertEquals(favourites, Favourites.fromStored(favourites.stored()))
        assertEquals(recents, RecentApps.fromStored(recents.stored()))
        assertEquals(collections, AppCollections.fromStored(collections.stored()))
        assertEquals(hidden, HiddenApps.fromStored(hidden.stored()))
        assertEquals(aliases, Aliases.fromStored(aliases.stored()))
        assertEquals(groups, UserGroups.fromStored(groups.stored()))
    }

    @Test
    fun `keys stored before work profiles read back for the personal app only`() {
        val stored = "com.example.mail/com.example.mail.Main"

        assertEquals(listOf(mail), Favourites.fromStored(stored).shownFrom(listOf(mail, workMail)))
    }
}
