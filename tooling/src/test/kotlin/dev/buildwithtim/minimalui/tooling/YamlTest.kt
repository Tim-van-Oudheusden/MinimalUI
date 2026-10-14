package dev.buildwithtim.minimalui.tooling

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** The invariant tests are only as good as this reader, so it is pinned on the shapes workflows use. */
class YamlTest {
    @Test
    fun `reads nested mappings, sequences of mappings and typed scalars`() {
        val yaml =
            """
            on:
              push:
                branches: [main, 'release/*']
            jobs:
              build:
                name: "Build, lint, test" # trailing comment
                timeout-minutes: 30
                steps:
                  - uses: actions/checkout@abc # v7
                    with:
                      fetch-depth: 1
                  - name: Gate
                    if: always()
                    continue-on-error: false
            """.trimIndent()

        val expected =
            mapOf(
                "on" to mapOf("push" to mapOf("branches" to listOf("main", "release/*"))),
                "jobs" to
                    mapOf(
                        "build" to
                            mapOf(
                                "name" to "Build, lint, test",
                                "timeout-minutes" to 30,
                                "steps" to
                                    listOf(
                                        mapOf("uses" to "actions/checkout@abc", "with" to mapOf("fetch-depth" to 1)),
                                        mapOf("name" to "Gate", "if" to "always()", "continue-on-error" to false),
                                    ),
                            ),
                    ),
            )
        assertEquals(expected, Yaml.parse(yaml))
    }

    @Test
    fun `keeps literal block scalars verbatim and folds folded ones`() {
        val yaml =
            """
            run: |
              set -e
              # not a YAML comment
              echo "a: b"

            if: >-
              github.event_name == 'issues'
              && always()
            keep: |-
              no trailing newline
            """.trimIndent()

        val expected =
            mapOf(
                "run" to "set -e\n# not a YAML comment\necho \"a: b\"\n",
                "if" to "github.event_name == 'issues' && always()",
                "keep" to "no trailing newline",
            )
        assertEquals(expected, Yaml.parse(yaml))
    }

    @Test
    fun `keeps line breaks around more-indented lines of a folded scalar`() {
        val yaml =
            """
            if: >-
              (event == 'issues'
                && labeled)
              || mentioned
            """.trimIndent()

        assertEquals(mapOf("if" to "(event == 'issues'\n  && labeled)\n|| mentioned"), Yaml.parse(yaml))
    }

    @Test
    fun `reads a sequence at its key's indentation and quoted keys`() {
        val yaml =
            """
            ignore:
            - dependency-name: typescript
              versions: [">=6.1.0"]
            'on': push
            """.trimIndent()

        val expected =
            mapOf(
                "ignore" to listOf(mapOf("dependency-name" to "typescript", "versions" to listOf(">=6.1.0"))),
                "on" to "push",
            )
        assertEquals(expected, Yaml.parse(yaml))
    }

    @Test
    fun `rejects constructs it cannot read instead of misreading them`() {
        listOf(
            "a: &anchor 1",
            "a: {b: 1}",
            "a: first line\n  continued plain scalar",
            "a: 1\na: 2",
        ).forEach { yaml ->
            assertThrows(yaml, IllegalArgumentException::class.java) { Yaml.parse(yaml) }
        }
    }
}
