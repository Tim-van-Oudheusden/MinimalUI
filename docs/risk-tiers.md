# Risk tiers

Every pull request is classified into one of three risk tiers from the paths it changes. The `Risk tier` job
(`.github/workflows/tier-classifier.yml`) runs `scripts/tier-classify.sh` on each PR and posts the tier to the run's job
summary, together with every changed file and its own tier. The classification is informational. It never fails the
run. It tells reviewers how closely to look.

The **highest tier of any changed file wins**, so one workflow edit makes the whole PR high risk. A path that matches no
rule is **medium**, so new kinds of files get a real review until they are classified.

Run it locally against your branch:

```bash
git diff --name-only --no-renames origin/main...HEAD | scripts/tier-classify.sh
```

`--no-renames` matters. With rename detection on, `git diff --name-only` lists only a moved file's new path, so moving
`.github/workflows/ci.yml` to `docs/ci.yml` would read as a low-risk docs change. Without it, the deleted workflow path
is listed too.

## Tier: high

Changes that alter how the app is built, gated or allowed to act on the phone, or how agents behave. A mistake here can
bypass every other check or reach users' phones directly.

| Paths | Why |
| --- | --- |
| `.github/*` | CI gates, review rubric, `main` ruleset, labeler, Dependabot, and this policy |
| `scripts/*` | Gate tooling that CI runs (`pr-review-check.sh` backs a required check) |
| `gradle/*`, `*.gradle.kts`, `gradle.properties`, `gradlew*` | Version catalog, Gradle wrapper and build scripts (supply chain) |
| `app/src/main/AndroidManifest.xml` | Permissions, exported components and the HOME intent filter |
| `.gitattributes` | Line-ending and binary handling for every file |
| `AGENTS.md`, `CLAUDE.md`, `.claude/*`, `.cursor/*`, `.beads/*`, `prompts/*` | Agent instructions and governance |

Review: a human reads the full diff line by line. Dependency changes follow `AGENTS.md` "Dependency Management"
(approved, exact version, only in `gradle/libs.versions.toml`, wrapper jar never hand-edited). Workflow changes keep
`permissions:` least-privilege and pass PR-supplied fields through `env:`, never inline in `run:`. Manifest changes add
no permission or exported component the issue did not ask for. Agent-instruction changes that loosen a boundary need
the maintainer's explicit approval.

## Tier: medium

Application code and its tests: `app/*` and `core/*` (Kotlin sources, Compose UI, resources, JUnit tests), plus anything
no rule matches, such as `LICENSE`, `.editorconfig` or `.gitignore`.

Review: the standard rubric in `.github/workflows/review.yml`: TDD evidence, behaviour-level tests, no weakened tests or
`@Suppress`, quality gates green, and keyboard-first UI.

## Tier: low

Prose that does not execute: `docs/*`, and Markdown files (`*.md`) outside the high-risk paths, such as `README.md`,
`CONTRIBUTING.md` and `BRANDING.md`.

Review: a read-through for accuracy, checking that commands and paths in the text still exist.

## Changing the tiers

The rules are policy as code in `.github/policies/risk-tiers.policy`, one `<tier> <glob>` rule per line. The **first
matching rule wins**, so keep high-risk rules above broad globs such as `*.md`. `*` matches across `/`.
`scripts/tier-classify.sh` evaluates the policy and exits 2 without printing a tier if a rule is malformed or names an
unknown tier. Set `RISK_TIER_POLICY` to try a different policy file locally.

`scripts/tier-classify.test.sh` covers the engine, the shipped rules, and the sync between the policy and this page:
every glob in the policy must appear in the tables above. Update the policy, its tests and this page in the same PR. The
policy file (`.github/*`) and both scripts (`scripts/*`) are high risk. This page is low.
