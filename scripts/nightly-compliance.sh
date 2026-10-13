#!/usr/bin/env bash
# Print a Markdown nightly compliance report for the repository to stdout.
#
# Usage:
#   scripts/nightly-compliance.sh > nightly-compliance.md
#
# Checks that can go stale without a code change, so they run on a schedule
# (.github/workflows/nightly-compliance.yml) rather than only on push:
#   - Dependency graph submission: the workflow's dependency-submission job
#     sent the resolved Gradle dependency graph to GitHub, so Dependabot
#     alerts reflect the current build. Outcome read from
#     DEPENDENCY_GRAPH_RESULT (the job's `needs.<job>.result`).
#   - Gradle wrapper jar checksum: the gradle/actions/wrapper-validation step
#     matched gradle/wrapper/gradle-wrapper.jar against Gradle's published
#     checksums. Outcome read from WRAPPER_VALIDATION_OUTCOME (the step's
#     `steps.<id>.outcome`).
#   - Dependabot alerts (high+): no open high or critical Dependabot alert,
#     except those in ALERTS_IGNORE below. Alerts come from
#     `gh api repos/$GITHUB_REPOSITORY/dependabot/alerts` (needs a token that
#     can read Dependabot alerts), or from the JSON array in the file named by
#     ALERTS_JSON when set.
# A check with no recorded outcome fails. Diagnostics go to stderr so stdout
# stays a clean report. Every check runs even when an earlier one fails; the
# script exits non-zero if any failed. Needs jq.
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT" || exit

# Advisories accepted after review, one GHSA id per line with the reason and
# the date to revisit. Remove an entry once a fix ships and the build picks
# it up. The ALERTS_IGNORE environment variable (space-separated ids)
# replaces this list; the tests use it.
#   (none yet)
DEFAULT_ALERTS_IGNORE=()

commit="${GITHUB_SHA:-$(git rev-parse HEAD 2>/dev/null || echo unknown)}"
failed=0
details=""

row() {
  if [ "$2" -eq 0 ]; then
    echo "| $1 | ✅ pass |"
  else
    echo "| $1 | ❌ fail |"
    failed=1
  fi
}

# Fails unless the GitHub Actions outcome in $2 is "success".
outcome_row() {
  local check="$1" outcome="$2" source="$3"
  if [ "$outcome" = success ]; then
    row "$check" 0
  else
    echo "$check: ${source} is '${outcome:-unset}', expected 'success'" >&2
    details+="- ${check}: \`${source}\` is \`${outcome:-unset}\`"$'\n'
    row "$check" 1
  fi
}

# Prints the open Dependabot alerts as one JSON array on stdout.
read_alerts() {
  if [ -n "${ALERTS_JSON:-}" ]; then
    cat "$ALERTS_JSON"
    return
  fi
  local repo="${GITHUB_REPOSITORY:-$(gh repo view --json nameWithOwner --jq .nameWithOwner 2>/dev/null)}"
  # --paginate prints one array per page; jq -s merges them.
  gh api --paginate "repos/${repo}/dependabot/alerts?state=open&per_page=100" | jq -s 'add // []'
}

if [ -n "${ALERTS_IGNORE+set}" ]; then
  read -ra ignore <<<"$ALERTS_IGNORE"
else
  ignore=("${DEFAULT_ALERTS_IGNORE[@]}")
fi

echo "# Nightly compliance"
echo
echo "Commit: \`${commit}\`"
echo
echo "| Check | Result |"
echo "| ----- | ------ |"

outcome_row "Dependency graph submission" "${DEPENDENCY_GRAPH_RESULT:-}" DEPENDENCY_GRAPH_RESULT
outcome_row "Gradle wrapper jar checksum" "${WRAPPER_VALIDATION_OUTCOME:-}" WRAPPER_VALIDATION_OUTCOME

alerts_check=1
if alerts="$(read_alerts)" && jq -e 'type == "array"' >/dev/null 2>&1 <<<"$alerts"; then
  blocking="$(jq -r --args '
    $ARGS.positional as $ignore
    | .[]
    | select(.state == "open")
    | select(.security_advisory.severity == "high" or .security_advisory.severity == "critical")
    | select(.security_advisory.ghsa_id as $id | $ignore | index($id) | not)
    | "- \(.security_advisory.ghsa_id) (\(.security_advisory.severity)) \(.dependency.package.name): \(.security_advisory.summary) \(.html_url)"
  ' "${ignore[@]}" <<<"$alerts")" && {
    if [ -z "$blocking" ]; then
      alerts_check=0
    else
      echo "Open high/critical Dependabot alerts:" >&2
      echo "$blocking" >&2
      details+="${blocking}"$'\n'
    fi
  }
else
  echo "Could not read Dependabot alerts (needs a token with Dependabot alerts read access): ${alerts:-no output}" >&2
  details+="- Dependabot alerts: could not read the alerts API; see the job log"$'\n'
fi
row "Dependabot alerts (high+)" "$alerts_check"

if [ -n "$details" ]; then
  echo
  echo "## Failures"
  echo
  printf '%s' "$details"
fi

exit "$failed"
