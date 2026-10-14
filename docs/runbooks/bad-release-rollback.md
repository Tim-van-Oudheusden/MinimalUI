# Bad release rollback

## Symptom

A release published by `.github/workflows/release.yml` (see
[`CONTRIBUTING.md#releasing`](../../CONTRIBUTING.md#releasing)) turns out to crash, misbehave, or otherwise needs to
be pulled back after users have already installed it. There is no crash reporting and no analytics
([`docs/metrics/README.md`](../metrics/README.md): "nothing is collected from the launcher or its users"), so the
first signal is almost always a user-filed GitHub issue or a device report in [`DEVICES.md`](../../DEVICES.md), not
an automated alert.

## Why this app is different

MinimalUI has no server to roll back and no auto-update channel: each release is a static APK attached to a GitHub
release, installed by hand or via `adb install`. That removes the usual rollback move — "push the previous version" —
and replaces it with two harder constraints:

- **Forward-only installs.** Every release is signed with the same key so it installs *over* the previous one and
  keeps the user's settings (`CONTRIBUTING.md#releasing`). But Android refuses to install an APK whose `versionCode`
  is lower than the one already on the device (`INSTALL_FAILED_VERSION_DOWNGRADE`), and the release build is not
  debuggable, so `adb install -d` cannot override it either. A user cannot downgrade to a known-good release without
  **uninstalling first** — and since `android:allowBackup="false"` and the app holds no `INTERNET` permission (no
  backup transport exists), uninstalling loses every favourite, collection, rename, hidden app and group with no
  recovery path.
- **It can be the one screen that reaches Settings.** If the broken release crashes `MainActivity` on every launch,
  Android keeps relaunching it as the HOME app, and there is no in-app fallback UI to escape it. See **HOME-app
  lockout** below before assuming a user can just "open Settings and switch launchers."

## Response

### If the crash is confined to a feature (the home list itself still loads)

1. The user can still reach **Settings** from the MinimalUI list (README.md "Making it your home screen") and switch
   the default home app away from MinimalUI, or use the app's own uninstall entry from a long-press/menu action.
2. File or triage the GitHub issue, fix forward, and cut a new release with a higher `versionCode` per
   `CONTRIBUTING.md#cutting-a-release`. There is no mechanism to amend a published APK in place — a fix always ships
   as a new, higher `versionCode` release.

### HOME-app lockout (MainActivity crashes immediately on every launch)

A crash loop in the HOME app can leave a user unable to reach Settings through the launcher at all. The universal
Android recovery path does not depend on MinimalUI working:

1. **Boot into Safe Mode** — on most devices, long-press Power, then long-press the "Power off" option until the
   "Reboot to safe mode" prompt appears (older/OEM devices: hold Volume Down during boot instead; this varies by
   manufacturer, which is why [`DEVICES.md`](../../DEVICES.md) exists — add the device-specific key combo there once
   confirmed for a device). Safe Mode disables all third-party apps, including MinimalUI's HOME role, and falls back
   to the device's built-in launcher.
2. From Safe Mode, go to **Settings → Apps → Default apps → Home app** and pick a different default, or
   **Settings → Apps → MinimalUI → Uninstall**.
3. Reboot normally. The device now opens its stock launcher instead of crash-looping MinimalUI.
4. Advise the user to wait for a fixed release before reinstalling, or to sideload a known-good APK — which still
   requires the uninstall-and-lose-settings path above, since the install already on the device is the broken one.

Put this sequence (Safe Mode → switch default app or uninstall) directly in the response to any issue reporting a
startup crash; don't assume the reporter already knows it.

### Maintainer-side: pulling back the release

1. If the broken release is recent and likely has few installs, delete it so it stops being offered as the latest
   download: `gh release delete <tag> --cleanup-tag` (removes the GitHub release and its tag; add `--yes` for
   non-interactive use). Do this only before a known-good next release is ready to take its place, otherwise
   `Releases` briefly has nothing current to point to.
2. If the broken release has already seen meaningful uptake, leave it published — deleting it does not reach devices
   that already downloaded it — and instead ship the fix forward as fast as possible: bump `versionName`/`versionCode`
   in the same PR that fixes the bug, per `CONTRIBUTING.md#cutting-a-release`. Note in the release notes (or a pinned
   issue) that the previous version should be upgraded away from immediately.
3. Either way, file or update a GitHub issue describing user impact and link it from the fix PR (`Fixes #<number>`),
   so the regression and its resolution are in the same place a future rollback runbook update would look.

## Prevention

- `./gradlew test lint` runs before every signed build in `release.yml`, but neither exercises `MainActivity`'s
  actual startup path on a device — a startup crash can still pass CI. There is no additional release-time device
  smoke test today; adding one (emulator boot + HOME launch check in `release.yml`) is a `.github/workflows/*` change
  and so is out of scope for this runbook (see `docs/risk-tiers.md`: `.github/*` is high-risk, human-reviewed).
- [`DEVICES.md`](../../DEVICES.md) exists precisely because this app's failure modes are device-specific; keep
  reporting the device and Android version on every crash issue so patterns (a bad release that only breaks one OEM)
  are visible.
