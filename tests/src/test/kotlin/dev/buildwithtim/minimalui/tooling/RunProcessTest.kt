package dev.buildwithtim.minimalui.tooling

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** A shell that appends its own PID and its child's to [pids] as one line and then never exits. */
private fun hang(pids: File) = listOf("bash", "-c", "sleep 300 & echo \"$$ $!\" >> '${pids.path}'; while :; do :; done")

/**
 * Run in a JVM of its own by [RunProcessTest]: runs [hang] again each time it ends, the way a test class goes on to its
 * next test once the script it waited for is killed, until the JVM is terminated.
 */
object RunProcessUntilTerminated {
    @JvmStatic
    fun main(args: Array<String>) {
        val pids = File(args.single())
        while (true) runProcess(hang(pids), timeout = 10.minutes)
    }
}

/**
 * A script under test that hangs must not outlive the test: an orphan keeps a core busy, and keeps writing into its
 * deleted output file, until someone kills it by hand (#357).
 */
class RunProcessTest {
    @get:Rule
    val temp = TemporaryFolder()

    /** The PIDs [hang] wrote to [pids], one line per run. */
    private fun runs(pids: File): List<List<Long>> =
        pids.readLines().filter { it.isNotBlank() }.map { line -> line.trim().split(" ").map { it.toLong() } }

    private fun isAlive(pid: Long) = ProcessHandle.of(pid).map { it.isAlive }.orElse(false)

    private fun assertDead(pids: File) {
        val runs = runs(pids)
        assertTrue("no run wrote its PIDs", runs.isNotEmpty())
        for (run in runs) {
            assertEquals("the shell and its child", 2, run.size)
            for (pid in run) assertFalse("pid $pid still running", isAlive(pid))
        }
    }

    @Test
    fun `a process that runs past its timeout fails the call and is killed with its children`() {
        val pids = temp.newFile("pids")

        assertThrows(IllegalStateException::class.java) { runProcess(hang(pids), timeout = 2.seconds) }

        assertDead(pids)
    }

    @Test
    fun `a process is killed with its children when the wait for it is interrupted`() {
        val pids = temp.newFile("pids")
        val waiter = Thread.currentThread()
        val interrupter =
            Thread {
                while (pids.length() == 0L) Thread.sleep(10)
                waiter.interrupt()
            }.apply { start() }

        assertThrows(InterruptedException::class.java) { runProcess(hang(pids), timeout = 30.seconds) }
        interrupter.join()

        assertDead(pids)
    }

    // An outside timeout (`timeout 150 java ... JUnitCore`, a CI step's limit) stops the test JVM with SIGTERM, which
    // never reaches runProcess's own cleanup. The script it was waiting for, and the next one the test went on to
    // start while the JVM was exiting, ran on at 100 % CPU (#389).
    @Test
    fun `processes are killed, and no more start, when the JVM is terminated`() {
        val pids = temp.newFile("pids")
        val jvm =
            ProcessBuilder(
                File(System.getProperty("java.home"), "bin/java").path,
                "-cp",
                System.getProperty("java.class.path"),
                "-Drepo.root=${Repo.root}",
                RunProcessUntilTerminated::class.java.name,
                pids.path,
            ).redirectErrorStream(true)
                .redirectOutput(temp.newFile("jvm.log"))
                .start()
        try {
            while (pids.length() == 0L) {
                assertTrue("the JVM exited before running anything", jvm.isAlive)
                Thread.sleep(10)
            }

            jvm.destroy()
            assertTrue("the JVM did not exit on SIGTERM", jvm.waitFor(30, TimeUnit.SECONDS))

            assertDead(pids)
        } finally {
            jvm.destroyForcibly()
            runs(pids).flatten().forEach { pid -> ProcessHandle.of(pid).ifPresent { it.destroyForcibly() } }
        }
    }
}
