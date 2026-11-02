package dev.buildwithtim.minimalui.tooling

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The SDK levels the app builds against. CONTRIBUTING, `docs/testing.md`, `Dockerfile.sandbox` and `ci.yml` name
 * `app/build.gradle.kts` as the place `compileSdk` lives, but `e2e/build.gradle.kts` (#236) declares its own
 * `compileSdk`, `minSdk` and `targetSdk` too. A bump made only in the app would leave the end-to-end tests on the old
 * levels, so a module that declares a level different from the app's fails here (#296).
 */
class SdkLevelsTest {
    private val levels = listOf("compileSdk", "minSdk", "targetSdk")

    /** The values [module]'s build script assigns to each SDK level, by level; levels it never assigns are left out. */
    private fun declared(module: String): Map<String, List<String>> {
        val code =
            Repo
                .read("$module/build.gradle.kts")
                .lines()
                .joinToString("\n") { it.substringBefore("//") }
        return levels
            .associateWith { level ->
                Regex("""(?m)^\s*$level\s*=\s*(\S+)\s*$""")
                    .findAll(code)
                    .map { it.groupValues[1] }
                    .toList()
            }.filterValues { it.isNotEmpty() }
    }

    @Test
    fun `the app declares each SDK level once`() {
        val app = declared("app")

        assertEquals(levels.associateWith { 1 }, levels.associateWith { app[it].orEmpty().size })
    }

    @Test
    fun `every module that declares an SDK level declares the app's`() {
        val app = declared("app").mapValues { it.value.single() }
        val others =
            Repo
                .modules()
                .filterNot { it == "app" }
                .associateWith(::declared)
                .filterValues { it.isNotEmpty() }
        assertTrue("e2e declares no SDK level; modules declaring one: ${others.keys}", "e2e" in others)

        val differing =
            others.flatMap { (module, declaredLevels) ->
                declaredLevels.flatMap { (level, values) ->
                    values
                        .filter { it != app[level] }
                        .map { "$module/build.gradle.kts: $level = $it, app/build.gradle.kts has ${app[level]}" }
                }
            }

        assertEquals(emptyList<String>(), differing)
    }
}
