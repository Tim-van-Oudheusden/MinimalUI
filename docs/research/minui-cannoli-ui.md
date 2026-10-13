# MinUI and Cannoli: user-interface research

Research notes for MinimalUI. The goal is to recreate the MinUI/Cannoli *interface* in an Android home-screen launcher
that opens installed apps rather than ROMs. Every claim links to a primary source: the projects' own code, assets,
READMEs, docs or release metadata, or Android's official reference for platform constraints. Anything not confirmed
from a primary source is marked `[unverified]`. Anything worked out here rather than read in a source is marked
`[inference]`.

Permalink bases used below (all pinned to the commits examined):

- MinUI: `M` = <https://github.com/shauninman/MinUI/blob/dbf89435a5179788418ff3573042cf9c7e75d9e0>
- Cannoli app: `C` = <https://github.com/CannoliHQ/cannoli/blob/7755e376b6add0bbd25ea19104c368b7a40b979f>
- Cannoli docs site source: `W` = <https://github.com/CannoliHQ/website/blob/8b377dcebb86932ca4a9b5ca034de747b7f42d9b/docs/documentation>
  (published at <https://cannoli.dev/documentation/>)

---

## 1. The projects

### MinUI

- **What it is:** "a focused, custom launcher and libretro frontend for a variety of retro handhelds", by Shaun Inman
  ([README.md](https://github.com/shauninman/MinUI/blob/dbf89435a5179788418ff3573042cf9c7e75d9e0/README.md)). It is
  written in C on SDL. The launcher is
  [`workspace/all/minui/minui.c`](https://github.com/shauninman/MinUI/blob/dbf89435a5179788418ff3573042cf9c7e75d9e0/workspace/all/minui/minui.c),
  the shared drawing, input and power code is
  [`workspace/all/common/api.c`](https://github.com/shauninman/MinUI/blob/dbf89435a5179788418ff3573042cf9c7e75d9e0/workspace/all/common/api.c),
  and the in-game menu belongs to the frontend
  [`workspace/all/minarch/minarch.c`](https://github.com/shauninman/MinUI/blob/dbf89435a5179788418ff3573042cf9c7e75d9e0/workspace/all/minarch/minarch.c).
- **Repo:** <https://github.com/shauninman/MinUI>
- **Licence:** the repo has no LICENSE file, and the GitHub API reports `license: null` for it
  (`GET https://api.github.com/repos/shauninman/MinUI`, checked 2026-10-13). Under default copyright that means all
  rights are reserved `[inference]`. Its bundled font is BPreplay, which Cannoli redistributes under the SIL OFL
  ([`licenses/fonts/BPreplayBold-unhinted-OFL.txt` in Cannoli](https://github.com/CannoliHQ/cannoli/blob/7755e376b6add0bbd25ea19104c368b7a40b979f/licenses/fonts/BPreplayBold-unhinted-OFL.txt)).
- **Version examined:** `main` @ `dbf89435` (2025-11-27, "renamed "Deprecated" to "Legacy""). The latest release is
  [`v20251127-1` / MinUI-20251127-1](https://github.com/shauninman/MinUI/releases/tag/v20251127-1). The README's device
  table marks every supported device as **Legacy**, meaning "will be retired in a future update"
  ([README.md#L65-L97](https://github.com/shauninman/MinUI/blob/dbf89435a5179788418ff3573042cf9c7e75d9e0/README.md#L65-L97)).
- **Screenshots (first-party):**
  [main menu](https://raw.githubusercontent.com/shauninman/MinUI/dbf89435a5179788418ff3573042cf9c7e75d9e0/github/minui-main.png),
  [in-game menu](https://raw.githubusercontent.com/shauninman/MinUI/dbf89435a5179788418ff3573042cf9c7e75d9e0/github/minui-menu-gbc.png),
  [frontend options](https://raw.githubusercontent.com/shauninman/MinUI/dbf89435a5179788418ff3573042cf9c7e75d9e0/github/minui-menu-options-frontend.png),
  [clock tool](https://raw.githubusercontent.com/shauninman/MinUI/dbf89435a5179788418ff3573042cf9c7e75d9e0/github/minui-clock.png).

### Cannoli

- **What it is:** "an opinionated retro gaming setup for Android". It ships built-in libretro cores and can also
  launch RetroArch or standalone emulators
  ([README.md](https://github.com/CannoliHQ/cannoli/blob/7755e376b6add0bbd25ea19104c368b7a40b979f/README.md)). The
  author calls it an attempt "to bring [MinUI's] experience to Android"
  ([README.md](https://github.com/CannoliHQ/cannoli/blob/7755e376b6add0bbd25ea19104c368b7a40b979f/README.md);
  [docs "The Inspiration"](https://cannoli.dev/documentation/#the-inspiration)). It is written in Kotlin with Jetpack
  Compose, in modules `app`, `cannoli-ui` (shared Compose components and theme), `cannoli-igm` (in-game menu) and
  `cannoli-core`.
- **Repo:** <https://github.com/CannoliHQ/cannoli>. Docs: <https://cannoli.dev/documentation/>, sourced from
  <https://github.com/CannoliHQ/website> (examined @ `8b377dce`, 2026-09-17).
- **Licence:** GPL-3.0
  ([LICENSE](https://github.com/CannoliHQ/cannoli/blob/7755e376b6add0bbd25ea19104c368b7a40b979f/LICENSE); the GitHub
  API also reports GPL-3.0). That is the same licence as MinimalUI's `LICENSE`.
- **Version examined:** `main` @ `7755e376` (2026-08-06). The latest release is
  [`v1.9.1`](https://github.com/CannoliHQ/cannoli/releases/tag/v1.9.1) (2026-08-04).
- **Android integration:** the main activity declares the `HOME`, `DEFAULT`, `LAUNCHER` and `LEANBACK_LAUNCHER`
  categories with `launchMode="singleTop"`, and the app requests `QUERY_ALL_PACKAGES`
  ([AndroidManifest.xml#L19, L51-L59](https://github.com/CannoliHQ/cannoli/blob/7755e376b6add0bbd25ea19104c368b7a40b979f/app/src/main/AndroidManifest.xml#L19-L59)).
  Settings has "Set as Default Launcher" and "Main Menu Quit" for when it is *not* the home app
  ([settings.md#L9-L15](https://github.com/CannoliHQ/website/blob/8b377dcebb86932ca4a9b5ca034de747b7f42d9b/docs/documentation/settings.md#L9-L15)).
- **Screenshots (first-party):**
  [main menu](https://raw.githubusercontent.com/CannoliHQ/cannoli/7755e376b6add0bbd25ea19104c368b7a40b979f/.github/resources/screenshots/main_menu.png),
  [favorites](https://raw.githubusercontent.com/CannoliHQ/cannoli/7755e376b6add0bbd25ea19104c368b7a40b979f/.github/resources/screenshots/favorites.png),
  [in-game menu](https://raw.githubusercontent.com/CannoliHQ/cannoli/7755e376b6add0bbd25ea19104c368b7a40b979f/.github/resources/screenshots/igm.png).

**Main difference in philosophy.** MinUI promises "No settings or configuration" and "No boxart, themes, or
distractions"
([README.md#L9-L11](https://github.com/shauninman/MinUI/blob/dbf89435a5179788418ff3573042cf9c7e75d9e0/README.md#L9-L11)).
Cannoli keeps the same look but adds a settings screen, colours, wallpapers, fonts, box art, favourites, search and
reordering. It also lists features it will never add: art scraper, game switcher, play-time tracker, menu music,
themes and video previews ([docs index](https://cannoli.dev/documentation/#project-philosophy)).

---

## 2. Screens and navigation

### MinUI

MinUI is a stack of directory lists (`Directory`) with one list drawn at a time. A opens an entry and pushes a new
list; B pops one
([minui.c#L1138-L1183](https://github.com/shauninman/MinUI/blob/dbf89435a5179788418ff3573042cf9c7e75d9e0/workspace/all/minui/minui.c#L1138-L1183),
[#L1470-L1483](https://github.com/shauninman/MinUI/blob/dbf89435a5179788418ff3573042cf9c7e75d9e0/workspace/all/minui/minui.c#L1470-L1483)).

**Root menu.** `getRoot()` builds it in this order
([minui.c#L639-L753](https://github.com/shauninman/MinUI/blob/dbf89435a5179788418ff3573042cf9c7e75d9e0/workspace/all/minui/minui.c#L639-L753)):

1. **Recently Played**, only when there are recents whose emulator is installed (`hasRecents()`).
2. **Collections**, only when `/Collections` has a visible file and at least one system is visible. When there are no
   systems, the collections themselves are promoted to the root (#L717-L746).
3. **Systems:** each folder in `/Roms` that has an emulator pak and at least one non-hidden file. They are sorted
   case-insensitively, and folders with the same display name are collated into one entry (#L650-L677; see §5).
4. **Tools** (`/Tools/<PLATFORM>`), unless simple mode is on (#L749-L750).

**Other list screens:** a system's ROM list, a sub-folder, a collection's contents, a multi-disc `.m3u` shown as
"Disc 1…n" (`Directory_new`, #L330-L355; `getDiscs`, #L799-L836), and Recently Played itself.

**Info screen.** Tapping MENU (released within 250 ms) toggles a centred "Release / Commit / Model" panel. B or
another MENU tap closes it
([minui.c#L1352-L1366, #L1523-L1586](https://github.com/shauninman/MinUI/blob/dbf89435a5179788418ff3573042cf9c7e75d9e0/workspace/all/minui/minui.c#L1352-L1366);
`PAD_tappedMenu`
[api.c#L1383-L1395](https://github.com/shauninman/MinUI/blob/dbf89435a5179788418ff3573042cf9c7e75d9e0/workspace/all/common/api.c#L1383-L1395)).

**In-game menu** (in minarch, not the launcher): Continue, Save, Load, Options, Quit. Simple mode replaces Options
with Reset. Left/Right changes the save slot or the disc
([minarch.c#L3011-L3073](https://github.com/shauninman/MinUI/blob/dbf89435a5179788418ff3573042cf9c7e75d9e0/workspace/all/minarch/minarch.c#L3011-L3073),
[#L4299-L4325](https://github.com/shauninman/MinUI/blob/dbf89435a5179788418ff3573042cf9c7e75d9e0/workspace/all/minarch/minarch.c#L4299-L4325)).

**Buttons in the launcher** (main loop,
[minui.c#L1350-L1484](https://github.com/shauninman/MinUI/blob/dbf89435a5179788418ff3573042cf9c7e75d9e0/workspace/all/minui/minui.c#L1350-L1484)):

| Input | Action |
|---|---|
| Up / Down | Move one row. A **fresh press** at the top or bottom wraps to the other end; a **held auto-repeat stops** there (#L1367-L1401). Auto-repeat starts after 300 ms and repeats every 100 ms ([api.h#L240-L241](https://github.com/shauninman/MinUI/blob/dbf89435a5179788418ff3573042cf9c7e75d9e0/workspace/all/common/api.h#L240-L241)). |
| Left / Right | Page up or down by `MAIN_ROW_COUNT` rows, clamped to the ends with no wrap (#L1402-L1428). |
| L1 / R1 | Jump to the previous or next **first letter** (alphabetical index; see §3) (#L1431-L1456). |
| A | Open the folder, launch the ROM or run the pak (#L1470-L1476). |
| B | Back one level; does nothing at the root (#L1477-L1483). |
| X (`BTN_RESUME`) | Resume from the last-used save state, only when one exists (#L1465-L1469; e.g. [miyoomini platform.h#L95](https://github.com/shauninman/MinUI/blob/dbf89435a5179788418ff3573042cf9c7e75d9e0/workspace/miyoomini/platform/platform.h#L95)). |
| MENU tap | Show or hide the info screen. |
| POWER (or MENU on switch-power devices) | Sleep or wake; holding POWER for 1 s powers off ([api.c#L1530-L1557](https://github.com/shauninman/MinUI/blob/dbf89435a5179788418ff3573042cf9c7e75d9e0/workspace/all/common/api.c#L1530-L1557)). |
| MENU + Vol, or SELECT/START + L/R | Brightness and volume; the binding depends on the device ([skeleton/BASE/README.txt#L105-L135](https://github.com/shauninman/MinUI/blob/dbf89435a5179788418ff3573042cf9c7e75d9e0/skeleton/BASE/README.txt#L105-L135)). |

**Power and sleep.** The device auto-sleeps after 30 s without input, unless charging or on HDMI
([api.c#L1543-L1557](https://github.com/shauninman/MinUI/blob/dbf89435a5179788418ff3573042cf9c7e75d9e0/workspace/all/common/api.c#L1543-L1557)).
After 2 minutes asleep it powers off; while charging it checks again every minute
([api.c#L1671-L1683](https://github.com/shauninman/MinUI/blob/dbf89435a5179788418ff3573042cf9c7e75d9e0/workspace/all/common/api.c#L1671-L1683)).
Powering off in-game makes a quicksave that auto-resumes on the next boot
([README.txt#L137-L140](https://github.com/shauninman/MinUI/blob/dbf89435a5179788418ff3573042cf9c7e75d9e0/skeleton/BASE/README.txt#L137-L140);
`autoResume()`
[minui.c#L1033-L1066](https://github.com/shauninman/MinUI/blob/dbf89435a5179788418ff3573042cf9c7e75d9e0/workspace/all/minui/minui.c#L1033-L1066)).

### Cannoli

Cannoli also shows one list at a time on a navigation stack, but it has many more screens: a dozen-plus settings
sub-screens, RomM browsing, controller set-up and others (`app/.../ui/screens/`).

**Main menu** ("Platform List"). `SystemListViewModel.scan()` builds it in this order
([SystemListViewModel.kt#L156-L257](https://github.com/CannoliHQ/cannoli/blob/7755e376b6add0bbd25ea19104c368b7a40b979f/app/src/main/java/dev/cannoli/scorza/ui/viewmodel/SystemListViewModel.kt#L156-L257)):

1. **Recently Played**, when enabled and non-empty (L218-L220).
2. **Favorites**, when enabled and non-empty (L221-L225).
3. **Collections** folder (Default mode only) (L226-L231).
4. **Platforms** with at least one game, sorted by display name; platforms sharing a display name are merged (L188-L197,
   L235-L239).
5. **Ports** and **Tools**: user-picked Android apps (L249-L252).
6. Items 4–5 can then be **reordered** by the user, and the custom order is applied on top (L253-L256, L437-L447).

There are three **content modes**: Default, Collections Only (collections are hoisted to the root) and Five Game
Handheld (one collection's games are hoisted to the root)
([library.md "Content Modes"](https://cannoli.dev/documentation/library/#content-modes)).

**Game list** (also used for Recently Played, Favorites, collections, Tools and Ports). It has a title or breadcrumb,
the list, and an optional box-art panel on the right
([GameListScreen.kt#L171-L281](https://github.com/CannoliHQ/cannoli/blob/7755e376b6add0bbd25ea19104c368b7a40b979f/app/src/main/java/dev/cannoli/scorza/ui/screens/GameListScreen.kt#L171-L281)).

**Other screens:**

- A **context menu** on Start, with actions such as Add To Favorites, Manage Collections, Rename, Delete
  ([library.md "Managing Games"](https://cannoli.dev/documentation/library/#managing-games)).
- **Settings** on X, organised as General / Appearance / Library / Input / Emulation / Integrations / Advanced / About
  ([settings.md](https://cannoli.dev/documentation/settings/)).
- An **on-screen keyboard** for search and rename.
- An **in-game menu** with Resume, Save State, Load State, Achievements, Guide, Cheats, Settings, Reassign Players,
  Switch Disc, Reset and Quit
  ([playing-games.md#L28-L122](https://github.com/CannoliHQ/website/blob/8b377dcebb86932ca4a9b5ca034de747b7f42d9b/docs/documentation/playing-games.md#L28-L122)).

**Buttons** (default "Plumber" labels; Redmond and Shapes styles swap confirm and back)
([button-reference.md](https://cannoli.dev/documentation/button-reference/)):

| Screen | Input | Action |
|---|---|---|
| Main menu | Up/Down, A | Move, open |
| Main menu | X | Settings |
| Main menu | R1 | Global search |
| Main menu | Start | Context menu (e.g. Rename on Tools/Ports) |
| Main menu | Select | Reorder mode |
| Main menu | Y | Nonna's Kitchen, shown only when the list is empty ([SystemListScreen.kt#L280-L284](https://github.com/CannoliHQ/cannoli/blob/7755e376b6add0bbd25ea19104c368b7a40b979f/app/src/main/java/dev/cannoli/scorza/ui/screens/SystemListScreen.kt#L280-L284)) |
| Main menu | B | Quit, only when "Main Menu Quit" is enabled (L285-L287) |
| Game list | A | Launch or open |
| Game list | X | Resume (hold for the save-state picker) |
| Game list | Y | New collection (in Collections) |
| Game list | R1 | Search this list |
| Game list | Start | Context menu |
| Game list | Select | Multi-select |
| Game list | B | Back |
| Any list | Left / Right | Page jump (below) |

- **Up/Down always wrap**, held or not: `(selectedIndex + delta).mod(count)`
  ([ScrollListInputHandler.kt#L44-L51](https://github.com/CannoliHQ/cannoli/blob/7755e376b6add0bbd25ea19104c368b7a40b979f/app/src/main/java/dev/cannoli/scorza/input/screen/ScrollListInputHandler.kt#L44-L51);
  `moveSelection`
  [SystemListViewModel.kt#L316-L324](https://github.com/CannoliHQ/cannoli/blob/7755e376b6add0bbd25ea19104c368b7a40b979f/app/src/main/java/dev/cannoli/scorza/ui/viewmodel/SystemListViewModel.kt#L316-L324)).
  Held directions repeat after 280 ms, every 80 ms
  ([MenuNavigationPoller.kt#L93-L98](https://github.com/CannoliHQ/cannoli/blob/7755e376b6add0bbd25ea19104c368b7a40b979f/app/src/main/java/dev/cannoli/scorza/input/runtime/MenuNavigationPoller.kt#L93-L98)).
- **Left/Right page jump.** The first press moves the selection to the last (or first) fully visible row; the next
  press moves by one screenful, clamped with no wrap
  ([PageJump.kt#L5-L26](https://github.com/CannoliHQ/cannoli/blob/7755e376b6add0bbd25ea19104c368b7a40b979f/app/src/main/java/dev/cannoli/scorza/input/PageJump.kt#L5-L26),
  [GameListInputHandler.kt#L109-L117](https://github.com/CannoliHQ/cannoli/blob/7755e376b6add0bbd25ea19104c368b7a40b979f/app/src/main/java/dev/cannoli/scorza/input/screen/GameListInputHandler.kt#L109-L117)).
- **No letter jump.** R1 opens search instead of MinUI's next-letter jump
  ([GameListInputHandler.kt#L135-L140](https://github.com/CannoliHQ/cannoli/blob/7755e376b6add0bbd25ea19104c368b7a40b979f/app/src/main/java/dev/cannoli/scorza/input/screen/GameListInputHandler.kt#L135-L140)).
- **Search ignores case and accents** (`pokemon` matches `Pokémon`)
  ([library.md "Search"](https://cannoli.dev/documentation/library/#search)).
- **Hardware keyboards are not a first-class input in Cannoli.** The keyboard mapping (Enter = south, Esc = east,
  X/Y/L/R letters, Space = Start, Shift = Select, M = Menu) is a development aid, enabled only in DEBUG builds on an
  Android Virtual Device
  ([DevKeyboardMapping.kt#L12-L49](https://github.com/CannoliHQ/cannoli/blob/7755e376b6add0bbd25ea19104c368b7a40b979f/app/src/main/java/dev/cannoli/scorza/input/resolver/DevKeyboardMapping.kt#L12-L49),
  [ControllerBindingsModule.kt#L88-L89](https://github.com/CannoliHQ/cannoli/blob/7755e376b6add0bbd25ea19104c368b7a40b979f/app/src/main/java/dev/cannoli/scorza/di/ControllerBindingsModule.kt#L88-L89)).
  A raw-keycode fallback (D-pad, Enter, Esc/Back, Menu) exists only on the onboarding screen
  ([MainActivity.kt#L560-L577](https://github.com/CannoliHQ/cannoli/blob/7755e376b6add0bbd25ea19104c368b7a40b979f/app/src/main/java/dev/cannoli/scorza/MainActivity.kt#L560-L577)).
- **Power and sleep** are left to Android; no auto-sleep code exists in the launcher `[unverified: no such code found
  by search]`.

---

## 3. Visual design

### MinUI

All MinUI sizes are in unscaled units, multiplied by the platform's `FIXED_SCALE` through `SCALE1()`
([defines.h#L50-L69, #L80-L83](https://github.com/shauninman/MinUI/blob/dbf89435a5179788418ff3573042cf9c7e75d9e0/workspace/all/common/defines.h#L50-L83)).

**Screen sizes and scale:**

| Device (examples) | Resolution | `FIXED_SCALE` | Rows per page | `PADDING` | Source |
|---|---|---|---|---|---|
| Miyoo Mini | 640×480 | 2 | 6 | 10 | [platform.h#L106-L117](https://github.com/shauninman/MinUI/blob/dbf89435a5179788418ff3573042cf9c7e75d9e0/workspace/miyoomini/platform/platform.h#L106-L117) |
| Miyoo Mini 560p mode | 752×560 | 2 | 8 | 5 | same file |
| RGB30 (square) | 720×720 | 2 | 8 | 40 | [platform.h#L108-L125](https://github.com/shauninman/MinUI/blob/dbf89435a5179788418ff3573042cf9c7e75d9e0/workspace/rgb30/platform/platform.h#L108-L125) |
| TrimUI Smart Pro | 1280×720 | 2 | 8 | 40 | [platform.h#L120-L131](https://github.com/shauninman/MinUI/blob/dbf89435a5179788418ff3573042cf9c7e75d9e0/workspace/tg5040/platform/platform.h#L120-L131) |
| TrimUI Brick | 1024×768 | 3 | 7 | 5 | same file |
| TrimUI Smart, GKD Pixel | 320×240 | 1 | 6 (default) | 10 (default) | [trimuismart platform.h#L104-L106](https://github.com/shauninman/MinUI/blob/dbf89435a5179788418ff3573042cf9c7e75d9e0/workspace/trimuismart/platform/platform.h#L104-L106) |

The default row count follows the comment `FIXED_HEIGHT / (PILL_SIZE * FIXED_SCALE) - 2`, i.e. the screen holds whole
pills, with one pill's worth of space reserved for the top status pill and one for the bottom hint row
([defines.h#L58-L64](https://github.com/shauninman/MinUI/blob/dbf89435a5179788418ff3573042cf9c7e75d9e0/workspace/all/common/defines.h#L58-L64)).

**Font.** A single font, `BPreplayBold-unhinted.otf`
([defines.h#L17](https://github.com/shauninman/MinUI/blob/dbf89435a5179788418ff3573042cf9c7e75d9e0/workspace/all/common/defines.h#L17);
[asset](https://github.com/shauninman/MinUI/blob/dbf89435a5179788418ff3573042cf9c7e75d9e0/skeleton/SYSTEM/res/BPreplayBold-unhinted.otf)),
loaded at four sizes, all rendered with `TTF_STYLE_BOLD`
([api.c#L154-L163](https://github.com/shauninman/MinUI/blob/dbf89435a5179788418ff3573042cf9c7e75d9e0/workspace/all/common/api.c#L154-L163);
sizes in [defines.h#L66-L69](https://github.com/shauninman/MinUI/blob/dbf89435a5179788418ff3573042cf9c7e75d9e0/workspace/all/common/defines.h#L66-L69)):

| Size | Points (×scale) | Used for |
|---|---|---|
| large | 16 | list rows |
| medium | 14 | single-letter button labels |
| small | 12 | button hints |
| tiny | 10 | multi-letter button labels |

**Colours**
([defines.h#L34-L48](https://github.com/shauninman/MinUI/blob/dbf89435a5179788418ff3573042cf9c7e75d9e0/workspace/all/common/defines.h#L34-L48);
asset colours [api.c#L102-L121](https://github.com/shauninman/MinUI/blob/dbf89435a5179788418ff3573042cf9c7e75d9e0/workspace/all/common/api.c#L102-L121)):

| Element | Colour |
|---|---|
| Background | black (screen cleared) |
| Row text | white `#FFFFFF` |
| Selected row | black text on a **white pill** |
| Status and hint pills on the main menu | dark grey `#262626` (`ASSET_DARK_GRAY_PILL`) |
| Button circle | white |
| Button letter | grey `#999999` (`COLOR_BUTTON_TEXT`) |
| Hint text | white |
| Secondary / disambiguation text, info-screen labels | `#666666` (`COLOR_DARK_TEXT`) |

**The selection pill**
([minui.c#L1590-L1640](https://github.com/shauninman/MinUI/blob/dbf89435a5179788418ff3573042cf9c7e75d9e0/workspace/all/minui/minui.c#L1590-L1640);
`GFX_blitPill`
[api.c#L520-L539](https://github.com/shauninman/MinUI/blob/dbf89435a5179788418ff3573042cf9c7e75d9e0/workspace/all/common/api.c#L520-L539)):

- **Size and position:** `PILL_SIZE` = 30 units tall. Row *j* sits at `y = PADDING + j*PILL_SIZE`, so rows are
  contiguous with no gap. Text is inset `BUTTON_PADDING` = 12 units and drawn 4 units below the row top.
- **Shape:** fully rounded ends (radius = h/2), built from a 30×30 circle in `assets@Nx.png` that is split in half
  and stretched in the middle.
- **Width hugs the text:** `max_width = MIN(available_width, text_width)`. It is **not** a full-width bar.

**Truncation.** Names that do not fit are cut and given a `...` suffix until they fit; nothing scrolls
([`GFX_truncateText`, api.c#L228-L242](https://github.com/shauninman/MinUI/blob/dbf89435a5179788418ff3573042cf9c7e75d9e0/workspace/all/common/api.c#L228-L242)).
The first visible row is shortened by the status pill's width so the two never overlap
([minui.c#L1595-L1596](https://github.com/shauninman/MinUI/blob/dbf89435a5179788418ff3573042cf9c7e75d9e0/workspace/all/minui/minui.c#L1595-L1596)).

**Alphabetical index.** Each entry's first letter maps to a bucket: a–z are 1–26, and everything else (digits,
symbols) shares bucket 0. A list of bucket start indices drives L1/R1
([minui.c#L192-L197, #L307-L317](https://github.com/shauninman/MinUI/blob/dbf89435a5179788418ff3573042cf9c7e75d9e0/workspace/all/minui/minui.c#L192-L197)).
Recently Played and collections are not indexed, because they are not alphabetical (#L216).

**Empty state.** A centred "Empty folder"
([minui.c#L1647](https://github.com/shauninman/MinUI/blob/dbf89435a5179788418ff3573042cf9c7e75d9e0/workspace/all/minui/minui.c#L1647)).

**Animation.** None. The screen is redrawn only when `dirty`, with vsync
([minui.c#L1673-L1676](https://github.com/shauninman/MinUI/blob/dbf89435a5179788418ff3573042cf9c7e75d9e0/workspace/all/minui/minui.c#L1673-L1676)).

### Cannoli

**Resolution.** Cannoli is density-independent Compose (dp/sp) with no fixed resolution. Rows per page come from
`solveListRhythm`, which fits a whole number of rows between the title and footer and spreads the leftover space
evenly over the gaps
([ListRhythm.kt](https://github.com/CannoliHQ/cannoli/blob/7755e376b6add0bbd25ea19104c368b7a40b979f/cannoli-ui/src/main/java/dev/cannoli/ui/components/ListRhythm.kt)).
Screen padding is 20 dp, halved vertically on screens shorter than 400 dp
([PillRow.kt#L46-L60](https://github.com/CannoliHQ/cannoli/blob/7755e376b6add0bbd25ea19104c368b7a40b979f/cannoli-ui/src/main/java/dev/cannoli/ui/components/PillRow.kt#L46-L60)).
A "Screen Geometry" setting can shrink the UI to 50–100% for overscan
([ScreenGeometry.kt](https://github.com/CannoliHQ/cannoli/blob/7755e376b6add0bbd25ea19104c368b7a40b979f/cannoli-ui/src/main/java/dev/cannoli/ui/ScreenGeometry.kt)).

**Fonts.** Two are bundled:

- **Default:** M PLUS 1 Code, shipped as `MPlus-1c-NerdFont-Bold.ttf`, which also supplies the Nerd Font icon glyphs.
- **"The OG":** MinUI's BPreplay.

Users can drop `.ttf`/`.otf` files into `Config/Fonts/`
([customization.md "Font"](https://cannoli.dev/documentation/customization/#font);
[assets/fonts](https://github.com/CannoliHQ/cannoli/tree/7755e376b6add0bbd25ea19104c368b7a40b979f/app/src/main/assets/fonts)).
The BPreplay option arrived in the changelog as "Bundled OG MinUI font (not default)"
([CHANGELOG.md](https://github.com/CannoliHQ/cannoli/blob/7755e376b6add0bbd25ea19104c368b7a40b979f/.github/CHANGELOG.md)).

**Text sizes.**

- The setting runs 10–30 sp in steps of 2, default **24 sp**
  ([SettingsRepository.kt#L531-L542](https://github.com/CannoliHQ/cannoli/blob/7755e376b6add0bbd25ea19104c368b7a40b979f/app/src/main/java/dev/cannoli/scorza/settings/SettingsRepository.kt#L531-L542)).
  The docs say "Eight sizes … from compact (16) to large (30). The default is 24"
  ([customization.md "Text Size"](https://cannoli.dev/documentation/customization/#text-size)), so docs and code
  disagree on the range.
- Row text uses `FontWeight.Black`
  ([Type.kt#L19-L28](https://github.com/CannoliHQ/cannoli/blob/7755e376b6add0bbd25ea19104c368b7a40b979f/cannoli-ui/src/main/java/dev/cannoli/ui/theme/Type.kt#L19-L28)).
- The screen title is 1.3× the row size
  ([ScreenTitle.kt#L38](https://github.com/CannoliHQ/cannoli/blob/7755e376b6add0bbd25ea19104c368b7a40b979f/cannoli-ui/src/main/java/dev/cannoli/ui/components/ScreenTitle.kt#L38)).

**Colours.** Defaults are white highlight, white text, black highlight text, white accent, white title, black
background and white status bar
([Color.kt#L22-L30](https://github.com/CannoliHQ/cannoli/blob/7755e376b6add0bbd25ea19104c368b7a40b979f/cannoli-ui/src/main/java/dev/cannoli/ui/theme/Color.kt#L22-L30)).
All seven are user-settable from 16 presets or by hex value
([Color.kt#L43-L60](https://github.com/CannoliHQ/cannoli/blob/7755e376b6add0bbd25ea19104c368b7a40b979f/cannoli-ui/src/main/java/dev/cannoli/ui/theme/Color.kt#L43-L60);
[customization.md "Colors"](https://cannoli.dev/documentation/customization/#colors)). A wallpaper with a 0–90% tint
is optional.

**The selection pill**
([PillRow.kt#L76-L160](https://github.com/CannoliHQ/cannoli/blob/7755e376b6add0bbd25ea19104c368b7a40b979f/cannoli-ui/src/main/java/dev/cannoli/ui/components/PillRow.kt#L76-L160);
[Shapes.kt#L10](https://github.com/CannoliHQ/cannoli/blob/7755e376b6add0bbd25ea19104c368b7a40b979f/cannoli-ui/src/main/java/dev/cannoli/ui/theme/Shapes.kt#L10)):

- **Shape:** `RoundedCornerShape(50)` filled with the highlight colour.
- **Padding:** 14 dp horizontal and 6 dp vertical, both scaled down (never up) for text under 24 sp.
- **Rows sit flush.** The code comment says MinUI does the same: "MinUI lays its list out the same way:
  `SCALE1(PADDING + (j * PILL_SIZE))`, pills contiguous" (L82-L84).
- **Width:** like MinUI, `PillRowText` wraps its content, so the pill hugs the label `[inference from the absence of
  fillMaxWidth on PillRowText's PillRow, L182]`. Key/value settings rows are full width.

**Long names.** The selected row scrolls as a marquee: it waits 800 ms, then scrolls at about 4 ms per pixel (bounded
to 0.5–8 s), pauses 800 ms, scrolls back, and repeats
([PillRow.kt#L105-L122](https://github.com/CannoliHQ/cannoli/blob/7755e376b6add0bbd25ea19104c368b7a40b979f/cannoli-ui/src/main/java/dev/cannoli/ui/components/PillRow.kt#L105-L122)).
Unselected rows are clipped, not ellipsised.

**Scrolling.** The list scrolls only as much as needed to keep the selection fully visible (top edge when moving up,
bottom edge when moving down). It jumps to index 0 when the selection teleports into the first screenful
([ListScrollEffect.kt#L69-L86](https://github.com/CannoliHQ/cannoli/blob/7755e376b6add0bbd25ea19104c368b7a40b979f/cannoli-ui/src/main/java/dev/cannoli/ui/components/ListScrollEffect.kt#L69-L86)).

**Empty states.** "No content found. Go add some!" on the main menu, and "%1$s is empty" in a list
([strings.xml#L434, #L524](https://github.com/CannoliHQ/cannoli/blob/7755e376b6add0bbd25ea19104c368b7a40b979f/cannoli-ui/src/main/res/values/strings.xml#L434)).
While the library loads, the app icon is shown centred
([SystemListScreen.kt#L124-L134](https://github.com/CannoliHQ/cannoli/blob/7755e376b6add0bbd25ea19104c368b7a40b979f/app/src/main/java/dev/cannoli/scorza/ui/screens/SystemListScreen.kt#L124-L134)).

**Disabled and hint rows** are drawn at 60% alpha of the text colour
([HintRow.kt#L35](https://github.com/CannoliHQ/cannoli/blob/7755e376b6add0bbd25ea19104c368b7a40b979f/cannoli-ui/src/main/java/dev/cannoli/ui/components/HintRow.kt#L35)).

---

## 4. Chrome

### MinUI

**Status pill (top right).** A dark-grey pill holding a **Wi-Fi** glyph (only when online) and a **battery** glyph
([`GFX_blitHardwareGroup`, api.c#L692-L777](https://github.com/shauninman/MinUI/blob/dbf89435a5179788418ff3573042cf9c7e75d9e0/workspace/all/common/api.c#L692-L777)).
The battery fill is proportional to charge. It switches to a "low" battery outline at ≤10% and a "low" fill at ≤20%,
and shows a bolt while charging
([`GFX_blitBattery`, api.c#L559-L589](https://github.com/shauninman/MinUI/blob/dbf89435a5179788418ff3573042cf9c7e75d9e0/workspace/all/common/api.c#L559-L589)).
There is **no clock and no percentage text**. Setting the clock is a separate Tools pak
([skeleton/EXTRAS/Tools/](https://github.com/shauninman/MinUI/tree/dbf89435a5179788418ff3573042cf9c7e75d9e0/skeleton/EXTRAS/Tools)).

**Brightness and volume overlay.** While a brightness or volume modifier is held, the status pill turns into an
icon plus an 80-unit bar and the hint row shows the key combo
([api.c#L700-L745, #L778-L788](https://github.com/shauninman/MinUI/blob/dbf89435a5179788418ff3573042cf9c7e75d9e0/workspace/all/common/api.c#L700-L745);
ranges brightness 0–10, volume 0–20,
[defines.h#L6-L9](https://github.com/shauninman/MinUI/blob/dbf89435a5179788418ff3573042cf9c7e75d9e0/workspace/all/common/defines.h#L6-L9)).

**Button-hint row (bottom).** Dark-grey pills, one aligned left and one right
([`GFX_blitButtonGroup`, api.c#L790-L838](https://github.com/shauninman/MinUI/blob/dbf89435a5179788418ff3573042cf9c7e75d9e0/workspace/all/common/api.c#L790-L838);
`GFX_blitButton` [#L610-L644](https://github.com/shauninman/MinUI/blob/dbf89435a5179788418ff3573042cf9c7e75d9e0/workspace/all/common/api.c#L610-L644)):

- **Buttons:** single-letter buttons are 20-unit white circles. Multi-letter buttons ("MENU", "POWER") are small white
  pills. The label is in upper case.
- **Left group:**
  - `POWER SLEEP` (or `MENU INFO` on devices that sleep with MENU) by default.
  - `X RESUME` when the highlighted item has a save state.
  - The modifier hint while adjusting brightness or volume.
- **Right group:** `A OPEN` at the root; `B BACK  A OPEN` deeper down; `B BACK` alone for an empty folder
  ([minui.c#L1650-L1670](https://github.com/shauninman/MinUI/blob/dbf89435a5179788418ff3573042cf9c7e75d9e0/workspace/all/minui/minui.c#L1650-L1670)).
- **Narrow screens** (< 320 px wide) keep only the primary pair (api.c#L810).

**Title.** None. The list starts at the top-left `PADDING`.

**Thumbnails.** Optional and minimal. If `<folder>/.res/<name.ext>.png` exists for the selected entry, it is drawn
right-aligned and vertically centred, at no more than screen-height square. Unselected rows are truncated short of
it, while the selected row may overlap it
([minui.c#L1492-L1519](https://github.com/shauninman/MinUI/blob/dbf89435a5179788418ff3573042cf9c7e75d9e0/workspace/all/minui/minui.c#L1492-L1519)).
There is no box art.

### Cannoli

**Status bar (top right).**

- Drawn as text and Nerd Font glyphs over the content, with no pill background.
- **Order:** cloud-sync, RomM cache, Kitchen, downloads, update, Bluetooth, Wi-Fi, VPN, battery (percentage or icon,
  plus a charging glyph), clock (`h:mm a` or `HH:mm`).
- **Updates:** the clock refreshes every 15 s. The battery comes from the sticky `ACTION_BATTERY_CHANGED` broadcast;
  Wi-Fi and VPN from `ConnectivityManager` network callbacks.
- **Configurable:** every item can be switched on or off
  ([StatusBar.kt#L77-L237](https://github.com/CannoliHQ/cannoli/blob/7755e376b6add0bbd25ea19104c368b7a40b979f/cannoli-ui/src/main/java/dev/cannoli/ui/components/StatusBar.kt#L77-L237);
  placement
  [AppNavGraph.kt#L1974-L1998](https://github.com/CannoliHQ/cannoli/blob/7755e376b6add0bbd25ea19104c368b7a40b979f/app/src/main/java/dev/cannoli/scorza/navigation/AppNavGraph.kt#L1974-L1998);
  [customization.md "Status Bar"](https://cannoli.dev/documentation/customization/#status-bar)).

**Bottom bar.** The left and right groups are "legend pills": an outer pill in the accent colour at 15% alpha,
holding an inner glyph pill at 30% alpha and an upper-case label. The glyph is 14 sp and the label 12 sp, both bold
([BottomBar.kt#L31-L152](https://github.com/CannoliHQ/cannoli/blob/7755e376b6add0bbd25ea19104c368b7a40b979f/cannoli-ui/src/main/java/dev/cannoli/ui/components/BottomBar.kt#L31-L152)).
Labels include BACK, SELECT, PLAY, LAUNCH, OPEN, NEW, SETTINGS, RESUME, QUIT and KITCHEN
([strings.xml](https://github.com/CannoliHQ/cannoli/blob/7755e376b6add0bbd25ea19104c368b7a40b979f/cannoli-ui/src/main/res/values/strings.xml#L3-L45)).

| Screen | Left group | Right group |
|---|---|---|
| Main menu ([SystemListScreen.kt#L262-L297](https://github.com/CannoliHQ/cannoli/blob/7755e376b6add0bbd25ea19104c368b7a40b979f/app/src/main/java/dev/cannoli/scorza/ui/screens/SystemListScreen.kt#L262-L297)) | `X SETTINGS`, plus `B QUIT` when enabled | `A SELECT` |
| Game list ([GameListScreen.kt#L280-L313](https://github.com/CannoliHQ/cannoli/blob/7755e376b6add0bbd25ea19104c368b7a40b979f/app/src/main/java/dev/cannoli/scorza/ui/screens/GameListScreen.kt#L280-L313)) | `B BACK` (`Y NEW` in collections) | `X RESUME` (when a save state exists) and `A PLAY`; `A LAUNCH` for Tools apps |

**Title.**

- Main menu: an optional user-set title, empty by default. When it is empty the space goes back to the rows
  ([SystemListScreen.kt#L141-L154](https://github.com/CannoliHQ/cannoli/blob/7755e376b6add0bbd25ea19104c368b7a40b979f/app/src/main/java/dev/cannoli/scorza/ui/screens/SystemListScreen.kt#L141-L154);
  [ListRhythm.kt `WithoutScreenTitle`](https://github.com/CannoliHQ/cannoli/blob/7755e376b6add0bbd25ea19104c368b7a40b979f/cannoli-ui/src/main/java/dev/cannoli/ui/components/ListRhythm.kt)).
- Game lists always show a breadcrumb title, e.g. the platform name, or `Name: "query"` while searching
  ([GameListScreen.kt#L188-L192](https://github.com/CannoliHQ/cannoli/blob/7755e376b6add0bbd25ea19104c368b7a40b979f/app/src/main/java/dev/cannoli/scorza/ui/screens/GameListScreen.kt#L188-L192)).

**Art.** Box art for the selected item (ROM or app) appears in a right-hand panel, 40% wide by default (0 or 35–65%),
scaled to Fit, Original, Fit Width or Fit Height, with 8 dp rounded corners
([GameListScreen.kt#L250-L278](https://github.com/CannoliHQ/cannoli/blob/7755e376b6add0bbd25ea19104c368b7a40b979f/app/src/main/java/dev/cannoli/scorza/ui/screens/GameListScreen.kt#L250-L278);
[customization.md "Art Width"](https://cannoli.dev/documentation/customization/#art-width)). Apps in Tools and Ports
can have art too, but **app icons are never shown** `[inference: no icon loading found; AppsRepository resolves only
artFile]`.

---

## 5. Behaviour

| Topic | MinUI | Cannoli |
|---|---|---|
| **Recents: size** | `MAX_RECENTS 24`, "a multiple of all menu rows" ([minui.c#L440](https://github.com/shauninman/MinUI/blob/dbf89435a5179788418ff3573042cf9c7e75d9e0/workspace/all/minui/minui.c#L440)) | 15 ([GameListViewModel.kt#L346](https://github.com/CannoliHQ/cannoli/blob/7755e376b6add0bbd25ea19104c368b7a40b979f/app/src/main/java/dev/cannoli/scorza/ui/viewmodel/GameListViewModel.kt#L346); [library.md](https://cannoli.dev/documentation/library/#recently-played)) |
| **Recents: order and de-dup** | Newest first. Re-launching an item bumps it to the top (no duplicates). Overflow drops the oldest. Multi-disc games collapse to one entry ([`addRecent`, #L456-L475](https://github.com/shauninman/MinUI/blob/dbf89435a5179788418ff3573042cf9c7e75d9e0/workspace/all/minui/minui.c#L456-L475); [`hasRecents`, #L518-L598](https://github.com/shauninman/MinUI/blob/dbf89435a5179788418ff3573042cf9c7e75d9e0/workspace/all/minui/minui.c#L518-L598)) | A `last_played_at` timestamp per ROM or app, so each item appears at most once. Ordered `last_played_at DESC`; ROMs and apps are mixed ([RecentlyPlayedRepository.kt#L6-L27](https://github.com/CannoliHQ/cannoli/blob/7755e376b6add0bbd25ea19104c368b7a40b979f/app/src/main/java/dev/cannoli/scorza/db/RecentlyPlayedRepository.kt#L6-L27)) |
| **Recents: what is shown** | Only entries whose file and emulator still exist; the alias they were opened under is kept ([`Recent_new`, #L386-L398](https://github.com/shauninman/MinUI/blob/dbf89435a5179788418ff3573042cf9c7e75d9e0/workspace/all/minui/minui.c#L386-L398); [`getRecents`, #L754-L771](https://github.com/shauninman/MinUI/blob/dbf89435a5179788418ff3573042cf9c7e75d9e0/workspace/all/minui/minui.c#L754-L771)) | Removable one at a time or in bulk via the context menu; the root entry can be hidden in settings ([library.md](https://cannoli.dev/documentation/library/#recently-played)) |
| **Recents: storage** | `/.userdata/shared/.minui/recent.txt`, one path per line plus an optional tab and alias ([defines.h#L21](https://github.com/shauninman/MinUI/blob/dbf89435a5179788418ff3573042cf9c7e75d9e0/workspace/all/common/defines.h#L21)) | `cannoli.db` (SQLite) ([directory-structure.md](https://cannoli.dev/documentation/directory-structure/#config-folder-reference)) |
| **Favourites** | None | Star `★` before the name. Favourites sort to the top of their own list and appear in a Favorites root entry ([Glyphs.kt#L13](https://github.com/CannoliHQ/cannoli/blob/7755e376b6add0bbd25ea19104c368b7a40b979f/cannoli-ui/src/main/java/dev/cannoli/ui/Glyphs.kt#L13); [GameListScreen.kt#L230-L236](https://github.com/CannoliHQ/cannoli/blob/7755e376b6add0bbd25ea19104c368b7a40b979f/app/src/main/java/dev/cannoli/scorza/ui/screens/GameListScreen.kt#L230-L236); [GameListViewModel.kt#L324](https://github.com/CannoliHQ/cannoli/blob/7755e376b6add0bbd25ea19104c368b7a40b979f/app/src/main/java/dev/cannoli/scorza/ui/viewmodel/GameListViewModel.kt#L324)) |
| **Collections** | `/Collections/<Name>.txt`, an ordered list of full paths, in file order with no alphabetical index ([README.txt#L192-L201](https://github.com/shauninman/MinUI/blob/dbf89435a5179788418ff3573042cf9c7e75d9e0/skeleton/BASE/README.txt#L192-L201); [`getCollection`, minui.c#L772-L797](https://github.com/shauninman/MinUI/blob/dbf89435a5179788418ff3573042cf9c7e75d9e0/workspace/all/minui/minui.c#L772-L797)) | Created and managed in the UI (context menu → Manage Collections; Y = new). Can be nested (children shown as `/Name` at the top of the parent) and reordered ([library.md "Collections"](https://cannoli.dev/documentation/library/#collections)) |
| **Hidden items** | Files starting with `.`, `*.disabled`, `map.txt`. Also any entry whose `map.txt` alias starts with `.` ([utils.c#L32-L34](https://github.com/shauninman/MinUI/blob/dbf89435a5179788418ff3573042cf9c7e75d9e0/workspace/all/common/utils.c#L32-L34); [minui.c#L248-L268](https://github.com/shauninman/MinUI/blob/dbf89435a5179788418ff3573042cf9c7e75d9e0/workspace/all/minui/minui.c#L248-L268); [README.txt#L203-L210](https://github.com/shauninman/MinUI/blob/dbf89435a5179788418ff3573042cf9c7e75d9e0/skeleton/BASE/README.txt#L203-L210)) | Dot-prefixed files and folders ([CHANGELOG](https://github.com/CannoliHQ/cannoli/blob/7755e376b6add0bbd25ea19104c368b7a40b979f/.github/CHANGELOG.md)). Ignore lists `ignore_extensions_roms.txt` and `ignore_files_roms.txt`. Apps appear only if the user ticked them in Manage Tools or Ports ([directory-structure.md](https://cannoli.dev/documentation/directory-structure/#config-folder-reference); [library.md](https://cannoli.dev/documentation/library/#tools-and-ports)) |
| **Sorting** | `strcasecmp` on the display name, re-sorted after `map.txt` aliases. No article stripping and no numeric-aware sort ([minui.c#L143-L150](https://github.com/shauninman/MinUI/blob/dbf89435a5179788418ff3573042cf9c7e75d9e0/workspace/all/minui/minui.c#L143-L150)) | ROMs: natural sort (lower-case, digit runs padded to 10 so `2` < `10`), stored as `sort_key` ([NaturalSort.kt](https://github.com/CannoliHQ/cannoli/blob/7755e376b6add0bbd25ea19104c368b7a40b979f/app/src/main/java/dev/cannoli/scorza/util/NaturalSort.kt); [RomsRepository.kt#L128](https://github.com/CannoliHQ/cannoli/blob/7755e376b6add0bbd25ea19104c368b7a40b979f/app/src/main/java/dev/cannoli/scorza/db/RomsRepository.kt#L128)). Apps and collections: `sort_order, display_name COLLATE NOCASE`, i.e. the user's order, then name ([AppsRepository.kt#L16](https://github.com/CannoliHQ/cannoli/blob/7755e376b6add0bbd25ea19104c368b7a40b979f/app/src/main/java/dev/cannoli/scorza/db/AppsRepository.kt#L16)). Favourites first. No article stripping found |
| **Sort-order prefixes** | A leading `NNN) ` is stripped *at draw time only*, so `001) Foo` sorts first but shows as `Foo` ([`trimSortingMeta`, utils.c#L123-L138](https://github.com/shauninman/MinUI/blob/dbf89435a5179788418ff3573042cf9c7e75d9e0/workspace/all/common/utils.c#L123-L138); [minui.c#L1600](https://github.com/shauninman/MinUI/blob/dbf89435a5179788418ff3573042cf9c7e75d9e0/workspace/all/minui/minui.c#L1600)) | Not found; explicit reorder mode instead |
| **Name display** | Takes the file name, strips 1–4-letter extensions (repeatedly), strips trailing `(...)` and `[...]` tags and trailing spaces, and keeps the original if nothing would be left ([`getDisplayName`, utils.c#L36-L72](https://github.com/shauninman/MinUI/blob/dbf89435a5179788418ff3573042cf9c7e75d9e0/workspace/all/common/utils.c#L36-L72)). Aliases come from `map.txt` (`file<TAB>Name`) | Strips every `(...)` and `[...]` tag into a separate `tags` field ([RomNaming.kt](https://github.com/CannoliHQ/cannoli/blob/7755e376b6add0bbd25ea19104c368b7a40b979f/app/src/main/java/dev/cannoli/scorza/util/RomNaming.kt)). Arcade names come from `arcade_map.txt`. Rename in the UI (renames the file for ROMs, sets the display name for apps) |
| **Duplicate names** | When two entries share a display name, a disambiguator (the file name, or `Name (EMU)`) is drawn in `#666` under the white name ([minui.c#L287-L305, #L1612-L1631](https://github.com/shauninman/MinUI/blob/dbf89435a5179788418ff3573042cf9c7e75d9e0/workspace/all/minui/minui.c#L287-L305)) | Only duplicates get a suffix: the tags (and `(PLATFORM)` in mixed lists), at 0.75× size in the accent colour ([GameListScreen.kt#L144-L169](https://github.com/CannoliHQ/cannoli/blob/7755e376b6add0bbd25ea19104c368b7a40b979f/app/src/main/java/dev/cannoli/scorza/ui/screens/GameListScreen.kt#L144-L169); [PillRow.kt#L211-L220](https://github.com/CannoliHQ/cannoli/blob/7755e376b6add0bbd25ea19104c368b7a40b979f/cannoli-ui/src/main/java/dev/cannoli/ui/components/PillRow.kt#L211-L220)) |
| **Collating systems** | Rom folders sharing a display name (e.g. `Game Boy Advance (GBA)` and `(MGBA)`) merge into one entry ([README.txt#L142-L146](https://github.com/shauninman/MinUI/blob/dbf89435a5179788418ff3573042cf9c7e75d9e0/skeleton/BASE/README.txt#L142-L146); [minui.c#L899-L938](https://github.com/shauninman/MinUI/blob/dbf89435a5179788418ff3573042cf9c7e75d9e0/workspace/all/minui/minui.c#L899-L938)) | Platforms sharing a display name are grouped ([SystemListViewModel.kt#L188-L197](https://github.com/CannoliHQ/cannoli/blob/7755e376b6add0bbd25ea19104c368b7a40b979f/app/src/main/java/dev/cannoli/scorza/ui/viewmodel/SystemListViewModel.kt#L188-L197)) |
| **Remembering selection** | Each open `Directory` keeps `selected/start/end`, so Back restores the parent's highlight ([minui.c#L1138-L1183](https://github.com/shauninman/MinUI/blob/dbf89435a5179788418ff3573042cf9c7e75d9e0/workspace/all/minui/minui.c#L1138-L1183)). On return from a game, `/tmp/last.txt` re-opens the folder path and re-selects the launched item; from Recently Played it simply selects the top row ([`saveLast`/`loadLast`, #L1212-L1290](https://github.com/shauninman/MinUI/blob/dbf89435a5179788418ff3573042cf9c7e75d9e0/workspace/all/minui/minui.c#L1212-L1290)) | Each screen saves its selected item and scroll offset when it closes, and restores by item identity after a rescan, so a re-sorted list keeps the same item selected ([SystemListViewModel.kt#L150-L154, #L266-L276](https://github.com/CannoliHQ/cannoli/blob/7755e376b6add0bbd25ea19104c368b7a40b979f/app/src/main/java/dev/cannoli/scorza/ui/viewmodel/SystemListViewModel.kt#L150-L154)) |
| **Resume** | X resumes the last-used save slot. Power-off in game is followed by auto-resume on boot | X resumes; hold X for the slot picker. "Swap Play and Resume" setting. Auto-resume after quit-in-game ([playing-games.md](https://cannoli.dev/documentation/playing-games/#resuming-a-game); [CHANGELOG](https://github.com/CannoliHQ/cannoli/blob/7755e376b6add0bbd25ea19104c368b7a40b979f/.github/CHANGELOG.md)) |
| **User settings** | None in the launcher. Brightness and volume by key combo only. Simple mode via a flag file | Settings screen. Left/Right cycles values ([SettingsViewModel.kt#L445-L449](https://github.com/CannoliHQ/cannoli/blob/7755e376b6add0bbd25ea19104c368b7a40b979f/app/src/main/java/dev/cannoli/scorza/ui/viewmodel/SettingsViewModel.kt#L445-L449)). Brightness and volume are left to Android `[unverified: no launcher-level control found]` |
| **Launching apps** | n/a (paks are shell scripts) | `getLaunchIntentForPackage` + `FLAG_ACTIVITY_NEW_TASK`, started with **no transition animation** via `startActivityNoAnim`, which passes `ActivityOptions.makeCustomAnimation(this, 0, 0)` ([ApkLauncher.kt#L30-L39](https://github.com/CannoliHQ/cannoli/blob/7755e376b6add0bbd25ea19104c368b7a40b979f/app/src/main/java/dev/cannoli/scorza/launcher/ApkLauncher.kt#L30-L39); [RetroArchLauncher.kt#L130-L135](https://github.com/CannoliHQ/cannoli/blob/7755e376b6add0bbd25ea19104c368b7a40b979f/app/src/main/java/dev/cannoli/scorza/launcher/RetroArchLauncher.kt#L130-L135)). The picker lists `ACTION_MAIN`/`CATEGORY_LAUNCHER` activities ([SettingsInputHandler.kt#L311-L313](https://github.com/CannoliHQ/cannoli/blob/7755e376b6add0bbd25ea19104c368b7a40b979f/app/src/main/java/dev/cannoli/scorza/input/screen/SettingsInputHandler.kt#L311-L313)) |

---

## 6. Configuration that drives the UI

**MinUI.** It is all files on the SD card and there is no settings UI
([README.txt](https://github.com/shauninman/MinUI/blob/dbf89435a5179788418ff3573042cf9c7e75d9e0/skeleton/BASE/README.txt)):

- **Folder layout.**
  - `/Roms/<Display Name> (<TAG>)/`: the folder name sets the system's display name; the tag selects the emulator.
  - `/Tools/<PLATFORM>/*.pak`: Tools entries.
  - `/Collections/*.txt`: collections.
- **`map.txt`.** Per-folder display-name aliases and hiding.
- **`.res/<file>.png`.** Thumbnails.
- **`/.userdata/shared/enable-simple-mode`.** Hides Tools and replaces Options with Reset
  ([README.txt#L212-L215](https://github.com/shauninman/MinUI/blob/dbf89435a5179788418ff3573042cf9c7e75d9e0/skeleton/BASE/README.txt#L212-L215)).
- **`/.userdata/<DEVICE>/auto.sh`.** A boot script (#L217-L219).
- **State files.** `recent.txt` and `auto_resume.txt`
  ([defines.h#L21-L32](https://github.com/shauninman/MinUI/blob/dbf89435a5179788418ff3573042cf9c7e75d9e0/workspace/all/common/defines.h#L21-L32)).

**Cannoli.** In-app settings stored in `Config/settings.json`, plus a library database and drop-in folders
([directory-structure.md](https://cannoli.dev/documentation/directory-structure/#config-folder-reference)):

- **Library database:** `Config/cannoli.db` holds collections, favourites, recents and order.
- **Name mapping and filtering:** `arcade_map.txt` and the two ignore lists.
- **Drop-in folders:** `Config/Fonts/`, `Wallpapers/`, `Art/<TAG>/`.
- **UI-relevant settings:**
  - Content mode; Recently Played and Favorites visibility; Manage Tools / Manage Ports.
  - Title, text size, font, the seven colours, background and tint.
  - Status-bar items, battery as percent or icon, 12/24-hour clock.
  - Art width and scale, portrait margin, screen geometry.
  - Swap Play/Resume, Main Menu Quit, glyph style and confirm button per controller.

  ([settings.md](https://cannoli.dev/documentation/settings/), [customization.md](https://cannoli.dev/documentation/customization/)).

---

## 7. Mapping to an Android launcher (MinimalUI)

Context that already constrains the choices:

- **[BRANDING.md](../../BRANDING.md)** (*decided*):
  - **Keyboard first:** everything is reachable without touch.
  - **Less on screen:** "Text over icons, one list at a time, no animations that delay input".
  - **Colour:** black background, white text, focused row inverted "as in MinUI".
  - **Type:** "the system font until a dedicated one is chosen".
  - **Fast:** opens instantly.
- **Already shipped** (§8): type-to-filter search, Home returns to the top, Back swallowed, system Settings always in
  the list.
- **Target hardware:** phones with physical keyboards (Zinwa, Clicks, Unihertz) and small, often square screens
  ([README.md](../../README.md)). Many have no shoulder buttons, and some have no D-pad `[unverified for specific
  models]`. MinUI/Cannoli button names (A/B/X/Y/L1/R1/Start/Select/Menu) therefore need keyboard equivalents.
- **Architecture rule:** logic goes in `core` as plain JVM code; `app` stays thin (`AGENTS.md`/`CONTRIBUTING.md`).

Legend: **Decided** = settled by branding or already shipped. **Open** = maintainer choice, with options listed.
**N/A** = has no Android-launcher equivalent.

### 7.1 Component-by-component translation

| MinUI / Cannoli element | Android-launcher translation | Status |
|---|---|---|
| ROM entry | A launchable activity (`ACTION_MAIN` + `CATEGORY_LAUNCHER`); `LauncherApp` already models one | Decided (shipped) |
| System folder (`/Roms/GBA`) | A grouping of apps; source still open (D2) | Open |
| Recently Played | Recently opened apps (D3) | Open |
| Collections / Favorites | User-made app groups or pins (D4, D5) | Open |
| Tools (MinUI) | The system Settings entry MinimalUI guarantees ([SystemSettings.kt](../../core/src/main/kotlin/dev/buildwithtim/minimalui/core/SystemSettings.kt)); possibly also a "Tools" group for utilities (D2) | Partly decided |
| Cannoli Tools/Ports buckets | Already "Android apps on a launcher list". MinimalUI lists every app by default and Cannoli only ticked ones. That is the inverse default; see hidden apps (D6) | Open |
| Display-name cleaning (`(USA)`, `[!]`, extensions, `001) `) | App labels have no ROM tags or extensions, so stripping is not needed. The useful parts are user aliases/renames (D7) and duplicate-label disambiguation (two "Phone" apps) | N/A for stripping; disambiguation open (D7) |
| Multi-disc `.m3u`, `.cue` auto-launch, BIOS, emulator pak lookup | — | N/A |
| Save states, Resume (X), auto-resume on boot, in-game menu | Android keeps app tasks alive. MinimalUI already starts apps with `FLAG_ACTIVITY_RESET_TASK_IF_NEEDED`, which brings back the existing task ([InstalledApps.kt](../../app/src/main/kotlin/dev/buildwithtim/minimalui/InstalledApps.kt)). There is no launcher-level save state | N/A |
| Box art / `.res` thumbnails | Branding says "Text over icons", so no art panel. An app-icon thumbnail is still conceivable (D16) | Decided (text), D16 open |
| Selection pill / inverted row | Inversion decided; shape open (D9) | Partly decided |
| Font BPreplay / M PLUS 1 Code | Branding: system font for now. A future dedicated font is open (D10) | Decided for now |
| Status pill (battery, Wi-Fi; Cannoli also clock) | Reading battery (sticky `ACTION_BATTERY_CHANGED`) and connectivity needs no dangerous permission ([battery monitoring](https://developer.android.com/training/monitoring-device-state/battery-monitoring); Cannoli does exactly this, [StatusBar.kt#L114-L148](https://github.com/CannoliHQ/cannoli/blob/7755e376b6add0bbd25ea19104c368b7a40b979f/cannoli-ui/src/main/java/dev/cannoli/ui/components/StatusBar.kt#L114-L148)). Whether to draw one at all is D11 | Open |
| Button-hint row | Needs keyboard key names, not pad letters (D12) | Open |
| Brightness/volume overlay | **Volume:** hardware volume keys are already handled by the system; a launcher can also call `AudioManager.adjustStreamVolume` without a permission ([AudioManager](https://developer.android.com/reference/android/media/AudioManager#adjustStreamVolume(int,%20int,%20int))). **Brightness:** writing `Settings.System.SCREEN_BRIGHTNESS` needs the special `WRITE_SETTINGS` access that the user grants via `ACTION_MANAGE_WRITE_SETTINGS` ([WRITE_SETTINGS](https://developer.android.com/reference/android/Manifest.permission#WRITE_SETTINGS), [Settings.System](https://developer.android.com/reference/android/provider/Settings.System)). A window-only brightness via `WindowManager.LayoutParams.screenBrightness` affects only MinimalUI's own window ([LayoutParams.screenBrightness](https://developer.android.com/reference/android/view/WindowManager.LayoutParams#screenBrightness)) (D14) | Open |
| Auto-sleep 30 s, power-off after 2 min, POWER = sleep | Screen timeout and power belong to Android. Locking from an app needs device-admin `force-lock` (`DevicePolicyManager.lockNow`) ([lockNow](https://developer.android.com/reference/android/app/admin/DevicePolicyManager#lockNow())); an accessibility service can also lock the screen (`GLOBAL_ACTION_LOCK_SCREEN`, API 28+) ([AccessibilityService](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService#GLOBAL_ACTION_LOCK_SCREEN)). Both are heavy-handed for a launcher `[inference]` | N/A (recommend leaving to system) |
| MENU-tap info screen (Release/Commit/Model) | An "About" screen or row showing version, build commit and `Build.MODEL` (D15) | Open |
| Up/Down, Left/Right paging, L1/R1 letter jump | Arrow keys exist on most keyboard phones `[unverified per model]`; L1/R1 do not. Key mapping is D8 | Open |
| A = open, B = back | Enter / D-pad centre opens (shipped). Back/Esc goes back in sub-lists; on the root list Back is swallowed (shipped) | Decided |
| Cannoli "Main Menu Quit" | A home app cannot meaningfully quit; MinimalUI swallows Back on the root | N/A |
| Cannoli Start context menu, Select multi-select/reorder | Possible as context actions on an app (D13) | Open |
| Cannoli global search (R1 + on-screen keyboard) | Type-to-filter needs no on-screen keyboard on a hardware keyboard. It already matches word starts, ignoring case and accents, the same matching Cannoli advertises ([FilterQuery.kt](../../core/src/main/kotlin/dev/buildwithtim/minimalui/core/FilterQuery.kt), [AppFilter.kt](../../core/src/main/kotlin/dev/buildwithtim/minimalui/core/AppFilter.kt)) | Decided (shipped) |
| Empty states ("Empty folder", "%s is empty") | Needed for an empty group and for a filter matching nothing (D17) | Open |
| No-animation app launch (Cannoli `makeCustomAnimation(0,0)`) | Fits "no animations that delay input". Today MinimalUI uses the default activity transition (`startActivity` without options; [MainActivity.kt](../../app/src/main/kotlin/dev/buildwithtim/minimalui/MainActivity.kt)) (D18) | Open (branding leans "yes") |
| Remember selection per list, restore after launch | MinUI restores the launched item; MinimalUI's Home press resets to the top (shipped). Behaviour on returning from an app via Back or Recents is D19 | Partly decided |
| Configuration files (`map.txt`, `Collections/*.txt`) | Scoped storage makes shared, user-editable files awkward ([data storage overview](https://developer.android.com/training/data-storage)); in-app UI plus app-private storage is the Android-native option (D20) | Open |
| Licence reuse | MinUI has no licence, so do not copy its code or the `assets@Nx.png` sprite sheet. Re-implement from behaviour. BPreplay is OFL, so it can be bundled with its licence. Cannoli is GPL-3.0, like MinimalUI, so its code may be adapted with attribution | Constraint |

### 7.2 Decision points (maintainer preference or several reasonable options)

**D1. Root-screen structure.**

- **(a)** Keep one flat alphabetical list (today). Simplest and fastest to scan with type-to-filter, but there is no
  "recent" shortcut.
- **(b)** MinUI-style root: `Recently Used` (when non-empty), `Favorites`/`Collections` (when non-empty), the groups,
  then `Settings` last, like MinUI's Tools. This adds a level: two key presses to reach any app without filtering.
- **(c)** Hybrid: the flat list with a short recents/favourites block pinned above it, separated by nothing (MinUI
  never draws separators) or by a blank row. One list stays one list, matching "one list at a time".
- **(d)** Cannoli-style content modes as a setting. This adds settings surface that branding does not ask for.

**D2. What replaces "systems".**

- **(a)** Nothing (flat).
- **(b)** Android's `ApplicationInfo.category` (`CATEGORY_GAME`, `PRODUCTIVITY`, `SOCIAL`, …; API 26+, so available
  at MinimalUI's minSdk 28). It is declared by the developer and many apps leave it `CATEGORY_UNDEFINED`, so an
  "Other" bucket would be large
  ([ApplicationInfo.category](https://developer.android.com/reference/android/content/pm/ApplicationInfo#category)).
- **(c)** User-made groups only, like MinUI collections or Cannoli buckets.
- **(d)** Both: automatic categories plus user groups.

  Collation of same-named groups (MinUI, Cannoli) only matters with (b) or (d).

**D3. Recents: data source.**

- **(a)** Track launches made through MinimalUI. Needs no permission and is simple to unit-test in `core`, but misses
  apps opened from notifications or other routes.
- **(b)** `UsageStatsManager`. It needs `PACKAGE_USAGE_STATS`, a special access the user must grant in Settings, and
  is more accurate ([UsageStatsManager](https://developer.android.com/reference/android/app/usage/UsageStatsManager)).

**D4. Recents: rules.**

- **Size:** 24 (MinUI, "a multiple of all menu rows"), 15 (Cannoli), or one screenful.
- **De-duplication:** bump to the top on re-launch (both projects).
- **Missing apps:** drop uninstalled apps (both projects only list what still exists).
- **Removal:** whether the user can remove an entry (Cannoli yes, MinUI no).
- **Visibility:** whether the root entry appears only when non-empty (both projects: yes).

**D5. Favourites and collections.**

- **(a)** None.
- **(b)** Cannoli-style favourites: a star prefix, pinned to the top of lists, plus a Favorites entry.
- **(c)** MinUI-style ordered collections, kept in the order the user builds them, with no alphabetical index.
- **(d)** Both.

  Cannoli's nested collections are probably beyond "minimal" `[inference]`.

**D6. Hidden apps.**

- **(a)** Show everything (today).
- **(b)** Per-app "hide" with a way back, e.g. a "Hidden apps" list reachable from settings or the context menu.
  MinUI hides by `.` prefix or a `.`-alias in `map.txt`.
- **(c)** Cannoli-style opt-in lists, where only picked apps appear. That inverts the default and adds a set-up step.

  With (b) or (c), the guaranteed Settings entry must stay unhideable, because it is the user's route to change the
  default home app ([SystemSettings.kt](../../core/src/main/kotlin/dev/buildwithtim/minimalui/core/SystemSettings.kt)).

**D7. Renaming and duplicate labels.**

- **Renaming:** whether users may alias app labels (MinUI `map.txt`, Cannoli Rename). If yes, sorting, filtering and
  recents must all use the alias.
- **Duplicate labels:**
  - **(a)** Suffix the package name in dim grey, MinUI-style (`#666`).
  - **(b)** Show a suffix only for duplicates, at a smaller size, Cannoli-style.
  - **(c)** Nothing.

**D8. Key mapping for a keyboard phone.**

Shipped today: arrows/D-pad move, Enter/centre opens, typed letters filter, Home goes to the top, Back is swallowed.
Open choices:

- **Back in sub-lists:** Back/Esc.
- **Wrap-around:** MinUI-style (wrap only on a fresh press, stop on a held repeat), Cannoli-style (always wrap), or
  none (today).
- **Page jump:** Left/Right as in both projects, which only works when there is no horizontal use for those keys. The
  alternatives are PageUp/PageDown, Space and Shift+Space, or the volume keys (which steals system volume).
- **Letter jump (L1/R1):** largely superseded by type-to-filter. It is only worth keeping for D-pad-only input.
- **Context menu:** Menu key, long-press Enter, or a dedicated letter. Typing letters already filters, so a letter key
  conflicts.
- **Repeat timing:** the platform key-repeat or MinUI's 300/100 ms or Cannoli's 280/80 ms. Android already
  auto-repeats key events, so this matters only if custom repeat is wanted.

**D9. Selection shape.**

- **(a)** Full-width inverted bar (today).
- **(b)** MinUI/Cannoli pill: rounded ends (radius = height/2), width hugging the text, rows contiguous with no gap,
  text inset about 12 units or 14 dp.

  BRANDING decides *inversion* only. "Pill" is the visual signature of both projects.

**D10. Font and size.**

- **Font:** stays the system font (decided for now). Later candidates: BPreplay Bold (MinUI's; OFL) or M PLUS 1 Code
  (Cannoli default; OFL). Both are assets rather than Gradle dependencies, but they still add APK size and a licence
  file `[inference]`.
- **Size:** MinUI uses 16 pt × scale and Cannoli 24 sp by default, with a user setting from 10 to 30 sp. MinimalUI uses
  M3 `titleLarge` today.

  A fixed size suits "legible at arm's length". A size setting adds settings surface.

**D11. Status pill.**

- **(a)** None; keep Android's own status bar visible (accurate, zero work).
- **(b)** Hide the system bar (immersive) and draw a MinUI pill: battery glyph plus Wi-Fi when connected, no clock.
- **(c)** Cannoli-style text: battery %, Wi-Fi, Bluetooth, clock, configurable. A clock is arguably more useful on a
  phone than on a handheld `[inference]`.

  Reading the clock and battery is permission-free. Bluetooth state needs `BLUETOOTH_CONNECT` on API 31+
  ([BluetoothAdapter.isEnabled](https://developer.android.com/reference/android/bluetooth/BluetoothAdapter#isEnabled())).

**D12. Button-hint row.**

- **(a)** None (today); the README key table documents the keys.
- **(b)** MinUI-style pills at the bottom, e.g. `ENTER OPEN`, `ESC BACK`, `MENU OPTIONS`. Costs a row on small square
  screens; MinUI reserves one pill height for it.
- **(c)** Show it only until the user has used each key once.

  Labels must be keyboard key names. Pad letters (A/B) mean nothing on a Clicks keyboard `[inference]`.

**D13. Context actions per app.**

- Candidates: favourite, add to group, rename, hide, open App info
  (`Settings.ACTION_APPLICATION_DETAILS_SETTINGS`), uninstall (`Intent.ACTION_DELETE`).
- Presentation:
  - **(a)** None.
  - **(b)** A Cannoli-style context menu: a second list drawn in the same style, so it is still "one list at a time".

**D14. Brightness and volume.**

- **(a)** Leave both to the system (no code).
- **(b)** A MinUI-style overlay bar driven by launcher key combos. Volume needs no permission; system brightness needs
  `WRITE_SETTINGS` special access. A launcher can only adjust these while it is in the foreground.

**D15. About / info screen.**

- **(a)** None.
- **(b)** A MinUI-style "Release / Commit / Model" panel on a key.
- **(c)** An About row in a settings list.

**D16. Icons.**

- Branding says text over icons, so the default is text-only, as in MinUI and Cannoli.
- **Option:** a MinUI-style thumbnail of the *selected* app's icon only, drawn right-aligned. It helps recognise
  similarly named apps but costs icon loading and is arguably "on screen".

**D17. Empty and loading states.**

- **Wording:** MinUI's terse "Empty folder", or Cannoli's "%s is empty" / "No content found".
- **Filter matching nothing:** today only the typed query shows.
- **Loading:** a blank screen vs Cannoli's centred logo. "Opens instantly" argues for nothing visible.

**D18. Launch transition.**

- **(a)** System default (today).
- **(b)** No animation, like Cannoli's `ActivityOptions.makeCustomAnimation(context, 0, 0)` (ActivityOptions:
  [makeCustomAnimation](https://developer.android.com/reference/android/app/ActivityOptions#makeCustomAnimation(android.content.Context,%20int,%20int))).
  Branding's "no animations that delay input" favours (b).

**D19. Selection memory.**

- Within groups (if D1 adds levels): restore the parent's highlight on Back (both projects do).
- Returning to the launcher by any route other than Home:
  - **(a)** Keep the current position.
  - **(b)** Reset to the top.
  - **(c)** Select the last-launched app, as MinUI's `last.txt` does.
- Home already resets to the top (decided).

**D20. Where configuration lives.**

- **(a)** In-app settings list (Cannoli) in app-private storage. This is the natural Android choice and testable in
  `core`.
- **(b)** MinUI-style text files. User-editable, but scoped storage forces SAF or app-specific directories that users
  cannot easily reach.
- **(c)** No configuration at all (MinUI's "No settings").

  Each user-facing setting added is a branding question ("Less on screen").

**D21. Rows per screen.**

- **(a)** Lazy list sized by content (today; partial last row possible).
- **(b)** MinUI/Cannoli: compute a whole number of rows that fit between the chrome and spread the remainder, so a
  page is always whole rows. Paging (D8) then moves by exact pages.

**D22. Work-profile and other-user apps.**

- Today MinimalUI queries `PackageManager` for the current user only `[inference]`. Showing work-profile apps would
  need `LauncherApps`
  ([LauncherApps](https://developer.android.com/reference/android/content/pm/LauncherApps)).
- Neither MinUI nor Cannoli has an equivalent. Whether this is in scope is the maintainer's call.

---

## 8. What MinimalUI already has (as of `08dfb33a`)

| Feature | MinimalUI today | Compared with MinUI / Cannoli |
|---|---|---|
| App list | One list of every `CATEGORY_LAUNCHER` activity, plus system Settings if no Settings activity is listed ([InstalledApps.kt](../../app/src/main/kotlin/dev/buildwithtim/minimalui/InstalledApps.kt), [SystemSettings.kt](../../core/src/main/kotlin/dev/buildwithtim/minimalui/core/SystemSettings.kt)) | Equivalent to a single MinUI system list; no root menu, recents, collections or groups |
| Sorting | `Collator(Locale.ROOT)` at SECONDARY strength (case-insensitive, accents next to base letter), tie-break by package then activity ([AppOrdering.kt](../../core/src/main/kotlin/dev/buildwithtim/minimalui/core/AppOrdering.kt)) | More locale-correct than MinUI's `strcasecmp`. Not numeric-aware like Cannoli's natural sort. No article stripping in any of the three |
| Search | Type-to-filter on word starts, ignoring case and accents. Label-prefix matches come first. Query shown above the list with a trailing `_` ([AppFilter.kt](../../core/src/main/kotlin/dev/buildwithtim/minimalui/core/AppFilter.kt), [FilterQuery.kt](../../core/src/main/kotlin/dev/buildwithtim/minimalui/core/FilterQuery.kt), [AppList.kt](../../app/src/main/kotlin/dev/buildwithtim/minimalui/ui/AppList.kt)) | MinUI has no search. Cannoli's needs R1 plus an on-screen keyboard and Start to submit. MinimalUI's is more direct for keyboards |
| Selection look | Full-width row, white background, black text; 16 dp horizontal and 10 dp vertical padding; M3 `titleLarge` system font ([AppList.kt](../../app/src/main/kotlin/dev/buildwithtim/minimalui/ui/AppList.kt), [Theme.kt](../../app/src/main/kotlin/dev/buildwithtim/minimalui/ui/Theme.kt)) | Same colours as both defaults. Shape differs: no pill, not text-hugging (D9). Font differs (D10) |
| Long labels | One line, ellipsis | Like MinUI's `...` truncation; Cannoli marquees the selected row instead |
| Navigation | Compose focus traversal with arrows/D-pad; Enter or centre opens; touch also works. No wrap, page jump or letter jump `[inference: default focus search, no custom handling in AppList.kt]` | D8, D21 |
| Home key | `singleTask`; `onNewIntent` clears the query and focuses and scrolls to the first row ([MainActivity.kt](../../app/src/main/kotlin/dev/buildwithtim/minimalui/MainActivity.kt), [AndroidManifest.xml](../../app/src/main/AndroidManifest.xml)) | MinUI has no Home concept; Cannoli has no `onNewIntent` override `[observed: none in MainActivity.kt]` |
| Back key | Swallowed on the home screen | Matches MinUI root (B does nothing at the root) |
| Launching | `FLAG_ACTIVITY_NEW_TASK \| FLAG_ACTIVITY_RESET_TASK_IF_NEEDED`; ignores `ActivityNotFoundException` (then refreshes) and `SecurityException` | Cannoli uses `getLaunchIntentForPackage` and no animation (D18) |
| Live updates | Reloads on resume and on package add/remove/change broadcasts, off the main thread ([LatestLoader.kt](../../core/src/main/kotlin/dev/buildwithtim/minimalui/core/LatestLoader.kt)) | Cannoli rescans on a file watcher. MinUI reads the folder each time a directory opens |
| Chrome | None: no status pill, hint row or title. Respects `safeDrawing` insets | D11, D12 |
| Not present | Recents, favourites, collections or groups, hiding, renaming, settings screen, about screen, empty-state text, brightness/volume | D3–D7, D13–D15, D17 |
