# Pull Request Prompt

Create a PR body for `<ISSUE>` (title: `<TITLE>`) implemented by commits
`<COMMIT_RANGE>` in `MinimalUI`.

Rules: Conventional Commits title, one commit per issue, issue referenced in
commits, body says `Fixes #<number>`. Fill in `.github/pull_request_template.md`:

## Summary

- **What:** one or two sentences: what changes for the user.
- **Why:** the issue's goal; note any verified deviations from the ticket
  (stale premises, review-driven changes).

## Testing

- Failing-before / passing-after output for the changed path.
- For UI changes: the device or emulator used and that keyboard-only
  navigation was checked.

## Quality gates

- The exact commands run and their results (`./gradlew check assembleDebug`).

## Notes for the reviewer

- Documented tradeoffs, harness limitations, or pre-existing issues touched.

## Dependencies

- Only if changed: coordinates, exact version, why, and the approval.
