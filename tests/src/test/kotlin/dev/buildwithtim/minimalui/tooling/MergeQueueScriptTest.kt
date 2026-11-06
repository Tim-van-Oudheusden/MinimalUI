package dev.buildwithtim.minimalui.tooling

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class MergeQueueScriptTest {
    private val dir: File = Files.createTempDirectory("merge-queue").toFile()
    private val origin = dir.resolve("origin.git")
    private val seed = dir.resolve("seed")
    private val clone = dir.resolve("clone")
    private val pulls = dir.resolve("pulls.json")
    private val calls = dir.resolve("gh-calls.txt")
    private val githubOutput = dir.resolve("github-output")
    private val sleeps = dir.resolve("sleeps.txt")

    /**
     * Fake `gh` first on PATH. `pr list` and `pr view <number>` answer from the pull requests in [pulls], filtered
     * through `--jq` the way gh does; every other call is logged to [calls], its arguments tab-separated on one line.
     * A `fail-pr-list` file in [dir] makes `pr list` fail, `fail-merge-state` makes `pr view` fail when it asks for
     * the merge state (and only then, so the other reads still pass); `fail-<workflow>` makes
     * `workflow run <workflow>` fail every time, and `fail-once-<workflow>` once (the file is removed). A failing
     * call is logged first. The fake `sleep` returns at once, so retries cost no time.
     */
    private val bin =
        dir.resolve("bin").apply {
            mkdirs()
            resolve("gh").apply {
                writeText(
                    """
                    #!/usr/bin/env bash
                    set -euo pipefail
                    if [ "${'$'}1" = pr ] && { [ "${'$'}2" = list ] || [ "${'$'}2" = view ]; }; then
                      sub="${'$'}2"
                      shift 2
                      number=""
                      filter=.
                      json=""
                      while [ ${'$'}# -gt 0 ]; do
                        case "${'$'}1" in
                          --jq) filter="${'$'}2"; shift 2 ;;
                          --json) json="${'$'}2"; shift 2 ;;
                          --*) shift 2 ;;
                          *) number="${'$'}1"; shift ;;
                        esac
                      done
                      if [ "${'$'}sub" = list ] && [ -e "${dir.path}/fail-pr-list" ]; then
                        echo "gh: HTTP 500" >&2
                        exit 1
                      fi
                      if [ "${'$'}sub" = view ] && [ "${'$'}json" = mergeStateStatus ] &&
                        [ -e "${dir.path}/fail-merge-state" ]; then
                        echo "gh: HTTP 500" >&2
                        exit 1
                      fi
                      if [ "${'$'}sub" = view ]; then
                        jq -r --argjson n "${'$'}number" ".[] | select(.number == \${'$'}n) | ${'$'}filter" "${pulls.path}"
                      else
                        jq -r "${'$'}filter" "${pulls.path}"
                      fi
                      exit 0
                    fi
                    (IFS=${'$'}'\t'; printf '%s\n' "${'$'}*") >> "${calls.path}"
                    if [ "${'$'}1" = workflow ] && [ "${'$'}2" = run ]; then
                      if [ -e "${dir.path}/fail-${'$'}3" ]; then
                        exit 1
                      fi
                      if [ -e "${dir.path}/fail-once-${'$'}3" ]; then
                        rm "${dir.path}/fail-once-${'$'}3"
                        exit 1
                      fi
                    fi

                    """.trimIndent(),
                )
                setExecutable(true)
            }
            resolve("sleep").apply {
                writeText("#!/usr/bin/env bash\nexit 0\n")
                setExecutable(true)
            }
        }

    private val gitEnv =
        mapOf(
            "GIT_CONFIG_GLOBAL" to "/dev/null",
            "GIT_CONFIG_NOSYSTEM" to "1",
            "GIT_AUTHOR_NAME" to "Test",
            "GIT_AUTHOR_EMAIL" to "test@example.com",
            "GIT_COMMITTER_NAME" to "Test",
            "GIT_COMMITTER_EMAIL" to "test@example.com",
        )

    private val runUrl = "https://github.com/owner/minimalui/actions/runs/42"

    @After
    fun cleanUp() {
        dir.deleteRecursively()
    }

    private data class Pull(
        val number: Int,
        val files: Map<String, String> = mapOf("docs/pr-$number.md" to "text\n"),
        val isDraft: Boolean = false,
        val isCrossRepository: Boolean = false,
        val labels: List<String> = emptyList(),
        val mergeStateStatus: String = "CLEAN",
        /** The head `gh pr list` reports, when a push since the listing moved the real one. */
        val listedHead: String? = null,
    )

    private fun git(
        workDir: File,
        vararg args: String,
    ): String {
        val result = runProcess(listOf("git") + args, workDir, gitEnv, mergeStderr = true)
        check(result.exitCode == 0) { "git ${args.joinToString(" ")} failed: ${result.stdout}" }
        return result.stdout.trim()
    }

    private fun commit(
        files: Map<String, String>,
        message: String,
    ): String {
        files.forEach { (path, text) ->
            seed.resolve(path).apply {
                parentFile.mkdirs()
                writeText(text)
            }
        }
        git(seed, "add", "--all")
        git(seed, "commit", "--quiet", "-m", message)
        return git(seed, "rev-parse", "HEAD")
    }

    /**
     * An origin whose main holds [mainFiles] and whose `refs/pull/<n>/head` carries each pull request's files on top
     * of main, like GitHub's pull refs, cloned the way the workflow checks main out. Returns each pull request's head.
     */
    private fun setUp(
        mainFiles: Map<String, String>,
        vararg pullRequests: Pull,
    ): Map<Int, String> {
        git(dir, "init", "--quiet", "--bare", "--initial-branch=main", origin.path)
        git(dir, "init", "--quiet", "--initial-branch=main", seed.path)
        commit(mainFiles, "main")
        git(seed, "push", "--quiet", origin.path, "main")
        val heads =
            pullRequests.associate { pull ->
                git(seed, "switch", "--quiet", "--create", "pr-${pull.number}", "main")
                val head = commit(pull.files, "pr ${pull.number}")
                git(seed, "push", "--quiet", origin.path, "HEAD:refs/pull/${pull.number}/head")
                pull.number to head
            }
        git(seed, "switch", "--quiet", "main")
        git(dir, "clone", "--quiet", origin.path, clone.path)
        pulls.writeText(
            pullRequests.joinToString(",", "[", "]") { pull ->
                """{"number": ${pull.number}, "headRefOid": "${pull.listedHead ?: heads.getValue(pull.number)}", """ +
                    """"isDraft": ${pull.isDraft}, "isCrossRepository": ${pull.isCrossRepository}, """ +
                    """"labels": ${pull.labels.joinToString(",", "[", "]") { """{"name": "$it"}""" }}, """ +
                    """"mergeStateStatus": "${pull.mergeStateStatus}"}"""
            },
        )
        return heads
    }

    private fun mergeQueue(
        vararg args: String,
        env: Map<String, String> = emptyMap(),
    ): ScriptResult {
        githubOutput.writeText("")
        return runScript(
            "scripts/merge-queue.sh",
            *args,
            env =
                gitEnv +
                    mapOf(
                        "PATH" to "${bin.path}:${System.getenv("PATH")}",
                        "GITHUB_OUTPUT" to githubOutput.path,
                    ) + env,
            workDir = clone,
        )
    }

    /** Fake `sleep` first on PATH: logs its delay and, given [settlesTo], lets GitHub finish computing merge states. */
    private fun fakeSleep(settlesTo: String? = null) {
        val settle = settlesTo?.let { "sed -i 's/\"UNKNOWN\"/\"$it\"/' \"${pulls.path}\"" }.orEmpty()
        bin.resolve("sleep").apply {
            writeText("#!/usr/bin/env bash\necho \"\$1\" >> \"${sleeps.path}\"\n$settle\n")
            setExecutable(true)
        }
    }

    private fun output(): Map<String, String> =
        githubOutput
            .readLines()
            .filter { it.isNotEmpty() }
            .associate { it.substringBefore("=") to it.substringAfter("=") }

    private fun calls(): List<List<String>> =
        if (calls.exists()) calls.readLines().map { it.split("\t") } else emptyList()

    private fun cloneHead() = git(clone, "rev-parse", "HEAD")

    private val nextRun = listOf("workflow", "run", "merge-queue.yml", "--ref", "main")

    private val pushWorkflows = listOf("ci.yml", "coverage-gate.yml", "e2e.yml", "quality-report.yml")

    private fun workflowRuns(): List<String> = calls().filter { it.take(2) == listOf("workflow", "run") }.map { it[2] }

    // sentinel-alert: Hive flagged a sensitive change, and a maintainer removes the label once they have reviewed it.
    @Test
    fun `next picks the oldest same-repo, ready, unheld pull request whose required checks pass`() {
        val heads =
            setUp(
                mapOf("README.md" to "launcher\n"),
                Pull(9),
                Pull(3, isDraft = true),
                Pull(4, isCrossRepository = true),
                Pull(2, labels = listOf("sentinel-alert")),
                Pull(5, labels = listOf("hold")),
                Pull(6, mergeStateStatus = "BLOCKED"),
                Pull(7, labels = listOf("stage/100-merge-queue")),
            )

        val result = mergeQueue("next")

        assertEquals(result.stderr, 0, result.exitCode)
        assertEquals(mapOf("number" to "7", "head" to heads.getValue(7), "main" to cloneHead()), output())
        assertEquals(emptyList<List<String>>(), calls())
    }

    @Test
    fun `next skips a pull request pushed to since it was listed`() {
        setUp(mapOf("README.md" to "launcher\n"), Pull(4, listedHead = "0123456789abcdef0123456789abcdef01234567"))

        val result = mergeQueue("next")

        assertEquals(result.stderr, 0, result.exitCode)
        assertEquals(emptyMap<String, String>(), output())
    }

    @Test
    fun `next moves on from a pull request pushed to since it was listed`() {
        setUp(
            mapOf("README.md" to "launcher\n"),
            Pull(4, listedHead = "0123456789abcdef0123456789abcdef01234567"),
            Pull(5),
        )

        val result = mergeQueue("next")

        assertEquals(result.stderr, 0, result.exitCode)
        assertEquals("5", output()["number"])
    }

    @Test
    fun `next waits for GitHub to finish computing a merge state`() {
        setUp(mapOf("README.md" to "launcher\n"), Pull(4, mergeStateStatus = "UNKNOWN"))
        fakeSleep(settlesTo = "CLEAN")

        val result = mergeQueue("next")

        assertEquals(result.stderr, 0, result.exitCode)
        assertEquals("4", output()["number"])
        assertEquals(listOf("5"), sleeps.readLines())
    }

    @Test
    fun `next gives up on a merge state still unknown at the third look, without picking it`() {
        setUp(mapOf("README.md" to "launcher\n"), Pull(4, mergeStateStatus = "UNKNOWN"))
        fakeSleep()

        val result = mergeQueue("next")

        assertEquals(result.stderr, 0, result.exitCode)
        assertEquals(emptyMap<String, String>(), output())
        assertEquals(listOf("5", "5"), sleeps.readLines())
    }

    @Test
    fun `next leaves a high-risk pull request for the maintainer and picks the next one`() {
        val app = "core/src/main/kotlin/A.kt"
        val heads =
            setUp(
                mapOf("README.md" to "launcher\n"),
                Pull(2, files = mapOf(app to "class A\n", ".github/workflows/ci.yml" to "name: CI\n")),
                Pull(8, files = mapOf(app to "class A\n")),
            )

        val result = mergeQueue("next")

        assertEquals(result.stderr, 0, result.exitCode)
        assertEquals(mapOf("number" to "8", "head" to heads.getValue(8), "main" to cloneHead()), output())
        assertTrue(result.stdout, result.stdout.contains("#2 is high risk"))
    }

    // Moving a script out of scripts/ still changes CI; rename detection would list only the new, medium-risk path.
    @Test
    fun `next rates a file moved out of a high-risk path by its old path`() {
        setUp(mapOf("scripts/release.sh" to "#!/bin/sh\n"))
        git(seed, "switch", "--quiet", "--create", "pr-5", "main")
        git(seed, "mv", "scripts/release.sh", "release.sh")
        git(seed, "commit", "--quiet", "-m", "move")
        git(seed, "push", "--quiet", origin.path, "HEAD:refs/pull/5/head")
        pulls.writeText(
            """[{"number": 5, "headRefOid": "${git(seed, "rev-parse", "HEAD")}", "isDraft": false, """ +
                """"isCrossRepository": false, "labels": [], "mergeStateStatus": "CLEAN"}]""",
        )

        val result = mergeQueue("next")

        assertEquals(result.stderr, 0, result.exitCode)
        assertEquals(emptyMap<String, String>(), output())
    }

    // No rating is not a rating: a rule-less risk assessment on main must stop the queue, not merge what it can't rate.
    @Test
    fun `next fails and picks nothing when the risk assessment holds no rules`() {
        setUp(mapOf("README.md" to "launcher\n"), Pull(2, files = mapOf(".github/workflows/ci.yml" to "name: CI\n")))
        val policy = dir.resolve("risk-assessment.yml").apply { writeText("# only a comment\n") }

        val result = mergeQueue("next", env = mapOf("RISK_TIER_POLICY" to policy.path))

        assertEquals(result.stderr, 2, result.exitCode)
        assertEquals(emptyMap<String, String>(), output())
    }

    @Test
    fun `next picks nothing when no pull request is ready`() {
        setUp(mapOf("README.md" to "launcher\n"), Pull(4, mergeStateStatus = "BLOCKED"))

        val result = mergeQueue("next")

        assertEquals(result.stderr, 0, result.exitCode)
        assertEquals(emptyMap<String, String>(), output())
        assertEquals(emptyList<List<String>>(), calls())
    }

    // UNSTABLE means every required check passed and another check did not, so nothing else says why the pull request
    // waits. The queue still requires CLEAN: picking it would merge past a red CodeQL or risk tier check.
    @Test
    fun `next reports a pull request held back by a check that is not required, without picking it`() {
        setUp(mapOf("README.md" to "launcher\n"), Pull(4, mergeStateStatus = "UNSTABLE"))

        val result = mergeQueue("next")

        assertEquals(result.stderr, 0, result.exitCode)
        assertTrue(result.stdout, result.stdout.contains("::notice title=Merge queue::#4 is UNSTABLE"))
        assertEquals(emptyMap<String, String>(), output())
        assertEquals(emptyList<List<String>>(), calls())
    }

    // A merge made with GITHUB_TOKEN starts no push run, so the queue starts main's own runs: without them a failure
    // on main would go unnoticed (auto-issues.yml watches those runs).
    @Test
    fun `land merges the verified head, then starts every workflow that runs on pushes to main and the next run`() {
        val head = setUp(mapOf("README.md" to "launcher\n"), Pull(7)).getValue(7)
        val pushToMain =
            Repo
                .workflows()
                .filterValues { workflow ->
                    val push = (workflow.getValue("on") as? Map<*, *>)?.get("push")?.asMap()
                    push != null && "main" in push.getValue("branches").asList() && "paths" !in push
                }.keys

        val result = mergeQueue("land", "7", head, cloneHead())

        assertEquals(result.stderr, 0, result.exitCode)
        val calls = calls()
        assertEquals(listOf("pr", "merge", "7", "--merge", "--match-head-commit", head), calls.first())
        assertEquals(nextRun, calls.last())
        val started = calls.drop(1).dropLast(1)
        assertTrue(
            started.toString(),
            started.all {
                it.take(2) == listOf("workflow", "run") &&
                    it.drop(3) == listOf("--ref", "main")
            },
        )
        assertEquals(pushToMain, started.map { it[2] }.toSet())
        val notDispatchable =
            started.map { it[2] }.filterNot {
                "workflow_dispatch" in
                    Repo.workflows().getValue(it).triggers()
            }
        assertEquals(emptyList<String>(), notDispatchable)
    }

    @Test
    fun `land does not merge when main moved after verification, and queues a run against the new main`() {
        val head = setUp(mapOf("README.md" to "launcher\n"), Pull(7)).getValue(7)
        val verifiedMain = cloneHead()
        commit(mapOf("docs/other.md" to "other\n"), "other")
        git(seed, "push", "--quiet", origin.path, "main")

        val result = mergeQueue("land", "7", head, verifiedMain)

        assertEquals(result.stderr, 0, result.exitCode)
        assertEquals(listOf(nextRun), calls())
        assertTrue(result.stdout, result.stdout.contains("::notice title=Merge queue::main moved since #7"))
    }

    private fun assertLandSkips(pull: Pull) {
        val head = setUp(mapOf("README.md" to "launcher\n"), pull).getValue(pull.number)

        val result = mergeQueue("land", "${pull.number}", head, cloneHead())

        assertEquals(result.stderr, 0, result.exitCode)
        assertEquals(listOf(nextRun), calls())
        assertTrue(
            result.stdout,
            result.stdout.contains("::notice title=Merge queue::#${pull.number} stopped being ready"),
        )
    }

    @Test
    fun `land does not merge a pull request flagged sentinel-alert during verification`() {
        assertLandSkips(Pull(7, labels = listOf("sentinel-alert")))
    }

    @Test
    fun `land does not merge a pull request put on hold during verification`() {
        assertLandSkips(Pull(7, labels = listOf("hold")))
    }

    @Test
    fun `land does not merge a pull request turned back into a draft during verification`() {
        assertLandSkips(Pull(7, isDraft = true))
    }

    @Test
    fun `land does not merge a pull request whose required checks stopped passing during verification`() {
        assertLandSkips(Pull(7, mergeStateStatus = "BLOCKED"))
    }

    @Test
    fun `land starts a workflow again when its first dispatch fails, and does not fail the run`() {
        val head = setUp(mapOf("README.md" to "launcher\n"), Pull(7)).getValue(7)
        dir.resolve("fail-once-e2e.yml").writeText("")
        fakeSleep()

        val result = mergeQueue("land", "7", head, cloneHead())

        assertEquals(result.stderr, 0, result.exitCode)
        assertEquals(2, workflowRuns().count { it == "e2e.yml" })
        assertEquals(listOf("10"), sleeps.readLines())
        assertTrue(result.stdout, !result.stdout.contains("::error"))
        assertEquals(nextRun, calls().last())
    }

    // The merge has landed by then, so the other gates on main and the next run must still start.
    private fun assertLandReportsWorkflowThatWillNotStart(workflow: String) {
        val head = setUp(mapOf("README.md" to "launcher\n"), Pull(7)).getValue(7)
        dir.resolve("fail-$workflow").writeText("")
        fakeSleep()

        val result = mergeQueue("land", "7", head, cloneHead())

        assertEquals(result.stderr, 1, result.exitCode)
        val runs = workflowRuns()
        assertEquals(3, runs.count { it == workflow })
        // A wait between tries, and none after the last.
        assertEquals(listOf("10", "10"), sleeps.readLines())
        assertTrue(
            result.stdout,
            result.stdout.contains(
                "::error title=Merge queue::could not start $workflow; start it by hand: " +
                    "gh workflow run $workflow --ref main",
            ),
        )
        assertEquals((pushWorkflows + "merge-queue.yml").toSet(), runs.toSet())
        assertEquals(nextRun, calls().last())
    }

    @Test
    fun `land starts the rest and fails the run when ci cannot be started`() {
        assertLandReportsWorkflowThatWillNotStart("ci.yml")
    }

    @Test
    fun `land starts the rest and fails the run when the coverage gate cannot be started`() {
        assertLandReportsWorkflowThatWillNotStart("coverage-gate.yml")
    }

    @Test
    fun `land starts the rest and fails the run when e2e cannot be started`() {
        assertLandReportsWorkflowThatWillNotStart("e2e.yml")
    }

    @Test
    fun `land starts the rest and fails the run when the quality report cannot be started`() {
        assertLandReportsWorkflowThatWillNotStart("quality-report.yml")
    }

    @Test
    fun `land fails the run when the next queue run cannot be started after a merge`() {
        val head = setUp(mapOf("README.md" to "launcher\n"), Pull(7)).getValue(7)
        dir.resolve("fail-merge-queue.yml").writeText("")

        val result = mergeQueue("land", "7", head, cloneHead())

        assertEquals(result.stderr, 1, result.exitCode)
        assertEquals(3, workflowRuns().count { it == "merge-queue.yml" })
        assertEquals(pushWorkflows, workflowRuns().filter { it != "merge-queue.yml" })
        assertTrue(result.stdout, result.stdout.contains("::error title=Merge queue::could not start merge-queue.yml"))
    }

    // A failed listing must not pass for an empty queue.
    @Test
    fun `next fails and picks nothing when the pull request listing fails`() {
        setUp(mapOf("README.md" to "launcher\n"), Pull(4))
        dir.resolve("fail-pr-list").writeText("")

        val result = mergeQueue("next")

        assertTrue(result.stderr, result.exitCode != 0)
        assertEquals(emptyMap<String, String>(), output())
    }

    // A failed read of the merge state must not pass for a pull request that is merely not ready.
    @Test
    fun `next fails and picks nothing when the merge state cannot be read`() {
        setUp(mapOf("README.md" to "launcher\n"), Pull(4))
        dir.resolve("fail-merge-state").writeText("")

        val result = mergeQueue("next")

        assertTrue(result.stderr, result.exitCode != 0)
        assertEquals(emptyMap<String, String>(), output())
    }

    @Test
    fun `land fails without merging or queueing a run when the merge state cannot be read`() {
        val head = setUp(mapOf("README.md" to "launcher\n"), Pull(7)).getValue(7)
        dir.resolve("fail-merge-state").writeText("")

        val result = mergeQueue("land", "7", head, cloneHead())

        assertTrue(result.stderr, result.exitCode != 0)
        assertTrue(result.stdout, !result.stdout.contains("stopped being ready"))
        assertEquals(emptyList<List<String>>(), calls())
    }

    @Test
    fun `next picks nothing when no pull request is open`() {
        setUp(mapOf("README.md" to "launcher\n"))

        val result = mergeQueue("next")

        assertEquals(result.stderr, 0, result.exitCode)
        assertEquals(emptyMap<String, String>(), output())
        assertEquals(emptyList<List<String>>(), calls())
    }

    @Test
    fun `fails with a usage line on an unknown command`() {
        setUp(mapOf("README.md" to "launcher\n"))

        val result = mergeQueue("merge")

        assertEquals(2, result.exitCode)
        assertTrue(result.stderr, result.stderr.contains("usage:"))
    }

    @Test
    fun `reject holds the pull request, links the failed run and moves on to the next one`() {
        setUp(mapOf("README.md" to "launcher\n"), Pull(7))

        val result = mergeQueue("reject", "7", runUrl)

        assertEquals(result.stderr, 0, result.exitCode)
        val calls = calls()
        assertEquals(3, calls.size)
        assertEquals(listOf("pr", "edit", "7", "--add-label", "hold"), calls[0])
        assertEquals(listOf("pr", "comment", "7", "--body"), calls[1].take(4))
        assertTrue(calls[1][4], calls[1][4].contains(runUrl))
        assertEquals(nextRun, calls[2])
    }
}
