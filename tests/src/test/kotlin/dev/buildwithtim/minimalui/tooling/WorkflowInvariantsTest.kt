package dev.buildwithtim.minimalui.tooling

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Security and reliability rules every workflow under `.github/workflows` must keep. Their `GITHUB_TOKEN` scopes are
 * held to `.github/policies/workflow-permissions.yml` by [WorkflowPermissionsPolicyTest].
 */
class WorkflowInvariantsTest {
    private val workflows = Repo.workflows()

    // A job without timeout-minutes runs for GitHub's six-hour default when a step hangs.
    @Test
    fun `bounds every job with timeout-minutes`() {
        val unbounded =
            workflows.flatMap { (name, workflow) ->
                workflow
                    .jobs()
                    .filterValues { it["timeout-minutes"] !is Int }
                    .keys
                    .map { "$name: $it" }
            }

        assertEquals(emptyList<String>(), unbounded)
    }

    // A tag can be moved to different code after review; a commit SHA cannot.
    @Test
    fun `pins every action to a full commit SHA`() {
        val pinned = Regex("[A-Za-z0-9_.-]+/[A-Za-z0-9_./-]+@[0-9a-f]{40}")
        val unpinned =
            workflows.flatMap { (name, workflow) ->
                workflow
                    .jobs()
                    .values
                    .flatMap { job -> job.steps().mapNotNull { it["uses"] } + listOfNotNull(job["uses"]) }
                    .map { it.toString() }
                    .filterNot { it.startsWith("./") || pinned.matches(it) }
                    .map { "$name: $it" }
            }

        assertEquals(emptyList<String>(), unpinned)
    }

    // Event text is attacker-controlled; interpolated into a script it becomes code. Pass it through env: instead.
    @Test
    fun `never interpolates event payload into run scripts`() {
        val untrusted = Regex("""\$\{\{[^}]*\b(github\.event\.|github\.head_ref)""")
        val injectable =
            workflows.flatMap { (name, workflow) ->
                workflow.jobs().flatMap { (jobName, job) ->
                    job
                        .steps()
                        .filter { untrusted.containsMatchIn(it["run"]?.toString().orEmpty()) }
                        .map { "$name: $jobName: ${it["name"] ?: it["run"]}" }
                }
            }

        assertEquals(emptyList<String>(), injectable)
    }

    // pull_request_target runs with base-branch secrets and a write token; checking out PR code there runs it.
    @Test
    fun `never checks out code in a pull_request_target workflow`() {
        val targetWorkflows = workflows.filterValues { "pull_request_target" in it.triggers() }

        assertEquals(setOf("labeler.yml"), targetWorkflows.keys)
        targetWorkflows.forEach { (name, workflow) ->
            val uses = workflow.jobs().values.flatMap { job -> job.steps().mapNotNull { it["uses"]?.toString() } }
            assertTrue("$name checks out code: $uses", uses.none { it.startsWith("actions/checkout@") })
        }
    }
}
