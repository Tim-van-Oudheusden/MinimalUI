package dev.buildwithtim.minimalui.tooling

import java.io.File
import javax.imageio.ImageIO
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The store listing in `fastlane/metadata/android/` (#120), which F-Droid, IzzyOnDroid and Google Play (fastlane
 * `supply`) read. A text over its store limit is cut off or rejected there, and a release without a changelog for its
 * `versionCode` ships with an empty "What's new", so both fail here instead.
 */
class FastlaneMetadataTest {
    private val locales: List<File> = Repo.file("fastlane/metadata/android").listFiles { file -> file.isDirectory }
        .orEmpty().sortedBy { it.name }

    /** Google Play's limits, the strictest of the stores reading this layout. */
    private val textLimits = mapOf("title.txt" to 30, "short_description.txt" to 80, "full_description.txt" to 4000)
    private val changelogLimit = 500

    private val versionCode: Int = Regex("""^\s*versionCode = (\d+)\s*$""", RegexOption.MULTILINE)
        .findAll(Repo.read("app/build.gradle.kts")).map { it.groupValues[1].toInt() }.toList()
        .also { assertEquals("exactly one versionCode in app/build.gradle.kts", 1, it.size) }
        .single()

    /** The text a store shows: the file without its trailing newline. */
    private fun shown(file: File): String = file.readText().trimEnd('\n')

    @Test
    fun `there is an en-US listing`() {
        assertTrue("fastlane/metadata/android/en-US is missing", locales.any { it.name == "en-US" })
    }

    @Test
    fun `every listing text is present and within its store limit`() {
        for (locale in locales) {
            for ((name, limit) in textLimits) {
                val file = locale.resolve(name)
                assertTrue("${file.path} is missing", file.isFile)
                val text = shown(file)
                assertTrue("${file.path} is empty", text.isNotBlank())
                assertTrue("${file.path} has ${text.length} characters, over the limit of $limit", text.length <= limit)
            }
        }
    }

    @Test
    fun `the current versionCode has a changelog, and every changelog is within the store limit`() {
        val enUs = Repo.file("fastlane/metadata/android/en-US/changelogs/$versionCode.txt")
        assertTrue("${enUs.path} is missing: add what changed in this release", enUs.isFile)
        for (changelog in locales.flatMap { it.resolve("changelogs").listFiles().orEmpty().toList() }) {
            val text = shown(changelog)
            assertTrue("${changelog.path} is empty", text.isNotBlank())
            assertTrue(
                "${changelog.path} has ${text.length} characters, over the limit of $changelogLimit",
                text.length <= changelogLimit,
            )
        }
    }

    @Test
    fun `the icon, feature graphic and phone screenshots are PNGs of the sizes the stores take`() {
        val images = Repo.file("fastlane/metadata/android/en-US/images")
        fun size(file: File): Pair<Int, Int> {
            val image = checkNotNull(ImageIO.read(file)) { "${file.path} is not a readable image" }
            return image.width to image.height
        }
        assertEquals(512 to 512, size(images.resolve("icon.png")))
        assertEquals(1024 to 500, size(images.resolve("featureGraphic.png")))
        val screenshots = images.resolve("phoneScreenshots").listFiles { file -> file.extension == "png" }.orEmpty()
        assertTrue("${images.path}/phoneScreenshots has no PNG", screenshots.isNotEmpty())
        for (screenshot in screenshots) {
            val (width, height) = size(screenshot)
            // Google Play: each side 320 to 3840 px, the long side at most twice the short one.
            assertTrue("${screenshot.path} is ${width}x$height", minOf(width, height) >= 320 && maxOf(width, height) <= 3840)
            assertTrue("${screenshot.path} is ${width}x$height", maxOf(width, height) <= 2 * minOf(width, height))
        }
    }
}
