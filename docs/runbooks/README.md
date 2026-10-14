# Runbooks

What to do when something goes wrong in production — for MinimalUI, that means a published release, not a running
server: the app has no backend, collects no telemetry, and makes no network call beyond reading Wi-Fi connection
state (see [`docs/metrics/README.md`](../metrics/README.md)). Every runbook here starts from a user-visible symptom
and ends with the concrete commands or taps that resolve it.

## Format

One Markdown file per scenario, with:

- **Symptom** — what a user or the maintainer observes.
- **Why this app is different** — the launcher-specific risk, if any (see below).
- **Response** — ordered steps, maintainer-side and user-side.
- **Prevention** — what already guards against this, and what does not.

## Why runbooks matter more here than in a typical app

MinimalUI can register as the device's **default HOME app** (`android.intent.category.HOME` in
`app/src/main/AndroidManifest.xml`). A launcher that fails on startup is not just a broken feature: on a device
without a second launcher installed, it can block the one screen every other recovery step depends on. Every runbook
here that touches a release should say explicitly whether this risk applies and how to route around it.

## Index

- [Bad release rollback](bad-release-rollback.md) — a published release crashes, misbehaves, or otherwise needs to be
  pulled back, including the HOME-app lockout case above.
