# Testing

How MinimalUI's tests and quality checks run, and where each one can run.

## Test timeout

Every `Test` task in every module fails after 10 minutes (the root `build.gradle.kts`, #216), so a test stuck in a loop
fails the build instead of hanging it. A normal run is far below that: `:app:testDebugUnitTest` takes about 20 s
locally. Test helpers that step towards a state, such as cycling a setting or pressing Down to a row, take at most as
many steps as there are choices or rows, and then fail naming what they did not find.

## Nightly compliance

`.github/workflows/nightly-compliance.yml` runs nightly (`23 3 * * *` UTC) and on manual dispatch, for checks that can
go stale without a code change. It has two jobs:

1. **Submit dependency graph** runs `gradle/actions/dependency-submission`, which resolves every Gradle configuration
   and sends the graph to GitHub's dependency graph, so Dependabot alerts cover the current build. Only this job has
   `contents: write`, which the submission API needs.
2. **Nightly compliance** runs even when the submission failed. It validates the wrapper jar with
   `gradle/actions/wrapper-validation`, then runs `scripts/nightly-compliance.sh > nightly-compliance.md`. The report is
   published as the job summary and the `nightly-compliance` artifact.

The script reports one pass/fail row per check, runs every check even when an earlier one fails, and exits non-zero if
any failed:

| Check | Fails when |
| ----- | ---------- |
| Dependency graph submission | the submission job's result (`DEPENDENCY_GRAPH_RESULT`) is not `success` |
| Gradle wrapper jar checksum | the wrapper-validation step's outcome (`WRAPPER_VALIDATION_OUTCOME`) is not `success`, so `gradle/wrapper/gradle-wrapper.jar` does not match a checksum Gradle publishes |
| Dependabot alerts (high+) | an open Dependabot alert has severity `high` or `critical` and its GHSA id is not on the ignore list, or the alerts API cannot be read |

A check with no recorded outcome fails, so running the script outside the workflow shows the two workflow-fed rows as
failed.

The alerts come from `gh api repos/$GITHUB_REPOSITORY/dependabot/alerts?state=open`. The job's `GITHUB_TOKEN` reads them
through the `vulnerability-alerts: read` permission
([workflow syntax: permissions](https://docs.github.com/en/actions/reference/workflows-and-actions/workflow-syntax#permissions)),
so no secret is needed. actionlint 1.7.12 does not know that scope yet; `.github/actionlint.yaml` ignores that one
message for this workflow ([rhysd/actionlint#713](https://github.com/rhysd/actionlint/issues/713)).

### Advisories in build tools

An advisory in a library that only a build tool pulls in (AGP, Kover, Android Lint) cannot be fixed by Dependabot: no
build file declares it, so its security update ends in `security_update_dependency_not_found`. Raise it instead with
the `build-tool-floors` bundle in `gradle/libs.versions.toml`: `build.gradle.kts` adds each library of the bundle as a
constraint to the plugin classpath and every module's `androidLintTool`, so the patched version resolves there unless a
tool brings a newer one (#147). Check the result with `./gradlew buildEnvironment` and
`./gradlew :<module>:dependencies --configuration androidLintTool`. Drop a floor once every tool that pulled the library
in ships the patched version itself.

### Accepting an advisory

Add its GHSA id to `DEFAULT_ALERTS_IGNORE` in `scripts/nightly-compliance.sh`, with a comment on the line above that gives
the reason and the date to revisit. Remove the entry once a fixed version is in the build. Dismissing the alert on
GitHub also removes it from the open alerts, but leaves no reviewed record in the repository.

### Running it locally

```bash
DEPENDENCY_GRAPH_RESULT=success WRAPPER_VALIDATION_OUTCOME=success scripts/nightly-compliance.sh
```

This reads the alerts with your `gh` login (needs `jq`). Set `ALERTS_JSON=<file>` to read an alerts JSON array from a
file instead, and `ALERTS_IGNORE="GHSA-… GHSA-…"` to replace the ignore list; the tests in
`tooling/src/test/kotlin/dev/buildwithtim/minimalui/tooling/NightlyComplianceScriptTest.kt` use both.

## Agent sandbox: what runs where

**Decision:** the agent sandbox (`Dockerfile.sandbox`, also built by `.devcontainer/devcontainer.json`) runs the
JVM and build gates only: `./gradlew check assembleDebug`. Anything that needs an Android device or emulator
(`./gradlew installDebug`, `connectedAndroidTest`, manual keyboard/D-pad checks on the launcher) runs on a host with a
device attached over `adb`, or on an emulator on the host (see
[Running it in an emulator on Linux](../CONTRIBUTING.md#running-it-in-an-emulator-on-linux)).

Why: the image ships the SDK pieces the build needs (`platform-tools`, the `compileSdk` platform, and the build-tools
revision AGP defaults to) but no emulator or system images. An emulator needs KVM (`/dev/kvm`) and a display, and
reaching a USB device needs device passthrough; both widen the sandbox's privileges, which is the opposite of what it
is for.

The SDK in the image is root-owned and read-only for the `dev` user, so a missing component fails the build instead of
being downloaded at runtime. When `compileSdk` or the AGP version changes, bump `ANDROID_PLATFORM` /
`ANDROID_BUILD_TOOLS` in `Dockerfile.sandbox` in the same change. Gradle dependencies still download into the Gradle
user home (`/home/dev/.gradle`); mount a volume there to cache them.

### The debug signing key

AGP signs debug builds with `~/.android/debug.keystore` and creates a new one when it is missing. The image has no
`/home/dev/.android`, so without a mount every container signs with a key of its own, and a sandbox-built APK never
installs over the previous one: `adb install -r` fails with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, and uninstalling
to get past it wipes MinimalUI's settings on the device. Mount your host's debug keystore read-only instead. Sandbox
builds then sign like builds on the host (Android Studio or `./gradlew` with a local JDK), and either installs over the
other. The keystore goes inside a tmpfs `/home/dev/.android` that the container can write: AGP's `validateSigning`
tasks fail with `Unable to create debug keystore … because it is not writable` on an unwritable `~/.android`, even
when the keystore is there (#159). Nothing written there outlives the container.

If `~/.android/debug.keystore` does not exist yet, create it once with the sandbox's `keytool` (the host needs no
JDK), from the repository root. These are the values AGP itself uses for a debug key; `*.keystore` is gitignored, and
the file moves out of the checkout straight away:

```bash
podman run --rm --userns=keep-id -v "$PWD":/workspace:z localhost/minimalui-sandbox \
  keytool -genkeypair -keystore debug.keystore -storepass android -keypass android \
  -alias androiddebugkey -dname "CN=Android Debug,O=Android,C=US" -keyalg RSA -keysize 2048 -validity 10000
mkdir -p ~/.android && mv debug.keystore ~/.android/
```

It is a debug key: it signs nothing that ships (releases use the release key, see
[CONTRIBUTING.md](../CONTRIBUTING.md#the-release-key)), so it needs no backup; a new one only means uninstalling
debug builds once.

### Running the gates

Build the image once (again whenever `Dockerfile.sandbox` changes), then run Gradle in it with
`scripts/sandbox-gradlew.sh`, from anywhere in the checkout. It passes its arguments to `./gradlew` and mounts the
repository at `/workspace`, Gradle's caches from the `minimalui-sandbox-gradle` volume and the debug key read-only in a
tmpfs `~/.android` (rootless podman; `--userns=keep-id` maps your host user to `dev` so build outputs stay owned by
you):

```bash
podman build -f Dockerfile.sandbox -t localhost/minimalui-sandbox .
scripts/sandbox-gradlew.sh check assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk   # from the host, onto a device or emulator
```

It stops before starting the container if `~/.android/debug.keystore` is missing. Worktrees building side by side
each want a Gradle cache of their own: `SANDBOX_GRADLE_VOLUME=minimalui-sandbox-gradle-2 scripts/sandbox-gradlew.sh …`.
For a shell in the sandbox instead, run the same `podman run` the script does with `-it` and `bash` in place of
`./gradlew`.

In a git worktree, `.git` is a file pointing outside the mount, so git commands inside the container see no
repository; the Gradle gates do not need one.

## Store screenshots

`app/src/test/kotlin/dev/buildwithtim/minimalui/StoreScreenshots.kt` renders the README and store screenshots from the
real `MainActivity` under Robolectric's native graphics (`@GraphicsMode(NATIVE)`), so it needs no device or emulator
and runs in the agent sandbox. It installs a fixed set of demo apps, puts the phone on Wi-Fi at 80 % charge, makes two
favourites and two recent apps by key presses, and captures the home screen, a typed filter, an app's menu and
MinimalUI settings at 1080 × 1620 px (360 × 540 dp at xxhdpi, the 3:2 screen of the targeted keyboard phones).

```bash
./gradlew :app:testDebugUnitTest -PstoreScreenshots
```

The `storeScreenshots` property makes the unit-test task run only `StoreScreenshots` and write the PNGs to
`fastlane/metadata/android/en-US/images/phoneScreenshots/`; without it, every test task leaves the class out, so
`./gradlew test` and CI never run it. The status pill shows the time of the run, so regenerated files differ even
when the UI has not changed: regenerate them when the look of a captured screen changes, and commit the result.
`FastlaneMetadataTest` in `tooling` checks that the screenshots exist and fit the stores' size rules, and that the
listing texts and the changelog for the current `versionCode` exist within the stores' length limits.

## Coverage floors

Unit-test coverage is measured with [Kover](https://github.com/Kotlin/kotlinx-kover) (`org.jetbrains.kotlinx.kover`),
applied to `core` and `app`. `tooling` holds only repository tests and is not measured.
`./gradlew :<module>:koverXmlReport` runs the module's unit tests (`testDebugUnitTest` for `app`) and writes a
JaCoCo-format XML report to `<module>/build/reports/kover/report.xml`.

The floors live in `.github/auto-qa-tuning.json`, per module, in percent:

```json
{ "margin": 2, "floors": { "core": { "functions": 100, "lines": 100 } } }
```

`scripts/coverage-check.sh <module> <functions> <lines>` reads the module's report and compares the module-level
`METHOD` (functions) and `LINE` counters with the floors. It prints the observed coverage next to each floor and exits 1
if either is below its floor, 2 if the report is missing or a floor is not a percentage between 0 and 100. The
comparison uses the exact ratio, so 99.999 % does not pass a 100 % floor.

`.github/workflows/coverage-gate.yml` (job **Unit-test coverage floors**, every PR and push to `main`) runs the check
for every module listed in the floors file and is a required status check in the `main` ruleset. Adding a module to the
gate means adding it to `.github/auto-qa-tuning.json` and applying the Kover plugin in its `build.gradle.kts`. Apply the
ruleset entry to the live ruleset only after the workflow has run on `main` once; a required check that never reports
blocks every PR.

Raise a floor when coverage rises; never lower one to make a PR pass. Add tests instead.

The initial floors are the coverage observed when the gate was added, rounded down: `core` 100 % / 100 %, `app` 0 % /
0 % (it had no unit tests yet). `app` was raised to 93 % / 84 % once it was unit-tested (#95): observed coverage minus
the margin, taken from the lowest of `main` and the PRs then open, so no open PR started failing. It was raised to
98 % / 97 % (#127) once the status pill's glyphs and a removed work profile were tested and the generated `BuildConfig`
left the report. Run the check locally:

```bash
./gradlew :core:koverXmlReport
scripts/coverage-check.sh core 100 100
```

`./gradlew :core:koverHtmlReport` writes a browsable report to `core/build/reports/kover/html/`.

## Quality report

`.github/workflows/quality-report.yml` (job **Quality report**: every push to `main`, weekly on Monday at 06:17 UTC,
and on demand) runs `scripts/quality-report.sh > quality-report.md`. The script runs each quality gate separately
(`./gradlew check`, which includes unit tests, lint and formatting, then `assembleDebug`), then
`./gradlew :<module>:koverXmlReport` for every module in `.github/auto-qa-tuning.json`, and prints a Markdown report:
pass/fail per gate and function/line coverage per module.
Every gate and coverage run happens even when an earlier one fails. A module whose coverage run fails, or leaves no
report, shows `❌ n/a`; a report left by an earlier build is deleted first, so it never stands in for the current run.
Gradle output goes to stderr. The script exits 1 if anything failed, 2 if the floors file lists no module.

The workflow appends the floor tuning below (even when a gate failed), publishes the result to the job summary and
uploads it as the `quality-report` artifact. Run it locally:

```bash
scripts/quality-report.sh > quality-report.md
```

## Coverage floor self-tuning

`scripts/auto-qa-tuner.sh --report quality-report.md [--config .github/auto-qa-tuning.json] [--write]` reads the
coverage rows of a quality report and proposes, per module and metric, the floor raised to the observed coverage minus
the config's `margin` (in percentage points), rounded down to a whole percent. Floors only ratchet up: a proposal below
the current floor keeps the floor, and a module reported as `n/a` keeps both floors. It prints the proposals as a
Markdown table (`⬆️` marks a raise); `--write` saves them to the config, keeping every other key. It exits 2 for a
missing report or a malformed config (a floor outside 0–100, a negative or missing margin, no modules) and then leaves
the config untouched.

The quality report workflow runs it with `--write` and uploads the tuned file as the `auto-qa-tuning` artifact. Nothing
is committed automatically: to adopt the proposal, download the artifact, review the diff and commit it in a PR.
