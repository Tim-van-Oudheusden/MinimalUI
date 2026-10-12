# Contributing

Thanks for helping out. This guide covers how to get set up, find work, and ship changes through review.

Stack: Kotlin, Jetpack Compose, Gradle (Kotlin DSL with a version catalog), Android lint, JUnit.

## Getting started

You need:

- JDK 21 (Gradle runs on it; e.g. Temurin 21)
- The Android SDK with the platform named by `compileSdk` in `app/build.gradle.kts`. Android Studio installs it; from a
  terminal, use [Android CLI](https://developer.android.com/tools/agents/android-cli) (see below). Point Gradle at the SDK
  with `ANDROID_HOME` or a `local.properties` file (`sdk.dir=...`, gitignored)
- A device or emulator for UI work; a physical keyboard phone is best

Everything else, Gradle included, is fetched by the wrapper:

```bash
./gradlew assembleDebug                  # build app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug                   # install on the connected device
```

Every CI run uploads the debug APK as the `minimalui-debug-apk` artifact, so a build can be tried on a phone without a
local toolchain.

### Installing the SDK from a terminal

Android CLI (`android`) replaces the deprecated `sdkmanager`. Install it with the command for your OS from the
[download page](https://developer.android.com/tools/agents/android-cli/download) (Linux shown), then install the
platform tools and the `compileSdk` platform:

```bash
curl -fsSL https://dl.google.com/android/cli/latest/linux_x86_64/install.sh | bash   # Linux, per-user install
android update                                              # move to the latest Android CLI
android info                                                # shows the SDK path it manages
android sdk install platform-tools platforms/android-37.0   # keep in step with compileSdk
```

`android sdk install` accepts the SDK licence, so AGP downloads the build tools it needs on the first build. Use
`android --sdk=<path> sdk install ...` to target an SDK other than the one `android info` reports.

## Project layout

```text
app/                        Android application: manifest, activity, Compose UI, resources
  src/main/kotlin/          Kotlin sources, package dev.buildwithtim.minimalui (ui/ holds composables)
  src/main/res/             strings, theme, adaptive launcher icon, backup rules
core/                       pure Kotlin/JVM logic (no Android dependencies), package dev.buildwithtim.minimalui.core
  src/test/kotlin/          JUnit tests, run on the JVM without an emulator
gradle/libs.versions.toml   every dependency and plugin version
gradle/wrapper/             Gradle wrapper (pinned version and checksum)
scripts/                    CI helper scripts
```

**Where code goes.** Anything that can be expressed without the Android framework (sorting, filtering, key-to-action
mapping, config parsing) goes in `core`, so it is tested with plain JUnit and no emulator. `app` stays a thin shell that
reads from Android APIs and renders with Compose.

**Where a test goes.** Next to the module it covers, in the same package: a test for
`core/src/main/kotlin/dev/buildwithtim/minimalui/core/Foo.kt` goes in `core/src/test/kotlin/dev/buildwithtim/minimalui/core/FooTest.kt`.

## Finding and claiming work

- GitHub issues are the **primary source of truth**. Browse open issues and assign one to yourself before starting.
- Issues are mirrored into the local `bd` (beads) tracker; run `bd ready` to see available work.

We favour small, focused changes tied to a tracked issue.

## Development workflow

1. Create a branch off `main`: `<type>/<number>-<slug>`.
2. Work the issue **test-first** (see below).
3. Commit with [Conventional Commits][conventional-commits] messages, e.g. `feat(launcher): add app search`,
   `fix(input): handle Fn key`, `docs(agents): clarify gates`.
4. Open a pull request and fill in the template (link the issue with `Fixes #<number>`, paste evidence).

`main` is protected by the ruleset in [`.github/rulesets/main.json`](.github/rulesets/main.json): no direct pushes,
force-pushes or deletion; changes land only through pull requests whose `PR review rubric` and `Build, lint, test`
checks pass.

[conventional-commits]: https://www.conventionalcommits.org/

### Test-driven development

We require TDD. One test at a time:

1. Write a failing test for the behaviour you're adding or fixing.
2. Confirm it fails for the expected reason.
3. Make it pass with the smallest change.
4. Refactor only as needed.

Assert on observable outputs, return values, and side effects — never on internal state or private methods. Never
weaken or skip a failing test to make it pass; fix the implementation.

## Quality gates

Run these before opening a PR (CI runs the same on every PR):

| Command                    | What it runs                                  |
| -------------------------- | --------------------------------------------- |
| `./gradlew test`           | Unit tests for every module                   |
| `./gradlew lint`           | Android lint, warnings as errors              |
| `./gradlew assembleDebug`  | Debug APK build                               |

## Code style

- Kotlin official code style (`kotlin.code.style=official`), 4-space indent, max line length 120 (`.editorconfig`).
- Prefer immutable data (`val`, `data class`, read-only collections) and pure functions in `core`.
- No `!!`; model absence with nullable types or sealed results.
- Compose: stateless composables that take state and callbacks; every interactive element must be reachable and usable
  by keyboard focus.

## Applying review suggestions

Reviewers with write access (human or Claude) can leave ```` ```suggestion ```` blocks on a pull request's diff. To
commit them all at once, a maintainer comments `/apply-suggestions` on the PR. `.github/workflows/auto-review.yml` then
runs `scripts/apply-review-suggestions.sh`, commits the result to the PR branch as
`chore(review): apply review suggestions (#<number>)`, and replies with a report of what was applied and skipped.

- **Who:** only comments from `OWNER`, `MEMBER` or `COLLABORATOR` trigger it, and only their suggestions are applied.
- **Refused:** pull requests from forks, and a head committed after the `/apply-suggestions` comment. Comment again to
  apply to the newer head.
- **Skipped, and listed in the report:** outdated suggestions; comments on the base (left) side of the diff; ranges
  that overlap a suggestion already applied in the same run; paths outside the checkout or missing from it; comments
  with more than one suggestion block; and files under `.github/workflows/`, which the workflow's token cannot push.
- **Not detected:** whether a review thread was resolved, or the suggestion declined in discussion. To keep a declined
  suggestion out, delete or edit its comment before running the command.
- **Safety:** the script is checked out from `main`, never from the PR, and the PR checkout keeps no credentials. If
  the branch moves while the job runs, the push fails rather than applying suggestions to lines they weren't written
  for.
- Pushes made with `GITHUB_TOKEN` start no new workflow runs, so CI re-runs on the PR's next push or a manual re-run.

`scripts/apply-review-suggestions.test.sh` covers the script.

## Dependencies

- Ask before adding, upgrading, or removing a dependency or plugin; state coordinates, version, and rationale.
- Versions live only in `gradle/libs.versions.toml`.
- Prefer the platform, AndroidX and the Kotlin stdlib over new libraries.

## Agent safety layers

AI agents working in this repo are bounded by several layers:

1. **Instructions** — `AGENTS.md` (authoritative), `CLAUDE.md`, `.github/copilot-instructions.md` and
   `.cursor/rules/agent-guidance.mdc` state the rules.
2. **Project permissions** — `.claude/settings.json` pre-approves the quality gates, asks before dependency and
   history-rewriting commands, and denies reading secrets (`.env*`, keystores), force-pushes and `--no-verify` commits.
3. **Personal overrides** — `.claude/settings.local.json` (gitignored). Deny rules from the project file still apply.
4. **Structural gates** — the ruleset on `main` and CI must pass before merge, regardless of who wrote the change.
