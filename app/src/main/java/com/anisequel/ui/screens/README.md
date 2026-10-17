# `ui/screens/` — one directory per destination

A *destination* is a place a reader can be sent and come back from: the dashboard, a
profile, a studio, a settings tab, a tier board. Each gets a directory holding its
screen file **and everything only that screen draws**.

The rule that decides placement: **would this file move if the destination moved?** If
yes, it belongs in the destination's directory. If it would be dragged along no matter
where you go, it is shared and belongs in `ui/components/`.

## Where things go

| Path | Holds |
|---|---|
| `screens/dashboard/` | `DashboardScreen.kt` and the four files only it draws. |
| `screens/profile/` | `UserProfileScreen.kt`, its five tabs, the two activity sheets, the chart components. |
| `screens/studio/` | `StudioScreen.kt` and its header, shelves and title cards. |
| `screens/tier/` | The tier list, the board, the library and the placement sheets. |
| `screens/settings/` | `SettingsScreen.kt` and its four tabs and section cards. |
| `screens/login/` | `LoginScreen.kt`, the hero, the OAuth and username cards. |

## Rules

- **The screen file lives with its parts.** `profile/` and `studio/` and `tier/` keep
  their screen file inside the directory; so do the other three. A screen file at
  `screens/` root with its components in a subdirectory is the one arrangement to
  avoid — it reads as though the screen belongs to no destination.
- **A screen holds no `NavController`.** Every "go somewhere" is a callback the caller
  supplies. `ProfileDestination`, `StudioScreen` and `DashboardScreen` all work this
  way, and it is why the same screen can be reached from three different routes.
- **A `ModalBottomSheet` drawn by a tab is a bug.** `AnimatedContent` disposes the
  outgoing tab when its transition ends, and a sheet is its own window — so it is torn
  down with the tab, mid-sentence, draft included. Both sheets on the profile are
  therefore drawn by `UserProfileScreen`, as siblings of the tab content.
- **Hoist anything that must survive a tab switch.** Scroll states, sheet state, and
  which sub-section is open all die with the tab otherwise.