package dev.buildwithtim.minimalui.tooling

import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** The repository under test; `tests/build.gradle.kts` passes its root as the `repo.root` system property. */
object Repo {
    val root: File =
        File(
            checkNotNull(System.getProperty("repo.root")) { "repo.root is set by tests/build.gradle.kts" },
        )

    fun file(path: String): File = root.resolve(path)

    fun read(path: String): String = file(path).readText()

    /** Every module `settings.gradle.kts` includes, as its directory relative to the root (`app`, `core`, ...). */
    fun modules(): List<String> =
        Regex("""include\(\s*"(:[^"]+)"\s*\)""")
            .findAll(read("settings.gradle.kts"))
            .map { it.groupValues[1].removePrefix(":").replace(':', '/') }
            .toList()

    /** The bodies of the root `build.gradle.kts`'s `subprojects { ... }` blocks, with `//` comments dropped. */
    fun subprojectsBlocks(): List<String> {
        val script =
            read("build.gradle.kts")
                .lines()
                .joinToString("\n") { it.substringBefore("//") }
        return Regex("""(?m)^subprojects\s*\{""")
            .findAll(script)
            .map { start ->
                var depth = 0
                var end = script.length
                for (i in start.range.last until script.length) {
                    when (script[i]) {
                        '{' -> {
                            depth++
                        }

                        '}' -> {
                            if (--depth == 0) {
                                end = i
                                break
                            }
                        }
                    }
                }
                script.substring(start.range.last + 1, end)
            }.toList()
    }

    /** Every workflow under `.github/workflows`, parsed, by file name. */
    fun workflows(): Map<String, Map<String, Any?>> =
        checkNotNull(file(".github/workflows").listFiles())
            .filter { it.extension == "yml" || it.extension == "yaml" }
            .sortedBy { it.name }
            .associate { it.name to Yaml.parse(it.readText()).asMap() }

    /** The `<tier> <glob>` rules of `.github/risk-assessment.yml`, in order, as a YAML reader sees them. */
    fun riskRules(): List<Pair<String, String>> =
        Yaml.parse(read(".github/risk-assessment.yml")).asMap().getValue("rules").asList().map { rule ->
            rule.toString().split(Regex("\\s+")).let { (tier, glob) -> tier to glob }
        }
}

fun Any?.asMap(): Map<String, Any?> {
    val map = this as? Map<*, *> ?: error("expected a mapping, got $this")
    return map.entries.associate { (key, value) -> key.toString() to value }
}

fun Any?.asList(): List<Any?> = this as? List<*> ?: error("expected a sequence, got $this")

fun Map<String, Any?>.jobs(): Map<String, Map<String, Any?>> = getValue("jobs").asMap().mapValues { it.value.asMap() }

fun Map<String, Any?>.steps(): List<Map<String, Any?>> =
    (this["steps"] ?: emptyList<Any?>()).asList().map {
        it.asMap()
    }

/** The Gradle tasks [this] job's `run:` steps pass to `./gradlew`, in order, without options. */
fun Map<String, Any?>.gradleTasks(): List<String> =
    steps()
        .mapNotNull { it["run"]?.toString() }
        .flatMap { it.lines() }
        .map { it.trim().split(Regex("\\s+")) }
        .filter { it.first() == "./gradlew" }
        .flatMap { words -> words.drop(1).filterNot { it.startsWith("-") } }

/** Event names that trigger [this] workflow, whatever form its `on:` takes. */
fun Map<String, Any?>.triggers(): Set<String> =
    when (val on = getValue("on")) {
        is String -> setOf(on)
        is List<*> -> on.map { it.toString() }.toSet()
        else -> on.asMap().keys
    }

data class ScriptResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
)

/**
 * Runs [script] (a path relative to the repository root) the way CI does: executed directly, so its shebang and
 * executable bit count. [env] is added to a copy of this process's environment.
 */
fun runScript(
    script: String,
    vararg args: String,
    env: Map<String, String> = emptyMap(),
    stdin: String = "",
    workDir: File = Repo.root,
): ScriptResult = runProcess(listOf(Repo.file(script).path) + args, workDir, env, stdin, name = script)

/**
 * Runs [command] in [workDir] with [env] added to a copy of this process's environment and [stdin] as its input, and
 * waits up to [timeout] for it. With [mergeStderr], stderr goes to [ScriptResult.stdout] as well. [name] is how a
 * failure refers to the command.
 *
 * If the command runs past [timeout], or the wait fails any other way (an interrupt), the command and every process it
 * started are killed and waited for before the failure propagates: a hung script must not outlive the test (#357). If
 * the JVM itself is stopped (an outside timeout's SIGTERM), [Children] kills them instead, and the call fails rather
 * than start a command once that has begun.
 */
fun runProcess(
    command: List<String>,
    workDir: File = Repo.root,
    env: Map<String, String> = emptyMap(),
    stdin: String = "",
    mergeStderr: Boolean = false,
    timeout: Duration = 60.seconds,
    name: String = command.joinToString(" "),
): ScriptResult {
    val input = File.createTempFile("process-in", ".txt").apply { writeText(stdin) }
    val out = File.createTempFile("process-out", ".txt")
    val err = File.createTempFile("process-err", ".txt")
    try {
        val process =
            Children.start(
                ProcessBuilder(command)
                    .directory(workDir)
                    .redirectInput(input)
                    .redirectOutput(out)
                    .apply {
                        if (mergeStderr) redirectErrorStream(true) else redirectError(err)
                        environment().putAll(env)
                    },
            )
        try {
            check(process.waitFor(timeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)) {
                "$name did not finish within $timeout"
            }
        } catch (failure: Throwable) {
            runCatching { process.killTree() }.exceptionOrNull()?.let(failure::addSuppressed)
            throw failure
        }
        return ScriptResult(process.exitValue(), out.readText(), err.readText())
    } finally {
        input.delete()
        out.delete()
        err.delete()
    }
}

/**
 * Starts the processes [runProcess] runs, and kills every process this JVM started when it shuts down. An outside
 * timeout (`timeout 150 java ... JUnitCore`, a CI step's limit) stops the JVM with SIGTERM: the test waiting on a
 * script never gets to [runProcess]'s cleanup, and when the shutdown kills that script the test goes on to start the
 * next one while the JVM exits. Both ran on at 100 % CPU (#389). [start] and the kill share a lock, so nothing starts
 * after the kill has listed what to kill.
 */
private object Children {
    private var shuttingDown = false

    init {
        Runtime.getRuntime().addShutdownHook(Thread { killAll() })
    }

    @Synchronized
    fun start(builder: ProcessBuilder): Process {
        check(!shuttingDown) { "not starting ${builder.command()}: the JVM is shutting down" }
        return builder.start()
    }

    @Synchronized
    private fun killAll() {
        shuttingDown = true
        killAndWait(ProcessHandle.current().descendants().toList())
    }
}

/**
 * Kills this process and every process it started (`quality-report.sh` runs Gradle, which starts daemons), and waits
 * for them all to exit. The tree is listed before the kill, while the children are still this process's.
 */
private fun Process.killTree() = killAndWait(listOf(toHandle()) + descendants().toList())

/** Kills [processes] and waits up to 10 seconds for each to exit. */
private fun killAndWait(processes: List<ProcessHandle>) {
    processes.forEach { it.destroyForcibly() }
    processes.forEach { it.onExit().get(10, TimeUnit.SECONDS) }
}
