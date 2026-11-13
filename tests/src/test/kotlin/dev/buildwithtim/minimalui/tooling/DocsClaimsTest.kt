package dev.buildwithtim.minimalui.tooling

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Facts the docs state about this repository, read from the docs and from the file that decides them. A doc names the
 * release APK, the required checks, the labels that keep a pull request out of the merge queue, the workflows that
 * start the queue or that it starts, the untranslated group and suffix names, the release certificate digest, and the
 * way out of a HOME-app lockout that the crash report form shows; a later pull request changes one of them, and until
 * now nothing failed (#329, after #175, #185 and #209). Each test extracts both sides with a Regex and fails when
 * either stops matching, so rewording a doc cannot make a claim pass by finding nothing to compare.
 *
 * It covers facts a file records. A claim about code behaviour or about how Hive behaves at run time is out of scope.
 */
class DocsClaimsTest {
    private fun backticked(text: String): Set<String> =
        Regex("""`([^`]+)`""").findAll(text).map { it.groupValues[1] }.toSet()

    /** The backticked names listed straight after [leadIn], as `a`, `b` and `c` or as (`a`, `b`, `c`). */
    private fun namesAfter(
        leadIn: String,
        doc: String,
    ): Set<String> =
        Regex(
            leadIn.split(" ").joinToString("""\s+""") { Regex.escape(it) } +
                """\s*\(?(`[^`]+`(?:,?\s+(?:and\s+)?`[^`]+`)+)""",
        ).find(Repo.read(doc))
            ?.let { backticked(it.groupValues[1]) }
            .orEmpty()

    // README.md names the file a user downloads; the release workflow names the file it attaches, built from the tag.
    // The tag carries the `v` (#175), which scripts/release-version.sh puts there.
    @Test
    fun `the README names the release APK the way the release workflow builds it`() {
        val name =
            Regex("""(?m)^\s*apk="(minimalui-)[$][{]TAG[}](\.apk)"$""")
                .find(Repo.read(".github/workflows/release.yml"))
        assertTrue("apk=\"minimalui-\${TAG}.apk\" in .github/workflows/release.yml", name != null)
        val tagHasV = Regex("""(?m)^echo "tag=v[$]version"$""").containsMatchIn(Repo.read("scripts/release-version.sh"))
        assertTrue("echo \"tag=v\$version\" in scripts/release-version.sh", tagHasV)

        val (prefix, suffix) = name!!.destructured

        assertTrue("${prefix}v<version>$suffix in README.md", "${prefix}v<version>$suffix" in Repo.read("README.md"))
    }

    // The ruleset decides which checks block a merge; each doc lists them by name after its own lead-in words.
    @Test
    fun `the docs list the checks the main ruleset requires`() {
        val required =
            Regex(""""context": "([^"]+)"""")
                .findAll(Repo.read(".github/rulesets/main.json"))
                .map { it.groupValues[1] }
                .toSet()
        val lists =
            mapOf(
                "docs/multi-agent.md" to "run the same required checks as any other",
                "docs/ai-ops-runbook.md" to "means a required check",
                "AGENTS.md" to "with passing checks",
                "CONTRIBUTING.md" to "changes land only through pull requests whose",
            )
        assertTrue("required_status_checks contexts in .github/rulesets/main.json", required.isNotEmpty())

        lists.forEach { (doc, leadIn) ->
            val names = namesAfter(leadIn, doc)
            assertTrue("a list of check names after \"$leadIn\" in $doc", names.isNotEmpty())

            assertEquals("$doc: names missing from the ruleset", emptySet<String>(), names - required)
            assertEquals("$doc: names missing from the doc", emptySet<String>(), required - names)
        }
    }

    // The queue's rule in docs/multi-agent.md is "has neither a `hold` nor a `sentinel-alert` label".
    @Test
    fun `the docs name the labels the merge queue script keeps out`() {
        val inScript =
            Regex("""(?m)^KEEP_OUT_LABELS=\(([^)]*)\)$""")
                .find(Repo.read("scripts/merge-queue.sh"))
                ?.groupValues
                ?.get(1)
                ?.trim()
                ?.split(Regex("""\s+"""))
                ?.toSet()
                .orEmpty()
        val inDoc =
            Regex("""neither a `([^`]+)` nor a `([^`]+)`""")
                .find(Repo.read("docs/multi-agent.md"))
                ?.groupValues
                ?.drop(1)
                ?.toSet()
                .orEmpty()
        assertTrue("KEEP_OUT_LABELS=(...) in scripts/merge-queue.sh", inScript.isNotEmpty())
        assertTrue("\"neither a `x` nor a `y`\" in docs/multi-agent.md", inDoc.isNotEmpty())

        assertEquals("labels the doc names, then the script's", inDoc, inScript)
    }

    // workflow_run decides what starts the queue, and docs/multi-agent.md restates it.
    @Test
    fun `the docs name the workflows that start the merge queue`() {
        val watched =
            Repo
                .workflows()
                .getValue("merge-queue.yml")
                .getValue("on")
                .asMap()
                .getValue("workflow_run")
                .asMap()
                .getValue("workflows")
                .asList()
                .map { it.toString() }
                .toSet()
        val inDoc = namesAfter("Whenever a workflow holding a required check", "docs/multi-agent.md")
        assertTrue("(`x`, `y`) after \"Whenever a workflow holding a required check\" in docs", inDoc.isNotEmpty())

        assertEquals("workflows the doc names, then merge-queue.yml's", inDoc, watched)
    }

    // A merge made with GITHUB_TOKEN starts no push run, so the queue starts these on main itself. Both docs name them,
    // and the runbook gives the loop that starts them by hand when the queue did not: a workflow missing from that
    // loop goes unnoticed in exactly the failure the loop is there for.
    @Test
    fun `the docs name the workflows the merge queue starts on main`() {
        val files =
            Regex("""(?m)^MAIN_PUSH_WORKFLOWS=\(([^)]*)\)$""")
                .find(Repo.read("scripts/merge-queue.sh"))
                ?.groupValues
                ?.get(1)
                ?.trim()
                ?.split(Regex("""\s+"""))
                .orEmpty()
        val names =
            files
                .map {
                    Repo
                        .workflows()
                        .getValue(it)
                        .getValue("name")
                        .toString()
                }.toSet()
        val byHand =
            Regex("""for w in ([^;`]+); do gh workflow run""")
                .find(Repo.read("docs/ai-ops-runbook.md"))
                ?.groupValues
                ?.get(1)
                ?.trim()
                ?.split(Regex("""\s+"""))
                .orEmpty()
        val prose =
            mapOf(
                "docs/multi-agent.md" to "it merges the pull request (a merge commit) and starts",
                "docs/ai-ops-runbook.md" to "after each merge the queue starts",
            )
        assertTrue("MAIN_PUSH_WORKFLOWS=(...) in scripts/merge-queue.sh", files.isNotEmpty())

        assertEquals("the runbook's loop, then the script's array", byHand.toSet(), files.toSet())
        prose.forEach { (doc, leadIn) ->
            assertEquals("$doc: workflows after \"$leadIn\", then the script's", namesAfter(leadIn, doc), names)
        }
    }

    // CONTRIBUTING.md says these two words stay in English and names the constants that hold them. BRANDING.md gives
    // the same words as the row suffixes, without the constants.
    @Test
    fun `the docs give the work group and clone suffix the values core defines`() {
        val work = constant("core/src/main/kotlin/dev/buildwithtim/minimalui/core/AppGroups.kt", "WORK_GROUP")
        val clone = constant("core/src/main/kotlin/dev/buildwithtim/minimalui/core/DuplicateLabels.kt", "CLONE_SUFFIX")
        val contributing = Repo.read("CONTRIBUTING.md")
        val branding = Repo.read("BRANDING.md")
        val docWork = Regex("""`(\w+)` is its `WORK_GROUP` constant""").find(contributing)?.groupValues?.get(1)
        val docClone =
            Regex("""`(\w+)`, the suffix[^`]*`strings\.xml`[^`]*`CLONE_SUFFIX`""")
                .find(contributing)
                ?.groupValues
                ?.get(1)
        val suffixes =
            Regex("""suffix is `(\w+)` and an app clone's `(\w+)`""")
                .find(branding)
                ?.groupValues
                ?.drop(1)
        assertTrue("`Work` is its `WORK_GROUP` constant in CONTRIBUTING.md", docWork != null)
        assertTrue("`Clone`, the suffix ... `strings.xml` ... `CLONE_SUFFIX` in CONTRIBUTING.md", docClone != null)
        assertTrue("suffix is `Work` and an app clone's `Clone` in BRANDING.md", suffixes != null)

        assertEquals(listOf(work, clone, work, clone), listOf(docWork, docClone) + suffixes!!)
    }

    // The certificate digest is stated in the release-key runbook and again in the README's install section.
    @Test
    fun `the README and the release key runbook state the same certificate digest`() {
        val digest = Regex("""\b[0-9a-f]{64}\b""")
        val inRunbook = digest.findAll(Repo.read("docs/runbooks/release-key.md")).map { it.value }.toSet()
        val inReadme = digest.findAll(Repo.read("README.md")).map { it.value }.toSet()
        assertTrue("a 64-digit hex digest in docs/runbooks/release-key.md", inRunbook.isNotEmpty())
        assertTrue("a 64-digit hex digest in README.md", inReadme.isNotEmpty())

        assertEquals(inRunbook, inReadme)
    }

    // The rollback runbook's first signal is a user's issue, and a user locked out by a crash-looping HOME app needs
    // the way out before anyone answers it, so the crash report form shows the Settings paths of the runbook's lockout
    // steps (#416).
    @Test
    fun `the crash report form gives the rollback runbook's way out of a HOME-app lockout`() {
        val form = ".github/ISSUE_TEMPLATE/crash-report.yml"
        val runbook = Repo.read("docs/runbooks/bad-release-rollback.md")
        val lockout = Regex("""(?s)### HOME-app lockout.*?(?=\n### )""").find(runbook)?.value.orEmpty()
        val paths = Regex("""\*\*(Settings → [^*]+)\*\*""").findAll(lockout).map { it.groupValues[1] }.toSet()
        assertTrue("**Settings → ...** under ### HOME-app lockout in bad-release-rollback.md", paths.isNotEmpty())
        assertTrue("bad-release-rollback.md links ../../$form", "../../$form" in runbook)
        val wayOut =
            Yaml
                .parse(Repo.read(form))
                .asMap()
                .getValue("body")
                .asList()
                .first()
                .asMap()
                .getValue("attributes")
                .asMap()
                .getValue("value")
                .toString()

        assertEquals(paths, paths.filter { "**$it**" in wayOut }.toSet())
    }

    /** The string [name] is assigned to in the Kotlin source [path]. */
    private fun constant(
        path: String,
        name: String,
    ): String {
        val value = Regex("""const val $name = "([^"]+)"""").find(Repo.read(path))?.groupValues?.get(1)
        assertTrue("const val $name = \"...\" in $path", value != null)
        return value!!
    }
}
