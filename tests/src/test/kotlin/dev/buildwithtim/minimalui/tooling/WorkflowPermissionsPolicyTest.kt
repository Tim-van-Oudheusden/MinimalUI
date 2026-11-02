package dev.buildwithtim.minimalui.tooling

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Evaluates `.github/policies/workflow-permissions.yml`, the policy for what `GITHUB_TOKEN` may do in each workflow
 * under `.github/workflows`. The policy file documents its rule format.
 */
class WorkflowPermissionsPolicyTest {
    private val workflows = Repo.workflows()
    private val policy = Yaml.parse(Repo.read(".github/policies/workflow-permissions.yml")).asMap()
    private val rules = policy.getValue("workflows").asMap().mapValues { (_, rule) -> rule?.asMap().orEmpty() }

    private fun rule(workflow: String): Map<String, Any?> = rules[workflow].orEmpty()

    @Test
    fun `has a rule for every workflow, and only for workflows that exist`() {
        assertEquals(workflows.keys, rules.keys)
    }

    @Test
    fun `grants every workflow exactly the top-level token scopes its rule allows`() {
        val allowed = workflows.keys.associateWith { rule(it)["permissions"] ?: policy.getValue("default") }

        assertEquals(allowed, workflows.mapValues { (_, workflow) -> workflow["permissions"] })
    }

    @Test
    fun `lets only the jobs its rule lists declare token scopes, and exactly those`() {
        val allowed = workflows.keys.associateWith { rule(it)["jobs"]?.asMap().orEmpty() }
        val declared =
            workflows.mapValues { (_, workflow) ->
                workflow.jobs().filterValues { it.containsKey("permissions") }.mapValues { it.value["permissions"] }
            }

        assertEquals(allowed, declared)
    }
}
