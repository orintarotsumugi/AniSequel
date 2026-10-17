# TO-DO

A prioritised list of known improvements. Not a wishlist — each entry says what
is actually wrong, what it costs, and what the first step would be, so the next
person can start without re-investigating.

Ordered by *value per unit of risk*. Anything in §0 is a correctness or security
problem and should jump the queue.

---

## 0. Correctness

**Nothing open.** This queue is for correctness and security problems and should
jump everything below it. It held one entry — a tier-list key that was written and
never read — which is now deleted rather than wired up; see [Closed](#closed).

The section is kept rather than renumbered away so that the `§N.M` references
throughout this file, and in the code comments that point at it, keep pointing at
the right thing. If you add something here, it goes above `## 1`.

---

## 1. Code structure

### 1.1 Split `UpdateManager` — medium effort, mechanical once started

508 lines, of which the manifest fetch, the download loop, the certificate
verification and the cache management are four unrelated jobs. This was left as-is
because the signing block depends on Android APIs that need careful test setup,
and there is no local Android SDK in the development environment to verify a split
against.

Suggested split, in dependency order:

| File | Contents |
| --- | --- |
| `ApkSignatureVerifier.kt` | cert extraction, fingerprinting, the pin |
| `UpdateCache.kt` | `downloadedApkFor`, `targetFileFor`, `downloadDirectory`, `clearDownloadedApk` |
| `UpdateManager.kt` | manifest fetch + download streaming |

The cache half is pure file operations and is the easiest to test — a
`TemporaryFolder` and no Android framework at all. Start there.

### 1.2 Split `DashboardViewModel`'s recompute pipeline — medium effort, needs care

The file is ~1140 lines including comments, past the 700-line ceiling in
[AGENTS.md §1](AGENTS.md) and the largest file in the repo by a wide margin. The
public contract (`DashboardUiState`, `DashboardEvent`) is already extracted to
`DashboardUiState.kt`, and the triplicated cache-invalidation is now one
`invalidateDerivedState()` function with its divergence made explicit.

The rest is blocked on real coupling: `discoveredCandidates`, `discoveryKey`,
`detailCache`, `addedToPlanningIds` and the debounce generation guard share
about a dozen private fields. Extraction means either threading that state
through or making it `internal`, and both make the invariants harder to hold,
not easier.

The version that would actually work:

- **First**, extract a `DashboardFilters` value type (the sort/filter/search
  state). It is pure data, it is what every UI control binds to, and it makes
  the recompute trigger obvious.
- **Then**, extract a `MissedSequelPipeline` class taking `(collection, filters)`
  and returning `List<MissedSequel>`, owning the discovery memo and the
  debounce. That is the unit worth testing on its own — it is where the
  correctness rules live, and it is currently only reachable through a
  ViewModel.
- **Last**, `DashboardViewModel` becomes load/mutate + a `StateFlow` of results.

Do not start at the end. The filters first is not busywork; it is what makes the
pipeline's inputs explicit enough to hand to a class.

### 1.3 `bouncyPress` is a `composed { }` modifier, and 43 call sites skip its fix — medium effort

`ExpressiveMotion.bouncyPress` uses `Modifier.composed`, which is deprecated in
favour of `Modifier.Node` / `ModifierNodeElement`. The cost is not just the
deprecation warning: `composed` cannot be skipped in recomposition and allocates
its lambda chain per use.

There is a testable stopping point well before a full node implementation — an
`Animatable` held in a `remember` alongside a `graphicsLayer` reads almost the
same and already removes the deprecation. The full `ModifierNodeElement` rewrite
should measure the frame time before and after with a macrobenchmark; there is no
benchmark module in the repo yet.

**The bigger half of this is the `interactionSource` argument, not `composed`.**
Of 57 call sites, 24 now pass a source and **33** do not. The other 33 get the
fallback path, which installs a second `pointerInput` node that sets "pressed" on
every pointer-down — so a finger dragged across one of those elements springs it
down and back up again: two springs per gesture.

**The fallback cannot be fixed centrally, and that is worth knowing before anyone
tries.** The obvious repair is to defer the press until the first movement
confirms the gesture is still within touch slop — but a genuinely still tap may
produce no movement event at all before lift, and deferring would silently
remove press feedback from every tap in the app. `Modifier.clickable` does not
have this problem because it cancels press the moment the gesture is *claimed*,
which is information this heuristic does not have and cannot get later. So the
fix is per-site, and it is one line each.

Fixed so far, in three passes. The first two because they are the two a finger
meets most; then the controls a press *and* a scroll can both start on, which is
where the fallback is wrong rather than merely wasteful:

| Site | Why it mattered |
| --- | --- |
| `SequelCard` | One of these is on every row of the dashboard's `LazyColumn`, and `bouncyPress`'s own KDoc names it as the case that matters most |
| `AnimeTrailerBanner` | Inside the detail sheet's scroll, and directly implicated in a reported "bounces twice". The file is gone now — the title page's Extras card replaced it — so this row is a record, not a site to check |
| `AniTopAppBar` (filter, refresh, settings) | The three actions sit above a feed the user scrolls constantly; a press that turns into a scroll used to keep the icon squashed until the finger lifted |
| `SettingsButton` (all four variants) | Inside the settings scroll, and the variant chosen at runtime shares one source because only one of them is ever drawn |
| The sign-in flow — `LoginScreen`'s demo button, `UsernameScanCard`'s Scan button, `OAuthCard`'s Connect / Paste Token / Client ID | The whole login screen is one `verticalScroll`, so starting a scroll on any of these was reachable on every launch. It is also the first screen a new user touches |

The remaining 33 are **not** all the same, and the split has not been measured —
it needs reading each one rather than pattern-matching the file:

- some have a `clickable` / `combinedClickable` / `Button(onClick=)` in the same
  modifier chain and can share a source, which is a one-line change each;
- some are decorative presses on elements that are not themselves interactive, and
  there is no source to share — the honest fix there may be to drop the press
  rather than wire it up, which is a different change with a different review.

**Do these as one pass, with a real phone afterwards.** There is no Android SDK in
the development environment, so none of them can be checked from here, and a
press-behaviour regression is invisible in review. Doing forty of them blind in
small batches is how one of them ships broken.

---

## 2. Animation and rendering

### 2.1 `ExpressiveVisuals`' orb still animates while it is not visible

`ExpressiveVisuals.kt` starts a `rememberInfiniteTransition` for the empty-state
orb. It ticks for as long as the composable is in the tree, including when the
orb is off-screen.

Two of the three costs that used to be grouped under this entry are now gone:

- The instant motion style no longer runs the clock at all - the orb branches to
  a static composable instead of animating a shape it then ignores.
- The morph progress is read by `MorphShape` *while drawing* rather than in
  composition, so a frame of the morph no longer recomposes the empty state.

What is left is the transition itself ticking behind an orb that has been
scrolled out of view. The shimmer loader had the same shape and is fixed - its
phase clock is now hoisted and shared across every skeleton on screen.

Same fix applies: derive the animation from a shared phase, and stop driving it
when the surface is not visible. There is no visible-surface API in the current
Compose version being targeted, so this needs either a lifecycle gate or an
accept-and-measure decision.

### 2.2 No animation regression tests

Every existing test covers data logic. Nothing asserts that a spring resolves,
that a colour transition completes, or that nothing animates forever. The
`AnimatedContent`-per-frame bug this PR fixes was invisible to the entire test
suite.

Worth one test with a fake clock asserting that a counter's rendered value stops
changing once the animation settles. It is the cheapest possible guard against
the class of bug that costs frame budget silently.

### 2.3 Six near-identical colour transitions

`ExpressiveControls.kt` had six separate `spring<Color>` literals. They now share
`FastColorEffects`, but they are still six call sites. A `Modifier` that springs
a container colour would collapse them to one.

---

## 3. Build and release

### 3.1 No `release` build type guard against a missing key locally

`assembleRelease` silently falls back to the debug keystore when no release key
is configured. That is deliberate — it makes a local release build installable —
but it means a locally "successful" release build produces an APK no user can
ever update to. A `BuildConfig` flag recording which key was used, surfaced in
`Settings > About`, would make it obvious rather than a trap.

### 3.2 R8 keep rules for Moshi are broad

`proguard-rules.pro` keeps Moshi's reflective members broadly, because the
models rely on `@Json(name = ...)` mapping. That is correct and it is why the
models are annotated at all — but the rule is a blanket keep rather than a
per-class one.

`ksp` (already wired for `moshiKotlinCodegen`) generates adapters; the reflective
keep can likely narrow to the annotated classes. Measure before and after: a
keep rule that is too narrow produces a runtime parse failure, not a build
failure, and that failure only appears for users in the field.

### 3.3 Dependency currency

Nothing is *wrong*, but several entries are behind:

| Dependency | Current | Note |
| --- | --- | --- |
| `composeBom` | 2025.11.01 | Governs the Compose stack; bumping means re-checking the `material3` alpha pin against it |
| `navigationCompose` | 2.8.9 | Upgrade path is unrelated to the Material 3 pin |
| `coilCompose` | 2.7.0 | Newer majors changed the API surface |

Room used to be listed here. It is gone — no `roomRuntime` entry exists in
`gradle/libs.versions.toml`, and there is no database in the app at all (see
[Closed](#closed)). Nothing here should be read as "needs migrating"; these are
ordinary libraries that can move a version at a time.

Upgrade one at a time, with `./gradlew test` green in between, and keep the
reasoning inline in `gradle/libs.versions.toml` in the same change.

### 3.4 No benchmark or screenshot-testing module

There is no way to measure an animation change or to catch a UI regression
automatically. A `macrobenchmark` module with one scrolling benchmark plus
Compose screenshot tests on the dashboard and settings screens would turn §2.1
and §2.3 from opinions into measurements.

This is the highest-leverage item in the whole list for the quality of future
animation work, and the most expensive to start.

### 3.5 A docs-only push to `main` cannot pass branch protection

Found by trying to push a README-only commit. `main` requires **2 status
checks**, and `android-release.yml` declares `paths-ignore: ['update.json',
'**.md']` on its `push` trigger. Those two settings contradict each other:

1. a markdown-only push starts **no** workflow run;
2. therefore no check reports against that commit;
3. therefore the required checks are unsatisfied and the push is rejected.

It only appeared to work because the push credential used had admin rights —
GitHub answered `Bypassed rule violations for refs/heads/main: 2 of 2 required
status checks are expected`. **Anyone without that bypass cannot land a docs-only
change.** In 124 commits this had never surfaced, because every commit to date
also touched source and so always ran the workflow.

Pick one deliberately rather than leaving it to whoever pushes next:

- **Accept the bypass** as the documented path for docs, and say so in
  [README § CI](README.md).
- **Add a cheap docs workflow** — `on: push: paths: ['**.md']` — running a link
  and anchor check. It satisfies the required checks without building an APK, and
  it would also catch the broken `#anchor` links the README accumulates.

The second is the better fix and is genuinely useful on its own: nothing
currently verifies that the README's own table of contents matches its headings.

---

## 4. Product

### 4.1 Scan progress is not reported

Scanning a large account walks every relation and the UI has no progress state
for it. `DashboardUiState.Loading` carries a static message. A real progress
model would need the scan to emit intermediate results, which is a domain-layer
change — related to §1.2.

### 4.2 The detail sheet bounces twice at the ends — OPEN, needs a device

Reported as: fling the "Anime Info & Specs" tab to the bottom and it bounces
twice; same again at the top. The **top** half is now explained and fixed — the
hero image's `transformable` gesture gate was a tautology, so the sheet could not
be scrolled by dragging its own first row, and the spring settling afterwards read
as the double bounce (see Closed).

**The bottom half is not.** There is no hero and no gesture handler at the bottom
of that tab; the content there is static. Two candidates, neither provable from the
code alone:

1. **Double overscroll.** `overscrollEffect` is never configured anywhere in the
   app, so `Modifier.verticalScroll` installs the platform effect, and
   `ModalBottomSheet` has its own nested-scroll handoff into the sheet's anchored
   drag. A fling that exhausts the inner scroll may fire both. The one-line thing
   to try is `verticalScroll(state, overscrollEffect = null)` on the sheet's
   content, which leaves exactly one of them reacting.
2. **`AnimatedContent`'s size transform.** The default animates the container
   height between tabs, which changes `maxValue` under the scroll state while a
   fling is still settling.

**The tab that report names no longer exists.** "Anime Info & Specs" was the middle
of three tabs and is now the whole page — one `LazyColumn` under the hero — so the
report has to be taken again before either candidate is worth touching. Both of
them move with that change rather than being invalidated by it:

- candidate 1 loses its `verticalScroll` call site. A `LazyColumn` is not a scroll
  container with an `overscrollEffect` to pass `null` to in the same way, so the
  one-line experiment has to be re-derived rather than renamed;
- candidate 2 is still live for the *destination* switch (the page and the entry
  editor are still swapped by an `AnimatedContent`), but it is no longer what a
  fling *inside* the page interacts with — the page's own height does not change
  when its data arrives.

This needs a device and a finger, not a JVM test — which is exactly the gap
[§2.2](#22-no-animation-regression-tests) and [§3.4](#34-no-benchmark-or-screenshot-testing-module)
describe. Do not "fix" it by guessing, and record which candidate it actually was
here.

---

## 5. Profile UI follow-ups

The profile screen got a visual pass — hero header, summary strip, blurred
activity backdrops, a scan-friendly friends grid, and nested-markdown rendering.
What that pass deliberately did **not** do is listed here so the next person does
not mistake each one for an oversight.

### 5.1 Blur has no cost measurement on a long activity feed

`ActivityCard` blurs every cover it draws (`Modifier.blur(28.dp)`), and an
activity feed is unbounded — it pages. On a low-end device, a screenful of
blurred layers is the first thing likely to drop frames, and there is no
benchmark module to say whether it does (see §2.3 / §3.4).

**Partly addressed:** the backdrop is no longer decoded at the card's own size. It
was asking Coil for a ~1,000px-wide bitmap per row and then blurring 84px of
detail straight back out of it; it now asks for `BACKDROP_DECODE_PX = 220`, a tenth
of the pixels and indistinguishable under the blur. That is a real cut to the
decode work on every row that scrolls into view — and it is still, itself, a
change with no measurement behind it.

**Still open, and the smaller bitmap does not help:** the blurred *layer* is the
cost, not the source image. `Modifier.blur` allocates a render texture the size of
the card for every visible row, on the GPU, whatever the bitmap came from.

First step: measure with `macrobenchmark` on one scrolling test, then either cap
the blur to the visible cards or replace it with a pre-blurred server-side
transform. Do not guess either way.

### 5.2 The hero header does not collapse on scroll

The banner is a fixed **260dp** block (it was 200dp when this was written, and is
now horizontally pannable via `PannableBackdrop`, so it is draggable but still
does not yield its height). The reference design it follows collapses it into the
top app bar as the page scrolls, reclaiming that height for the bio and
favourites.

This needs the header to be a `LazyColumn` item that reports a scroll offset
to a `TopAppBar`'s `scrollBehavior`, plus a decision about what the bar shows
while collapsed. Worth doing only with a screenshot test in place first, because
the failure mode (a header that flickers between two states) is exactly what a
review cannot see.

### 5.3 ~~Markdown nesting stops at four levels~~ — not a defect, moved out of the backlog

`AniListMarkdownParser` re-parses an emphasis run's or link label's inner
expression, capped at `MAX_INLINE_DEPTH = 4`, and past the cap the renderer falls
back to the flat source text.

This is a **working guard, not a known problem**, and it was only ever in this
list because it reads like one: a cap is a thing that might need raising. It does
not, and it should not be "fixed". The cap is what prevents unbounded recursion
on a hand-edited string, a real bio never comes close to it, and the degradation
below it is a legible fallback rather than a crash.

If it ever genuinely needs raising, raise it *with a test that pins the new
value* rather than by removing the guard. Nothing to do.

### 5.4 No screenshot tests for the profile tabs

The five profile tabs — Home, Activity, Stats, Library, Social — are the most
shape-variable screens in the app (they change entirely between an account that
pinned nothing and one that pinned fifty), and nothing asserts what they look
like. Every visual claim in this pass was verified by reading code and comparing
against a reference image, not by a test.

A Compose screenshot test per tab, over the demo fixture `DemoProfileProvider`
already builds, would turn §5.1 and §5.2 into measurements and catch the next
regression in the header the way `ExpressiveShapesTest` catches capsule geometry.

---

## 6. Network and request budget

AniList serves roughly 30 requests a minute, and this app spends that budget
fairly carefully: duplicate reads coalesce, lists and profiles are cached for the
user's own refresh window, covers are one batched request per profile, and manga
is fetched on demand. The three places below still spend it in a way nobody has
measured. Each is a deliberate *non*-change rather than an oversight, so each one
records what it would take to do properly.

### 6.1 Every profile open downloads the whole media list

`UserOverviewViewModel.load` fetches `MediaListCollection` for every profile it
opens, because four of the Stats tab's tallies (country, release year, watch year,
episode bands) have no aggregate on AniList and are counted from the list itself.

For the signed-in viewer that is usually free — the dashboard has already put the
list under `user:<id>`, and this screen reads it by id precisely so it can reuse
that entry. For a profile the reader has not visited before it is the app's most
expensive query, one request per 500 entries, and it is paid even when all they
did was look at a banner and a bio.

The obvious fix — "fetch it when Stats or Library is actually opened" — was
considered and not taken. `UserOverviewLoadOrderTest` pins the two-publish order
*and* the `isLibraryLoading` contract that makes it safe, and four of its
automated assertions are about the list arriving behind the overview; changing the
design means rewriting requirement tests, not deleting a convenience. It also
moves the download in front of the Stats tab, which is the screen somebody opened
the profile *for*, rather than letting it run while they read the bio.

What it would take: a `loadLibrary()` on the ViewModel, an `onLoadLibrary`
threaded through `ProfileTabsContent` exactly like the existing `onLoadManga`, and
those tests rewritten to the new contract.

### 6.2 The list's chunk walk is sequential, and stops at ten

`fetchFullMediaListCollection` asks for chunk 1, then walks `2..10` **one at a
time** (`while (hasNext && currentChunk <= 10)`). Two things are wrong in
principle and neither is reachable for a realistic account:

- The ceiling silently truncates a list of more than 5,000 entries and returns it
  as `Result.success`, which is a correctness bug rather than a performance one —
  the caller caches a short list as if it were the whole account.
- The sequential walk spends wall-clock seconds where two or three chunks in
  flight would not.

Both are left alone because the trivial case dominates: a 500-entry list is one
chunk, and an 800-entry one is two. Against a limiter that punishes bursts, more
concurrency buys less than it costs.

What it would take: a test that a 5,001-entry list is *not* reported complete
(that half is worth doing on its own), then a bounded `async` fan-out per batch of
chunks with the merge below it unchanged.

### 6.3 A bio's cover batch is one document with no upper bound

A bio's AniList links are resolved in a single `Page`-aliased document
(`GraphQLQueries.getMediaCoversQuery`) — the right shape, since the alternative
was one request per card, and a "hall of fame" bio carries a dozen links.

But the document grows with the bio and nothing caps it: one alias and one field
set per link, all in one query. AniList rejects a query over its complexity limit
with HTTP 400 for the *whole* document, and covers are the one request whose
failure is deliberately invisible — a missing cover leaves the card as it was — so
a link-heavy bio would sit on placeholder posters permanently with nothing on
screen saying why.

What it would take: a cap of N keys per request, with the remainder in further
batches, and a log line (not an error banner) when a batch is refused, so the next
occurrence is diagnosable instead of merely quiet.

---

## Closed

Entries that used to live above and are no longer true. Kept rather than
deleted, because in each case the *reason* is the part worth not re-deriving —
see [AGENTS.md §1](AGENTS.md).

### The tier list's `seeded_for_v1` was written and never read

`TierListPreferences.markSeededFor` recorded which AniList account had been
offered "Rank from AniList scores", so the offer could be made once per account.
Nothing ever read it back — the offer ended up on the empty state of every list,
which is the better behaviour now that lists are documents: a new list *is* a new
offer, with no flag to keep in step.

Deleted rather than wired up, because "have we already seeded this account" is not
a question the library has. The key stays in stored preferences and does not need
removing: DataStore is a map and an unread key is inert.

### `UpdateManifest.kt` mixed the wire model with UI display helpers

`formatBytes` and `formatRate` are pure display formatting for the settings and
update dialogs; they were sitting next to the manifest DTO, which meant
`UpdateManifestTest` had to import a UI helper to check a unit conversion. Both
are in `UpdateFormatting.kt` now, same package, so no call site changed.

The original entry proposed moving `isNewerThan` out too, "so it could get a
proper test without constructing a full manifest". **That test already existed** —
four dedicated cases, and every manifest field defaults, so
`UpdateManifest(versionCode = 17)` is the whole fixture. `isNewerThan` stayed on
the manifest, which is where a fact about a manifest belongs.

### `stripMarkdown` was UI-dead and only its own test kept it alive

`ProfileAboutCard.stripMarkdown` reduced a bio to plain text. Nothing rendered it
— `AboutCard` draws the parsed AST — but twelve cases in `ProfileTextFormattingTest`
exercised it, which is the worst kind of evidence: a tested-but-uncalled function
reads as "some screen must be using this".

Both went together. The property those tests pinned is preserved in a note at the
site, because it is right and worth keeping if a single-line bio preview is ever
wanted: **over-stripping is the real risk.** A bio that loses its asterisks and
also loses its apostrophes is a person's words edited by their app.

### The nav pill's label never fitted

`ExpressiveNavigationBar`'s `SELECTED_WEIGHT` was 1.6, described as "enough that
the longest label fits". It was not — it was tuned against the label *clipping*
rather than its fitting. On a 360dp phone the selected slot was 86dp, leaving
29.3dp of label room against the ~55dp "Activity" needs, so **every** destination
ellipsized at every phone width. Now 2.3, with the bar's and item's own padding
trimmed to hand the difference to the label: 60.2dp of room, with the four
unselected items still at 49.2dp against the 48dp tap-target floor.

`ExpressiveTabBar` had the same problem with longer labels and could not be fixed
by geometry — "Anime Info & Specs" needs ~133dp of a 78.7dp budget. Those labels
are shortened instead.

### The dashboard detail cache had no size bound

It was a `Map` in `DashboardViewModel`, never evicted. The cache has since moved
down to `AniListRepositoryImpl` alongside the rest of the media fetching, and
now carries a soft cap: `MAX_DETAIL_CACHE_NODES = 128`, cleared wholesale rather
than evicted one at a time. Clearing is the deliberate choice — holding every
full cast/studio tree the user has ever tapped costs more than the occasional
refetch does. The entry named the wrong file the whole time it was open.

### There was no in-app way to see what changed between versions

The notes were a hardcoded `listOf` in the Info tab, stale since v1.0.21 and with
no version attached to check it against. `app/src/main/assets/changelog.json` is
now the single source, read by the "What's new" card and by the release workflow
when it writes the GitHub release body. `ChangelogTest` exists because the
workflow reads that file without any of this repo's tests running. See
[README § Release notes](README.md#release-notes).

### AI Studio leftovers were removed

`metadata.json` declared `MAJOR_CAPABILITY_SERVER_SIDE_GEMINI_API`, the app
applied the Secrets Gradle plugin for a Maps integration that never existed, and
an `.env.example` sat in the root full of Gemini placeholders. None of it was
imported by the app: there was no Firebase or Gemini dependency on the classpath
and no `com.google.firebase` import anywhere in `app/src/main`. All of it is
gone, along with the commented-out Firebase/Room/Camera dependencies and their
version catalog entries — which is why [§3.3](#33-dependency-currency) no longer
lists Room.

### Partial: the empty-state orb animates while off-screen

Two of the three costs are gone — the instant motion style no longer runs the
clock at all, and the morph progress is read while drawing rather than in
composition. What remains is the transition ticking behind an orb that has been
scrolled out of view; it is still tracked as [§2.1](#21-expressivevisuals-orb-still-animates-while-it-is-not-visible).

### The detail sheet's three tabs became one page

The sheet split a single title across "Story", "Specs" and "My Entry". The story
and specs halves were two answers to one question — "what is this show" — and
neither was complete without the other, so the reader had to hold both in their
head and switch tabs to check either. It is now one `LazyColumn` under the hero:
artwork, progress, synopsis, the production facts, genres, tags, alternative
titles, rankings, cast, crew, relations, and the actions the app has for the title.
The entry editor is the sheet's second destination, reached from the capsule at the
bottom, because editing is a mode rather than a section of the page. The scroll is
hoisted out of the `AnimatedContent` so coming back from the editor lands on the
card the reader left — the same trap the profile tabs' scroll positions fall into,
for the same reason: `AnimatedContent` disposes the destination it leaves.

Opening the page also widened `GET_MEDIA_DETAIL`: it now asks for `characters`
(with `voiceActors`), `staff` and `relations` in the request it was already making
for the synopsis, rankings and tags, rather than a second round trip per section.

### The detail sheet could not be scrolled from its own hero

`PannableBackdrop`'s `hasOverflow` asked "is there anywhere to drag to?" as
`width overflows || height overflows`. That is a near-tautology, because the
cover fit is a `max` of the two axis ratios and over-covers the other axis *by
definition* — its own doc comment says so. So the gate was true for essentially
every image and `canPan` never once declined.

`Modifier.transformable` consumes a single-pointer drag in **both** axes when
`canPan` is true, and `canPan` is one boolean for the whole gesture rather than a
per-axis decision. So the hero ate every vertical drag aimed at it, including the
ones that should have scrolled the sheet — and then `backdropTransform` clamped
the pan straight back to zero, because a 1920x1080 banner over a 360x280 hero has
0.0000dp of vertical slack. Gesture consumed and discarded, sheet unscrolled, and
the release spring read as the hero bouncing twice.

The gate is now shut at the cover fit and opens once the picture is zoomed. The
trade is that the banner can no longer be panned sideways at zoom 1 to reach the
cropped left and right thirds; pinch still zooms, and panning returns once
zoomed. `BackdropTransformTest` pins all three states, including that the
geometry really does overflow horizontally — otherwise the test would just be
asserting the new answer.

### The backdrop's fit mode has no caller, and that is deliberate

`BackdropFit.FIT` was written for the title page's hero, so a wide AniList banner
would open whole instead of cropped to its middle third. The hero then stopped
using `PannableBackdrop` in the same release: the title page redraws its own
backdrop so the parallax can be driven by the page's scroll in the draw phase,
and that drawing has to keep covering its box as it moves - a fitted hero would
show its letterbox the moment the page scrolled.

So nothing passes `fit = BackdropFit.FIT` today. It stays because it is the half
of the component a caller who wants "show the whole image" rather than "fill the
box" reaches for, and `BackdropTransformTest` holds the arithmetic until then.
Removing the changelog entry that promised the behaviour was the other half of
the same honesty: the app crops the way it always did, so the release cannot
claim it stopped.

### The edit tab refetched the entry on every tab switch

The draft was `remember`ed *inside* `DetailEditTab`, and the tabs live in an
`AnimatedContent`, which disposes the tab it animates away from. So every switch
away and back tore the form down: blank fields, the spinner again, and
`editor.load()` firing a second request for an entry AniList had already sent.

The state moved to `EntryEditState`, remembered by the sheet one level up, so it
outlives the tab — still deliberately not in the ViewModel, since nothing outside
the tab renders an in-progress edit. `load()` also refuses to fetch an entry it
already holds, which is the part that cannot be seen from the UI;
`EntryEditStateTest` pins it as "three visits to the tab must still cost exactly
one request".