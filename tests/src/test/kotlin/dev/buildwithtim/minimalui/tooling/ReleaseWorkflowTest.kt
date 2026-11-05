package dev.buildwithtim.minimalui.tooling

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ReleaseWorkflowTest {
    private val release = Repo.workflows().getValue("release.yml")
    private val releaseSteps = release.jobs().getValue("release").steps()
    private val secretsCheck = releaseSteps.first()
    private val publish = releaseSteps.single { it["name"] == "Publish release" }

    private val dir: File = Files.createTempDirectory("release-workflow").toFile()
    private val notes = dir.resolve("notes.txt")
    private val ghArgs = dir.resolve("gh-args.txt")

    /** Fake `gh` first on PATH: logs its arguments one per line to [ghArgs] and the `--notes` value to [notes]. */
    private val bin =
        dir.resolve("bin").apply {
            mkdirs()
            resolve("gh").apply {
                writeText(
                    """
                    #!/usr/bin/env bash
                    set -euo pipefail
                    printf '%s\n' "${'$'}@" >> "${ghArgs.path}"
                    while [ ${'$'}# -gt 0 ]; do
                      if [ "${'$'}1" = --notes ]; then printf '%s' "${'$'}2" > "${notes.path}"; fi
                      shift
                    done

                    """.trimIndent(),
                )
                setExecutable(true)
            }
        }

    /** A checkout for the Publish release step: a build file with [versionCode], a signed APK and [changelogs]. */
    private val checkout = dir.resolve("checkout")

    private fun checkout(
        versionCode: Int,
        changelogs: Map<Int, String>,
    ) {
        checkout.resolve("app").mkdirs()
        checkout.resolve("app/build.gradle.kts").writeText(
            "android {\n    defaultConfig {\n        versionCode = $versionCode\n    }\n}\n",
        )
        checkout
            .resolve("app/build/outputs/apk/release")
            .apply { mkdirs() }
            .resolve("app-release.apk")
            .writeText("apk")
        val changelogDir = checkout.resolve("fastlane/metadata/android/en-US/changelogs").apply { mkdirs() }
        changelogs.forEach { (code, text) -> changelogDir.resolve("$code.txt").writeText(text) }
    }

    @After
    fun cleanUp() {
        dir.deleteRecursively()
    }

    /** Runs the release job's [step] in [workDir] with [env], as GitHub's bash shell does. */
    private fun runStep(
        step: Map<String, Any?>,
        env: Map<String, String>,
        workDir: File = dir,
    ): Pair<Int, String> {
        val result =
            runProcess(
                listOf("bash", "-eo", "pipefail", "-c", step["run"].toString()),
                workDir,
                env,
                mergeStderr = true,
                name = step["name"].toString(),
            )
        return result.exitCode to result.stdout
    }

    private fun checkSecrets(env: Map<String, String>): Pair<Int, String> = runStep(secretsCheck, env)

    private fun publish(): Pair<Int, String> =
        runStep(
            publish,
            mapOf(
                "PATH" to "${bin.path}:${System.getenv("PATH")}",
                "GH_REPO" to "owner/MinimalUI",
                "TAG" to "v9.9.9",
                "PRERELEASE" to "false",
                "GITHUB_SHA" to "0123abc",
            ),
            checkout,
        )

    // The jobs hold the signing key and a write token; they must never run for a pull request. A version bump reaches
    // main only through the build file, so other pushes don't start a release.
    @Test
    fun `runs for build file changes on main and by hand, never for pull requests`() {
        val push =
            release
                .getValue("on")
                .asMap()
                .getValue("push")
                .asMap()

        assertEquals(setOf("push", "workflow_dispatch"), release.triggers())
        assertEquals(mapOf("branches" to listOf("main"), "paths" to listOf("app/build.gradle.kts")), push)
    }

    // Gradle runs main's code while a job holds the signing key, so the token must not stay in .git/config.
    @Test
    fun `checks out without persisting the token`() {
        val persisted =
            release
                .jobs()
                .values
                .flatMap { it.steps() }
                .filter { it["uses"].toString().startsWith("actions/checkout@") }
                .map { it["with"]?.asMap()?.get("persist-credentials").toString() }

        assertTrue(persisted.isNotEmpty())
        assertEquals(listOf("false"), persisted.distinct())
    }

    // A release is held to the same gate as a pull request before the signed APK is built.
    @Test
    fun `runs the check gate before building the release`() {
        val tasks = release.jobs().getValue("release").gradleTasks()

        assertTrue("./gradlew tasks: $tasks", "check" in tasks)
        assertTrue("./gradlew tasks: $tasks", tasks.indexOf("check") < tasks.indexOf("assembleRelease"))
    }

    // CONTRIBUTING.md#the-release-key and docs/runbooks/release-key.md rely on this: a missing secret is named and
    // stops the job before it checks out or builds anything, so nothing is published. So the check comes first and
    // looks at every secret the later steps use.
    @Test
    fun `checks every signing secret first`() {
        val used =
            releaseSteps
                .drop(1)
                .flatMap { Regex("""secrets\.(\w+)""").findAll(it.toString()).map { match -> match.groupValues[1] } }
                .toSet()

        assertEquals("Check signing secrets", secretsCheck["name"])
        assertEquals(4, used.size)
        assertEquals(used.associateWith { "\${{ secrets.$it }}" }, secretsCheck.getValue("env").asMap())
    }

    @Test
    fun `names each missing signing secret and fails`() {
        val names = secretsCheck.getValue("env").asMap().keys

        assertEquals(0, checkSecrets(names.associateWith { "set" }).first)
        for (name in names) {
            val (exitCode, output) = checkSecrets(names.associateWith { if (it == name) "" else "set" })

            assertEquals(output, 1, exitCode)
            assertEquals("::error::Missing repository secrets: $name. See CONTRIBUTING.md#the-release-key.\n", output)
        }
        val (exitCode, output) = checkSecrets(names.associateWith { "" })
        assertEquals(output, 1, exitCode)
        assertTrue(output, names.all { it in output })
    }

    // The release page is the only install path (#349): it opens with what changed for users, the changelog written for
    // this versionCode, then the install note, and keeps GitHub's generated list of merged pull requests below them.
    @Test
    fun `release notes open with the changelog of the released versionCode`() {
        checkout(
            versionCode = 7,
            changelogs =
                mapOf(
                    6 to "Older release.\n",
                    7 to "Search finds apps by nickname.\n- Fixed a crash.\n",
                ),
        )

        val (exitCode, output) = publish()

        assertEquals(output, 0, exitCode)
        val text = notes.readText()
        assertTrue(
            text,
            text.startsWith(
                "Search finds apps by nickname.\n- Fixed a crash.\n\nInstall `minimalui-v9.9.9.apk` on the phone;",
            ),
        )
        assertTrue(text, "https://github.com/owner/MinimalUI/blob/v9.9.9/DEVICES.md" in text)
        assertTrue(ghArgs.readText(), "--generate-notes" in ghArgs.readLines())
    }

    // FastlaneMetadataTest stops a release without a changelog at the check gate; should one get this far anyway, the
    // step fails rather than publish a release whose notes don't say what changed.
    @Test
    fun `publishes nothing when the released versionCode has no changelog`() {
        checkout(versionCode = 7, changelogs = mapOf(6 to "Older release.\n"))

        val (exitCode, output) = publish()

        assertTrue(output, exitCode != 0)
        assertFalse("gh was called: ${ghArgs.takeIf { it.exists() }?.readText()}", ghArgs.exists())
    }
}
