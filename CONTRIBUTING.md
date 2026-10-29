# Contributing

Thanks for helping out. This guide covers how to get set up, find work, and ship changes through review. All
participation is covered by the [Code of Conduct](CODE_OF_CONDUCT.md).

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

Without a local JDK, build in the agent sandbox instead with `scripts/sandbox-gradlew.sh assembleDebug` (see
[docs/testing.md](docs/testing.md#running-the-gates)) and install from the host:
`adb install -r app/build/outputs/apk/debug/app-debug.apk` in place of `./gradlew installDebug`. The script signs with
your host debug key, so the APK installs over the previous build and the settings stay.

The emulator window passes your keyboard through, so arrows, Enter, Esc, Backspace and letters work as on a keyboard
phone, and the mouse stands in for touch. A PC keyboard has no phone keys, so the emulator maps them to shortcuts:

| Phone key  | In the emulator window      |
| ---------- | --------------------------- |
| Menu (`☰`) | Ctrl+M                      |
| Home       | Ctrl+H                      |
| Back       | Ctrl+Backspace (or Esc)     |

If the emulator misbehaves:

- **It crashes while starting without a window.** On some hosts `emulator -no-window` (the
  `qemu-system-x86_64-headless` binary) segfaults during startup whatever the AVD or `-gpu` mode; seen with emulator
  37.2.12.0. Start it with its window through `android emulator start`, which works on the same hosts.
- **The screen stays black and keys do nothing.** An AVD can finish booting with its display asleep (seen on API 28).
  Wake it and keep it awake: `adb shell input keyevent KEYCODE_WAKEUP && adb shell svc power stayon true`.

## Project layout

```text
app/                        Android application: manifest, activity, Compose UI, resources
  src/main/kotlin/          Kotlin sources, package dev.buildwithtim.minimalui (ui/ holds composables)
  src/main/res/             strings, theme, adaptive launcher icon, backup rules
  src/test/kotlin/          Robolectric + Compose UI tests, run on the JVM without an emulator
core/                       pure Kotlin/JVM logic (no Android dependencies), package dev.buildwithtim.minimalui.core
  src/test/kotlin/          JUnit tests, run on the JVM without an emulator
docs/                       usage guide, testing notes, brand assets, research, metrics, reflections
e2e/                        end-to-end tests: UiAutomator drives the installed debug app on an emulator
  src/main/kotlin/          the tests (a com.android.test module, so they are its main sources)
fastlane/metadata/android/  store listing: title, descriptions, changelogs, icon, feature graphic, screenshots
gradle/libs.versions.toml   every dependency and plugin version
gradle/wrapper/             Gradle wrapper (pinned version and checksum)
prompts/                    reusable agent prompts (code review, PR body)
scripts/                    CI helper scripts
tests/                      JUnit tests over .github/, scripts/ and fastlane/ (workflow invariants, CI scripts, listing)
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

**End-to-end tests.** What only shows on a real Android (the app as installed, opening other apps, Home as the default
home app) is tested in `e2e/` with UiAutomator, from outside the app's process, by key presses. They run on an emulator
that Gradle starts, not in `./gradlew test`; see [End-to-end tests](docs/testing.md#end-to-end-tests).

**Tests for CI and scripts.** `tests` has no main sources: its tests read `.github/` and `fastlane/` and run
`scripts/` the way CI does, so they run with `./gradlew test`. `WorkflowInvariantsTest` holds the reviewed
`GITHUB_TOKEN` scopes of every workflow and checks that every job has `timeout-minutes`, every action is pinned to a
commit SHA, no `run:` interpolates event payload (pass it through `env:`), and no `pull_request_target` workflow checks
out code. Adding a workflow means adding its scopes there. A test for `scripts/foo.sh` goes in
`tests/src/test/kotlin/dev/buildwithtim/minimalui/tooling/` and runs it with `runScript`. `FastlaneMetadataTest`
holds the store listing to the stores' limits and requires a changelog for the current `versionCode`. The module sits
at the root as `tests/` because the ACMM evaluation finds a test suite only by a root `tests/` directory (#235);
module tests under `<module>/src/test/` are invisible to it.

## Finding and claiming work

- GitHub issues are the **primary source of truth**. Browse open issues and assign one to yourself before starting.
- Issues are mirrored into the local `bd` (beads) tracker; run `bd ready` to see available work.
- Several agents take issues from the same queue; [`docs/multi-agent.md`](docs/multi-agent.md) covers how they claim
  work, keep out of each other's way, and hand over to a person.

We favour small, focused changes tied to a tracked issue.

### Failures on main

Workflows that run on `main` (pushes, schedules, manual runs) file their own failures as issues:
`.github/workflows/auto-issues.yml` runs `scripts/auto-issues.sh` after each of their runs. A failed or timed-out run
opens an issue labelled `ci` and titled `<workflow> fails on main` with the run and commit, or comments on that issue
when one is already open; the next successful run on `main` comments and closes it. Cancelled runs and pull request
runs change nothing. The watched workflows are listed by name in the workflow's `workflow_run.workflows`; a new or
renamed workflow that runs on `main` must be added there (`AutoIssuesWorkflowTest` fails until it is).

## Development workflow

1. Create a branch off `main`: `<type>/<number>-<slug>`.
2. Work the issue **test-first** (see below).
3. Commit with [Conventional Commits][conventional-commits] messages, e.g. `feat(launcher): add app search`,
   `fix(input): handle Fn key`, `docs(agents): clarify gates`.
4. Open a pull request and fill in the template (link the issue with `Fixes #<number>`, paste evidence).

`main` is protected by the ruleset in [`.github/rulesets/main.json`](.github/rulesets/main.json): no direct pushes,
force-pushes or deletion; changes land only through pull requests whose `PR review rubric`, `Build, lint, test`,
`Unit-test coverage floors`, `Static checks` and `End-to-end tests` checks pass. Pull requests merge with a merge commit
only (no squash or rebase), so each issue's single commit lands on `main` unchanged, and the head branch is deleted on
merge. The file is the source of truth: a change to it lands by PR, then a maintainer applies it with
`gh api -X PUT repos/Tim-van-Oudheusden/MinimalUI/rulesets/<id> --input .github/rulesets/main.json`.

Once its checks pass, the [merge queue](docs/multi-agent.md#merge-queue) re-runs `./gradlew check assembleDebug` on the
pull request merged with the latest `main` and merges it. High-risk pull requests and those labelled `hold` or
`sentinel-alert` wait for a maintainer; add `hold` or leave a pull request a draft to keep it out of the queue.

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

| Command                   | What it runs                                                                              |
| ------------------------- | ----------------------------------------------------------------------------------------- |
| `./gradlew check`         | Unit tests and Android lint (all checks, warnings as errors) for every module, formatting |
| `./gradlew assembleDebug` | Debug APK build                                                                           |

`check` is the gate: every check added to the build hooks into it, so CI and the docs never list its tasks one by one.

The Kotlin compiler treats warnings as errors, extra warnings included (`allWarningsAsErrors` and `extraWarnings` in
every module's build file), so an unused variable or a deprecated call fails the build. Fix the code; never
`@Suppress` a warning.

The `main` ruleset also requires the **Unit-test coverage floors** check (`.github/workflows/coverage-gate.yml`): it
runs `./gradlew :<module>:koverXmlReport` for every module in `.github/auto-qa-tuning.json` and fails the PR if a
module's function or line coverage drops below its floor there. Check locally with
`./gradlew :core:koverXmlReport && scripts/coverage-check.sh core <functions> <lines>`; see
[`docs/testing.md`](docs/testing.md#coverage-floors).

It also requires the **End-to-end tests** check (`.github/workflows/e2e.yml`), which runs the `e2e/` tests on an
emulator; see [`docs/testing.md`](docs/testing.md#end-to-end-tests).

### Shell scripts and workflows

The `main` ruleset also requires the **Static checks** job in `.github/workflows/ci.yml`. It downloads pinned
[shellcheck](https://www.shellcheck.net/) and [actionlint](https://github.com/rhysd/actionlint) releases (versions and
SHA-256 checksums in the job's `env:`) and runs `shellcheck -x` on every tracked `*.sh` file and every extensionless
sh/bash script under `scripts/`, then `actionlint` on `.github/workflows/`. actionlint reads `.github/actionlint.yaml`
and runs shellcheck on each `run:` block too. `gradlew` (Gradle's) and the generated `.beads/hooks/` are not linted.
Run the same locally, with both tools at the versions in `ci.yml` on your `PATH`:

```bash
shellcheck -x scripts/*.sh scripts/lib/*.sh
actionlint
```

Fix a finding rather than silencing it. A check that cannot apply to this repository is turned off in
`.github/actionlint.yaml` with a comment saying why.

### Formatting

[Spotless](https://github.com/diffplug/spotless) formats the repository: Kotlin and `*.gradle.kts` with
[ktlint](https://pinterest.github.io/ktlint/) in its `ktlint_official` style, at 120 columns, and every other text file
to no trailing whitespace (Markdown keeps it, as a line break) and a final newline. `./gradlew spotlessCheck`, part of
`check`, fails on a deviation; `./gradlew spotlessApply` fixes it. ktlint cannot shorten a long string or comment, so
those you wrap by hand. Its settings live in `.editorconfig`, which Android Studio reads too, so **Code → Reformat Code**
formats the same way. Composables keep type-style names (`AppList`).

A rule that misfires is turned off for the whole project in `.editorconfig`, with a comment saying why. Never
silence one finding with `@Suppress` or a `ktlint-disable` comment.

The commit that first formatted the code base is listed in `.git-blame-ignore-revs`. Run
`git config blame.ignoreRevsFile .git-blame-ignore-revs` once so local `git blame` skips it; GitHub does so on its own.

### Android Lint

Every module runs Android Lint as part of `check`: `app` through AGP, `core` and `tests` through the standalone
`com.android.lint` plugin. Each `lint {}` block turns on every check (`checkAllWarnings`) and fails on any warning;
`app` adds [Slack's Compose rules](https://slackhq.github.io/compose-lints/rules/) through `lintChecks`, and
`LintConfigTest` fails if a module stops treating warnings as errors. `core` and `tests` lint their test sources
too; `app` keeps lint's default for its Robolectric tests, where device-runtime checks misfire.

A check that is noise for this project is turned off by name in the module's `lint { disable += ... }`, with a comment
saying why. Never `@Suppress`/`@SuppressLint` a finding and never add a lint baseline.

### Static analysis

[detekt](https://detekt.dev) checks the Kotlin of every module (`src/main` and `src/test`) against its default rules
for code smells: overlong or overly complex functions, swallowed exceptions, magic numbers. `./gradlew detekt`, part of
`check`, fails on a finding; the report names the rule and lands in `<module>/build/reports/detekt/`. Fix the code: a
function that grows past a size or complexity limit is split, not exempted. `config/detekt/detekt.yml` holds only the
overrides, each with the reason: detekt's documented Compose settings, and size and complexity limits lifted for test
sources. A rule that misfires is changed there for the whole project; never `@Suppress` a finding or add a baseline.

### Applying review suggestions

Comment `/apply-suggestions` on a pull request to commit every ```` ```suggestion ```` block that reviewers with write
access (human or Claude) left on its current diff. `.github/workflows/auto-review.yml` commits them to the PR branch as
`chore(review): apply review suggestions (#N)` and posts a report comment listing what was applied and what was skipped:
outdated suggestions, ones on the base side of the diff, ranges past the end of the file or overlapping an already
applied range, comments holding more than one suggestion block, paths outside the checkout, and files under
`.github/workflows/` (the workflow token cannot push those, and one such edit would sink the whole push). A suggestion
block ends only at a line holding nothing but a fence at least as long as its opening one, as GitHub renders it, so a
````` ````suggestion ````` block can carry a code block. Only `OWNER`/`MEMBER`/`COLLABORATOR` comments trigger it and
only their suggestions count; fork pull requests are refused, and so is a head committed after the command comment
(comment again once you have looked at it). Resolved or declined review threads are not detected (the REST API does not
expose thread state): delete or edit a suggestion you do not want before commenting. The apply script
(`scripts/apply-review-suggestions.sh`) runs from the default branch, never from the PR. Pushes made with the workflow
token start no CI run, so push again or re-run CI before merging.

### Handing work to Claude Code

`.github/workflows/claude.yml` runs [Claude Code](https://github.com/anthropics/claude-code-action) in CI. Label an
issue `ai-fix-requested`, or mention `@claude` in an issue comment, PR review, or review comment, and Claude works the
request on a `claude/*` branch and opens a pull request; it never pushes to `main`. On a pull request, `@claude review
this` asks for a review. Mentions only count from `OWNER`/`MEMBER`/`COLLABORATOR`, the label can only be applied by
triagers and only counts when a person applies it (a bot's or GitHub App's label, such as the one `hive-forge-app` puts
on `[ACMM L<n>]` issues, skips the job), and fork pull requests are refused, so outside contributors cannot start or
steer a run. Claude may run only the quality gates (`./gradlew test`, `lint`, `assembleDebug`, `check`, and the module
test tasks) and `./gradlew spotlessApply`. Its PRs go through the same checks and human review as any other. The workflow needs the
`CLAUDE_CODE_OAUTH_TOKEN` repo secret and the Claude GitHub App.

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
If it leaks or is lost, follow [`docs/runbooks/release-key.md`](docs/runbooks/release-key.md).

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

If a published release turns out to be broken, see
[`docs/runbooks/bad-release-rollback.md`](docs/runbooks/bad-release-rollback.md) — there is no auto-update channel and
no downgrade path without uninstalling, so pulling one back is not a `git revert`.

## Code style

- Kotlin official code style (`kotlin.code.style=official`), 4-space indent, max line length 120 (`.editorconfig`).
- Prefer immutable data (`val`, `data class`, read-only collections) and pure functions in `core`.
- No `!!`; model absence with nullable types or sealed results.
- Compose: stateless composables that take state and callbacks; every interactive element must be reachable and usable
  by keyboard focus.

## Adding a translation

Every string in `app/src/main/kotlin` goes through `stringResource(R.string.…)`, so a new language is a new resource
directory (for what it does not cover yet, see [below](#what-a-translation-does-not-change)):

1. Copy `app/src/main/res/values/strings.xml` to `app/src/main/res/values-<lang>/strings.xml`, where `<lang>` is the
   target language's ISO 639-1 code (e.g. `nl` for Dutch, `de` for German), or `<lang>-r<REGION>` for a
   region-specific variant (e.g. `pt-rBR`).
2. Translate each `<string name="…">` value; keep the `name` attributes and any `%s`/`%d` format placeholders
   unchanged, in the same order as the English source.
3. Leave `app/src/main/res/values/strings.xml` (the English default) untouched in the same change — it's the
   fallback for any string a translation hasn't caught up on yet.
4. Any new string added anywhere in `app/src/main/kotlin` must go through `stringResource`/`strings.xml`, never a
   hardcoded literal, so it stays translatable.

`AndroidManifest.xml` already declares `android:supportsRtl="true"`, so a right-to-left language needs no code change
to lay out.

### What a translation does not change

- **The automatic group names and the profile suffixes stay in English.** Games, Audio, Video, Images, Social, News,
  Maps, Productivity, Accessibility and Other are the names of the `AppCategory` enum in `core/.../AppGroups.kt`, and
  `Work` is its `WORK_GROUP` constant, which is also the suffix that tells a work app apart from a personal app of the
  same name. None of them is in `strings.xml`. They are also the groups' identity, since a user group of the same name
  joins the automatic one. `Clone`, the suffix that tells an app clone apart from the app it copies, is not in
  `strings.xml` either: it is the `CLONE_SUFFIX` constant in `core/.../DuplicateLabels.kt`. Making any of them
  translatable is a code change, not part of a translation.
- **The sort order is the same in every language.** `labelCollator()` in `core/.../AppOrdering.kt` uses the root
  collation (`Locale.ROOT`), not the phone's language: accents sort next to their base letter, and other scripts sort
  after Latin, grouped by script. A language's own rules are not applied. In Swedish, for example, `Å`, `Ä` and `Ö`
  come after `Z`, but MinimalUI sorts them with `A` and `O`.

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
   `.github/CODEOWNERS` makes the maintainer the owner of the high-risk paths
   ([`docs/risk-tiers.md`](docs/risk-tiers.md)): a change to CI, scripts, the build, the manifest or agent instructions
   requests his review, and the merge queue leaves it for him to merge.

The security side of these layers (trust boundaries, secrets, supply chain, review focus, reporting) is set out in
[`docs/security/SECURITY-AI.md`](docs/security/SECURITY-AI.md).

How agent work fares is measured, not guessed: the weekly `PR metrics` workflow reports how many agent-authored PRs
(`claude/*` branches and the `hive-forge-app` GitHub App) were merged vs. closed unmerged, the weekly `Strategy report`
whether the backlog is shrinking and which ACMM gaps are still open, and the weekly `AI audit trail` workflow lists
every PR merged into `main` with the agent and model behind it and who merged it. Every metric, its
cadence, where it is published and how to reproduce it locally is catalogued in
[`docs/metrics/README.md`](docs/metrics/README.md).
