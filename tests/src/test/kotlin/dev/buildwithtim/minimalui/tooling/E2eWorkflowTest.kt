package dev.buildwithtim.minimalui.tooling

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** `.github/workflows/e2e.yml`, whose job is a required check on `main` (#241). */
class E2eWorkflowTest {
    private val e2e = Repo.workflows().getValue("e2e.yml")

    // A required check must report on every pull request: a path filter that skips one leaves it waiting forever.
    @Test
    fun `runs on every pull request and on pushes to main`() {
        val on = e2e.getValue("on").asMap()

        assertEquals(setOf("pull_request", "push"), e2e.triggers())
        assertEquals(null, on["pull_request"])
        assertEquals(listOf("main"), on.getValue("push").asMap().getValue("branches"))
    }

    // The ruleset names the check by job name; renaming the job silently drops the gate from required checks.
    @Test
    fun `the main ruleset requires the end-to-end job`() {
        val jobName = e2e.jobs().values.single()["name"]
        val ruleset = Repo.read(".github/rulesets/main.json")

        assertEquals("End-to-end tests", jobName)
        assertTrue(ruleset, ruleset.contains("""{ "context": "$jobName", "integration_id": 15368 }"""))
    }
}
