package dev.buildwithtim.minimalui.tooling

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** `.github/workflows/merge-queue.yml`, which merges ready pull requests one at a time (`scripts/merge-queue.sh`). */
class MergeQueueWorkflowTest {
    private val workflows = Repo.workflows()
    private val queue = workflows.getValue("merge-queue.yml")

    // workflow_run matches workflows by name: a required check in a workflow the queue does not watch would leave a
    // pull request whose last check came from there waiting for the next unrelated run.
    @Test
    fun `runs when any workflow holding a required check finishes`() {
        val required =
            Regex(""""context": "([^"]+)"""")
                .findAll(Repo.read(".github/rulesets/main.json"))
                .map { it.groupValues[1] }
                .toSet()
        val holding =
            workflows.values
                .filter { workflow -> workflow.jobs().values.any { it["name"]?.toString() in required } }
                .map { it.getValue("name").toString() }
                .toSet()
        val workflowRun =
            queue
                .getValue("on")
                .asMap()
                .getValue("workflow_run")
                .asMap()

        assertEquals(listOf("completed"), workflowRun["types"])
        assertEquals(
            holding,
            workflowRun
                .getValue("workflows")
                .asList()
                .map { it.toString() }
                .toSet(),
        )
    }

    // Verification runs the pull request's code merged onto main: the same gate CI runs, with no token left behind.
    @Test
    fun `verifies with CI's check gate from a checkout that keeps no token`() {
        val verify = queue.jobs().getValue("verify")
        val tasks = verify.gradleTasks()
        val checkouts = verify.steps().filter { it["uses"].toString().startsWith("actions/checkout@") }

        assertTrue("./gradlew tasks: $tasks", "check" in tasks && "assembleDebug" in tasks)
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
