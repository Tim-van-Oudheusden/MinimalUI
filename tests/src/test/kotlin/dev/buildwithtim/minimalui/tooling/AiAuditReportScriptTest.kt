package dev.buildwithtim.minimalui.tooling

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiAuditReportScriptTest {
    /** One PR as `gh pr list --json number,title,author,headRefName,mergedAt,mergedBy,body` prints it. */
    private fun pr(
        number: Int,
        mergedAt: String = "2026-10-10T12:00:00Z",
        author: String = "Tim-van-Oudheusden",
        branch: String = "fix/$number-change",
        mergedBy: String? = "Tim-van-Oudheusden",
        body: String = "Fixes #1",
        title: String = "PR $number",
    ) = """{"number":$number,"title":"$title","author":{"login":"$author"},"headRefName":"$branch",""" +
        """"mergedAt":"$mergedAt","mergedBy":${mergedBy?.let { """{"login":"$it"}""" } ?: "null"},""" +
        """"body":"${body.replace("\n", "\\n")}"}"""

    private fun report(vararg prs: String) =
        runScript("scripts/ai-audit-report.sh", "--since", "2026-10-01", stdin = prs.joinToString(",", "[", "]"))

    /** The table rows, header and divider dropped, under the `## <heading>` section. */
    private fun ScriptResult.rows(heading: String): List<String> =
        stdout
            .substringAfter("\n## $heading\n", "")
            .substringBefore("\n## ")
            .lines()
            .filter { it.startsWith("|") }
            .drop(2)

    private fun row(vararg cells: String) = cells.joinToString(" | ", "| ", " |")

    private val tim = "Tim-van-Oudheusden"

    @Test
    fun `attributes PRs by head branch, oldest merge first`() {
        val result =
            report(
                pr(6, mergedAt = "2026-10-06T11:00:00Z", branch = "docs/6-readme"),
                pr(
                    2,
                    mergedAt = "2026-10-02T08:00:00Z",
                    branch = "claude/issue-1-fix",
                    mergedBy = "app/github-actions",
                ),
                pr(3, mergedAt = "2026-10-03T07:15:00Z", author = "app/dependabot", branch = "dependabot/gradle/agp"),
            )

        assertEquals(result.stderr, 0, result.exitCode)
        assertEquals(
            listOf(
                row(
                    "#2",
                    "2026-10-02 08:00",
                    "Claude Code (CI)",
                    "—",
                    "—",
                    tim,
                    "app/github-actions",
                    "`claude/issue-1-fix`",
                ),
                row("#3", "2026-10-03 07:15", "Dependabot", "—", "—", "app/dependabot", tim, "`dependabot/gradle/agp`"),
                row("#6", "2026-10-06 11:00", "Other", "—", "—", tim, tim, "`docs/6-readme`"),
            ),
            result.rows("Ledger").map { it.substringBeforeLast(" | ") + " |" },
        )
    }

    @Test
    fun `attributes Hive PRs by signature or author, with the agent and model the signature names`() {
        val signed = "Adds a test.\n\n— hive: agent=quality backend=claude model=claude-opus-5-5 effort=high\n"
        val result =
            report(
                pr(4, author = "app/hive-forge-app", branch = "quality/test-gap", body = signed),
                pr(5, body = "Fixes #1\n\n— hive: backend=omp model=anthropic/claude-5"),
                pr(7, author = "hive-forge-app[bot]", branch = "scanner/doc-drift"),
            )

        assertEquals(
            listOf(
                row("#4", "2026-10-10 12:00", "Hive", "quality", "claude-opus-5-5", "app/hive-forge-app", tim),
                row("#5", "2026-10-10 12:00", "Hive", "—", "anthropic/claude-5", tim, tim),
                row("#7", "2026-10-10 12:00", "Hive", "—", "—", "hive-forge-app[bot]", tim),
            ),
            result.rows("Ledger").map { it.split(" | ").take(7).joinToString(" | ") + " |" },
        )
    }

    @Test
    fun `lets a Hive signature outrank the head branch`() {
        val result = report(pr(7, branch = "claude/issue-7", body = "— hive: agent=scanner model=claude-sonnet-5"))

        val ledger = result.rows("Ledger").single()

        assertTrue(ledger, ledger.startsWith(row("#7", "2026-10-10 12:00", "Hive", "scanner", "claude-sonnet-5")))
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
        assertEquals(
            listOf("#2", "#3", "#4", "#5", "#6"),
            result.rows("Ledger").map {
                it.split(" | ").first().removePrefix("| ")
            },
        )
    }

    @Test
    fun `escapes pipes so a title cannot add table columns`() {
        val result = report(pr(8, title = "fix(a|b): keep | out"))

        assertTrue(result.stdout, result.rows("Ledger").single().endsWith("| fix(a\\|b): keep \\| out |"))
    }

    @Test
    fun `states the window`() {
        assertTrue(report().stdout.contains("Pull requests merged into `main` since 2026-10-01."))
    }

    @Test
    fun `rejects a missing or valueless --since with a usage line`() {
        for (args in listOf(emptyList(), listOf("--since"))) {
            val result = runScript("scripts/ai-audit-report.sh", *args.toTypedArray(), stdin = "[]")

            assertEquals(args.toString(), 2, result.exitCode)
            assertTrue(result.stderr, result.stderr.contains("usage:"))
        }
    }

    @Test
    fun `fails on input that is not a PR list`() {
        assertTrue(report("not json").exitCode != 0)
    }
}
