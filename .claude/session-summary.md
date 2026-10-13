# Session summary

Hand-off notes for agent work in this repository, so the next session starts from where the last one stopped instead
of cold (AGENTS.md "Session Completion" step 7). Keep it short: overwrite the "Latest session" section at the end of
each session, in the issue's own commit; durable lessons belong in `.claude/memory/corrections.tsv`,
`docs/reflections/` or `bd remember`, not here.

Format: date, issue/PR worked, outcome, open follow-ups, gotchas for next time.

## Latest session

- **Date:** 2026-10-13
- **Worked:** #78 — work-profile apps (stack row 11 of the #60 interface stack, on `feat/69-app-groups`, PR #105).
- **Outcome:** D22 (c) work apps in their own group. Core `LauncherApp.profile` (profile serial, null = own user),
  `isWorkApp`, public `component` (`<serial>:pkg/activity` for profile apps, unchanged for own-user apps, so stored
  keys stay valid), `WORK_GROUP` ("Work", the only automatic group of a work app), root full list without work apps
  (blocks keep them; Groups row alone when only work apps), duplicate suffix `Work`, `AppFacts.isWorkApp` disables
  Uninstall, profile tie-break in `sortedForDisplay`. App: `LauncherApps.otherProfileApps(userManager)`,
  `UserManager.pausedProfiles()`; work apps start via `LauncherApps.startMainActivity`/`startAppDetailsActivity`;
  managed-profile broadcasts + a `LauncherApps.Callback` reload; paused profiles dim rows (`paused` on AppList and
  CollectionApps).
- **Open follow-ups:** none for #78.
- **Notes for next session:**
  - Robolectric: `LauncherActivityInfo.getLabel()` trims glyphless characters and Robolectric's Paint has none, so work
    tests need `@Config(shadows = [GlyphsFound::class])`; `startMainActivity` is unshadowed and
    `startAppDetailsActivity` throws, so tests record them with `LauncherAppsRecorder`.
  - No JDK on the host: run Gradle via the podman sandbox (`/tmp/gates.sh`).
