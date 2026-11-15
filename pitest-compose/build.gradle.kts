import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// A PIT plugin for app's mutation run (#412; docs/testing.md#mutation-testing): it filters the mutants of the code the
// Compose compiler adds to every composable, which no test can kill and which took most of the run's time. app puts it
// on PIT's classpath; nothing ships it.
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
    checkTestSources = true
}

dependencies {
    // PIT itself provides its API when it loads the plugin.
    compileOnly(libs.pitest.entry)
    testImplementation(libs.pitest.entry)
    testImplementation(libs.junit)
}
