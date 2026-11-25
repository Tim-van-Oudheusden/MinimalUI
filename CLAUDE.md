# CLAUDE.md

Guidance for Claude-family agents working in this repository.
**`AGENTS.md` at the repo root is the authoritative agent instructions file —
when this file conflicts with AGENTS.md, AGENTS.md wins.**

## Issue tracking

- GitHub issues are the single source of truth for all work. Read and claim
  issues through the GitHub API (`gh` CLI); an issue closes when the PR that
  says `Fixes #<number>` merges.
- Work one issue at a time; pick work via `gh issue list`, and let
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
  `main`, `git push -u origin HEAD`, then `gh pr create` with
  `Fixes #<number>` in the body. `main` is protected; once the required checks
  pass the merge queue or Hive's merge sweep merges it (the maintainer merges
  high-risk, `hold` or `sentinel-alert` PRs).

See `CONTRIBUTING.md` for contributor workflow and `AGENTS.md` for the full
agent ruleset.
