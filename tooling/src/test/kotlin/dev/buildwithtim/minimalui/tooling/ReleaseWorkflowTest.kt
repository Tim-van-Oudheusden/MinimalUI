package dev.buildwithtim.minimalui.tooling

import org.junit.Assert.assertEquals
import org.junit.Test

class ReleaseWorkflowTest {

    private val release = Repo.workflows().getValue("release.yml")

    // The job holds the signing key and a write token; it must run for a pushed version tag and nothing else.
    @Test
    fun `runs only when a version tag is pushed`() {
        val push = release.getValue("on").asMap().getValue("push").asMap()

        assertEquals(setOf("push"), release.triggers())
        assertEquals(mapOf("tags" to listOf("v*")), push)
    }

    // Gradle runs the tagged code while the job holds the signing key, so the token must not stay in .git/config.
    @Test
    fun `checks out without persisting the token`() {
        val checkouts = release.jobs().values.flatMap { it.steps() }
            .filter { it["uses"].toString().startsWith("actions/checkout@") }

        assertEquals(1, checkouts.size)
        assertEquals("false", checkouts.single()["with"]?.asMap()?.get("persist-credentials").toString())
    }
}
