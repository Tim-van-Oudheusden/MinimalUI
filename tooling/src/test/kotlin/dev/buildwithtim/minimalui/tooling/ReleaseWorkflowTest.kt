package dev.buildwithtim.minimalui.tooling

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class ReleaseWorkflowTest {
    private val release = Repo.workflows().getValue("release.yml")
    private val releaseSteps = release.jobs().getValue("release").steps()
    private val secretsCheck = releaseSteps.first()

    /** Runs the release job's Check signing secrets step with [env], as GitHub's bash shell does. */
    private fun checkSecrets(env: Map<String, String>): Pair<Int, String> {
        val process =
            ProcessBuilder("bash", "-eo", "pipefail", "-c", secretsCheck["run"].toString())
                .redirectErrorStream(true)
                .apply { environment().putAll(env) }
                .start()
        check(process.waitFor(60, TimeUnit.SECONDS)) { "Check signing secrets did not finish within 60 s" }
        return process.exitValue() to process.inputStream.bufferedReader().readText()
    }

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
}
