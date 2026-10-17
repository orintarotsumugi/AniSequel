# `ui/` — presentation

Everything the reader sees. This layer decides what a thing *looks like* and *what
happens next*; it never decides what a fact is, and it never talks to AniList.

## Where things go

| Path | Holds |
|---|---|
| `ui/screens/<destination>/` | One screen and everything only it draws. See `screens/README.md`. |
| `ui/components/` | Shared controls and the reusable pieces. See `components/README.md`. |
| `ui/viewmodel/` | State for a screen, plus the store shared between two of them. |
| `ui/navigation/` | Routes and the one place a `NavController` is used. |
| `ui/theme/` | Colour schemes, typography, shapes, and the dynamic-colour builder. |
| `ui/widget/` | The home-screen widgets. Each is a separate process entry point. |

## Rules

- **A composable takes data and callbacks; it holds no `NavController`.** Every route
  decision is made by the caller. This is why the same detail sheet is reachable from
  the dashboard, a profile, a studio page and a profile activity row without knowing
  about any of them.
- **Animate transforms in the draw phase.** `Modifier.graphicsLayer { }`,
  `Modifier.bouncyPress(...)`. Animating a value that layout reads re-runs layout on
  every frame — which is why the Info page's parallax is a lambda and not a `Float`.
- **`remember` needs keys when what it reads can change.** An unkeyed `remember` holding
  a `derivedStateOf` keeps the state it was created with forever, which is how opening
  a relation left a second copy of the close button on screen. See
  `InfoFloatingBarKeyingTest`.
- **Springs come from `ExpressiveMotion`.** `AGENTS.md` §3 has the tokens and the
  reason six literal copies of one spring is a bug.
- **Capsules are `RoundedCornerShape(percent = 50)`, not `RoundedPolygon`.** The
  expressive shapes are authored on a square perimeter and stretch to the `Size` they
  are given, so a 120×44 tab renders as an ellipse from `MaterialShapes.Pill`.

## ViewModels

- One per screen, keyed so two instances cannot share state. `ProfileDestination` keys
  on `username` + `id` precisely so following a follower cannot reuse the previous
  person's ViewModel and render their bio.
- **Hoist state that must outlive an `AnimatedContent`.** A remembered scroll position
  inside a tab dies with the tab's transition, so returning to a tab dropped the reader
  at the top. Every profile tab's state is held by the screen.
- **Never hold a `Context` in a field.** `ThemePreferences` takes one per access and is
  remembered on it.