# Code Review Prompt

Review the diff between `<FIXED_POINT>` and `<HEAD>` in the `MinimalUI` repository against issue `<ISSUE>`.
MinimalUI is a keyboard-first Android launcher in Kotlin and Jetpack Compose, built with Gradle. `app` is the Android
shell and `core` holds pure Kotlin/JVM logic with JUnit tests.

Check two axes and report them separately.

## Standards

- **TDD evidence:** the diff adds or changes a test for every behaviour it adds or fixes, and the PR shows that test
  failing before the change and passing after it.
- **Behaviour, not implementation:** tests assert return values, emitted state and side effects, not private fields or
  call order.
- **Testable placement:** logic that needs no Android API (sorting, filtering, key-to-action mapping, parsing) lives in
  `core` with a test in the same package under `core/src/test/kotlin/`. `app` stays a thin shell over it.
- **No test tampering:** no test deleted or weakened, no assertion loosened, no `@Ignore`, and no `@Suppress` added to
  silence a failure or a lint warning.
- **Gates green:** `./gradlew test`, `./gradlew lint` (warnings are errors) and `./gradlew assembleDebug`.
- **Dependencies:** no dependency or Gradle plugin added, upgraded or removed without the maintainer's approval in the
  issue or PR. Every version comes from `gradle/libs.versions.toml`, never inline in a `build.gradle.kts`. No major
  upgrade rides along with another change. `gradle/wrapper/gradle-wrapper.jar` is not hand-edited.
- **Keyboard first:** every new or changed screen and control can be reached and used with a hardware keyboard and
  D-pad alone. Interactive Compose elements are focusable and act on key events, not only on touch.
- **Code style:** Kotlin official style, max line length 120, no `!!`, immutable data and pure functions in `core`,
  stateless composables that take state and callbacks.
- **Commits and title:** Conventional Commits, one commit per issue, with the issue number referenced
  (`type(scope): summary (#N)`). The PR body links the issue with `Fixes #N`.
- **Security-sensitive paths:** a change to `.github/workflows/`, `scripts/`, `.claude/settings.json`, `AGENTS.md`,
  `app/src/main/AndroidManifest.xml` or the Gradle build files keeps permissions least-privilege and loosens no agent
  boundary without the maintainer's explicit approval.

## Spec

- Every acceptance criterion in `<ISSUE>` is implemented and covered by a test, or the PR says why it can't be (for
  example a repository setting, or a device-only check).
- Nothing outside the issue's scope is added: no unrequested behaviour, refactor or drive-by change.
- No criterion is half-done without a follow-up issue linked from the PR.

For each finding, quote the offending line, name the rule and where it comes from (`AGENTS.md`, `CONTRIBUTING.md` or the
issue), and say what would satisfy it. Lead with blocking findings. Keep it under 400 words.
