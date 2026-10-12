# Reflections

Cross-session knowledge for agent work in this repository: short write-ups of what a session learned that the next one
should not have to rediscover. Read the newest few before starting work. Add one when a session uncovers something
non-obvious about the repo, its tooling or the environment agents run in.

## How this fits with the other learning artifacts

| Artifact | Holds | Lifetime |
| --- | --- | --- |
| `.claude/session-summary.md` | The hand-off from the last session only | Overwritten each session |
| `.claude/memory/corrections.tsv` | One line per correction round | Append-only log |
| `docs/reflections/` | The *why* behind a lesson, with its context | One file per reflection, kept |

If a lesson becomes a rule, encode it where agents already look (`AGENTS.md`, `CLAUDE.md`, a skill, a lint check or a
test) and link that from the reflection.

## Corrections log format

`.claude/memory/corrections.tsv` is tab-separated, with the header row `date`, `behaviour`, `correction`. Append one row
per correction round, meaning each time a human (or a failing check) had to correct what an agent did:

| Column | Holds |
| --- | --- |
| `date` | ISO date, `YYYY-MM-DD` |
| `behaviour` | What the agent did wrong, in one sentence |
| `correction` | What to do instead, and where that is now encoded as `file:rule` (for example `AGENTS.md:Testing`), or `not yet` with a follow-up issue |

Never edit or delete an earlier row. If a later round reverses a correction, append a new row that says so. Keep tabs
out of the text, so each row stays three columns.

## Reflection format

One Markdown file per reflection, named `YYYY-MM-DD-short-slug.md`, with these sections:

- **Context:** the issue or PR, and what was being attempted.
- **What happened:** the surprise, failure or discovery.
- **Lesson:** what to do differently, stated as guidance.
- **Encoded in:** where the lesson now lives, or "not yet" with a follow-up issue.

Add the new file to the index below, newest first.

## Index

None yet.
