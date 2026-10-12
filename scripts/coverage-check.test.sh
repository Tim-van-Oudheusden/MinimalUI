#!/usr/bin/env bash
# Tests for coverage-check.sh: runs it against small JaCoCo XML reports and
# checks the pass/fail verdict at and around the floors.
# Usage: scripts/coverage-check.test.sh (exits 1 if any case fails).
set -uo pipefail

check="$(dirname "$0")/coverage-check.sh"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
failures=0

# report <file> <methods missed> <methods covered> <lines missed> <lines covered>
# Writes a JaCoCo-shaped report: per-class counters first, the report totals last.
report() {
  cat > "$1" <<XML
<?xml version="1.0" encoding="UTF-8" standalone="yes"?><!DOCTYPE report PUBLIC "-//JACOCO//DTD Report 1.1//EN" "report.dtd"><report name="m"><package name="p"><class name="p/A"><counter type="LINE" missed="99" covered="1"/><counter type="METHOD" missed="99" covered="1"/></class><counter type="LINE" missed="99" covered="1"/></package><counter type="INSTRUCTION" missed="1" covered="1"/><counter type="LINE" missed="$4" covered="$5"/><counter type="METHOD" missed="$2" covered="$3"/><counter type="CLASS" missed="0" covered="1"/></report>
XML
}

# expect <pass|fail> <description> <report> <min functions> <min lines> [output fragment]
expect() {
  local want="$1" description="$2" got output
  if output="$(bash "$check" m "$3" "$4" "$5" 2>&1)"; then got=pass; else got=fail; fi
  if [ "$got" = "$want" ] && { [ -z "${6:-}" ] || printf '%s' "$output" | grep -qF -- "$6"; }; then
    echo "ok: $description"
  else
    echo "FAIL: $description (expected $want${6:+ with \"$6\"}, got $got: $(printf '%s' "$output" | tr '\n' '|'))"
    failures=$((failures + 1))
  fi
}

# 9 of 10 methods (90%), 49 of 50 lines (98%).
report "$work/r.xml" 1 9 1 49
expect pass 'both metrics above their floors' "$work/r.xml" 80 90
expect pass 'both metrics exactly at their floors' "$work/r.xml" 90 98
expect fail 'functions just below the floor' "$work/r.xml" 90.01 98 'below'
expect fail 'lines just below the floor' "$work/r.xml" 90 98.01 'below'
expect fail 'both below' "$work/r.xml" 95 99
expect pass 'reads the report totals, not the first class counters' "$work/r.xml" 89 97
expect pass 'prints both percentages and floors' "$work/r.xml" 90 98 'functions 90.00%, lines 98.00% (floors: 90% functions, 98% lines)'

# 2 of 3 methods (66.67%), 1 of 3 lines (33.33%).
report "$work/thirds.xml" 1 2 2 1
expect pass 'fractional coverage at a fractional floor' "$work/thirds.xml" 66.66 33.33
expect fail 'fractional coverage below a fractional floor' "$work/thirds.xml" 66.67 33.34

# Full coverage meets a 100% floor.
report "$work/full.xml" 0 4 0 12
expect pass '100% meets a 100% floor' "$work/full.xml" 100 100

# A module with no tests has no report; that is 0%, so only a zero floor passes.
expect pass 'missing report meets zero floors' "$work/absent.xml" 0 0 'no coverage report'
expect fail 'missing report fails a non-zero floor' "$work/absent.xml" 0 1 'below'

# A report without totals is not silently treated as covered.
printf '<report name="m"></report>' > "$work/empty.xml"
expect fail 'report without LINE and METHOD totals' "$work/empty.xml" 0 0 'totals'

# Bad floors are usage errors, not a pass.
expect fail 'non-numeric floor' "$work/r.xml" abc 90 'floor'
expect fail 'floor above 100' "$work/r.xml" 90 101 'floor'
expect fail 'missing floor argument' "$work/r.xml" 90 ''

if [ "$failures" -ne 0 ]; then
  echo "$failures case(s) failed"
  exit 1
fi
echo 'all cases passed'
