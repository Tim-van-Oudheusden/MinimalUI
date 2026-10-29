# Session summary

Hand-off notes for agent work in this repository, so the next session starts from where the last one stopped instead
of cold (AGENTS.md "Session Completion" step 7). Keep it short: overwrite the "Latest session" section at the end of
each session, in the issue's own commit; durable lessons belong in `.claude/memory/corrections.tsv`,
`docs/reflections/` or `bd remember`, not here.

Format: date, issue/PR worked, outcome, open follow-ups, gotchas for next time.

## Latest session

- **Date:** 2026-10-29
- **Worked:** #243 → `ci/243-auto-issues`. `.github/workflows/auto-issues.yml` (`workflow_run` on every workflow
  that runs on `main`) runs `scripts/auto-issues.sh`: a failed or timed-out run on `main` opens a `ci` issue titled
  `<workflow> fails on main` (or comments on the open one); the next success closes it. Only same-repo push,
  schedule and dispatch runs count. `AutoIssuesScriptTest` drives the script against a fake `gh`;
  `AutoIssuesWorkflowTest` fails when a workflow on `main` is missing from the watch list. CONTRIBUTING
  "Failures on main" documents it.
- **Outcome:** PR open. `workflow_run` only fires from the default branch's copy, so it acts after the merge.
- **Open follow-ups:** none.
- **Notes for next session:**
  - The emulator on this host segfaults with SwiftShader Vulkan; use `-gpu swangle_indirect`
    (`docs/reflections/2026-10-29-emulator-on-this-host.md`).
  - Sandbox Gradle in this checkout needs `--project-cache-dir /tmp/pcache`, otherwise Spotless trips over host paths
    left in `.gradle/`.
  - Managed-device runs locally: `podman run --rm --device /dev/kvm -v "$PWD":/workspace:z -w /workspace
    -v minimalui-sandbox-gradle:/root/.gradle localhost/minimalui-emu:dev2 ./gradlew :e2e:atdApi35DebugAndroidTest
    -Pandroid.testoptions.manageddevices.emulator.gpu=swangle_indirect`.
  - In the sandbox, Spotless can fail with `Could not read path '…/build/…'` when it runs alongside compilation in one
    `check`: run `spotlessCheck` on its own first, then `check assembleDebug -x spotlessCheck`.
