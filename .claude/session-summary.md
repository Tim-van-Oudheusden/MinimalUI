# Session summary

Hand-off notes for agent work in this repository, so the next session starts from where the last one stopped instead
of cold (AGENTS.md "Session Completion" step 7). Keep it short: overwrite the "Latest session" section at the end of
each session, in the issue's own commit; durable lessons belong in `.claude/memory/corrections.tsv`,
`docs/reflections/` or `bd remember`, not here.

Format: date, issue/PR worked, outcome, open follow-ups, gotchas for next time.

## Latest session

- **Date:** 2026-10-13
- **Worked:** #74 — button-hint row (stack row 12 of the #60 interface stack, on `feat/78-work-profile`, PR #106).
- **Outcome:** D12 (b) hint row always shown, keys as glyphs. Core `ButtonHints.kt`: `HintKey` (glyphs `☰ ⌫ ⎋ ⏎`),
  `HintAction`, `Hint`, `Hints(left, right)`, `HintedScreen` (Root(content, query, selected), SubList(hasMenu,
  isEmpty), Menu, Choice, Settings(row), About, HiddenApps(isEmpty), Editor), `buttonHints(screen)`,
  `Hints.fitting(widthDp)` (< 320 dp drops the left group). App: `ui/HintLine.kt` (two `#262626` pills, glyph in a
  white circle, label upper-cased from sentence-case strings `hint_*`); `TitledList(title, status, hints:
  HintedScreen, onClose)` and `AppList` reserve the bottom row, so every list has one row fewer per page.
- **Open follow-ups:** none for #74.
- **Notes for next session:**
  - Lint rejects an all-caps `CANCEL` string (ButtonCase) and `Locale.getDefault()` in a composable
    (NonObservableLocale): keep strings sentence case and upper-case with `LocalConfiguration.current.locales[0]`.
  - Tests that need a whole menu or root on one page now run on `@Config(qualifiers = "h514dp")` (one row taller).
  - No JDK on the host: run Gradle via the podman sandbox (`/tmp/gates.sh`).
