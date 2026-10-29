package dev.buildwithtim.minimalui.tooling

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiAuditReportScriptTest {
    /** One PR as `gh pr list --json number,title,author,headRefName,mergedAt,mergedBy,body` prints it. */
    private fun pr(
        number: Int,
        branch: String = "fix/$number-change",
        mergedAt: String = "2026-10-10T12:00:00Z",
        author: String = "Tim-van-Oudheusden",
        mergedBy: String? = "Tim-van-Oudheusden",
        body: String = "Fixes #1",
        title: String = "PR $number",
    ) = """{"number":$number,"title":${json(title)},"author":{"login":"$author"},"headRefName":"$branch",""" +
        """"mergedAt":"$mergedAt","mergedBy":${mergedBy?.let { """{"login":"$it"}""" } ?: "null"},""" +
        """"body":${json(body)}}"""

    private fun json(text: String) =
        "\"" +
            text
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r") + "\""

    private fun report(vararg prs: String) =
        runScript("scripts/ai-audit-report.sh", "--since", "2026-10-01", stdin = prs.joinToString(",", "[", "]"))

    /** The table rows (header and divider dropped) under the `## <heading>` section. */
    private fun ScriptResult.rows(heading: String) =
        stdout
            .lines()
            .dropWhile { it != "## $heading" }
            .drop(1)
            .takeWhile { !it.startsWith("## ") }
            .filter { it.startsWith("|") }
            .drop(2)

    @Test
    fun `attributes each merged PR to its source, agent and model, oldest merge first`() {
        val result =
            report(
                pr(
                    4,
                    branch = "quality/test-gap",
                    mergedAt = "2026-10-04T09:30:00Z",
                    author = "app/hive-forge-app",
                    body = "Adds a test.\n\n— hive: agent=quality backend=claude model=claude-opus-5-5 effort=high\n",
                ),
                pr(
                    2,
                    branch = "claude/issue-1-fix",
                    mergedAt = "2026-10-02T08:00:00Z",
                    mergedBy = "app/github-actions",
                ),
                pr(
                    3,
                    branch = "dependabot/github_actions/github-actions-abc",
                    mergedAt = "2026-10-03T07:15:00Z",
                    author = "app/dependabot",
                    body = "Bumps the github-actions group.",
                ),
                pr(
                    5,
                    mergedAt = "2026-10-05T10:00:00Z",
                    // A body edited in the web UI has CRLF line ends; the CR must not stick to the last field.
                    body = "Closes #1\r\n\r\n— hive: backend=omp effort=high model=anthropic/claude-opus-5-5\r\n",
                ),
                pr(6, branch = "docs/6-readme", mergedAt = "2026-10-06T11:00:00Z"),
            )

        assertEquals(result.stderr, 0, result.exitCode)
        assertEquals(
            listOf(
                "| #2 | 2026-10-02 08:00 | Claude Code (CI) | — | — | Tim-van-Oudheusden | app/github-actions | " +
                    "`claude/issue-1-fix` | PR 2 |",
                "| #3 | 2026-10-03 07:15 | Dependabot | — | — | app/dependabot | Tim-van-Oudheusden | " +
                    "`dependabot/github_actions/github-actions-abc` | PR 3 |",
                "| #4 | 2026-10-04 09:30 | Hive | quality | claude-opus-5-5 | app/hive-forge-app | " +
                    "Tim-van-Oudheusden | `quality/test-gap` | PR 4 |",
                "| #5 | 2026-10-05 10:00 | Hive | — | anthropic/claude-opus-5-5 | Tim-van-Oudheusden | " +
                    "Tim-van-Oudheusden | `fix/5-change` | PR 5 |",
                "| #6 | 2026-10-06 11:00 | Other | — | — | Tim-van-Oudheusden | Tim-van-Oudheusden | " +
                    "`docs/6-readme` | PR 6 |",
            ),
            result.rows("Ledger"),
        )
    }

    @Test
    fun `a Hive signature outranks the head branch`() {
        val result = report(pr(7, branch = "claude/issue-7", body = "— hive: agent=scanner model=claude-sonnet-5"))

        val row = result.rows("Ledger").single()
        assertTrue(row, row.startsWith("| #7 | 2026-10-10 12:00 | Hive | scanner | claude-sonnet-5 |"))
    }

    // `gh pr list` (GraphQL) reports a GitHub App author as `app/<slug>`; the REST API as `<slug>[bot]`.
    @Test
    fun `counts a hive-forge-app PR as Hive even without a signature`() {
        val result =
            report(
                pr(8, branch = "claude/issue-8", author = "app/hive-forge-app", body = "No signature."),
                pr(9, author = "hive-forge-app[bot]", body = ""),
            )

        assertEquals(
            listOf("| #8 | Hive | — | — |", "| #9 | Hive | — | — |"),
            result.rows("Ledger").map { row -> row.split(" | ").let { "${it[0]} | ${it[2]} | ${it[3]} | ${it[4]} |" } },
        )
    }

    @Test
    fun `counts merges per source and per merging account, leaving out merges before the window`() {
        val result =
            report(
                pr(1, mergedAt = "2026-09-30T23:59:59Z", body = "— hive: agent=scanner"),
                pr(2, mergedAt = "2026-10-01T00:00:00Z", body = "— hive: agent=scanner"),
                pr(3, body = "— hive: agent=quality", mergedBy = "app/github-actions"),
                pr(4, branch = "dependabot/gradle/x", mergedBy = null),
                pr(5, mergedBy = "app/github-actions"),
                pr(6, mergedBy = "app/github-actions"),
            )

        assertEquals(
            listOf(
                "| Hive | 2 |",
                "| Claude Code (CI) | 0 |",
                "| Dependabot | 1 |",
                "| Other | 2 |",
                "| **All** | 5 |",
            ),
            result.rows("By source"),
        )
        assertEquals(
            listOf("| app/github-actions | 3 |", "| Tim-van-Oudheusden | 1 |", "| Unknown (no merger recorded) | 1 |"),
            result.rows("Merged by"),
        )
        assertEquals(listOf("#2", "#3", "#4", "#5", "#6"), result.rows("Ledger").map { it.split(" | ")[0].drop(2) })
        val dependabot = result.rows("Ledger")[2]
        assertTrue(dependabot, dependabot.contains("| Dependabot | — | — | Tim-van-Oudheusden | — |"))
    }

    @Test
    fun `states the reporting window`() {
        assertTrue(report().stdout.contains("Pull requests merged into `main` since 2026-10-01 (UTC)."))
    }

    @Test
    fun `escapes pipes so a title cannot add table columns`() {
        val result = report(pr(10, title = "fix(a|b): keep | out"))

        assertTrue(result.stdout, result.rows("Ledger").single().endsWith("| fix(a\\|b): keep \\| out |"))
    }

    // `shift 2` with one argument left shifts nothing, so an unguarded parser loops on the same flag forever.
    @Test
    fun `rejects a missing, valueless or malformed --since`() {
        for (args in listOf(emptyList(), listOf("--since"), listOf("--since", "1 Oct"), listOf("--days", "7"))) {
            val result = runScript("scripts/ai-audit-report.sh", *args.toTypedArray(), stdin = "[]")

            assertEquals(args.toString(), 2, result.exitCode)
            assertTrue(result.stderr, result.stderr.contains("usage:"))
        }
    }

    @Test
    fun `fails on input that is not a PR list`() {
        val result = report("not json")

        assertTrue(result.exitCode != 0)
        assertEquals("", result.stdout)
    }
}
