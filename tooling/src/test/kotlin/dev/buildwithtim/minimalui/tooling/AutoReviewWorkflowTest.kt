package dev.buildwithtim.minimalui.tooling

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit

class AutoReviewWorkflowTest {

    private val workflow = Yaml.parse(Repo.read(".github/workflows/auto-review.yml")).asMap()
    private val job = workflow.jobs().getValue("apply")
    private val steps = job.steps()

    private fun step(name: String) = steps.single { it["name"] == name }

    private fun Map<String, Any?>.env(): Map<String, Any?> = (this["env"] ?: emptyMap<String, Any?>()).asMap()

    @Test
    fun `runs only on a trusted maintainer's apply-suggestions comment on a pull request`() {
        val jobIf = job.getValue("if").toString()

        assertEquals(mapOf("issue_comment" to mapOf("types" to listOf("created"))), workflow["on"])
        assertTrue(jobIf, jobIf.contains("github.event.issue.pull_request"))
        assertTrue(jobIf, jobIf.contains("startsWith(github.event.comment.body, '/apply-suggestions')"))
        assertTrue(
            jobIf,
            jobIf.contains("""contains(fromJSON('["OWNER", "MEMBER", "COLLABORATOR"]'), github.event.comment.author_association)"""),
        )
    }

    // The PR checkout is untrusted: nothing from it may run, and nothing running in it may hold the token.
    @Test
    fun `hands GITHUB_TOKEN only to the steps that call GitHub, never to code running in the PR checkout`() {
        val token = Regex("""\$\{\{\s*(github\.token|secrets\.)""")
        val withToken = steps.filter { step -> step.env().values.any { token.containsMatchIn(it.toString()) } }
            .map { it["name"] }

        assertFalse(job.env().values.any { token.containsMatchIn(it.toString()) })
        assertEquals(
            listOf(
                "Acknowledge command",
                "Resolve pull request head",
                "Fetch review comments",
                "Push to the pull request branch",
                "Report on the pull request",
            ),
            withToken,
        )
        val inCheckout = steps.filter { it["working-directory"] == "pr" }.map { it["name"] }
        assertEquals(listOf("Apply suggestions", "Commit suggestions", "Push to the pull request branch"), inCheckout)
    }

    @Test
    fun `runs the apply script from the default branch and keeps no credentials in either checkout`() {
        val checkouts = steps.filter { it["uses"].toString().startsWith("actions/checkout@") }
        val trusted = step("Check out apply script (default branch)")["with"].asMap()

        assertEquals(2, checkouts.size)
        checkouts.forEach { assertEquals(it["name"].toString(), false, it["with"].asMap()["persist-credentials"]) }
        assertEquals(mapOf("path" to "trusted", "persist-credentials" to false), trusted)
        assertEquals("\${{ steps.pr.outputs.sha }}", step("Check out pull request head")["with"].asMap()["ref"])
        assertTrue(step("Apply suggestions")["run"].toString().contains("../trusted/scripts/apply-review-suggestions.sh"))
    }

    @Test
    fun `commits under the issue-linked message and pushes onto the checked-out head`() {
        val commit = step("Commit suggestions")["run"].toString()
        val push = step("Push to the pull request branch")

        assertTrue(commit, commit.contains("""git commit -am "chore(review): apply review suggestions (#${'$'}PR_NUMBER)""""))
        assertEquals("steps.commit.outputs.committed == 'true'", push["if"])
        assertTrue(push["run"].toString(), push["run"].toString().contains("""push origin "HEAD:refs/heads/${'$'}HEAD_REF""""))
    }

    private val workDir: File = Files.createTempDirectory("auto-review-").toFile()

    @After
    fun cleanUp() {
        workDir.deleteRecursively()
    }

    private data class StepRun(val exitCode: Int, val output: String, val stepOutputs: String)

    /** Runs the "Resolve pull request head" step against a fake `gh` that answers its two REST calls. */
    private fun resolveHead(
        commentedAt: String,
        headCommittedAt: String,
        headRepo: String = "owner/minimalui",
    ): StepRun {
        workDir.resolve("gh").apply {
            writeText(
                """
                #!/usr/bin/env bash
                set -euo pipefail
                endpoint="${'$'}2"; shift 2
                case "${'$'}endpoint" in
                  repos/*/pulls/*) response="${'$'}(cat "${'$'}FAKE_DIR/pull.json")" ;;
                  repos/*/commits/*) response="${'$'}(cat "${'$'}FAKE_DIR/commit.json")" ;;
                  *) echo "unexpected gh api ${'$'}endpoint" >&2; exit 1 ;;
                esac
                if [ "${'$'}{1:-}" = "--jq" ]; then jq -r "${'$'}2" <<< "${'$'}response"; else echo "${'$'}response"; fi
                """.trimIndent() + "\n",
            )
            setExecutable(true)
        }
        workDir.resolve("pull.json").writeText(
            """{"head":{"ref":"feat/1-thing","sha":"${"a".repeat(40)}","repo":{"full_name":"$headRepo"}}}""",
        )
        workDir.resolve("commit.json").writeText("""{"commit":{"committer":{"date":"$headCommittedAt"}}}""")
        val outputs = workDir.resolve("github-output").apply { writeText("") }
        val log = workDir.resolve("log.txt")

        val process = ProcessBuilder("bash", "-c", step("Resolve pull request head")["run"].toString())
            .directory(workDir)
            .redirectErrorStream(true)
            .redirectOutput(log)
            .apply {
                environment()["PATH"] = "${workDir.path}:${System.getenv("PATH")}"
                environment()["FAKE_DIR"] = workDir.path
                environment()["GITHUB_OUTPUT"] = outputs.path
                environment()["REPO"] = "owner/minimalui"
                environment()["PR_NUMBER"] = "7"
                environment()["COMMENT_AT"] = commentedAt
            }
            .start()
        check(process.waitFor(60, TimeUnit.SECONDS)) { "step did not finish within 60 s" }
        return StepRun(process.exitValue(), log.readText(), outputs.readText())
    }

    @Test
    fun `resolves the head ref and sha when the head was committed before the comment`() {
        val run = resolveHead("2026-10-08T12:00:00Z", "2026-10-08T11:59:59Z")

        assertEquals(run.output, 0, run.exitCode)
        assertEquals("ref=feat/1-thing\nsha=${"a".repeat(40)}\n", run.stepOutputs)
    }

    @Test
    fun `refuses a head committed after the apply-suggestions comment`() {
        val run = resolveHead("2026-10-08T12:00:00Z", "2026-10-08T12:00:01Z")

        assertNotEquals(0, run.exitCode)
        assertTrue(run.output, run.output.contains("::error::"))
        assertEquals("", run.stepOutputs)
    }

    @Test
    fun `refuses when the head commit date cannot be read`() {
        val run = resolveHead("2026-10-08T12:00:00Z", "not a date")

        assertNotEquals(0, run.exitCode)
        assertEquals("", run.stepOutputs)
    }

    @Test
    fun `refuses a pull request from a fork`() {
        val run = resolveHead("2026-10-08T12:00:00Z", "2026-10-08T11:59:59Z", headRepo = "someone/minimalui")

        assertNotEquals(0, run.exitCode)
        assertTrue(run.output, run.output.contains("::error::Fork pull requests are not supported"))
        assertEquals("", run.stepOutputs)
    }
}
