package dev.buildwithtim.minimalui.tooling

import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

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
): ScriptResult {
    val out = File.createTempFile("script-out", ".txt")
    val err = File.createTempFile("script-err", ".txt")
    try {
        val process =
            ProcessBuilder(listOf(Repo.file(script).path) + args)
                .directory(workDir)
                .redirectOutput(out)
                .redirectError(err)
                .apply { environment().putAll(env) }
                .start()
        try {
            process.outputStream.bufferedWriter().use { it.write(stdin) }
        } catch (ignored: IOException) {
            // The script exited before reading all of stdin (a usage or policy error); its exit code and output
            // below are the result.
        }
        check(process.waitFor(60, TimeUnit.SECONDS)) { "$script did not finish within 60 s" }
        return ScriptResult(process.exitValue(), out.readText(), err.readText())
    } finally {
        out.delete()
        err.delete()
    }
}
