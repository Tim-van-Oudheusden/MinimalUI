package dev.buildwithtim.minimalui.tooling

import org.junit.Assert.assertEquals
import org.junit.Test

class AutoIssuesWorkflowTest {
    private val workflows = Repo.workflows()
    private val workflowRun =
        workflows
            .getValue("auto-issues.yml")
            .getValue("on")
            .asMap()
            .getValue("workflow_run")
            .asMap()

    @Test
    fun `reacts only to finished runs on main`() {
        assertEquals(setOf("workflow_run"), workflows.getValue("auto-issues.yml").triggers())
        assertEquals(listOf("completed"), workflowRun["types"])
        assertEquals(listOf("main"), workflowRun["branches"])
    }

    // workflow_run matches workflows by name: a new or renamed workflow on main would otherwise fail unnoticed.
    @Test
    fun `watches every workflow that runs on main by name`() {
        val onMain =
            workflows
                .filterKeys { it != "auto-issues.yml" }
                .values
                .filter { workflow ->
                    val on = workflow.getValue("on")
                    val push = (on as? Map<*, *>)?.get("push")?.asMap()
                    "schedule" in workflow.triggers() ||
                        ("push" in workflow.triggers() && (push?.get("branches")?.asList()?.contains("main") ?: true))
                }.map { it.getValue("name").toString() }
                .toSet()

        assertEquals(
            onMain,
            workflowRun
                .getValue("workflows")
                .asList()
                .map { it.toString() }
                .toSet(),
        )
    }
}
