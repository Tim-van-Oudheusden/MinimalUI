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
    ) = """{"number":$number,"mergedAt":${if (merged) "\"2026-10-01T10:00:00Z\"" else "null"},""" +
        """"headRefName":"$branch","author":{"login":"$author"}}"""

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

    @Test
    fun `fails on input that is not a PR list`() {
        val result = report("not json")

        assertTrue(result.exitCode != 0)
    }
}
