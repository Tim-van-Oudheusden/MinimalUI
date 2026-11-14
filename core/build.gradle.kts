import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Pure Kotlin/JVM: launcher logic that needs no Android framework, tested with plain JUnit.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kover)
    alias(libs.plugins.android.lint)
    alias(libs.plugins.pitest)
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
    // Tests are code we maintain too; lint them like the main sources.
    checkTestSources = true
}

dependencies {
    testImplementation(libs.junit)
}

// Mutation testing (#412): `./gradlew :core:pitest` changes the compiled code one mutant at a time and runs the unit
// tests against each, writing build/reports/pitest/mutations.xml. A mutant the tests let through is a line nothing
// asserts; scripts/mutation-check.sh fails on one that config/mutation/core.txt does not list. Not part of `check`: it
// is meant for a weekly scheduled run (docs/testing.md#mutation-testing).
pitest {
    pitestVersion.set(libs.versions.pitest)
    targetClasses.set(listOf("dev.buildwithtim.minimalui.core.*"))
    threads.set(Runtime.getRuntime().availableProcessors())
    outputFormats.set(listOf("XML", "HTML"))
    // One fixed report directory, the path the check script reads.
    timestampedReports.set(false)
}
