# Session summary

Hand-off notes for agent work in this repository, so the next session starts from where the last one stopped instead
of cold (AGENTS.md "Session Completion" step 7). Keep it short: overwrite the "Latest session" section at the end of
each session, in the issue's own commit; durable lessons belong in `.claude/memory/corrections.tsv`,
`docs/reflections/` or `bd remember`, not here.

Format: date, issue/PR worked, outcome, open follow-ups, gotchas for next time.

## Latest session

- **Date:** 2026-11-02
- **Worked:** #298 → `fix/298-auto-issues-stale-green`. Runs of one workflow on two quick pushes can finish out of
  order, so an older commit's green closed #293 right after the newer commit's red opened it. On `success`,
  `scripts/auto-issues.sh` now reads the failed commits off the open issue (its `Commit:` line and each "Failed again"
  comment) and keeps the issue open when `compare/<failed>...<passing>` is `behind`; any other status, or a commit
  GitHub cannot compare, closes it as before.
- **Outcome:** PR open, `Fixes #298`. It touches `scripts/` and `tests/`; no workflow change.
- **Open follow-ups:** none.
- **Notes for next session:**
  - The script parses the issue body and comment formats it writes itself; `AutoIssuesScriptTest` builds its fixtures
    by running the script, so a wording change to either format fails the keep-open tests.
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
  - The edit tools resolve relative paths against the main checkout, not a `worktrees/` checkout: a session that
    edits in a worktree must use its full path, or the edits land on whatever branch the main checkout has.
  - Robolectric runs offline from jars Gradle resolves (#279), so the sandbox no longer needs the host's `~/.m2`
    mounted for `:app:testDebugUnitTest`. A Robolectric upgrade needs the android-all versions it names: the build
    ids are string constants in `org/robolectric/plugins/DefaultSdkProvider.class` of the robolectric jar, the `iN`
    suffix in `DefaultSdkProvider$DefaultSdk.class`.
