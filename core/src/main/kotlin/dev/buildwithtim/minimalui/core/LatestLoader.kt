package dev.buildwithtim.minimalui.core

import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicLong

/**
 * Runs [load] on [background] and hands the result to [publish] on [main], so a slow load never blocks the caller.
 * Until then the previously published value stays current.
 * A refresh supersedes any earlier one: a superseded load is skipped if it has not started yet, and its result is
 * dropped if it has, so the published value never goes back to an older load.
 * Call [refresh] on the [main] thread: the newest-load check and [publish] then cannot interleave with it.
 */
class LatestLoader<T>(
    private val background: Executor,
    private val main: Executor,
    private val load: () -> T,
    private val publish: (T) -> Unit,
) {
    private val newest = AtomicLong()

    fun refresh() {
        val ticket = newest.incrementAndGet()
        background.execute {
            if (newest.get() != ticket) return@execute
            val result = load()
            main.execute { if (newest.get() == ticket) publish(result) }
        }
    }
}
