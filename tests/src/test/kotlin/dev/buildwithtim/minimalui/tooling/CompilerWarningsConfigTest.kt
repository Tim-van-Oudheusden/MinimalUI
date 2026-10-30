package dev.buildwithtim.minimalui.tooling

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Compiler warnings fail the build (#133), extra warnings included. The root `build.gradle.kts` sets this for every
 * module's Kotlin compilations (#276), so a module added to `settings.gradle.kts` gets it without copying a block. A
 * root script that drops a flag, or a module that turns one back off, would compile with warnings allowed and nothing
 * else would notice, so this test does.
 */
class CompilerWarningsConfigTest {
    private val flags = listOf("allWarningsAsErrors", "extraWarnings")

    /** The root `subprojects {}` blocks that configure every Kotlin compilation task. */
    private fun compilationBlocks(): List<String> =
        Repo.subprojectsBlocks().filter {
            Regex("""tasks\s*\.\s*withType<(?:[\w.]+\.)?KotlinCompilationTask<\*>>\(\)""").containsMatchIn(it)
        }

    private fun turnsOn(
        block: String,
        flag: String,
    ): Boolean = Regex("""\b$flag\s*(?:\.\s*set\(\s*true\s*\)|=\s*true\b)""").containsMatchIn(block)

    @Test
    fun `every module's Kotlin compilations treat warnings as errors, set once in the root build script`() {
        val blocks = compilationBlocks()

        for (flag in flags) {
            assertTrue(
                "build.gradle.kts: no subprojects {} block turns on $flag for KotlinCompilationTask<*>",
                blocks.any { turnsOn(it, flag) },
            )
        }
    }

    // A module's own setting wins over the root's convention, so it could turn the policy off for that module; and a
    // module that only repeats it is a second copy to keep in step.
    @Test
    fun `no module sets its own compiler warning flags`() {
        val modules = Repo.modules()
        assertTrue("modules in settings.gradle.kts: $modules", "app" in modules && "tests" in modules)

        val overriding =
            modules.filter { module ->
                Repo.read("$module/build.gradle.kts").lines().any { line ->
                    val code = line.substringBefore("//")
                    (flags + "suppressWarnings").any { Regex("""\b$it\b""").containsMatchIn(code) }
                }
            }

        assertEquals(emptyList<String>(), overriding)
    }
}
