# Pull Request Prompt

Write the PR title and body for `<ISSUE>` (issue title: `<TITLE>`), implemented by commits `<COMMIT_RANGE>` on branch
`<BRANCH>` in `MinimalUI`.

Rules:

- Title: Conventional Commits with the issue number, `type(scope): summary (#<ISSUE>)`. Allowed types are `build`,
  `chore`, `ci`, `docs`, `feat`, `fix`, `perf`, `refactor`, `revert`, `style` and `test`. The scope is lower-case.
  `scripts/pr-review-check.sh` rejects anything else.
- The body follows `.github/pull_request_template.md` section by section and links the issue with `Fixes #<ISSUE>`
  (`Refs #<ISSUE>` only if the PR deliberately leaves part of the issue open, saying what and why on the same line).
- Evidence, not claims: paste real command output. Never tick a box that wasn't done. Write `n/a` and the reason
  instead.

Template:

```markdown
## Summary

**What:** <one or two sentences: what changes for the user or contributor>

**Why:** <the issue's goal; note any verified deviation from the ticket, such as a stale premise or a review-driven
change>

Fixes #<ISSUE>

## Testing

<The test that failed before the change (its output) and passes after it; the exact commands run.>

- [ ] New/changed behaviour covered by a test written first (TDD); test confirmed failing before implementation
- [ ] Test asserts observable behaviour, never implementation internals
- [ ] UI changes checked on a device or emulator (keyboard-only navigation included)

## Quality gates

- [ ] `./gradlew test` passes (full suite)
- [ ] `./gradlew lint` passes
- [ ] `./gradlew assembleDebug` passes

## Notes for the reviewer

<Trade-offs, harness limits (for example "no emulator in this sandbox"), pre-existing problems found, and anything
risky.>

## Dependencies

- None <or: coordinates, exact version, why, and where the maintainer approved it>
```
