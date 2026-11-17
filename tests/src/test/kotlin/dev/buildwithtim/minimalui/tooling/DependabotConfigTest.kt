package dev.buildwithtim.minimalui.tooling

import org.junit.Assert.assertTrue
import org.junit.Test

class DependabotConfigTest {
    private val gradle: Map<String, Any?> =
        Yaml
            .parse(Repo.read(".github/dependabot.yml"))
            .asMap()
            .getValue("updates")
            .asList()
            .map { it.asMap() }
            .single { it["package-ecosystem"] == "gradle" }

    // Dependabot leaves the android-all jars to a hand edit (#279), and without them a Robolectric update fails every
    // Robolectric test. Inside a group it held back every other update of the week with it (#447, #449).
    @Test
    fun `Robolectric updates arrive in a pull request of their own`() {
        val ignored = gradle.getValue("ignore").asList().map { it.asMap()["dependency-name"] }
        assertTrue(ignored.toString(), "org.robolectric:android-all-instrumented" in ignored)

        for ((name, group) in gradle.getValue("groups").asMap()) {
            val excluded = (group.asMap()["exclude-patterns"] ?: emptyList<Any?>()).asList()
            assertTrue("group $name takes org.robolectric:robolectric", "org.robolectric:robolectric" in excluded)
        }
    }
}
