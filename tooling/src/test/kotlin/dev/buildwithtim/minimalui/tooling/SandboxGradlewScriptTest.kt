package dev.buildwithtim.minimalui.tooling

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class SandboxGradlewScriptTest {
    private val dir: File = Files.createTempDirectory("sandbox-gradlew").toFile()
    private val home = dir.resolve("home")
    private val keystore = home.resolve(".android/debug.keystore")
    private val calls = dir.resolve("podman-args.txt")

    /** Fake `podman` first on PATH: writes its arguments one per line and exits with FAKE_EXIT. */
    private val bin =
        dir.resolve("bin").apply {
            mkdirs()
            resolve("podman").apply {
                writeText(
                    """
                    #!/usr/bin/env bash
                    printf '%s\n' "${'$'}@" > "${calls.path}"
                    exit "${'$'}{FAKE_EXIT:-0}"

                    """.trimIndent(),
                )
                setExecutable(true)
            }
        }

    init {
        keystore.parentFile.mkdirs()
        keystore.writeText("key")
    }

    @After
    fun cleanUp() {
        dir.deleteRecursively()
    }

    private fun sandbox(
        vararg args: String,
        env: Map<String, String> = emptyMap(),
    ) = runScript(
        "scripts/sandbox-gradlew.sh",
        *args,
        env = mapOf("HOME" to home.path, "PATH" to "${bin.path}:${System.getenv("PATH")}") + env,
        // From a module directory: the repository root is still what gets mounted.
        workDir = Repo.file("core"),
    )

    @Test
    fun `runs gradlew in the sandbox with the checkout, the Gradle cache and the debug key mounted`() {
        val result = sandbox("check", "assembleDebug")

        assertEquals(result.stderr, 0, result.exitCode)
        assertEquals(
            listOf(
                "run",
                "--rm",
                "--userns=keep-id",
                "-v",
                "${Repo.root.canonicalPath}:/workspace:z",
                "-v",
                "minimalui-sandbox-gradle:/home/dev/.gradle",
                // AGP needs ~/.android writable even when the keystore exists (#159); the key stays read-only.
                "--tmpfs",
                "/home/dev/.android:rw,U",
                "-v",
                "${keystore.path}:/home/dev/.android/debug.keystore:ro,z",
                "localhost/minimalui-sandbox",
                "./gradlew",
                "--no-daemon",
                "--console=plain",
                "check",
                "assembleDebug",
            ),
            calls.readLines(),
        )
    }

    // Parallel worktrees each need a Gradle cache of their own.
    @Test
    fun `mounts another Gradle cache volume when one is named`() {
        sandbox("test", env = mapOf("SANDBOX_GRADLE_VOLUME" to "minimalui-sandbox-gradle-135"))

        assertTrue(calls.readLines().toString(), "minimalui-sandbox-gradle-135:/home/dev/.gradle" in calls.readLines())
    }

    @Test
    fun `stops before podman when there is no debug keystore`() {
        keystore.delete()

        val result = sandbox("assembleDebug")

        assertEquals(2, result.exitCode)
        assertTrue(result.stderr, result.stderr.contains(keystore.path))
        assertTrue(result.stderr, result.stderr.contains("docs/testing.md#the-debug-signing-key"))
        assertFalse(calls.exists())
    }

    @Test
    fun `exits with Gradle's status`() {
        val result = sandbox("check", env = mapOf("FAKE_EXIT" to "3"))

        assertEquals(3, result.exitCode)
    }
}
