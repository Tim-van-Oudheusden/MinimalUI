// Build-tool transitives with published advisories (#147): AGP and Kover put them on the plugin classpath, and Android
// Lint on every module's androidLintTool, at vulnerable versions. These constraints raise them to the patched releases
// in gradle/libs.versions.toml's build-tool-floors bundle; a tool that later brings a newer version still wins. Neither
// classpath is packaged into the APK.
buildscript {
    dependencies {
        constraints {
            libs.bundles.build.tool.floors
                .get()
                .forEach { add("classpath", it) }
        }
    }
}

// Plugin versions come from gradle/libs.versions.toml; modules apply what they need.
plugins {
    // Gives the root project a `check` task, so `./gradlew check` runs spotlessCheck along with every module's checks.
    base
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.android.lint) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kover) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.pitest.jvm) apply false
    // Here with AGP, not only in :app: the plugin depends on AGP 8.5.1, which resolves to the catalog's AGP on this
    // classpath instead of loading a second AGP for one module.
    alias(libs.plugins.pitest.android) apply false
    alias(libs.plugins.spotless)
}

// Static analysis for every Kotlin module (#135): detekt's default rules plus the overrides in config/detekt/detekt.yml
// (the plugin's default config location). `check` runs two kinds of detekt task, and either fails on a finding. The
// plain `detekt` task covers src/main and src/test with no classpath, so it skips every rule that needs type analysis.
// `detektMain` and `detektTest` have each compilation's classpath and run every rule, but the plugin leaves them out of
// `check`, so this adds them (#356). In an Android module they group the per-variant tasks (`detektDebug`, ...).
subprojects {
    apply(plugin = "dev.detekt")
    extensions.configure<dev.detekt.gradle.extensions.DetektExtension> {
        buildUponDefaultConfig = true
    }
    // By name, and matched as it appears: a module applies the plugins that add these tasks after this block has run.
    tasks.matching { it.name == "check" }.configureEach {
        dependsOn("detektMain", "detektTest")
    }
}

// The same floors (#147) for Android Lint, which AGP and the standalone lint plugin resolve per module.
subprojects {
    val constraints = dependencies.constraints
    configurations.matching { it.name == "androidLintTool" }.configureEach {
        libs.bundles.build.tool.floors
            .get()
            .forEach { constraints.add(name, it) }
    }
}

// No test can hang a build (#216): a test stuck in a loop fails its task at this limit instead of running until someone
// kills it. Far above a normal run: :app:testDebugUnitTest takes about 20 s locally, CI's whole gate job about 3 min.
subprojects {
    tasks.withType<Test>().configureEach {
        timeout.set(java.time.Duration.ofMinutes(10))
    }
}

// Compiler warnings fail the build like Android Lint warnings do (#133), extra warnings included, in every module
// (#276). The task type covers the kotlin.jvm modules and AGP's built-in Kotlin alike.
subprojects {
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask<*>>().configureEach {
        compilerOptions {
            allWarningsAsErrors.set(true)
            extraWarnings.set(true)
        }
    }
}

// Java compilation one task at a time (#339): CodeQL's autobuild intermittently failed a JavaCompile task with no
// compiler output, 3 hits in 35 runs on CodeQL CLI 2.27.4. With this limit, 41 runs on the same CLI had none. If the
// failure returns, concurrency was not the cause; build CodeQL manually on JDK 21 (advanced setup) instead. Only
// generated BuildConfig is compiled, so serializing costs next to nothing. The service holds no state; it exists to be
// limited.
abstract class JavacLimit : BuildService<BuildServiceParameters.None>

val javacLimit =
    gradle.sharedServices.registerIfAbsent("javacLimit", JavacLimit::class) {
        maxParallelUsages.set(1)
    }

subprojects {
    tasks.withType<JavaCompile>().configureEach {
        usesService(javacLimit)
    }
}

// Formatting for the whole repository: `./gradlew spotlessApply` fixes it, `spotlessCheck` (part of `check`) fails on
// it. ktlint takes its style from .editorconfig; build output, tool state and local checkouts are not ours to format.
val notOurs = listOf("**/build/**", ".gradle/**", ".kotlin/**", ".beads/**", ".tmp/**", "worktrees/**")

spotless {
    kotlin {
        target("**/*.kt")
        targetExclude(notOurs)
        ktlint(libs.versions.ktlint.get()).setEditorConfigPath(rootProject.file(".editorconfig"))
    }
    kotlinGradle {
        target("**/*.gradle.kts")
        targetExclude(notOurs)
        ktlint(libs.versions.ktlint.get()).setEditorConfigPath(rootProject.file(".editorconfig"))
    }
    format("text") {
        target(
            "**/*.yml",
            "**/*.yaml",
            "**/*.toml",
            "**/*.json",
            "**/*.sh",
            "**/*.properties",
            "**/*.txt",
            "**/.gitignore",
            ".gitattributes",
            ".editorconfig",
        )
        targetExclude(notOurs)
        trimTrailingWhitespace()
        endWithNewline()
    }
    // Two trailing spaces are a Markdown line break, so only the final newline is enforced (.editorconfig agrees).
    format("markdown") {
        target("**/*.md")
        targetExclude(notOurs)
        endWithNewline()
    }
}
