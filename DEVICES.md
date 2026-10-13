# Devices

Phones MinimalUI has been tried on, and how well it worked. A phone is only listed after someone has run it there and
filed a [device report](#reporting-a-device); nothing is assumed from specifications.

## Reported devices

| Phone | Android | Input | Build | Result | Report |
| ----- | ------- | ----- | ----- | ------ | ------ |

No device has been reported yet.

**Result** is one of:

- **Works**: every check below passed.
- **Partly works**: some checks failed; the report links an issue for each.
- **Does not work**: MinimalUI cannot be used as the home app on this phone.

**Input** is how the phone was driven: built-in keyboard, D-pad, external keyboard, or touch only. **Build** is the
commit the APK was built from, so a result can be traced to the code it was tested against.

## Reporting a device

Open a new issue with the **Device report** template and tick the checks that passed. For each check that failed,
say what happened instead; a maintainer turns those into bug reports and adds a row to the table above.

Install a build as described under [Installing](README.md#installing) in the README, then run these checks:

1. **Home app.** MinimalUI can be chosen as the default home app, and the Home key brings its list back.
2. **Moving.** Up and Down move one row; Left and Right turn a page.
3. **Opening.** Enter (or the D-pad centre) opens the highlighted app. Coming back from it highlights the same app.
4. **Filtering.** Typing letters on the keyboard narrows the list; Home clears the filter.
5. **App menu.** Menu, or holding Enter, opens the highlighted app's menu; Back or Esc closes it.
6. **Status pill.** The pill in the top-right corner shows the battery level and the time, and Wi-Fi when connected.
7. **Live list.** Installing or uninstalling an app updates the list without restarting MinimalUI.
8. **Way out.** The Settings entry in the list opens Android's settings, where another home app can be chosen.
