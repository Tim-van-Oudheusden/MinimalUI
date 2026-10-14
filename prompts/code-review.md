# Code Review Prompt

Review the diff between `<FIXED_POINT>` and `<HEAD>` in the `MinimalUI` repository
(Android launcher in Kotlin: `app` Jetpack Compose UI, `core` pure Kotlin/JVM
logic, Gradle with a version catalog).

Check two axes and report them separately:

## Standards
- TDD compliance: tests were written for the behaviour being changed, assert
  observable outputs (not private state or composable internals), and the full
  suite passes (`./gradlew test`).
- Logic that needs no Android framework lives in `core` with plain JUnit tests;
  `app` stays a thin shell.
- No test weakened, deleted or skipped (`@Ignore`) to make CI pass; no
  `@Suppress` added to silence failures or lint.
- No new dependency or plugin without approval; every version in
  `gradle/libs.versions.toml`, never inline in a build file.
- Keyboard first: every changed screen is fully usable with a hardware keyboard
  and D-pad, without touch.
- Gates green: `./gradlew check assembleDebug` (unit tests, lint, formatting).
- Conventional Commits, one commit per issue, issue number referenced.

## Spec
- Every acceptance criterion named in `<ISSUE>` is implemented and tested.
- No unrequested behaviour (scope creep); no partially implemented criteria.

Quote the offending line and source rule for each finding. Under 400 words.
