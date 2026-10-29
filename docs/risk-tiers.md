# Risk tiers

Every pull request is classified into one of three risk tiers from the paths it
changes. `.github/workflows/tier-classifier.yml` runs `scripts/tier-classify.sh`
on each PR and posts the tier, plus every changed file with its own tier, to the
`Risk tier` job summary. The classification never fails the run: it tells
reviewers how closely to look, and it decides who merges. The
[merge queue](multi-agent.md#merge-queue) merges low- and medium-risk PRs once
their required checks pass, so review them before then or add `hold`; it
leaves high-risk PRs for a maintainer to merge after the review below.

The **highest tier of any changed file wins**, so one workflow edit makes the
whole PR high risk. A path that matches no rule is **medium** — new kinds of
files get a real review until they are classified.

The workflow runs the **base branch's** script and risk assessment (a second
checkout of the base commit) against the PR's diff, so a PR cannot lower its
own tier by editing either. A change to the rules takes effect for the PRs
after it merges.

Run it locally against your branch:

```bash
git diff -z --name-only --no-renames origin/main...HEAD | scripts/tier-classify.sh -z
```

`--no-renames` matters: with rename detection on, a moved file is listed only
under its new path, so moving `.github/workflows/ci.yml` to `docs/ci.yml` would
read as a low-risk docs change. Without it, the old path is listed too.

`-z` passes the paths NUL-terminated. Without it, git C-quotes a path holding
non-ASCII, `"`, `\` or a control character (`"d\303\251ploy.yml"`); the script
decodes a line in exactly that form back to the path before matching it, so a
high-risk file such as `.github/workflows/déploy.yml` is still high. A line that
is not exactly git's quoting is matched as written.

## Tier: high

Changes that alter how the app is built, gated or permitted, or how agents
behave. A mistake here can bypass every other check or ship to every device.

| Paths                                                                                | Why                                                  |
| ------------------------------------------------------------------------------------ | ---------------------------------------------------- |
| `.github/*`                                                                          | CI gates, review rubric, rulesets, the risk rules    |
| `scripts/*`, `tests/*`                                                               | Gate tooling and the tests that pin the workflows    |
| `gradle/*`, `*.gradle.kts`, `gradle.properties`, `gradlew`, `gradlew.bat`            | Build, version catalog, wrapper (supply chain)       |
| `app/src/main/AndroidManifest.xml`                                                   | App permissions and the HOME intent filter           |
| `AGENTS.md`, `CLAUDE.md`, `.claude/*`, `.cursor/*`, `.beads/*`, `prompts/*`          | Agent instructions and governance                    |
| `.gitattributes`                                                                     | Line endings, binary handling, diff/merge drivers    |
| `.devcontainer/*`, `Dockerfile*`, `*/Dockerfile`, `.dockerignore`                    | Development container and image configuration        |

Review: a human reviews the full diff line by line. Dependency changes follow
the rules in `AGENTS.md` (approved, exact version, only in
`gradle/libs.versions.toml`). Workflow changes keep PR-supplied fields in
`env:`, never inline in `run:`, and update `WorkflowInvariantsTest` when they
add a workflow or widen a token scope. Manifest changes justify every new
permission.

`.github/CODEOWNERS` makes the maintainer the owner of exactly these paths, so
a PR that touches one requests his review automatically (GitHub skips the
request when he opened the PR himself, as local agents do under his account).
`CodeownersTest` in `tests/` fails if a tracked file is rated high but has no
owner, or has an owner but is not rated high, so a new high-risk rule needs a
matching CODEOWNERS pattern in the same PR. The ruleset does not require the
code owner's approval (`require_code_owner_review` is off in
`.github/rulesets/main.json`), since an author cannot approve his own PR; what
keeps a high-risk PR from merging unreviewed is that only a maintainer merges
it.

## Tier: medium

Application code and anything not otherwise classified: `app/*`, `core/*`
(sources, resources and tests), `e2e/*` (the end-to-end tests), plus root files
such as `.editorconfig`.

Review: the standard rubric in `.github/workflows/review.yml` — TDD evidence,
behaviour-level tests, no weakened tests, keyboard and D-pad usability, quality
gates green.

## Tier: low

Prose that does not execute: `docs/*`, and Markdown files (`*.md`) outside the
high-risk and application paths.

Review: a read-through for accuracy against the code and workflows it
describes.

## Changing the tiers

The rules are policy as code in the risk assessment config,
`.github/risk-assessment.yml`: an ordered `rules:` list of `- <tier> <glob>`
items, one per line, where the **first matching rule wins** — keep high-risk
rules above broad globs such as `app/*` and `*.md`. `*` matches across `/`.
`scripts/tier-classify.sh` evaluates the rules and fails without a tier if a
line is not a blank line, a whole-line `#` comment, the `rules:` line or such
an item, or if an item names an unknown tier; set `RISK_TIER_POLICY` to try a
different file locally.

The engine and the shipped rules are covered by
`tests/src/test/kotlin/dev/buildwithtim/minimalui/tooling/TierClassifyScriptTest.kt`,
which also checks that the file is YAML and that every rule appears under its
tier above. Update the rules, their tests, and this document in the same PR.
The config (`high .github/*`), the script (`high scripts/*`) and the test
(`high tests/*`) are high risk; this doc is low.
