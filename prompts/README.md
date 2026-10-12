# Prompts Catalog

Reusable prompt templates for this repository. They encode the conventions from `AGENTS.md` (authoritative),
`CONTRIBUTING.md` and the PR review rubric (`.github/workflows/review.yml`, `scripts/pr-review-check.sh`), so agent and
human reviews apply the same rules.

## Templates

- [`code-review.md`](./code-review.md): a two-axis review (standards and spec) that matches the quality gates and the
  reviewer rubric.
- [`pull-request.md`](./pull-request.md): a PR body built from an issue and its commit range that follows
  `.github/pull_request_template.md`, so it passes the `PR review rubric` check.

To use one, copy the template into your prompt, fill in the `<BRACKETED>` fields, and follow `AGENTS.md` for issue
tracking and session completion. When a rule changes in `AGENTS.md`, the rubric or the PR template, update the template
that repeats it in the same pull request.
