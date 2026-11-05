package dev.buildwithtim.minimalui.tooling

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `./gradlew check` runs detekt's analysis tasks (#356). The plain `detekt` task, the only one the plugin puts under
 * `check`, has no classpath, so it skips every rule that needs type analysis and still reports success. The root
 * `build.gradle.kts` makes `check` depend on `detektMain` and `detektTest` as well. Nothing else notices if that goes:
 * the build stays green and those rules stop running, so this does.
 *
 * Also guards `UnusedImport` (#369): detekt ships it off, and no other gate reports an unused import.
 */
class DetektAnalysisConfigTest {
    // Set once at the root, next to the plugin, so a module added later gets it without anyone remembering.
    @Test
    fun `check runs the analysis tasks in every module, set once in the root build script`() {
        val block =
            checkNotNull(Repo.subprojectsBlocks().firstOrNull { """apply(plugin = "dev.detekt")""" in it }) {
                "no subprojects {} block in build.gradle.kts applies detekt"
            }
        val checkDependsOn =
            Regex(""""check"[\s\S]*?\bdependsOn\(([^)]*)\)""")
                .find(block)
                ?.groupValues
                ?.get(1)
                .orEmpty()

        for (task in listOf("detektMain", "detektTest")) {
            assertTrue(
                "build.gradle.kts: `check` must depend on $task, not only on: '$checkDependsOn'",
                "\"$task\"" in checkDependsOn,
            )
        }
    }

    // Off in detekt's defaults, and neither ktlint nor the compiler flags an unused import: the override is the gate.
    @Test
    fun `detekt turns UnusedImport on in the style rule set`() {
        val style = Yaml.parse(Repo.read("config/detekt/detekt.yml")).asMap().getValue("style").asMap()
        val rule = style["UnusedImport"]?.asMap()

        assertEquals(
            "config/detekt/detekt.yml: style.UnusedImport must set `active: true`, got: $rule",
            true,
            rule?.get("active"),
        )
    }
}
