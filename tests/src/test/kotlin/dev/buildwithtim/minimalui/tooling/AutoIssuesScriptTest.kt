package dev.buildwithtim.minimalui.tooling

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class AutoIssuesScriptTest {
    private val dir: File = Files.createTempDirectory("auto-issues").toFile()
    private val calls = dir.resolve("gh-calls.txt")
    private val body = dir.resolve("issue-body.md")
    private val openIssues = dir.resolve("open-issues.json")

    /**
     * Fake `gh` first on PATH: logs each call's arguments tab-separated on one line, answers `issue list` with
     * [openIssues], and keeps the body `issue create` reads from stdin in [body].
     */
    private val bin =
        dir.resolve("bin").apply {
            mkdirs()
            resolve("gh").apply {
                writeText(
                    """
                    #!/usr/bin/env bash
                    (IFS=${'$'}'\t'; printf '%s\n' "${'$'}*") >> "${calls.path}"
                    case "${'$'}1 ${'$'}2" in
                      "issue list") cat "${openIssues.path}" ;;
                      "issue create") cat > "${body.path}"; echo "https://github.com/owner/minimalui/issues/99" ;;
                    esac

                    """.trimIndent(),
                )
                setExecutable(true)
            }
        }

    @After
    fun cleanUp() {
        dir.deleteRecursively()
    }

    private val runUrl = "https://github.com/owner/minimalui/actions/runs/42"
    private val sha = "0123456789abcdef0123456789abcdef01234567"

    private fun autoIssues(
        conclusion: String,
        workflow: String = "CI",
        open: String = "[]",
        env: Map<String, String> = emptyMap(),
    ): ScriptResult {
        openIssues.writeText(open)
        return runScript(
            "scripts/auto-issues.sh",
            env =
                mapOf(
                    "PATH" to "${bin.path}:${System.getenv("PATH")}",
                    "GITHUB_REPOSITORY" to "owner/minimalui",
                    "WORKFLOW_NAME" to workflow,
                    "CONCLUSION" to conclusion,
                    "RUN_URL" to runUrl,
                    "HEAD_SHA" to sha,
                ) + env,
        )
    }

    private fun calls(): List<List<String>> =
        if (calls.exists()) calls.readLines().map { it.split("\t") } else emptyList()

    /** The calls that change an issue: everything but the `issue list` lookup. */
    private fun changes() = calls().filterNot { it.take(2) == listOf("issue", "list") }

    private fun List<String>.option(name: String) = this[indexOf(name) + 1]

    private fun issue(
        number: Int,
        title: String,
    ) = """{"number": $number, "title": "$title"}"""

    @Test
    fun `opens a ci issue naming the run and commit when a workflow fails and none is open`() {
        val result = autoIssues("failure")

        assertEquals(result.stderr, 0, result.exitCode)
        val create = changes().single()
        assertEquals(listOf("issue", "create"), create.take(2))
        assertEquals("owner/minimalui", create.option("--repo"))
        assertEquals("CI fails on main", create.option("--title"))
        assertEquals("ci", create.option("--label"))
        val text = body.readText()
        assertTrue(text, text.contains(runUrl))
        assertTrue(text, text.contains(sha))
    }

    @Test
    fun `comments on the open issue instead of opening a second one`() {
        val result = autoIssues("failure", open = "[${issue(7, "CI fails on main")}]")

        assertEquals(result.stderr, 0, result.exitCode)
        val comment = changes().single()
        assertEquals(listOf("issue", "comment", "7"), comment.take(3))
        assertTrue(comment.toString(), comment.option("--body").contains(runUrl))
    }

    @Test
    fun `matches the open issue by its exact title`() {
        val open = "[${issue(12, "E2E fails on main")}, ${issue(13, "CI fails on main (flaky)")}]"

        val result = autoIssues("failure", open = open)

        assertEquals(result.stderr, 0, result.exitCode)
        assertEquals(listOf("issue", "create"), changes().single().take(2))
    }

    @Test
    fun `treats a timed-out run as a failure`() {
        autoIssues("timed_out", workflow = "E2E")

        assertEquals("E2E fails on main", changes().single().option("--title"))
    }

    @Test
    fun `closes the open issue with a link to the run once the workflow passes again`() {
        val result = autoIssues("success", open = "[${issue(7, "CI fails on main")}]")

        assertEquals(result.stderr, 0, result.exitCode)
        val close = changes().single()
        assertEquals(listOf("issue", "close", "7"), close.take(3))
        assertTrue(close.toString(), close.option("--comment").contains(runUrl))
    }

    @Test
    fun `changes nothing when the workflow passes and no issue is open`() {
        val result = autoIssues("success", open = "[${issue(12, "E2E fails on main")}]")

        assertEquals(result.stderr, 0, result.exitCode)
        assertEquals(emptyList<List<String>>(), changes())
    }

    // A cancelled run (superseded by a newer push, or stopped by hand) says nothing about main.
    @Test
    fun `leaves the issue alone when the run was cancelled`() {
        val result = autoIssues("cancelled", open = "[${issue(7, "CI fails on main")}]")

        assertEquals(result.stderr, 0, result.exitCode)
        assertEquals(emptyList<List<String>>(), changes())
    }

    @Test
    fun `refuses to run without the run's details`() {
        val result = autoIssues("failure", env = mapOf("RUN_URL" to ""))

        assertEquals(2, result.exitCode)
        assertTrue(result.stderr, result.stderr.contains("RUN_URL"))
        assertEquals(emptyList<List<String>>(), calls())
    }
}
