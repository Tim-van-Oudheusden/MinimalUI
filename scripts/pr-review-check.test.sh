#!/usr/bin/env bash
# Tests for pr-review-check.sh: runs the rubric against PR titles, bodies and
# authors and checks that each one passes or fails as expected.
# Usage: scripts/pr-review-check.test.sh (exits 1 if any case fails).
set -uo pipefail

check="$(dirname "$0")/pr-review-check.sh"
failures=0

# expect <pass|fail> <description> <title> <body> [author]
expect() {
  local want="$1" description="$2" got
  if PR_TITLE="$3" PR_BODY="$4" PR_AUTHOR="${5:-someone}" bash "$check" >/dev/null 2>&1; then
    got=pass
  else
    got=fail
  fi
  if [ "$got" = "$want" ]; then
    echo "ok: $description"
  else
    echo "FAIL: $description (expected $want, got $got)"
    failures=$((failures + 1))
  fi
}

title='fix(launcher): survive a missing app'
body='Fixes #12'

# Title: Conventional Commits.
expect pass 'title with type and scope' "$title" "$body"
expect pass 'title without scope' 'docs: explain the build' "$body"
expect pass 'title with breaking-change marker' 'feat(ui)!: drop touch mode' "$body"
expect pass 'title with dotted/slashed scope' 'build(gradle/libs.versions): pin junit' "$body"
expect fail 'title with unknown type' 'feature(ui): add search' "$body"
expect fail 'title with capitalised type' 'Fix(ui): add search' "$body"
expect fail 'title with capitalised scope' 'fix(UI): add search' "$body"
expect fail 'title without space after colon' 'fix(ui):add search' "$body"
expect fail 'title with empty summary' 'fix(ui): ' "$body"
expect fail 'title without type' 'Add app search' "$body"
expect fail 'empty title' '' "$body"

# Body: every keyword form links the issue.
for keyword in fix fixes fixed close closes closed resolve resolves resolved ref refs; do
  expect pass "body '$keyword #12'" "$title" "$keyword #12"
done
expect pass 'body keyword in upper case' "$title" 'FIXES #12'
expect pass "body 'Fixes owner/repo#N' (GitHub's long form)" "$title" 'Fixes Tim-van-Oudheusden/MinimalUI#12'
expect pass 'body link after other text on the line' "$title" 'Part of the launcher work. Closes #4'
expect pass 'body link on a later line' "$title" $'## Summary\n\nWhat: x\n\nResolves #7'
expect pass 'body link inside parentheses' "$title" '(Fixes #12)'
expect pass 'body link with CRLF line endings' "$title" $'## Summary\r\n\r\nFixes #12\r\n'

# Body: no issue linked.
expect fail 'empty body' "$title" ''
expect fail 'body without a link' "$title" 'Small cleanup.'
expect fail "body 'hotfix #3' (keyword is only a word suffix)" "$title" 'Follow-up to the hotfix #3; no issue yet.'
expect fail "body 'prefix #1'" "$title" 'Uses the new prefix #1 format'
expect fail "body 'prefs #2'" "$title" 'See prefs #2'
expect fail "body 'x_fixes #12' (underscore is a word character)" "$title" 'x_fixes #12'
expect fail 'body with a bare #N and no keyword' "$title" 'See #12'
expect fail 'body with keyword and number on separate lines' "$title" $'Fixes\n#12'
expect fail 'body with keyword but no number' "$title" 'Fixes #'
expect fail 'unfilled pull request template' "$title" "$(cat "$(dirname "$0")/../.github/pull_request_template.md")"

# Author: bots need no issue link, but still need a valid title.
expect pass 'bot author without an issue link' 'build(deps): bump junit' 'Bumps junit.' 'dependabot[bot]'
expect fail 'bot author with an invalid title' 'Bump junit' 'Bumps junit.' 'dependabot[bot]'
expect fail "author merely containing 'bot'" "$title" 'Small cleanup.' 'robotics-fan'

# Both checks failing still exits non-zero.
expect fail 'invalid title and no issue link' 'Add app search' 'Small cleanup.'

if [ "$failures" -ne 0 ]; then
  echo "$failures case(s) failed"
  exit 1
fi
echo 'all cases passed'
