package dev.buildwithtim.minimalui.tooling

import org.junit.Assert.assertTrue
import org.junit.Test

/** The `Build, lint, test` job in `.github/workflows/ci.yml`, a required check on `main`. */
class CiWorkflowTest {
    private val build =
        Repo
            .workflows()
            .getValue("ci.yml")
            .jobs()
            .getValue("build")

    // `check` is the quality gate: unit tests, Android Lint and spotlessCheck all hang off it, and so does every check
    // added later. A job that names the tasks one by one silently skips the next one.
    @Test
    fun `runs the check gate`() {
        val tasks = build.gradleTasks()

        assertTrue("./gradlew tasks: $tasks", "check" in tasks)
    }
}
