#!/usr/bin/env bash
# Print a Markdown quality report for the repository to stdout.
#
# Usage:
#   scripts/quality-report.sh > quality-report.md
#
# Runs every quality gate (`./gradlew test`, `lint`, `assembleDebug`), then `./gradlew :<module>:koverXmlReport` for
# every module in .github/auto-qa-tuning.json, and reports pass/fail per gate and the function/line coverage per
# module. Gradle output goes to stderr so stdout stays a clean report. Every gate and coverage run happens even when an
# earlier one fails; a failed coverage run reports n/a. The coverage floors themselves are enforced by
# scripts/coverage-check.sh, and scripts/auto-qa-tuner.sh reads the coverage rows printed here.
#
# Environment: $GRADLE replaces ./gradlew, $QA_TUNING_CONFIG the floors file, $COVERAGE_ROOT the root the Kover
# reports are read from (see scripts/lib/coverage.sh); $GITHUB_SHA names the commit.
#
# Exit status: 0 when everything passed, 1 when a gate or coverage run failed, 2 when the floors file lists no module.
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# shellcheck source=scripts/lib/coverage.sh
. "$ROOT/scripts/lib/coverage.sh"
cd "$ROOT" || exit 2

gradle="${GRADLE:-./gradlew}"
config="${QA_TUNING_CONFIG:-$ROOT/.github/auto-qa-tuning.json}"

if ! modules="$(jq -er '.floors | keys[]' "$config" 2>/dev/null)"; then
  echo "No modules in $config: expected {\"floors\": {\"<module>\": {...}}}" >&2
  exit 2
fi

commit="${GITHUB_SHA:-$(git rev-parse HEAD 2>/dev/null || echo unknown)}"
failed=0

echo "# Quality report"
echo
echo "Commit: \`${commit}\`"
echo
echo "## Quality gates"
echo
echo "| Gate | Result |"
echo "| ---- | ------ |"
for gate in test lint assembleDebug; do
  if "$gradle" "$gate" >&2; then
    result="✅ pass"
  else
    result="❌ fail"
    failed=1
  fi
  echo "| \`./gradlew ${gate}\` | ${result} |"
done

echo
echo "## Unit-test coverage"
echo
echo "| Module | Functions | Lines |"
echo "| ------ | --------- | ----- |"
for module in $modules; do
  report="$(coverage_report_path "$module")"
  # A report left by an earlier build must not stand in for this run's.
  rm -f "$report"
  methods_covered=-
  lines_covered=-
  if "$gradle" ":$module:koverXmlReport" >&2 && [ -f "$report" ]; then
    read -r methods_missed methods_covered lines_missed lines_covered < <(coverage_counters "$report")
  fi
  # A failed run makes its numbers meaningless: report n/a rather than a partial measurement.
  if [ "${methods_covered:--}" = - ] || [ "${lines_covered:--}" = - ]; then
    echo "| ${module} | ❌ n/a | ❌ n/a |"
    failed=1
  else
    functions="$(coverage_percent "$methods_missed" "$methods_covered")"
    lines="$(coverage_percent "$lines_missed" "$lines_covered")"
    echo "| ${module} | ${functions}% | ${lines}% |"
  fi
done

exit "$failed"
