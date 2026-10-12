#!/usr/bin/env bash
# Tests for tier-classify.sh: feeds changed paths to the classifier and checks
# each file's tier, the overall tier, and how bad policies are rejected.
# Usage: scripts/tier-classify.test.sh (exits 1 if any case fails).
set -uo pipefail

dir="$(cd "$(dirname "$0")" && pwd)"
classify="$dir/tier-classify.sh"
doc="$dir/../docs/risk-tiers.md"
shipped_policy="$dir/../.github/policies/risk-tiers.policy"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
failures=0

ok() { echo "ok: $1"; }
not_ok() {
  echo "FAIL: $1"
  failures=$((failures + 1))
}

# run <paths, one per line> [policy file]: sets $status and $output.
run() {
  if [ -n "${2:-}" ]; then
    output="$(printf '%s' "$1" | RISK_TIER_POLICY="$2" bash "$classify" 2>&1)"
  else
    output="$(printf '%s' "$1" | bash "$classify" 2>&1)"
  fi
  status=$?
}

# expect_tier <tier> <description> <paths>: the overall tier of the change.
expect_tier() {
  run "$3"
  if [ "$status" -eq 0 ] && printf '%s\n' "$output" | grep -qx "tier=$1"; then
    ok "$2"
  else
    not_ok "$2 (expected tier=$1, got status $status: $(printf '%s' "$output" | tr '\n' '|'))"
  fi
}

# expect_line <regex> <description>: a line of the last run's output.
expect_line() {
  if printf '%s\n' "$output" | grep -Eq "$1"; then ok "$2"; else not_ok "$2 (no line matching '$1')"; fi
}

# expect_rejected <description> <policy text> [message fragment]
expect_rejected() {
  printf '%s' "$2" > "$work/policy"
  run 'app/src/main/kotlin/A.kt' "$work/policy"
  if [ "$status" -ne 0 ] && ! printf '%s' "$output" | grep -q 'tier=' &&
    { [ -z "${3:-}" ] || printf '%s' "$output" | grep -qF "$3"; }; then
    ok "$1"
  else
    not_ok "$1 (status $status: $(printf '%s' "$output" | tr '\n' '|'))"
  fi
}

# Shipped policy: high-risk paths.
for path in \
  .github/workflows/ci.yml \
  .github/rulesets/main.json \
  .github/policies/risk-tiers.policy \
  scripts/pr-review-check.sh \
  gradle/libs.versions.toml \
  gradle/wrapper/gradle-wrapper.jar \
  gradle/wrapper/gradle-wrapper.properties \
  build.gradle.kts \
  settings.gradle.kts \
  app/build.gradle.kts \
  core/build.gradle.kts \
  gradle.properties \
  gradlew \
  gradlew.bat \
  app/src/main/AndroidManifest.xml \
  AGENTS.md \
  CLAUDE.md \
  .claude/settings.json \
  .claude/skills/github-issue-flow/SKILL.md \
  .cursor/rules/agent-guidance.mdc \
  .beads/config.yaml \
  prompts/code-review.md \
  .gitattributes; do
  expect_tier high "high: $path" "core/src/main/kotlin/A.kt"$'\n'"$path"
done

# Shipped policy: application code and tests are medium.
for path in \
  app/src/main/kotlin/dev/buildwithtim/minimalui/MainActivity.kt \
  app/src/main/res/values/strings.xml \
  core/src/main/kotlin/dev/buildwithtim/minimalui/core/AppOrdering.kt \
  core/src/test/kotlin/dev/buildwithtim/minimalui/core/AppOrderingTest.kt; do
  expect_tier medium "medium: $path" "docs/risk-tiers.md"$'\n'"$path"
done

# Shipped policy: prose is low.
expect_tier low 'low: docs and top-level Markdown' $'docs/risk-tiers.md\nREADME.md\nCONTRIBUTING.md\nBRANDING.md'
expect_tier low 'low: Markdown inside a module' 'core/NOTES.md'

# Unclassified paths are medium, so new kinds of files still get a real review.
expect_tier medium 'medium: path matching no rule' 'somewhere/new.txt'
expect_tier medium 'medium: LICENSE matches no rule' 'LICENSE'

# Output shape.
expect_tier high 'highest tier of any file wins' $'README.md\napp/src/main/kotlin/A.kt\ngradle/libs.versions.toml'
expect_line '^high +gradle/libs\.versions\.toml$' 'lists each file with its own tier (high)'
expect_line '^medium +app/src/main/kotlin/A\.kt$' 'lists each file with its own tier (medium)'
expect_line '^low +README\.md$' 'lists each file with its own tier (low)'
expect_tier low 'empty change set is low' ''
expect_tier high 'last path without a trailing newline is classified' $'README.md\nscripts/x.sh'
expect_tier low 'blank lines are skipped' $'\nREADME.md\n\n'

# Custom policy: first match wins, comments and blank lines ignored, CRLF tolerated.
printf '# secrets first\nhigh  vault/keys.md\n\nlow   vault/*\r\nlow   *.md\n' > "$work/policy"
run $'vault/keys.md\nvault/readme.txt\nnotes.md\nsrc/app.kt' "$work/policy"
if [ "$status" -eq 0 ]; then ok 'custom policy accepted'; else not_ok "custom policy accepted (status $status: $output)"; fi
expect_line '^high +vault/keys\.md$' 'first matching rule wins over a later broader one'
expect_line '^low +vault/readme\.txt$' 'rule on a CRLF line still matches'
expect_line '^low +notes\.md$' 'later rule applies when earlier ones miss'
expect_line '^medium +src/app\.kt$' 'path matching no custom rule is medium'
expect_line '^tier=high$' 'custom policy overall tier'

# Bad policies fail without printing a tier.
expect_rejected 'unknown tier is rejected' 'critical  deploy/*' "critical"
expect_rejected 'rule without a glob is rejected' 'high' "expected '<tier> <glob>'"
expect_rejected 'rule with an extra field is rejected' 'high  app/*  # trailing comment' "expected '<tier> <glob>'"
expect_rejected 'error names the policy line' $'low docs/*\nhgih scripts/*' ':2:'
run 'README.md' "$work/absent.policy"
if [ "$status" -ne 0 ] && ! printf '%s' "$output" | grep -q 'tier='; then
  ok 'missing policy file fails without a tier'
else
  not_ok "missing policy file fails without a tier (status $status: $output)"
fi

# docs/risk-tiers.md stays in sync with the shipped policy.
for tier in low medium high; do
  if grep -Eiq "^#+ .*\\b$tier\\b" "$doc"; then ok "doc has a section for tier $tier"; else not_ok "doc has a section for tier $tier"; fi
done
[ -r "$shipped_policy" ] || not_ok "shipped policy $shipped_policy is readable"
while read -r tier glob _ || [ -n "${tier:-}" ]; do
  tier="${tier%$'\r'}"
  glob="${glob%$'\r'}"
  [ -z "$tier" ] || [[ $tier == \#* ]] && continue
  if grep -qF "\`$glob\`" "$doc"; then
    ok "doc lists policy glob $glob"
  else
    not_ok "doc lists policy glob \`$glob\` (docs/risk-tiers.md is out of sync with the policy)"
  fi
done < "$shipped_policy" 2>/dev/null

if [ "$failures" -ne 0 ]; then
  echo "$failures case(s) failed"
  exit 1
fi
echo 'all cases passed'
