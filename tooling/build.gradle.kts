import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Repository tooling tests: they read .github/ and run scripts/ so a change that weakens a workflow or breaks a CI
// script fails `./gradlew test`. The module has no main sources.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
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
    for (dir in listOf(".github", "scripts")) {
        inputs.dir(repoRoot.dir(dir))
            .withPropertyName("repo-$dir")
            .withPathSensitivity(PathSensitivity.RELATIVE)
    }
}
