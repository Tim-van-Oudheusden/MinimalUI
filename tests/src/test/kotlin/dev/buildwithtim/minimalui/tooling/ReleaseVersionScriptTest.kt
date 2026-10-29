package dev.buildwithtim.minimalui.tooling

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ReleaseVersionScriptTest {
    private fun outputs(result: ScriptResult): Map<String, String> =
        result.stdout
            .lines()
            .filter { "=" in it }
            .associate { it.substringBefore("=") to it.substringAfter("=") }

    private fun releaseOf(vararg buildFileLines: String): ScriptResult {
        val buildFile = File.createTempFile("build", ".gradle.kts")
        try {
            buildFile.writeText(buildFileLines.joinToString("\n", postfix = "\n"))
            return runScript("scripts/release-version.sh", buildFile.path)
        } finally {
            buildFile.delete()
        }
    }

    // The release workflow reads the real build file; reformatting its versionName line must fail here, not there.
    @Test
    fun `reads the release from the app's own build file`() {
        val result = runScript("scripts/release-version.sh")
        val release = outputs(result)

        assertEquals(result.stdout, 0, result.exitCode)
        assertTrue(result.stdout, release.getValue("version").matches(Regex("""\d+\.\d+\.\d+(-[0-9A-Za-z.-]+)?""")))
        assertEquals("v" + release.getValue("version"), release.getValue("tag"))
    }

    @Test
    fun `a plain version is a full release`() {
        val result = releaseOf("android {", "    defaultConfig {", "        versionName = \"1.2.3\"", "    }", "}")

        assertEquals(result.stdout, 0, result.exitCode)
        assertEquals(mapOf("version" to "1.2.3", "tag" to "v1.2.3", "prerelease" to "false"), outputs(result))
    }

    @Test
    fun `a version with a suffix is a pre-release`() {
        val result = releaseOf("        versionName = \"0.2.0-beta.1\"")

        assertEquals(
            mapOf("version" to "0.2.0-beta.1", "tag" to "v0.2.0-beta.1", "prerelease" to "true"),
            outputs(result),
        )
    }

    @Test
    fun `fails unless there is exactly one versionName`() {
        val none = releaseOf("        versionCode = 2")
        val two = releaseOf("        versionName = \"1.0.0\"", "        versionName = \"1.0.1\"")

        assertEquals(1, none.exitCode)
        assertEquals(1, two.exitCode)
        assertTrue(none.stdout, none.stdout.startsWith("::error::"))
    }

    // The version becomes a git tag and a release name; anything but MAJOR.MINOR.PATCH[-suffix] is refused.
    @Test
    fun `refuses a version that is not semantic`() {
        val result = releaseOf("        versionName = \"1.0 beta\"")

        assertEquals(1, result.exitCode)
        assertTrue(result.stdout, result.stdout.startsWith("::error::"))
    }
}
