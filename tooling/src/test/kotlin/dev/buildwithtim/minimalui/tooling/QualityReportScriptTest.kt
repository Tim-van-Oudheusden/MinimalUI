package dev.buildwithtim.minimalui.tooling

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class QualityReportScriptTest {

    private val dir: File = Files.createTempDirectory("quality-report").toFile()
    private val fixtures = dir.resolve("fixtures")
    private val coverageRoot = dir.resolve("root")
    private val calls = dir.resolve("gradle-calls.txt")
    private val config = dir.resolve("auto-qa-tuning.json")

    /**
     * Fake `./gradlew`: logs each call, fails a task named in FAIL_TASKS, and for `:<module>:koverXmlReport` copies
     * the module's fixture report into COVERAGE_ROOT, as Kover would write it.
     */
    private val gradle = dir.resolve("gradlew").apply {
        writeText(
            """
            #!/usr/bin/env bash
            echo "${'$'}*" >> "${calls.path}"
            echo "gradle output for ${'$'}*"
            for task in ${'$'}{FAIL_TASKS:-}; do
              [ "${'$'}task" = "${'$'}1" ] && exit 1
            done
            if [[ "${'$'}1" =~ ^:([a-z]+):koverXmlReport${'$'} ]]; then
              module="${'$'}{BASH_REMATCH[1]}"
              mkdir -p "${coverageRoot.path}/${'$'}module"
              cp -R "${fixtures.path}/${'$'}module/build" "${coverageRoot.path}/${'$'}module/"
            fi
            exit 0

            """.trimIndent(),
        )
        setExecutable(true)
    }

    init {
        config.writeText(
            """{"margin": 2, "floors": {"app": {"functions": 0, "lines": 0}, "core": {"functions": 0, "lines": 0}}}""",
        )
        writeKoverReport(fixtures, "app", methods = 20 to 4, lines = 90 to 10)
        writeKoverReport(fixtures, "core", methods = 0 to 7, lines = 1 to 20)
    }

    @After
    fun cleanUp() {
        dir.deleteRecursively()
    }

    private fun report(failTasks: String = "") = runScript(
        "scripts/quality-report.sh",
        env = mapOf(
            "GRADLE" to gradle.path,
            "COVERAGE_ROOT" to coverageRoot.path,
            "QA_TUNING_CONFIG" to config.path,
            "GITHUB_SHA" to "abc1234",
            "FAIL_TASKS" to failTasks,
        ),
    )

    @Test
    fun `reports every gate as passing and each module's coverage`() {
        val result = report()

        assertEquals(result.stderr, 0, result.exitCode)
        assertTrue(result.stdout, result.stdout.startsWith("# Quality report\n"))
        assertTrue(result.stdout, result.stdout.contains("Commit: `abc1234`"))
        assertTrue(result.stdout, result.stdout.contains("| `./gradlew test` | ✅ pass |"))
        assertTrue(result.stdout, result.stdout.contains("| `./gradlew lint` | ✅ pass |"))
        assertTrue(result.stdout, result.stdout.contains("| `./gradlew assembleDebug` | ✅ pass |"))
        assertTrue(result.stdout, result.stdout.contains("| app | 16.67% | 10.00% |"))
        assertTrue(result.stdout, result.stdout.contains("| core | 100.00% | 95.24% |"))
    }

    @Test
    fun `keeps Gradle output out of the report`() {
        val result = report()

        assertFalse(result.stdout, result.stdout.contains("gradle output"))
        assertTrue(result.stderr, result.stderr.contains("gradle output for lint"))
    }

    @Test
    fun `marks a failing gate, still runs the others, and exits non-zero`() {
        val result = report(failTasks = "test")

        assertEquals(1, result.exitCode)
        assertTrue(result.stdout, result.stdout.contains("| `./gradlew test` | ❌ fail |"))
        assertTrue(result.stdout, result.stdout.contains("| `./gradlew assembleDebug` | ✅ pass |"))
        assertTrue(result.stdout, result.stdout.contains("| core | 100.00% | 95.24% |"))
        assertEquals(
            listOf("test", "lint", "assembleDebug", ":app:koverXmlReport", ":core:koverXmlReport"),
            calls.readLines(),
        )
    }

    @Test
    fun `reports n-a and exits non-zero when a module's coverage run fails, ignoring a stale report`() {
        writeKoverReport(coverageRoot, "app", methods = 0 to 1, lines = 0 to 1)

        val result = report(failTasks = ":app:koverXmlReport")

        assertEquals(1, result.exitCode)
        assertTrue(result.stdout, result.stdout.contains("| app | ❌ n/a | ❌ n/a |"))
        assertTrue(result.stdout, result.stdout.contains("| core | 100.00% | 95.24% |"))
    }

    @Test
    fun `measures exactly the modules the floors file lists`() {
        config.writeText("""{"margin": 2, "floors": {"core": {"functions": 0, "lines": 0}}}""")

        val result = report()

        assertEquals(result.stderr, 0, result.exitCode)
        assertFalse(result.stdout, result.stdout.contains("| app |"))
        assertEquals(listOf("test", "lint", "assembleDebug", ":core:koverXmlReport"), calls.readLines())
    }

    @Test
    fun `fails before running Gradle when the floors file lists no modules`() {
        for (text in listOf("not json", """{"margin": 2, "floors": {}}""")) {
            config.writeText(text)

            val result = report()

            assertEquals(text, 2, result.exitCode)
            assertTrue(result.stderr, result.stderr.contains(config.path))
            assertFalse(text, calls.exists())
        }
    }

    @Test
    fun `the tuner reads the coverage rows the report prints`() {
        val qualityReport = dir.resolve("quality-report.md").apply { writeText(report().stdout) }

        val result = runScript(
            "scripts/auto-qa-tuner.sh",
            "--config", config.path,
            "--report", qualityReport.path,
        )

        assertEquals(result.stderr, 0, result.exitCode)
        assertTrue(result.stdout, result.stdout.contains("| app | functions | 0% | 16.67% | 14% ⬆️ |"))
        assertTrue(result.stdout, result.stdout.contains("| core | lines | 0% | 95.24% | 93% ⬆️ |"))
    }
}
