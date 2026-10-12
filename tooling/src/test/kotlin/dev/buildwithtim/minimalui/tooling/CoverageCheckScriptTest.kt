package dev.buildwithtim.minimalui.tooling

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * A Kover XML report (JaCoCo format) for [module] under [root], at the path `scripts/coverage-check.sh` reads. The
 * report-level METHOD and LINE counters carry the given numbers; the class-level counters deliberately disagree, so a
 * parser that picks the wrong level fails the tests.
 */
fun writeKoverReport(
    root: File,
    module: String,
    methods: Pair<Int, Int>,
    lines: Pair<Int, Int>,
    singleLine: Boolean = false,
) {
    val (methodsMissed, methodsCovered) = methods
    val (linesMissed, linesCovered) = lines
    val tags = listOf(
        """<?xml version="1.0" ?>""",
        """<report name="Kover Gradle Plugin XML report for :$module">""",
        """<package name="dev/example">""",
        """<class name="dev/example/Thing" sourcefilename="Thing.kt">""",
        """<method name="&lt;init&gt;" desc="()V">""",
        """<counter type="LINE" missed="0" covered="1"/>""",
        """</method>""",
        """<counter type="LINE" missed="0" covered="7"/>""",
        """<counter type="METHOD" missed="0" covered="3"/>""",
        """</class>""",
        """<counter type="LINE" missed="0" covered="7"/>""",
        """<counter type="METHOD" missed="0" covered="3"/>""",
        """</package>""",
        """<counter type="INSTRUCTION" missed="5" covered="5"/>""",
        """<counter type="LINE" missed="$linesMissed" covered="$linesCovered"/>""",
        """<counter type="METHOD" missed="$methodsMissed" covered="$methodsCovered"/>""",
        """</report>""",
    )
    val report = root.resolve("$module/build/reports/kover/report.xml")
    report.parentFile.mkdirs()
    report.writeText(tags.joinToString(if (singleLine) "" else "\n", postfix = "\n"))
}

class CoverageCheckScriptTest {

    private val root: File = Files.createTempDirectory("coverage-check").toFile()

    @After
    fun cleanUp() {
        root.deleteRecursively()
    }

    private fun check(vararg args: String) =
        runScript("scripts/coverage-check.sh", *args, env = mapOf("COVERAGE_ROOT" to root.path))

    @Test
    fun `passes when coverage equals the floors and prints observed against floor`() {
        writeKoverReport(root, "core", methods = 1 to 9, lines = 10 to 90)

        val result = check("core", "90", "90")

        assertEquals(result.stderr, 0, result.exitCode)
        assertTrue(
            result.stdout,
            result.stdout.contains("Coverage (core): functions 90.00% (floor 90%), lines 90.00% (floor 90%)"),
        )
    }

    @Test
    fun `fails when function coverage is just below its floor`() {
        writeKoverReport(root, "core", methods = 11 to 89, lines = 0 to 100)

        val result = check("core", "90", "90")

        assertEquals(1, result.exitCode)
        assertTrue(result.stderr, result.stderr.contains("core functions coverage 89.00% is below its floor of 90%"))
    }

    @Test
    fun `fails when line coverage is just below its floor`() {
        writeKoverReport(root, "core", methods = 0 to 10, lines = 1 to 2)

        val result = check("core", "0", "67")

        assertEquals(1, result.exitCode)
        assertTrue(result.stderr, result.stderr.contains("core lines coverage 66.67% is below its floor of 67%"))
    }

    @Test
    fun `compares the exact ratio, not the rounded percentage`() {
        // 99999 of 100000 functions is 99.999%, printed as 100.00%, yet still below a 100% floor.
        writeKoverReport(root, "core", methods = 1 to 99999, lines = 0 to 1)

        val result = check("core", "100", "0")

        assertEquals(result.stdout, 1, result.exitCode)
    }

    @Test
    fun `accepts a fractional floor and gates at it`() {
        // 2 of 3 is 66.666...%: it meets 66.66 but not 66.67.
        writeKoverReport(root, "core", methods = 1 to 2, lines = 1 to 2)

        assertEquals(0, check("core", "66.66", "66.66").exitCode)
        assertEquals(1, check("core", "66.67", "66.66").exitCode)
        assertEquals(1, check("core", "66.66", "66.67").exitCode)
    }

    @Test
    fun `reports both metrics when both are below their floors`() {
        writeKoverReport(root, "app", methods = 24 to 0, lines = 96 to 0)

        val result = check("app", "1", "1")

        assertEquals(1, result.exitCode)
        assertTrue(result.stderr, result.stderr.contains("app functions coverage 0.00% is below its floor of 1%"))
        assertTrue(result.stderr, result.stderr.contains("app lines coverage 0.00% is below its floor of 1%"))
    }

    @Test
    fun `a module without tests passes a zero floor`() {
        writeKoverReport(root, "app", methods = 24 to 0, lines = 96 to 0)

        assertEquals(0, check("app", "0", "0").exitCode)
    }

    @Test
    fun `reads a report written on a single line`() {
        writeKoverReport(root, "core", methods = 0 to 7, lines = 0 to 21, singleLine = true)

        val result = check("core", "100", "100")

        assertEquals(result.stderr, 0, result.exitCode)
        assertTrue(result.stdout, result.stdout.contains("functions 100.00%"))
    }

    @Test
    fun `fails with a hint when the report is missing`() {
        val result = check("core", "90", "90")

        assertEquals(2, result.exitCode)
        assertTrue(result.stderr, result.stderr.contains("core/build/reports/kover/report.xml"))
        assertTrue(result.stderr, result.stderr.contains("./gradlew :core:koverXmlReport"))
    }

    @Test
    fun `fails when the report has no module-level counter`() {
        val report = root.resolve("core/build/reports/kover/report.xml")
        report.parentFile.mkdirs()
        report.writeText("<?xml version=\"1.0\" ?>\n<report name=\"empty\">\n</report>\n")

        val result = check("core", "0", "0")

        assertEquals(2, result.exitCode)
        assertTrue(result.stderr, result.stderr.contains("no module-level METHOD counter"))
    }

    @Test
    fun `rejects malformed floors`() {
        writeKoverReport(root, "core", methods = 0 to 7, lines = 0 to 21)

        for (floors in listOf(listOf("ninety", "90"), listOf("90", ""), listOf("101", "90"), listOf("90", "-1"))) {
            val result = check("core", *floors.toTypedArray())

            assertEquals("floors $floors", 2, result.exitCode)
            assertTrue(result.stderr, result.stderr.contains("must be a percentage between 0 and 100"))
        }
    }

    @Test
    fun `rejects a wrong argument count`() {
        val result = check("core", "90")

        assertEquals(2, result.exitCode)
        assertTrue(result.stderr, result.stderr.contains("usage: coverage-check.sh <module> <functions> <lines>"))
    }
}
