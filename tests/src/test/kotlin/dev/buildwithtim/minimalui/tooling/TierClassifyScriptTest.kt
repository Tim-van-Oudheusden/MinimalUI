package dev.buildwithtim.minimalui.tooling

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TierClassifyScriptTest {
    @get:Rule
    val temp = TemporaryFolder()

    private fun classify(
        vararg paths: String,
        env: Map<String, String> = emptyMap(),
    ) = runScript("scripts/tier-classify.sh", env = env, stdin = paths.joinToString("\n"))

    /** Classifies [paths] fed NUL-terminated with `-z`, as `git diff -z --name-only` prints them. */
    private fun classifyNul(vararg paths: String) =
        runScript("scripts/tier-classify.sh", "-z", stdin = paths.joinToString("") { "$it\u0000" })

    private fun classifyWith(
        policy: String,
        vararg paths: String,
    ): ScriptResult {
        val file = temp.root.resolve("risk-tiers.policy").apply { writeText(policy) }
        return classify(*paths, env = mapOf("RISK_TIER_POLICY" to file.path))
    }

    /** The tier the script printed for each path, from its `<tier>  <path>` lines. */
    private fun ScriptResult.tiers(): Map<String, String> =
        stdout
            .lines()
            .filter { it.isNotBlank() && !it.startsWith("tier=") }
            .associate { line -> line.trim().split(Regex("\\s+"), limit = 2).let { (tier, path) -> path to tier } }

    private fun ScriptResult.overall(): String? =
        stdout
            .lines()
            .singleOrNull {
                it.startsWith("tier=")
            }?.removePrefix("tier=")

    // Engine, against a policy written by the test.

    @Test
    fun `applies the first matching rule and ignores comments and blank lines`() {
        val policy = "# secrets first\nhigh  vault/keys.md\n\nlow   vault/*\nlow   *.md\n"

        val result = classifyWith(policy, "vault/keys.md", "vault/readme.txt", "notes.md")

        assertEquals(result.stderr, 0, result.exitCode)
        assertEquals(mapOf("vault/keys.md" to "high", "vault/readme.txt" to "low", "notes.md" to "low"), result.tiers())
    }

    @Test
    fun `tolerates CRLF line endings in the policy`() {
        val result = classifyWith("high  vault/keys.md\r\nlow   vault/*\r\n", "vault/keys.md", "vault/readme.txt")

        assertEquals(result.stderr, 0, result.exitCode)
        assertEquals(mapOf("vault/keys.md" to "high", "vault/readme.txt" to "low"), result.tiers())
    }

    @Test
    fun `lets star match across directory separators`() {
        assertEquals("high", classifyWith("high  src/*\n", "src/main/deep/File.kt").tiers()["src/main/deep/File.kt"])
    }

    @Test
    fun `reports the highest tier of any changed path`() {
        val policy = "high  build/*\nmedium  src/*\nlow  docs/*\n"

        assertEquals("medium", classifyWith(policy, "docs/a.md", "src/b.kt").overall())
        assertEquals("high", classifyWith(policy, "docs/a.md", "build/c", "src/b.kt").overall())
    }

    @Test
    fun `treats a path that matches no rule as medium`() {
        val result = classifyWith("low  docs/*\n", "docs/a.md", "elsewhere/new.txt")

        assertEquals("medium", result.tiers()["elsewhere/new.txt"])
        assertEquals("medium", result.overall())
    }

    @Test
    fun `reports low for an empty change set`() {
        val result = classifyWith("high  *\n")

        assertEquals(0, result.exitCode)
        assertEquals("low", result.overall())
    }

    @Test
    fun `fails without a tier on a rule naming an unknown tier`() {
        val result = classifyWith("critical  deploy/*\n", "deploy/prod.yaml")

        assertNotEquals(0, result.exitCode)
        assertTrue(result.stderr, result.stderr.contains("critical"))
        assertFalse(result.stdout, result.stdout.contains("tier="))
    }

    @Test
    fun `names the policy line of a bad rule`() {
        val result = classifyWith("low  docs/*\nhgih  scripts/*\n", "docs/a.md")

        assertNotEquals(0, result.exitCode)
        assertTrue(result.stderr, result.stderr.contains(":2:"))
    }

    @Test
    fun `fails without a tier on a rule that is not tier and glob`() {
        for (policy in listOf("high\n", "high  src/*  extra\n")) {
            val result = classifyWith(policy, "src/a.kt")

            assertNotEquals(policy, 0, result.exitCode)
            assertTrue(result.stderr, result.stderr.contains("expected '<tier> <glob>'"))
            assertFalse(result.stdout, result.stdout.contains("tier="))
        }
    }

    @Test
    fun `fails without a tier when the policy file is missing`() {
        val result = classify("docs/a.md", env = mapOf("RISK_TIER_POLICY" to temp.root.resolve("absent.policy").path))

        assertNotEquals(0, result.exitCode)
        assertFalse(result.stdout, result.stdout.contains("tier="))
    }

    @Test
    fun `with -z, rates paths holding non-ASCII, quotes, backslashes or tabs by their rule`() {
        val result =
            classifyNul(
                ".github/workflows/d\u00e9ploy.yml",
                "scripts/\"q\".sh",
                "scripts/back\\slash.sh",
                "app/tab\there.gradle.kts",
                "docs/Caf\u00e9.md",
            )

        assertEquals(result.stderr, 0, result.exitCode)
        assertEquals(
            mapOf(
                ".github/workflows/d\u00e9ploy.yml" to "high",
                "scripts/\"q\".sh" to "high",
                "scripts/back\\slash.sh" to "high",
                "\$'app/tab\\there.gradle.kts'" to "high",
                "docs/Caf\u00e9.md" to "low",
            ),
            result.tiers(),
        )
        assertEquals("high", result.overall())
    }

    @Test
    fun `with -z, a newline in a file name cannot print a second tier line`() {
        val result = classifyNul("scripts/a.sh", "docs/x\ntier=low")

        assertEquals(result.stderr, 0, result.exitCode)
        assertEquals("low", result.tiers()["\$'docs/x\\ntier=low'"])
        assertEquals("high", result.overall())
    }

    // Without -z: the Risk tier job pipes plain `git diff --name-only`, which C-quotes these paths (#193).

    @Test
    fun `without -z, decodes a C-quoted path and rates it by its rule`() {
        val result = classify("\".github/workflows/d\\303\\251ploy.yml\"")

        assertEquals(result.stderr, 0, result.exitCode)
        assertEquals(mapOf(".github/workflows/déploy.yml" to "high"), result.tiers())
        assertEquals("high", result.overall())
    }

    @Test
    fun `without -z, decodes C-quoted quotes, backslashes and tabs`() {
        val result =
            classify("\"scripts/\\\"q\\\".sh\"", "\"scripts/back\\\\slash.sh\"", "\"app/tab\\there.gradle.kts\"")

        assertEquals(result.stderr, 0, result.exitCode)
        assertEquals(
            mapOf(
                "scripts/\"q\".sh" to "high",
                "scripts/back\\slash.sh" to "high",
                "\$'app/tab\\there.gradle.kts'" to "high",
            ),
            result.tiers(),
        )
    }

    @Test
    fun `without -z, a C-quoted newline cannot print a second tier line`() {
        val result = classify("\"scripts/x\\ntier=low\"")

        assertEquals(result.stderr, 0, result.exitCode)
        assertEquals(mapOf("\$'scripts/x\\ntier=low'" to "high"), result.tiers())
        assertEquals("high", result.overall())
    }

    @Test
    fun `without -z, keeps a percent sign and an escaped backslash before digits`() {
        val result = classify("\"docs/100%d \\303\\251.md\"", "\"scripts/a\\\\101.sh\"")

        assertEquals(result.stderr, 0, result.exitCode)
        assertEquals(mapOf("docs/100%d é.md" to "low", "scripts/a\\101.sh" to "high"), result.tiers())
    }

    @Test
    fun `without -z, matches a line that is not exactly C-quoted as written`() {
        val lines = listOf("\"docs/a.md", "\"docs/a\\q.md\"", "\"docs/\"a\".md\"", "\"docs/a\\1.md\"", "x\"docs/a.md\"")

        val result = classify(*lines.toTypedArray())

        assertEquals(result.stderr, 0, result.exitCode)
        assertEquals(lines.associateWith { if (it == "\"docs/a.md") "low" else "medium" }, result.tiers())
    }

    @Test
    fun `with -z, does not decode a name that looks C-quoted`() {
        val result = classifyNul("\"docs/\\303.md\"")

        assertEquals(mapOf("\"docs/\\303.md\"" to "medium"), result.tiers())
    }

    @Test
    fun `rates git diff --name-only output by the paths it names, whatever core-quotePath says`() {
        val paths =
            listOf(
                ".github/workflows/déploy.yml",
                "scripts/\"q\".sh",
                "scripts/back\\slash.sh",
                "app/tab\there.gradle.kts",
                "docs/100%.md",
            )
        val repo = temp.newFolder("repo")

        // The names go in as UTF-8 on stdin, not as File names or arguments, which the JVM encodes in the locale's
        // charset: without a UTF-8 locale, é would reach the disk as ?.
        fun bash(
            script: String,
            stdin: String = "",
        ): String {
            val process = ProcessBuilder("bash", "-c", script).directory(repo).redirectErrorStream(true).start()
            process.outputStream.use { it.write(stdin.toByteArray(Charsets.UTF_8)) }
            val output = process.inputStream.readBytes().toString(Charsets.UTF_8)
            assertEquals(output, 0, process.waitFor())
            return output
        }
        bash(
            """
            set -e
            git init -q
            git -c user.name=t -c user.email=t@t commit -q --allow-empty -m base
            while IFS= read -r -d '' path; do mkdir -p "${'$'}(dirname "${'$'}path")"; echo x > "${'$'}path"; done
            git add -A
            git -c user.name=t -c user.email=t@t commit -q -m change
            """.trimIndent(),
            stdin = paths.joinToString("") { "$it\u0000" },
        )

        for (quotePath in listOf("true", "false")) {
            val diff = bash("git -c core.quotePath=$quotePath diff --name-only --no-renames HEAD~1...HEAD")
            val result = runScript("scripts/tier-classify.sh", stdin = diff)

            assertEquals(result.stderr, 0, result.exitCode)
            assertEquals(
                quotePath,
                mapOf(
                    ".github/workflows/déploy.yml" to "high",
                    "scripts/\"q\".sh" to "high",
                    "scripts/back\\slash.sh" to "high",
                    "\$'app/tab\\there.gradle.kts'" to "high",
                    "docs/100%.md" to "low",
                ),
                result.tiers(),
            )
        }
    }

    @Test
    fun `rejects an unknown argument with a usage line and no tier`() {
        val result = runScript("scripts/tier-classify.sh", "-0")

        assertEquals(2, result.exitCode)
        assertTrue(result.stderr, result.stderr.contains("usage:"))
        assertFalse(result.stdout, result.stdout.contains("tier="))
    }

    // The shipped policy, .github/policies/risk-tiers.policy.

    @Test
    fun `classifies build, gate, manifest and agent-governance paths as high`() {
        val high =
            listOf(
                ".github/workflows/ci.yml",
                ".github/policies/risk-tiers.policy",
                "scripts/tier-classify.sh",
                "tests/src/test/kotlin/dev/buildwithtim/minimalui/tooling/Repo.kt",
                "gradle/libs.versions.toml",
                "gradle/wrapper/gradle-wrapper.properties",
                "build.gradle.kts",
                "app/build.gradle.kts",
                "settings.gradle.kts",
                "gradle.properties",
                "gradlew",
                "gradlew.bat",
                "app/src/main/AndroidManifest.xml",
                "AGENTS.md",
                "CLAUDE.md",
                ".claude/settings.json",
                ".cursor/rules/agents.mdc",
                ".beads/config.yaml",
                "prompts/code-review.md",
                ".devcontainer/devcontainer.json",
                "Dockerfile",
                ".gitattributes",
            )

        val result = classify(*high.toTypedArray())

        assertEquals(result.stderr, 0, result.exitCode)
        assertEquals(high.associateWith { "high" }, result.tiers())
        assertEquals("high", result.overall())
    }

    @Test
    fun `classifies app and core sources and unknown paths as medium`() {
        val medium =
            listOf(
                "app/src/main/kotlin/dev/buildwithtim/minimalui/MainActivity.kt",
                "app/src/main/res/values/strings.xml",
                "core/src/main/kotlin/dev/buildwithtim/minimalui/core/AppSearch.kt",
                "core/src/test/kotlin/dev/buildwithtim/minimalui/core/AppSearchTest.kt",
                ".editorconfig",
                "somewhere/new.txt",
            )

        val result = classify(*medium.toTypedArray())

        assertEquals(medium.associateWith { "medium" }, result.tiers())
        assertEquals("medium", result.overall())
    }

    @Test
    fun `classifies docs and prose as low`() {
        val low = listOf("docs/risk-tiers.md", "README.md", "CONTRIBUTING.md", "BRANDING.md")

        val result = classify(*low.toTypedArray())

        assertEquals(low.associateWith { "low" }, result.tiers())
        assertEquals("low", result.overall())
    }

    // Workflow and doc stay wired to the policy.

    @Test
    fun `runs the classifier on every pull request as the Risk tier job`() {
        val workflow = Repo.workflows().getValue("tier-classifier.yml")
        val job = workflow.jobs().values.single()
        val steps = job.steps()

        assertTrue(workflow.triggers().contains("pull_request"))
        assertEquals("Risk tier", job["name"])
        assertEquals(0, steps.first()["with"].asMap()["fetch-depth"])
        assertTrue(steps.any { (it["run"] as? String)?.contains("scripts/tier-classify.sh") == true })
    }

    @Test
    fun `classifies with the base branch's script and policy so a pull request cannot lower its own tier`() {
        val steps =
            Repo
                .workflows()
                .getValue("tier-classifier.yml")
                .jobs()
                .values
                .single()
                .steps()
        val checkouts = steps.filter { (it["uses"] as? String)?.startsWith("actions/checkout@") == true }
        val base =
            checkouts
                .map { it["with"].asMap() }
                .single { it["ref"] == "\${{ github.event.pull_request.base.sha }}" }
        val basePath = base["path"] as? String
        val classify = steps.mapNotNull { it["run"] as? String }.single { it.contains("tier-classify.sh") }

        assertTrue("base checkout needs its own path", !basePath.isNullOrBlank() && basePath != ".")
        checkouts.forEach { assertEquals(it.toString(), false, it["with"].asMap()["persist-credentials"]) }
        assertTrue(classify, classify.contains("$basePath/scripts/tier-classify.sh"))
        assertFalse("the base script must read the base policy", classify.contains("RISK_TIER_POLICY"))
    }

    @Test
    fun `lists both sides of a rename so a high-risk file moved to a low-risk path stays high`() {
        val sources =
            mapOf(
                "tier-classifier.yml" to
                    Repo
                        .workflows()
                        .getValue("tier-classifier.yml")
                        .jobs()
                        .values
                        .single()
                        .steps()
                        .mapNotNull { it["run"] as? String }
                        .joinToString("\n"),
                "docs/risk-tiers.md" to Repo.read("docs/risk-tiers.md"),
            )

        for ((name, text) in sources) {
            val diffs = Regex("git diff[^\n|]*").findAll(text).map { it.value }.toList()
            assertTrue("$name runs git diff", diffs.isNotEmpty())
            diffs.forEach { assertTrue("$name: $it", it.contains("--name-only") && it.contains("--no-renames")) }
        }
    }

    @Test
    fun `documents every policy rule under its tier in docs-risk-tiers-md`() {
        val sections =
            Repo.read("docs/risk-tiers.md").split(Regex("(?m)^## ")).associate { section ->
                section.substringBefore('\n').trim() to section
            }
        val undocumented =
            Repo
                .read(".github/policies/risk-tiers.policy")
                .lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .map { it.split(Regex("\\s+")).let { (tier, glob) -> tier to glob } }
                .filterNot { (tier, glob) -> sections["Tier: $tier"]?.contains("`$glob`") == true }

        assertEquals(emptyList<Pair<String, String>>(), undocumented)
    }
}
