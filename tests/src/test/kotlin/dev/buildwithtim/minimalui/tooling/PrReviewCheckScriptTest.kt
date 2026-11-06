package dev.buildwithtim.minimalui.tooling

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PrReviewCheckScriptTest {
    private fun check(
        title: String,
        body: String,
        author: String = "Tim-van-Oudheusden",
    ) = runScript(
        "scripts/pr-review-check.sh",
        env = mapOf("PR_TITLE" to title, "PR_BODY" to body, "PR_AUTHOR" to author),
    )

    @Test
    fun `passes a Conventional Commits title with a linked issue`() {
        val result = check("feat(launcher): add app search (#12)", "Fixes #12")

        assertEquals(result.stdout, 0, result.exitCode)
        assertEquals("ok: title follows Conventional Commits\nok: body links a GitHub issue\n", result.stdout)
    }

    @Test
    fun `rejects a title that is not a Conventional Commit`() {
        val result = check("Add app search", "Fixes #12")

        assertEquals(1, result.exitCode)
        assertTrue(result.stdout, result.stdout.contains("must follow Conventional Commits"))
    }

    @Test
    fun `rejects a body without an issue link`() {
        assertEquals(1, check("fix(input): handle Fn key", "No issue here").exitCode)
    }

    @Test
    fun `does not take a keyword inside another word as a link`() {
        assertEquals(1, check("fix(input): handle Fn key", "hotfix #3").exitCode)
    }

    @Test
    fun `accepts the owner-repo long form of an issue link`() {
        assertEquals(0, check("fix(input): handle Fn key", "Fixes Tim-van-Oudheusden/MinimalUI#3").exitCode)
    }

    @Test
    fun `does not check a bot's title or issue link`() {
        val result = check("📖 docs(ci): point at the parser", "No issue here", author = "hive-forge-app[bot]")

        assertEquals(result.stdout, 0, result.exitCode)
        assertEquals("ok: bot author, title and issue link not checked\n", result.stdout)
    }

    // The full case table (every closing keyword, CRLF, the colon form, the unfilled template) is a plain-bash script
    // that runs without Gradle; running it here puts it in CI too.
    @Test
    fun `passes every case in the script's own case table`() {
        val result = runScript("scripts/pr-review-check.test.sh")

        assertEquals(result.stdout, 0, result.exitCode)
    }

    @Test
    fun `reports every failed item in one run`() {
        val result = check("Add app search", "No issue here")

        assertEquals(2, result.stdout.lines().count { it.startsWith("::error::") })
    }
}
