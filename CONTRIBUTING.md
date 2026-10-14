# Contributing

Thanks for helping out. This guide covers how to get set up, find work, and ship changes through review.

Stack: Kotlin, Jetpack Compose, Gradle (Kotlin DSL with a version catalog), Android lint, JUnit.

## Getting started

You need:

- JDK 21 (Gradle runs on it; e.g. Temurin 21; on Linux, see [below](#installing-jdk-21-on-linux))
- The Android SDK with the platform named by `compileSdk` in `app/build.gradle.kts`. Android Studio installs it; from a
  terminal, use [Android CLI](https://developer.android.com/tools/agents/android-cli) (see below). Point Gradle at the SDK
  with `ANDROID_HOME` or a `local.properties` file (`sdk.dir=...`, gitignored)
- A device or emulator for UI work; a physical keyboard phone is best (no phone? see
  [Running it in an emulator on Linux](#running-it-in-an-emulator-on-linux))

Everything else, Gradle included, is fetched by the wrapper:

```bash
./gradlew assembleDebug                  # build app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug                   # install on the connected device
```

Every CI run uploads the debug APK as the `minimalui-debug-apk` artifact, so a build can be tried on a phone without a
local toolchain.

### Installing JDK 21 on Linux

Your distribution's OpenJDK 21 package works where it has one. Otherwise use Temurin's tarball, which needs no root
and suits immutable distributions such as Fedora Atomic or Bazzite:

```bash
mkdir -p ~/.jdks
curl -fsSL https://api.adoptium.net/v3/binary/latest/21/ga/linux/x64/jdk/hotspot/normal/eclipse | tar -xz -C ~/.jdks
export JAVA_HOME="$(echo ~/.jdks/jdk-21*)"   # add to ~/.bashrc to keep it
```

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

To skip the local SDK install, use the sandbox image instead: `Dockerfile.sandbox` (JDK 21, Android SDK, `git`, `jq`,
`gh`, `bd`) is also what `.devcontainer/devcontainer.json` builds, so VS Code "Reopen in Container" and a plain
`podman build -f Dockerfile.sandbox -t minimalui-sandbox .` give the same environment. It runs the quality gates; see
[docs/testing.md](docs/testing.md#agent-sandbox-what-runs-where) for what needs a real device.

### Running it in an emulator on Linux

With JDK 21 and the SDK installed as above, this takes a fresh clone to MinimalUI as the home screen of an emulated
phone. The emulator needs KVM: `emulator -accel-check` should say `KVM ... is installed and usable`. Put the SDK's
tools on `PATH` first:

```bash
export ANDROID_HOME=~/Android/Sdk                                    # the `sdk:` path `android info` prints
export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH"

android sdk install emulator          # the emulator itself
android emulator create medium_phone  # a phone AVD named medium_phone, hardware keyboard on; fetches its system image
android emulator start medium_phone   # opens the emulator window, returns once Android has booted
./gradlew installDebug                # builds and installs the debug APK
adb shell cmd package set-home-activity dev.buildwithtim.minimalui/.MainActivity
adb shell input keyevent KEYCODE_HOME
```

`android emulator start` prints the serial it started as (`emulator-5554` for the first one). With more than one
device or emulator attached, pick one for both Gradle and `adb` with `export ANDROID_SERIAL=emulator-5554`. After a
code change, rerun `./gradlew installDebug`; the home setting stays.

The emulator window passes your keyboard through, so arrows, Enter, Esc, Backspace and letters work as on a keyboard
phone, and the mouse stands in for touch. A PC keyboard has no phone keys, so the emulator maps them to shortcuts:

| Phone key  | In the emulator window      |
| ---------- | --------------------------- |
| Menu (`☰`) | Ctrl+M                      |
| Home       | Ctrl+H                      |
| Back       | Ctrl+Backspace (or Esc)     |

## Project layout

```text
app/                        Android application: manifest, activity, Compose UI, resources
  src/main/kotlin/          Kotlin sources, package dev.buildwithtim.minimalui (ui/ holds composables)
  src/main/res/             strings, theme, adaptive launcher icon, backup rules
  src/test/kotlin/          Robolectric + Compose UI tests, run on the JVM without an emulator
core/                       pure Kotlin/JVM logic (no Android dependencies), package dev.buildwithtim.minimalui.core
  src/test/kotlin/          JUnit tests, run on the JVM without an emulator
docs/                       usage guide, testing notes, brand assets, research, metrics, reflections
fastlane/metadata/android/  store listing: title, descriptions, changelogs, icon, feature graphic, screenshots
gradle/libs.versions.toml   every dependency and plugin version
gradle/wrapper/             Gradle wrapper (pinned version and checksum)
prompts/                    reusable agent prompts (code review, PR body)
scripts/                    CI helper scripts
tooling/                    JUnit tests over .github/, scripts/ and fastlane/ (workflow invariants, CI scripts, listing)
```

**Where code goes.** Anything that can be expressed without the Android framework (sorting, filtering, key-to-action
mapping, config parsing) goes in `core`, so it is tested with plain JUnit and no emulator. `app` stays a thin shell that
reads from Android APIs and renders with Compose.

**Where a test goes.** Next to the module it covers, in the same package: a test for
`core/src/main/kotlin/dev/buildwithtim/minimalui/core/Foo.kt` goes in `core/src/test/kotlin/dev/buildwithtim/minimalui/core/FooTest.kt`.

**App tests.** What has to stay in `app` (focus, key handling, intents, the package manager) is tested with Robolectric
and `createComposeRule()` in `app/src/test`, inside `./gradlew test`. Drive the UI the way a user does: keys through
`performKeyInput`, or through the activity's `dispatchKeyEvent` for keys no row consumes, and assert on what shows or
what was started. Robolectric emulates SDK 35 (`app/src/test/resources/robolectric.properties`), because it cannot yet
emulate the app's `targetSdk`. Mark a test `@Config(sdk = [28, 35])` when it covers a version branch.

**Tests for CI and scripts.** `tooling` has no main sources: its tests read `.github/` and `fastlane/` and run
`scripts/` the way CI does, so they run with `./gradlew test`. `WorkflowInvariantsTest` holds the reviewed
`GITHUB_TOKEN` scopes of every workflow and checks that every job has `timeout-minutes`, every action is pinned to a
commit SHA, no `run:` interpolates event payload (pass it through `env:`), and no `pull_request_target` workflow checks
out code. Adding a workflow means adding its scopes there. A test for `scripts/foo.sh` goes in
`tooling/src/test/kotlin/dev/buildwithtim/minimalui/tooling/` and runs it with `runScript`. `FastlaneMetadataTest`
holds the store listing to the stores' limits and requires a changelog for the current `versionCode`.

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
checks pass. Pull requests merge with a merge commit only (no squash or rebase), so each issue's single commit lands on
`main` unchanged, and the head branch is deleted on merge.

[conventional-commits]: https://www.conventionalcommits.org/

Every pull request is classified into a low, medium or high risk tier from the paths it changes (the informational
`Risk tier` check); [`docs/risk-tiers.md`](docs/risk-tiers.md) lists the paths in each tier and the review each expects.

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

The `main` ruleset also requires the **Unit-test coverage floors** check (`.github/workflows/coverage-gate.yml`): it
runs `./gradlew :<module>:koverXmlReport` for every module in `.github/auto-qa-tuning.json` and fails the PR if a
module's function or line coverage drops below its floor there. Check locally with
`./gradlew :core:koverXmlReport && scripts/coverage-check.sh core <functions> <lines>`; see
[`docs/testing.md`](docs/testing.md#coverage-floors).

### Applying review suggestions

Comment `/apply-suggestions` on a pull request to commit every ```` ```suggestion ```` block that reviewers with write
access (human or Claude) left on its current diff. `.github/workflows/auto-review.yml` commits them to the PR branch as
`chore(review): apply review suggestions (#N)` and posts a report comment listing what was applied and what was skipped:
outdated suggestions, ones on the base side of the diff, ranges past the end of the file or overlapping an already
applied range, comments holding more than one suggestion block, paths outside the checkout, and files under
`.github/workflows/` (the workflow token cannot push those, and one such edit would sink the whole push). A suggestion
block ends only at a fence that starts a line. Only `OWNER`/`MEMBER`/`COLLABORATOR` comments trigger it and only their
suggestions count; fork pull requests are refused, and so is a head committed after the command comment (comment again
once you have looked at it). Resolved or declined review threads are not detected (the REST API does not expose thread
state): delete or edit a suggestion you do not want before commenting. The apply script
(`scripts/apply-review-suggestions.sh`) runs from the default branch, never from the PR. Pushes made with the workflow
token start no CI run, so push again or re-run CI before merging.

### Handing work to Claude Code

`.github/workflows/claude.yml` runs [Claude Code](https://github.com/anthropics/claude-code-action) in CI. Label an
issue `ai-fix-requested`, or mention `@claude` in an issue comment, PR review, or review comment, and Claude works the
request on a `claude/*` branch and opens a pull request; it never pushes to `main`. On a pull request, `@claude review
this` asks for a review. Mentions only count from `OWNER`/`MEMBER`/`COLLABORATOR`, the label can only be applied by
triagers, and fork pull requests are refused, so outside contributors cannot start or steer a run. Claude may run only
the quality gates (`./gradlew test`, `lint`, `assembleDebug`, `check`, and the module test tasks). Its PRs go through the
same checks and human review as any other. The workflow needs the `CLAUDE_CODE_OAUTH_TOKEN` repo secret and the Claude
GitHub App.

For reviews, point Claude at the shared rubric in [`prompts/code-review.md`](prompts/code-review.md), e.g.
`@claude review this PR using prompts/code-review.md against issue #12`; [`prompts/`](prompts/README.md) also holds the
PR-body template.

## Releasing

`versionName` in `app/build.gradle.kts` decides what gets released. When `main` gets a `versionName` that has no GitHub
release yet, `.github/workflows/release.yml` runs the unit tests and lint and builds the release APK signed with the
MinimalUI release key. It then tags that commit `v<versionName>` and publishes a GitHub release with
`minimalui-v<versionName>.apk` and generated notes attached. A version with a `-` suffix (`0.2.0-beta.1`) is marked as
a pre-release. Every release is signed with the same key, so it installs over the previous one and keeps the user's
settings.

### The release key

The maintainer creates the key once, outside the repository, and keeps a backup of it and its password. Android
refuses an update signed with a different key, so losing it means every user has to uninstall to move to a new one.

```bash
keytool -genkeypair -keystore ~/minimalui-release.jks -alias minimalui \
  -keyalg RSA -keysize 4096 -validity 10000 -dname "CN=MinimalUI"       # asks for a password
base64 -w0 ~/minimalui-release.jks | gh secret set RELEASE_KEYSTORE_BASE64
gh secret set RELEASE_KEY_ALIAS --body minimalui
gh secret set RELEASE_KEYSTORE_PASSWORD   # asks for the keytool password
gh secret set RELEASE_KEY_PASSWORD        # the same password: keytool's PKCS12 keystores have only one
```

If a secret is missing, the workflow names it and stops before building, so nothing is published. Locally,
`./gradlew assembleRelease` builds an unsigned `app-release-unsigned.apk` unless `RELEASE_KEYSTORE_FILE` (the keystore's
path), `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS` and `RELEASE_KEY_PASSWORD` are set, as the workflow sets them.

### Cutting a release

1. Open a pull request that raises `versionName` (Settings → About shows it) and `versionCode` in
   `app/build.gradle.kts`. Android refuses to install an update with a lower `versionCode`. In the same pull request,
   add `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`: what changed for users, in at most 500
   characters. `./gradlew test` fails without it (`FastlaneMetadataTest`).
2. Merge it. The `Release` workflow under **Actions** publishes the release, which then appears under **Releases**.

A merge that leaves `versionName` alone, or sets one that is already released, publishes nothing. To retry a failed
release, run the workflow again on `main`: `gh workflow run release.yml`, or **Actions → Release → Run workflow**. If a
`v<versionName>` tag already exists on another commit, the workflow refuses to release; raise `versionName`, or delete
the tag with `git push origin :refs/tags/v<versionName>` and run it again.

## Code style

- Kotlin official code style (`kotlin.code.style=official`), 4-space indent, max line length 120 (`.editorconfig`).
- Prefer immutable data (`val`, `data class`, read-only collections) and pure functions in `core`.
- No `!!`; model absence with nullable types or sealed results.
- Compose: stateless composables that take state and callbacks; every interactive element must be reachable and usable
  by keyboard focus.

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

The security side of these layers (trust boundaries, secrets, supply chain, review focus, reporting) is set out in
[`docs/security/SECURITY-AI.md`](docs/security/SECURITY-AI.md).

How agent work fares is measured, not guessed: the weekly `PR metrics` workflow reports how many agent-authored PRs
(`claude/*` branches and the `hive-forge-app` GitHub App) were merged vs. closed unmerged. Every metric, its cadence,
where it is published and how to reproduce it locally is catalogued in [`docs/metrics/README.md`](docs/metrics/README.md).
