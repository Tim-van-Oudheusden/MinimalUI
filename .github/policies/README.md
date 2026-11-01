# Policies

Policy as code for this repository. Each policy is machine-read by CI or a
script and is covered by a test, so changing one is a reviewed code change.

| Policy | Enforced by | Documented in |
| --- | --- | --- |
| [`workflow-permissions.yml`](workflow-permissions.yml): `GITHUB_TOKEN` scopes per workflow and job | `WorkflowPermissionsPolicyTest` | [`docs/security/SECURITY-AI.md`](../../docs/security/SECURITY-AI.md) |
| [`../risk-assessment.yml`](../risk-assessment.yml): path to risk tier rules | `scripts/tier-classify.sh`, `TierClassifyScriptTest` | [`docs/risk-tiers.md`](../../docs/risk-tiers.md) |
| [`../labeler.yml`](../labeler.yml): path to label rules | `PolicyLabelParityTest` | [`docs/risk-tiers.md`](../../docs/risk-tiers.md) |
| [`../CODEOWNERS`](../CODEOWNERS): owners of high-risk paths | `CodeownersTest` | [`docs/risk-tiers.md`](../../docs/risk-tiers.md) |
| [`../rulesets/`](../rulesets): branch protection | GitHub rulesets | [`CONTRIBUTING.md`](../../CONTRIBUTING.md) |
| AI agent security rules | `.claude/settings.json`, CI workflows | [`docs/security/SECURITY-AI.md`](../../docs/security/SECURITY-AI.md) |

This directory exists so the ACMM `policy-as-code` check finds it.
`workflow-permissions.yml` lives here; the other policy files stay where their
tooling expects them.
