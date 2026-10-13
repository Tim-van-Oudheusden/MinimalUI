plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kover)
}

// The commit the APK was built from, shown in Settings > About (#73). "unknown" where git or the repository is
// missing (a source archive, say), so the build never fails for it.
val buildCommit: String = runCatching {
    providers.exec {
        commandLine("git", "rev-parse", "--short", "HEAD")
        isIgnoreExitValue = true
    }.standardOutput.asText.get().trim()
}.getOrNull().orEmpty().ifEmpty { "unknown" }

android {
    namespace = "dev.buildwithtim.minimalui"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.buildwithtim.minimalui"
        // Android 9: the oldest release shipped on the targeted keyboard phones (Unihertz Titan).
        minSdk = 28
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField("String", "BUILD_COMMIT", "\"$buildCommit\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.toVersion(libs.versions.jvmTarget.get())
        targetCompatibility = JavaVersion.toVersion(libs.versions.jvmTarget.get())
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            // Robolectric reads the merged manifest and resources, so app tests run on the JVM without a device.
            isIncludeAndroidResources = true
        }
    }

    lint {
        abortOnError = true
        warningsAsErrors = true
        // Version-freshness checks change verdict whenever a library ships; Dependabot owns updates.
        disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion", "OldTargetApi")
    }
}

dependencies {
    implementation(project(":core"))
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    // Declares the empty activity that createComposeRule() hosts content in; debug builds only.
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
