package dev.buildwithtim.minimalui.tooling

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseWorkflowTest {
    private val release = Repo.workflows().getValue("release.yml")

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
}
