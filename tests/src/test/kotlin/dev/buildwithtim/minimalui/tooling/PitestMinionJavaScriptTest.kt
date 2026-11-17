package dev.buildwithtim.minimalui.tooling

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class PitestMinionJavaScriptTest {
    private val dir: File = Files.createTempDirectory("pitest-minion-java").toFile()
    private val base = dir.resolve("minions")
    private val calls = dir.resolve("java-args.txt")
    private val javaPid = dir.resolve("java-pid")

    /** Fake `java`: writes its arguments one per line, and its process ID, and exits with FAKE_EXIT. */
    private val java =
        dir.resolve("java").apply {
            writeText(
                """
                #!/usr/bin/env bash
                printf '%s\n' "${'$'}@" > "${calls.path}"
                echo "${'$'}${'$'}" > "${javaPid.path}"
                exit "${'$'}{FAKE_EXIT:-0}"

                """.trimIndent(),
            )
            setExecutable(true)
        }

    private val env = mapOf("PITEST_MINION_JAVA" to java.path, "PITEST_MINION_TMP" to base.path)

    @After
    fun cleanUp() {
        dir.deleteRecursively()
    }

    private fun minion(vararg args: String) = runScript("scripts/pitest-minion-java.sh", *args, env = env)

    /** A temp directory as an earlier minion with process ID [pid] left it: Robolectric's runtime still inside. */
    private fun leftBy(pid: Long): File =
        base.resolve("minion.$pid").apply {
            resolve("robolectric-nativeruntime1").mkdirs()
            resolve("robolectric-nativeruntime1/libandroid_runtime.so").writeText("so")
        }

    @Test
    fun `runs java with a temp directory of its own and passes its arguments and exit status on`() {
        val result =
            runScript(
                "scripts/pitest-minion-java.sh",
                "-Xmx2g",
                "Minion",
                "4711",
                env =
                    env + ("FAKE_EXIT" to "11"),
            )

        assertEquals(result.stderr, 11, result.exitCode)
        val args = calls.readLines()
        assertEquals(listOf("-Xmx2g", "Minion", "4711"), args.drop(1))
        val tmp = File(args.first().removePrefix("-Djava.io.tmpdir="))
        assertTrue("$tmp is not a directory", tmp.isDirectory)
        assertEquals(base.canonicalFile, tmp.canonicalFile.parentFile)
    }

    // The next minion finds a directory to delete by the process ID in its name, so the name must be the ID of the
    // process that runs java: the script's own, which exec hands to the JVM (#442).
    @Test
    fun `names its temp directory after the process that runs java`() {
        val result = minion("Minion")

        assertEquals(result.stderr, 0, result.exitCode)
        val tmp = File(calls.readLines().first().removePrefix("-Djava.io.tmpdir="))
        assertEquals("minion." + javaPid.readText().trim(), tmp.name)
    }

    @Test
    fun `exits 2 without running java when the temp directory cannot be made`() {
        base.writeText("a file, not a directory")

        val result = minion("Minion")

        assertEquals(result.stderr, 2, result.exitCode)
        assertFalse("java ran", calls.exists())
    }

    // PIT kills a minion it gives up on, so no minion cleans up after itself: the next one does.
    @Test
    fun `deletes the temp directories of minions that have exited and keeps those of running ones`() {
        val exited = ProcessBuilder("true").start().apply { waitFor() }.pid()
        val running = ProcessHandle.current().pid()
        val gone = leftBy(exited)
        val live = leftBy(running)

        val result = minion("Minion")

        assertEquals(result.stderr, 0, result.exitCode)
        assertFalse("$gone was kept", gone.exists())
        assertTrue("$live was deleted", live.resolve("robolectric-nativeruntime1/libandroid_runtime.so").exists())
    }

    @Test
    fun `refuses to run without the java or the temp directory`() {
        for (missing in env.keys) {
            val result = runScript("scripts/pitest-minion-java.sh", "Minion", env = env + (missing to ""))

            assertEquals(missing, 2, result.exitCode)
            assertTrue(result.stderr, result.stderr.contains(missing))
            assertFalse(missing, calls.exists())
        }
    }
}
