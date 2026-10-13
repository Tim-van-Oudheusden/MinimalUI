# Session summary

Hand-off notes for agent work in this repository, so the next session starts from where the last one stopped instead
of cold (AGENTS.md "Session Completion" step 7). Keep it short: overwrite the "Latest session" section at the end of
each session, in the issue's own commit; durable lessons belong in `.claude/memory/corrections.tsv`,
`docs/reflections/` or `bd remember`, not here.

Format: date, issue/PR worked, outcome, open follow-ups, gotchas for next time.

## Latest session

- **Date:** 2026-10-13
- **Worked:** #70 — root screen structure (stack row 6 of the #60 interface stack, on `feat/73-settings-list`).
- **Outcome:** D1 (c) hybrid with one blank row. Core `RootEntries.kt` (`RootSection`, `RootEntry.App`/`Blank`,
  `rootEntries(apps, recents, settings)`, `followedRow` so the selection follows its entry when the block changes),
  `ScreenStack<S>` (`pushed`/`popped`/`home`), `ListContent.Apps(entries)` + `rowApps`, the `Recently used` setting
  (Off / 3 / 5 / 15, default 3; `Choice.count`), and `ListPosition.stepped`/`paged`/`selecting` taking a `blank`
  predicate. App: `MainActivity` keeps one `screens: ScreenStack<Screen>` instead of the menu/settings flags;
  `PillList(selectable = …)` draws an empty, unfocusable row.
- **Open follow-ups:** #68 adds a favourites `RootSection` and entries; #69 adds a `RootEntry` kind that opens a list
  and a `Screen` for it.
- **Notes for next session:**
  - A screen opened from the root is one `Screen` subclass pushed on `screens`; Back calls `back()`, Home `home()`.
  - Typing filters only while `screens.isRoot`; a query hides the block and the blank row.
  - The emulator kept an older APK when `adb install -r` hit a signature mismatch: check `lastUpdateTime` and
    `adb uninstall dev.buildwithtim.minimalui` first.
  - No JDK on the host: run Gradle via the podman sandbox (`/tmp/gates.sh`).
