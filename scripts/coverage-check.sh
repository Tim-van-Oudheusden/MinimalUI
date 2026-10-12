#!/usr/bin/env bash
# Enforce a module's unit-test coverage floors.
#
# Usage:
#   scripts/coverage-check.sh <module> <functions> <lines>
#
# Reads the module's Kover XML report from <module>/build/reports/kover/report.xml (produce it first with
# `./gradlew :<module>:koverXmlReport`; $COVERAGE_ROOT replaces the repository root when set) and fails if the
# module-level function (JaCoCo METHOD) or line coverage is below the given floor, in percent. The floors live in
# .github/auto-qa-tuning.json.
#
# Exit status: 0 at or above both floors, 1 below a floor, 2 for bad arguments or a missing/unreadable report.
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# shellcheck source=scripts/lib/coverage.sh
. "$ROOT/scripts/lib/coverage.sh"

if [ "$#" -ne 3 ]; then
  echo "usage: coverage-check.sh <module> <functions> <lines>" >&2
  exit 2
fi
module="$1"
floor_functions="$2"
floor_lines="$3"

for floor in "$floor_functions" "$floor_lines"; do
  if ! [[ "$floor" =~ ^[0-9]+(\.[0-9]+)?$ ]] || ! awk -v f="$floor" 'BEGIN { exit !(f <= 100) }'; then
    echo "Floor '$floor' must be a percentage between 0 and 100" >&2
    exit 2
  fi
done

report="$(coverage_report_path "$module")"
if [ ! -f "$report" ]; then
  echo "No coverage report at $report; run ./gradlew :$module:koverXmlReport first" >&2
  exit 2
fi

read -r methods_missed methods_covered lines_missed lines_covered < <(coverage_counters "$report")
if [ "${methods_covered:--}" = - ]; then
  echo "$report has no module-level METHOD counter" >&2
  exit 2
fi
if [ "${lines_covered:--}" = - ]; then
  echo "$report has no module-level LINE counter" >&2
  exit 2
fi

functions="$(coverage_percent "$methods_missed" "$methods_covered")"
lines="$(coverage_percent "$lines_missed" "$lines_covered")"
echo "Coverage ($module): functions ${functions}% (floor ${floor_functions}%), lines ${lines}% (floor ${floor_lines}%)"

# Compare the exact ratio: a rounded 100.00% may still be below a 100% floor.
meets() {
  awk -v m="$1" -v c="$2" -v f="$3" 'BEGIN { exit !(c * 100 >= f * (m + c)) }'
}

failed=0
if ! meets "$methods_missed" "$methods_covered" "$floor_functions"; then
  echo "::error::$module functions coverage ${functions}% is below its floor of ${floor_functions}%" >&2
  failed=1
fi
if ! meets "$lines_missed" "$lines_covered" "$floor_lines"; then
  echo "::error::$module lines coverage ${lines}% is below its floor of ${floor_lines}%" >&2
  failed=1
fi
exit "$failed"
