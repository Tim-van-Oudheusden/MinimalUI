package dev.buildwithtim.minimalui.tooling

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** `.github/workflows/ci.yml`, whose jobs are all required checks on `main`. */
class CiWorkflowTest {
    private val jobs =
        Repo
            .workflows()
            .getValue("ci.yml")
            .jobs()

    // `check` is the quality gate: unit tests, Android Lint and spotlessCheck all hang off it, and so does every check
    // added later. A job that names the tasks one by one silently skips the next one.
    @Test
    fun `runs the check gate`() {
        val tasks = jobs.getValue("build").gradleTasks()

        assertTrue("./gradlew tasks: $tasks", "check" in tasks)
    }

    // The ruleset names a check by job name; a renamed or added job that the ruleset misses stops gating merges.
    @Test
    fun `the main ruleset requires every CI job`() {
        val jobNames = jobs.values.map { it.getValue("name").toString() }
        val required =
            Regex(""""context": "([^"]+)"""")
                .findAll(Repo.read(".github/rulesets/main.json"))
                .map { it.groupValues[1] }
                .toSet()

        assertEquals(emptyList<String>(), jobNames.filterNot { it in required })
    }
}
