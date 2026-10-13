# Session summary

Hand-off notes for agent work in this repository, so the next session starts from where the last one stopped instead
of cold (AGENTS.md "Session Completion" step 7). Keep it short: overwrite the "Latest session" section at the end of
each session, in the issue's own commit; durable lessons belong in `.claude/memory/corrections.tsv`,
`docs/reflections/` or `bd remember`, not here.

Format: date, issue/PR worked, outcome, open follow-ups, gotchas for next time.

## Latest session

- **Date:** 2026-10-13
- **Worked:** #71 — hide apps (stack row 8 of the #60 interface stack, on `feat/68-favourites`, PR #102).
- **Outcome:** D6 (b), way back = both. Core `HiddenApps` (by component; `hidden(app, settingsPackage)` refuses
  Android's Settings and the MinimalUI settings row; `unhidden`, `visibleFrom`, `shownFrom` alphabetical, stored),
  `listContent(..., hidden)` (root without them; a query lists them after the visible matches with
  `RootEntry.App.isHidden`), `MenuAction.Hide` / `Unhide` with `AppFacts.isHidden`, `SettingsLink.HiddenApps`.
  `AppCollections.moved` now keeps apps it cannot show in place instead of dropping them. App: `HiddenAppsStore`
  (prefs "hidden_apps"), `ui/HiddenAppsList.kt`, `PillList(dim = …)` draws a working row in `DimText`.
- **Open follow-ups:** none for #71. Hidden state is never pruned on uninstall (documented choice: a reinstall stays
  hidden).
- **Notes for next session:**
  - Collections and the root take `visibleApps` in `MainActivity`; the Hidden apps list and the filter take all apps.
  - Removing the focused row in place (Hidden apps) lets focus land on another row, whose `onFocused` moves the
    selection: re-select the wanted row in a `LaunchedEffect(items)` after the shorter list composes.
  - No JDK on the host: run Gradle via the podman sandbox (`/tmp/gates.sh`).
