# Where a pull request came from, shared by scripts/ai-audit-report.sh and scripts/pr-metrics.sh so the two reports
# cannot disagree about which pull requests are agent work (#342). Reads one entry of
# `gh pr list --json author,headRefName,body`; without a body only the author and the branch decide.
#
# Sources, first match wins:
#   Hive             the body has Hive's signature line ("— hive: agent=<a> … model=<m> …"; contributor runs
#                    leave out agent=), or the author is the hive-worker-bee GitHub App, formerly hive-forge-app
#                    (gh pr list reports it as app/hive-worker-bee, the REST API as hive-worker-bee[bot];
#                    the old names are still matched)
#   Claude Code (CI) head branch claude/*
#   Dependabot       head branch dependabot/*
#   Other            anything else

def sources: ["Hive", "Claude Code (CI)", "Dependabot", "Other"];

# Hive ends every PR body it writes with its signature line; null when there is none.
def signature:
  (.body // "") | split("\n") | map(rtrimstr("\r") | select(startswith("— hive: "))) | .[0];

def source:
  (.headRefName // "") as $branch
  | if signature != null
      or ((.author.login // "")
        | IN("app/hive-worker-bee", "hive-worker-bee[bot]", "app/hive-forge-app", "hive-forge-app[bot]"))
    then "Hive"
    elif ($branch | startswith("claude/")) then "Claude Code (CI)"
    elif ($branch | startswith("dependabot/")) then "Dependabot"
    else "Other" end;

# The agent side of the PR acceptance metric.
def agent_authored: source | IN("Hive", "Claude Code (CI)");
