# Session summary

Hand-off notes for agent work in this repository, so the next session starts from where the last one stopped instead
of cold (AGENTS.md "Session Completion" step 7). Keep it short: overwrite the "Latest session" section at the end of
each session, in the issue's own commit; durable lessons belong in `.claude/memory/corrections.tsv`,
`docs/reflections/` or `bd remember`, not here.

Format: date, issue/PR worked, outcome, open follow-ups, gotchas for next time.

## Latest session

- **Date:** 2026-10-14
- **Worked:** after the #131 linting rollout (#132–#136, refactors #143–#145) and its merge into the open PRs: release
  0.1.1 (#151, PR #152, published), the sandbox debug key (#153, PR #154) and `scripts/sandbox-gradlew.sh` (#155,
  this branch, stacked on #154).
- **Outcome:** `check` runs unit tests, Android Lint on every module (warnings as errors, all checks, Compose rules),
  detekt 2.0.0-alpha.6, `spotlessCheck`, and Kotlin compiles with warnings as errors; CI also requires `Static checks`
  (ruleset id 24941495). Sandbox builds sign with the host's `~/.android/debug.keystore`, so they install over each
  other; `scripts/sandbox-gradlew.sh <tasks>` runs Gradle in the sandbox with every mount.
- **Open follow-ups:** #137 (quality report rows for formatting/detekt).
- **Notes for next session:**
  - No JDK on the host: `scripts/sandbox-gradlew.sh check assembleDebug`. Parallel worktrees set
    `SANDBOX_GRADLE_VOLUME` to a volume of their own.
  - The headless emulator (`-no-window`) segfaults on this host; `android emulator start` (windowed) works. The API 28
    AVD can boot with its display asleep: `adb shell input keyevent KEYCODE_WAKEUP`.
  - detekt is an alpha: a Dependabot bump may rename rules or config keys; check `detekt.yml` against its changelog.
  - App Robolectric tests are outside Android Lint (`checkTestSources = false` in app). `TestDevice.broadcastBattery()`
    reaches the deprecated sticky broadcast through reflection on purpose.
