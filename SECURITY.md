# Security Policy

MinimalUI is a home-screen launcher: it lists installed apps, opens them, and keeps its settings on the phone. A flaw in
it can expose what is installed, open the wrong app, or let another app act through the launcher, so reports are
welcome.

## Supported versions

Fixes go into the next release; older releases are not patched.

| Version | Supported |
| --- | --- |
| Latest [release](https://github.com/Tim-van-Oudheusden/MinimalUI/releases/latest) | Yes |
| `main` | Yes |
| Older releases, CI debug builds | No; update to the latest release |

## Reporting a vulnerability

**Do not open a public issue, pull request, or discussion for a vulnerability.**

Report it privately through GitHub: the repository's **Security** tab → **Report a vulnerability**
([direct link](https://github.com/Tim-van-Oudheusden/MinimalUI/security/advisories/new)). Only the maintainer
(@Tim-van-Oudheusden) sees the report.

Include what you can of:

- the MinimalUI version (Settings → About) and the phone and Android version
- what an attacker can do, and what they need first (another app installed, physical access, a crafted app label, …)
- steps or a proof of concept that reproduce it
- for a leaked secret: where you found it

## What to expect

This is a one-person project, so these are aims, not guarantees:

- an acknowledgement within 7 days
- a first assessment (accepted or not, and the severity) within 14 days
- a fix in a release as soon as practical, coordinated with you, then a published GitHub security advisory that credits
  you unless you prefer otherwise

Please keep the details private until the fix is released or 90 days have passed, whichever comes first.

## Scope

In scope:

- the launcher app (`app`, `core`): its manifest, exported components and intents, stored settings, and the signed
  release APKs
- the build and release pipeline in this repository (`.github/workflows/`, Gradle build files), including leaked
  secrets

Out of scope:

- flaws in Android, the phone's firmware, or the apps the launcher opens; report those to their vendors
- a phone that is already rooted or unlocked
- advisories in libraries that only the build tools use (AGP, Kover, Android Lint) and that never ship in the APK.
  Dependabot and the nightly compliance check track these;
  [`docs/testing.md`](docs/testing.md#advisories-in-build-tools) describes how they are raised or accepted

## Related

- [`docs/security/SECURITY-AI.md`](docs/security/SECURITY-AI.md): how AI coding agents may work in this repository
  safely
