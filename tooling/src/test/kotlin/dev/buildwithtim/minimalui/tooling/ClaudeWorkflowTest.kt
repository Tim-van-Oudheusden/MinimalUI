package dev.buildwithtim.minimalui.tooling

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClaudeWorkflowTest {

    private val claudeAction = Regex("""uses: anthropics/claude-code-action@""")
    private val source = Repo.read(".github/workflows/claude.yml")
    private val job = Yaml.parse(source).asMap().jobs().getValue("claude")

    @Test
    fun `is the only workflow that runs Claude Code, so one mention starts one run`() {
        val runners = checkNotNull(Repo.file(".github/workflows").listFiles())
            .filter { claudeAction.containsMatchIn(it.readText()) }
            .map { it.name }

        assertEquals(listOf("claude.yml"), runners)
    }

    @Test
    fun `pins the action to a commit and only answers trusted mentions`() {
        val jobIf = job.getValue("if").toString()
        val trusted = Regex("""contains\(fromJSON\('\["OWNER","MEMBER","COLLABORATOR"\]'\), github\.event\.""")
        val mention = Regex("""contains\(github\.event\.(comment|review)\.body, '@claude'\)""")

        assertTrue(Regex("""uses: anthropics/claude-code-action@[0-9a-f]{40}\b""").containsMatchIn(source))
        assertTrue(source.contains("label_trigger: ai-fix-requested"))
        assertEquals(3, mention.findAll(jobIf).count())
        assertEquals(3, trusted.findAll(jobIf).count())
        assertTrue(!source.contains("pull_request_target"))
    }

    // claude-code-action checks out the PR head (fork PRs via refs/pull/N/head) and Claude may run the Gradle build
    // on it while the job holds a write token.
    @Test
    fun `refuses fork pull requests before anything else runs`() {
        val first = job.steps().first()
        val run = first["run"].toString()

        assertEquals("Refuse fork pull requests", first["name"])
        assertEquals("github.event.issue.pull_request || github.event.pull_request", first["if"])
        assertTrue(run.contains(""".head.repo.full_name // """""))
        assertTrue(run.contains("""[ "${'$'}head_repo" != "${'$'}REPO" ]"""))
        assertTrue(run.contains("exit 1"))
    }

    @Test
    fun `lets Claude run only the quality gates`() {
        val args = job.steps().single { it["name"] == "Run Claude Code" }["with"].asMap()["claude_args"].toString()
        val allowed = Regex("""Bash\(([^)]*)\)""").findAll(args).map { it.groupValues[1] }.toList()

        assertTrue("allowed tools: $allowed", allowed.isNotEmpty())
        assertTrue("allowed tools: $allowed", allowed.all { it.startsWith("./gradlew ") && !it.contains("wrapper") })
    }
}
