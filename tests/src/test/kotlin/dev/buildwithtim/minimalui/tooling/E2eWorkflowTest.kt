package dev.buildwithtim.minimalui.tooling

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** `.github/workflows/e2e.yml`, whose job is a required check on `main` (#241). */
class E2eWorkflowTest {
    private val e2e = Repo.workflows().getValue("e2e.yml")

    // A required check must report on every pull request: a path filter that skips one leaves it waiting forever.
    // workflow_dispatch: the merge queue starts main's run, since its merges trigger no push run.
    @Test
    fun `runs on every pull request, on pushes to main and when the merge queue starts it`() {
        val on = e2e.getValue("on").asMap()

        assertEquals(setOf("pull_request", "push", "workflow_dispatch"), e2e.triggers())
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

    // The job installs the managed device's system image itself so that a bad download is retried (#307). Once the
    // device changes, a stale image loses that retry without failing anything: Gradle downloads the right one, once.
    @Test
    fun `installs the system image of the managed device`() {
        val device = Repo.read("e2e/build.gradle.kts")

        fun setting(name: String) =
            checkNotNull(Regex("""\b$name = "?([\w-]+)"?""").find(device)) { "no $name in e2e/build.gradle.kts" }
                .groupValues[1]
        val installed =
            e2e
                .jobs()
                .values
                .single()
                .steps()
                .firstNotNullOf { it["env"]?.asMap()?.get("SYSTEM_IMAGE") }
        val source = setting("systemImageSource").replace('-', '_')

        assertEquals("system-images;android-${setting("apiLevel")};$source;${setting("testedAbi")}", installed)
    }
}
