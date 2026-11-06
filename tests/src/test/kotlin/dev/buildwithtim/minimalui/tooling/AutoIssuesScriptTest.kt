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
    private val issueView = dir.resolve("issue-view.json")
    private val compareStatus = dir.resolve("compare-status.txt")

    /**
     * Fake `gh` first on PATH: logs each call's arguments tab-separated on one line, answers `issue list` with
     * [openIssues], `issue view` with [issueView] and `api` (the commit compare) with the status in
     * `compare-<base sha>.txt`, else in [compareStatus], and keeps the body `issue create` reads from stdin in [body].
     * A status of `404` answers like `gh api` does for a commit GitHub cannot find: an error and exit 1.
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
                      "issue view") cat "${issueView.path}" ;;
                      api*)
                        base="${'$'}{2##*/compare/}"
                        status_file="${dir.path}/compare-${'$'}{base%%...*}.txt"
                        [ -f "${'$'}status_file" ] || status_file="${compareStatus.path}"
                        status="${'$'}(cat "${'$'}status_file")"
                        if [ "${'$'}status" = 404 ]; then
                          echo '{"message": "Not Found", "status": "404"}'
                          echo 'gh: Not Found (HTTP 404)' >&2
                          exit 1
                        fi
                        printf '{"status": "%s"}\n' "${'$'}status" ;;
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
    private val failedSha = "fedcba9876543210fedcba9876543210fedcba98"
    private val laterFailedSha = "abcdefabcdefabcdefabcdefabcdefabcdefabcd"

    /** What `gh issue view --json body,comments` returns for an issue the script opened for [failedSha]. */
    private val openedIssue =
        """{"body": "- Run: https://github.com/owner/minimalui/actions/runs/41\n- Commit: $failedSha\n", "comments": []}"""

    private fun autoIssues(
        conclusion: String,
        workflow: String = "CI",
        open: String = "[]",
        view: String = openedIssue,
        compare: String = "ahead",
        compareByBase: Map<String, String> = emptyMap(),
        env: Map<String, String> = emptyMap(),
    ): ScriptResult {
        openIssues.writeText(open)
        issueView.writeText(view)
        compareStatus.writeText(compare)
        compareByBase.forEach { (base, status) -> dir.resolve("compare-$base.txt").writeText(status) }
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

    /** The calls that change an issue: everything but the `issue list` / `issue view` lookups and `api` reads. */
    private fun changes() =
        calls().filterNot { it[0] == "api" || it.take(2) in listOf(listOf("issue", "list"), listOf("issue", "view")) }

    private fun List<String>.option(name: String) = this[indexOf(name) + 1]

    private fun jsonString(text: String) =
        "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""

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
        assertEquals("-", create.option("--body-file"))
        val text = body.readText()
        assertTrue(text, text.contains(runUrl))
        assertTrue(text, text.contains(sha))
        assertTrue(text, text.startsWith("The **CI** workflow failed on `main` (`failure`).\n"))
        assertTrue(text, text.endsWith("the next\nsuccessful run on `main` closes this issue.\n"))
        assertEquals(
            "CI failed on main: opened https://github.com/owner/minimalui/issues/99.\n",
            result.stdout,
        )
    }

    @Test
    fun `comments on the open issue instead of opening a second one`() {
        val result = autoIssues("failure", open = "[${issue(7, "CI fails on main")}]")

        assertEquals(result.stderr, 0, result.exitCode)
        val comment = changes().single()
        assertEquals(listOf("issue", "comment", "7"), comment.take(3))
        assertTrue(comment.toString(), comment.option("--body").contains(runUrl))
        assertEquals("CI failed again on main: commented on #7.\n", result.stdout)
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
        assertEquals("CI passes again on main: closed #7.\n", result.stdout)
    }

    @Test
    fun `compares a failing commit recorded more than once only once`() {
        val view =
            """{"body": "- Commit: $failedSha\n", "comments": [{"body": "Failed again: [run](u) on $failedSha."}]}"""

        autoIssues("success", open = "[${issue(7, "CI fails on main")}]", view = view)

        assertEquals(1, calls().count { it[0] == "api" })
    }

    // Runs on quick successive pushes finish out of order: a green on an older commit says nothing about the newer red.
    @Test
    fun `keeps the issue open when the passing commit is older than the failing one`() {
        val result = autoIssues("success", open = "[${issue(7, "CI fails on main")}]", compare = "behind")

        assertEquals(result.stderr, 0, result.exitCode)
        assertEquals(emptyList<List<String>>(), changes())
        assertTrue(result.stdout, result.stdout.contains("#7 stays open"))
        val compare = calls().single { it[0] == "api" }
        assertEquals("repos/owner/minimalui/compare/$failedSha...$sha", compare[1])
    }

    @Test
    fun `checks the commits of later failures recorded in comments too`() {
        val comment = "Failed again (`failure`): [run]($runUrl) on $laterFailedSha."
        val view = """{"body": "- Commit: $failedSha\n", "comments": [{"body": "$comment"}]}"""

        autoIssues("success", open = "[${issue(7, "CI fails on main")}]", view = view)

        val compared = calls().filter { it[0] == "api" }.map { it[1] }.toSet()
        assertEquals(
            setOf(
                "repos/owner/minimalui/compare/$failedSha...$sha",
                "repos/owner/minimalui/compare/$laterFailedSha...$sha",
            ),
            compared,
        )
        assertEquals(listOf("issue", "close", "7"), changes().single().take(3))
    }

    // A failing run that is re-run and passes is the same commit: GitHub compares it as identical.
    @Test
    fun `closes the issue when the failed commit itself passes on a re-run`() {
        autoIssues("success", open = "[${issue(7, "CI fails on main")}]", compare = "identical")

        assertEquals(listOf("issue", "close", "7"), changes().single().take(3))
    }

    // The passing commit is newer than the failure that opened the issue, but older than a later one.
    @Test
    fun `keeps the issue open when the passing commit is older than any one of the failing commits`() {
        val comment = "Failed again (`failure`): [run]($runUrl) on $laterFailedSha."
        val view = """{"body": "- Commit: $failedSha\n", "comments": [{"body": "$comment"}]}"""

        val result =
            autoIssues(
                "success",
                open = "[${issue(7, "CI fails on main")}]",
                view = view,
                compareByBase = mapOf(failedSha to "ahead", laterFailedSha to "behind"),
            )

        assertEquals(result.stderr, 0, result.exitCode)
        assertEquals(emptyList<List<String>>(), changes())
        assertTrue(result.stdout, result.stdout.contains("older than failing $laterFailedSha"))
    }

    // The parser reads the text the failure branches write; a format change on one side alone must fail here.
    @Test
    fun `reads the failing commits from the issue body and comment the script itself writes`() {
        autoIssues("failure", env = mapOf("HEAD_SHA" to failedSha))
        val openedBody = body.readText()
        calls.delete()
        autoIssues("failure", open = "[${issue(7, "CI fails on main")}]", env = mapOf("HEAD_SHA" to laterFailedSha))
        val failedAgain = changes().single().option("--body")
        calls.delete()
        val view = """{"body": ${jsonString(openedBody)}, "comments": [{"body": ${jsonString(failedAgain)}}]}"""

        autoIssues("success", open = "[${issue(7, "CI fails on main")}]", view = view)

        val compared = calls().filter { it[0] == "api" }.map { it[1] }.toSet()
        assertEquals(
            setOf(
                "repos/owner/minimalui/compare/$failedSha...$sha",
                "repos/owner/minimalui/compare/$laterFailedSha...$sha",
            ),
            compared,
        )
    }

    // A "CI fails on main" issue opened by hand has no "Commit:" line; the next green still closes it.
    @Test
    fun `closes an open issue that records no failing commit`() {
        val view = """{"body": "CI is red, see the last run.", "comments": []}"""

        val result = autoIssues("success", open = "[${issue(7, "CI fails on main")}]", view = view)

        assertEquals(result.stderr, 0, result.exitCode)
        assertEquals(emptyList<List<String>>(), calls().filter { it[0] == "api" })
        assertEquals(listOf("issue", "close", "7"), changes().single().take(3))
    }

    // A failing commit GitHub can no longer find (history rewritten since) must not hold the issue open for good.
    @Test
    fun `skips a failing commit GitHub cannot compare`() {
        val result = autoIssues("success", open = "[${issue(7, "CI fails on main")}]", compare = "404")

        assertEquals(result.stderr, 0, result.exitCode)
        assertTrue(result.stderr, result.stderr.contains("Could not compare failing $failedSha"))
        assertEquals(listOf("issue", "close", "7"), changes().single().take(3))
    }

    @Test
    fun `still checks the other failing commits after one cannot be compared`() {
        val comment = "Failed again (`failure`): [run]($runUrl) on $laterFailedSha."
        val view = """{"body": "- Commit: $failedSha\n", "comments": [{"body": "$comment"}]}"""

        val result =
            autoIssues(
                "success",
                open = "[${issue(7, "CI fails on main")}]",
                view = view,
                compareByBase = mapOf(laterFailedSha to "404", failedSha to "behind"),
            )

        assertEquals(result.stderr, 0, result.exitCode)
        assertEquals(emptyList<List<String>>(), changes())
        assertTrue(result.stdout, result.stdout.contains("older than failing $failedSha"))
    }

    @Test
    fun `changes nothing when the workflow passes and no issue is open`() {
        val result = autoIssues("success", open = "[${issue(12, "E2E fails on main")}]")

        assertEquals(result.stderr, 0, result.exitCode)
        assertEquals(emptyList<List<String>>(), changes())
        assertEquals("CI passes on main: no open issue.\n", result.stdout)
    }

    // A cancelled run (superseded by a newer push, or stopped by hand) says nothing about main.
    @Test
    fun `leaves the issue alone when the run was cancelled`() {
        val result = autoIssues("cancelled", open = "[${issue(7, "CI fails on main")}]")

        assertEquals(result.stderr, 0, result.exitCode)
        assertEquals(emptyList<List<String>>(), changes())
        assertEquals("CI concluded 'cancelled' on main: no issue change.\n", result.stdout)
    }

    @Test
    fun `refuses to run without the run's details`() {
        val result = autoIssues("failure", env = mapOf("RUN_URL" to ""))

        assertEquals(2, result.exitCode)
        assertTrue(result.stderr, result.stderr.contains("RUN_URL"))
        assertEquals(emptyList<List<String>>(), calls())
    }
}
