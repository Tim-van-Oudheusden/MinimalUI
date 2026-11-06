# Session summary

Hand-off notes for agent work in this repository, so the next session starts from where the last one stopped instead
of cold (AGENTS.md "Session Completion" step 7). Keep it short: overwrite the "Latest session" section at the end of
each session, in the issue's own commit; durable lessons belong in `.claude/memory/corrections.tsv`,
`docs/reflections/` or `bd remember`, not here.

Format: date, issue/PR worked, outcome, open follow-ups, gotchas for next time.

## Latest session

- **Date:** 2026-11-06
- **Worked:** #377 → `scanner/fix-377`. `scripts/merge-queue.sh` gets `start_workflow` (three tries, 10 s apart);
  `land` keeps starting main's other gates and the next run after a failed dispatch, then fails the run with an
  `::error`. `next` reads the listing into a variable so a failed `gh pr list` fails the job.
- **Outcome:** PR open, `Closes #377`. Tests and linters were not run locally; CI is the verdict.
- **Open follow-ups:** none.
- **Notes for next session:** none.
