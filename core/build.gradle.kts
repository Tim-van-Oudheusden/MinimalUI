import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Pure Kotlin/JVM: launcher logic that needs no Android framework, tested with plain JUnit.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kover)
    alias(libs.plugins.android.lint)
    alias(libs.plugins.pitest.jvm)
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

// Mutation testing (#412): `./gradlew :core:pitest` changes core's compiled classes one mutant at a time and runs the
// tests against each, writing the survivors to build/reports/pitest/. Weekly and report-only for now
// (docs/testing.md#mutation-testing), so `check` leaves it out.
pitest {
    pitestVersion.set(libs.versions.pitest.get())
    targetClasses.set(setOf("dev.buildwithtim.minimalui.core.*"))
    // GitHub's standard Linux runner has 4 CPUs.
    threads.set(4)
    outputFormats.set(setOf("XML", "HTML"))
    // One report directory per module, replaced by each run, so CI uploads a fixed path.
    timestampedReports.set(false)
    failWhenNoMutations.set(false)
}
