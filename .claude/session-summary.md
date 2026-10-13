# Session summary

Hand-off notes for agent work in this repository, so the next session starts from where the last one stopped instead
of cold (AGENTS.md "Session Completion" step 7). Keep it short: overwrite the "Latest session" section at the end of
each session, in the issue's own commit; durable lessons belong in `.claude/memory/corrections.tsv`,
`docs/reflections/` or `bd remember`, not here.

Format: date, issue/PR worked, outcome, open follow-ups, gotchas for next time.

## Latest session

- **Date:** 2026-10-13
- **Worked:** #67 — per-app context menu (stack row 4 of the #60 interface stack, on `feat/65-status-pill`).
- **Outcome:** core `ContextMenu.kt` (`MenuAction`, `MenuItem(action, enabled)`, `AppFacts`, `contextMenu(facts)`):
  App info, Uninstall (disabled for system apps and the Settings entry), Remove from / Clear recently used. The list
  machinery moved from `AppList` into a shared `ui/PillList.kt` (`PillList`, `PillListState`, `HeaderLine`); `AppList`
  keeps its selection in a hoisted `AppListState`, so it comes back from the menu unchanged. `ui/ContextMenu.kt`
  replaces the list until Back/Esc; opened by Menu, a held Enter/centre (first auto-repeat) or a touch long-press.
  Uninstall uses `ACTION_DELETE` with the normal `REQUEST_DELETE_PACKAGES` permission.
- **Open follow-ups:** the rest of the #60 stack builds on this branch (Favourite, Hide, Rename, groups add
  `MenuAction` kinds).
- **Notes for next session:**
  - `PillList` swallows a confirm-key release it did not see pressed, so the release ending a long press never
    triggers a row on the screen it opened.
  - Robolectric: `shadowOf(pm).installPackage(PackageInfo)` before `addActivityIfNotPresent` sets
    `ApplicationInfo.flags` (e.g. `FLAG_SYSTEM`).
  - After a touch action the next key may race the focus request; wait (`assertIsFocused`) before typing.
  - No JDK on the host: run Gradle via the podman sandbox (`/tmp/gates.sh`).
