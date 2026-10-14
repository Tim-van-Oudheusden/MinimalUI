package dev.buildwithtim.minimalui.tooling

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class NightlyComplianceScriptTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun alert(
        ghsa: String,
        severity: String,
        pkg: String = "com.example:lib",
    ) = """
        {"number": 1, "state": "open", "html_url": "https://github.com/o/r/security/dependabot/1",
         "dependency": {"package": {"ecosystem": "maven", "name": "$pkg"}},
         "security_advisory": {"ghsa_id": "$ghsa", "severity": "$severity", "summary": "Bad thing in $pkg"}}
        """.trimIndent()

    private fun compliance(
        vararg alerts: String,
        ignore: String? = null,
        graph: String = "success",
        wrapper: String = "success",
        alertsJson: String = "[${alerts.joinToString(",")}]",
    ): ScriptResult {
        val file = tmp.newFile("alerts.json").apply { writeText(alertsJson) }
        val env =
            mutableMapOf(
                "ALERTS_JSON" to file.path,
                "DEPENDENCY_GRAPH_RESULT" to graph,
                "WRAPPER_VALIDATION_OUTCOME" to wrapper,
            )
        if (ignore != null) env["ALERTS_IGNORE"] = ignore
        return runScript("scripts/nightly-compliance.sh", env = env)
    }

    private fun ScriptResult.row(check: String) = stdout.lines().single { it.startsWith("| $check |") }

    @Test
    fun `passes with no open alerts`() {
        val result = compliance()

        assertEquals(result.stdout + result.stderr, 0, result.exitCode)
        assertTrue(result.stdout, result.row("Dependabot alerts (high+)").contains("pass"))
    }

    @Test
    fun `fails on an open high alert and names it`() {
        val result = compliance(alert("GHSA-aaaa-bbbb-cccc", "high"))

        assertEquals(1, result.exitCode)
        assertTrue(result.stdout, result.row("Dependabot alerts (high+)").contains("fail"))
        assertTrue(result.stdout, result.stdout.contains("GHSA-aaaa-bbbb-cccc"))
    }

    @Test
    fun `fails on an open critical alert`() {
        assertEquals(1, compliance(alert("GHSA-aaaa-bbbb-cccc", "critical")).exitCode)
    }

    @Test
    fun `ignores medium and low alerts`() {
        val result = compliance(alert("GHSA-mmmm-mmmm-mmmm", "medium"), alert("GHSA-llll-llll-llll", "low"))

        assertEquals(result.stdout + result.stderr, 0, result.exitCode)
    }

    @Test
    fun `passes when the only high alert is on the ignore list`() {
        val result =
            compliance(alert("GHSA-aaaa-bbbb-cccc", "high"), ignore = "GHSA-zzzz-zzzz-zzzz GHSA-aaaa-bbbb-cccc")

        assertEquals(result.stdout + result.stderr, 0, result.exitCode)
    }

    @Test
    fun `still fails on a high alert that is not on the ignore list`() {
        val result =
            compliance(
                alert("GHSA-aaaa-bbbb-cccc", "high"),
                alert("GHSA-dddd-eeee-ffff", "critical"),
                ignore = "GHSA-aaaa-bbbb-cccc",
            )

        assertEquals(1, result.exitCode)
        assertTrue(result.stdout, result.stdout.contains("GHSA-dddd-eeee-ffff"))
    }

    @Test
    fun `fails when the alerts cannot be read`() {
        val result = compliance(alertsJson = """{"message": "Resource not accessible by integration"}""")

        assertEquals(1, result.exitCode)
        assertTrue(result.stdout, result.row("Dependabot alerts (high+)").contains("fail"))
    }

    @Test
    fun `fails when the wrapper jar did not validate`() {
        val result = compliance(wrapper = "failure")

        assertEquals(1, result.exitCode)
        assertTrue(result.stdout, result.row("Gradle wrapper jar checksum").contains("fail"))
    }

    @Test
    fun `fails when a check reports no outcome`() {
        val result =
            runScript(
                "scripts/nightly-compliance.sh",
                env = mapOf("ALERTS_JSON" to tmp.newFile("empty.json").apply { writeText("[]") }.path),
            )

        assertEquals(1, result.exitCode)
        assertTrue(result.stdout, result.row("Gradle wrapper jar checksum").contains("fail"))
        assertTrue(result.stdout, result.row("Dependency graph submission").contains("fail"))
    }

    @Test
    fun `runs every check even when an earlier one fails`() {
        val result = compliance(alert("GHSA-aaaa-bbbb-cccc", "high"), graph = "failure", wrapper = "failure")

        assertEquals(1, result.exitCode)
        listOf("Dependency graph submission", "Gradle wrapper jar checksum", "Dependabot alerts (high+)").forEach {
            assertTrue(result.stdout, result.row(it).contains("fail"))
        }
    }
}
