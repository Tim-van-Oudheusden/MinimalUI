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

To skip the local SDK install, use the sandbox image instead: `Dockerfile.sandbox` (JDK 21, Android SDK, `git`, `jq`,
`gh`, `bd`) is also what `.devcontainer/devcontainer.json` builds, so VS Code "Reopen in Container" and a plain
`podman build -f Dockerfile.sandbox -t minimalui-sandbox .` give the same environment. It runs the quality gates; see
[docs/testing.md](docs/testing.md#agent-sandbox-what-runs-where) for what needs a real device.

## Project layout

```text
app/                        Android application: manifest, activity, Compose UI, resources
  src/main/kotlin/          Kotlin sources, package dev.buildwithtim.minimalui (ui/ holds composables)
  src/main/res/             strings, theme, adaptive launcher icon, backup rules
core/                       pure Kotlin/JVM logic (no Android dependencies), package dev.buildwithtim.minimalui.core
  src/test/kotlin/          JUnit tests, run on the JVM without an emulator
gradle/libs.versions.toml   every dependency and plugin version
gradle/wrapper/             Gradle wrapper (pinned version and checksum)
prompts/                    reusable agent prompts (code review, PR body)
scripts/                    CI helper scripts
tooling/                    JUnit tests over .github/ and scripts/ (workflow invariants, CI script behaviour)
```

**Where code goes.** Anything that can be expressed without the Android framework (sorting, filtering, key-to-action
mapping, config parsing) goes in `core`, so it is tested with plain JUnit and no emulator. `app` stays a thin shell that
reads from Android APIs and renders with Compose.

**Where a test goes.** Next to the module it covers, in the same package: a test for
`core/src/main/kotlin/dev/buildwithtim/minimalui/core/Foo.kt` goes in `core/src/test/kotlin/dev/buildwithtim/minimalui/core/FooTest.kt`.

**Tests for CI and scripts.** `tooling` has no main sources: its tests read `.github/` and run `scripts/` the way CI
does, so they run with `./gradlew test`. `WorkflowInvariantsTest` holds the reviewed `GITHUB_TOKEN` scopes of every
workflow and checks that every job has `timeout-minutes`, every action is pinned to a commit SHA, no `run:` interpolates
event payload (pass it through `env:`), and no `pull_request_target` workflow checks out code. Adding a workflow means
adding its scopes there. A test for `scripts/foo.sh` goes in `tooling/src/test/kotlin/dev/buildwithtim/minimalui/tooling/`
and runs it with `runScript`.

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
