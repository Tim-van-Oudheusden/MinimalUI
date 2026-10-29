import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// End-to-end tests (#236): the debug APK on a real device or emulator, driven over adb the way a user drives it, with
// keys, and checked by what the screen shows (uiautomator) and which app is in front. They need a device, so they run
// through `./gradlew :e2e:e2eTest`, never under `test` or `check`; `check` still compiles, lints and formats them.
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
        // Compiler warnings fail the build like Android Lint warnings do (#133).
        allWarningsAsErrors.set(true)
        extraWarnings.set(true)
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

// Without a device every test would fail, so `./gradlew test` (and `check`) leaves this module's tests out.
tasks.test {
    enabled = false
}

tasks.check {
    dependsOn(tasks.testClasses)
}

val debugApk =
    rootProject.layout.projectDirectory
        .file("app/build/outputs/apk/debug/app-debug.apk")
        .asFile.absolutePath

tasks.register<Test>("e2eTest") {
    description = "Runs the end-to-end tests on the device adb picks (ANDROID_SERIAL chooses one of several)."
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    testClassesDirs =
        sourceSets.test
            .get()
            .output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    dependsOn(":app:assembleDebug")
    systemProperty("minimalui.apk", debugApk)
    // The device is not an input Gradle can see, so a passing run is never reused, from this build or the cache.
    outputs.upToDateWhen { false }
    outputs.doNotCacheIf("the result depends on the device") { true }
    // Each test's outcome in the build output, and a failure's whole message: it names the screen MinimalUI showed.
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = TestExceptionFormat.FULL
    }
}
