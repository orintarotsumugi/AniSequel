# AniSequel 2.4 — redesign and engineering plan

## Product direction

This release keeps AniSequel's identity and existing Compose architecture. Reference clients inform interaction patterns; their layouts and branding are not copied.

| Area | Direction |
| --- | --- |
| Anime and manga detail | Hybrid: AniSequel visual identity with the strongest discovery and information patterns from other clients |
| Statistics | Community-style dashboard using real AniList aggregates and locally derived list insights |
| Profile | Hybrid header, actions, navigation and content |
| Anime and manga library | Expressive poster cards as the default presentation |
| Motion | Cinematic page entrance and coordinated reverse exit |

## Release guardrails

- Work on `feature/anisequel-2.4-redesign`; never commit directly to `main`.
- Preserve the application ID, signing identity, AniList account flows, persisted preferences and working features.
- Use existing Material 3 Expressive dependencies and shared motion tokens. Do not add a dependency without checking repository constraints.
- Do not represent unavailable API fields as zero or fabricate community statistics.
- Keep screen readers, font scaling, touch targets, loading, empty and error states in scope.
- Add regression tests for every reproduced bug.
- Do not bump the release version until the implementation and release notes are ready.

## Audit baseline (2026-11-18)

### Detail navigation and motion

The detail surface remains a full-screen Compose `Dialog` in `SequelDetailSheet.kt`, preserving the existing host ownership and Back lifecycle. The reading page stays composed while **My Entry** is shown as a separate in-window overlay. The editor opens at 60% of the available height and its handle expands it to full height; dragging down returns it to the 60% resting height. The floating navigation capsule travels clear of the overlay, and the Info page's list state remains alive underneath it.

The sheet's entrance, settling and dismissal use `ExpressiveMotion` tokens, so the app-wide motion style (including `INSTANT`) controls the transition rather than local hard-coded durations. The existing detail entrance/exit still animates alpha, scale and vertical offset; it is not a shared-element transition.

The poster source still lives in the host screen while the detail page is in a separate dialog window. A real poster-to-page transition needs a coordinated host/dialog or navigation-overlay design. Do not label a scale animation as shared-element motion. Continue reproducing the reported blank frame on Android Back and verify close button, system Back and gesture Back share one lifecycle.

### Detail content and Nova

AniList's standard media model does not expose a full episode catalogue with individual episode titles, synopses and per-episode runtimes. Its `nextAiringEpisode` object supplies the next episode number and airing timestamp. The UI must present those two fields accurately and avoid inventing per-episode metadata; richer episode data would require a separately verified source.

The detail shell selects Classic or Nova content. Nova's hero, information page and information cards are separate components. The page's section order already includes summary metrics, progress, friends, synopsis, details, categories, trailer, external links, credits, cast, relations, staff and extras. Preserve the shared sections while improving the reading experience.

Audit the wavy progress indicator, split/action buttons and partially expanded information sheet on actual call paths. Record a reproducible failure and add a focused test before changing each component.

### Statistics and profile

The existing profile statistics tab combines AniList's aggregate `User.stats` with locally derived insights from the user's media list. It already separates Overview, Genres and Tags and includes score, episode-count, status, format, country and year distributions. The community dashboard should extend and reorganize available information, not duplicate the existing charts or imply that AniList exposes aggregates it does not.

The profile already has distinct header, home, activity, library, social, messages and statistics components. Build the hybrid redesign by composing these responsibilities, retaining scroll state and existing profile actions.

### Markdown

Profile descriptions and messages use the shared AniList Markdown parser/view. Audit parser coverage, inline rendering, block rendering, malformed input and parity between profile and message surfaces. Add regression cases for any real mismatch; do not replace the renderer wholesale without evidence.

### Library cards

The Nova library already has a dedicated card/grid renderer. Make expressive poster cards the default presentation while preserving existing filtering, sorting, progress, scores, accessibility descriptions and list/grid state.

## Delivery sequence

1. Reproduce and fix detail dismissal/Back lifecycle; add regression coverage.
2. Design and implement coordinated cinematic poster-to-detail and reverse transitions.
3. Audit and repair Nova progress, action controls, partial-height sheet and episode metadata.
4. Extend community statistics using verified AniList fields and explicitly labelled locally derived insights.
5. Refine profile header, actions, tabs, overview and Markdown parity.
6. Make expressive poster cards the default library presentation.
7. Run unit tests and CI, inspect the PR diff, update architecture and README, and only then set the 2.4.0 version baseline and release notes.

## Acceptance criteria

- No blank frame or lost host screen when closing a title through the close button, Android Back or predictive/gesture Back.
- The poster transition is coordinated in both directions and does not claim shared-element behaviour unless the source and destination are actually linked.
- Detail content remains scrollable with the dock visible; the last content item can scroll fully clear of it.
- My Entry opens at 60% height, expands to full height from its drag handle, and preserves the Info page and editor draft underneath/inside the overlay.
- My Entry motion follows the global Motion setting, including the instant/no-motion style.
- Episode fields are sourced from the correct AniList data and omitted when unavailable.
- Community charts distinguish AniList aggregates from locally derived list statistics.
- Profile and message Markdown render consistently and safely.
- Poster cards retain functional list actions, filters, scores and progress.
- Tests and required GitHub checks pass before merge.
