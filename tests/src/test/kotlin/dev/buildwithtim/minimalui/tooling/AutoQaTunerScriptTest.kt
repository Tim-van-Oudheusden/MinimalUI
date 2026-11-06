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

    /** Runs the tuner with a `jq` on PATH that exits 5 when [failingArgument] is among its arguments. */
    private fun tuneWithJqFailingOn(
        failingArgument: String,
        vararg args: String,
    ): ScriptResult {
        val path = System.getenv("PATH")
        val bin = dir.resolve("bin").apply { mkdirs() }
        bin.resolve("jq").apply {
            writeText(
                """
                #!/usr/bin/env bash
                for argument in "${'$'}@"; do
                  [ "${'$'}argument" = "$failingArgument" ] && exit 5
                done
                PATH="$path" exec jq "${'$'}@"

                """.trimIndent(),
            )
            setExecutable(true)
        }
        return runScript(
            "scripts/auto-qa-tuner.sh",
            "--config",
            config.path,
            "--report",
            report.path,
            *args,
            env = mapOf("PATH" to "${bin.path}:$path"),
        )
    }

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
    fun `takes a margin of zero as observed coverage rounded down`() {
        writeConfig(0, "core" to (0 to 0))
        writeReport("| core | 90.09% | 80.10% |")

        val result = tune()

        assertEquals(result.stderr, 0, result.exitCode)
        assertTrue(result.stdout, result.stdout.contains("| core | functions | 0% | 90.09% | 90% ⬆️ |"))
        assertTrue(result.stdout, result.stdout.contains("| core | lines | 0% | 80.10% | 80% ⬆️ |"))
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
    fun `keeps a fractional floor instead of rounding it down`() {
        config.writeText("{\"margin\": 2, \"floors\": {\"core\": {\"functions\": 97.5, \"lines\": 90.25}}}\n")
        writeReport("| core | 96.00% | 91.00% |")

        val result = tune("--write")

        assertEquals(result.stderr, 0, result.exitCode)
        assertTrue(result.stdout, result.stdout.contains("| core | functions | 97.5% | 96.00% | 97.5% |"))
        assertTrue(result.stdout, result.stdout.contains("| core | lines | 90.25% | 91.00% | 90.25% |"))
        assertTrue(config.readText(), config.readText().contains("\"functions\": 97.5,"))
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
        assertTrue(written.stderr, written.stderr.contains("Wrote the proposed floors to ${config.path}"))
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
                """{"margin": 2, "floors": {"a": {"functions": 0, "lines": 0}, "b": {"functions": -1, "lines": 0}}}""",
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
    fun `fails and prints no table when the floors cannot be tuned`() {
        writeConfig(2, "core" to (90 to 90))
        writeReport("| core | 99.12% | 96.03% |")

        val result = tuneWithJqFailingOn("--rawfile")

        assertEquals(2, result.exitCode)
        assertTrue(result.stderr, result.stderr.contains("Could not tune ${config.path} from ${report.path}"))
        assertEquals("", result.stdout)
    }

    @Test
    fun `fails when the proposed floors cannot be written`() {
        writeConfig(2, "core" to (90 to 90))
        writeReport("| core | 99.12% | 96.03% |")

        val result = tuneWithJqFailingOn(".config", "--write")

        assertEquals(2, result.exitCode)
        assertTrue(result.stderr, result.stderr.contains("Could not write ${config.path}"))
    }

    @Test
    fun `defaults to the repository's floors and the quality report in the working directory`() {
        writeReport("| core | 99.12% | 96.03% |")

        val result = runScript("scripts/auto-qa-tuner.sh", workDir = dir)

        assertEquals(result.stderr, 0, result.exitCode)
        assertTrue(result.stdout, result.stdout.contains("| core | lines |"))
    }

    @Test
    fun `takes its flags in either order`() {
        writeConfig(2, "core" to (90 to 90))
        writeReport("| core | 99.12% | 96.03% |")

        val result = runScript("scripts/auto-qa-tuner.sh", "--report", report.path, "--config", config.path)

        assertEquals(result.stderr, 0, result.exitCode)
        assertTrue(result.stdout, result.stdout.contains("| core | lines | 90% | 96.03% | 94% ⬆️ |"))
    }

    // Under set -u a missing $2 stops the script with bash's error and exit 1; only a guard gives usage and exit 2.
    @Test
    fun `rejects a trailing flag that has no value`() {
        for (flag in listOf("--config", "--report")) {
            val result = runScript("scripts/auto-qa-tuner.sh", flag)

            assertEquals(flag, 2, result.exitCode)
            assertTrue(result.stderr, result.stderr.contains("usage: auto-qa-tuner.sh"))
        }
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
