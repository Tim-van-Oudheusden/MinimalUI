package dev.buildwithtim.minimalui.tooling

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.JUnitCore
import org.junit.runner.Result
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * A mutation gate for `scripts/` (#442). PIT mutates only the Kotlin modules, so nothing checked that a script's tests
 * fail when its behaviour changes, and four times a branch ran in a test that asserted nothing about it (#309, #343,
 * #388, #442).
 *
 * Each row of [mutants] names a script, an exact edit to it and the test class that must fail on it. The test copies
 * the repository's `scripts/` and the `.github/` policy files they read into a temp directory, applies the edit there
 * and runs that class in this JVM against the copy: [runScript] takes its scripts from the directory the
 * [Repo.SCRIPTS_ROOT] system property names. First each class must pass on an unmutated copy, so no row is killed by a
 * broken copy.
 *
 * To add a row: name the script, a piece of its text that occurs exactly once, its replacement, the test class that
 * runs the script, and what the change would break. A new or changed script adds rows for its error and exit
 * branches. A row that survives needs a test that fails on it; write that test, not a weaker row.
 */
class ScriptMutantsTest {
    private val root: File = Files.createTempDirectory("script-mutants").toFile()

    private data class Mutant(
        val script: String,
        val old: String,
        val replacement: String,
        val testClass: Class<*>,
        val breaks: String,
    )

    private val mutants =
        listOf(
            Mutant(
                "scripts/pitest-minion-java.sh",
                "tmp=\"\$PITEST_MINION_TMP/minion.\$\$\"",
                "tmp=\"\$PITEST_MINION_TMP/\$\$\"",
                PitestMinionJavaScriptTest::class.java,
                "the cleanup globs minion.*, so no temp directory is deleted again",
            ),
            Mutant(
                "scripts/pitest-minion-java.sh",
                "tmp=\"\$PITEST_MINION_TMP/minion.\$\$\"",
                "tmp=\"\$PITEST_MINION_TMP/minion.\$RANDOM\"",
                PitestMinionJavaScriptTest::class.java,
                "the name holds no process ID, so the next minion deletes a running one's directory or none",
            ),
            Mutant(
                "scripts/pitest-minion-java.sh",
                "exec \"\$PITEST_MINION_JAVA\"",
                "\"\$PITEST_MINION_JAVA\"",
                PitestMinionJavaScriptTest::class.java,
                "the JVM is not the process the directory is named after, and PIT's kill reaches bash",
            ),
            Mutant(
                "scripts/pitest-minion-java.sh",
                "mkdir -p \"\$tmp\" || exit 2",
                "mkdir -p \"\$tmp\"",
                PitestMinionJavaScriptTest::class.java,
                "java starts without its temp directory instead of the script exiting 2",
            ),
            Mutant(
                "scripts/release-version.sh",
                "^[1-9][0-9]*\$",
                "^[0-9]+\$",
                ReleaseVersionScriptTest::class.java,
                "a release with versionCode 0 passes",
            ),
            Mutant(
                "scripts/release-version.sh",
                "^[1-9][0-9]*\$ ]]",
                "^[1-9][0-9]* ]]",
                ReleaseVersionScriptTest::class.java,
                "a versionCode line with a trailing comment passes and the comment reaches version_code",
            ),
            Mutant(
                "scripts/release-version.sh",
                "=~ ^[0-9]+\\.",
                "=~ [0-9]+\\.",
                ReleaseVersionScriptTest::class.java,
                "versionName v1.0.0 passes and the release is tagged vv1.0.0",
            ),
            Mutant(
                "scripts/release-version.sh",
                "(-[0-9A-Za-z.-]+)?\$ ]]",
                "(-[0-9A-Za-z.-]+)? ]]",
                ReleaseVersionScriptTest::class.java,
                "versionName 1.0.0 beta passes and the tag holds a space",
            ),
            Mutant(
                "scripts/merge-queue.sh",
                " and .headRefOid == \\\"\$head\\\"",
                "",
                MergeQueueScriptTest::class.java,
                "a failed merge passes for the verified one when GitHub merged the pull request at a later head",
            ),
            Mutant(
                "scripts/tier-classify.sh",
                "read -r item tier glob extra || [ -n \"\${item:-}\" ]; do",
                "read -r item tier glob extra; do",
                TierClassifyScriptTest::class.java,
                "a risk assessment without a final newline loses its last rule, and the paths it rates fall to medium",
            ),
            Mutant(
                "scripts/tier-classify.sh",
                "[ -r \"\$policy\" ] || fail",
                "[ -r \"\$policy\" ] || true",
                TierClassifyScriptTest::class.java,
                "a missing risk assessment is reported as one holding no rules, without naming the file",
            ),
            Mutant(
                "scripts/mutation-check.sh",
                "printf '  %s\\n' \"\${gone//\$'\\n'/\$'\\n  '}\"",
                "printf '  %s\\n' \"\$gone\"",
                MutationCheckScriptTest::class.java,
                "only the first stale entry is indented, so in the log the next one reads as a new survivor",
            ),
            Mutant(
                "scripts/mutation-check.sh",
                "printf '  %s\\n' \"\${new//\$'\\n'/\$'\\n  '}\" >&2",
                "printf '  %s\\n' \"\$new\" >&2",
                MutationCheckScriptTest::class.java,
                "only the first new survivor is indented, so the rest read as lines of the next message",
            ),
            Mutant(
                "scripts/auto-issues.sh",
                "failure | timed_out | startup_failure)",
                "failure | timed_out)",
                AutoIssuesScriptTest::class.java,
                "a workflow that cannot start on main opens no issue, and such a run has no log to notice it by",
            ),
            Mutant(
                "scripts/auto-issues.sh",
                "--label \"\$label\" --limit 200",
                "--limit 200",
                AutoIssuesScriptTest::class.java,
                "any open issue with the workflow's title, not only the ci one, gets the comments and is closed",
            ),
            Mutant(
                "scripts/strategy-report.sh",
                "def cell: gsub(\"\\\\|\"; \"\\\\|\");",
                "def cell: .;",
                StrategyReportScriptTest::class.java,
                "an ACMM gap title holding | adds a column to the ACMM progress table",
            ),
            Mutant(
                "scripts/strategy-report.sh",
                "\"| No stage | \\(\$open | map(",
                "\"| No stage | \\(\$all | map(",
                StrategyReportScriptTest::class.java,
                "closed issues without a stage label count in a table of open issues",
            ),
            Mutant(
                "scripts/strategy-report.sh",
                "def list(\$what): if type != \"array\"",
                "def list(\$what): if false",
                StrategyReportScriptTest::class.java,
                "an object in place of the issue list gives a report and exit 0",
            ),
            Mutant(
                "scripts/pr-metrics.sh",
                "if type != \"array\" then error(",
                "if false then error(",
                PrMetricsScriptTest::class.java,
                "an object in place of the PR list gives a report and exit 0",
            ),
            Mutant(
                "scripts/ai-audit-report.sh",
                "if type != \"array\" then error(",
                "if false then error(",
                AiAuditReportScriptTest::class.java,
                "an object in place of the PR list gives a report and exit 0",
            ),
            Mutant(
                "scripts/ai-audit-report.sh",
                "[[ \"\$since\" =~ ^[0-9]",
                "[[ \"\$since\" =~ [0-9]",
                AiAuditReportScriptTest::class.java,
                "--since x2026-10-01 passes and the report is silently empty",
            ),
            Mutant(
                "scripts/ai-audit-report.sh",
                "[0-9]{2}-[0-9]{2}\$ ]]",
                "[0-9]{2}-[0-9]{2} ]]",
                AiAuditReportScriptTest::class.java,
                "--since 2026-10-01x passes and the PRs merged that day drop out of the window",
            ),
            Mutant(
                "scripts/ai-audit-report.sh",
                "sort_by(.mergedAt, .number)",
                "sort_by(.mergedAt)",
                AiAuditReportScriptTest::class.java,
                "PRs merged in the same second keep gh's newest-first order in the ledger",
            ),
            Mutant(
                "scripts/lib/pr-source.jq",
                "select(startswith(\"— hive: \"))",
                "select(contains(\"— hive: \"))",
                PrMetricsScriptTest::class.java,
                "a PR body that quotes Hive's signature mid-line counts as agent work",
            ),
        )

    @After
    fun cleanUp() {
        root.deleteRecursively()
    }

    @Test
    fun `the script tests fail on every mutant in the table`() {
        val unmutated = copyOfScripts("unmutated")
        for (testClass in mutants.map { it.testClass }.distinct()) {
            val result = runAgainst(unmutated, testClass)
            assertTrue("${testClass.simpleName} fails on unmutated scripts: ${result.failures}", result.wasSuccessful())
        }

        val survivors =
            mutants.filterIndexed { index, mutant ->
                val copy = copyOfScripts("mutant-$index")
                mutant.applyTo(copy)
                runAgainst(copy, mutant.testClass).wasSuccessful()
            }

        assertEquals(
            "mutants their test class still passes on",
            emptyList<String>(),
            survivors.map { "${it.script}: `${it.old}` -> `${it.replacement}` (${it.breaks})" },
        )
    }

    /**
     * A copy of the repository's `scripts/`, modes and all, in [root]/[name]/scripts, with `.github/` beside it:
     * scripts find their policy files through their own root (`tier-classify.sh` reads `.github/risk-assessment.yml`,
     * `auto-qa-tuner.sh` `.github/auto-qa-tuning.json`), so without it their test classes fail on an unmutated copy and
     * no row can name them.
     */
    private fun copyOfScripts(name: String): File {
        val copy = root.resolve(name)
        for (source in listOf("scripts", ".github").flatMap { Repo.file(it).walkTopDown() }) {
            val target = copy.resolve(source.relativeTo(Repo.root))
            target.parentFile.mkdirs()
            Files.copy(source.toPath(), target.toPath(), StandardCopyOption.COPY_ATTRIBUTES)
        }
        return copy
    }

    /** Replaces [Mutant.old], which must occur exactly once in its script, in [copy]'s copy of the script. */
    private fun Mutant.applyTo(copy: File) {
        val file = copy.resolve(script)
        val text = file.readText()
        assertEquals("times `$old` occurs in $script", 1, text.split(old).size - 1)
        file.writeText(text.replace(old, replacement))
    }

    /** Runs [testClass] with [runScript] taking its scripts from [copy]. */
    private fun runAgainst(
        copy: File,
        testClass: Class<*>,
    ): Result {
        System.setProperty(Repo.SCRIPTS_ROOT, copy.path)
        try {
            return JUnitCore().run(testClass)
        } finally {
            System.clearProperty(Repo.SCRIPTS_ROOT)
        }
    }
}
