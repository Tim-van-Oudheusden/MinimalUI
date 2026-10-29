package dev.buildwithtim.minimalui.tooling

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Android Lint on every module (#134). `./gradlew check` runs each module's `lint` task, but a finding only fails the
 * gate when the module's `lint {}` block aborts on it and treats warnings as errors, so a module that drops either
 * setting, or never applies lint, fails here instead.
 */
class LintConfigTest {
    // AGP gives a com.android.test module (e2e) no lint task at all, so a lint {} block there would only look like a
    // gate; detekt, ktlint and the compiler's warnings-as-errors still cover its code.
    private val modules: List<String> =
        Regex("""include\(\s*"(:[^"]+)"\s*\)""")
            .findAll(Repo.read("settings.gradle.kts"))
            .map { it.groupValues[1].removePrefix(":").replace(':', '/') }
            .filterNot { "libs.plugins.android.test" in Repo.read("$it/build.gradle.kts") }
            .toList()

    /** The settings in [module]'s `lint { ... }` block, as `name = value` text with whitespace collapsed. */
    private fun lintSettings(module: String): Set<String> {
        val script = Repo.read("$module/build.gradle.kts")
        val start = Regex("""(?m)^\s*lint\s*\{""").find(script) ?: return emptySet()
        var depth = 0
        var end = start.range.last
        for (i in start.range.last until script.length) {
            when (script[i]) {
                '{' -> {
                    depth++
                }

                '}' -> {
                    if (--depth == 0) {
                        end = i
                        break
                    }
                }
            }
        }
        return script
            .substring(start.range.last + 1, end)
            .lines()
            .map { it.substringBefore("//").trim().replace(Regex("""\s+"""), " ") }
            .filter { it.isNotEmpty() }
            .toSet()
    }

    @Test
    fun `the module list is read from settings`() {
        assertTrue("modules in settings.gradle.kts: $modules", "app" in modules && "tests" in modules)
    }

    @Test
    fun `every module runs lint with warnings as errors and every check on`() {
        for (module in modules) {
            val settings = lintSettings(module)
            for (required in listOf("abortOnError = true", "warningsAsErrors = true", "checkAllWarnings = true")) {
                assertTrue("$module/build.gradle.kts lint {} lacks `$required`: $settings", required in settings)
            }
        }
    }

    @Test
    fun `every non-Android module applies the standalone lint plugin`() {
        for (module in modules) {
            val script = Repo.read("$module/build.gradle.kts")
            if ("libs.plugins.android.application" in script) continue
            assertTrue(
                "$module/build.gradle.kts must apply libs.plugins.android.lint",
                Regex("""alias\(\s*libs\.plugins\.android\.lint\s*\)""").containsMatchIn(script),
            )
        }
    }
}
