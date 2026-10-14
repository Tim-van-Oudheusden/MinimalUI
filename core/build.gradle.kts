import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Pure Kotlin/JVM: launcher logic that needs no Android framework, tested with plain JUnit.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kover)
    alias(libs.plugins.android.lint)
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
