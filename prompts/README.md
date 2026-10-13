# Prompts Catalog

Reusable prompt templates for this repository. They encode the conventions from
`AGENTS.md` (authoritative) and `CONTRIBUTING.md` so agent and human reviews stay
consistent.

## Templates

- [`code-review.md`](./code-review.md) — two-axis review checklist (standards +
  spec) matching the repo's quality gates and the `PR review rubric`.
- [`pull-request.md`](./pull-request.md) — PR body prepared from an issue and its
  commit, following `.github/pull_request_template.md`.

Usage: copy a template into your prompt, fill the bracketed fields, and follow
`AGENTS.md` for issue tracking and the session-completion workflow. On a pull
request, ask Claude Code for a review with a comment such as:

```text
@claude review this PR using prompts/code-review.md against issue #<number>
```
