# Session summary

Hand-off notes for agent work in this repository, so the next session starts from where the last one stopped instead
of cold (AGENTS.md "Session Completion" step 7). Keep it short: overwrite the "Latest session" section at the end of
each session, in the issue's own commit; durable lessons belong in `.claude/memory/corrections.tsv`,
`docs/reflections/` or `bd remember`, not here.

Format: date, issue/PR worked, outcome, open follow-ups, gotchas for next time.

## Latest session

- **Date:** 2026-10-29
- **Worked:** #258 → `fix/258-claude-human-label`. `claude.yml` answers `ai-fix-requested` only when
  `github.event.sender.type == 'User'`, in the job `if` and in the workflow-level concurrency group, so
  `hive-forge-app`'s labels on `[ACMM L<n>]` issues give a skipped run instead of a red one and cannot replace a
  pending run a person asked for. No `allowed_bots`: the trust boundary is unchanged. Ported from Dailies#280 and
  website#993. Docs: CONTRIBUTING "Handing work to Claude Code", `multi-agent.md` (actor row, "Where a person
  decides"), `SECURITY-AI.md`. No runbook covers a stuck hand-off, so none changed.
- **Outcome:** PR open. `ClaudeWorkflowTest` `answers the ai-fix-requested label only when a person applies it`
  fails on the old `claude.yml` and passes on the new one. The PR changes `.github/workflows/`, so it is high risk
  and the maintainer merges it.
- **Open follow-ups:** none.
- **Notes for next session:**
  - Podman may be missing on this host; then build on the host with `JAVA_HOME=~/tools/jdk` and
    `sdk.dir=/home/dev/tools/sdk` in `local.properties`. actionlint is not installed, and the host shellcheck is 0.9.0:
    fetch the pinned releases from `ci.yml` into `/tmp/bin`.
  - The host's `jq` is jaq 3.1.1, which rejects `$module`/`$label` as variable names, so 12 tests in
    `AutoQaTunerScriptTest`, `PrMetricsScriptTest` and `QualityReportScriptTest` fail locally on an unmodified `main`
    too; CI uses jq and passes. Put jq 1.7.1 (`jqlang/jq` release `jq-linux-amd64`) first on `PATH` to run them.
  - The emulator on this host segfaults with SwiftShader Vulkan; use `-gpu swangle_indirect`
    (`docs/reflections/2026-10-29-emulator-on-this-host.md`).
  - Sandbox Gradle in this checkout needs `--project-cache-dir /tmp/pcache`, otherwise Spotless trips over host paths
    left in `.gradle/`.
  - Managed-device runs locally: `podman run --rm --device /dev/kvm -v "$PWD":/workspace:z -w /workspace
    -v minimalui-sandbox-gradle:/root/.gradle localhost/minimalui-emu:dev2 ./gradlew :e2e:atdApi35DebugAndroidTest
    -Pandroid.testoptions.manageddevices.emulator.gpu=swangle_indirect`.
  - In the sandbox, Spotless can fail with `Could not read path '…/build/…'` when it runs alongside compilation in one
    `check`: run `spotlessCheck` on its own first, then `check assembleDebug -x spotlessCheck`.
