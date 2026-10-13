# Session summary

Hand-off notes for agent work in this repository, so the next session starts from where the last one stopped instead
of cold (AGENTS.md "Session Completion" step 7). Keep it short: overwrite the "Latest session" section at the end of
each session, in the issue's own commit; durable lessons belong in `.claude/memory/corrections.tsv`,
`docs/reflections/` or `bd remember`, not here.

Format: date, issue/PR worked, outcome, open follow-ups, gotchas for next time.

## Latest session

- **Date:** 2026-10-13
- **Worked:** #14–#27 — port the website repo's CI, review and agent scaffolding (Claude Code workflow, prompts,
  `tooling` invariant tests, apply-suggestions, coverage gate, quality report, risk tiers, metrics, nightly
  compliance, security policy, these hand-off artifacts, sandbox image, triage settings) as one stacked PR per issue.
- **Outcome:** repository settings for #23 and #27 applied through the API; the rest as stacked PRs, each based on the
  previous issue's branch so they merge in order. hive-forge-app had opened competing PRs for several of these issues;
  at the maintainer's request they were closed in favour of the stack (hive's sound hardening ideas folded in).
- **Open follow-ups:** after the coverage-gate PR merges, apply `.github/rulesets/main.json` to the live ruleset.
- **Notes for next session:**
  - Start from `bd ready` / `gh issue list`; AGENTS.md is authoritative and
    `.claude/skills/github-issue-flow/SKILL.md` encodes the per-issue loop.
  - No JDK on the host: run Gradle in a container (JDK 21 + Android SDK image), mounting the checkout and a named
    Gradle home volume. The image needs `jq` for the `tooling` script tests.
  - Adding a workflow means adding its token scopes to `WorkflowInvariantsTest`.
