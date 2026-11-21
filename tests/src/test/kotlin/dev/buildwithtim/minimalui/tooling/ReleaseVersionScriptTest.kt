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

    private fun buildFile(vararg lines: String): File =
        File.createTempFile("build", ".gradle.kts").apply { writeText(lines.joinToString("\n", postfix = "\n")) }

    /** Runs the script on [buildFile], and on [previous] as the last release's build file when given. */
    private fun run(
        buildFile: File,
        previous: File? = null,
    ): ScriptResult =
        try {
            runScript("scripts/release-version.sh", *listOfNotNull(buildFile.path, previous?.path).toTypedArray())
        } finally {
            buildFile.delete()
            previous?.delete()
        }

    private fun releaseOf(vararg buildFileLines: String): ScriptResult = run(buildFile(*buildFileLines))

    /** A build file with [version] as its versionName and some versionCode. */
    private fun releaseNamed(version: String): ScriptResult =
        releaseOf("        versionCode = 3", "        versionName = \"$version\"")

    /** Raises versionName from 1.0.0 to 1.1.0 and sets versionCode from [previousCode] to [versionCode]. */
    private fun releaseAfter(
        previousCode: Int,
        versionCode: Int,
    ): ScriptResult =
        run(
            buildFile("        versionCode = $versionCode", "        versionName = \"1.1.0\""),
            buildFile("        versionCode = $previousCode", "        versionName = \"1.0.0\""),
        )

    // The release workflow reads the real build file; reformatting its version lines must fail here, not there.
    @Test
    fun `reads the release from the app's own build file`() {
        val result = runScript("scripts/release-version.sh")
        val release = outputs(result)

        assertEquals(result.stdout, 0, result.exitCode)
        assertTrue(result.stdout, release.getValue("version").matches(Regex("""\d+\.\d+\.\d+(-[0-9A-Za-z.-]+)?""")))
        assertEquals("v" + release.getValue("version"), release.getValue("tag"))
        assertTrue(result.stdout, release.getValue("version_code").matches(Regex("""[1-9]\d*""")))
    }

    @Test
    fun `a plain version is a full release`() {
        val result =
            releaseOf(
                "android {",
                "    defaultConfig {",
                "        versionCode = 12",
                "        versionName = \"1.2.3\"",
                "    }",
                "}",
            )

        assertEquals(result.stdout, 0, result.exitCode)
        assertEquals(
            mapOf("version" to "1.2.3", "tag" to "v1.2.3", "prerelease" to "false", "version_code" to "12"),
            outputs(result),
        )
    }

    @Test
    fun `a version with a suffix is a pre-release`() {
        val result = releaseNamed("0.2.0-beta.1")

        assertEquals(
            mapOf("version" to "0.2.0-beta.1", "tag" to "v0.2.0-beta.1", "prerelease" to "true", "version_code" to "3"),
            outputs(result),
        )
    }

    @Test
    fun `accepts any digit in each part of the version`() {
        for (version in listOf("9.9.0", "1.0.9-0")) {
            val result = releaseNamed(version)

            assertEquals(version, 0, result.exitCode)
            assertEquals(version, outputs(result)["version"])
        }
    }

    @Test
    fun `fails unless there is exactly one versionName`() {
        val none = releaseOf("        versionCode = 2")
        val two =
            releaseOf("        versionCode = 2", "        versionName = \"1.0.0\"", "        versionName = \"1.0.1\"")

        assertEquals(1, none.exitCode)
        assertEquals(1, two.exitCode)
        assertTrue(none.stdout, none.stdout.startsWith("::error::"))
    }

    // The release notes pick the changelog by versionCode, and a release compares it with the last release's.
    @Test
    fun `fails unless there is exactly one numeric versionCode`() {
        val none = releaseOf("        versionName = \"1.0.0\"")
        val two = releaseOf("        versionCode = 2", "        versionCode = 3", "        versionName = \"1.0.0\"")
        val notANumber = releaseOf("        versionCode = two", "        versionName = \"1.0.0\"")
        // A trailing comment would reach $GITHUB_OUTPUT as version_code=3 // was 2.
        val trailingText = releaseOf("        versionCode = 3 // was 2", "        versionName = \"1.0.0\"")

        for (result in listOf(none, two, notANumber, trailingText)) {
            assertEquals(result.stdout, 1, result.exitCode)
            assertTrue(result.stdout, result.stdout.startsWith("::error::"))
            assertTrue(result.stdout, "versionCode" in result.stdout)
        }
    }

    // Android requires a versionCode above 0 (#442).
    @Test
    fun `refuses versionCode 0`() {
        val result = releaseOf("        versionCode = 0", "        versionName = \"1.0.0\"")

        assertEquals(result.stdout, 1, result.exitCode)
        assertTrue(result.stdout, result.stdout.startsWith("::error::"))
        assertTrue(result.stdout, "versionCode '0'" in result.stdout)
    }

    // The version becomes a git tag and a release name; anything but MAJOR.MINOR.PATCH[-suffix] is refused.
    @Test
    fun `refuses a version that is not semantic`() {
        val result = releaseNamed("1.0 beta")

        assertEquals(1, result.exitCode)
        assertTrue(result.stdout, result.stdout.startsWith("::error::"))
    }

    // The tag is v<version>, so a leading v would tag vv1.0.0, and trailing text would put a space or + in the tag.
    @Test
    fun `refuses a semantic version with text before or after it`() {
        for (version in listOf("v1.0.0", "1.0.0 beta", "1.0.0+build.1")) {
            val result = releaseNamed(version)

            assertEquals(version, 1, result.exitCode)
            assertTrue(result.stdout, result.stdout.startsWith("::error::versionName '$version'"))
        }
    }

    @Test
    fun `passes when versionCode rose since the last release`() {
        val result = releaseAfter(previousCode = 6, versionCode = 7)

        assertEquals(result.stdout, 0, result.exitCode)
        assertEquals("7", outputs(result)["version_code"])
    }

    // Android refuses to install a lower versionCode over the release a user has, and updaters that compare versionCode
    // (F-Droid, IzzyOnDroid, Obtainium) never offer an equal one (#437).
    @Test
    fun `refuses a versionCode that is not higher than the last release's`() {
        for (versionCode in listOf(5, 6)) {
            val result = releaseAfter(previousCode = 6, versionCode = versionCode)

            assertEquals(result.stdout, 1, result.exitCode)
            val error = result.stdout.lines().single { it.startsWith("::error::") }
            assertTrue(error, "versionCode $versionCode" in error && "versionCode 6" in error)
        }
    }

    @Test
    fun `fails when the last release's build file has no single versionCode`() {
        val previous = buildFile("        versionName = \"1.0.0\"")

        val result = run(buildFile("        versionCode = 7", "        versionName = \"1.1.0\""), previous)

        assertEquals(result.stdout, 1, result.exitCode)
        assertTrue(result.stdout, result.stdout.lines().any { it.startsWith("::error::") && previous.path in it })
    }
}
