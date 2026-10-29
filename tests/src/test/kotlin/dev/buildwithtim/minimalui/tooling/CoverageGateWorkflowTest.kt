package dev.buildwithtim.minimalui.tooling

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverageGateWorkflowTest {
    private val gate = Repo.workflows().getValue("coverage-gate.yml")

    // workflow_dispatch: the merge queue starts main's run, since its merges trigger no push run.
    @Test
    fun `runs on every pull request, on pushes to main and when the merge queue starts it`() {
        assertEquals(setOf("pull_request", "push", "workflow_dispatch"), gate.triggers())
        assertEquals(
            listOf("main"),
            gate
                .getValue("on")
                .asMap()
                .getValue("push")
                .asMap()
                .getValue("branches"),
        )
    }

    // The ruleset names the check by job name; renaming the job silently drops the gate from required checks.
    @Test
    fun `the main ruleset requires the coverage gate job`() {
        val jobName = gate.jobs().values.single()["name"]
        val ruleset = Repo.read(".github/rulesets/main.json")

        assertEquals("Unit-test coverage floors", jobName)
        assertTrue(ruleset, ruleset.contains("""{ "context": "$jobName", "integration_id": 15368 }"""))
    }

    // The gate runs PR code through Gradle and pushes nothing, so the token must not stay in .git/config.
    @Test
    fun `checks out without persisting the token`() {
        val checkouts =
            gate
                .jobs()
                .values
                .flatMap { it.steps() }
                .filter { it["uses"].toString().startsWith("actions/checkout@") }

        assertEquals(1, checkouts.size)
        assertEquals(
            "false",
            checkouts
                .single()["with"]
                ?.asMap()
                ?.get("persist-credentials")
                .toString(),
        )
    }
}
