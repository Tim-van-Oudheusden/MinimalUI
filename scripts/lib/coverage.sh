# shellcheck shell=bash
# Coverage report helpers, sourced by scripts/coverage-check.sh and scripts/quality-report.sh.
#
# Report path contract: `./gradlew :<module>:koverXmlReport` writes the module's Kover XML report (JaCoCo format) to
#   <root>/<module>/build/reports/kover/report.xml
# where <root> is the repository root, or $COVERAGE_ROOT when set (tests point it at fixture reports).

# coverage_report_path <module>: the report the contract above names.
coverage_report_path() {
  printf '%s/%s/build/reports/kover/report.xml\n' "${COVERAGE_ROOT:-$ROOT}" "$1"
}

# coverage_counters <report>: prints "<methods-missed> <methods-covered> <lines-missed> <lines-covered>" from the
# report-level METHOD and LINE counters (the direct children of <report>, not the per-package/class/method ones).
# A counter the report lacks prints as "-". Works whether the XML is pretty-printed or on one line.
coverage_counters() {
  grep -o '<[^>]*>' "$1" | awk '
    function attr(tag, name) {
      if (!match(tag, name "=\"[^\"]*\"")) return ""
      return substr(tag, RSTART + length(name) + 2, RLENGTH - length(name) - 3)
    }
    BEGIN { mm = "-"; mc = "-"; lm = "-"; lc = "-" }
    /^<[?!]/ { next }
    /\/>$/ {
      if (depth == 1 && /^<counter[[:space:]]/) {
        type = attr($0, "type")
        if (type == "METHOD") { mm = attr($0, "missed"); mc = attr($0, "covered") }
        if (type == "LINE") { lm = attr($0, "missed"); lc = attr($0, "covered") }
      }
      next
    }
    /^<\// { depth--; next }
    { depth++ }
    END { print mm, mc, lm, lc }
  '
}

# coverage_percent <missed> <covered>: coverage in percent with two decimals; 100.00 when there is nothing to cover.
coverage_percent() {
  awk -v m="$1" -v c="$2" 'BEGIN { if (m + c == 0) print "100.00"; else printf "%.2f\n", c * 100 / (m + c) }'
}
