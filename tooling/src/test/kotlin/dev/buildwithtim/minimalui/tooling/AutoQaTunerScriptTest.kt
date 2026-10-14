package dev.buildwithtim.minimalui.tooling

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class AutoQaTunerScriptTest {
    private val dir: File = Files.createTempDirectory("auto-qa-tuner").toFile()
    private val config = dir.resolve("auto-qa-tuning.json")
    private val report = dir.resolve("quality-report.md")

    @After
    fun cleanUp() {
        dir.deleteRecursively()
    }

    private fun writeConfig(
        margin: Int,
        vararg floors: Pair<String, Pair<Int, Int>>,
    ) {
        val modules =
            floors.joinToString(",\n") { (module, floor) ->
                "    \"$module\": {\n      \"functions\": ${floor.first},\n      \"lines\": ${floor.second}\n    }"
            }
        config.writeText("{\n  \"margin\": $margin,\n  \"floors\": {\n$modules\n  }\n}\n")
    }

    // Coverage rows in the format scripts/quality-report.sh prints.
    private fun writeReport(vararg rows: String) {
        report.writeText(
            (
                listOf(
                    "## Unit-test coverage",
                    "",
                    "| Module | Functions | Lines |",
                    "| ------ | --------- | ----- |",
                ) +
                    rows + ""
            ).joinToString("\n"),
        )
    }

    private fun tune(vararg args: String) =
        runScript("scripts/auto-qa-tuner.sh", "--config", config.path, "--report", report.path, *args)

    @Test
    fun `proposes raising a floor to observed coverage minus the margin, rounded down`() {
        writeConfig(2, "core" to (95 to 90))
        writeReport("| core | 99.12% | 96.03% |")

        val result = tune()

        assertEquals(result.stderr, 0, result.exitCode)
        assertTrue(result.stdout, result.stdout.contains("| core | functions | 95% | 99.12% | 97% ⬆️ |"))
        assertTrue(result.stdout, result.stdout.contains("| core | lines | 90% | 96.03% | 94% ⬆️ |"))
    }

    @Test
    fun `uses the margin from the config`() {
        writeConfig(5, "core" to (0 to 0))
        writeReport("| core | 92.00% | 80.29% |")

        val result = tune()

        assertTrue(result.stdout, result.stdout.contains("Margin: 5 percentage points below observed coverage."))
        assertTrue(result.stdout, result.stdout.contains("| core | functions | 0% | 92.00% | 87% ⬆️ |"))
        assertTrue(result.stdout, result.stdout.contains("| core | lines | 0% | 80.29% | 75% ⬆️ |"))
    }

    @Test
    fun `never proposes lowering a floor`() {
        writeConfig(2, "core" to (100 to 90))
        writeReport("| core | 100.00% | 85.00% |")

        val result = tune()

        assertEquals(0, result.exitCode)
        assertTrue(result.stdout, result.stdout.contains("| core | functions | 100% | 100.00% | 100% |"))
        assertTrue(result.stdout, result.stdout.contains("| core | lines | 90% | 85.00% | 90% |"))
    }

    @Test
    fun `keeps a module's floors when the report has no coverage for it`() {
        writeConfig(2, "app" to (10 to 12), "core" to (90 to 90))
        writeReport("| app | ❌ n/a | ❌ n/a |")

        val result = tune()

        assertEquals(result.stderr, 0, result.exitCode)
        assertTrue(result.stdout, result.stdout.contains("| app | functions | 10% | n/a | 10% |"))
        assertTrue(result.stdout, result.stdout.contains("| app | lines | 12% | n/a | 12% |"))
        assertTrue(result.stdout, result.stdout.contains("| core | lines | 90% | n/a | 90% |"))
    }

    @Test
    fun `writes the proposed floors back to the config only with --write`() {
        writeConfig(2, "app" to (0 to 0), "core" to (95 to 90))
        writeReport("| app | ❌ n/a | ❌ n/a |", "| core | 99.12% | 96.03% |")
        val original = config.readText()

        assertEquals(0, tune().exitCode)
        assertEquals(original, config.readText())

        val written = tune("--write")

        assertEquals(written.stderr, 0, written.exitCode)
        assertEquals(
            """
            {
              "margin": 2,
              "floors": {
                "app": {
                  "functions": 0,
                  "lines": 0
                },
                "core": {
                  "functions": 97,
                  "lines": 94
                }
              }
            }

            """.trimIndent(),
            config.readText(),
        )
    }

    @Test
    fun `rounds away float error before flooring`() {
        // 70.1 - 0.1 is 69.99999999999999 in binary floating point; the proposal must still be 70.
        config.writeText("""{"margin": 0.1, "floors": {"core": {"functions": 0, "lines": 0}}}""")
        writeReport("| core | 70.10% | 70.10% |")

        val result = tune()

        assertEquals(result.stderr, 0, result.exitCode)
        assertTrue(result.stdout, result.stdout.contains("| core | functions | 0% | 70.10% | 70% ⬆️ |"))
    }

    @Test
    fun `rejects a malformed config without touching it`() {
        writeReport("| core | 99.12% | 96.03% |")
        val malformed =
            listOf(
                "not json",
                """{"floors": {"core": {"functions": 90, "lines": 90}}}""",
                """{"margin": -1, "floors": {"core": {"functions": 90, "lines": 90}}}""",
                """{"margin": 2, "floors": {}}""",
                """{"margin": 2, "floors": {"core": {"functions": "90", "lines": 90}}}""",
                """{"margin": 2, "floors": {"core": {"functions": 90}}}""",
                """{"margin": 2, "floors": {"core": {"functions": 90, "lines": 101}}}""",
            )

        for (text in malformed) {
            config.writeText(text)

            val result = tune("--write")

            assertEquals(text, 2, result.exitCode)
            assertTrue(result.stderr, result.stderr.contains(config.path))
            assertEquals(text, config.readText())
        }
    }

    @Test
    fun `fails when the report is missing`() {
        writeConfig(2, "core" to (90 to 90))

        val result = tune()

        assertEquals(2, result.exitCode)
        assertTrue(result.stderr, result.stderr.contains(report.path))
    }

    @Test
    fun `rejects an unknown option`() {
        writeConfig(2, "core" to (90 to 90))
        writeReport("| core | 99.12% | 96.03% |")

        val result = tune("--wirte")

        assertEquals(2, result.exitCode)
        assertTrue(result.stderr, result.stderr.contains("usage: auto-qa-tuner.sh"))
    }
}
