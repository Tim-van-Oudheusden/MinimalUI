package dev.buildwithtim.minimalui.tooling

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Claude, Copilot and Cursor each load their guidance from their own path, so the same rules live in three files. Only
 * the header differs; from `## Issue tracking` to the end the files are one text. An edit that reached two of them and
 * missed the third failed nothing: #332 changed the merge rule in `CLAUDE.md` and `.github/copilot-instructions.md`,
 * and Cursor agents read the old rule until #333 (#351). This test compares the three bodies line by line.
 */
class AgentGuidanceParityTest {
    private val files =
        listOf(
            Repo.file("CLAUDE.md"),
            Repo.file(".github/copilot-instructions.md"),
            Repo.file(".cursor/rules/agent-guidance.mdc"),
        )

    @Test
    fun `the agent guidance files state the same rules from Issue tracking on`() {
        val bodies =
            files.associate { file ->
                val path = file.relativeTo(Repo.root).path
                val lines = file.readLines()
                val start = lines.indexOf(HEADING)
                assertTrue("a \"$HEADING\" line in $path", start >= 0)
                path to Body(start, lines.drop(start))
            }
        // The text most files share is the reference, so the message names the file that drifted, not its siblings.
        val (reference, sharedBy) =
            bodies.entries
                .groupBy({ it.value.lines }, { it.key })
                .maxBy { it.value.size }

        val drifted =
            bodies
                .filterValues { it.lines != reference }
                .map { (path, body) -> "$path:${body.firstDifference(reference)}" }

        assertEquals(
            "text from \"$HEADING\" on that differs from ${sharedBy.joinToString()}",
            emptyList<String>(),
            drifted,
        )
    }

    private class Body(
        val start: Int,
        val lines: List<String>,
    ) {
        /** The 1-based file line where these lines first leave [reference], with both versions of it. */
        fun firstDifference(reference: List<String>): String {
            val i = lines.indices.firstOrNull { it >= reference.size || lines[it] != reference[it] } ?: lines.size
            return "${start + i + 1}: \"${lines.getOrElse(i) { "<end of file>" }}\" " +
                "instead of \"${reference.getOrElse(i) { "<end of file>" }}\""
        }
    }

    private companion object {
        const val HEADING = "## Issue tracking"
    }
}
