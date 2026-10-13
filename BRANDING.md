# Branding: MinimalUI

## 1. Name

- **Name:** MinimalUI (one word, capital M, U and I)
- **Application ID:** `dev.buildwithtim.minimalui`

## 2. Tagline

> A minimal Android launcher for keyboard-centric phones.

## 3. Positioning

MinimalUI is a home-screen launcher for phones with a physical keyboard, such as those from Zinwa, Clicks and Unihertz.
It takes its cue from [MinUI](https://github.com/shauninman/MinUI) and [Cannoli](https://github.com/CannoliHQ/cannoli):
a plain list, driven entirely by keys, that gets out of the way. It is not a theme engine and not a widget host.

## 4. Principles

- **Keyboard first.** Every action is reachable without touching the screen. Touch works, but is never required.
- **Less on screen.** Text over icons, one list at a time, no animations that delay input.
- **Fast.** The launcher is the thing you see most; it must open instantly and never stutter.

## 5. Voice

- **Honest.** No invented device-compatibility claims; list a phone as supported only once it has been tested on one.
- **Plain words.** Short labels in the UI, short sentences in the docs.

## 6. Visual identity

- **Colour:** black background, white text; the focused row is inverted (black text on white), as in MinUI.
- **Selection:** the inversion is a pill with fully rounded ends that hugs the app's label, as in MinUI and Cannoli.
  Rows are contiguous, with no gap between them. A label too long for the row ends in an ellipsis and never scrolls.
- **Empty states:** a terse message (`No apps`, `No match`) centred on the screen in the list's text style, as in
  MinUI; while the apps load the screen stays plain black.
- **Status pill:** Android's status bar is hidden on the launcher (a swipe from the top edge shows it for a moment; it
  is back in every other app). In its place, a dark-grey (`#262626`) pill in the top-right corner, as in MinUI, holds
  a Wi-Fi glyph (only while on Wi-Fi), a battery glyph with the percentage, and a clock that follows the system
  12/24-hour setting. The battery's fill is proportional to the charge; the fill turns red at 20% and the outline too
  at 10%, and a bolt replaces the fill while charging. Its text is 20 sp, on the rows' line height. The pill takes the
  top row, so no app row runs under it; it stays hidden with the rest while the apps load. The Clock and Battery
  percentage settings leave those items out; with nothing left, the row stays empty. Glyphs are drawn in code, never
  copied from MinUI.
- **Hint row:** the bottom row of every screen names the keys that work there, as MinUI's button hints do: a
  dark-grey (`#262626`) pill at the left edge for the secondary keys (`☰ MENU`, or `⌫ DELETE` while filtering) and
  one at the right edge for the main ones (`⏎ OPEN`, with `⎋ BACK` before it below the root). Inside a pill, each
  key's glyph is black in a small white circle, followed by what it does in white upper case at 18 sp, on the rows'
  line height, so the pill is as tall as the selection pill. Keys are named by their keyboard glyphs (`⏎` Enter,
  `⎋` Esc or Back, `☰` Menu, `⌫` Backspace), never pad letters. The row takes a whole list row, so no row runs under
  it; on a screen narrower than 320 dp only the right pill shows, and while the apps load the row stays blank. A
  tap on a hint presses its key, with no ripple; the hints never take focus, so the selection stays where it was.
- **Root list:** the favourites head the list, then the recently used apps, then a `Collections` row when there are
  collections, then one empty row, then every app, then one more empty row and a `Groups` row last (as MinUI lists
  Tools last), so the first row is always an app and Up from it reaches the groups; no rule, header or other separator
  is drawn, and the selection never rests on an empty row.
- **Favourite marker:** a favourite's name has a `★` and a space before it wherever it is listed, as in Cannoli. The
  star is only drawn: typing matches the plain name.
- **Hidden apps:** a hidden app shows only when typing finds it, after the other matches, its name in grey
  (`#808080`, the colour of an action that does not apply) whether selected or not, so it reads as hidden.
- **Collections:** the `Collections` row opens a list of the collections' names, each opening its apps in the user's
  order (MinUI); both are lists in the same style, titled `Collections` and the collection's name. A new collection's
  name is typed in the text editor, titled `New collection`.
- **Groups:** the `Groups` row opens a list of group names, alphabetical with `Other` last, each opening its apps in
  the list's order; both are lists in the same style, titled `Groups` and the group's name. A group the user made and
  an automatic one of the same name are one entry, as MinUI merges folders that share a name.
- **Text editor:** one screen for every name the user types (`Rename`, `New collection`, `New group`, `Rename group`):
  its title in the dark-grey pill, then the text on the line below, aligned with the title's text, in the rows' type
  and white, with a white cursor. No box or underline is drawn around it. It opens focused, prefilled with the current
  name for Rename and Rename group.
- **Duplicate names:** when apps are listed under the same name, each shows a suffix after the name on the same line,
  as in Cannoli: its package name (or its activity's short class name when they share a package), in grey (`#808080`)
  at 0.75× the label's size; a work-profile app's suffix is `Work` (followed by its package name only when another
  work app shares its name), and a personal app whose name only work apps share needs none. Unique names show none.
  The name keeps priority on a narrow row: the suffix is cut off with an ellipsis first. The `★` is drawn before the
  name only, never on the suffix. While work apps are turned off, their rows are grey (`#808080`) too, yet still open.
- **Menus:** a menu (an app's context menu, or a group's) is another list in the same style that takes the list's
  place, one list at a time. Its title, the app's or group's name, sits in a dark-grey (`#262626`) pill below the
  status pill, as MinUI marks its in-game menu's title; an action that does not apply stays listed in grey (`#808080`)
  and does nothing.
- **Settings:** MinimalUI's settings are such a list, titled `MinimalUI settings`; a setting's value (`On`, `Off`)
  sits in white at the right end of its row, outside the selection pill, as in MinUI and Cannoli. Hidden apps and
  About, the last rows, open lists of their own: the hidden apps (`No hidden apps`, centred, when there are none), and
  the version and the build commit.
- **Type:** the system font at 24 sp until a dedicated one is chosen; legible at arm's length on small, square screens.
- **Icon:** three list rows on black: a label, the selected pill with its label cut out, and a shorter label. It is the
  launcher's own screen reduced to a mark.
- **Marketing type:** Roboto, the default system font on stock Android: Regular for labels and body text, Medium for
  headlines and the wordmark.

## 7. Assets

The icon, wordmark, banners, social images and poster live in [`docs/brand/`](docs/brand/README.md), with the sizes and
places each one is for.
