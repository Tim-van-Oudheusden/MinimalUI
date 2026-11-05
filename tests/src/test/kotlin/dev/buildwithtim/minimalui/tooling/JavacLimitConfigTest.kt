package dev.buildwithtim.minimalui.tooling

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JavaCompile tasks run one at a time (#339). CodeQL's autobuild intermittently fails a JavaCompile task with no
 * compiler output; the root `build.gradle.kts` now makes every `JavaCompile` task use a shared build service limited to
 * one usage. Nothing else notices if the limit is dropped or the tasks stop using the service, so this does.
 */
class JavacLimitConfigTest {
    private val script: String =
        Repo
            .read("build.gradle.kts")
            .lines()
            .joinToString("\n") { it.substringBefore("//") }

    /** The name of the build service the root script limits to one parallel usage, or null if there is none. */
    private fun limitedService(): String? =
        Regex(
            """val\s+(\w+)\s*=\s*gradle\s*\.\s*sharedServices\s*\.\s*registerIfAbsent\([^{]*\{[^}]*""" +
                """maxParallelUsages\s*\.\s*set\(\s*1\s*\)""",
        ).find(script)?.groupValues?.get(1)

    @Test
    fun `the root build script registers a build service limited to one parallel usage`() {
        assertTrue(
            "build.gradle.kts: no shared service is registered with maxParallelUsages.set(1)",
            limitedService() != null,
        )
    }

    // Set once at the root, so a module added later gets it without anyone remembering.
    @Test
    fun `every module's JavaCompile tasks use that service, set once in the root build script`() {
        val service = checkNotNull(limitedService()) { "no limited build service in build.gradle.kts" }
        val uses =
            Repo.subprojectsBlocks().any {
                Regex("""tasks\s*\.\s*withType<JavaCompile>\(\)""").containsMatchIn(it) &&
                    Regex("""\busesService\(\s*$service\s*\)""").containsMatchIn(it)
            }

        assertTrue("build.gradle.kts: no subprojects {} block makes JavaCompile tasks use $service", uses)
    }
}
