#!/usr/bin/env bash
# Tests for apply-review-suggestions.sh: builds review comments like the
# GitHub API returns them, runs the script in a scratch checkout, and checks
# the edited files and the applied/skipped report.
# Usage: scripts/apply-review-suggestions.test.sh (exits 1 if any case fails).
set -uo pipefail

script="$(cd "$(dirname "$0")" && pwd)/apply-review-suggestions.sh"
root="$(mktemp -d)"
trap 'rm -rf "$root"' EXIT
failures=0
case_number=0

ok() { echo "ok: $1"; }
not_ok() {
  echo "FAIL: $1"
  failures=$((failures + 1))
}

# new_case: a fresh, empty checkout in $checkout and an empty comment list.
new_case() {
  case_number=$((case_number + 1))
  checkout="$root/case$case_number/checkout"
  mkdir -p "$checkout"
  comments='[]'
}

# comment <id> <path> <line|null> <suggestion text> [key=value ...]
# Adds a review comment whose body holds one ```suggestion block. Extra fields
# (start_line, side, start_side, author_association) override the defaults.
comment() {
  local id="$1" path="$2" line="$3" text="$4"
  shift 4
  body="Nit:"$'\n\n'"\`\`\`suggestion"$'\n'"${text}\`\`\`"$'\n'
  raw_comment "$id" "$path" "$line" "$body" "$@"
}

# raw_comment <id> <path> <line|null> <body> [key=value ...]
raw_comment() {
  local id="$1" path="$2" line="$3" body="$4" extra='{}' kv
  shift 4
  for kv in "$@"; do
    extra="$(jq -c --arg k "${kv%%=*}" --arg v "${kv#*=}" \
      '. + {($k): ($v | (tonumber? // (if . == "null" then null else . end)))}' <<< "$extra")"
  done
  comments="$(jq -c --argjson id "$id" --arg path "$path" --argjson line "$line" --arg body "$body" \
    --argjson extra "$extra" \
    '. + [{id: $id, path: $path, line: $line, start_line: null, side: "RIGHT", start_side: null,
           author_association: "OWNER", body: $body} + $extra]' <<< "$comments")"
}

# apply: runs the script in $checkout; sets $status and $output.
apply() {
  printf '%s' "$comments" > "$root/case$case_number/comments.json"
  output="$(cd "$checkout" && COMMENTS_FILE="$root/case$case_number/comments.json" bash "$script" 2>&1)"
  status=$?
}

expect_file() { # <file> <expected content> <description>
  local actual
  actual="$(cat "$1"; printf x)"
  if [ "${actual%x}" = "$2" ]; then ok "$3"; else not_ok "$3 (got $(printf '%q' "${actual%x}"))"; fi
}
expect_output() { # <fixed string> <description>
  if printf '%s' "$output" | grep -qF -- "$1"; then ok "$2"; else not_ok "$2 (output: $(printf '%q' "$output"))"; fi
}
expect_no_output() { # <fixed string> <description>
  if printf '%s' "$output" | grep -qF -- "$1"; then not_ok "$2 (output: $(printf '%q' "$output"))"; else ok "$2"; fi
}
expect_status() { # <status> <description>
  if [ "$status" -eq "$1" ]; then ok "$2"; else not_ok "$2 (status $status, output: $(printf '%q' "$output"))"; fi
}

# A maintainer's single-line suggestion replaces the commented line.
new_case
printf 'one\ntwo\nthree\n' > "$checkout/a.kt"
comment 1 a.kt 2 $'TWO\n'
apply
expect_status 0 'single-line suggestion exits 0'
expect_file "$checkout/a.kt" $'one\nTWO\nthree\n' 'single-line suggestion replaces the commented line'
expect_output 'applied: a.kt:2-2 (comment 1)' 'single-line suggestion is reported as applied'

# Several multi-line suggestions in one file apply bottom-up; one overlapping an applied range is skipped.
new_case
printf '1\n2\n3\n4\n5\n6\n' > "$checkout/a.kt"
comment 1 a.kt 2 $'one-two\n' start_line=1
comment 2 a.kt 5 '' start_line=4
comment 3 a.kt 6 $'six\nseven\n'
comment 4 a.kt 6 $'clash\n' start_line=5
apply
expect_file "$checkout/a.kt" $'one-two\n3\nsix\nseven\n' 'multi-line, deleting and growing suggestions apply together'
expect_output 'skipped: comment 4 on a.kt: overlaps another applied suggestion' 'overlapping suggestion is skipped'

# Suggestions in two files.
new_case
printf 'a\n' > "$checkout/a.kt"
mkdir -p "$checkout/core/src"
printf 'b\n' > "$checkout/core/src/B.kt"
comment 1 core/src/B.kt 1 $'B\n'
comment 2 a.kt 1 $'A\n'
apply
expect_file "$checkout/a.kt" $'A\n' 'first file of two is edited'
expect_file "$checkout/core/src/B.kt" $'B\n' 'second file of two is edited'

# Backticks inside a suggestion line do not end it; a fence must start a line.
new_case
printf '# Title\nold\n' > "$checkout/README.md"
comment 1 README.md 2 $'Mark code with ```kotlin``` fences.\n'
apply
expect_file "$checkout/README.md" $'# Title\nMark code with ```kotlin``` fences.\n' 'backticks mid-line stay in the suggestion'

# CRLF in the comment body is ignored.
new_case
printf 'one\ntwo\n' > "$checkout/a.kt"
raw_comment 1 a.kt 1 $'```suggestion\r\nONE\r\n```\r\n'
apply
expect_file "$checkout/a.kt" $'ONE\ntwo\n' 'CRLF comment body applies without carriage returns'

# Untrusted authors and outdated comments are skipped.
new_case
printf 'one\ntwo\nthree\n' > "$checkout/a.kt"
comment 1 a.kt 1 $'ONE\n' author_association=CONTRIBUTOR
comment 2 a.kt 2 $'TWO\n' author_association=NONE
comment 3 a.kt null $'THREE\n'
apply
expect_status 0 'skips exit 0'
expect_file "$checkout/a.kt" $'one\ntwo\nthree\n' 'untrusted and outdated suggestions leave the file alone'
expect_output 'skipped: comment 1 on a.kt: author CONTRIBUTOR has no write access' 'CONTRIBUTOR suggestion is skipped'
expect_output 'skipped: comment 2 on a.kt: author NONE has no write access' 'NONE suggestion is skipped'
expect_output 'skipped: comment 3 on a.kt: outdated' 'outdated suggestion is skipped'
expect_no_output 'applied:' 'nothing is applied from untrusted or outdated comments'

# MEMBER and COLLABORATOR count as write access.
new_case
printf 'one\ntwo\n' > "$checkout/a.kt"
comment 1 a.kt 1 $'ONE\n' author_association=MEMBER
comment 2 a.kt 2 $'TWO\n' author_association=COLLABORATOR
apply
expect_file "$checkout/a.kt" $'ONE\nTWO\n' 'MEMBER and COLLABORATOR suggestions apply'

# Comments on the base (LEFT) side carry base-file line numbers, so they never edit the head.
new_case
printf 'one\ntwo\n' > "$checkout/a.kt"
comment 1 a.kt 1 $'ONE\n' side=LEFT
comment 2 a.kt 2 $'TWO\n' start_line=1 start_side=LEFT
apply
expect_file "$checkout/a.kt" $'one\ntwo\n' 'base-side suggestions leave the file alone'
expect_output 'skipped: comment 1 on a.kt: comment is on the base side of the diff' 'LEFT-side suggestion is skipped'
expect_output 'skipped: comment 2 on a.kt: comment is on the base side of the diff' 'range starting on the LEFT side is skipped'

# Workflow files cannot be pushed with GITHUB_TOKEN, so they are skipped instead of failing the whole push.
new_case
mkdir -p "$checkout/.github/workflows"
printf 'name: CI\n' > "$checkout/.github/workflows/ci.yml"
printf 'one\n' > "$checkout/a.kt"
comment 1 .github/workflows/ci.yml 1 $'name: Pwned\n'
comment 2 a.kt 1 $'ONE\n'
apply
expect_file "$checkout/.github/workflows/ci.yml" $'name: CI\n' 'workflow file is left alone'
expect_output 'skipped: comment 1 on .github/workflows/ci.yml: workflow files need the workflows permission' 'workflow suggestion is skipped with a reason'
expect_file "$checkout/a.kt" $'ONE\n' 'other suggestions still apply next to a skipped workflow one'

# A comment with two suggestion blocks is ambiguous and skipped.
new_case
printf 'one\n' > "$checkout/a.kt"
raw_comment 1 a.kt 1 $'Either\n```suggestion\nONE\n```\nor\n```suggestion\nUNO\n```\n'
apply
expect_file "$checkout/a.kt" $'one\n' 'two-block suggestion leaves the file alone'
expect_output 'skipped: comment 1 on a.kt: more than one suggestion block' 'two-block suggestion is skipped'

# A range past the end of the file is skipped.
new_case
printf 'one\n' > "$checkout/a.kt"
comment 1 a.kt 3 $'x\n' start_line=2
apply
expect_file "$checkout/a.kt" $'one\n' 'out-of-range suggestion leaves the file alone'
expect_output 'skipped: comment 1 on a.kt: lines 2-3 are not in the file' 'out-of-range suggestion is skipped'

# Paths outside the checkout, absolute paths, missing files and symlinked directories are refused.
new_case
printf 'secret\n' > "$root/case$case_number/outside.txt"
mkdir -p "$root/case$case_number/outdir"
printf 'secret\n' > "$root/case$case_number/outdir/s.txt"
ln -s "$root/case$case_number/outdir" "$checkout/linked"
comment 1 ../outside.txt 1 $'pwned\n'
comment 2 "$root/case$case_number/outside.txt" 1 $'pwned\n'
comment 3 missing.kt 1 $'x\n'
comment 4 linked/s.txt 1 $'pwned\n'
apply
expect_status 0 'refused paths exit 0'
expect_file "$root/case$case_number/outside.txt" $'secret\n' 'relative and absolute paths outside the checkout are not written'
expect_file "$root/case$case_number/outdir/s.txt" $'secret\n' 'symlinked directory outside the checkout is not written'
expect_output 'skipped: comment 3 on missing.kt: not a regular file in the checkout' 'missing file is skipped'
expect_no_output 'applied:' 'nothing outside the checkout is applied'

# Comments without suggestions are ignored; none at all is reported.
new_case
printf 'one\n' > "$checkout/a.kt"
raw_comment 1 a.kt 1 'Looks good, but see the other comment.'
apply
expect_status 0 'no suggestions exits 0'
expect_output 'no suggestions to apply' 'no suggestions is reported'

# A missing comments file is an error, not an empty run.
new_case
output="$(cd "$checkout" && COMMENTS_FILE="$root/absent.json" bash "$script" 2>&1)"
status=$?
if [ "$status" -ne 0 ]; then ok 'missing comments file fails'; else not_ok "missing comments file fails (output: $output)"; fi

if [ "$failures" -ne 0 ]; then
  echo "$failures case(s) failed"
  exit 1
fi
echo 'all cases passed'
