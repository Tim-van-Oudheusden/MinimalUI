# Testing

## Nightly compliance

`.github/workflows/nightly-compliance.yml` runs nightly (`23 3 * * *` UTC) and on manual dispatch, for checks that can
go stale without a code change. It has two jobs:

1. **Submit dependency graph** runs `gradle/actions/dependency-submission`, which resolves every Gradle configuration
   and sends the graph to GitHub's dependency graph, so Dependabot alerts cover the current build. Only this job has
   `contents: write`, which the submission API needs.
2. **Nightly compliance** runs even when the submission failed. It validates the wrapper jar with
   `gradle/actions/wrapper-validation`, then runs `scripts/nightly-compliance.sh > nightly-compliance.md`. The report is
   published as the job summary and the `nightly-compliance` artifact.

The script reports one pass/fail row per check, runs every check even when an earlier one fails, and exits non-zero if
any failed:

| Check | Fails when |
| ----- | ---------- |
| Dependency graph submission | the submission job's result (`DEPENDENCY_GRAPH_RESULT`) is not `success` |
| Gradle wrapper jar checksum | the wrapper-validation step's outcome (`WRAPPER_VALIDATION_OUTCOME`) is not `success`, so `gradle/wrapper/gradle-wrapper.jar` does not match a checksum Gradle publishes |
| Dependabot alerts (high+) | an open Dependabot alert has severity `high` or `critical` and its GHSA id is not on the ignore list, or the alerts API cannot be read |

A check with no recorded outcome fails, so running the script outside the workflow shows the two workflow-fed rows as
failed.

The alerts come from `gh api repos/$GITHUB_REPOSITORY/dependabot/alerts?state=open`. The job's `GITHUB_TOKEN` reads them
through the `vulnerability-alerts: read` permission
([workflow syntax: permissions](https://docs.github.com/en/actions/reference/workflows-and-actions/workflow-syntax#permissions)),
so no secret is needed. actionlint 1.7.12 does not know that scope yet; `.github/actionlint.yaml` ignores that one
message for this workflow ([rhysd/actionlint#713](https://github.com/rhysd/actionlint/issues/713)).

### Accepting an advisory

Add its GHSA id to `DEFAULT_ALERTS_IGNORE` in `scripts/nightly-compliance.sh`, with a comment on the line above that gives
the reason and the date to revisit. Remove the entry once a fixed version is in the build. Dismissing the alert on
GitHub also removes it from the open alerts, but leaves no reviewed record in the repository.

### Running it locally

```bash
DEPENDENCY_GRAPH_RESULT=success WRAPPER_VALIDATION_OUTCOME=success scripts/nightly-compliance.sh
```

This reads the alerts with your `gh` login (needs `jq`). Set `ALERTS_JSON=<file>` to read an alerts JSON array from a
file instead, and `ALERTS_IGNORE="GHSA-… GHSA-…"` to replace the ignore list; the tests in
`tooling/src/test/kotlin/dev/buildwithtim/minimalui/tooling/NightlyComplianceScriptTest.kt` use both.
