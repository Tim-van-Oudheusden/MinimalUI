#!/usr/bin/env bash
# Self-tune the unit-test coverage floors from observed coverage.
#
# Usage:
#   scripts/auto-qa-tuner.sh [--config <path>] [--report <path>] [--write]
#
# Reads the floors from the tuning config (default .github/auto-qa-tuning.json) and the observed coverage from a
# scripts/quality-report.sh report (default quality-report.md), then prints a Markdown table proposing, per module and
# metric, the floor raised to the observed coverage minus the config's margin, rounded down to a whole percent. Floors
# are never lowered, and a module without a coverage row (its run failed) keeps its floors. With --write the proposed
# floors are saved back to the config.
#
# Exit status: 0 on success, 2 for bad arguments, a missing report, or a malformed config.
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

usage() {
  echo "usage: auto-qa-tuner.sh [--config <path>] [--report <path>] [--write]" >&2
  exit 2
}

config="$ROOT/.github/auto-qa-tuning.json"
report="quality-report.md"
write=0
while [ "$#" -gt 0 ]; do
  case "$1" in
    --config)
      [ "$#" -ge 2 ] || usage
      config="$2"
      shift 2
      ;;
    --report)
      [ "$#" -ge 2 ] || usage
      report="$2"
      shift 2
      ;;
    --write)
      write=1
      shift
      ;;
    *) usage ;;
  esac
done

if [ ! -f "$report" ]; then
  echo "No quality report at $report; run scripts/quality-report.sh > $report first" >&2
  exit 2
fi

# A floor is a percentage; the margin is a non-negative number of percentage points.
valid='
  def percent: type == "number" and . >= 0 and . <= 100;
  (.margin | type == "number" and . >= 0)
  and (.floors | type == "object" and length > 0
    and all(.[]; type == "object" and (.functions | percent) and (.lines | percent)))
'
if ! jq -e "$valid" "$config" >/dev/null 2>&1; then
  echo "Malformed $config: expected {\"margin\": N, \"floors\": {\"<module>\": {\"functions\": N, \"lines\": N}}}" \
    "with percentages between 0 and 100" >&2
  exit 2
fi

# Coverage rows look like `| core | 97.50% | 93.10% |`; a failed run's `| app | ❌ n/a | ❌ n/a |` does not match.
# Proposals are computed in hundredths and rounded before flooring, so float error (70.1 - 0.1 = 69.999...) cannot
# drop a value below a whole percent.
tune="$(
  cat <<'JQ'
($report | split("\n")
  | map(capture("^\\| (?<module>\\S+) \\| (?<functions>[0-9]+(\\.[0-9]+)?)% \\| (?<lines>[0-9]+(\\.[0-9]+)?)% \\|$"))
  | map({key: .module, value: .}) | from_entries) as $observed
| .margin as $margin
| [.floors | to_entries[] | .key as $module | .value as $floors | ("functions", "lines") as $metric
    | $floors[$metric] as $floor
    | $observed[$module][$metric] as $seen
    | {
        module: $module,
        metric: $metric,
        floor: $floor,
        observed: $seen,
        proposed: (if $seen == null then $floor
          else [$floor, ((($seen | tonumber) - $margin) * 100 | round) / 100 | floor] | max end)
      }] as $rows
| {rows: $rows, config: (reduce $rows[] as $r (.; .floors[$r.module][$r.metric] = $r.proposed))}
JQ
)"
if ! tuned="$(jq --rawfile report "$report" "$tune" "$config")"; then
  echo "Could not tune $config from $report" >&2
  exit 2
fi

jq -r '
  "## Coverage floor tuning",
  "",
  "Margin: \(.config.margin) percentage points below observed coverage.",
  "",
  "| Module | Metric | Floor | Observed | Proposed |",
  "| ------ | ------ | ----- | -------- | -------- |",
  (.rows[] | "| \(.module) | \(.metric) | \(.floor)% | \(if .observed == null then "n/a" else "\(.observed)%" end)"
    + " | \(.proposed)%\(if .proposed > .floor then " ⬆️" else "" end) |")
' <<<"$tuned"

if [ "$write" -eq 1 ]; then
  # Keys and their order are kept; only the floors change.
  if ! jq '.config' <<<"$tuned" >"$config"; then
    echo "Could not write $config" >&2
    exit 2
  fi
  echo "Wrote the proposed floors to $config" >&2
fi
