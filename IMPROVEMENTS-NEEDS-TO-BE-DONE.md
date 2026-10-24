# Improvements Needs To Be Done

> **What this is.** A feature-by-feature comparison of **AniSequel** against
> **[AniSync](https://github.com/Marco-9456/AniSync)**, and a prioritised,
> implementable list of what AniSequel can take from it.
>
> **How to read it.** Only features AniSequel *already has* are compared. Where
> AniSync has a whole domain AniSequel does not (forums, a social feed, an
> AniList-wide search/discover surface, background notifications), it is listed
> under [§8 Out of scope](#8-out-of-scope-deliberately) rather than smuggled in
> as an improvement.
>
> Every recommendation is written to survive the repo's own rules
> ([AGENTS.md](AGENTS.md)): each one names what it changes, why AniSync's version
> is better, the first step, and whether it collides with a stated non-goal.

---

## Table of contents

- [0. Scope, sources and method](#0-scope-sources-and-method)
  - [Priority legend](#priority-legend)
- [1. Executive summary](#1-executive-summary)
  - [The ten highest-value changes](#the-ten-highest-value-changes)
  - [Full priority table](#full-priority-table)
- [2. Feature-by-feature breakdown](#2-feature-by-feature-breakdown)
  - [2.1 Dashboard and the sequel pipeline](#21-dashboard-and-the-sequel-pipeline)
  - [2.2 Library and list tracking](#22-library-and-list-tracking)
  - [2.3 The title page](#23-the-title-page)
  - [2.4 Character, staff and studio pages](#24-character-staff-and-studio-pages)
  - [2.5 The profile screen](#25-the-profile-screen)
  - [2.6 The tier list and sharing](#26-the-tier-list-and-sharing)
  - [2.7 Home-screen widgets](#27-home-screen-widgets)
  - [2.8 Updates and release notes](#28-updates-and-release-notes)
  - [2.9 Settings, theme and motion](#29-settings-theme-and-motion)
  - [2.10 Auth, session and demo mode](#210-auth-session-and-demo-mode)
  - [2.11 Networking and the request budget](#211-networking-and-the-request-budget)
  - [2.12 Caching and freshness](#212-caching-and-freshness)
  - [2.13 Security and privacy](#213-security-and-privacy)
  - [2.14 Accessibility](#214-accessibility)
  - [2.15 Testing, CI and distribution](#215-testing-ci-and-distribution)
  - [2.16 Localisation and typography](#216-localisation-and-typography)
- [3. Phased roadmap](#3-phased-roadmap)
- [4. Quick wins (a day or less each)](#4-quick-wins-a-day-or-less-each)
- [5. Do not change](#5-do-not-change)
- [6. Appendices](#6-appendices)
  - [A. AniSync constants worth copying](#a-anisync-constants-worth-copying)
  - [B. Mechanism glossary](#b-mechanism-glossary)
  - [C. File map: AniSequel → AniSync counterpart](#c-file-map-anisequel--anisync-counterpart)

---

## 0. Scope, sources and method

**Compared:**

| | AniSequel (this repo) | AniSync (reference) |
| --- | --- | --- |
| Version read | 1.3.8 / code 287 | 3.2.1 / code 21 |
| minSdk | 31 | 26 |
| compileSdk | 36.1 | 37 |
| DI | manual (hand-constructed in `Application`/routes) | Hilt |
| Network | Retrofit + OkHttp + Moshi, GraphQL as strings | Apollo + codegen, normalized cache |
| Persistence | DataStore prefs + JSON `SnapshotStore` | Room (v28) + Apollo normalized cache |
| Offline stance | **deliberate**: no offline-first DB (`NOTES § Deliberate non-goals`) | local-first: Room is the source of truth |
| Auth storage | plain DataStore | EncryptedSharedPreferences (AES-256-SIV/GCM) |
| Widgets | 4 `RemoteViews` tiles | 5 `RemoteViews` tiles + notifications |
| Tests | 88 JVM unit tests, no `androidTest` | JVM + instrumented + screenshot + a11y |
| Distribution | signed universal APK + in-app updater | APK splits per ABI, F-Droid, preview flavour |

**Method.** I read both READMEs in full, walked AniSequel's feature files
directly, and studied AniSync's implementations of the same surfaces
(`library/`, `details/`, `profile/`, `activity/`, `statistics/`, `widget/`,
`worker/`, `data/network/`, `data/security/`, `data/update/`, `ui/theme/`) with
their tests. Every claim about AniSync below is grounded in a file that exists
there; every recommendation names where it lands in AniSequel.

**Two constraints are load-bearing and are respected throughout:**

1. **AniSequel will not gain an offline-first database.** Its README makes the
   case (§ *Deliberate non-goals*): a stale list here fails *confidently*, and
   that is worse than no answer. So nothing below proposes Room as a cache.
   Where AniSync's local-first design matters, only the *mechanisms it enables*
   (optimistic writes, an empty-response guard, per-status TTLs) are borrowed —
   not the architecture.
2. **Vectors only, springs only, no Hilt, no WorkManager poller.** Where a
   borrowed mechanism would need one of those, the recommendation says so and
   offers the dependency-free version.

### Priority legend

| Tag | Meaning |
| --- | --- |
| **P0** | Correctness, security or data-loss. Should jump the queue, like `TO-DO §0`. |
| **P1** | High value, contained risk. Next up. |
| **P2** | Meaningful polish or platform parity. |
| **P3** | Nice to have; do when the area is already being touched. |

| Effort | Meaning |
| --- | --- |
| **S** | ≤ 1 day |
| **M** | 2–5 days |
| **L** | 1–3 weeks |
| **XL** | > 3 weeks / architectural |

---

## 1. Executive summary

AniSequel is, in several respects, the more *disciplined* of the two codebases:
its layer isolation, its "comment records the failure" culture, its test-per-bug
habit, its updater's resume-and-verify pipeline, and its tier-list document model
are all ahead of AniSync. What AniSync has that AniSequel lacks is **breadth of
mechanism**: a request-budget governor, optimistic writes, encrypted storage, a
much wider set of read-only AniList surfaces on the pages AniSequel already has,
an accessibility contract, and a test/CI story that includes screenshots,
instrumentation and ABI-split distribution.

Nothing that follows asks AniSequel to become AniSync. It asks it to take the
dozen or so ideas AniSync has proven, on device, to be worth the code.

### The ten highest-value changes

1. **Encrypt the OAuth token** (`data/repository/AuthRepositoryImpl.kt`), and
   offer an optional app lock. A bearer token that can drive the user's entire
   AniList account is currently sitting in plaintext DataStore. *(P0, M)*
2. **Add a request-budget governor** below the existing coalescer, with tiers
   and a persisted block, so a 429 becomes a wait instead of a failed screen.
   *(P1, L)*
3. **Turn the Library into a place you can write from**: bulk status/score, a
   swipe/stepper progress control, custom lists, and a list badge on every
   poster so the grid answers "what haven't I started". *(P1, L)*
4. **Make detail caching status-aware** — 3h for `RELEASING`, 6h for
   `NOT_YET_RELEASED`, 7d for finished — instead of a flat 24h, and treat an
   elapsed `nextAiringEpisode` as *provably* stale. *(P1, S)*
5. **Guard against the throttled-empty fetch**: never let an empty list response
   overwrite a populated library snapshot (AniSync's `refreshLibrary` early
   return). *(P1, S)*
6. **Widen the title page** with the read-only surfaces AniSync puts above the
   fold: where-to-watch, recommendations, reviews, and score/status distribution
   — all data AniSequel already has or can ask for in the request it already
   makes. *(P1–P2, M each)*
7. **Polish the write path**: format-aware score entry, an inline "finish
   prompt" at the last episode, a dirty-state guard on dismiss, and a mutation
   coalescer so a burst of `+` taps is one request. *(P1, M)*
8. **Systematise accessibility**: heading semantics, selected-state semantics
   *plus* a non-colour cue, merged descriptions on composite rows, and 48dp
   targets everywhere. *(P2, M)*
9. **Add screenshot and instrumented tests** for the screens whose value is
   visual (profile tabs, library grid, settings) and for the widgets. This is
   what turns `TO-DO §5.1/§5.2/§2.2` from opinions into measurements.
   *(P2, L)*
10. **Ship ABI splits and a preview flavour**, with F-Droid-safe build settings,
    so the APK is smaller and a tester build can sit beside the stable one.
    *(P2, M)*

### Full priority table

| # | Area | Change | P | Effort | Risk |
| --- | --- | --- | --- | --- | --- |
| 1 | Security | Encrypt token; keystore-invalidation recovery | P0 | M | Low |
| 2 | Security | Optional biometric/PIN app lock + recents hiding | P0 | M | Low |
| 3 | Caching | Empty-response guard on list fetch | P1 | S | Very low |
| 4 | Caching | Status-keyed detail TTL + countdown-override | P1 | S | Very low |
| 5 | Caching | Per-gesture vs per-auto fetch cooldowns | P1 | S | Low |
| 6 | Network | `RateLimitGate` (tiers, reserves, persisted block) | P1 | L | Medium |
| 7 | Network | Retry policy with typed errors; never retry mutations | P1 | M | Medium |
| 8 | Network | Cap error-body reads (64 KB) | P1 | S | Low |
| 9 | Library | Bulk edit (status/score/private) one column at a time | P1 | M | Low |
| 10 | Library | Custom lists (read + manage) | P1 | L | Medium |
| 11 | Library | List-status badge on every poster | P1 | M | Low |
| 12 | Library | Swipe/stepper progress + finish prompt | P1 | M | Low |
| 13 | Library | Mutation coalescer for progress/score taps | P1 | S | Very low |
| 14 | Library | Grid column count + per-account view prefs | P2 | S | Very low |
| 15 | Library | Notes field in the entry editor | P2 | S | Low |
| 16 | Entry | Format-aware score control (fixed `TO-DO §4.3`; see that file's Closed section) | P1 | M | Low |
| 17 | Entry | Dirty-state guard on sheet dismiss | P1 | S | Low |
| 18 | Entry | `hiddenFromStatusLists` + priority | P2 | S | Low |
| 19 | Detail | Where-to-watch above the fold | P1 | S | Low |
| 20 | Detail | Recommendations row | P1 | M | Low |
| 21 | Detail | Reviews (list + detail sheet) | P2 | M | Low |
| 22 | Detail | Score/status distribution from AniList stats | P2 | M | Low |
| 23 | Detail | Connection-total `≥500` cap handling | P2 | S | Very low |
| 24 | Detail | Rich-text parse cache + height estimate | P1 | S | Low |
| 25 | Detail | Clamp malicious image dimensions | P1 | S | Very low |
| 26 | Detail | `relationType(version: 2)` | P2 | S | Very low |
| 27 | Detail | Zoomable full-screen image viewer with download | P2 | M | Low |
| 28 | Person | Tabbed character/staff pages with filters + counts | P2 | L | Medium |
| 29 | Studio | Cover-mosaic mark + derived studio facts | P2 | M | Low |
| 30 | Profile | Month/week heatmap toggle + day sheet | P2 | M | Low |
| 31 | Profile | Blocked-user filtering in activity | P2 | S | Low |
| 32 | Profile | Reply threading from leading @mention | P2 | S | Low |
| 33 | Profile | Content-limit pre-validation + rich-text composer | P2 | M | Low |
| 34 | Tier | PNG share + gallery save shipped in 1.5.6; WebP remains | P3 | S | Low |
| 35 | Widgets | Image memory budget + 120ms fast-cover window | P1 | S | Low |
| 36 | Widgets | Size ladder / `targetCellWidth` correctness | P2 | S | Low |
| 37 | Widgets | `PendingIntent` identity via data URIs | P2 | S | Very low |
| 38 | Widgets | Widget refresh worker (short, foreground-friendly) | P2 | M | Medium |
| 39 | Updates | Periodic update check + notification channel | P2 | M | Low |
| 40 | Updates | Move APK to external cache, atomic `.tmp` rename | P3 | S | Low |
| 41 | Build | ABI splits with derived version codes | P2 | M | Low |
| 42 | Build | `preview` flavour beside `stable` | P2 | S | Low |
| 43 | Build | `dependenciesInfo { includeInApk = false }` | P3 | S | Low |
| 44 | Tests | Compose screenshot tests (profile, library, settings) | P2 | L | Low |
| 45 | Tests | Instrumented widget + accessibility tests | P2 | L | Low |
| 46 | Tests | Inject `Clock` into rate-limit code for testability | P1 | S | Very low |
| 47 | A11y | Headings, selected semantics, merged descriptions | P2 | M | Low |
| 48 | Theme | Font scale + type-scale settings | P2 | M | Low |
| 49 | i18n | `values-*` locales (Weblate workflow) | P2 | L | Low |
| 50 | Settings | Storage & cache inventory screen | P2 | M | Low |
| 51 | Onboarding | First-run onboarding (sign-in, permissions, personalise) | P2 | M | Low |
| 52 | Deep links | Verified `anilist.co` app links + in-app routing | P3 | M | Low |
| 53 | Motion | `MotionStyle` accessibility + reduced-motion respect | P3 | S | Low |

---

## 2. Feature-by-feature breakdown

Each section is: **what AniSequel has today → what AniSync does → what to
change**, then a **rule check** against `AGENTS.md`.

---

### 2.1 Dashboard and the sequel pipeline

#### Today in AniSequel

- `DashboardScreen.kt`, `DashboardViewModel.kt` (1,139 lines),
  `FindMissedSequelsUseCase.kt`, `FindArrivingEntriesUseCase.kt`,
  `BuildListInsightsUseCase.kt`.
- Two-phase pipeline: `discover()` builds candidates once, `applyFilters()` runs
  O(N) per keystroke, `splitHidden()` separates the hidden set; the hidden set is
  undoable.
- `ListFreshnessWatch` + `LifecycleResumeEffect` re-check on `ON_RESUME`, with a
  5-minute failure floor, and the interval is read per-lookup so a Settings
  change takes effect immediately.
- `RateLimitNotice` surfaces throttling; `RequestCoalescer` collapses identical
  in-flight queries.

This is genuinely good, and AniSync has **no equivalent**: it has no
franchise-graph traversal at all. The dashboard is AniSequel's moat. The
improvements below are about the *pipe feeding it* and the *bytes it costs*.

#### What AniSync does differently

- **Explicit request tiers** — `data/network/RateLimitGate.kt` +
  `RequestPriority.kt`: `Interactive` / `Prefetch` / `Background`, with reserved
  budget fractions (prefetch 0.2, background 0.4), an ambient
  `withRequestPriority { }` so worker code self-declares and no repository
  signature grows a parameter.
- **A persisted rate-limit block** — `RateLimitPersistence.kt` stores
  `blocked_until` as wall-clock in prefs so a process kill mid-block cannot
  reset it, and clamps a stale value to 120s.
- **Per-fetch cooldowns** — `data/fetcher/FetchCooldown.kt`: 5s floor for
  gesture-driven refresh, 15s for automatic. A pull-to-refresh is never throttled
  the way a background auto-refresh is.
- **Scan/pagination progress** — verified in its library and details paging: a
  per-page `hasNextPage` and a `knownIds` set, so appends dedupe and a "load
  more" footer only exists when there is more.

#### Improvements

1. **Report scan progress (resolve `TO-DO §4.1`) — P2, M.** AniSync never shows
   a bare spinner where a count is knowable. AniSequel's `discover()` already
   knows `collection.lists.sumOf { it.entries.size }` *before* it walks relations.
   Emit `Phase(entries, edgesWalked)` from `discover()` (a domain change, flagged
   in the TO-DO as the blocker) and render "Walking 412 entries · 1,204
   relations" in `DashboardStateViews`. Test: a fake collection of N entries
   produces N+ callbacks, last one complete.
2. **Tier the dashboard's own reads — P1, L.** Adopt AniSync's `RateLimitGate`
   shape *without* its dependency: a single `Mutex`, an `AtomicInteger` in
   flight, and a rolling 60s window driven by `x-ratelimit-remaining`. The
   repository's list fetch is `Interactive`; the snapshot warm-up and widget
   refresh are `Prefetch`. On a 429, write `blocked_until` to a DataStore key so
   a process death does not reset the wait. Keep `RateLimitInterceptor` as the
   detector and let the gate be the *scheduler*. See [§2.11](#211-networking-and-the-request-budget).
3. **Distinguish gesture from auto refresh — P1, S.** Add AniSync's
   `FetchCooldown` idea to `ListFreshnessWatch`: a pull-to-refresh may run 5s
   after the last one; the automatic resume check must wait 15s. This is a
   three-line change to the existing watch and removes a class of "I refreshed
   twice and got nothing" reports.
4. **Per-section error states — P2, M.** AniSync's discover proves the pattern:
   one failed rail renders an inline error card; only an all-failed state shows
   the offline screen. AniSequel's dashboard is one state machine, so one failed
   arriving-section blanks the whole page. Split `DashboardUiState.Success` into
   per-block health flags (`arriving: BlockHealth`) so the missed list survives a
   failed arriving fetch.
5. **Cause-aware empty states — P2, S.** AniSync distinguishes "offline", "you
   hid everything" (action: reopen the reorder sheet) and "no results" (action:
   clear filters *only when filters are on*). AniSequel's `EmptyStateView`
   should take the *cause* and draw the one tap that undoes it. A hidden-everything
   dashboard currently offers no path back.

**Rule check.** The gate is pure Kotlin + coroutines; it fits
`domain`/`data`. No new dependencies. It does **not** introduce a background
poller, so the notifications non-goal is untouched.

---

### 2.2 Library and list tracking

#### Today in AniSequel

- `LibraryScreen.kt` is explicitly **browse-only** (`canWriteToAniList = false`;
  the KDoc says "no title is written from here").
- `ProfileLibraryTab.kt` (1,086 lines): anime/manga/all, status filter
  (including **Favourites**), `LibrarySort { LIST_ORDER, UPDATED_AT, TITLE,
  SCORE }` with a direction, a search field shown only on a long list.
- `LibraryPreferences.kt` remembers type/filter/sort/sort-direction, resolved
  once per process via `RememberedLibraryView` — a genuinely good fix that
  AniSync does not have in this form (AniSync persists per-account last-tab
  keys but resolves them at the route).
- Editing happens only from the dashboard rows and the title page's editor.
- `QuickAddPreferences.kt` gives one-tap Planning/Watching.

#### What AniSync does differently

AniSync's Library is a **full tracking surface**, backed by Room as the source
of truth (`data/LibraryRepositoryImpl.kt`, `presentation/library/`):

- **Optimistic writes then push.** `updateEntry` reads the old row, auto-fills
  `startedAt`/`completedAt` on status transitions, writes Room, *then* sends
  `SaveMediaListEntry`.
- **Bulk edit, one column at a time.**
  `LibraryDao.updateStatusForIds/updateScoreForIds/updatePriorityForIds/updatePrivateForIds`
  exist so a bulk status change cannot clobber a concurrent `+1` progress on a
  selected row. Partial success returns the count done, not a failure.
- **A smart merge that cannot wipe a library.** `smartMergeByType` is a
  `@Transaction`: API rows win, stale duplicate `mediaId` rows are dropped, and
  a locally-created row younger than **5 minutes** is preserved. Crucially,
  `refreshLibrary` early-returns when the response is empty but Room already has
  rows — a 429 must never look like "the user deleted everything".
- **A mutation coalescer** — `presentation/util/MutationCoalescer.kt`
  (600ms debounce, per key): rapid `+`/`−` taps become one write of the settled
  value, and a like→unlike→like sequence sends **zero** requests.
- **Format-aware score input.** `POINT_5` → 5 stars, `POINT_3` → 3 faces,
  otherwise a slider with `scoreFormat.sliderSteps`; advanced-score categories
  average to the overall.
- **An inline finish prompt**: reaching the last episode offers one tap to set
  `COMPLETED` + `completedAt`.
- **Priority is only sent when it changed**, and only ever 0/1/2, so a foreign
  3..255 survives round-trips.
- **A list-status badge projection** (`observeListStatuses`) so every poster on
  every browse surface shows its status from two columns, not a full row.
- **Custom lists** as first-class (`createCustomList`, membership per entry).
- **Grid columns are a setting** (min 2, default 3, max 8) with `coerceIn` on
  read and write.

#### Improvements

1. **A mutation coalescer for progress and score — P1, S.** This is the single
   cheapest, highest-felt change in this document. Port
   `MutationCoalescer<K,V>` almost verbatim: a `ConcurrentHashMap<Int, Job>`, a
   600ms cancel-and-reschedule, a `seed`/`commit` baseline, and a
   suppress-if-equal-to-committed rule. Wire it into the dashboard's quick-add
   and any new progress steppers. Test: three `+1` taps inside 600ms produce one
   mutation with the final value.
2. **Bulk edit on the Library grid — P1, M.** Long-press enters selection
   (`LibrarySelectionUi` in AniSync); the toolbar offers Status / Score / Private
   / Delete. The rule to copy literally: **write one column at a time**, never
   re-save the whole row, so a bulk action cannot revert someone's progress.
   Return "done 7, failed 1" rather than all-or-nothing.
3. **A list-status badge on every cover — P1, M.** AniSequel already has the
   viewer's list in memory (the dashboard fetched it). A `CompositionLocal`-style
   map of `mediaId → status` should be read by `SequelPoster`, studio cards, and
   every relation row, exactly as AniSync's `LocalLibraryStatuses` does. Today a
   poster on a studio page or a relation row says nothing about whether it is
   already yours. No new request.
4. **Custom lists — P1, L.** AniSequel asks for `customLists` nowhere. AniList
   returns membership as a list of names; read it on the list query, show it as
   extra chips in the Library filter, and offer a manage sheet. This is the one
   place AniSequel's "your list" is materially thinner than AniSync's.
5. **Notes — P2, S.** `SaveMediaListEntry` already takes `notes`. Add a
   multiline field to `DetailEditTab` (`minLines = 3, maxLines = 6`), and send
   the empty string to clear (AniSync's comment: `Optional.absent()` would leave
   the old note instead of clearing it — the equivalent here is sending `""`).
6. **Grid column count as a remembered preference — P2, S.** Extend
   `LibraryPreferences` with `gridColumns` (clamp 2..8, default 3) and a view
   sheet. It is one key, one slider, and it is the difference between a usable
   tablet grid and a phone grid on a 12" screen.
7. **A library write path that is not the dashboard — P2, M.** The KDoc's
   reasoning (a list is written from the rows that already carry the controls)
   is sound, but it leaves the Library grid's long-press inert. Bulk edit (§2)
   is the honest version: selection is not per-entry editing, it is a different
   task, and it belongs where the many are visible.
8. **"Never blank on empty response" guard — P1, S.** Even without Room, apply
   the rule to `AniListRepositoryImpl`: if a `MediaListCollection` response
   parses to zero entries *and* a snapshot for that key exists, refuse to
   overwrite and surface a retry. This is a correctness guard, and `TO-DO` has
   no entry for it today.

**Rule check.** Optimistic writes and a coalescer do not need Room, but a true
offline *queue* does — so do not propose one. Bulk edit and custom lists are
plain GraphQL mutations. The coalescer is pure coroutines. Do **not** add Hilt
for `LibraryViewModel`; AniSequel constructs its ViewModels in the navigation
layer.

---

### 2.3 The title page

#### Today in AniSequel

`SequelDetailSheet.kt` + `ui/components/detail/**`:

- One `LazyColumn` under a parallax hero; the scroll drives the motion in the
  draw phase; a docked bar carries the title and a progress wash.
- Synopsis through the app's own AniList-markdown renderer (spoilers, centred
  lines, link cover-cards, images).
- Production facts, genres, weighted tags, rankings, cast with voice actors,
  crew, the franchise relation row (with the subject's own medium and the
  adaptation/source edge, sorted), and an Extras card whose only action is
  **Watch the trailer**.
- `GET_MEDIA_DETAIL` already asks for `characters`, `staff`, `relations`,
  `tags`, `rankings`, `trailer`, `studios { edges }`.
- The editor is the sheet's second destination.

#### What AniSync does differently (`presentation/details/**`)

- **Where-to-watch is above the fold.** `TrackingCard.kt` renders
  `ExternalLinkType.STREAMING` chips before the synopsis, with a comment
  recording that they "used to be the last block of the Overview tab, burying
  'where do I watch this'". Links are split into STREAMING vs everything else,
  and the section title changes when streaming is omitted.
- **A labelled trailer chip, not a play circle** — an unlabelled circle over
  cover art "reads as watch the episode".
- **Banner fallback** = banner → trailer thumbnail, with a hand-written
  `ContentScale` for the 4:3-letterboxed 16:9 YouTube thumbnail.
- **Recommendations** (`RecommendMediaSheet`, `recommendations(sort:
  [RATING_DESC], perPage: 25)`).
- **Reviews** — a 5-item snippet inline, a paginated list sheet, and a detail
  sheet; traffic-light score thresholds (≥75 green, ≥50 amber, else red-orange).
- **Score/status distribution charts** (`MediaStatsSection`) drawn from
  `GetMediaStats`, with the whole `Canvas` wrapped in one
  `clearAndSetSemantics { contentDescription = ... }`.
- **`relationType(version: 2)`** — version 1 has no `SOURCE`, so light-novel →
  anime collapses into "adaptation".
- **`ANILIST_CONNECTION_TOTAL_CAP = 500`**: a nested-connection total sitting on
  500 means "at least this many" and is displayed as such, not as an exact count.
- **Image-dimension clamping** (`MAX_RICH_IMAGE_WIDTH_DP = 3000`,
  `MAX_RICH_IMAGE_HEIGHT_DP = 800`) because a malformed `img2147483647(url)`
  overflows Compose constraints and crashes the layout pass.
- **A process-wide rich-text parse cache** (`LruCache(200)`), because a body
  re-entering a lazy list re-parsed and replayed an `animateContentSize` wiggle
  on every scroll-back.
- **`ClampedBody`** measures its child and only shows "Read more" when there is
  real overflow.

#### Improvements

1. **Clamp image dimensions in the markdown renderer — P1, S.** Do this first.
   AniSequel's parser already handles `imgN(url)`; a hostile or malformed N is a
   guaranteed layout crash on a public bio or synopsis. Add the two caps and a
   test that a 2,147,483,647-wide image renders without throwing.
   *File:* `ui/components/markdown/AniListMarkdownBlocks.kt`.
2. **Cache parsed markdown — P1, S.** AniSequel re-parses on every composition of
   a scroll-recycled item. Add an `LruCache<String, ParsedMarkdown>(200)` keyed
   by the raw source, seeded before the async pass. This removes a known class of
   jank in the Activity tab and the title page at once.
   *File:* `ui/components/markdown/AniListMarkdownParser.kt`.
3. **Where-to-watch chips — P1, S.** `Media.externalLinks` is one more field on
   the detail query AniSequel already makes. Filter `type == STREAMING` and draw
   them in `InfoCards`/`InfoHero` above the synopsis. This is the highest
   user-value-per-line change on the page.
4. **Recommendations row — P1, M.** Add
   `recommendations(sort: [RATING_DESC], perPage: 12)` to `GET_MEDIA_DETAIL` (it
   already carries the heavy cast/staff block, so this is incremental), and draw
   a horizontal rail of posters that open the same detail subject. It turns a
   dead-end page into the start of the next thing.
5. **Reviews: snippet then sheet — P2, M.** Add `reviews(sort: [RATING_DESC],
   perPage: 5)` for a snippet and `perPage: 20` for a paged sheet. Reuse the
   existing markdown renderer for the body, and add the traffic-light score
   colour. AniSequel has no review surface at all today, and the page is the
   natural home.
6. **Score/status distribution — P2, M.** AniSequel computes a score histogram
   from the *user's* list. AniSync's `GetMediaStats` gives the *global*
   distribution and the airing progression. Add it as a card; wrap the chart in
   one `clearAndSetSemantics` description.
7. **`relationType(version: 2)` — P2, S.** One-line query change; fixes the
   light-novel adaptation label. Pin it with a query test the way
   `MediaDetailQueryTest` pins the rest.
8. **Handle the 500-cap on connection totals — P2, S.** Anywhere AniSequel shows
   a cast/staff total, a value of exactly 500 is a floor. Render "500+" and
   comment why.
9. **A zoomable full-screen viewer — P2, M.** AniSequel has
   `FullScreenCoverViewer`; AniSync's `ImageViewerDialog` adds a `HorizontalPager`,
   pinch 1..5×, double-tap 1↔3× with a 300ms tween, pan clamped to
   `size*(scale-1)/2`, `userScrollEnabled = !isZoomed`, and a MediaStore download.
   The `userScrollEnabled` gate is the part most implementations miss.
10. **`ClampedBody` overflow detection — P2, S.** Replace the length-threshold
    "Read more" with a measured clamp so a two-line synopsis stops growing a
    button. This is a correctness-of-affordance fix, not a style one.

**Rule check.** All of it fits the existing string-GraphQL + Moshi stack. No new
dependency except, optionally, `media3` for the video viewer (AniSync uses
ExoPlayer for embeds) — but AniSequel's Extras card opens the trailer externally,
which is a legitimate choice; do not add a player unless the page is already
being reworked.

---

### 2.4 Character, staff and studio pages

#### Today in AniSequel

- `PersonProfileSheet.kt` + `PersonStaffCredits.kt`, served by
  `PersonProfileStore` (bounded, synchronous re-open), one composable for both
  character and staff.
- `StudioScreen.kt` as a **route** (`studio/{id}?name=`), with
  `StudioYearShelf` (newest year first, a `TBA` shelf at the top, duplicate-id
  drop) and its tests.
- Voice actors asked twice (`JAPANESE` + English alias), deduped per language.
- The person sheet is re-keyed on the person so it does not inherit scroll
  position.

This area is already strong — the "person is fetched once" store and the studio
route are ahead of a naive implementation. The gaps are *content depth*, not
structure.

#### What AniSync does differently

- **Tabs with counts** (`PersonTabs`, connected M3 toggle group): Character →
  Appearances / Voice Actors (the VA count only once all pages are in, because
  the cast is incomplete until then); Staff → Characters / Credits.
- **Per-appearance voice cast on the row** (`AppearanceRow`), with a comment
  that flattening every actor into a separate tab meant "you couldn't tell who
  voiced in which show".
- **One primary title + an "other titles (N)" count** instead of unfolding a
  long-running role into dozens of rows.
- **A 2-column facts tile grid** (`PersonFactsCard`) — halves the height versus
  an 8-row label/value list that pushed content below the fold.
- **`StudioCoverMark`**: a studio has no logo, so a 2×2 mosaic of its best-known
  covers, with 1dp seams in the background colour; falls back to one cover, then
  a Business icon.
- **Derived studio facts** from already-fetched fields: on-your-list count,
  average score %, lead-studio %, year span.
- **Client-side studio filtering**: main-studio-only, on-my-list, format; sorts
  by year/score/title **except popularity**, because popularity is the API's
  paging order and re-sorting it would put a later page above the viewport.

#### Improvements

1. **Facts as a 2-column tile grid — P2, S.** `PersonProfileSheet`'s facts are
   currently a label/value list. AniSync's tile grid is a direct height win and
   pushes the appearances row above the fold.
2. **Tab the person sheet with counts — P2, L.** Appearances vs Voice Actors for
   a character; Characters vs Credits for staff. The count-on-tab detail matters:
   show the VA count only after the last appearances page has arrived.
3. **Row-level voice cast — P2, M.** Put the voice actor(s) for *that title* on
   each appearance row rather than in a separate tab. It is the thing a reader
   actually wants ("who voiced them in this one").
4. **"Other titles (N)" collapsing — P2, S.** Stop unfolding long roles.
5. **Studio mosaic + derived facts — P2, M.** Add `StudioCoverMark` and a facts
   strip (years active, titles, average score, lead-studio share) to
   `StudioScreen`. AniSequel already has every field on the page; it is currently
   thrown away.
6. **Studio client-side filters — P2, M.** Main-studio-only, format, and
   on-my-list (using the badge map from §2.2). Keep popularity as the paging
   order and say so in a comment, or the next person re-sorts it and breaks
   paging.

**Rule check.** All Compose + existing queries. The studio mosaic is Coil
composition, not a new drawable, so the vectors-only rule is untouched.

---

### 2.5 The profile screen

#### Today in AniSequel

- Four tabs — Home (identity, bio, favourites), Activity (heatmap + paginated
  history), Stats, Social — with scroll positions preserved across tabs.
- Two-publish load: the overview paints before the list lands; `isLibraryLoading`
  guards the tallies so an empty list is not read as zero.
- `ProfileStatsTab` reads AniList aggregates for status/format/score/genre/tag
  and locally tallies country/release-year/watch-year/episode bands; the
  Overview/Genres/Tags section split is a deliberate answer to a real problem
  (a tag chart of 60 rows burying the interesting charts).
- Activity: likes, a comment thread with edit/delete gated on the viewer's id,
  likers sheet. `ShimmerBox` skeletons.
- `ActivityCalendarCard` heatmap with a `contentDescription` summary.

#### What AniSync does differently (`presentation/profile/**`,
`presentation/statistics/**`)

- **A month **and** a week heatmap** sharing one bucketed dataset: month =
  GitHub calendar (`MIN_WEEKS 18`, `MAX_WEEKS 53`, cell 13dp→26dp), week = one
  Sunday–Saturday breakdown with per-day bars, a `changePercent` vs last week,
  and a `DayDataState { Counted, Awaiting, Future }` because AniList's stats lag
  ~48h. Tapping a cell opens a **day sheet** listing that day's activity, with a
  `statusExcerpt` that strips embeds/links/HTML.
- **Far more chart types**: release-year histogram with a peak-year callout,
  year-over-year comparison (started vs aired), score histogram with per-bar
  spring, status donut, formats list, tag cloud by frequency, time breakdown
  (days/hours/mins), country chips, episode-length distribution, standard
  deviation with a plain-language verdict, top studios and top VAs/staff cards.
- **Social paging triggers tuned per layout**: load-more when within 4 of the
  end of a list, or within 2 rows of the end of a grid.
- **Blocked-user filtering** via inlined `isBlocked` per activity type — and a
  comment saying *why* it is inlined rather than in the shared fragment (the
  fragment is embedded in four lists in one profile query, and the extra field
  pushes it past AniList's limit → null).
- **Reply threading** emulated from a leading `@mention` (`ReplyThreader`), since
  AniList stores a flat reply list with no parent.
- **`ContentLimits`** pre-validation so an over-long comment is rejected locally
  rather than burning a round trip.
- **Activity sharing** is a deterministic `https://anilist.co/activity/<id>` URL
  — the list models carry no `siteUrl`, so deriving it is what makes the share
  work for every activity kind.
- **Profile favourites/stats share cards.**

#### Improvements

1. **Month/week heatmap + day sheet — P2, M.** AniSync's split is the right one:
   the calendar for "when do I watch", the week for "how much this week", the
   sheet for "what exactly". The 48h-lag `Awaiting` state is the detail that makes
   a heatmap honest rather than broken-looking. Pair it with the existing
   `BuildActivityCalendarUseCase`.
2. **Blocked-user filtering — P2, S.** Ask `user { isBlocked }` inline per
   activity type (not in a shared fragment — copy AniSync's reason), and hide
   blocked users' activities. AniSequel currently has no way to suppress someone
   from the feed.
3. **Reply threading from mentions — P2, S.** A pure function
   (`ReplyThreader.build`) over the existing reply list. It is testable with no
   Android, which fits `domain/`. Worth it because a flat 40-reply thread is
   currently unreadable.
4. **Content-limit pre-validation — P2, S.** Add a `domain/ContentLimits.kt`
   with AniSync's bounds (text activity 5..10,000; reply 2..8,000; thread
   comment 1..12,000; review summary 20..120, etc.) and disable the composer's
   submit with a reason rather than round-tripping a rejection.
5. **A richer composer — P2, M.** AniSync's `RichTextFormatBar` wraps a selection
   in `~!`/`!~` for spoilers, plus bold/italic/strike/code/link buttons. AniSequel
   renders spoilers but cannot *write* them, which is an odd asymmetry in a
   comment box.
6. **Add the missing charts, cheapest first — P2, M.** Peak-year callout,
   year-over-year comparison, time breakdown, standard deviation — all derived
   from data AniSequel already tallies. AniSync's charts early-return on empty
   and wrap the canvas in one TalkBack description; copy both habits.
7. **Favourites and stats share cards — P2, M.** AniSequel has an SVG tier-list
   exporter; AniSync has a whole `presentation/share/` system
   (`capturable`, fixed 1080px export density, WebP-lossless on API 30+). A
   "share my stats" and "share my favourites" card is a high-delight, low-risk
   addition that reuses the export plumbing already being built for the tier list.
8. **Per-layout paging triggers — P2, S.** Copy the "within 4 / within 2 rows"
   tuning; AniSequel's social grid currently pages on a single threshold.

**Rule check.** All read-only GraphQL + Compose. The share cards need
`capturable` (one small dependency) — acceptable, and it does not touch Compose
R8 rules. Charts stay `Canvas` + `clearAndSetSemantics`.

---

### 2.6 The tier list and sharing

#### Today in AniSequel

- `domain/tier/**` is pure Kotlin, tested without a device: rules, geometry,
  storage codec, SVG export. Documents are per-list keys with an id index and a
  one-time migration; every edit is one transaction and undo is the previous
  snapshot read inside the write.
- Drag is positional (tier + tile-to-go-above), with edge auto-scroll and a ghost
  translated by board movement. Sharing writes a full-board **PNG** rendered from
  the same layout at a fixed 1080-unit width, with a gallery save through
  `MediaStore`, and `domain/tier/TierListSvg.kt` remains the lossless vector
  renderer with base64 posters.

This is the strongest subsystem in either repo, and AniSync has no tier list at
all. The raster share and gallery save are shipped; the remaining borrowing is
smaller files.

#### Improvements

1. **WebP-lossless on API 30+** — P3, S. The PNG path is in place; a WebP
   variant would cut the file size while keeping rounded corners. Already behind
   AniSequel's floor, so no permission branch is needed.

**Rule check.** `capturable` is one dependency; the export path is `ui/`, so the
pure-`domain` tier rules are untouched. Do not move any layout maths out of
`domain/tier` for the sake of the raster path — the SVG path's whole value is
that it shares one layout.

---

### 2.7 Home-screen widgets

#### Today in AniSequel

- Four providers (profile activity, missed sequels, arriving sequels, featured
  sequel), each `: AppWidgetProvider()`, sharing `WidgetUpdater` as an `object`
  (deliberately not a base class, so `WidgetDeclarationTest` can read the source
  and require the literal).
- The provider *is* the receiver (the `AppWidgetProvider` unpacks both
  `EXTRA_APPWIDGET_ID` and `EXTRA_APPWIDGET_IDS`).
- Immediate placeholder render, then an off-main-thread cover fetch; `UPDATE`
  `RemoteViews`, `exported="false"`, `updatePeriodMillis = 1800000`.
- `WidgetCover.loadAsync` with a target px size computed from dp.

#### What AniSync does differently (`widget/**`)

- **`goAsync()` + a direct broadcast render** to keep `onUpdate` off the ANR
  path.
- **A two-phase cover fetch**: a `FAST_COVER_TIMEOUT_MS = 120` cache-hit-or-nothing
  window so text never waits on an image, then a `COVER_LOAD_BUDGET_MS = 2500`
  full budget for the rest.
- **A computed image memory budget** (`WidgetImageBudget.kt`):
  `DISPLAY_MULTIPLIER = 6`, `SAFETY_FRACTION = 0.4f`, `MAX_EDGE_PX = 480`,
  `MIN_EDGE_PX = 48`, `COVER_ASPECT = 1.4f`; per-image pixels divide the display
  budget by the image count. Degenerate budgets return the floor, never 0.
- **Every widget bitmap uses `allowHardware(false)`** — a hardware bitmap cannot
  cross the RemoteViews Binder boundary.
- **A size ladder** (`WidgetRows`: widths 180/300, heights 100/200, capped at 16
  reported sizes) and **`PendingIntent` identity via `data` URIs** — extras are
  ignored in PendingIntent equality, so two "different" intents with the same
  component collapse.
- `RemoteCollectionItems` on API 31+, a `WidgetCollectionService` fallback
  below — AniSequel's floor is 31, so it can use the modern path unconditionally
  (a simplification AniSync cannot make).
- A widget screenshot test and a `RemoteViewsCapabilityProbe` that discovered
  `HorizontalScrollView` is not `@RemoteView`.

#### Improvements

1. **Compute an image budget — P1, S.** `WidgetCover` currently decodes at a
   target dp size with no memory ceiling. Add AniSync's formula (multiplier 6,
   safety 0.4, edge clamps) split across the widget's image count. This is the
   difference between "works on my phone" and "works when the user stacks six
   widgets".
2. **A 120ms fast-cover window — P1, S.** `WidgetUpdater.updateWidgets` already
   paints text without a cover; make the *cover* fetch race a 120ms cache probe
   and only then enter the full budget. Text is on screen either way; this stops a
   cold cache from blocking the refresh.
3. **`allowHardware(false)` on every widget bitmap — P1, S.** Cheap; prevents a
   class of "widget is blank on one OEM" bugs with no other symptom.
4. **Size ladder correctness — P2, S.** AniSequel's XML declares explicit
   `minWidth/minHeight` but no size list; adopt `targetCellWidth/Height` plus a
   bounded ladder so a resized tile picks a layout instead of a stretched one.
5. **`PendingIntent` data-URI identity — P2, S.** Audit every widget's taps.
6. **A short refresh worker — P2, M.** `updatePeriodMillis` is floored at 30
   minutes by the platform. AniSync uses WorkManager, which AniSequel's README
   rules out for a *list* poller; a **widget-only** one-shot refresh enqueued as
   the app goes to background is compatible with that non-goal because it does
   not refresh the reader's list — it re-renders a tile the reader placed. Decide
   deliberately and write the reason down either way.
7. **Widget screenshot + interaction tests — P2, L.** AniSync's
   `WidgetScreenshotTest` writes PNGs for review and `WidgetInteractionTest`
   bounds a tap dispatch at <1000ms. This is the only way widget layout is
   tested without a human.

**Rule check.** RemoteViews XML is already the one sanctioned XML surface
(`NOTES § Home-screen widgets`); nothing here adds XML elsewhere. The budget
maths is pure Kotlin and belongs in a testable helper.

---

### 2.8 Updates and release notes

#### Today in AniSequel

The updater is **ahead** of AniSync's in every meaningful respect:

- Manifest read from a release asset (`releases/latest/download/update.json`) —
  with a written post-mortem of the four releases it lost by reading `main`.
- Download-before-permission, grant observed via `onResume`, APK cached per
  release version, resume-from-`.part` (206 continues, 200 restarts, 416
  deletes), rolling-window speed, zip magic + package + signature pin
  (`01924c4a…1f82`), `FileProvider` contract asserted by
  `UpdateFileProviderPathsTest`.
- `changelog.json` as the single source for both the in-app card and the release
  body, enforced by `ChangelogTest` and `VersionBaselineTest`.

#### What AniSync does differently (`data/update/`, `worker/UpdateCheckWorker`)

- A **periodic update check worker** that respects `autoUpdateEnabled` and
  `allowPrerelease` and posts to a notification channel.
- APK in the **external cache dir** (`context.externalCacheDir`), written to
  `latest.apk.tmp` and renamed atomically; a leftover `.tmp` is discarded.
- `versionToCode()` always uses exactly 3 segments × 1000 so `3.2.1` compares
  consistently.

#### Improvements

1. **A periodic check + notification — P2, M.** AniSequel deliberately has no
   notifications, and that non-goal is about a *list* poller. An update
   notification is a different thing: it is the app telling you a new version
   exists, once, and it is the only channel a sideloaded app has. If adopted, use
   a single channel, `autoUpdateEnabled`/`allowPrerelease` preferences, and do
   **not** reuse it for anything else. If rejected, record the rejection in
   `TO-DO` so it is not re-litigated.
2. **Atomic `.tmp` rename — P3, S.** AniSequel already writes `.part` and renames
   after verification, which is stricter. No change needed; listed only as a
   confirmation.
3. **External cache for the finished APK — P3, S.** AniSequel deliberately moved
   out of `cacheDir` for a good reason (the system may empty it). AniSync's
   external-cache placement is a *backup-quota* argument, not a durability one.
   Keep AniSequel's `filesDir/updates` and its test; do not copy this.
4. **Three-segment version compare — P3, S.** AniSequel compares `version_code`
   (monotonic by construction), which already sidesteps what AniSync's
   `versionToCode` exists to fix. No change.

**Rule check.** If the notification is adopted, it must be declared in the
manifest and requested on API 33+ — and must not become a general push surface.
AniSequel's `Upload`/release flow and `KeystoreIntegrityTest` are unaffected.

---

### 2.9 Settings, theme and motion

#### Today in AniSequel

`SettingsScreen` with three tabs — Info, Theme, Edit. Six handcrafted palettes +
dynamic colour + a custom accent + an **anime colour** layer (the title's hue
tinted into the surface ladder), theme preview, motion style
(`MotionStyle` INSTANT/SMOOTH/CHILL via `ExpressiveMotion.speed`, now snapshot
state), refresh interval, quick-add prefs, changelog, update section.

#### What AniSync does differently (`data/AppSettings.kt`, `ui/theme/**`,
`presentation/settings/**`)

- **11 palettes** (Dynamic + 10 hues) and the full `PaletteStyle` list with CMF
  appended last rather than disturbing default order.
- **A live look-and-feel preview** (`LookAndFeelPreview`).
- **Variable-font controls**: weight 100..1000, width 25..151, optical size
  8..144, slant −10..0, roundness 0..100 (default 100), persisted as JSON;
  `DisplaySize` caps font scale at 1.2.
- **A storage screen** that measures and clears every cache the app owns, plus an
  image-cache ceiling and `onTrimMemory` release.
- **A What's New store** with an expiry (`14 days`) and a baseline so a fresh
  install is not shown the whole back catalogue.
- **An AniList options sync** (adult content, title language, staff-name
  language, score format) mirrored into local settings with dirty-field diffing
  and a conflict dialog.
- **App icon variants** (4 activity-aliases) swapped on `onStop` with
  `DONT_KILL_APP`.
- **Onboarding**: welcome → sign-in → permissions → personalise → syncing → all
  set.
- **A compact nav bar** with floating-pill/anchored styles and a corner-radius
  slider, plus `LocalMainNavBarInset` so content clears it.
- Locale handling (`AppLocale`) so notifications, toasts and relative timestamps
  follow the app language rather than the device.

#### Improvements

1. **A per-app font scale — P2, M.** AniSequel has no text-size control, and its
   own README notes the Stats chip row "needs about 330dp of a 360dp phone before
   the user turns their font size up" — i.e. it already reasons about large text
   but offers no way to set it. Add a `DisplaySize` (capped at 1.2) and multiply
   the type scale, or simply expose the app's own `fontScale` preference into
   `Typography`. No font files required, so the vectors-only rule holds.
2. **A storage & cache screen — P2, M.** A "how much is this app using, and
   clear it" screen. AniSequel owns a snapshot directory
   (`cacheDir/anilist_snapshots/`), Coil's disk cache and the update directory —
   all worth surfacing and clearing. `CacheInventoryTest`'s habit (assert every
   cache the app owns is accounted for) is the right test to write with it.
3. **Palette styles — P2, M.** AniSequel has hue palettes; AniSync exposes the
   *style* axis too (tonal spot, vibrant, expressive, …). One extra control over
   the existing `DynamicThemeBuilder`, and it is where a "make it look like my
   wallpaper" reader actually lives.
4. **What's New with expiry — P2, S.** AniSequel's card always shows the newest
   block. AniSync's expiry + baseline prevents a fresh install from being shown
   the history. A small addition to `ChangelogRepository`.
5. **A per-account view-preferences bucket — P3, S.** AniSync has
   `clearAccountScoped()` so switching accounts resets *view* prefs without
   nuking device prefs. AniSequel is single-account today (§2.10), but if the
   library/tier/snapshot preferences are ever account-scoped, this is the shape.
6. **Respect reduced-motion — P3, S.** AniSequel's `MotionStyle` already offers
   INSTANT; consider mapping it to the system's "remove animations" setting as a
   default rather than a separate app-only toggle.

**Rule check.** A font-scale preference does **not** need `res/font` (no bundled
fonts), so the "no `res/font`" rule stands. A storage screen reads existing
directories; no new permission.

---

### 2.10 Auth, session and demo mode

#### Today in AniSequel

- OAuth2 PKCE against AniSequel's own client id (`52542`), overridable in
  Settings for forks.
- Token in plain DataStore, mirrored into a `@Volatile` cache for
  `AuthInterceptor` so the first request does not block on disk.
- Public username scan (read-only), demo mode with `DemoDataProvider` /
  `DemoProfileProvider`.
- Sign-out clears the token and the in-memory + disk caches
  (`clearDetailCache`, snapshot deletion).

#### What AniSync does differently (`data/account/**`, `data/security/**`)

- **Encrypted token storage** — `EncryptedSharedPreferences` with
  `AES256_SIV` keys / `AES256_GCM` values, one key per account, under a
  `MasterKey` with `AES256_GCM`.
- **Keystore-invalidation recovery**: deciphering is wrapped in `runCatching`;
  on failure it logs and *recreates* the file, which is the fix for the classic
  "device credential removed → keystore entry invalidated → crash on launch".
- **Multi-account**: metadata in plain prefs (JSON account list + active id) so
  the switcher renders without decrypting; tokens only in the secure file; a
  legacy single-token key migrated to a provisional account.
- **Account switch = surgical reset**: clear the normalized store, saved threads,
  account-scoped settings, viewer cache and badge; bump a `sessionEpoch` — no
  `Activity.recreate()`.
- **Per-account Apollo clients** with a 1 MB memory-only cache, so one account's
  queries cannot collide with another's key space.

#### Improvements

1. **Encrypt the token — P0, M.** Add `androidx.security:security-crypto` and
   move `KEY_ACCESS_TOKEN` into `EncryptedSharedPreferences`. Wrap the read in
   the keystore-invalidation recovery so a reset device credential cannot brick
   the app, and keep the `@Volatile` mirror for the interceptor. Update
   `AuthRepositoryImpl`'s tests. This is the single most important item in this
   document: the token can read and write the user's entire AniList account.
2. **An optional app lock — P0, M.** `AppLockManager` (locked on cold start when
   enabled, re-armed on `ON_STOP`, a suppression window while the biometric
   prompt is up) + `AppLockGate` (fail-open when the device has no secure lock,
   `BackHandler { moveTaskToBack(true) }`, `FLAG_SECURE` for the recents preview).
   Use `BIOMETRIC_WEAK or DEVICE_CREDENTIAL` so a PIN is enough. AniSequel's
   entire value is the reader's private watch history; a person who hands someone
   their phone should be able to lock it.
3. **A one-time legacy-token migration — P3, S.** When encryption lands, read the
   old plaintext key once, write it encrypted, delete the plaintext. AniSync's
   provisional-account trick is the template; AniSequel does not need the account
   part.
4. **Account switching — P3, XL.** Probably not worth it for a personal app, and
   it drags in per-account scoping of snapshots, tier lists, view prefs and the
   widget data. Listed for completeness; recommend *against* unless a concrete
   need appears.
5. **Onboarding — P2, M.** A first-run flow (welcome → sign in or scan or demo →
   permissions → theme → done) is the honest fix for a screen whose only door is
   a login button. It also gives the update-install permission a non-planted
   place to be explained.

**Rule check.** `security-crypto` and `biometric` are two small dependencies;
`biometric-compose` too if the prompt is drawn in Compose. Neither touches the
Compose R8 rules. `FLAG_SECURE` is a window flag, no new permission.

---

### 2.11 Networking and the request budget

#### Today in AniSequel

- Retrofit + OkHttp + Moshi; GraphQL as strings in `GraphQLQueries.kt`.
- `AuthInterceptor` (bearer), `RateLimitInterceptor` (reads `Retry-After`, backs
  off), `RequestCoalescer` (collapses identical in-flight reads, never
  mutations).
- A separate `OkHttpClient` for the update manifest so the auth interceptor can
  never attach a token to a public file.
- Snapshot-aware caching with per-kind TTLs (§2.12).

This is a solid, minimal stack. AniSync's is heavier because it has a much
larger read surface; the value to AniSequel is in the *governance*, not the
volume.

#### What AniSync does differently

- **`RateLimitGate`** as described in §2.1, with `minGapMs = 120`,
  `maxInteractiveWaitMs = 20_000`, reserved fractions, `jitterMs = 80`,
  `probeTimeoutMs = 10_000`, and a `Decision { Admit, Wait, Refuse }`.
- **An executed block persisted** with a clamp to 120s.
- **A retry policy with typed errors** (`RetryPolicy.kt`,
  `AniListErrorInterceptor.kt`): server-error attempts 3, transport 2, base
  backoff 500ms doubling to 8s, jitter 250ms, and **mutations are never
  retried**.
- **Error typing from the GraphQL body, not the HTTP status**, with a
  **64 KB body cap** (`readCapped`) so a hostile/proxied response cannot blow
  memory.
- **A coalescer that cancels only when the last waiter leaves**, in an
  `Unconfined` scope, with a waiter count — a naive cancel-on-first-collector
  breaks coalescing.
- **Priority is scoped per account/token**, so one account's key space cannot
  collide with another's.

#### Improvements

1. **Add the gate below the coalescer — P1, L.** One `Mutex`, a rolling 60s
   window seeded from `x-ratelimit-limit/remaining`, tiers (`Interactive`,
   `Prefetch`), a persisted `blocked_until`, and a `Decision`. Route the existing
   `RateLimitInterceptor`'s 429 into the gate rather than only backing off. The
   payoff: a throttled dashboard shows a countdown instead of an error, and a
   background prefetch cannot starve the reader's next tap.
2. **A retry policy with one rule worth pinning — P1, M.** Retry reads on 5xx
   with exponential backoff + jitter; **never retry a mutation**. That single
   distinction prevents the worst class of bug this app could have: a
   `SaveToPlanning` sent twice because the first response was slow.
3. **Cap error-body reads — P1, S.** Wrap any error-body read in a 64 KB cap.
   Cheap, and it is a real memory-safety property rather than a nicety.
4. **Fix the "last waiter" coalescer semantics — P1, S.** Verify
   `RequestCoalescer` cancels on the *last* collector, not the first. If it
   cancels early, two screens one frame apart can each fire the request the
   coalescer existed to prevent. Add a test with two collectors and a slow
   shared body.
5. **Inject a `Clock` — P1, S.** AniSync's gate/retry take an injected clock and
   `Random`, which is why their tests are trivial. AniSequel's `RateLimitNotice`
   and freshness watch should do the same now, before the gate lands.
6. **Ambient request priority — P3, S, only if needed.** If AniSequel grows
   widget/prefetch work, an ambient coroutine-context priority avoids threading a
   parameter through every repository call. Do not add it speculatively.

**Rule check.** Pure OkHttp/coroutines. No dependency. Do **not** migrate to
Apollo/codegen — AniSequel's string queries plus `MediaDetailQueryTest`,
`StudioQueryTest` and friends already pin the wire contract, and the migration
cost is enormous for no capability gain at this size.

---

### 2.12 Caching and freshness

#### Today in AniSequel

- In-memory maps + a disk `SnapshotStore`: one JSON file per kind under
  `cacheDir/anilist_snapshots/`, atomic write (temp + rename), read back with
  its own write timestamp (never "now"), release-version-stamped, cleared on
  sign-out and on mutation (`listEpoch`).
- Per-kind TTLs: viewer 30 min; list/overview follow the refresh interval;
  detail **flat 24h**; cover batch follows the list's window.
- `ListFreshnessWatch` on resume with a 5-minute failure floor.

#### What AniSync does differently

- **Status-keyed detail TTL** — releasing 3h, not-yet-released 6h,
  finished/cancelled **7 days**, else 1 day — plus a **countdown-override**: if
  the cached `nextAiringEpisode` time has passed, the record is *provably* stale
  regardless of TTL.
- **Per-query FetchPolicy**: cast/crew/stats are `CacheFirst` ("a show's cast
  does not change"); volatile reads are network-first.
- **Cooldowns** for gestures vs automatic refreshes (5s / 15s).
- **A cache-maintenance worker** that GCs the normalized cache to a 1-day max
  age and trims to 16 MB, re-trimming until under the ceiling.
- **`countOnly` search previews** and taxonomy cached with `CacheFirst`.

#### Improvements

1. **Status-keyed detail TTL + countdown override — P1, S.** This is the highest
   signal-to-effort item in the document. Replace the flat 24h with
   `staleAfterMillis(status)` and, before consulting the TTL, compare
   `nextAiringEpisode.airingAt` to now. A finished show is cached for a week (a
   real request saving), a currently-airing one for three hours (a real
   correctness win). One function, one test.
2. **Per-kind FetchPolicy analogue — P2, S.** AniSequel's snapshot kinds already
   are the policy; extend the idea by giving the *person* and *studio* snapshots
   a long window (they are as settled as a cast) while keeping the activity feed
   session-only, exactly as the README already argues.
3. **A cache-inventory screen + trim — P2, M.** See §2.9. AniSync separates
   "Room owns offline data" from "the GraphQL cache is disposable" — the analogue
   is "snapshots are structured data the app can re-fetch at any time", so a
   "clear cached data" action is always safe. Say that in the UI.
4. **Gesture vs auto cooldown — P1, S.** Repeated in §2.1 because it is the same
   fix for two callers.
5. **Do not collapse the snapshot windows.** AniSequel's decision to serve a
   snapshot with its *own* timestamp is subtler than AniSync's normalized cache
   in one respect, and the README's five rules are worth keeping verbatim.

**Rule check.** All local file operations, testable with `TemporaryFolder`.
Nothing here needs a database, and `SnapshotStoreTest` /
`RepositorySnapshotsTest` are the right places to pin the new windows.

---

### 2.13 Security and privacy

#### Today in AniSequel

- `REQUEST_INSTALL_PACKAGES` used correctly (declared, asked only on update,
  observed via `onResume`); signature pinning of downloaded APKs; a tight
  FileProvider path; `KeystoreIntegrityTest` asserting no key material in the
  repo and no literal secret in the workflow.
- No analytics, no telemetry.

#### What AniSync adds

- Encrypted token storage + keystore-invalidation recovery (§2.10).
- App lock / biometric gate with recents hiding (§2.10).
- A 64 KB error-body cap and typed errors (§2.11).
- Deliberately minimal crash reporting and local logs instead of a remote SDK.

#### Improvements

1. **Encryption + app lock — P0.** Already itemised in §2.10; listed here because
   they are security, not features.
2. **Consider `FLAG_SECURE` while the editor is open — P2, S.** Minor, but a
   private note or an editor with a token-paste field should not appear in the
   recents thumbnail.
3. **A privacy statement in Settings — P3, S.** AniSync's About/FAQ states
   plainly what the app does and does not collect. AniSequel's README is honest;
   the *app* should say the same thing in one card ("AniSequel talks only to
   AniList and GitHub Releases").
4. **Keep the zero-telemetry stance.** AniSync's "no trackers, no analytics" is a
   feature users choose it for; AniSequel should state the same and mean it.

**Rule check.** No new permission for `FLAG_SECURE`. The biometric prompt needs
`USE_BIOMETRIC` (normal, not runtime).

---

### 2.14 Accessibility

#### Today in AniSequel

Non-trivial and deliberate in places: `EngagementAction` disables itself when
liked-state is unknown, posters pass `contentDescription = null` and let the
title text be the accessible name, the heatmap carries a summary description,
and several chips set a 40–44dp touch target.

There is, however, no *contract* — no test asserts a minimum target, no shared
matcher, and headings/selected-state are ad hoc.

#### What AniSync does (and why it is worth copying)

- **`semantics { heading() }` on every section label**, so TalkBack can navigate
  by heading rather than reading a wall.
- **`selected` semantics plus a ring border**, never colour alone, on status and
  priority cells.
- **Merged descriptions on composite rows** (`mergeDescendants = true` with one
  "label: value" string for progress and rewatch rows).
- **Every chart canvas wrapped in one `clearAndSetSemantics` description.**
- **Content descriptions are string-resourced** (`cd_*`, `a11y_*`) throughout.
- **48dp interactive targets**, enforced by shared androidTest helpers
  (`accessibility/`), and 36dp steppers with 20dp glyphs where space is tight.
- **Counts are pluralized and read as "999+"** rather than truncated digits.
- **`BackHandler { moveTaskToBack(true) }`** so Back never dismisses a lock gate.

#### Improvements

1. **Add heading semantics to section labels — P2, S.** Start with
   `SectionHeading.kt`, the settings cards, and the profile tabs.
2. **Add `selected` semantics + a ring to every selected-state cell — P2, S.**
   The Library filter chips and the status grid are the two that currently rely
   on colour.
3. **Merge composite rows — P2, S.** Progress ("3 of 24"), score, and the
   entry-editor's steppers.
4. **Give every chart one description — P2, S.** Reuse the heatmap's existing
   pattern for the score/status/format charts.
5. **Write the contract as tests — P2, L.** A shared 48dp matcher plus a few
   `androidTest` assertions on the dashboard, library and settings, mirroring
   AniSync's `accessibility/` package. This is what makes the other four stick.

**Rule check.** Pure Compose semantics. The tests need an `androidTest` source
set, which AniSequel does not have today (§2.15).

---

### 2.15 Testing, CI and distribution

#### Today in AniSequel

88 JVM tests, and they are excellent: they pin *requirements* (query arguments,
chunk-walk semantics, spoiler reveal, update resume, version alignment), not
preferences. `TO-DO §2.2` and `§5.4` already name the gaps honestly: no
animation tests, no screenshot tests, no benchmark, no instrumentation.

#### What AniSync does

- **Instrumented tests**: `MigrationTest`, `accessibility/*`,
  `WidgetInteractionTest`, `WidgetScreenshotTest`,
  `RemoteViewsCapabilityProbe`, `CacheTrimTest`, `CacheInventoryTest`,
  `ImageCacheBoundsTest`, `UpdateApkLocationTest`.
- **Screenshot output** written to `/data/local/tmp` for visual review.
- **Injected `Clock` and `Random`** in the rate-limit/retry classes, which is why
  their tests need no `Thread.sleep`.
- **CI**: tag-triggered release, manual debug/release workflows, a sponsors
  workflow that regenerates a JSON asset; lint config that disables only
  `MissingTranslation` and `UseAppTint`, each with the reason.
- **F-Droid reproducibility**: `dependenciesInfo { includeInApk = false }` and
  `vcsInfo { include = false }`.
- **ABI splits with derived version codes** (`base * 10 + abiCode`) and a
  `universal` APK, so a device always prefers the optimised split.

#### Improvements

1. **Add Compose screenshot tests — P2, L.** A new `androidTest` (or Robolectric
   screenshot) target covering the four profile tabs over `DemoProfileProvider`,
   the library grid's empty vs full state, and the settings cards. This directly
   retires `TO-DO §5.4` and gives `§5.1` (blur cost) a measuring stick.
2. **Instrumented widget tests — P2, L.** A `WidgetScreenshotTest` + interaction
   test over the four providers. The widget code has one guard
   (`WidgetDeclarationTest`) and no behavioural test at all.
3. **An accessibility test source set — P2, M.** The 48dp matcher and a handful
   of assertions from §2.14.
4. **Inject `Clock`/`Random` into rate-limit and freshness code — P1, S.**
   Pre-work for the gate; makes the existing freshness test instantaneous.
5. **ABI splits — P2, M.** Add `splits { abi { ... isUniversalApk = true } }` with
   the `base * 10 + abiCode` version-code maths, and teach the updater that
   several APKs can carry the same `version_code` (or have the workflow publish
   per-ABI download URLs). **This one needs care with the updater**, which
   verifies a single `download_url`; either publish a universal asset *as well*
   or extend the manifest. AniSync publishes both, which is the safe shape.
6. **A `preview` flavour — P2, S.** `applicationIdSuffix = ".preview"` and a
   distinct label, so a tester build can sit beside the stable one. No release
   risk; it removes the "uninstall to test" tax.
7. **`dependenciesInfo { includeInApk = false }` and `vcsInfo { include = false }`
   — P3, S.** Only matters if F-Droid distribution is ever wanted, but it is two
   lines and it makes the APK byte-reproducible-friendly.
8. **Keep the version-baseline gate.** AniSync's tri-file version check exists
   only because it lacks AniSequel's derived-version workflow; AniSequel's
   `VersionBaselineTest` plus derived version is strictly better. Do not replace
   it.

**Rule check.** `androidTest` needs `androidx.test` + `espresso` +
`ui-test-junit4` (already in the version catalog as test deps) and a connected
device/emulator in CI. The screenshot tests can run under Robolectric to avoid a
device, which may be the pragmatic first step given the repo states there is no
local Android SDK.

---

### 2.16 Localisation and typography

#### Today in AniSequel

- `res/values/strings.xml` only. Every user-facing string is English, and
  `values-*` does not exist.
- Typography is a fixed `Type.kt`; no user control over size beyond the system.
- The README's own Stats-section KDoc reasons about large-font overflow, which is
  the tell that the app is already thinking about text it does not let you size.

#### What AniSync does

- Weblate-hosted translations; lint's `MissingTranslation` deliberately disabled
  because "Weblate owns the translated locales and they land incomplete by
  design".
- An observable `AppLocale` so notifications, toasts and relative timestamps
  follow the app language, not the device's.
- Variable-font axes and a capped display scale.

#### Improvements

1. **Externalise the settings/profile/changelog strings first — P2, L.** Localisation
   is a grind, not a project; the honest first step is to stop hard-coding in the
   screens that change most (settings cards, profile section titles) and keep
   `strings.xml` the growing source of truth.
2. **`MissingTranslation` must stay enabled until locales actually exist.**
   AniSync disables it for a reason that does not yet apply here; do not copy the
   `lint { disable += ... }` line early.
3. **A per-app font scale — P2, M.** See §2.9. It is the accessibility-adjacent
   half of this section and much cheaper than translation.
4. **Locale-aware relative times — P3, S.** `ProfileFormatters.kt` already
   formats; make it read the device locale rather than assuming `en`.

**Rule check.** No `res/font` (the font-scale control uses the system font). RTL
support falls out of Compose once strings are externalised, but the tier-list SVG
exporter will need checking for mirrored layout.

---

## 3. Phased roadmap

### Phase 1 — Foundations (2–3 weeks)

The things that are wrong rather than merely missing, plus the cheap wins that
unblock bigger ones.

- Encrypt the OAuth token + keystore-invalidation recovery. **(P0)**
- Optional app lock + recents hiding. **(P0)**
- Empty-response guard on the list fetch. **(P1, S)**
- Status-keyed detail TTL + countdown-override. **(P1, S)**
- Gesture-vs-auto fetch cooldowns. **(P1, S)**
- Mutation coalescer for progress/score. **(P1, S)**
- Image-dimension clamp + markdown parse cache. **(P1, S)**
- Inject `Clock`/`Random` into rate-limit and freshness code. **(P1, S)**
- Error-body cap + last-waiter coalescer test. **(P1, S)**
- Widget image budget + 120ms fast-cover + `allowHardware(false)`. **(P1, S)**
- Externalise the highest-churn strings. **(P2, L, ongoing)**

### Phase 2 — Depth on surfaces that exist (3–5 weeks)

- `RateLimitGate` with tiers and a persisted block. **(P1, L)**
- Retry policy; never retry mutations. **(P1, M)**
- Where-to-watch, recommendations, `relationType(version: 2)`, 500-cap. **(P1–P2)**
- Library: bulk edit, status badge map, custom lists, grid columns, notes. **(P1–P2)**
- Entry editor: format-aware score, finish prompt, dirty-state guard. **(P1)**
- Profile: month/week heatmap + day sheet, blocked-user filter, reply threading,
  content limits, richer composer. **(P2)**
- Person/studio: facts tiles, tabs with counts, row-level VAs, studio mosaic. **(P2)**

### Phase 3 — Platform quality (3–4 weeks)

- Screenshot tests + instrumented widget tests + accessibility tests. **(P2, L)**
- Accessibility pass (headings, selected semantics, merged rows, chart descs). **(P2)**
- ABI splits + preview flavour. **(P2)**
- Storage/cache screen, font scale, palette styles, What's New expiry. **(P2)**
- Reviews + media stats on the title page. **(P2)**
- Onboarding. **(P2)**
- Tier-list raster share. **(P2)**

### Phase 4 — Optional / decide-later

- Periodic update check + notification channel. **(P2, decide deliberately)**
- Widget refresh worker. **(P2)**
- Localisation rollout. **(P2)**
- Deep-link verified app links. **(P3)**
- Multi-account. **(P3, recommend against)**

---

## 4. Quick wins (a day or less each)

| Change | File(s) | Why |
| --- | --- | --- |
| Clamp markdown image dimensions | `ui/components/markdown/` | Prevents a real layout crash on public content |
| Cache parsed markdown (`LruCache(200)`) | `AniListMarkdownParser.kt` | Removes jank on every scroll-back |
| Status-keyed detail TTL + countdown override | `AniListRepositoryImpl.kt` | Weekend-long cache for finished shows; correctness for airing ones |
| Empty-response guard | `AniListRepositoryImpl.kt` | A 429 must never look like "list deleted" |
| Gesture vs auto cooldown | `ListFreshnessWatch.kt` | Stops double-refresh reports |
| Mutation coalescer (600ms) | new `ui/util/MutationCoalescer.kt` | One request per burst of taps |
| `relationType(version: 2)` | `GraphQLQueries.kt` | Correct "source" label for light novels |
| 500-cap on connection totals | detail UI | Stops printing a floor as an exact count |
| `allowHardware(false)` on widget bitmaps | `ui/widget/` | Fixes blank-on-some-OEM widgets |
| Widget image budget | `ui/widget/WidgetCover.kt` | Widgets that survive being stacked |
| `Clock` injection | rate-limit / freshness | Makes the next tests trivial |
| Error-body 64 KB cap | network layer | Memory safety |
| `dependenciesInfo { includeInApk = false }` | `app/build.gradle.kts` | F-Droid-ready, two lines |
| `preview` flavour | `app/build.gradle.kts` | Test beside stable |
| 48dp touch targets on chips/steppers | several | The most-felt a11y fix |
| Heading semantics on section labels | `SectionHeading.kt` | TalkBack navigation for free |

---

## 5. Do not change

AniSync is the reference for *mechanism*, not for *judgement*. These AniSequel
choices are better than the equivalent in AniSync, and the next reader should
know they were considered and kept:

1. **No offline-first database.** AniSync uses Room as the source of truth and
   maintains 28 schema versions and a destructive-fallback warning to make that
   safe. AniSequel's snapshot tier with its own write timestamps, its
   release-version stamp and its five documented rules is a smaller surface with
   the same practical benefit and no migration debt. Borrow optimistic *writes*;
   do not borrow the database.
2. **Query contracts pinned by tests.** AniSequel's `MediaDetailQueryArgumentTest`,
   `StudioQueryTest`, `MediaListChunkWalkTest` and friends encode AniList facts
   (the `studios` field takes no `perPage`; `studioId` does not exist on
   `Page.media`; nested totals cap at 500). AniSync pins its own facts in
   comments but has no equivalent gate for every one. Do not migrate to Apollo
   codegen and lose this.
3. **The updater.** Resume-from-`.part`, download-before-permission,
   on-resume grant handling, per-release APK caching, a signature pin and a
   `FileProvider`-contract test. This is stricter than AniSync's atomic-`.tmp`
   flow, and its post-mortems are the most valuable prose in the repo.
4. **The changelog gate.** `changelog.json` as one source for both the in-app
   card and the release body, enforced by tests. AniSync has a CHANGELOG file and
   a What's New store, but AniSequel's mandatory version-bump-plus-changelog rule
   is stronger.
5. **Pure-Kotlin tier list with one shared layout.** Better than anything
   AniSync has; a raster export should reuse that layout, not fork it.
6. **Comments that record the failure.** Keep this habit when adopting anything
   from this document. AniSync's inline "why" comments are good; AniSequel's are
   better and more consistent, and a borrowed mechanism without its failure
   recorded is how it gets "fixed" back.
7. **The vectors-only, springs-only UI rules.** AniSync depends on
   `material-icons-extended` and a variable-font stack; AniSequel's icon policy
   is a real APK-size and consistency win. Do not import icon packs to save a few
   `ImageVector` definitions.

---

## 6. Appendices

### A. AniSync constants worth copying

| Mechanism | Constant | Value |
| --- | --- | --- |
| Rate gate | min gap | 120 ms |
| Rate gate | max interactive wait | 20 s |
| Rate gate | prefetch / background reserve | 0.2 / 0.4 |
| Rate gate | jitter / probe timeout | 80 ms / 10 s |
| Rate window | default limit / window | 30 / 60 s |
| Rate window | retry-after clamp | 1..120 s |
| Retry | server attempts / transport attempts | 3 / 2 |
| Retry | backoff base → max / jitter | 500 ms → 8 s / 250 ms |
| Retry | mutations retried | **never** |
| Error body cap | max bytes | 64 KB |
| Coalescer | cancel policy | last waiter |
| Mutation coalescer | debounce | 600 ms (500 ms in some VMs) |
| Fetch cooldown | gesture / auto | 5 s / 15 s |
| Detail TTL | releasing / not-yet / finished | 3 h / 6 h / 7 d |
| Details total cap | nested connection | 500 |
| Widget cover | fast window / full budget | 120 ms / 2.5 s |
| Widget image budget | multiplier / safety / max edge / min edge / aspect | 6 / 0.4 / 480 px / 48 px / 1.4 |
| Widget size ladder | widths / heights / max sizes | 180,300 / 100,200 / 16 |
| Search debounce | — | 350 ms |
| Min search length / min spinner | — | 2 / 800 ms |
| Content limits | text activity | 5..10,000 |
| Content limits | reply | 2..8,000 |
| Content limits | thread comment | 1..12,000 |
| Content limits | review summary | 20..120 |
| Rich image clamp | max width / height | 3,000 dp / 800 dp |
| Share export | fixed width | 1080 px |
| Heatmap | min/max weeks, base cell, max cell | 18 / 53 / 13 dp / 26 dp |
| Feed page size | — | 25 |
| Profile activities page | — | 50 |

### B. Mechanism glossary

| Term | Meaning |
| --- | --- |
| **Optimistic write** | Update local state/UI first, send the mutation second, reconcile or roll back on failure. |
| **Mutation coalescer** | Per-key debounce that collapses a burst of counter taps into one write of the settled value, and sends nothing if it returns to the committed value. |
| **Request coalescer / inflight tracker** | Joins *identical concurrent reads* into one request; cancels only when the last waiter leaves; never joins mutations. |
| **Rate-limit gate** | Admission control in front of the network: a rolling window, tiers with reserved fractions, and a persisted block. |
| **Empty-response guard** | Refuse to overwrite a populated local store with an empty API response. |
| **Countdown override** | Refetch a cached detail whose `nextAiringEpisode` time has passed, regardless of TTL. |
| **Status-keyed TTL** | Staleness window that depends on the title's release status. |
| **Dirty-field diff + conflict** | Local-first preference sync that pushes only changed fields and surfaces a conflict when the server also moved. |
| **ClampedBody** | Measure a child and only offer "Read more" when it truly overflows. |
| **Fast-cover window** | A short cache-only image probe so text is never blocked on a bitmap. |
| **Image budget** | Derive a per-image pixel ceiling from screen metrics, image count and a safety fraction. |

### C. File map: AniSequel → AniSync counterpart

| AniSequel | AniSync |
| --- | --- |
| `data/repository/AniListRepositoryImpl.kt` | `data/LibraryRepositoryImpl.kt`, `data/DetailsRepositoryImpl.kt`, `data/ProfileRepositoryImpl.kt`, `data/fetcher/*` |
| `data/repository/SnapshotStore.kt` | `data/local/AppDatabase.kt` + `worker/CacheMaintenanceWorker.kt` |
| `data/network/RequestCoalescer.kt` | `data/network/RequestCoalescer.kt` + `data/util/InflightTracker.kt` |
| `data/network/RateLimitInterceptor.kt` | `data/network/RateLimitGate.kt`, `RateLimitWindow.kt`, `RateLimitPersistence.kt`, `RetryPolicy.kt` |
| `data/repository/AuthRepositoryImpl.kt` | `data/account/AccountStore.kt`, `AccountManager.kt` |
| `ui/screens/library/LibraryScreen.kt` + `ProfileLibraryTab.kt` | `presentation/library/*` |
| `ui/components/detail/DetailEditTab.kt` | `presentation/library/components/EditLibraryEntrySheet.kt` |
| `ui/components/detail/info/InfoPage.kt` | `presentation/details/MediaDetailsScreen.kt` |
| `ui/components/detail/PersonProfileSheet.kt` | `presentation/details/CharacterDetailsScreen.kt`, `StaffDetailsScreen.kt` |
| `ui/screens/studio/StudioScreen.kt` | `presentation/details/StudioDetailsScreen.kt` |
| `ui/screens/profile/UserProfileScreen.kt` | `presentation/profile/ProfileScreen.kt` |
| `ui/screens/profile/ProfileStatsTab.kt` | `presentation/statistics/*` |
| `ui/screens/profile/ActivityCalendarCard.kt` | `presentation/statistics/ActivityHeatmapSection.kt` + `ActivityWeekBreakdown.kt` + `ActivityDaySheet.kt` |
| `ui/screens/profile/ActivityCommentsSheet.kt` | `presentation/activity/*` + `presentation/components/richtext/*` |
| `ui/components/markdown/*` | `domain/parser/*` + `presentation/components/RichTextRenderer.kt` |
| `ui/widget/*` | `widget/*` + `widget/core/*` |
| `data/update/UpdateManager.kt` | `data/update/UpdateManager.kt` + `worker/UpdateCheckWorker.kt` |
| `ui/screens/settings/*` | `presentation/settings/*` + `data/AppSettings.kt` |
| `ui/theme/*` | `ui/theme/*` |
| `domain/tier/*` | *(no counterpart — AniSequel leads here)* |
| `domain/usecase/FindMissedSequelsUseCase.kt` | *(no counterpart — AniSequel leads here)* |

---

*Written by comparing the two repositories at AniSequel 1.3.8 and AniSync 3.2.1.
Every recommendation is scoped to a feature AniSequel already has, and every one
names the rule it must respect.*
