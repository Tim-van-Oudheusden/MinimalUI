# Copilot Instructions

Guidance for GitHub Copilot agents working in this repository.
**`AGENTS.md` at the repo root is the authoritative agent instructions file —
when this file conflicts with AGENTS.md, AGENTS.md wins.**

## Issue tracking

- GitHub issues are the primary source of truth for all work. Read and claim
  issues through the GitHub API (`gh` CLI), never via local mirrors; an issue
  closes when the PR that says `Fixes #<number>` merges.
- Beads (`bd`) mirrors issue state locally; before any `bd github` command run
  `export GITHUB_TOKEN="$(gh auth token)"`. After GitHub-only changes use
  `bd github sync --pull-only`. Never make a standalone sync/`issues.jsonl`
  commit.
- Work one issue at a time; pick work via `bd ready` / `gh issue list`, and let
  child/follow-up issues wait for their parents.

## Engineering rules

- Android launcher in Kotlin, built with Gradle (wrapper: `./gradlew`).
  Modules: `app` (Android application, Jetpack Compose UI) and `core` (pure
  Kotlin/JVM logic with no Android dependencies). Versions live only in
  `gradle/libs.versions.toml`.
- Keyboard first: every screen must be fully usable with a hardware keyboard
  and D-pad, without touch.
- TDD is mandatory for code changes: one failing test first, implement, run the
  failing test, then the full suite. Never modify tests to make them pass;
  never add `@Suppress` to silence failures.
- Test behaviour, not implementation. Put logic in `core` so it runs as a plain
  JVM unit test; keep Android framework code in `app` thin.
- Quality gates after any change: `./gradlew check assembleDebug` (unit tests,
  lint, formatting); `./gradlew spotlessApply` fixes formatting.
- No new dependencies without explicit approval; always an exact version in
  the version catalog.
- Conventional Commits, one commit per issue, referencing the issue number.
- Land each issue through a pull request: branch `<type>/<number>-<slug>` off
  `main`, `bd dolt push`, `git push -u origin HEAD`, then `gh pr create` with
  `Fixes #<number>` in the body. `main` is protected; the maintainer merges
  once the required checks pass.

See `CONTRIBUTING.md` for contributor workflow and `AGENTS.md` for the full
agent ruleset.
