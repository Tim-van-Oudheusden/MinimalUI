package dev.buildwithtim.minimalui.tooling

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The test-task timeout (#216). A test stuck in a loop ran `:core:test` for 4h40m; the root `build.gradle.kts` now
 * fails every module's `Test` tasks after a limit. Nothing else notices if that block goes, moves into one module or
 * grows past CI's job timeout, so this does.
 */
class TestTimeoutConfigTest {
    private val modules: List<String> = Repo.modules()

    /** The minutes every module's `Test` tasks get from a root `subprojects {}` block, or null if none sets them. */
    private fun timeoutMinutes(): Int? =
        Repo
            .subprojectsBlocks()
            .filter { Regex("""tasks\s*\.\s*withType<Test>\(\)""").containsMatchIn(it) }
            .firstNotNullOfOrNull {
                Regex("""timeout\s*\.\s*set\(\s*(?:java\.time\.)?Duration\.ofMinutes\(\s*(\d+)\s*\)\s*\)""")
                    .find(it)
                    ?.groupValues
                    ?.get(1)
                    ?.toInt()
            }

    // Set once at the root, so tooling's script tests are bounded too and a new module gets it without anyone
    // remembering.
    @Test
    fun `every module's test tasks time out, set once in the root build script`() {
        val minutes = timeoutMinutes()

        assertNotNull("build.gradle.kts: no subprojects {} block sets a Duration.ofMinutes timeout on Test", minutes)
        assertTrue("a test-task timeout of $minutes minutes", minutes!! > 0)
    }

    // A module that sets its own timeout replaces the root's, and could lift it again for that module's tests.
    @Test
    fun `no module overrides the root timeout`() {
        val overriding =
            modules.filter { module ->
                Repo.read("$module/build.gradle.kts").lines().any {
                    Regex("""\btimeout\s*[.=]""").containsMatchIn(it.substringBefore("//"))
                }
            }

        assertEquals(emptyList<String>(), overriding)
    }

    // A hung test should fail its Gradle step, which names the task and lets `if: failure()` upload the reports. A
    // job that hits its own timeout-minutes first is cancelled instead: failure() is false, nothing names the test.
    @Test
    fun `the timeout fires before any workflow job running Gradle times out`() {
        val minutes = checkNotNull(timeoutMinutes()) { "no test-task timeout in build.gradle.kts" }
        val jobs =
            Repo.workflows().flatMap { (file, workflow) ->
                workflow
                    .jobs()
                    .filter { (_, job) -> job.gradleTasks().isNotEmpty() }
                    .map { (id, job) -> "$file $id" to job }
            }

        assertTrue("no workflow job runs ./gradlew", jobs.isNotEmpty())
        for ((name, job) in jobs) {
            val jobMinutes = job["timeout-minutes"]?.toString()?.toIntOrNull()
            assertNotNull("$name has no timeout-minutes", jobMinutes)
            assertTrue(
                "$name: timeout-minutes $jobMinutes must exceed the $minutes-minute test timeout",
                jobMinutes!! > minutes,
            )
        }
    }
}
