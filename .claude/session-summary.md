# Session summary

Hand-off notes for agent work in this repository, so the next session starts from where the last one stopped instead
of cold (AGENTS.md "Session Completion" step 7). Keep it short: overwrite the "Latest session" section at the end of
each session, in the issue's own commit; durable lessons belong in `.claude/memory/corrections.tsv`,
`docs/reflections/` or `bd remember`, not here.

Format: date, issue/PR worked, outcome, open follow-ups, gotchas for next time.

## Latest session

- **Date:** 2026-10-29
- **Worked:** #256 → `ci/256-ai-audit` (ACMM L6 `aef:audit-trail`). Added `.github/workflows/ai-audit.yml` (weekly
  Mon 06:17 UTC, past 7 days; manual with `days`) and `scripts/ai-audit-report.sh`: every PR merged into `main`,
  attributed Hive (signature line or `hive-forge-app` author; agent/model from the signature), Claude Code (CI)
  (`claude/*`), Dependabot (`dependabot/*`) or Other, with author and merger. Job summary + 90-day `ai-audit` artifact
  with the raw PR JSON. Ported from the website repo's #978 as bash + `jq`, the convention here. Docs:
  `docs/metrics/README.md` (row + section), CONTRIBUTING "Agent safety layers", `multi-agent.md` "Measuring it".
- **Outcome:** PR open. Tests: `AiAuditReportScriptTest` (8), `WorkflowInvariantsTest` scopes,
  `AutoIssuesWorkflowTest` (the new workflow is watched by `auto-issues.yml`). The PR changes `.github/workflows/`, so
  it is high risk and the maintainer merges it.
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
