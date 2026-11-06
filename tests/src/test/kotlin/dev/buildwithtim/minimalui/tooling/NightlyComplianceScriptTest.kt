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
        state: String = "open",
    ) = """
        {"number": 1, "state": "$state", "html_url": "https://github.com/o/r/security/dependabot/1",
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
                "GITHUB_SHA" to "abc1234",
                "DEPENDENCY_GRAPH_RESULT" to graph,
                "WRAPPER_VALIDATION_OUTCOME" to wrapper,
            )
        if (ignore != null) env["ALERTS_IGNORE"] = ignore
        return runScript("scripts/nightly-compliance.sh", env = env)
    }

    /**
     * Runs without ALERTS_JSON, as the workflow does. Fake `gh` first on PATH: logs its arguments, answers `repo view`
     * with owner/from-gh, and `api` with [apiOutput] and [apiExit].
     */
    private fun complianceFromApi(
        apiOutput: String,
        apiExit: Int = 0,
        repository: String = "owner/minimalui",
    ): ScriptResult {
        val bin = tmp.newFolder("bin")
        val output = tmp.newFile("api-output").apply { writeText(apiOutput) }
        bin.resolve("gh").apply {
            writeText(
                """
                #!/usr/bin/env bash
                echo "${'$'}*" >> "${ghCalls.path}"
                if [ "${'$'}1" = repo ]; then echo owner/from-gh; exit 0; fi
                cat "${output.path}"
                exit $apiExit

                """.trimIndent(),
            )
            setExecutable(true)
        }
        return runScript(
            "scripts/nightly-compliance.sh",
            env =
                mapOf(
                    "PATH" to "${bin.path}:${System.getenv("PATH")}",
                    "ALERTS_JSON" to "",
                    "GITHUB_REPOSITORY" to repository,
                    "GITHUB_SHA" to "abc1234",
                    "DEPENDENCY_GRAPH_RESULT" to "success",
                    "WRAPPER_VALIDATION_OUTCOME" to "success",
                ),
        )
    }

    private val ghCalls by lazy { tmp.root.resolve("gh-calls.txt") }

    private fun ScriptResult.row(check: String) = stdout.lines().single { it.startsWith("| $check |") }

    @Test
    fun `passes with no open alerts`() {
        val result = compliance()

        assertEquals(result.stdout + result.stderr, 0, result.exitCode)
        assertTrue(result.stdout, result.row("Dependabot alerts (high+)").contains("pass"))
    }

    @Test
    fun `prints only the report table when everything passes`() {
        val result = compliance()

        assertEquals(
            """
            # Nightly compliance

            Commit: `abc1234`

            | Check | Result |
            | ----- | ------ |
            | Dependency graph submission | ✅ pass |
            | Gradle wrapper jar checksum | ✅ pass |
            | Dependabot alerts (high+) | ✅ pass |

            """.trimIndent(),
            result.stdout,
        )
    }

    @Test
    fun `lists every failure under Failures and in the job log`() {
        val result = compliance(alert("GHSA-aaaa-bbbb-cccc", "high"), graph = "failure")

        assertEquals(
            "- Dependency graph submission: `DEPENDENCY_GRAPH_RESULT` is `failure`\n" +
                "- GHSA-aaaa-bbbb-cccc (high) com.example:lib: Bad thing in com.example:lib " +
                "https://github.com/o/r/security/dependabot/1\n",
            result.stdout.substringAfter("\n\n## Failures\n\n"),
        )
        listOf("DEPENDENCY_GRAPH_RESULT is 'failure'", "Open high/critical Dependabot alerts:", "GHSA-aaaa-bbbb-cccc")
            .forEach { assertTrue(result.stderr, result.stderr.contains(it)) }
    }

    @Test
    fun `reads every page of the alerts API and fails on a high alert past the first`() {
        val pages = "[${alert("GHSA-aaaa-bbbb-cccc", "low")}]\n[${alert("GHSA-dddd-eeee-ffff", "high")}]\n"

        val result = complianceFromApi(pages)

        assertEquals(1, result.exitCode)
        assertTrue(result.stdout, result.stdout.contains("- GHSA-dddd-eeee-ffff (high)"))
        assertEquals(
            listOf("api --paginate repos/owner/minimalui/dependabot/alerts?state=open&per_page=100"),
            ghCalls.readLines(),
        )
    }

    @Test
    fun `passes when the alerts API prints no page at all`() {
        val result = complianceFromApi("")

        assertEquals(result.stdout + result.stderr, 0, result.exitCode)
    }

    // A call that fails before GitHub answers prints nothing, and jq -s reads that as no alerts: only gh's exit status
    // tells the two apart.
    @Test
    fun `fails the alerts check when the alerts API call fails`() {
        val result = complianceFromApi("", apiExit = 1)

        assertEquals(1, result.exitCode)
        assertTrue(result.stdout, result.row("Dependabot alerts (high+)").contains("fail"))
        assertTrue(result.stdout, result.stdout.contains("- Dependabot alerts: could not read the alerts API"))
    }

    @Test
    fun `asks gh for the repository when GITHUB_REPOSITORY is not set`() {
        val result = complianceFromApi("[]", repository = "")

        assertEquals(result.stdout + result.stderr, 0, result.exitCode)
        assertEquals(
            listOf(
                "repo view --json nameWithOwner --jq .nameWithOwner",
                "api --paginate repos/owner/from-gh/dependabot/alerts?state=open&per_page=100",
            ),
            ghCalls.readLines(),
        )
    }

    @Test
    fun `passes on a high alert that is no longer open`() {
        val result = compliance(alert("GHSA-aaaa-bbbb-cccc", "high", state = "fixed"))

        assertEquals(result.stdout + result.stderr, 0, result.exitCode)
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
        assertTrue(result.stdout, result.stdout.contains("- Dependabot alerts: could not read the alerts API"))
        assertTrue(result.stderr, result.stderr.contains("Could not read Dependabot alerts"))
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
