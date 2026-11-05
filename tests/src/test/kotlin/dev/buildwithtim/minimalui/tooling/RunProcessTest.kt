package dev.buildwithtim.minimalui.tooling

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.time.Duration.Companion.seconds

/**
 * A script under test that hangs must not outlive the test: an orphan keeps a core busy, and keeps writing into its
 * deleted output file, until someone kills it by hand (#357).
 */
class RunProcessTest {
    @get:Rule
    val temp = TemporaryFolder()

    /** A shell that writes its own PID and its child's to [pids] and then never exits. */
    private fun hang(pids: File) =
        listOf("bash", "-c", "sleep 300 & echo \"$$ $!\" > '${pids.path}'; while :; do :; done")

    private fun assertDead(pids: File) {
        val written =
            pids
                .readText()
                .trim()
                .split(" ")
                .map { it.toLong() }
        assertEquals("the shell and its child", 2, written.size)
        for (pid in written) {
            assertFalse("pid $pid still running", ProcessHandle.of(pid).map { it.isAlive }.orElse(false))
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
}
