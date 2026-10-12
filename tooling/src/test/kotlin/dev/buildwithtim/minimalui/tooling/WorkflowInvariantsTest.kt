package dev.buildwithtim.minimalui.tooling

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Security and reliability rules every workflow under `.github/workflows` must keep. */
class WorkflowInvariantsTest {

    private val workflows = Repo.workflows()

    /**
     * Reviewed GITHUB_TOKEN scopes. A workflow starts read-only and a job asks for more in its own block; adding a
     * workflow or widening a scope means editing these maps, so the change is reviewed rather than slipped in.
     */
    private val topLevelPermissions = mapOf(
        "ci.yml" to mapOf("contents" to "read"),
        "claude.yml" to mapOf("contents" to "read"),
        // Sanctioned pull_request_target exception: labels PRs, never checks out code.
        "labeler.yml" to mapOf("contents" to "read", "pull-requests" to "write"),
        "nightly-compliance.yml" to mapOf("contents" to "read"),
        "review.yml" to mapOf("contents" to "read"),
    )

    private val jobPermissions = mapOf(
        "claude.yml" to mapOf(
            "claude" to mapOf(
                "contents" to "write",
                "pull-requests" to "write",
                "issues" to "write",
                "id-token" to "write",
                "actions" to "read",
            ),
        ),
        "nightly-compliance.yml" to mapOf(
            // The dependency submission API writes the graph through contents: write.
            "dependency-graph" to mapOf("contents" to "write"),
            // vulnerability-alerts: read lists Dependabot alerts.
            "compliance" to mapOf("contents" to "read", "vulnerability-alerts" to "read"),
        ),
    )

    @Test
    fun `every workflow declares its reviewed top-level token scopes`() {
        assertEquals(topLevelPermissions, workflows.mapValues { (_, workflow) -> workflow["permissions"] })
    }

    @Test
    fun `only reviewed jobs widen the token`() {
        val widened = workflows
            .mapValues { (_, workflow) ->
                workflow.jobs().filterValues { it.containsKey("permissions") }.mapValues { it.value["permissions"] }
            }
            .filterValues { it.isNotEmpty() }

        assertEquals(jobPermissions, widened)
    }

    // A job without timeout-minutes runs for GitHub's six-hour default when a step hangs.
    @Test
    fun `bounds every job with timeout-minutes`() {
        val unbounded = workflows.flatMap { (name, workflow) ->
            workflow.jobs().filterValues { it["timeout-minutes"] !is Int }.keys.map { "$name: $it" }
        }

        assertEquals(emptyList<String>(), unbounded)
    }

    // A tag can be moved to different code after review; a commit SHA cannot.
    @Test
    fun `pins every action to a full commit SHA`() {
        val pinned = Regex("[A-Za-z0-9_.-]+/[A-Za-z0-9_./-]+@[0-9a-f]{40}")
        val unpinned = workflows.flatMap { (name, workflow) ->
            workflow.jobs().values.flatMap { job -> job.steps().mapNotNull { it["uses"] } + listOfNotNull(job["uses"]) }
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
        val injectable = workflows.flatMap { (name, workflow) ->
            workflow.jobs().flatMap { (jobName, job) ->
                job.steps().filter { untrusted.containsMatchIn(it["run"]?.toString().orEmpty()) }
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
