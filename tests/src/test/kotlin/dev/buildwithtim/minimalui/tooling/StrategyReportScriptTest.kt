package dev.buildwithtim.minimalui.tooling

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class StrategyReportScriptTest {
    @get:Rule
    val temp = TemporaryFolder()

    /** One issue as `gh issue list --json number,title,state,labels,createdAt,closedAt` prints it. */
    private fun issue(
        number: Int,
        title: String = "Issue $number",
        labels: List<String> = emptyList(),
        createdAt: String = "2026-09-01T12:00:00Z",
        closedAt: String? = null,
    ) = """{"number":$number,"title":"$title","state":"${if (closedAt == null) "OPEN" else "CLOSED"}",""" +
        """"labels":${labels.joinToString(",", "[", "]") { """{"name":"$it"}""" }},""" +
        """"createdAt":"$createdAt","closedAt":${closedAt?.let { "\"$it\"" } ?: "null"}}"""

    private fun report(
        issues: List<String>,
        mergedAt: List<String> = emptyList(),
    ): ScriptResult {
        val dir = temp.newFolder()
        val issuesFile = dir.resolve("issues.json").apply { writeText(issues.joinToString(",", "[", "]")) }
        val prsFile =
            dir.resolve("prs.json").apply {
                writeText(mergedAt.joinToString(",", "[", "]") { """{"mergedAt":"$it"}""" })
            }
        return runScript(
            "scripts/strategy-report.sh",
            "--since",
            "2026-10-01",
            "--issues",
            issuesFile.path,
            "--prs",
            prsFile.path,
        )
    }

    /** The table rows, header and divider dropped, under the `## <heading>` section. */
    private fun ScriptResult.rows(heading: String): List<String> =
        stdout
            .substringAfter("\n## $heading\n", "")
            .substringBefore("\n## ")
            .lines()
            .filter { it.startsWith("|") }
            .drop(2)

    @Test
    fun `counts issues opened and closed and PRs merged from the start of the window on`() {
        val result =
            report(
                listOf(
                    issue(1, createdAt = "2026-09-30T23:59:59Z"),
                    issue(2, createdAt = "2026-10-01T00:00:00Z"),
                    issue(3, createdAt = "2026-10-12T08:00:00Z", closedAt = "2026-10-13T08:00:00Z"),
                    issue(4, closedAt = "2026-09-30T10:00:00Z"),
                    issue(5, closedAt = "2026-10-02T10:00:00Z"),
                    issue(6, createdAt = "2026-10-20T08:00:00Z"),
                ),
                mergedAt = listOf("2026-09-29T10:00:00Z", "2026-10-01T00:00:00Z", "2026-10-15T10:00:00Z"),
            )

        assertEquals(result.stderr, 0, result.exitCode)
        assertEquals(
            listOf(
                "| Issues opened | 3 |",
                "| Issues closed | 2 |",
                "| Net backlog change | +1 |",
                "| PRs merged | 2 |",
                "| Open issues now | 3 |",
            ),
            result.rows("Flow"),
        )
    }

    @Test
    fun `signs a shrinking backlog negative and an unchanged one zero`() {
        val shrinking = report(listOf(issue(1, closedAt = "2026-10-02T10:00:00Z")))
        val unchanged = report(listOf(issue(1)))

        assertTrue(shrinking.rows("Flow").toString(), "| Net backlog change | -1 |" in shrinking.rows("Flow"))
        assertTrue(unchanged.rows("Flow").toString(), "| Net backlog change | 0 |" in unchanged.rows("Flow"))
    }

    @Test
    fun `counts open issues per pipeline stage in stage order with unstaged ones last`() {
        val result =
            report(
                listOf(
                    issue(1, labels = listOf("bug", "stage/045-needs-decision")),
                    issue(2, labels = listOf("stage/010-actionable")),
                    issue(3, labels = listOf("stage/045-needs-decision")),
                    issue(4, labels = listOf("enhancement")),
                    issue(5, labels = listOf("stage/100-merge-queue"), closedAt = "2026-10-02T10:00:00Z"),
                ),
            )

        assertEquals(
            listOf("| stage/010-actionable | 1 |", "| stage/045-needs-decision | 2 |", "| No stage | 1 |"),
            result.rows("Backlog by stage"),
        )
    }

    @Test
    fun `tallies the ACMM gap issues per level and names the ones still open`() {
        val acmm = listOf("acmm")
        val result =
            report(
                listOf(
                    issue(13, title = "[ACMM L6] Add Auto issue generation", labels = acmm),
                    issue(
                        12,
                        title = "[ACMM L4] Add Nightly compliance",
                        labels = acmm,
                        closedAt = "2026-09-03T21:00:00Z",
                    ),
                    issue(11, title = "[ACMM L6] Add Merge queue", labels = acmm, closedAt = "2026-10-29T19:00:00Z"),
                    issue(10, title = "[ACMM L6] Add Strategic dashboard", labels = acmm),
                    issue(9, title = "[ACMM L10] Add Something later", labels = acmm),
                    issue(14, title = "[ACMM L6] Not filed by the evaluation"),
                ),
            )

        assertEquals(
            listOf(
                "| L4 | 1 | 0 | — |",
                "| L6 | 1 | 2 | #10 Strategic dashboard, #13 Auto issue generation |",
                "| L10 | 0 | 1 | #9 Something later |",
            ),
            result.rows("ACMM progress"),
        )
    }

    @Test
    fun `states the reporting window`() {
        assertTrue(report(emptyList()).stdout.contains("Since 2026-10-01."))
    }

    @Test
    fun `fails naming the input it cannot read`() {
        val missing = temp.root.resolve("missing.json").path
        val prs = temp.newFile("prs.json").apply { writeText("[]") }
        val result =
            runScript("scripts/strategy-report.sh", "--since", "2026-10-01", "--issues", missing, "--prs", prs.path)

        assertEquals(1, result.exitCode)
        assertTrue(result.stderr, result.stderr.contains(missing))
        assertEquals("", result.stdout)
    }

    @Test
    fun `fails on input that is not a list`() {
        val result = report(listOf("not json"))

        assertTrue(result.exitCode != 0)
    }

    // `shift 2` with one argument left shifts nothing, so an unguarded parser loops on the same flag forever.
    @Test
    fun `rejects a missing or valueless flag with a usage line`() {
        for (args in listOf(
            listOf("--since", "2026-10-01", "--issues", "a.json"),
            listOf("--issues", "a.json", "--prs"),
        )) {
            val result = runScript("scripts/strategy-report.sh", *args.toTypedArray())

            assertEquals(args.toString(), 2, result.exitCode)
            assertTrue(result.stderr, result.stderr.contains("usage:"))
        }
    }
}
