# Session summary

Hand-off notes for agent work in this repository, so the next session starts from where the last one stopped instead
of cold (AGENTS.md "Session Completion" step 7). Keep it short: overwrite the "Latest session" section at the end of
each session, in the issue's own commit; durable lessons belong in `.claude/memory/corrections.tsv`,
`docs/reflections/` or `bd remember`, not here.

Format: date, issue/PR worked, outcome, open follow-ups, gotchas for next time.

## Latest session

- **Date:** 2026-10-30
- **Worked:** #274 → `build/274-guava-test-classpath`. Nightly compliance failed on GHSA-xxph-c9ww-hj94 (Guava
  <= 33.7.1) although #257 had put a Guava 33.7.2 floor on the build classpath: the submitted dependency graph showed
  Robolectric 4.17 (latest release) bringing Guava 33.6.0-jre onto the app's unit-test classpaths, which the
  build-tool floors never reach. `app/build.gradle.kts` now adds `libs.guava` as a `testImplementation` constraint;
  `docs/testing.md` says how to handle a test library's vulnerable transitive.
- **Outcome:** PR open, `Fixes #274`. It touches this file (`.claude/*`), so it is high risk and the maintainer merges
  it. The alert closes after the next dependency-graph submission from `main`.
- **Open follow-ups:** drop the constraint once a Robolectric release brings Guava >= 33.7.2 itself.
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
  - The edit tools resolve relative paths against the main checkout, not a `worktrees/` checkout: a session that
    edits in a worktree must use its full path, or the edits land on whatever branch the main checkout has.
  - Robolectric fetches its android-all jars from Maven Central at test time into `~/.m2`, which the sandbox does not
    keep, so a docs change that reruns `:app:testDebugUnitTest` can fail every Robolectric class with HTTP 429. The
    host's `~/.m2` has the jars: run the sandbox's `podman run` by hand with `-v "$HOME/.m2:/home/dev/.m2:ro,z"` added.
