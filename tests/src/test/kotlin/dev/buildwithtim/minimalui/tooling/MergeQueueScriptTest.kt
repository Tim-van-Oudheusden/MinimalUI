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

    /**
     * Fake `gh` first on PATH. `pr list` and `pr view <number>` answer from the pull requests in [pulls], filtered
     * through `--jq` the way gh does; every other call is logged to [calls], its arguments tab-separated on one line.
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
                      while [ ${'$'}# -gt 0 ]; do
                        case "${'$'}1" in
                          --jq) filter="${'$'}2"; shift 2 ;;
                          --*) shift 2 ;;
                          *) number="${'$'}1"; shift ;;
                        esac
                      done
                      if [ "${'$'}sub" = view ]; then
                        jq -r --argjson n "${'$'}number" ".[] | select(.number == \${'$'}n) | ${'$'}filter" "${pulls.path}"
                      else
                        jq -r "${'$'}filter" "${pulls.path}"
                      fi
                      exit 0
                    fi
                    (IFS=${'$'}'\t'; printf '%s\n' "${'$'}*") >> "${calls.path}"

                    """.trimIndent(),
                )
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
        val process =
            ProcessBuilder(listOf("git") + args)
                .directory(workDir)
                .redirectErrorStream(true)
                .apply { environment().putAll(gitEnv) }
                .start()
        val output = process.inputStream.bufferedReader().readText()
        check(process.waitFor() == 0) { "git ${args.joinToString(" ")} failed: $output" }
        return output.trim()
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

    private fun mergeQueue(vararg args: String): ScriptResult {
        githubOutput.writeText("")
        return runScript(
            "scripts/merge-queue.sh",
            *args,
            env =
                gitEnv +
                    mapOf(
                        "PATH" to "${bin.path}:${System.getenv("PATH")}",
                        "GITHUB_OUTPUT" to githubOutput.path,
                    ),
            workDir = clone,
        )
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

    @Test
    fun `next picks nothing when no pull request is ready`() {
        setUp(mapOf("README.md" to "launcher\n"), Pull(4, mergeStateStatus = "BLOCKED"))

        val result = mergeQueue("next")

        assertEquals(result.stderr, 0, result.exitCode)
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
    }

    private fun assertLandSkips(pull: Pull) {
        val head = setUp(mapOf("README.md" to "launcher\n"), pull).getValue(pull.number)

        val result = mergeQueue("land", "${pull.number}", head, cloneHead())

        assertEquals(result.stderr, 0, result.exitCode)
        assertEquals(listOf(nextRun), calls())
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
