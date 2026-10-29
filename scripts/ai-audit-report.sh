#!/usr/bin/env bash
# AI audit trail: every pull request merged into main since a date, attributed
# to its source (a Hive agent, Claude Code in CI, Dependabot, or other), with
# the agent and model a Hive PR signs, who opened it and who merged it. Reads
# the JSON of `gh pr list --state merged --json
# number,title,author,headRefName,mergedAt,mergedBy,body` on stdin and prints
# Markdown tables.
#
# usage: ai-audit-report.sh --since YYYY-MM-DD
# The window runs from <since> (UTC midnight) to now. Sources, first match wins:
#   Hive              the body has Hive's signature line (`— hive: agent=<name> …
#                     model=<model> …`), or the author is the hive-forge-app
#                     GitHub App (app/hive-forge-app or hive-forge-app[bot])
#   Claude Code (CI)  head branch claude/*
#   Dependabot        head branch dependabot/*
#   Other             anything else: the maintainer or a local agent
set -uo pipefail

usage() {
  echo "usage: $0 --since YYYY-MM-DD < prs.json" >&2
  exit 2
}

since=""
while [ "$#" -gt 0 ]; do
  # A flag must have its value: `shift 2` with one argument left shifts nothing and the loop would never end.
  case "$1" in
    --since) [ "$#" -ge 2 ] || usage; since="$2"; shift 2 ;;
    *) usage ;;
  esac
done
[ -n "$since" ] || usage

# gh prints UTC timestamps (2026-10-01T08:00:00Z), which order as strings, so
# a merge is in the window when its timestamp sorts at or after the bare date.
jq -r --arg since "$since" '
  def cell: gsub("\\|"; "\\|");
  def signature:
    (.body // "") | split("\n") | map(rtrimstr("\r") | select(startswith("— hive: "))) | first;
  def fields: ltrimstr("— hive: ") | [scan("([A-Za-z_]+)=([^ ]+)") | {key: .[0], value: .[1]}] | from_entries;
  def attribute:
    signature as $signature
    | if $signature != null or ((.author.login // "") | IN("app/hive-forge-app", "hive-forge-app[bot]")) then
        ($signature // "" | fields) as $signed
        | {source: "Hive", agent: ($signed.agent // "—"), model: ($signed.model // "—")}
      elif (.headRefName // "") | startswith("claude/") then {source: "Claude Code (CI)", agent: "—", model: "—"}
      elif (.headRefName // "") | startswith("dependabot/") then {source: "Dependabot", agent: "—", model: "—"}
      else {source: "Other", agent: "—", model: "—"}
      end;
  if type != "array" then error("expected a JSON array of pull requests") else . end
  | map(select(.mergedAt >= $since)) | sort_by(.mergedAt) | map(. + {attribution: attribute}) as $merges
  | ($merges | map(.mergedBy.login // null)) as $mergers
  | "# AI audit trail",
    "",
    "Pull requests merged into `main` since \($since).",
    "",
    "## By source",
    "",
    "| Source | Merged PRs |",
    "| --- | ---: |",
    ("Hive", "Claude Code (CI)", "Dependabot", "Other"
      | . as $source
      | "| \($source) | \($merges | map(select(.attribution.source == $source)) | length) |"),
    "| **All** | \($merges | length) |",
    "",
    "## Merged by",
    "",
    "| Account | Merged PRs |",
    "| --- | ---: |",
    # Most merges first; a merge with no recorded merger is listed last so it stands out.
    ($mergers | map(select(. != null)) | group_by(.) | sort_by(0 - length, .[0])[] | "| \(.[0] | cell) | \(length) |"),
    ($mergers | map(select(. == null)) | length | select(. > 0) | "| Unknown (no merger recorded) | \(.) |"),
    "",
    "## Ledger",
    "",
    "| PR | Merged (UTC) | Source | Agent | Model | Author | Merged by | Branch | Title |",
    "| --- | --- | --- | --- | --- | --- | --- | --- | --- |",
    ($merges[]
      | [
          "#\(.number)",
          (.mergedAt[0:16] | sub("T"; " ")),
          .attribution.source,
          (.attribution.agent | cell),
          (.attribution.model | cell),
          (.author.login // "—" | cell),
          (.mergedBy.login // "—" | cell),
          "`\(.headRefName | cell)`",
          (.title | cell)
        ]
      | "| " + join(" | ") + " |")
'
