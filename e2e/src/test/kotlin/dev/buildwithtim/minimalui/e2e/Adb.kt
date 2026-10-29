package dev.buildwithtim.minimalui.e2e

import java.io.File
import java.util.concurrent.TimeUnit

/** adb, run against the device it picks: the only one attached, or the one `ANDROID_SERIAL` names. */
object Adb {
    // Far above any single command here; an install on a cold emulator is the slowest.
    private const val TIMEOUT_SECONDS = 120L

    // The SDK's adb when ANDROID_HOME names one, else the one on PATH.
    private val adb: String =
        System
            .getenv("ANDROID_HOME")
            ?.let { File(it, "platform-tools/adb") }
            ?.takeIf { it.canExecute() }
            ?.path ?: "adb"

    /** What adb printed for [args]; fails the test with that output when adb exits non-zero. */
    fun run(vararg args: String): String {
        val (exit, output) = exec(args)
        if (exit != 0) throw AssertionError("adb ${args.joinToString(" ")} exited $exit: $output")
        return output
    }

    /** What `adb shell` printed for [command], failing the test like [run]. */
    fun shell(vararg command: String): String = run("shell", *command)

    /** What `adb shell` printed for [command], or null when it exited non-zero. */
    fun tryShell(vararg command: String): String? {
        val (exit, output) = exec(arrayOf("shell", *command))
        return output.takeIf { exit == 0 }
    }

    // The exit code and everything adb printed; a hung adb fails the test instead of the task timeout.
    private fun exec(args: Array<out String>): Pair<Int, String> {
        val output = File.createTempFile("adb", ".out")
        try {
            val process =
                ProcessBuilder(adb, *args)
                    .redirectErrorStream(true)
                    .redirectOutput(output)
                    .start()
            if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                throw AssertionError("adb ${args.joinToString(" ")} took over $TIMEOUT_SECONDS s: ${output.readText()}")
            }
            return process.exitValue() to output.readText()
        } finally {
            output.delete()
        }
    }
}
