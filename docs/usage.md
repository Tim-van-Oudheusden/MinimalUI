# Using MinimalUI

How the launcher behaves, screen by screen. The keys themselves are in the README's [Keys](../README.md#keys)
table.

## The home screen

The home screen is one list. At its top, your favourites, marked `★`, in alphabetical order; then the apps you opened
last from MinimalUI, newest first (leaving out favourites); then a **Collections** row once you have a collection;
then one blank row, then every app in alphabetical order (those at the top too), then another blank row and a
**Groups** row last, so Up from the first app reaches it at once. Up and Down skip the blank rows. The
**Recently used** setting picks how many recent apps show there: Off, 3 (the default), 5 or 15. Typing filters all
apps by their name (the `★` is not part of it), favourites first, and hides the block and the Groups row until the
filter is cleared. Hidden apps still match, listed in grey after the others; Enter opens them as usual.

The list updates on its own when apps are installed or removed. Apps open without a transition animation. Coming
back from an app by Back, Recents or the app closing selects that app again, so one press reopens it; the filter
stays. Home instead clears the filter and returns to the top.

## The app menu

The app menu replaces the list until it is closed: App info, Uninstall (dim and inactive for system apps, work apps, app
clones and the Settings entry), Add to favourites or Remove from favourites, Add to collection, Add to group, Rename
(dim and inactive for the MinimalUI settings row), Hide or Unhide (dim and inactive for Android's Settings and the
MinimalUI settings row, so the way back to another home app always stays listed), and, when there are recently used
apps, Remove from recently used and Clear recently used. After an action, Back or Esc, the list comes back with the
same app highlighted.

## Renaming apps

**Rename** opens the text editor on the app's current name, the cursor at its end: type the new one (any characters,
spaces included) and press Enter, or Esc to cancel and return to the menu. The app is then listed, sorted, filtered
and shown in the recently used block, favourites, collections and Hidden apps by that name, and stays highlighted at
its new place. An empty name, or the app's own, gives it back its own name. Names are kept by app in the app's private
storage, so they survive app updates and reinstalls. When two apps are listed under the same name (letter case
ignored), each shows its package name after it, smaller and in grey (or its activity's name when both come from one
package); a work app shows `Work` there instead and an app clone `Clone`, and an app whose name only work apps or
clones share shows nothing. Typing never matches that part.

## Hiding apps

**Hide** takes an app off the home screen, its favourites and recently used block, every collection and every group;
it stays a favourite, recent and in its collections and groups, so **Unhide** brings it back where it was. Two ways
lead back to a hidden app: type its name and open its menu, or open **MinimalUI settings → Hidden apps**, which lists
the hidden apps alphabetically (or says `No hidden apps`) and unhides the highlighted one with Enter. Uninstalling a
hidden app does not forget it: reinstalled, it stays hidden until you unhide it. Hidden apps are kept in the app's
private storage.

## Collections

Collections are named lists of apps in your own order, as in MinUI. **Add to collection** lists the collections that
do not hold the app yet and a last row, **New collection**: type a name in the text editor (spaces and punctuation
allowed) and press Enter to make it with the app, or Esc to cancel. Enter on the home screen's **Collections** row
lists them by name; Enter on a name lists its apps, which open like any other. Inside a collection the app menu also
offers Remove from collection, Move up and Move down; a collection goes away with its last app. A name that differs
from an existing collection's only in letter case (`fun` for `Fun`) adds to that collection, as MinUI's folders do.
Favourites and collections are kept in the app's private storage.

## Groups

Groups gather apps the way MinUI's systems gather games. Every app falls in an automatic group from the category its
developer declared: Games, Audio, Video, Images, Social, News, Maps, Productivity, Accessibility, or Other when it
declared none (the MinimalUI settings row is in none). **Add to group** lists your own groups that do not hold the app
and a last row, **New group**, which makes one with the app from a name typed in the text editor. Enter on the
**Groups** row lists the groups by name, alphabetical with Other last; a group of yours named like an automatic one is
one entry holding the apps of both. Letter case does not tell groups apart: `games` is the Games group, and a new or
renamed group named like one of yours in another case joins it. Enter on a name lists its apps in the home screen's
order (renamed apps by their new name, favourites with their `★`); they open like any other, and coming back keeps
the group open on that app. Inside a group of yours the app menu also offers Remove from group; a group goes away with
its last app. The Menu key on a group in the list opens its menu: **Rename group** (the text editor, prefilled; a name
another group of yours has merges the two) and **Delete group**, which forgets only your group, never the apps or an
automatic group. For an automatic group both rows show grey and do nothing. A group whose apps are all hidden or
uninstalled is not listed but kept until you delete it. Your groups are kept in the app's private storage.

## Work apps

Apps of a work profile are listed in one more automatic group, **Work**, and nowhere else on their own: not in the
home screen's full list nor in their category's group. They still show in the favourites and recently used block when
you make one a favourite or open it, typing finds them like any app, and you can add them to collections and your own
groups, rename or hide them. A work app opens in its profile; App info shows its profile's details, and Uninstall is
inactive (remove work apps from the work profile's own settings). A work app and the personal app it copies are two
apps everywhere. While work apps are turned off (work mode paused), the Work group still lists them in grey; opening
one lets Android offer to turn work apps back on. The list updates when a work profile is added or removed, work mode
is paused or resumed, or an app is installed or removed in the profile.

Apps your phone cloned for a second account ("dual apps", an app-clone profile) are not work apps: from Android 15 on
they are listed with your other apps, in the home screen's full list and their category's group, right after the app
they copy and marked `Clone`. Like a work app, a clone opens in its own profile and Uninstall is inactive for it.
Android 14 does not tell a launcher which kind a profile is, so there clones are listed as work apps.

## MinimalUI settings

MinimalUI's own settings open from the **MinimalUI settings** row, listed right after Android's Settings (type `min`
to find it). They replace the list until closed, the way the app menu does: Clock and Battery percentage show or hide
those items in the status pill (both on by default), Recently used sizes the block at the top of the list, Hidden apps
lists the apps you hid, and About at the bottom shows the version and the commit the app was built from. Settings are
kept in the app's private storage.

## Where your setup is kept

Favourites, recently used apps, collections, renamed apps, hidden apps, your groups and MinimalUI's settings are all
kept in the app's private storage on the phone. Installing a newer release over the one you have keeps them.
Uninstalling MinimalUI, or **Clear storage** in Android's App info for it, erases them all, and there is no way to get
them back: MinimalUI is left out of Android's backup and of its transfer to a new phone, so on a new phone it starts
empty. This is also why switching between a debug build and a release, which needs an uninstall first (see the
README's [Installing](../README.md#installing)), starts over from an empty setup.
