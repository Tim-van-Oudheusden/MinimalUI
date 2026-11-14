#!/usr/bin/env bash
# Fail on a PIT survivor that the module's reviewed baseline does not list (#412).
#
# Usage:
#   scripts/mutation-check.sh <mutations.xml> <baseline>
#   scripts/mutation-check.sh --write <mutations.xml> <baseline>
#
# <mutations.xml> is a PIT XML report, such as the core/build/reports/pitest/mutations.xml that `./gradlew :core:pitest`
# writes (#427 adds that task). A survivor is a mutant with status SURVIVED or NO_COVERAGE: a change to the code that no
# test noticed. It is keyed by class, method and its descriptor, mutator and PIT's description, without the line
# number, so an edit elsewhere in the file does not change the key.
# The baseline (config/mutation/<module>.txt) lists one key per line, as often as that key survives; `#` lines and
# blank lines are comments. --write replaces the baseline with the report's survivors; review the diff before
# committing it.
#
# Exit status: 0 when every survivor is in the baseline (entries that no longer survive are listed, so the baseline can
# shrink), 1 when one is not, 2 for bad arguments or a missing, unreadable or empty report or a missing baseline.
set -uo pipefail

write=0
if [ "${1:-}" = --write ]; then
  write=1
  shift
fi
if [ "$#" -ne 2 ]; then
  echo "usage: mutation-check.sh [--write] <mutations.xml> <baseline>" >&2
  exit 2
fi
report="$1"
baseline="$2"

if [ ! -r "$report" ]; then
  echo "No PIT report at $report; run the module's pitest task first" >&2
  exit 2
fi

# survivors: the report's surviving mutants as sorted keys, one per line. PIT writes each <mutation> on one line.
survivors() {
  awk '
    function field(name,   open, start, rest) {
      open = "<" name ">"
      start = index($0, open)
      if (!start) return ""
      rest = substr($0, start + length(open))
      return substr(rest, 1, index(rest, "</" name ">") - 1)
    }
    /<mutation[[:space:]]/ {
      total++
      if ($0 !~ /status=.(SURVIVED|NO_COVERAGE)./) next
      print field("mutatedClass") " " field("mutatedMethod") field("methodDescription") " " field("mutator") \
        " " field("description")
    }
    END { if (!total) exit 3 }
  ' "$report" | LC_ALL=C sort
  # awk exits 3 when the report holds no mutation at all: a broken run, not a clean one.
  return "${PIPESTATUS[0]}"
}

found="$(survivors)"
status=$?
if [ "$status" -ne 0 ]; then
  echo "$report has no <mutation> elements; the PIT run produced nothing to check" >&2
  exit 2
fi

if [ "$write" -eq 1 ]; then
  {
    echo "# PIT survivors (#412) reviewed as accepted: class method(descriptor) mutator description, one per survivor."
    echo "# Regenerate with scripts/mutation-check.sh --write <mutations.xml> <this file>, and review the diff."
    if [ -n "$found" ]; then printf '%s\n' "$found"; fi
  } >"$baseline"
  echo "Wrote $(grep -c . <<<"$found") survivor(s) to $baseline"
  exit 0
fi

if [ ! -r "$baseline" ]; then
  echo "No baseline at $baseline; create one with --write" >&2
  exit 2
fi
accepted="$(grep -v -e '^#' -e '^[[:space:]]*$' "$baseline" | LC_ALL=C sort)"

# comm pairs equal lines one to one, so a key that survives twice needs two baseline entries.
new="$(LC_ALL=C comm -23 <(printf '%s\n' "$found" | grep .) <(printf '%s\n' "$accepted" | grep .))"
gone="$(LC_ALL=C comm -13 <(printf '%s\n' "$found" | grep .) <(printf '%s\n' "$accepted" | grep .))"

if [ -n "$gone" ]; then
  echo "$(grep -c . <<<"$gone") baseline entry(ies) in $baseline no longer survive; remove them:"
  printf '  %s\n' "$gone"
fi
if [ -n "$new" ]; then
  echo "::error::$(grep -c . <<<"$new") survivor(s) in $report that $baseline does not list:" >&2
  printf '  %s\n' "$new" >&2
  echo "Add a test that kills each one, or, if no test can, add its line to the baseline with the reason." >&2
  exit 1
fi
echo "Every survivor in $report is in $baseline"
exit 0
