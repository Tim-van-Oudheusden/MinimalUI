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
    private val openIssue = dir.resolve("open-issue.json")
    private val compares = dir.resolve("compare").apply { mkdirs() }

    /**
     * Fake `gh` first on PATH: logs each call's arguments tab-separated on one line, answers `issue list` with
     * [openIssues] and `issue view` with [openIssue], keeps the body `issue create` reads from stdin in [body], and
     * answers `api repos/<repo>/compare/<base>...<head>` with the file `<base>...<head>` in [compares] (what
     * `--jq .status` prints), or fails as GitHub's 404 does when there is none.
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
                      "issue view") cat "${openIssue.path}" ;;
                      "issue create") cat > "${body.path}"; echo "https://github.com/owner/minimalui/issues/99" ;;
                      "api repos/owner/minimalui/compare/"*)
                        status="${compares.path}/${'$'}{2##*/compare/}"
                        [ -f "${'$'}status" ] || { echo "gh: Not Found (HTTP 404)" >&2; exit 1; }
                        cat "${'$'}status" ;;
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

    // Three commits on main, oldest first.
    private val older = "1111111111111111111111111111111111111111"
    private val newer = "2222222222222222222222222222222222222222"
    private val newest = "3333333333333333333333333333333333333333"

    private fun autoIssues(
        conclusion: String,
        workflow: String = "CI",
        open: String = "[]",
        headSha: String = sha,
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
                    "HEAD_SHA" to headSha,
                ) + env,
        )
    }

    /** What `gh issue view --json body,comments` answers for the open issue. */
    private fun openIssueReads(
        body: String,
        comments: List<String> = emptyList(),
    ) {
        val list = comments.joinToString(", ") { """{"body": ${json(it)}}""" }
        openIssue.writeText("""{"body": ${json(body)}, "comments": [$list]}""")
    }

    /** GitHub's `status` for `compare/<base>...<head>`: how [head] relates to [base]. */
    private fun compare(
        base: String,
        head: String,
        status: String,
    ) = compares.resolve("$base...$head").writeText("$status\n")

    private fun json(text: String) = "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""

    /** The body the script gives the issue it opens for a failure on [commit]. */
    private fun openedBodyFor(commit: String): String {
        autoIssues("failure", headSha = commit)
        calls.delete()
        return body.readText()
    }

    /** The comment the script adds to the open issue for a further failure on [commit]. */
    private fun failedAgainCommentFor(commit: String): String {
        autoIssues("failure", open = "[${issue(7, "CI fails on main")}]", headSha = commit)
        val comment = changes().single().option("--body")
        calls.delete()
        return comment
    }

    private fun calls(): List<List<String>> =
        if (calls.exists()) calls.readLines().map { it.split("\t") } else emptyList()

    /** The calls that change an issue: everything but the `issue list`, `issue view` and `api` lookups. */
    private fun changes() =
        calls().filterNot { it.take(2) in listOf(listOf("issue", "list"), listOf("issue", "view")) || it[0] == "api" }

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
        openIssueReads(openedBodyFor(older))
        compare(older, newer, "ahead")

        val result = autoIssues("success", open = "[${issue(7, "CI fails on main")}]", headSha = newer)

        assertEquals(result.stderr, 0, result.exitCode)
        val close = changes().single()
        assertEquals(listOf("issue", "close", "7"), close.take(3))
        assertTrue(close.toString(), close.option("--comment").contains(runUrl))
    }

    // Runs on two quick pushes can finish out of order: an older commit's green says nothing about a newer red (#298).
    @Test
    fun `keeps the issue open when the passing commit is older than the one that opened it`() {
        openIssueReads(openedBodyFor(newer))
        compare(newer, older, "behind")

        val result = autoIssues("success", open = "[${issue(7, "CI fails on main")}]", headSha = older)

        assertEquals(result.stderr, 0, result.exitCode)
        assertEquals(emptyList<List<String>>(), changes())
        assertTrue(result.stdout, result.stdout.contains("#7") && result.stdout.contains(newer))
    }

    @Test
    fun `keeps the issue open when the passing commit is older than a later failure on it`() {
        openIssueReads(openedBodyFor(older), comments = listOf("Fixed, I think.", failedAgainCommentFor(newest)))
        compare(older, newer, "ahead")
        compare(newest, newer, "behind")

        val result = autoIssues("success", open = "[${issue(7, "CI fails on main")}]", headSha = newer)

        assertEquals(result.stderr, 0, result.exitCode)
        assertEquals(emptyList<List<String>>(), changes())
    }

    // identical: a re-run of the failed commit; diverged: main's history was rewritten. Neither holds the issue open.
    @Test
    fun `closes the open issue when the passing commit is not older than any failure on it`() {
        for (status in listOf("ahead", "identical", "diverged")) {
            openIssueReads(openedBodyFor(newer), comments = listOf(failedAgainCommentFor(newest)))
            compare(newer, newest, "ahead")
            compare(newest, newest, status)

            val result = autoIssues("success", open = "[${issue(7, "CI fails on main")}]", headSha = newest)

            assertEquals(result.stderr, 0, result.exitCode)
            assertEquals(status, listOf("issue", "close", "7"), changes().single().take(3))
            calls.delete()
        }
    }

    // A failed commit GitHub no longer knows (main force-pushed and the commit collected) must not hold the issue open.
    @Test
    fun `closes the open issue when a failed commit cannot be compared`() {
        openIssueReads(openedBodyFor(newer))

        val result = autoIssues("success", open = "[${issue(7, "CI fails on main")}]", headSha = newest)

        assertEquals(result.stderr, 0, result.exitCode)
        assertEquals(listOf("issue", "close", "7"), changes().single().take(3))
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
