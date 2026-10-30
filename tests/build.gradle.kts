import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Repository tooling tests: they read .github/ and run scripts/ so a change that weakens a workflow or breaks a CI
// script fails `./gradlew test`. The module has no main sources.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.android.lint)
}

java {
    sourceCompatibility = JavaVersion.toVersion(libs.versions.jvmTarget.get())
    targetCompatibility = JavaVersion.toVersion(libs.versions.jvmTarget.get())
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.fromTarget(libs.versions.jvmTarget.get()))
    }
}

lint {
    abortOnError = true
    warningsAsErrors = true
    checkAllWarnings = true
    // The module has only test sources, which lint skips unless told otherwise.
    checkTestSources = true
}

dependencies {
    testImplementation(libs.junit)
}

val repoRoot = rootProject.layout.projectDirectory

tasks.test {
    systemProperty("repo.root", repoRoot.asFile.absolutePath)
    // The files under test live outside this module; declare them so an edit reruns the tests instead of reusing a
    // cached result.
    for (dir in listOf(".github", "scripts", "docs", "fastlane")) {
        inputs
            .dir(repoRoot.dir(dir))
            .withPropertyName("repo-$dir")
            .withPathSensitivity(PathSensitivity.RELATIVE)
    }
    // FastlaneMetadataTest reads versionCode from app/build.gradle.kts; LintConfigTest, TestTimeoutConfigTest and
    // CompilerWarningsConfigTest read the build script of every module in settings.gradle.kts, and the last two the
    // root script's test timeout and compiler flags. The modules come from the build itself, so a module added to
    // settings.gradle.kts is an input too (#245).
    inputs.file(repoRoot.file("settings.gradle.kts")).withPropertyName("repo-settings")
    inputs.file(repoRoot.file("build.gradle.kts")).withPropertyName("repo-root-build")
    for (module in rootProject.childProjects.keys) {
        inputs.file(repoRoot.file("$module/build.gradle.kts")).withPropertyName("repo-$module-build")
    }
    // CodeownersTest compares what CODEOWNERS and the risk rules select among the tracked files, so a file added or
    // removed anywhere in the repository can change its result.
    inputs
        .property(
            "repo-tracked-files",
            providers
                .exec {
                    workingDir(repoRoot.asFile)
                    commandLine("git", "ls-files")
                }.standardOutput.asText,
        )
}
