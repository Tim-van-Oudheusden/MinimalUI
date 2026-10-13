package dev.buildwithtim.minimalui.tooling

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QualityReportWorkflowTest {

    private val workflow = Repo.workflows().getValue("quality-report.yml")
    private val steps = workflow.jobs().values.single().steps()

    @Test
    fun `runs on pushes to main, weekly on Monday 06-17 UTC, and on demand`() {
        val on = workflow.getValue("on").asMap()

        assertEquals(setOf("push", "schedule", "workflow_dispatch"), workflow.triggers())
        assertEquals(listOf("main"), on.getValue("push").asMap().getValue("branches"))
        assertEquals("17 6 * * 1", on.getValue("schedule").asList().single().asMap()["cron"])
    }

    // A failing gate fails the report step; the report, the tuned floors and the summary must still be published.
    @Test
    fun `publishes the report and the tuned floors even when a gate fails`() {
        val uploads = steps.filter { it["uses"].toString().startsWith("actions/upload-artifact@") }
            .associate { step ->
                val with = step.getValue("with").asMap()
                with["name"].toString() to with["path"].toString()
            }

        assertEquals(
            mapOf("quality-report" to "quality-report.md", "auto-qa-tuning" to ".github/auto-qa-tuning.json"),
            uploads,
        )
        val afterReport = steps.dropWhile { !it["run"].toString().contains("scripts/quality-report.sh") }.drop(1)
        assertTrue(afterReport.isNotEmpty())
        for (step in afterReport) {
            assertEquals(step.toString(), "always()", step["if"])
        }
        val tuner = "scripts/auto-qa-tuner.sh --report quality-report.md --write"
        assertTrue(afterReport.any { it["run"].toString().contains(tuner) })
        assertTrue(afterReport.any { it["run"].toString().contains("\$GITHUB_STEP_SUMMARY") })
    }

    // Gradle runs main's code and the job pushes nothing, so the token must not stay in .git/config.
    @Test
    fun `checks out without persisting the token`() {
        val checkout = steps.single { it["uses"].toString().startsWith("actions/checkout@") }

        assertEquals("false", checkout["with"]?.asMap()?.get("persist-credentials").toString())
    }
}
