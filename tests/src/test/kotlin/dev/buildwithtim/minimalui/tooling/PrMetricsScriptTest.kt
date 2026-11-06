package dev.buildwithtim.minimalui.tooling

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PrMetricsScriptTest {
    private fun pr(
        number: Int,
        merged: Boolean,
        branch: String,
        author: String = "Tim-van-Oudheusden",
        body: String = "Fixes #1",
    ) = """{"number":$number,"mergedAt":${if (merged) "\"2026-10-01T10:00:00Z\"" else "null"},""" +
        """"headRefName":"$branch","author":{"login":"$author"},"body":"$body"}"""

    private fun report(vararg prs: String) =
        runScript(
            "scripts/pr-metrics.sh",
            "--since",
            "2026-09-14",
            "--days",
            "28",
            stdin = prs.joinToString(",", "[", "]"),
        )

    private fun ScriptResult.row(label: String) = stdout.lines().single { it.startsWith("| $label ") }

    @Test
    fun `splits agent-authored from other PRs and computes acceptance`() {
        val result =
            report(
                pr(1, merged = true, branch = "claude/issue-1"),
                pr(2, merged = true, branch = "claude/issue-2"),
                pr(3, merged = false, branch = "claude/issue-3"),
                pr(4, merged = true, branch = "feat/4-search"),
            )

        assertEquals(result.stderr, 0, result.exitCode)
        assertEquals("| Agent | 3 | 2 | 1 | 66.7% |", result.row("Agent"))
        assertEquals("| Other | 1 | 1 | 0 | 100% |", result.row("Other"))
        assertEquals("| **All** | 4 | 3 | 1 | 75% |", result.row("**All**"))
    }

    // `gh pr list` (GraphQL) reports a GitHub App author as `app/<slug>`; the REST API as `<slug>[bot]`.
    @Test
    fun `counts the hive-forge bot as an agent whatever its branch`() {
        val result =
            report(
                pr(5, merged = false, branch = "fix/5-crash", author = "app/hive-forge-app"),
                pr(6, merged = true, branch = "fix/6-crash", author = "hive-forge-app[bot]"),
            )

        assertEquals("| Agent | 2 | 1 | 1 | 50% |", result.row("Agent"))
        assertEquals("| Other | 0 | 0 | 0 | n/a |", result.row("Other"))
    }

    // The App was renamed from hive-forge-app; GitHub reports the new login for its old PRs too (#318).
    @Test
    fun `counts the renamed hive-worker-bee App as an agent whatever its branch`() {
        val result =
            report(
                pr(7, merged = false, branch = "fix/7-crash", author = "app/hive-worker-bee"),
                pr(8, merged = true, branch = "fix/8-crash", author = "hive-worker-bee[bot]"),
            )

        assertEquals("| Agent | 2 | 1 | 1 | 50% |", result.row("Agent"))
        assertEquals("| Other | 0 | 0 | 0 | n/a |", result.row("Other"))
    }

    // Local agents open PRs under the maintainer's account on <type>/<N>-<slug> branches; only the signature tells.
    @Test
    fun `counts a Hive-signed PR as an agent whatever its author and branch`() {
        val signature = "\\n\\n— hive: backend=omp model=claude-opus-5-5 effort=high"
        val result =
            report(
                pr(9, merged = true, branch = "fix/9-crash", body = "Fixes #9$signature"),
                pr(10, merged = false, branch = "fix/10-crash", body = "Mentions — hive: mid-line"),
            )

        assertEquals("| Agent | 1 | 1 | 0 | 100% |", result.row("Agent"))
        assertEquals("| Other | 1 | 0 | 1 | 0% |", result.row("Other"))
    }

    // pr-source.jq reads a missing body as empty, so without `body` signed runs land in Other and nothing fails.
    @Test
    fun `the workflow requests the PR body the source rule reads`() {
        val workflow = Repo.read(".github/workflows/pr-metrics.yml")

        assertTrue(Regex("""--json \S*\bbody\b""").containsMatchIn(workflow))
    }

    @Test
    fun `leaves a Dependabot PR out of the agent row`() {
        val result = report(pr(11, merged = true, branch = "dependabot/gradle/junit", author = "dependabot[bot]"))

        assertEquals("| Agent | 0 | 0 | 0 | n/a |", result.row("Agent"))
        assertEquals("| Other | 1 | 1 | 0 | 100% |", result.row("Other"))
    }

    @Test
    fun `reports n-a rather than a rate when the window is empty`() {
        val result = report()

        assertEquals(result.stderr, 0, result.exitCode)
        assertEquals("| **All** | 0 | 0 | 0 | n/a |", result.row("**All**"))
    }

    @Test
    fun `states the reporting window`() {
        assertTrue(report().stdout.contains("PRs closed since 2026-09-14 (last 28 days)."))
    }

    // `shift 2` with one argument left shifts nothing, so an unguarded parser loops on the same flag forever.
    @Test
    fun `rejects a trailing flag that has no value`() {
        for (args in listOf(listOf("--days", "28", "--since"), listOf("--since", "2026-09-14", "--days"))) {
            val result = runScript("scripts/pr-metrics.sh", *args.toTypedArray(), stdin = "[]")

            assertEquals(args.last(), 2, result.exitCode)
            assertTrue(result.stderr, result.stderr.contains("usage:"))
        }
    }

    @Test
    fun `fails on input that is not a PR list`() {
        val result = report("not json")

        assertTrue(result.exitCode != 0)
    }
}
