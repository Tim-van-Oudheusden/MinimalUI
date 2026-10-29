package dev.buildwithtim.minimalui.tooling

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.concurrent.TimeUnit

/**
 * `.github/CODEOWNERS` makes the maintainer the owner of exactly the paths `.github/risk-assessment.yml` rates high,
 * so a pull request touching one requests his review. The two files use different glob dialects, so the test compares
 * what they select among the tracked files rather than the patterns themselves.
 */
class CodeownersTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val rules: List<String> =
        Repo
            .read(".github/CODEOWNERS")
            .lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }

    private fun git(vararg args: String): String {
        val out = temp.newFile()
        val process =
            ProcessBuilder(listOf("git") + args)
                .directory(Repo.root)
                .redirectOutput(out)
                .redirectError(ProcessBuilder.Redirect.INHERIT)
                .start()
        check(process.waitFor(60, TimeUnit.SECONDS)) { "git ${args.joinToString(" ")} did not finish within 60 s" }
        assertEquals("git ${args.joinToString(" ")}", 0, process.exitValue())
        return out.readText()
    }

    private fun String.nulSeparated(): List<String> = split('\u0000').filter { it.isNotEmpty() }.sorted()

    /** Tracked files a CODEOWNERS pattern owns. Its patterns follow .gitignore syntax, so git itself can apply them. */
    private fun ownedFiles(): List<String> {
        val patterns = temp.newFile("patterns")
        patterns.writeText(rules.joinToString("") { it.split(Regex("\\s+")).first() + "\n" })
        return git("ls-files", "-z", "--cached", "--ignored", "--exclude-from=${patterns.path}").nulSeparated()
    }

    /** Tracked files scripts/tier-classify.sh rates high. */
    private fun highRiskFiles(): List<String> {
        val result = runScript("scripts/tier-classify.sh", "-z", stdin = git("ls-files", "-z"))
        assertEquals(result.stderr, 0, result.exitCode)
        return result.stdout
            .lines()
            .filter { it.startsWith("high ") }
            .map { it.removePrefix("high").trim() }
            .sorted()
    }

    @Test
    fun `owns exactly the tracked files the risk assessment rates high`() {
        val owned = ownedFiles()
        val high = highRiskFiles()

        assertEquals("high risk but unowned", emptyList<String>(), high - owned.toSet())
        assertEquals("owned but not high risk", emptyList<String>(), owned - high.toSet())
    }

    @Test
    fun `gives every pattern an owner`() {
        assertTrue("no rules", rules.isNotEmpty())
        rules.forEach { assertTrue(it, Regex("\\S+(\\s+@[\\w-]+(/[\\w-]+)?)+").matches(it)) }
    }
}
