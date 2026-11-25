# Beads retirement

Date: 2026-11-25. Issue: #493.

## Decision

GitHub issues are the only tracker. The beads (`bd`) mirror is retired, as website did in
Tim-van-Oudheusden/website#988 (decision record: `research/beads_retirement.md` there): `.beads/`, the `bd` steps in
the agent instructions and docs, `bd` in `Dockerfile.sandbox`, and the `.beads` rules in CODEOWNERS, the labeler and the
risk tiers are gone. Agents no longer export `GITHUB_TOKEN="$(gh auth token)"` either; local GitHub access goes through
`gh` and its keyring token ([SECURITY-AI.md § Secrets](../security/SECURITY-AI.md#secrets)).

## The Dolt data on the GitHub remote

Kept, as website kept its own. `refs/dolt/data` (last push 2026-11-17, `e990458`) holds the full Dolt database and
`refs/heads/__dolt_remote_info__` its remote metadata; git history holds the removed `.beads/` files. Every mirrored
issue still exists on GitHub, so nothing there is needed for current work. Deleting the two refs cannot be undone, so it
stays a maintainer call:

```bash
git push origin --delete refs/dolt/data refs/heads/__dolt_remote_info__
```

## Local cleanup on a clone that ran `bd`

Removing the tracked files does not touch local state. The root `.gitignore` still ignores `.beads/`,
`.beads-credential-key`, `.dolt/` and `*.db`, so leftovers cannot be committed by accident.

- Remove the beads git hooks: `.git/hooks/*` entries that call `bd`, or unset `core.hooksPath` if it points at
  `.beads/hooks`.
- Delete `.beads/` and `.beads-credential-key`. Spotless no longer skips `.beads/`, so a leftover `*.json` or `*.yaml`
  there can fail `spotlessCheck`.
- Drop any `export GITHUB_TOKEN=...` from shell profiles that existed only for `bd github sync`.
