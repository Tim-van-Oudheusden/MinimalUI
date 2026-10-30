# AI operations runbook

How to watch the agents working in this repository and how to debug them when they misbehave.
[`multi-agent.md`](multi-agent.md) is the coordination contract that says what each agent is supposed to do,
[`metrics/`](metrics/README.md) catalogues what is measured, and [`security/SECURITY-AI.md`](security/SECURITY-AI.md)
covers trust boundaries. This page is the day-to-day side: where to look, what a healthy state looks like, and what to
do when something is stuck.

There is no alerting backend. Every signal below is a GitHub Actions run, a label or an issue, read with `gh` or on the
Actions tab. The exception is a workflow that fails on `main`: `auto-issues.yml` files a `<workflow> fails on main`
issue for it on its own and closes it after the next green run.

The launcher itself runs no AI and sends nothing anywhere. For a bad release see
[`runbooks/bad-release-rollback.md`](runbooks/bad-release-rollback.md), and for the signing key
[`runbooks/release-key.md`](runbooks/release-key.md).

## Where to look

| Question | Signal | Command |
| --- | --- | --- |
| What are agents working on? | Open PRs by head branch: `claude/*` (Claude Code in CI), `<type>/<N>-…` or `<agent>/…` (local agents, Hive), `dependabot/*` | `gh pr list --json number,title,headRefName,author,labels` |
| Who holds an issue? | Assignee, a Hive `hive:claim` comment, or a `hive/claimed-by-<agent>` label | `gh issue view <N> --json assignees,labels,comments` |
| What is queued for Claude Code? | Issues labelled `ai-fix-requested` | `gh issue list --label ai-fix-requested` |
| Did Claude Code run, and how did it end? | `Claude Code` workflow runs | `gh run list --workflow claude.yml` |
| Why is a PR not merging? | Merge queue runs and their `::notice` lines, the `hold` and `sentinel-alert` labels, the merge state | `gh run list --workflow merge-queue.yml`, `gh pr view <N> --json mergeStateStatus,isDraft,labels` |
| How risky is a PR? | `Risk tier` job summary (`Change classification` workflow) | `git diff -z --name-only --no-renames origin/main...<branch> \| scripts/tier-classify.sh -z` |
| Are agent PRs being accepted? | Weekly `PR metrics` job summary | `gh workflow run pr-metrics.yml -f days=28` |
| Is the backlog shrinking? Which ACMM gaps are open? | Weekly `Strategy report` job summary | `gh workflow run strategy-report.yml -f days=28` |
| What did each agent merge, and with which model? | Weekly `AI audit trail` job summary and `ai-audit` artifact | `gh workflow run ai-audit.yml -f days=7` |
| Is `main` healthy? | `Quality report` (every push to `main`), `Nightly compliance`, open `ci` issues titled `… fails on main` | `gh run list --branch main --limit 20`, `gh issue list --label ci` |
| How does an issue link to its change? | The issue → branch → commit → PR chain in [`agent-tasks/`](agent-tasks/README.md) | `gh issue view <N> --json closedByPullRequestsReferences` |
| Have agents been corrected before? | `.claude/memory/corrections.tsv`, [`reflections/`](reflections/README.md) | — |

## Routine checks

- **Every Monday**, after 06:17 UTC: read the `PR metrics`, `Strategy report`, `AI audit trail` and `Quality report`
  job summaries. Look for an agent acceptance rate that keeps falling, a stage label where issues pile up, merges
  whose source you cannot account for, and a coverage floor proposal waiting to be committed.
- **When a PR you expect to merge has not merged within a day**, work through
  [A PR does not merge](#a-pr-does-not-merge).
- **When a `… fails on main` issue opens**, fix it like any other issue; the next green run on `main` closes it.

## Playbooks

### Claude Code did not pick up an `ai-fix-requested` issue

`gh run list --workflow claude.yml` lists one run per label, comment or review event:

- **`skipped`** is expected for every other event. Only an `ai-fix-requested` label that a person applies passes the
  job's `if`; a comment or review passes only when it mentions `@claude` and its author is an `OWNER`, `MEMBER` or
  `COLLABORATOR`.
- **`skipped` on the `issues` event that labelled the issue** means a bot added the label (the Hive ACMM evaluation
  files its issues already labelled, as `hive-forge-app`). The job answers only a person's label; Claude Code's action
  would refuse a bot sender anyway ("Workflow initiated by non-human actor"). To hand the issue over, a maintainer
  removes the label and adds it again:
  `gh issue edit <N> --remove-label ai-fix-requested && gh issue edit <N> --add-label ai-fix-requested`.
- **`failure` at "Refuse fork pull requests"**: the PR comes from a fork. Review it locally; the workflow never runs
  fork code.
- **`failure` at "Run Claude Code"** for any other reason: read the log (`gh run view <run-id> --log-failed`). An
  authentication error means the `CLAUDE_CODE_OAUTH_TOKEN` secret has expired or is missing.

Before handing an issue to Claude Code, check that no one else holds it ([two agents worked the same
issue](#two-agents-worked-the-same-issue)).

### A PR does not merge

The merge queue (`merge-queue.yml`, logic in `scripts/merge-queue.sh`) only picks a PR that meets every condition in
[`multi-agent.md`](multi-agent.md#merge-queue). Check them in order:

1. **Draft, `hold` or `sentinel-alert`:** `gh pr view <N> --json isDraft,labels`. Hive puts `hold` on its own PRs and
   `sentinel-alert` on PRs that touch workflows, policies or security docs; the queue puts `hold` on a PR that failed
   verification.
2. **Required checks:** `gh pr view <N> --json mergeStateStatus` must be `CLEAN`. `BLOCKED` means a required check
   (`PR review rubric`, `Build, lint, test`, `Unit-test coverage floors`, `Static checks`, `End-to-end tests`) failed
   or has not run, or a review thread is unresolved. `BEHIND` and `DIRTY` mean the branch needs a rebase or a conflict
   fixed.
3. **High risk:** the queue leaves high-risk PRs for a maintainer and logs `#<N> is high risk` as a notice in its run.
   The tier comes from the rules in [`risk-tiers.md`](risk-tiers.md), and `.github/CODEOWNERS` has requested the
   maintainer's review on them.
4. **Fork:** the queue never picks a PR from a fork.

If the PR meets every condition and still sits there, no event has started the queue since it became ready. Drain the
queue by hand with `gh workflow run merge-queue.yml`.

### The merge queue put `hold` on a PR

The queue merged the PR onto the latest `main` and `./gradlew check assembleDebug` failed on the result; its comment on
the PR links the failed run. Most often a PR that merged in the meantime conflicts with this one. Rebase on `main`, fix
it, push, then remove the label (`gh pr edit <N> --remove-label hold`) to queue the PR again.

### `main` shows no CI run after a queued merge

Merges made with `GITHUB_TOKEN` start no push workflows, so after each merge the queue starts `CI`, `Coverage gate`,
`E2E` and `Quality report` on `main` itself. If those runs are missing, look at the queue's `land` job, then start them
by hand:
`for w in ci.yml coverage-gate.yml e2e.yml quality-report.yml; do gh workflow run "$w" --ref main; done`.

### The PR review rubric fails

`scripts/pr-review-check.sh` has two requirements: a Conventional Commits title, and a body that links the issue
(`Fixes #<N>`, or `Refs #<N>` for partial work). Bot authors are exempt from the link. Edit the PR title or body and
the check runs again; no push is needed.

### Two agents worked the same issue

Agents claim an issue by assigning it, or with a Hive claim comment and label
([claim first](multi-agent.md#how-work-flows)). Find the competing PRs with `gh pr list --state all --search "<N>"`,
keep the one from the agent holding the claim, and close the other with a comment linking it. If an agent ignored a
claim it could see, treat it as agent misbehaviour (below).

### An agent acted outside its instructions

Ignored rules, an injection attempt that got through, changes outside the issue's scope. Stop the agent
([stopping the agents](#stopping-the-agents)), then follow
[SECURITY-AI.md § Reporting](security/SECURITY-AI.md#reporting): rotate any exposed secret first, open an issue, and add
a line to `.claude/memory/corrections.tsv` saying where the fix is encoded. If the lesson needs context, write a
reflection in [`reflections/`](reflections/README.md).

### Beads is out of step with GitHub

GitHub is the source of truth. In a non-interactive shell, export the token first, then pull only:

```bash
export GITHUB_TOKEN="$(gh auth token)"
bd github sync --pull-only
```

## Stopping the agents

To pause agent work, for example during an incident or while a bad instruction gets fixed, stop each entry point:

| Stop | Command | Undo |
| --- | --- | --- |
| Claude Code in CI | `gh workflow disable claude.yml` | `gh workflow enable claude.yml` |
| Automatic merges | `gh workflow disable merge-queue.yml` | `gh workflow enable merge-queue.yml`, then `gh workflow run merge-queue.yml` |
| One PR | `gh pr edit <N> --add-label hold` | `gh pr edit <N> --remove-label hold` |
| Hive | Pause it in the Hive dashboard; it runs outside this repository | Resume it there |

CI keeps running on every PR either way, so nothing lands on `main` without passing the gates.
