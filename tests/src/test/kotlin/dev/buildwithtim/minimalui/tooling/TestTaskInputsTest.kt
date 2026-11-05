package dev.buildwithtim.minimalui.tooling

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The build cache is on, so `:tests:test` replays its last result until a declared input changes. A file a test reads
 * but `tests/build.gradle.kts` does not declare can change with no test running: a stale doc claim lands green on its
 * pull request and on `main`, then fails on the next unrelated pull request that does rerun the tests (#337).
 *
 * Gradle alone knows the task's inputs, so this reads them from the build script: a path counts as declared when a
 * string in the `tasks.test` block is that path or a directory above it. Modules come from `settings.gradle.kts`, as
 * the build script's own loop does. The reads are the paths passed to `Repo.read` or `Repo.file`, plus every path
 * `DocsClaimsTest` names, because it passes some of them through a map or a helper.
 */
class TestTaskInputsTest {
    private val sources =
        Repo
            .file("tests/src/test/kotlin")
            .walkTopDown()
            .filter { it.extension == "kt" }
            .toList()

    private val declared: List<String> =
        Regex(""""([^"$\s]+)"""")
            .findAll(Repo.read("tests/build.gradle.kts").substringAfter("tasks.test {"))
            .map { it.groupValues[1] }
            .toList() + Repo.modules().map { "$it/build.gradle.kts" }

    private val reads: Set<String> =
        sources
            .flatMap { source ->
                val literal = if (source.name == "DocsClaimsTest.kt") """"([^"$\s]+)"""" else REPO_CALL
                Regex(literal).findAll(source.readText()).map { it.groupValues[1] }
            }.filter { Repo.file(it).isFile }
            .toSet()

    @Test
    fun `every repository file a test reads is an input of the test task`() {
        assertTrue("paths passed to Repo.read in tests/src/test/kotlin", reads.isNotEmpty())

        val undeclared = reads.filterNot { read -> declared.any { read == it || read.startsWith("$it/") } }

        assertEquals("read by a test, not an input in tests/build.gradle.kts", emptyList<String>(), undeclared)
    }

    private companion object {
        const val REPO_CALL = """Repo\s*\.(?:read|file)\(\s*"([^"$]+)""""
    }
}
