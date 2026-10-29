# Session summary

Hand-off notes for agent work in this repository, so the next session starts from where the last one stopped instead
of cold (AGENTS.md "Session Completion" step 7). Keep it short: overwrite the "Latest session" section at the end of
each session, in the issue's own commit; durable lessons belong in `.claude/memory/corrections.tsv`,
`docs/reflections/` or `bd remember`, not here.

Format: date, issue/PR worked, outcome, open follow-ups, gotchas for next time.

## Latest session

- **Date:** 2026-10-29
- **Worked:** the six ACMM L6 issues as a stack of PRs, each branched off the previous one so their shared docs and
  this file do not conflict: #254 `docs/agent-tasks/` (task traceability), #252 `.github/risk-assessment.yml` (risk
  rules as YAML), #255 `.github/CODEOWNERS` (structural gates), #251 `strategy-report.yml` (strategic dashboard),
  #256 `ai-audit.yml` (audit trail), and last #253 → `docs/253-ai-ops-runbook`: `docs/ai-ops-runbook.md` (where to
  look, routine checks, playbooks for stuck or misbehaving agents, how to stop them), linked from the README,
  `multi-agent.md` and `runbooks/README.md`. All ported from the website repo's equivalents (bash + jq here).
- **Outcome:** six PRs open. Every one touches this file (`.claude/*`), so all are high risk and the maintainer
  merges them; merge them in stack order (oldest first) so each PR's diff is its own commit.
- **Open follow-ups:** none.
- **Notes for next session:**
  - `scripts/sandbox-gradlew.sh --project-cache-dir /tmp/pcache …` works on this host (podman present); actionlint
    is not installed, fetch the pinned release from `ci.yml` into `/tmp`.
  - The edit tools resolve relative paths against the main checkout, not a `worktrees/` checkout: a session that
    edits in a worktree must use its full path, or the edits land on whatever branch the main checkout has.
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
  - Robolectric fetches its android-all jars from Maven Central at test time into `~/.m2`, which the sandbox does not
    keep, so a docs change that reruns `:app:testDebugUnitTest` can fail every Robolectric class with HTTP 429. The
    host's `~/.m2` has the jars: run the sandbox's `podman run` by hand with `-v "$HOME/.m2:/home/dev/.m2:ro,z"` added.
