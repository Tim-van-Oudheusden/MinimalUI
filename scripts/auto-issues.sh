#!/usr/bin/env bash
# Open, update or close the issue that tracks a workflow failing on main.
#
# Usage:
#   WORKFLOW_NAME=CI CONCLUSION=failure RUN_URL=<url> HEAD_SHA=<sha> \
#     GITHUB_REPOSITORY=<owner/repo> scripts/auto-issues.sh
#
# Run by .github/workflows/auto-issues.yml each time a watched workflow
# finishes a run on main. Each workflow has at most one open issue, titled
# "<workflow> fails on main" and labelled ci:
#   - failure, timed_out, startup_failure: opens that issue, or comments on
#     the open one with the new run.
#   - success: comments with the run and closes the open issue.
#   - any other conclusion (cancelled, skipped, ...): changes nothing.
# Prints what it did to stdout. Needs gh (authenticated through GH_TOKEN in
# CI) and jq.
set -euo pipefail

for var in WORKFLOW_NAME CONCLUSION RUN_URL HEAD_SHA GITHUB_REPOSITORY; do
  if [ -z "${!var:-}" ]; then
    echo "$var is not set" >&2
    exit 2
  fi
done

repo="$GITHUB_REPOSITORY"
label=ci
title="${WORKFLOW_NAME} fails on main"
run="[run](${RUN_URL}) on ${HEAD_SHA}"

case "$CONCLUSION" in
  failure | timed_out | startup_failure) failing=true ;;
  success) failing=false ;;
  *)
    echo "${WORKFLOW_NAME} concluded '${CONCLUSION}' on main: no issue change."
    exit 0
    ;;
esac

# gh's --search is fuzzy, so match the title exactly here.
issue="$(gh issue list --repo "$repo" --state open --label "$label" --limit 200 --json number,title |
  jq -r --arg title "$title" 'map(select(.title == $title)) | first | .number // empty')"

if [ "$failing" = true ]; then
  if [ -n "$issue" ]; then
    gh issue comment "$issue" --repo "$repo" --body "Failed again (\`${CONCLUSION}\`): ${run}."
    echo "${WORKFLOW_NAME} failed again on main: commented on #${issue}."
  else
    url="$(gh issue create --repo "$repo" --title "$title" --label "$label" --body-file - <<EOF
The **${WORKFLOW_NAME}** workflow failed on \`main\` (\`${CONCLUSION}\`).

- Run: ${RUN_URL}
- Commit: ${HEAD_SHA}

Opened by \`.github/workflows/auto-issues.yml\`. Later failures on \`main\` are added here as comments; the next
successful run on \`main\` closes this issue.
EOF
    )"
    echo "${WORKFLOW_NAME} failed on main: opened ${url}."
  fi
elif [ -n "$issue" ]; then
  gh issue close "$issue" --repo "$repo" --comment "Passes again on \`main\`: ${run}."
  echo "${WORKFLOW_NAME} passes again on main: closed #${issue}."
else
  echo "${WORKFLOW_NAME} passes on main: no open issue."
fi
