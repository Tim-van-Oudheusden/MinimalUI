# MinimalUI

![MinimalUI: a minimal Android launcher for keyboard-centric phones](docs/brand/banner/readme-banner.svg)

A minimal Android launcher for keyboard-centric phones, such as those from Zinwa, Clicks and Unihertz. Inspired by
[MinUI](https://github.com/shauninman/MinUI) and [Cannoli](https://github.com/CannoliHQ/cannoli): a plain list of
apps, driven entirely by keys.

## Status

Early development (version 0.1.0). There are no releases yet, and no phone is listed as supported until it has been
tested on one. Today the launcher shows every installed app in one alphabetical list and opens the one you pick.

## Requirements

Android 9 (API 28) or newer. A hardware keyboard or D-pad is the intended input; touch also works.

## Installing

Pick one:

- **Download a build.** Every CI run on GitHub uploads a debug APK as the `minimalui-debug-apk` artifact, kept for 14
  days. Open a recent run of the `CI` workflow under the repository's **Actions** tab (you must be signed in to GitHub),
  download the artifact, unzip it, and install `app-debug.apk` on the phone. You can do this with `adb install
  app-debug.apk`, or by opening the file on the phone and allowing installs from that source.
- **Build from source.** Follow [Getting started](CONTRIBUTING.md#getting-started) in `CONTRIBUTING.md`, then run
  `./gradlew installDebug` with the phone connected.

## Making it your home screen

After installing, choose MinimalUI as the default home app. On most phones the setting is under **Settings → Apps →
Default apps → Home app**; the exact wording varies by manufacturer.

To switch back, open **Settings** from the MinimalUI list (MinimalUI keeps it in the list for this) and choose
another home app in the same place.

## Keys

| Key                          | Action                                                                        |
| ---------------------------- | ----------------------------------------------------------------------------- |
| Up / Down (arrows, D-pad)    | Move one row. A fresh press past either end wraps around; holding stops there |
| Left / Right (arrows, D-pad) | Turn a page, stopping at the first or last app                                |
| Enter or D-pad centre        | Open the highlighted app                                                      |
| Home                         | Return to the top of the list                                                 |
| Back                         | Does nothing on the home screen                                               |

The list updates on its own when apps are installed or removed.

## Contributing

See [`CONTRIBUTING.md`](CONTRIBUTING.md) for setup, project layout and the quality gates. AI agents follow
[`AGENTS.md`](AGENTS.md).

## Licence

[GNU General Public License v3.0](LICENSE).
