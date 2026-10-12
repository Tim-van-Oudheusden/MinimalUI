#!/usr/bin/env bash
# PR review rubric: the mechanically checkable items of the PR template.
# Reads PR_TITLE, PR_BODY and PR_AUTHOR from the environment; reports every
# failed item and exits 1 if any failed.
set -uo pipefail

title="${PR_TITLE:-}"
body="${PR_BODY:-}"
author="${PR_AUTHOR:-}"
failures=0

fail() {
  echo "::error::$1"
  failures=$((failures + 1))
}

types='build|chore|ci|docs|feat|fix|perf|refactor|revert|style|test'
if [[ "$title" =~ ^($types)(\([a-z0-9._/-]+\))?!?:\ .+ ]]; then
  echo "ok: title follows Conventional Commits"
else
  fail "PR title '$title' must follow Conventional Commits, e.g. 'feat(launcher): add app search' (types: ${types//|/, })"
fi

if [[ "$author" == *"[bot]" ]]; then
  echo "ok: bot author, issue link not required"
elif printf '%s' "$body" | grep -Eiq '(fix(es|ed)?|close[sd]?|resolve[sd]?|refs?)[[:space:]]+#[0-9]+'; then
  echo "ok: body links a GitHub issue"
else
  fail "PR body must link its GitHub issue, e.g. 'Fixes #123' (see .github/pull_request_template.md)"
fi

[ "$failures" -eq 0 ] || exit 1
