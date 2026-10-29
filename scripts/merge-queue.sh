#!/usr/bin/env bash
# Merge queue for main: merges ready pull requests one at a time, each after
# re-running the check gate on top of the latest main. Driven by
# .github/workflows/merge-queue.yml; see docs/multi-agent.md#merge-queue.
#
# Usage:
#   scripts/merge-queue.sh next
#       From a full-history checkout of main: pick the oldest pull request that
#       is ready to merge and write number=, head= and main= to $GITHUB_OUTPUT.
#       Writes nothing when none is ready.
#   scripts/merge-queue.sh land <number> <head-sha> <verified-main-sha>
#       Merge the pull request at <head-sha> if main is still the commit it was
#       verified on and the pull request is still ready, then start main's push
#       workflows (a merge made with GITHUB_TOKEN starts none) and the next run.
#   scripts/merge-queue.sh reject <number> <run-url>
#       Hold a pull request that failed verification, link the run, move on.
#
# Ready means: from this repository (not a fork), not a draft, neither a `hold`
# nor a `sentinel-alert` label (Hive's flag on a sensitive change, removed by a
# maintainer after review), every required check passing (merge state CLEAN),
# and not high risk by scripts/tier-classify.sh — high-risk changes wait for
# the maintainer.
# Needs gh authenticated (GH_TOKEN) and pointed at the repository (GH_REPO, or
# run from a checkout).
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
HOLD_LABEL=hold
# jq test that a pull request carries neither label that keeps it out.
UNHELD='all(.labels[]; .name != "hold" and .name != "sentinel-alert")'
# Every workflow that runs on a push to main. Release is left out: it runs only
# when app/build.gradle.kts changes, which is high risk, so the queue never
# merges such a change.
MAIN_PUSH_WORKFLOWS=(ci.yml coverage-gate.yml e2e.yml quality-report.yml)

# Picks up runs that land, reject or skip a pull request, so each merge is
# verified against the main it lands on.
queue_next_run() {
  gh workflow run merge-queue.yml --ref main
}

# GitHub computes the merge state lazily and answers UNKNOWN until it has.
merge_state() {
  local state attempt
  for attempt in 1 2 3; do
    state="$(gh pr view "$1" --json mergeStateStatus --jq .mergeStateStatus)"
    [ "$state" != UNKNOWN ] && break
    [ "$attempt" -lt 3 ] && sleep 5
  done
  echo "$state"
}

# Prints the risk tier of the change from main to <sha>, as tier-classifier.yml
# rates it: renames listed under their old path too.
tier_of() {
  git diff -z --name-only --no-renames "HEAD...$1" | "$HERE/tier-classify.sh" -z | sed -n 's/^tier=//p'
}

next() {
  local number head tier
  # gh lists open pull requests by default; --jq runs gh's built-in jq, so the
  # runner needs no jq.
  while read -r number head; do
    [ "$(merge_state "$number")" = CLEAN ] || continue
    git fetch --no-tags --quiet origin "refs/pull/${number}/head"
    # A push since the listing: the run its checks start sees it.
    [ "$(git rev-parse FETCH_HEAD)" = "$head" ] || continue
    tier="$(tier_of "$head")"
    if [ "$tier" = high ]; then
      echo "::notice title=Merge queue::#${number} is high risk (docs/risk-tiers.md); the maintainer merges it"
      continue
    fi
    {
      echo "number=${number}"
      echo "head=${head}"
      echo "main=$(git rev-parse HEAD)"
    } >> "$GITHUB_OUTPUT"
    return 0
  done < <(gh pr list --base main --limit 100 \
    --json number,headRefOid,isDraft,isCrossRepository,labels \
    --jq "sort_by(.number) | .[]
      | select((.isDraft or .isCrossRepository) | not)
      | select(${UNHELD})
      | \"\\(.number) \\(.headRefOid)\"")
}

land() {
  local number="$1" head="$2" verified_main="$3" main ready workflow
  main="$(git ls-remote origin refs/heads/main | cut -f1)"
  if [ "$main" != "$verified_main" ]; then
    echo "::notice title=Merge queue::main moved since #${number} was verified; verifying again"
    queue_next_run
    return 0
  fi

  ready="$(gh pr view "$number" --json isDraft,labels --jq "(.isDraft | not) and ${UNHELD}")"
  if [ "$ready" != true ] || [ "$(merge_state "$number")" != CLEAN ]; then
    echo "::notice title=Merge queue::#${number} stopped being ready during verification; skipped"
    queue_next_run
    return 0
  fi

  # --match-head-commit refuses the merge if the branch moved past what was verified.
  gh pr merge "$number" --merge --match-head-commit "$head"
  for workflow in "${MAIN_PUSH_WORKFLOWS[@]}"; do
    gh workflow run "$workflow" --ref main
  done
  queue_next_run
}

reject() {
  local number="$1" run_url="$2"
  gh pr edit "$number" --add-label "$HOLD_LABEL"
  gh pr comment "$number" --body "The merge queue held this pull request: the check gate failed on it merged with the latest main (${run_url}). Fix it and remove the \`${HOLD_LABEL}\` label to queue it again."
  queue_next_run
}

usage() {
  echo "usage: $0 next | land <number> <head-sha> <verified-main-sha> | reject <number> <run-url>" >&2
  exit 2
}

case "${1:-}" in
  next) [ "$#" -eq 1 ] || usage; next ;;
  land) [ "$#" -eq 4 ] || usage; land "$2" "$3" "$4" ;;
  reject) [ "$#" -eq 3 ] || usage; reject "$2" "$3" ;;
  *) usage ;;
esac
