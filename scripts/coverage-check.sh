#!/usr/bin/env bash
# Enforce overall code-coverage floors for one module's unit tests.
#
# Usage:
#   scripts/coverage-check.sh <module> <jacoco-xml-report> <min-functions-pct> <min-lines-pct>
#
# Reads the report totals (the METHOD and LINE counters directly under
# <report>, which JaCoCo writes last) and fails if function or line coverage is
# below its floor. A module whose tests produced no report counts as 0%, so it
# passes only a zero floor. Floors live in .github/auto-qa-tuning.json.
set -euo pipefail

usage="usage: coverage-check.sh <module> <jacoco-xml-report> <min-functions-pct> <min-lines-pct>"
module="${1:?$usage}"
report="${2:?$usage}"
min_funcs="${3:?$usage}"
min_lines="${4:?$usage}"

for floor in "$min_funcs" "$min_lines"; do
  if ! [[ $floor =~ ^[0-9]+(\.[0-9]+)?$ ]] || ! awk -v f="$floor" 'BEGIN { exit !(f <= 100) }'; then
    echo "Coverage ($module): floor '$floor' is not a percentage between 0 and 100" >&2
    exit 2
  fi
done

# totals <counter type>: "<covered> <total>" from the report's last counter of that type, or nothing.
totals() {
  { grep -o "<counter type=\"$1\" missed=\"[0-9]*\" covered=\"[0-9]*\"/>" "$report" || true; } | tail -n 1 |
    sed -E 's/.*missed="([0-9]+)" covered="([0-9]+)".*/\2 \1/' |
    awk '{ print $1, $1 + $2 }'
}

if [ -f "$report" ]; then
  method_totals="$(totals METHOD)"
  line_totals="$(totals LINE)"
  if [ -z "$method_totals" ] || [ -z "$line_totals" ]; then
    echo "Coverage ($module): no METHOD and LINE totals in $report" >&2
    exit 1
  fi
else
  echo "Coverage ($module): no coverage report at $report (no unit tests ran), counting as 0%"
  method_totals="0 0"
  line_totals="0 0"
fi

pct() { awk -v c="${1% *}" -v t="${1#* }" 'BEGIN { printf "%.2f", (t > 0 ? 100 * c / t : 0) }'; }
funcs="$(pct "$method_totals")"
lines="$(pct "$line_totals")"

echo "Coverage ($module): functions ${funcs}%, lines ${lines}% (floors: ${min_funcs}% functions, ${min_lines}% lines)"

if ! awk -v f="$funcs" -v l="$lines" -v mf="$min_funcs" -v ml="$min_lines" \
    'BEGIN { exit !(f >= mf && l >= ml) }'; then
  echo "Coverage ($module) is below the configured floor" >&2
  exit 1
fi
