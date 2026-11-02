#!/usr/bin/env bash
# PR review rubric: the mechanically checkable items of the PR template.
# Reads PR_TITLE, PR_BODY and PR_AUTHOR from the environment; reports every
# failed item and exits 1 if any failed. Bot authors (login ending in "[bot]")
# are exempt from every item: their titles and bodies come from the bot's own
# format (Hive's titles start with an emoji, #303), not from the PR template.
set -uo pipefail

title="${PR_TITLE:-}"
body="${PR_BODY:-}"
author="${PR_AUTHOR:-}"
failures=0

fail() {
  echo "::error::$1"
  failures=$((failures + 1))
}

if [[ "$author" == *"[bot]" ]]; then
  echo "ok: bot author, title and issue link not checked"
  exit 0
fi

types='build|chore|ci|docs|feat|fix|perf|refactor|revert|style|test'
if [[ "$title" =~ ^($types)(\([a-z0-9._/-]+\))?!?:\ .+ ]]; then
  echo "ok: title follows Conventional Commits"
else
  fail "PR title '$title' must follow Conventional Commits, e.g. 'feat(launcher): add app search' (types: ${types//|/, })"
fi

# The keyword must be a whole word ("hotfix #3" is no link) and may end in a colon ("Closes: #10");
# "owner/repo#N" is GitHub's long form.
if printf '%s' "$body" |
  grep -Eiq '(^|[^[:alnum:]_])(fix(es|ed)?|close[sd]?|resolve[sd]?|refs?):?[[:space:]]+([[:alnum:]_.-]+/[[:alnum:]_.-]+)?#[0-9]+'; then
  echo "ok: body links a GitHub issue"
else
  fail "PR body must link its GitHub issue, e.g. 'Fixes #123' (see .github/pull_request_template.md)"
fi

[ "$failures" -eq 0 ] || exit 1
