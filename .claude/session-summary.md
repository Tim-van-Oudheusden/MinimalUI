# Session summary

Hand-off notes for agent work in this repository, so the next session starts from where the last one stopped instead
of cold (AGENTS.md "Session Completion" step 7). Keep it short: overwrite the "Latest session" section at the end of
each session, in the issue's own commit; durable lessons belong in `.claude/memory/corrections.tsv`,
`docs/reflections/` or `bd remember`, not here.

Format: date, issue/PR worked, outcome, open follow-ups, gotchas for next time.

## Latest session

- **Date:** 2026-10-14
- **Worked:** #131, the linting/formatting rollout: #132 Spotless + ktlint and the `./gradlew check assembleDebug`
  gate (PR #138), #133 compiler warnings as errors + `.git-blame-ignore-revs` (#142), #134 Android Lint on every
  module with all checks and Slack compose-lints (#141), #136 `Static checks` job with shellcheck + actionlint (#140),
  the refactors detekt's size limits required (#143 → #150, #144 → #149, #145 → #148), and #135 detekt (#146, this
  branch).
- **Outcome:** `check` now runs unit tests, Android Lint (warnings as errors, `checkAllWarnings`, Compose rules) on
  app/core/tooling, detekt 2.0.0-alpha.6 (default rules, overrides in `config/detekt/detekt.yml`), `spotlessCheck`,
  and Kotlin compiles with `allWarningsAsErrors` + `extraWarnings`. CI also runs the required `Static checks` job;
  the `main` ruleset (id 24941495) requires it. No baselines, no `@Suppress`; every disabled rule has a reason in its
  config.
- **Open follow-ups:** #137 (quality report rows for formatting/detekt). #139 (another agent's PR for #129) conflicts
  with the reformat and needs a rebase + `spotlessApply`.
- **Notes for next session:**
  - detekt is an alpha: a Dependabot bump may rename rules or config keys; check `detekt.yml` against its changelog.
  - App Robolectric tests are outside Android Lint (`checkTestSources = false` in app); device-runtime checks misfire
    there. `TestDevice.broadcastBattery()` reaches the deprecated sticky broadcast through reflection on purpose.
  - Spotless won't write a file while ktlint reports an error it can't fix; the ktlint CLI (`java -jar ktlint
    --format`) writes what it can and lists the rest.
  - Parallel worktrees each got their own Gradle home volume (a copy of `minimalui-sandbox-gradle`), so no two
    sandbox containers share Gradle's cache locks.
  - No JDK on the host: run Gradle in the sandbox with `--userns=keep-id` and the Gradle home at `/home/dev/.gradle`.
