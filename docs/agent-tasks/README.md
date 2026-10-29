# Agent task traceability

Every change an agent makes traces back to the GitHub issue that asked for it, and every issue leads forward to the
change that resolved it. GitHub issues are the task records; this directory does not copy them. It describes the
chain of links from an issue to the code on `main`, which links are enforced, and how to follow the chain either way.
The coordination rules behind it are in [`../multi-agent.md`](../multi-agent.md).

## The chain

| Link | Carries the issue number as | Example | Enforced by |
| --- | --- | --- | --- |
| Issue | The task itself, filed by a person, an agent or a workflow; Hive's issues end with `— hive: agent=<name>` | #246 | — |
| Claim | Assignee, or a Hive `<!-- hive:claim … -->` comment with its `hive/claimed-by-<agent>` label | `gh issue edit 246 --add-assignee @me` | Convention ([`multi-agent.md`](../multi-agent.md#how-work-flows)) |
| Branch | `<type>/<N>-<slug>` for local agents; `claude/*` for Claude Code in CI (`claude/issue-<N>-…` when it starts from an issue); Hive uses `<type>/<N>-<slug>` or `<agent>/<slug>` | `docs/246-multi-agent` | Convention |
| Commit | `(#<N>)` at the end of the Conventional Commits subject | `docs(agents): document multi-agent orchestration (#246)` | Convention |
| Pull request | `Fixes #<N>` in the body (or `Closes`, `Resolves`, `Refs`); Hive's PRs also end with `— hive: agent=<name> … model=<model> …` | #248 | `PR review rubric` check (`scripts/pr-review-check.sh`), required on `main` |
| Merge | Merge commit `Merge pull request #<PR> from <owner>/<branch>`, made by the merge queue or a maintainer | `9af64d1` | `main` only changes through merged PRs (`.github/rulesets/main.json`) |
| Close | The merge closes the issue through `Fixes #<N>` | #246 closed by #248 | GitHub |

The required rubric check is what guarantees the chain: a pull request into `main` cannot merge without an issue link.
Branch names and commit subjects carry the number by convention, which makes them quicker to search but not
authoritative. `Refs #<N>` satisfies the rubric without closing the issue; use it when a PR covers only part of one.

**Exempt:** pull requests from bot accounts (`*[bot]`, in practice Dependabot) need no issue link. Their task is the
dependency update itself, and the branch `dependabot/<ecosystem>/<package>` names it.

## Following the chain

From an issue to the change that resolved it:

```bash
gh issue view <N> --json state,assignees,labels,closedByPullRequestsReferences
gh pr list --state all --search "<N>"
git log --oneline --grep "(#<N>)" origin/main
```

From a line of code back to its issue:

```bash
git blame -L <line>,<line> <file>              # the commit
git log -1 --format=%s <commit>                # its subject ends in (#<N>)
gh pr list --state merged --search "<commit>"  # the PR, whose body links the issue
```

From an agent to everything it did: filter pull requests by author or branch, for example
`gh pr list --state all --author app/hive-forge-app` for Hive, `gh pr list --state all --search "head:claude/"` for
Claude Code in CI. The `— hive: agent=<name>` signature at the end of a Hive PR body names the Hive agent.

## When the chain breaks

- **Work with no issue:** file one (`gh issue create`) before opening the pull request. An agent that finds work
  outside its issue files a new issue rather than widening the PR.
- **A PR that links the wrong issue:** edit the body; the rubric check runs again on the edit.
- **An issue closed with no linked PR:** reopen it, or comment with the PR or commit that resolved it so the record is
  complete.
