# Metrics

The repository's metrics: what is measured about the codebase and the work landing in it (agent-authored and
otherwise), where each number is published, and how to reproduce it locally. Every metric here comes from a script or
workflow in the repo; nothing is collected from the launcher or its users.

Results are published as GitHub Actions **job summaries** (open the workflow run on the Actions tab) and, where noted,
as a downloadable **run artifact**. Thresholds that gate merges live in version control, so changes to them are
reviewed like code. Scripts are bash + `jq`, print Markdown to stdout and diagnostics to stderr, and run the same
locally as in CI.

## Catalog

| Metric | Measures | Source | Cadence | Published as |
| --- | --- | --- | --- | --- |
| [Quality gates](#quality-gates) | Pass/fail of `test`, `lint`, `assembleDebug` | `scripts/quality-report.sh` via `quality-report.yml` | Push to `main`, weekly (Mon 06:17 UTC), manual | Job summary + `quality-report` artifact |
| [Unit-test coverage](#unit-test-coverage) | Function and line coverage per module | `scripts/quality-report.sh` via `quality-report.yml` | Same as above | Job summary + `quality-report` artifact |
| [Coverage floors](#coverage-floors) | Minimum coverage each module must keep | `.github/auto-qa-tuning.json`, enforced by `coverage-gate.yml` | Every PR and push to `main` | `Unit-test coverage floors` check; proposed floors as `auto-qa-tuning` artifact |
| [PR acceptance rate](#pr-acceptance-rate) | Merged vs. closed-unmerged PRs, agent vs. other | `scripts/pr-metrics.sh` via `pr-metrics.yml` | Weekly (Mon 06:17 UTC), manual | Job summary |
| [Nightly compliance](#nightly-compliance) | Checks that can go stale without a code change | `scripts/nightly-compliance.sh` via `nightly-compliance.yml` | Nightly (03:23 UTC), manual | Job summary + `nightly-compliance` artifact |
| [Risk tier](#risk-tier) | Review risk of each PR from the paths it changes | `scripts/tier-classify.sh` via `tier-classifier.yml` | Every PR | `Risk tier` job summary |

## Quality gates

Runs the quality gates `./gradlew test`, `./gradlew lint` and `./gradlew assembleDebug` and reports each as
pass/fail. Every gate runs even when an earlier one fails, so one report shows the full state. The workflow fails if
any gate fails.

```bash
scripts/quality-report.sh > quality-report.md
```

## Unit-test coverage

Part of the same report: [Kover](https://github.com/Kotlin/kotlinx-kover) runs each module's unit tests
(`./gradlew :<module>:koverXmlReport`, `testDebugUnitTest` for `app`), and the report records the module's function
(`METHOD`) and line (`LINE`) percentages. A failing test run reports `n/a` rather than a misleading number. See
[`docs/testing.md`](../testing.md#coverage-floors) for the coverage tooling.

## Coverage floors

`.github/auto-qa-tuning.json` holds a function and line floor per module
(`{"margin": 2, "floors": {"<module>": {"functions": N, "lines": N}}}`). The `Unit-test coverage floors` job of
`coverage-gate.yml` fails a PR or push that drops a module below its floor:

```bash
./gradlew :core:koverXmlReport
scripts/coverage-check.sh core <functions> <lines>
```

The floors ratchet: on each quality-report run, `scripts/auto-qa-tuner.sh` proposes raising each floor to observed
coverage minus the file's `margin` (floors are never lowered), appends the proposal to the report, and uploads the
tuned file as the `auto-qa-tuning` artifact. Floors change only when that proposal is committed.

```bash
scripts/auto-qa-tuner.sh --report quality-report.md          # propose
scripts/auto-qa-tuner.sh --report quality-report.md --write  # apply to .github/auto-qa-tuning.json
```

## PR acceptance rate

Of the PRs closed in the reporting window (default 28 days), how many were merged vs. closed without merging. Split
into agent-authored (head branch `claude/*`, or authored by the `hive-forge-app` GitHub App) and other. A falling
agent acceptance rate is the signal to revisit agent instructions, prompts or skills.

Run it with a different window from the Actions tab (**PR metrics → Run workflow → days**), or locally with an
authenticated `gh`:

```bash
since="$(date -u -d '28 days ago' +%Y-%m-%d)"
gh pr list -R Tim-van-Oudheusden/MinimalUI --state closed --limit 1000 --search "closed:>=${since}" \
  --json number,mergedAt,headRefName,author | scripts/pr-metrics.sh --since "$since" --days 28
```

## Nightly compliance

Checks that can go stale without a code change (for example dependency advisories), run every night so drift shows up
before the next PR trips over it.

```bash
scripts/nightly-compliance.sh > nightly-compliance.md
```

## Risk tier

Classifies each PR as low, medium, or high risk from its changed paths; the highest tier of any file wins.
Informational only; it never fails the run. The path rules live in `.github/policies/risk-tiers.policy`; tiers and the
review each expects are defined in [`docs/risk-tiers.md`](../risk-tiers.md).

```bash
git diff --name-only --no-renames origin/main...HEAD | scripts/tier-classify.sh
```

`--no-renames` lists a file moved out of a high-risk path under its old path too, as `tier-classifier.yml` does;
without it, the move reads as a change to its new path only.

## Adding a metric

1. Compute it in a script under `scripts/` (bash + `jq`; Markdown to stdout, diagnostics to stderr) so it runs the
   same locally and in CI, and cover it with a test in the `tooling` module (`./gradlew :tooling:test`).
2. Publish it from a workflow to the job summary, plus an artifact if it is worth downloading. Give the workflow a
   reviewed token scope in `WorkflowInvariantsTest`.
3. Add a row to the catalog above and a section describing what it measures and how to reproduce it.
