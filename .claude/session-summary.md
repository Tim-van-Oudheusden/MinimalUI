# Session summary

Hand-off notes for agent work in this repository, so the next session starts from where the last one stopped instead
of cold (AGENTS.md "Session Completion" step 7). Keep it short: overwrite the "Latest session" section at the end of
each session, in the issue's own commit; durable lessons belong in `.claude/memory/corrections.tsv`,
`docs/reflections/` or `bd remember`, not here.

Format: date, issue/PR worked, outcome, open follow-ups, gotchas for next time.

## Latest session

- **Date:** 2026-10-29
- **Worked:** #246 → `docs/246-multi-agent`. Added `docs/multi-agent.md` (ACMM L6 `acmm:multi-agent-orchestration`):
  the agents that work the repo (Hive agents via `hive-forge-app`, ACMM evaluation, Claude Code in CI, local agents,
  the auto-issues and apply-suggestions workflows), the issue → claim → PR → human merge flow, the claim signals to
  check before starting (assignee, `hive:claim` comment lease, covering PR, running `Claude Code` job), worktree
  isolation, hand-off files, the Hive `stage/*` labels and where a person decides. Linked from README's documentation
  table and CONTRIBUTING "Finding and claiming work".
- **Outcome:** PR open; docs only.
- **Open follow-ups:** none. The `Claude Code` run for #246 (40934622464) failed because `hive-forge-app` applied
  `ai-fix-requested` and claude-code-action refuses bot actors; by design, recorded in `docs/multi-agent.md`.
- **Notes for next session:**
  - The emulator on this host segfaults with SwiftShader Vulkan; use `-gpu swangle_indirect`
    (`docs/reflections/2026-10-29-emulator-on-this-host.md`).
  - Sandbox Gradle in this checkout needs `--project-cache-dir /tmp/pcache`, otherwise Spotless trips over host paths
    left in `.gradle/`.
  - In the sandbox, Spotless can fail with `Could not read path '…/build/…'` when it runs alongside compilation in one
    `check`: run `spotlessCheck` on its own first, then `check assembleDebug -x spotlessCheck`.
