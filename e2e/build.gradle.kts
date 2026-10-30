import com.android.build.api.dsl.ManagedVirtualDevice

// End-to-end tests (#236): UiAutomator drives the installed debug app on an emulator from outside its process, the way
// a user with a keyboard does, across app boundaries the Robolectric tests in app/src/test cannot cross. `./gradlew
// :e2e:atdApi35DebugAndroidTest` starts the emulator through Gradle Managed Devices, so it needs KVM but no device.
plugins {
    alias(libs.plugins.android.test)
}

android {
    namespace = "dev.buildwithtim.minimalui.e2e"
    compileSdk = 37
    targetProjectPath = ":app"
    // The tests run in their own process, not the app's: they see it only as a user does, and their androidx.test
    // libraries never replace the app's own AndroidX classes at runtime.
    experimentalProperties["android.experimental.self-instrumenting"] = true

    defaultConfig {
        minSdk = 28
        targetSdk = 37
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.toVersion(libs.versions.jvmTarget.get())
        targetCompatibility = JavaVersion.toVersion(libs.versions.jvmTarget.get())
    }

    testOptions {
        managedDevices {
            allDevices {
                // An Automated Test Device image: AOSP without the apps and services tests do not need, so it boots
                // faster and steadier on CI runners.
                create<ManagedVirtualDevice>("atdApi35") {
                    device = "Pixel 5"
                    apiLevel = 35
                    systemImageSource = "aosp-atd"
                    // x86_64 runs natively on the x86_64 hosts and runners with KVM; AGP 10 defaults to arm64-v8a.
                    testedAbi = "x86_64"
                }
            }
        }
    }
}

dependencies {
    implementation(libs.junit)
    implementation(libs.androidx.test.runner)
    implementation(libs.androidx.test.uiautomator)
}
