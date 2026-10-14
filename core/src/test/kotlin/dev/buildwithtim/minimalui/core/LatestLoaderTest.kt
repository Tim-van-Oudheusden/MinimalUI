package dev.buildwithtim.minimalui.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.Executor

class LatestLoaderTest {
    /** Holds tasks until the test runs them, standing in for a worker thread or the main looper. */
    private class QueuedExecutor : Executor {
        private val tasks = ArrayDeque<Runnable>()

        override fun execute(command: Runnable) {
            tasks.addLast(command)
        }

        fun runNext() = tasks.removeFirst().run()

        fun runAll() {
            while (tasks.isNotEmpty()) runNext()
        }
    }

    private val background = QueuedExecutor()
    private val main = QueuedExecutor()
    private val published = mutableListOf<String>()

    @Test
    fun `loads on the background executor and publishes on the main executor`() {
        var loads = 0
        val loader = LatestLoader(background, main, load = { "apps ${++loads}" }, publish = { published += it })

        loader.refresh()
        assertEquals("nothing loads on the caller's thread", 0, loads)

        background.runAll()
        assertEquals("the previous value stays until the main thread takes the new one", emptyList<String>(), published)

        main.runAll()
        assertEquals(listOf("apps 1"), published)
    }

    @Test
    fun `skips a queued load that a newer refresh superseded`() {
        // An app update broadcasts removed, added and replaced back to back: one load is enough.
        var loads = 0
        val loader = LatestLoader(background, main, load = { "apps ${++loads}" }, publish = { published += it })

        loader.refresh()
        loader.refresh()
        loader.refresh()
        background.runAll()
        main.runAll()

        assertEquals(1, loads)
        assertEquals(listOf("apps 1"), published)
    }

    @Test
    fun `drops a result that a newer refresh superseded while it was loading`() {
        var installed = "before install"
        val loader = LatestLoader(background, main, load = { installed }, publish = { published += it })

        loader.refresh()
        background.runNext() // Read the old app list; its result now waits for the main thread.
        installed = "after install"
        loader.refresh()
        background.runAll()
        main.runAll()

        assertEquals(listOf("after install"), published)
    }
}
