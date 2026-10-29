# Session summary

Hand-off notes for agent work in this repository, so the next session starts from where the last one stopped instead
of cold (AGENTS.md "Session Completion" step 7). Keep it short: overwrite the "Latest session" section at the end of
each session, in the issue's own commit; durable lessons belong in `.claude/memory/corrections.tsv`,
`docs/reflections/` or `bd remember`, not here.

Format: date, issue/PR worked, outcome, open follow-ups, gotchas for next time.

## Latest session

- **Date:** 2026-10-29
- **Worked:** #246 → `docs/246-multi-agent`. Added `docs/multi-agent.md` (ACMM L6 `acmm:multi-agent-orchestration`):
  the agents that work the repo (Hive agents via `hive-forge-app`, ACMM evaluation, Claude Code in CI, local agents,
  the auto-issues and apply-suggestions workflows), the issue → claim → PR → human merge flow, the claim signals to
  check before starting (assignee, `hive:claim` comment lease, covering PR, running `Claude Code` job), worktree
  isolation, hand-off files, the Hive `stage/*` labels and where a person decides. Linked from README's documentation
  table and CONTRIBUTING "Finding and claiming work".
- **Outcome:** PR open; docs only.
- **Open follow-ups:** none. The `Claude Code` run for #246 (40934622464) failed because `hive-forge-app` applied
  `ai-fix-requested` and claude-code-action refuses bot actors; by design, recorded in `docs/multi-agent.md`.
- **Worked:** #245 → `fix/245-tests-module-inputs`. `tests/build.gradle.kts` declared `app`, `core` and `tests`
  build scripts as `:tests:test` inputs from a hard-coded list that missed `e2e`, so an edit to `e2e/build.gradle.kts`
  left the task up to date (or from cache) and `LintConfigTest`/`TestTimeoutConfigTest` never reran on it. The list
  is now `rootProject.childProjects.keys`.
- **Outcome:** PR open. Checked by hand: an `e2e/build.gradle.kts` edit was UP-TO-DATE before, reruns after; an
  unchanged rerun stays UP-TO-DATE and reuses the configuration cache.
- **Open follow-ups:** none.
- **Notes for next session:**
  - This host has no podman; Gradle runs directly with `JAVA_HOME=~/tools/jdk ANDROID_HOME=~/tools/sdk` and
    `--project-cache-dir /tmp/<dir>`.
  - The host's `jq` is jaq 3.1.1, which rejects `$module`/`$label` as variable names, so 12 tests in
    `AutoQaTunerScriptTest`, `PrMetricsScriptTest` and `QualityReportScriptTest` fail locally on an unmodified `main`
    too; CI uses jq and passes.
  - The emulator on this host segfaults with SwiftShader Vulkan; use `-gpu swangle_indirect`
    (`docs/reflections/2026-10-29-emulator-on-this-host.md`).
  - Sandbox Gradle in this checkout needs `--project-cache-dir /tmp/pcache`, otherwise Spotless trips over host paths
    left in `.gradle/`.
  - Managed-device runs locally: `podman run --rm --device /dev/kvm -v "$PWD":/workspace:z -w /workspace
    -v minimalui-sandbox-gradle:/root/.gradle localhost/minimalui-emu:dev2 ./gradlew :e2e:atdApi35DebugAndroidTest
    -Pandroid.testoptions.manageddevices.emulator.gpu=swangle_indirect`.
  - In the sandbox, Spotless can fail with `Could not read path '…/build/…'` when it runs alongside compilation in one
    `check`: run `spotlessCheck` on its own first, then `check assembleDebug -x spotlessCheck`.
