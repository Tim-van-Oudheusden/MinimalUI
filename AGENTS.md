# Agent Instructions

MinimalUI is a minimal Android launcher for keyboard-centric phones (Zinwa, Clicks, Unihertz), inspired by MinUI and
[Cannoli](https://github.com/CannoliHQ/cannoli). Kotlin, Jetpack Compose, Gradle. See `CONTRIBUTING.md` for the project
layout and quality gates.

## Issue Tracking

### GitHub Issues — Primary Source

GitHub issues are the **primary source of truth** for issue information. All issue content (requirements, acceptance
criteria, discussion, links to PRs/commits) lives in GitHub. Access, read, address, and implement issues **exclusively
through the GitHub API** — never through beads alone.

- Repository: `Tim-van-Oudheusden/MinimalUI` (remote `origin`, git@github.com)
- Use the `gh` CLI when available; otherwise call the REST API with `GITHUB_TOKEN`
- Beads only mirrors tracking state (see below); it is not a source of issue information

```bash
gh issue list -S "is:open"      # Find available work
gh issue view <number>          # Read issue, comments, acceptance criteria
gh issue create --title "Summary" --body "Why and what"   # File remaining/follow-up work
gh issue comment <number> --body "Progress / links"       # Report progress
gh pr create --title "type(scope): summary (#<number>)" --body-file <file>   # Land work; body says "Fixes #<number>"
```

### Beads — Tracking Layer

Beads tracks every issue locally (status, dependencies, blocking, history) and mirrors GitHub issues:

```bash
bd github sync --pull-only   # Mirror GitHub -> beads
bd github status             # Show sync state
bd ready                     # Find available work
bd prime                     # Full beads workflow reference
```

> Agent shells (non-interactive) don't source `~/.bashrc`, so `GITHUB_TOKEN` is unset by default. Before any
> `bd github` command, run `export GITHUB_TOKEN="$(gh auth token)"`.

- Use `bd` for task tracking — not markdown TODO lists
- Use `bd remember` for persistent knowledge — not MEMORY.md files

## Sessions with GitHub issues

- Before working an issue: read it with `gh issue view <number>`, claim/assign it there, then branch off an up-to-date
  `main` as `<type>/<number>-<slug>` (e.g. `feat/12-app-search`)
- When done: push the branch and open a pull request whose body says `Fixes #<number>`; the maintainer merges it once
  the required checks pass, and the merge closes the issue

## Session Completion

Work is NOT complete until the issue branch is pushed and its pull request is open.

1. **File issues for remaining work** — `gh issue create` for anything that needs follow-up
2. **Run quality gates** (if code changed) — `./gradlew test lint assembleDebug`
3. **Update issue status** — comment progress on the GitHub issue; run `bd github sync --pull-only`
4. **Push the branch and open a PR** — `main` is protected by `.github/rulesets/main.json`: it only changes through
   merged pull requests with passing checks (`PR review rubric`, `Build, lint, test`):
   ```bash
   bd dolt push
   git push -u origin HEAD
   gh pr create --title "type(scope): summary (#<number>)" --body-file <file>  # follow .github/pull_request_template.md
   git status  # MUST show "up to date with 'origin/<branch>'"
   ```
5. **Clean up** — clear stashes, remove finished worktrees, prune merged branches (`git fetch --prune`)
6. **Verify** — all changes committed, branch pushed, PR open with CI running
7. **Hand off** — overwrite the "Latest session" section of `.claude/session-summary.md` (in the issue's commit) and
   give the PR URL. Log a correction round in `.claude/memory/corrections.tsv`; write the *why* behind a non-obvious
   lesson in `docs/reflections/` (see its README)

Never push to `main`, never force-push, and never say "ready to push when you are" — push the branch and open the PR.

## Git and Version Management

- Commits follow the Conventional Commits specification; one commit per issue, referencing the issue number.
- Bead data lives in the Dolt database (`.beads/embeddeddolt/`, gitignored), synced with `bd dolt push` / `bd dolt pull`.
  `.beads/issues.jsonl` is only an optional export — do not commit it as a sync step.
- The ruleset in `.github/rulesets/main.json` is the source of truth for branch protection; change it by PR, then apply
  it with `gh api -X PUT repos/Tim-van-Oudheusden/MinimalUI/rulesets/<id> --input .github/rulesets/main.json`.

## Testing

- **TDD must be used** — write tests before implementation code
- **One test at a time** — write one failing test, make it pass, refactor, then write the next
- **Confirm the test fails first** — run each new test and verify it fails for the expected reason
- **Test behaviour, not implementation** — assert on observable outputs, return values, and side effects
- **Put logic where it is testable** — pure logic belongs in `core` (plain JVM tests, no emulator); keep `app` a thin
  Android shell over it
- **Run the full suite after every change** — `./gradlew test`
- **Never modify tests to make them pass** — never delete, skip (`@Ignore`), or weaken assertions, and never add
  `@Suppress` to silence failures
- **Don't over-implement** — if the test passes, the implementation is done

## Dependency Management

- **Never add, upgrade, or remove a dependency or Gradle plugin without explicit user approval.** State the coordinates,
  version, and why it is needed
- **All versions live in `gradle/libs.versions.toml`** — never inline a version in a `build.gradle.kts`
- **Verify the artifact exists** on Google Maven or Maven Central with a real repository and history (slopsquatting)
- **Prefer the platform and existing dependencies** — AndroidX and the Kotlin stdlib cover most needs
- **Never upgrade a major version as a side-effect** of another change; major upgrades are their own PR
- **Never hand-edit `gradle/wrapper/gradle-wrapper.jar`**; change the wrapper with `./gradlew wrapper --gradle-version <v>`
