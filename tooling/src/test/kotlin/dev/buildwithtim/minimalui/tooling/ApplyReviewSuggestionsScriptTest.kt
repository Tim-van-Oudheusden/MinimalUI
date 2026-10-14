package dev.buildwithtim.minimalui.tooling

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ApplyReviewSuggestionsScriptTest {
    private val sandbox: File = Files.createTempDirectory("apply-review-").toFile()
    private val checkout: File = sandbox.resolve("pr").apply { mkdir() }

    @After
    fun cleanUp() {
        sandbox.deleteRecursively()
    }

    private fun comment(
        id: Int = 1,
        path: String,
        line: Int?,
        body: String,
        startLine: Int? = null,
        association: String = "OWNER",
        side: String = "RIGHT",
        startSide: String? = null,
    ) = """{"id":$id,"path":${json(path)},"line":$line,"start_line":$startLine,"side":${json(side)},""" +
        """"start_side":${startSide?.let(::json)},"author_association":${json(association)},"body":${json(body)}}"""

    private fun suggestion(text: String) = "Nit:\n\n```suggestion\n$text```\n"

    private fun json(text: String) =
        buildString {
            append('"')
            text.forEach { c ->
                when {
                    c == '"' -> append("\\\"")
                    c == '\\' -> append("\\\\")
                    c < ' ' -> append("\\u%04x".format(c.code))
                    else -> append(c)
                }
            }
            append('"')
        }

    private fun apply(vararg comments: String): ScriptResult {
        val commentsFile = sandbox.resolve("comments.json")
        commentsFile.writeText(comments.joinToString(",", "[", "]"))
        return runScript(
            "scripts/apply-review-suggestions.sh",
            env = mapOf("COMMENTS_FILE" to commentsFile.path),
            workDir = checkout,
        )
    }

    private fun file(
        path: String,
        text: String,
    ) = checkout.resolve(path).apply {
        parentFile.mkdirs()
        writeText(text)
    }

    @Test
    fun `applies a maintainer's single-line suggestion to the commented line`() {
        val a = file("a.kt", "one\ntwo\nthree\n")

        val result = apply(comment(path = "a.kt", line = 2, body = suggestion("TWO\n")))

        assertEquals(result.stderr, 0, result.exitCode)
        assertEquals("one\nTWO\nthree\n", a.readText())
        assertEquals("applied: a.kt:2-2 (comment 1)\n", result.stdout)
    }

    @Test
    fun `applies suggestions from members and collaborators across files, keeping unicode and CRLF bodies clean`() {
        val a = file("src/a.kt", "one\ntwo\n")
        val b = file("b.kt", "x\ny\n")

        val result =
            apply(
                comment(
                    id = 1,
                    path = "src/a.kt",
                    line = 1,
                    body = "```suggestion\r\nÉén ✓\r\n```",
                    association = "MEMBER",
                ),
                comment(id = 2, path = "b.kt", line = 2, body = suggestion("Y\n"), association = "COLLABORATOR"),
            )

        assertEquals(result.stderr, 0, result.exitCode)
        assertEquals("Één ✓\ntwo\n", a.readText())
        assertEquals("x\nY\n", b.readText())
        assertTrue(result.stdout, result.stdout.contains("applied: src/a.kt:1-1 (comment 1)"))
        assertTrue(result.stdout, result.stdout.contains("applied: b.kt:2-2 (comment 2)"))
    }

    @Test
    fun `skips suggestions from authors without write access and outdated comments`() {
        val a = file("a.kt", "one\ntwo\nthree\n")

        val result =
            apply(
                comment(id = 1, path = "a.kt", line = 1, body = suggestion("ONE\n"), association = "CONTRIBUTOR"),
                comment(id = 2, path = "a.kt", line = 2, body = suggestion("TWO\n"), association = "NONE"),
                comment(id = 3, path = "a.kt", line = null, body = suggestion("THREE\n")),
            )

        assertEquals(result.stderr, 0, result.exitCode)
        assertEquals("one\ntwo\nthree\n", a.readText())
        assertFalse(result.stdout, result.stdout.contains("applied:"))
        assertTrue(
            result.stdout,
            result.stdout.contains("skipped: comment 1 on a.kt: author CONTRIBUTOR has no write access"),
        )
        assertTrue(result.stdout, result.stdout.contains("skipped: comment 2 on a.kt: author NONE has no write access"))
        assertTrue(result.stdout, result.stdout.contains("skipped: comment 3 on a.kt: outdated"))
    }

    // A LEFT-side comment's line numbers refer to the base file, not the checkout being edited.
    @Test
    fun `skips suggestions on the base side of the diff`() {
        val a = file("a.kt", "one\ntwo\n")

        val result =
            apply(
                comment(id = 1, path = "a.kt", line = 1, body = suggestion("ONE\n"), side = "LEFT"),
                comment(id = 2, path = "a.kt", startLine = 1, line = 2, body = suggestion("TWO\n"), startSide = "LEFT"),
            )

        assertEquals(result.stderr, 0, result.exitCode)
        assertEquals("one\ntwo\n", a.readText())
        assertTrue(result.stdout, result.stdout.contains("skipped: comment 1 on a.kt: on the base side of the diff"))
        assertTrue(result.stdout, result.stdout.contains("skipped: comment 2 on a.kt: on the base side of the diff"))
    }

    // A GITHUB_TOKEN push touching .github/workflows/ is rejected, which would lose every other suggestion too.
    @Test
    fun `skips suggestions on workflow files and still applies the rest`() {
        val workflow = file(".github/workflows/ci.yml", "name: CI\n")
        val a = file("a.kt", "one\n")

        val result =
            apply(
                comment(id = 1, path = ".github/workflows/ci.yml", line = 1, body = suggestion("name: Pwned\n")),
                comment(id = 2, path = "a.kt", line = 1, body = suggestion("ONE\n")),
            )

        assertEquals(result.stderr, 0, result.exitCode)
        assertEquals("name: CI\n", workflow.readText())
        assertEquals("ONE\n", a.readText())
        assertTrue(
            result.stdout,
            result.stdout.contains(
                "skipped: comment 1 on .github/workflows/ci.yml: " +
                    "workflow files cannot be pushed with the workflow token",
            ),
        )
    }

    @Test
    fun `skips a comment holding more than one suggestion block`() {
        val a = file("a.kt", "one\n")

        val result =
            apply(
                comment(
                    path = "a.kt",
                    line = 1,
                    body = "Either\n```suggestion\nONE\n```\nor\n```suggestion\nUNO\n```\n",
                ),
            )

        assertEquals(result.stderr, 0, result.exitCode)
        assertEquals("one\n", a.readText())
        assertTrue(result.stdout, result.stdout.contains("skipped: comment 1 on a.kt: more than one suggestion block"))
    }

    @Test
    fun `keeps backticks inside a suggestion line, ending the block only at a fence that starts a line`() {
        val readme = file("README.md", "# Title\nold\n")

        val result =
            apply(comment(path = "README.md", line = 2, body = suggestion("Fence code with ```kotlin``` blocks.\n")))

        assertEquals(result.stderr, 0, result.exitCode)
        assertEquals("# Title\nFence code with ```kotlin``` blocks.\n", readme.readText())
    }

    @Test
    fun `skips a range past the end of the file`() {
        val a = file("a.kt", "one\n")

        val result = apply(comment(path = "a.kt", startLine = 2, line = 3, body = suggestion("x\n")))

        assertEquals(result.stderr, 0, result.exitCode)
        assertEquals("one\n", a.readText())
        assertTrue(result.stdout, result.stdout.contains("skipped: comment 1 on a.kt: lines 2-3 are not in the file"))
    }

    @Test
    fun `applies a suggestion to the last line of a file without a trailing newline`() {
        val a = file("a.kt", "one\ntwo")

        val result = apply(comment(path = "a.kt", line = 2, body = suggestion("TWO\n")))

        assertEquals(result.stderr, 0, result.exitCode)
        assertEquals("one\nTWO\n", a.readText())
    }

    @Test
    fun `refuses paths outside the checkout and files that do not exist`() {
        val outside = sandbox.resolve("outside.txt").apply { writeText("secret\n") }

        val result =
            apply(
                comment(id = 1, path = "../outside.txt", line = 1, body = suggestion("pwned\n")),
                comment(id = 2, path = outside.absolutePath, line = 1, body = suggestion("pwned\n")),
                comment(id = 3, path = "missing.kt", line = 1, body = suggestion("x\n")),
            )

        assertEquals(result.stderr, 0, result.exitCode)
        assertEquals("secret\n", outside.readText())
        assertFalse(result.stdout, result.stdout.contains("applied:"))
        listOf(1, 2, 3).forEach {
            assertTrue(
                result.stdout,
                Regex("skipped: comment $it on .*: not a regular file in the checkout").containsMatchIn(result.stdout),
            )
        }
    }

    @Test
    fun `refuses files reached through a symlinked directory that points outside the checkout`() {
        val outside = sandbox.resolve("outside").apply { mkdir() }
        val secret = outside.resolve("secret.txt").apply { writeText("secret\n") }
        Files.createSymbolicLink(checkout.resolve("linked").toPath(), outside.toPath())

        val result = apply(comment(path = "linked/secret.txt", line = 1, body = suggestion("pwned\n")))

        assertEquals(result.stderr, 0, result.exitCode)
        assertEquals("secret\n", secret.readText())
        assertFalse(result.stdout, result.stdout.contains("applied:"))
    }

    @Test
    fun `applies several multi-line suggestions in one file and skips one overlapping an applied range`() {
        val a = file("a.kt", "1\n2\n3\n4\n5\n6\n")

        val result =
            apply(
                comment(id = 1, path = "a.kt", startLine = 1, line = 2, body = suggestion("one-two\n")),
                comment(id = 2, path = "a.kt", startLine = 4, line = 5, body = suggestion("")),
                comment(id = 3, path = "a.kt", line = 6, body = suggestion("six\nseven\n")),
                comment(id = 4, path = "a.kt", startLine = 5, line = 6, body = suggestion("clash\n")),
            )

        assertEquals(result.stderr, 0, result.exitCode)
        assertEquals("one-two\n3\nsix\nseven\n", a.readText())
        assertTrue(
            result.stdout,
            result.stdout.contains("skipped: comment 4 on a.kt: overlaps another applied suggestion"),
        )
        assertTrue(result.stdout, result.stdout.contains("applied: a.kt:4-5 (comment 2)"))
    }

    @Test
    fun `reports when no comment carries a suggestion`() {
        val a = file("a.kt", "one\n")

        val result = apply(comment(path = "a.kt", line = 1, body = "Looks good."))

        assertEquals(result.stderr, 0, result.exitCode)
        assertEquals("one\n", a.readText())
        assertEquals("no suggestions to apply\n", result.stdout)
    }

    @Test
    fun `fails without a comments file`() {
        val result = runScript("scripts/apply-review-suggestions.sh", workDir = checkout)

        assertTrue(result.exitCode != 0)
        assertTrue(result.stderr, result.stderr.contains("COMMENTS_FILE"))
    }
}
