# `data/` — the network boundary and everything behind it

The only layer that knows AniList exists. `ui/` never sees a JSON key or an HTTP
status; `domain/` never sees either.

## Where things go

| Path | Holds | May import Android? |
|---|---|---|
| `data/model/` | What AniList sends and the derivations the UI reads. Moshi-annotated wire types, plus pure `val MediaNode.x` extensions. | No |
| `data/network/` | The Retrofit service, the GraphQL documents, the error mapping, the interceptors. | Yes |
| `data/repository/` | The interface screens depend on and the implementation. Caching and request coalescing live here. | Yes |
| `data/update/` | The in-app updater: manifest, download, formatting. A different product surface that happens to also be network. | Yes |
| `data/changelog/` | Reads the bundled `changelog.json` for Settings > What's new. | Yes |

## Rules

- **A field is asked for only if it is rendered.** AniList resolves `MediaListCollection`
  eagerly: on a 476-entry account every field asked for is paid for hundreds of times.
  `getMediaDetailQuery` exists purely to keep the heavy fields off the list query.
- **Never pass an argument AniList's schema does not define.** GraphQL validates the
  whole document before resolving any of it and AniList answers **400 with `data: null`**,
  so one bad argument costs the entire response rather than the one field. This is the
  bug that emptied the title page's synopsis, studios, cast, staff, tags and rankings
  at once — see `MediaDetailQueryArgumentTest`.
- **Ids are not unique across namespaces.** `anime/1` and `manga/1` are different
  entries. Anything keyed on an id must be keyed on the *type and* the id
  (`MediaCoverKey`), and any `Media(id:)` query takes the type as a variable.
- **Never ask for a relation that makes the server resolve a second entity.**
  `studios(isMain: true)` inside `relations` does exactly that, and on a real large
  account it does not slow the response down, it **fails** with an HTTP 500.
- **Derivations live on the model, not in the UI.** `MediaNodeDisplay.kt` is one file of
  pure extensions; a screen reads `media.studioCredits` rather than re-deriving it.

## Adding a field

1. Find which queries actually need it — often not all of them.
2. Add it to the model. Defaults matter: Moshi leaves an unasked-for field null.
3. If a derivation depends on it, put the derivation in `data/model/` with a note on
   what was broken by it being absent.
4. Add a test that fails without the field. A field with no test is a field someone
   will delete believing it is unused.