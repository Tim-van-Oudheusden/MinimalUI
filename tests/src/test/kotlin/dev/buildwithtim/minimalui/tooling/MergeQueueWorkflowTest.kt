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

    // pick needs merge state CLEAN, which also waits for checks that are not required, such as CodeQL. When one of
    // those is the last check to pass, no watched workflow finishes after it, so a ready pull request would wait for
    // an unrelated event or a person (#373). A schedule starts the queue so it looks again.
    @Test
    fun `is started on a schedule, for a pull request whose last check to pass is not required`() {
        val sweep = workflows.getValue("merge-queue-sweep.yml")
        val runs =
            sweep
                .jobs()
                .values
                .flatMap { it.steps() }
                .mapNotNull { it["run"]?.toString()?.trim() }

        assertEquals(setOf("schedule"), sweep.triggers())
        assertEquals(listOf("gh workflow run merge-queue.yml --ref main"), runs)
        assertTrue("merge-queue.yml accepts workflow_dispatch", "workflow_dispatch" in queue.triggers())
    }

    // GitHub expressions cannot read the script's KEEP_OUT_LABELS, so the workflow restates them twice. Removing a
    // label the script keeps out but `pick` does not know starts no run; a concurrency group that disagrees with `pick`
    // lets a run that picks nothing replace a pending one that would merge, or lets two picking runs overlap.
    @Test
    fun `runs and joins the queue's group when a label the script keeps out comes off`() {
        val keepOut =
            Regex("""(?m)^KEEP_OUT_LABELS=\(([^)]*)\)$""")
                .find(Repo.read("scripts/merge-queue.sh"))
                ?.groupValues
                ?.get(1)
                ?.trim()
                ?.split(Regex("""\s+"""))
                ?.toSet()
        val fromJson = Regex("""fromJSON\('\[([^\]]*)\]'\)""")
        val label = Regex(""""([^"]+)"""")
        val sites =
            mapOf(
                "concurrency group" to queue.getValue("concurrency").asMap().getValue("group"),
                "pick job if" to queue.jobs().getValue("pick").getValue("if"),
            )

        assertTrue("KEEP_OUT_LABELS=(...) in scripts/merge-queue.sh", !keepOut.isNullOrEmpty())
        sites.forEach { (site, expression) ->
            val lists =
                fromJson
                    .findAll(expression.toString())
                    .map { list -> label.findAll(list.groupValues[1]).map { it.groupValues[1] }.toSet() }
                    .toList()
            assertEquals(site, listOf(keepOut), lists)
        }
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

    // Verification runs on a main-scoped event, so setup-gradle would save the Gradle home the pull request's code just
    // ran in to main's cache, which release.yml (signing secrets) and claude.yml restore. It may read that cache, never
    // write it; setup-java gets no `cache:` for the same reason.
    @Test
    fun `verifies without saving to main's Gradle or JDK cache`() {
        val steps = queue.jobs().getValue("verify").steps()
        val setupGradle = steps.filter { it["uses"].toString().startsWith("gradle/actions/setup-gradle@") }
        val setupJava = steps.filter { it["uses"].toString().startsWith("actions/setup-java@") }

        assertEquals(1, setupGradle.size)
        assertEquals(
            "true",
            setupGradle
                .single()["with"]
                ?.asMap()
                ?.get("cache-read-only")
                .toString(),
        )
        setupJava.forEach { assertEquals(null, it["with"]?.asMap()?.get("cache")) }
    }

    // The queue takes only merge state CLEAN, which any red check blocks. A failed API call on a convenience label
    // would keep a ready pull request out of the queue until someone re-runs the check.
    @Test
    fun `is not blocked by a failed labeler step`() {
        val steps =
            workflows
                .getValue("labeler.yml")
                .jobs()
                .getValue("label")
                .steps()

        assertEquals(listOf(true), steps.map { it["continue-on-error"] })
    }
}
