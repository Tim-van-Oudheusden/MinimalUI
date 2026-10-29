package dev.buildwithtim.minimalui.tooling

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RunScriptTest {
    @Test
    fun `returns the result of a script that exits before reading its stdin`() {
        // More than a pipe buffer, so the write is still blocked when the script exits.
        val result =
            runScript(
                "scripts/tier-classify.sh",
                env = mapOf("RISK_TIER_POLICY" to Repo.file("absent.policy").path),
                stdin = "docs/a.md\n".repeat(100_000),
            )

        assertEquals(2, result.exitCode)
        assertTrue(result.stderr, result.stderr.contains("cannot read policy file"))
    }
}
