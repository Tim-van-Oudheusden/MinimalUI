package dev.buildwithtim.minimalui.tooling

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * `.github/policies/risk-tiers.policy` and `.github/labeler.yml` classify the same paths for different readers: the
 * review tier and the PR label. The policy must give a path a tier explicitly exactly when the labeler gives it a
 * label (#100), so neither file covers a path the other forgets. Each glob in one file is reduced to a sample path it
 * matches, and that sample must be covered by the other file.
 */
class PolicyLabelParityTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val policyGlobs: List<String> =
        Repo
            .read(".github/policies/risk-tiers.policy")
            .lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .map { it.split(Regex("\\s+"))[1] }

    private val labelGlobs: Map<String, List<String>> =
        Yaml.parse(Repo.read(".github/labeler.yml")).asMap().mapValues { (label, rules) ->
            rules.asList().flatMap { rule ->
                val changedFiles =
                    rule
                        .asMap()
                        .also {
                            assertEquals(
                                "label $label: only changed-files rules are supported",
                                setOf("changed-files"),
                                it.keys,
                            )
                        }.getValue("changed-files")
                changedFiles.asList().flatMap { match ->
                    val globs =
                        match
                            .asMap()
                            .also {
                                assertEquals(
                                    "label $label: only any-glob-to-any-file is supported",
                                    setOf("any-glob-to-any-file"),
                                    it.keys,
                                )
                            }.getValue("any-glob-to-any-file")
                    if (globs is String) listOf(globs) else globs.asList().map { it.toString() }
                }
            }
        }

    /** A path [glob] matches, in either file's dialect: every wildcard run becomes one plain segment. */
    private fun sample(glob: String): String = glob.replace(Regex("\\*+"), "x")

    /**
     * Whether actions/labeler matches [path] with [glob]: minimatch with `dot: true`, so `**` spans directories, `*`
     * and `?` stay within one, and dotfiles match like any other name.
     */
    private fun labelerMatches(
        glob: String,
        path: String,
    ): Boolean {
        val regex =
            buildString {
                var i = 0
                while (i < glob.length) {
                    when {
                        glob.startsWith("**/", i) -> append("(?:.*/)?").also { i += 3 }
                        glob.startsWith("**", i) -> append(".*").also { i += 2 }
                        glob[i] == '*' -> append("[^/]*").also { i++ }
                        glob[i] == '?' -> append("[^/]").also { i++ }
                        else -> append(Regex.escape(glob[i].toString())).also { i++ }
                    }
                }
            }
        return Regex(regex).matches(path)
    }

    private fun labelsOf(path: String): Set<String> =
        labelGlobs.filterValues { globs -> globs.any { labelerMatches(it, path) } }.keys

    /**
     * The paths scripts/tier-classify.sh finds a rule for. The script reports unmatched paths as medium, the same as
     * a `medium` rule, so it runs against a copy of the policy with every rule raised to high: a path that comes out
     * high matched a rule.
     */
    private fun pathsWithTierRule(paths: Collection<String>): Set<String> {
        val raised = temp.root.resolve("risk-tiers.policy")
        raised.writeText(policyGlobs.joinToString("\n", postfix = "\n") { "high $it" })
        val result =
            runScript(
                "scripts/tier-classify.sh",
                env = mapOf("RISK_TIER_POLICY" to raised.path),
                stdin = paths.joinToString("\n"),
            )
        assertEquals(result.stderr, 0, result.exitCode)
        return result.stdout
            .lines()
            .filter { it.startsWith("high ") }
            .map { it.removePrefix("high").trim() }
            .toSet()
    }

    @Test
    fun `every path the risk-tier policy names gets a label`() {
        val unlabelled = policyGlobs.filter { labelsOf(sample(it)).isEmpty() }

        assertEquals("risk-tiers.policy globs with no labeler.yml label", emptyList<String>(), unlabelled)
    }

    @Test
    fun `every labelled path has a risk-tier rule`() {
        val samples = labelGlobs.values.flatten().associateBy(::sample)
        val ruled = pathsWithTierRule(samples.keys)

        val unruled = samples.filterKeys { it !in ruled }.values.toList()

        assertEquals("labeler.yml globs with no risk-tiers.policy rule", emptyList<String>(), unruled)
    }
}
