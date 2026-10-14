// Plugin versions come from gradle/libs.versions.toml; modules apply what they need.
plugins {
    // Gives the root project a `check` task, so `./gradlew check` runs spotlessCheck along with every module's checks.
    base
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kover) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.spotless)
}

// Static analysis for every Kotlin module (#135): detekt's default rules plus the overrides in config/detekt/detekt.yml
// (the plugin's default config location). Its `detekt` task covers src/main and src/test, runs under `check` and fails
// on a finding.
subprojects {
    apply(plugin = "dev.detekt")
    extensions.configure<dev.detekt.gradle.extensions.DetektExtension> {
        buildUponDefaultConfig = true
    }
}

// Formatting for the whole repository: `./gradlew spotlessApply` fixes it, `spotlessCheck` (part of `check`) fails on
// it. ktlint takes its style from .editorconfig; build output, tool state and local checkouts are not ours to format.
val notOurs = listOf("**/build/**", ".gradle/**", ".kotlin/**", ".beads/**", ".tmp/**", "worktrees/**")

spotless {
    kotlin {
        target("**/*.kt")
        targetExclude(notOurs)
        ktlint(libs.versions.ktlint.get()).setEditorConfigPath(rootProject.file(".editorconfig"))
    }
    kotlinGradle {
        target("**/*.gradle.kts")
        targetExclude(notOurs)
        ktlint(libs.versions.ktlint.get()).setEditorConfigPath(rootProject.file(".editorconfig"))
    }
    format("text") {
        target(
            "**/*.yml",
            "**/*.yaml",
            "**/*.toml",
            "**/*.json",
            "**/*.sh",
            "**/*.properties",
            "**/*.txt",
            "**/.gitignore",
            ".gitattributes",
            ".editorconfig",
        )
        targetExclude(notOurs)
        trimTrailingWhitespace()
        endWithNewline()
    }
    // Two trailing spaces are a Markdown line break, so only the final newline is enforced (.editorconfig agrees).
    format("markdown") {
        target("**/*.md")
        targetExclude(notOurs)
        endWithNewline()
    }
}
