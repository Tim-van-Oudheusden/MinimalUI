import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Repository tooling tests: they read .github/ and run scripts/ so a change that weakens a workflow or breaks a CI
// script fails `./gradlew test`. The module has no main sources.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.toVersion(libs.versions.jvmTarget.get())
    targetCompatibility = JavaVersion.toVersion(libs.versions.jvmTarget.get())
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.fromTarget(libs.versions.jvmTarget.get()))
        // Compiler warnings fail the build like Android Lint warnings do (#133).
        allWarningsAsErrors.set(true)
        extraWarnings.set(true)
    }
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
    // FastlaneMetadataTest reads versionCode from it.
    inputs.file(repoRoot.file("app/build.gradle.kts")).withPropertyName("repo-app-build")
}
