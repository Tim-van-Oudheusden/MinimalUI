package dev.buildwithtim.minimalui.tooling

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** One `<mutation>` of a PIT XML report, on one line as PIT writes it. */
fun pitMutation(
    status: String,
    method: String = "take",
    line: Int = 12,
    description: String = "changed conditional boundary",
    mutatedClass: String = "dev.example.JsonCursor",
): String =
    "<mutation detected='${status == "KILLED"}' status='$status' numberOfTestsRun='1'>" +
        "<sourceFile>Json.kt</sourceFile><mutatedClass>$mutatedClass</mutatedClass>" +
        "<mutatedMethod>$method</mutatedMethod><methodDescription>(I)Ljava/lang/String;</methodDescription>" +
        "<lineNumber>$line</lineNumber>" +
        "<mutator>org.pitest.mutationtest.engine.gregor.mutators.ConditionalsBoundaryMutator</mutator>" +
        "<indexes><index>7</index></indexes><blocks><block>2</block></blocks><killingTest/>" +
        "<description>$description</description></mutation>"

class MutationCheckScriptTest {
    private val root: File = Files.createTempDirectory("mutation-check").toFile()
    private val report = root.resolve("mutations.xml")
    private val baseline = root.resolve("baseline.txt")

    @After
    fun cleanUp() {
        root.deleteRecursively()
    }

    private fun writeReport(vararg mutations: String) {
        val head = listOf("""<?xml version="1.0" encoding="UTF-8"?>""", """<mutations partial="true">""")
        report.writeText((head + mutations + "</mutations>").joinToString("\n", postfix = "\n"))
    }

    private fun check(vararg args: String) = runScript("scripts/mutation-check.sh", *args)

    @Test
    fun `a survivor the baseline does not list fails the check and is named`() {
        writeReport(pitMutation("KILLED", method = "skipSpace"), pitMutation("SURVIVED", method = "take"))
        baseline.writeText("")

        val result = check(report.path, baseline.path)

        assertEquals(result.stdout, 1, result.exitCode)
        assertTrue(result.stderr, result.stderr.contains("dev.example.JsonCursor take(I)Ljava/lang/String;"))
        assertTrue(result.stderr, result.stderr.contains("changed conditional boundary"))
        assertFalse(result.stderr, result.stderr.contains("skipSpace"))
    }

    @Test
    fun `an uncovered mutant counts as a survivor`() {
        writeReport(pitMutation("NO_COVERAGE"))
        baseline.writeText("")

        assertEquals(1, check(report.path, baseline.path).exitCode)
    }

    @Test
    fun `passes when the baseline lists every survivor, wherever its line moved`() {
        writeReport(pitMutation("SURVIVED", line = 12))
        check("--write", report.path, baseline.path)
        writeReport(pitMutation("SURVIVED", line = 40), pitMutation("KILLED", method = "skipSpace"))

        val result = check(report.path, baseline.path)

        assertEquals(result.stderr, 0, result.exitCode)
    }

    @Test
    fun `a second identical survivor in the same method fails when the baseline has one`() {
        writeReport(pitMutation("SURVIVED", line = 12))
        check("--write", report.path, baseline.path)
        writeReport(pitMutation("SURVIVED", line = 12), pitMutation("SURVIVED", line = 15))

        val result = check(report.path, baseline.path)

        assertEquals(result.stdout, 1, result.exitCode)
        assertTrue(result.stderr, result.stderr.contains("1 survivor"))
    }

    @Test
    fun `a survivor that occurs twice passes when the baseline lists it twice`() {
        writeReport(pitMutation("SURVIVED", line = 12), pitMutation("SURVIVED", line = 15))
        check("--write", report.path, baseline.path)

        val result = check(report.path, baseline.path)

        assertEquals(result.stderr, 0, result.exitCode)
        assertFalse(result.stdout, result.stdout.contains("no longer survive"))
    }

    @Test
    fun `a baseline entry that no longer survives passes and is reported for removal`() {
        writeReport(pitMutation("SURVIVED"))
        check("--write", report.path, baseline.path)
        writeReport(pitMutation("KILLED"))

        val result = check(report.path, baseline.path)

        assertEquals(result.stderr, 0, result.exitCode)
        assertTrue(result.stdout, result.stdout.contains("no longer survive"))
        assertTrue(result.stdout, result.stdout.contains("dev.example.JsonCursor take"))
    }

    @Test
    fun `comments and blank lines in the baseline are not entries`() {
        writeReport(pitMutation("SURVIVED"))
        check("--write", report.path, baseline.path)
        baseline.writeText("# reviewed 2026-11-14\n\n" + baseline.readText())

        val result = check(report.path, baseline.path)

        assertEquals(result.stderr, 0, result.exitCode)
        assertFalse(result.stdout, result.stdout.contains("no longer survive"))
    }

    @Test
    fun `write records every survivor and no killed mutant`() {
        writeReport(
            pitMutation("SURVIVED", method = "take"),
            pitMutation("NO_COVERAGE", method = "peek"),
            pitMutation("KILLED", method = "skipSpace"),
            pitMutation("TIMED_OUT", method = "loop"),
        )

        val result = check("--write", report.path, baseline.path)

        assertEquals(result.stderr, 0, result.exitCode)
        val entries = baseline.readLines().filter { it.isNotBlank() && !it.startsWith("#") }
        assertEquals(entries.joinToString("\n"), 2, entries.size)
        assertTrue(entries.any { "take" in it } && entries.any { "peek" in it })
    }

    @Test
    fun `a missing report is a usage error, not a pass`() {
        baseline.writeText("")

        val result = check(root.resolve("absent.xml").path, baseline.path)

        assertEquals(2, result.exitCode)
        assertTrue(result.stderr, result.stderr.contains("absent.xml"))
    }

    @Test
    fun `a report with no mutations is an error, not a pass`() {
        writeReport()
        baseline.writeText("")

        assertEquals(2, check(report.path, baseline.path).exitCode)
    }

    @Test
    fun `a missing baseline is a usage error`() {
        writeReport(pitMutation("KILLED"))

        assertEquals(2, check(report.path, root.resolve("absent.txt").path).exitCode)
    }

    @Test
    fun `wrong arguments are a usage error`() {
        assertEquals(2, check(report.path).exitCode)
    }
}
