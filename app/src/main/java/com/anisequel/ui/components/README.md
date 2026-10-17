# `ui/components/` — shared, reusable presentation

Everything here is drawn by more than one screen, or is a control rather than a
surface. If a file is only ever drawn by one destination, it belongs next to that
destination instead — see `ui/screens/*/`.

## Where things go

| Path | What belongs there |
|---|---|
| `components/` (top level) | Controls with no single owner: `EngagementAction`, `EmptyStateView`, `ShimmerLoading`, `OpenExternalUrl`. |
| `components/detail/` | The one title page — the sheet, the entry editor, the subject type that lets a sequel and a bare media share it. |
| `components/detail/info/` | The Info page's sections. One file per section, plus the page shell. |
| `components/cards/` | Poster cards and the controls that live on them. |
| `components/expressive/` | The Material 3 Expressive layer: motion tokens, shapes, buttons, indicators. |
| `components/icons/` | Custom `ImageVector`s that `AppVectorIcons` re-exports. |
| `components/markdown/` | AniList-flavored Markdown: parser, AST, inline model, renderer. Pure — no state, no screens. |
| `components/update/` | The in-app updater's dialogs. |

## Rules

- **No screen logic here.** A component takes data and callbacks. If it needs a
  ViewModel, it is a screen.
- **One control, one declaration.** `EngagementAction` was `private` to the activity
  card until the comment sheet needed the same heart; re-declaring it would leave four
  visual details free to drift apart. If you find yourself copying a `@Composable`
  into a second file, move it here instead.
- **Vectors only.** No emoji, no drawable resources for UI. See `AGENTS.md` §2.
- **Springs come from `ExpressiveMotion`.** Never inline a literal spring; six copies
  of one is how they drift.

## Adding a shared component

1. Does more than one screen draw it? If not, it is not shared — keep it local.
2. Does it hold state? Only `remember`/`rememberSaveable` for its own rendering.
   Anything the caller needs back is a parameter.
3. Give it a `testTag` if it is a control a UI test would need to find.