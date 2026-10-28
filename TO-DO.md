# TO-DO

A prioritised list of known improvements. Not a wishlist — each entry says what
is actually wrong, what it costs, and what the first step would be, so the next
person can start without re-investigating.

Ordered by *value per unit of risk*. Anything in §0 is a correctness or security
problem and should jump the queue.

---

## 0. Correctness

### 0.1 The comment sheet force-stops while loading a thread — OPEN, needs a trace

What is actually wrong: opening the comment sheet on an activity closes the app.
The report is exact and useless — "it force-closes when loading the comments" —
and there is no stack trace, because a force-stop leaves nothing on the device
once the system dialog is dismissed and the app has no crash backend by design.

What is *not* wrong, and has already been ruled out by reading the code and by
two hardening rounds: every repository call on that path is wrapped in
`repositoryResult` (an API or JSON failure becomes an error message, not a
throw), the markdown parser catches `Exception` and `StackOverflowError`, the
`LazyColumn` keys are deduped in `CommentThreadStore` so a repeated id cannot
throw, the composer's selection maths is clamped, and the sheet's
`fillMaxHeight` is a bounded `ModalBottomSheet` height - `ActivityLikersSheet`
proves that one by working.

What the first step is: **turn on Settings > Info > Diagnostics > "Keep the last
crash report"** (it is off by default), reproduce once, then come back to that
card - it shows the exception on one line, with Copy and Show for the whole
report. The file is `files/diagnostics/last-crash.txt` and the format is pinned
by `CrashReportsTest`. The exception line names the failing path - an
`OutOfMemoryError` under `Modifier.blur` on a long feed (see §5.1) is a different
bug from an `IllegalStateException` in the sheet's composition - and the frames
say which file to open. Do not guess at a fix before that line exists: a rewrite
of a 1,900-line surface that cannot be verified locally is how a working screen
is replaced by a differently broken one.

The section numbering is kept rather than shifted so that the `§N.M` references
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

> **Also exempt, and deliberately so: `GraphQLQueries.kt` (~1160) and
> `AniListRepositoryImpl.kt` (~1610).** Decided in PR #71, not deferred. Both sit
> far past the 700-line ceiling and are staying there; the reasoning is recorded
> at the top of each file, and this entry exists so the exemption is visible from
> the list of tracked exceptions rather than only from inside the file.
>
> `GraphQLQueries` is one responsibility — *what this app asks AniList for* — and
> the reason a field is or is not in a document is its effect on the *other*
> documents. `studios(isMain: true)` inside `relations` is what turns a
> 476-entry list into an HTTP 500, and that is a fact about the boundary between
> two documents, not about either one. Splitting puts the detail query and the
> list query that must stay lean in different files, and the next person adding a
> field to one cannot see what it costs the other. Most of the length is raw
> GraphQL inside string literals, which is not code that factors.
>
> `AniListRepositoryImpl` is the same shape as `DashboardViewModel` below: the
> caches, the coalescer and the namespace-keyed detail cache share about a dozen
> private fields, and extraction means threading that state or making it
> `internal`, which makes the invariants harder to hold.
>
> **What is worth doing instead of splitting, and is not done:** assert the
> relationships the comments describe. The cache-cap eviction and the
> coalescer's double-request collapse are both behaviour nothing currently pins,
> and that is a real gap — a smaller one than the line count suggests. The disk
> tier that arrived with the snapshots is the exception: `SnapshotStoreTest` and
> `RepositorySnapshotsTest` pin its windows, its invalidation and its sign-out,
> which is most of what those new lines were for.

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
Of 69 call sites, 26 now pass a source and **43** do not. The other 43 get the
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

### 1.4 `AppNavigation.kt` keeps every destination in one function — low effort

The file is ~1,130 lines and a dozen `composable()` registrations, past the
700-line ceiling. Unlike §1.1 and §1.2 this is not shared state: it is a list of
independent registrations, which is why it has been allowed to grow. What it
costs is the ceiling itself — the number stops meaning "this file is doing two
things" and starts being a rule everyone bends.

Two splits have already happened: `MainTabs.kt` holds the tab enum, the bar and
the tap navigation, so the file no longer owns the app's navigation model, only
its destination table; and `PageTransitions.kt` holds the route springs and the
direction rules, which change for motion reasons rather than for destination
reasons. The next step is a `NavGraphBuilder` extension per destination group —
studio first, because it needs the repository and the `NavController` and nothing
else — with the captured locals (preferences, `authState`, `signedInViewer`)
passed as parameters rather than closed over. Do it a group at a time; the
registrations do not call each other.

---

## 2. Animation and rendering

### 2.1 `ExpressiveVisuals`' orb still animates while it is not visible

`ExpressiveVisuals.kt` starts a `rememberInfiniteTransition` for the empty-state
orb. It ticks for as long as the composable is in the tree, including when the
orb is off-screen.

Three of the four costs that used to be grouped under this entry are now gone:

- The instant motion style no longer runs the clock at all - the orb branches to
  a static composable instead of animating a shape it then ignores.
- The morph progress is read *while drawing* rather than in composition, so a
  frame of the morph no longer recomposes the empty state. It used to be read
  inside a `Shape.createOutline`, which turned out not to draw at all: a clipped
  layer computes its outline once, so the orb froze mid-morph (that is what the
  lopsided clover was). The silhouette is drawn with `Modifier.drawBehind` now -
  see `Morph.writeSilhouette` in `ExpressiveShapes.kt`.
- **A stopped Activity no longer animates it either.** The branch is gated on
  `rememberIsResumed`, so while the app is in the background the static composable
  is the only one composed and no clock is allocated to be paused. That is the
  same signal the joke toast's timer uses.

What is left is the transition ticking behind an orb that has been **scrolled out
of view while the app is resumed**. That is the narrow case: an empty state sits
in a plain `Column` rather than a lazy list, so scrolling it away does not dispose
it. The shimmer loader had the same shape and is fixed - its phase clock is now
hoisted and shared across every skeleton on screen.

The fix is a visibility gate, not a lifecycle one: there is no visible-surface API
in the Compose version being targeted, so it needs either an
`onGloballyPositioned`-derived "is any of this on screen" check (cheap, but it
runs a layout pass on every scroll) or an accept-and-measure decision. Do not
replace the lifecycle gate with one of those without measuring - the lifecycle
gate is the part that was free.

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
  [BUILDING § Continuous integration](BUILDING.md#continuous-integration).
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

The four profile tabs — Home, Activity, Stats, Social — are the most
shape-variable screens in the app (they change entirely between an account that
pinned nothing and one that pinned fifty), and nothing asserts what they look
like. The main bar's Library page has the same problem, and it is the one screen
whose empty state and full state differ by hundreds of rows. Every visual claim in
this pass was verified by reading code and comparing against a reference image,
not by a test.

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

The obvious fix — "fetch it when Stats is actually opened" — was
considered and not taken. `UserOverviewLoadOrderTest` pins the two-publish order
*and* the `isLibraryLoading` contract that makes it safe, and four of its
automated assertions are about the list arriving behind the overview; changing the
design means rewriting requirement tests, not deleting a convenience. It also
moves the download in front of the Stats tab, which is the screen somebody opened
the profile *for*, rather than letting it run while they read the bio.

What it would take: a `loadLibrary()` on the ViewModel, an `onLoadLibrary`
threaded through `ProfileTabsContent` exactly like the existing `onLoadManga`, and
those tests rewritten to the new contract.

### 6.2 The list's chunk walk is sequential

`fetchFullMediaListCollection` asks for chunk 1, then walks
`2..MAX_LIST_CHUNKS` **one at a time** (`while (hasNext && currentChunk <=
MAX_LIST_CHUNKS)`). The sequential walk spends wall-clock seconds where two or
three chunks in flight would not.

That is left alone because the trivial case dominates: a 500-entry list is one
chunk, and an 800-entry one is two. Against a limiter that punishes bursts, more
concurrency buys less than it costs.

The ceiling used to be a literal ten, and it used to **truncate silently**: a list
of more than 5,000 entries was cut to its first ten chunks and returned as
`Result.success`, so the caller cached a short list as if it were the whole
account. That half is fixed. The walk now follows AniList's own `hasNextChunk` up
to a runaway guard of forty chunks, and a walk the guard stops while the server
still has chunks is reported as a failure rather than a short success —
`MediaListChunkWalkTest` pins both halves.

What it would take (for the remaining half): a bounded `async` fan-out per batch
of chunks with the merge below it unchanged.

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

### The score control could not write the scores its own row printed

It was two defects in the same control, both from not knowing the account's score
format.

The scale was guessed: `scoreMax` came from `if (score > 10)`, so a
hundred-point entry scored 8 or 10 opened on a 0-10 slider whose ceiling could
not be passed, and a five- or three-point account got the same 0-10 range, which
offers values AniList rejects. The ticks were guessed too: `steps = scoreMax - 1`
is one per whole point, so a `POINT_10_DECIMAL` entry showing "8.5 / 10" could
only be dragged to 8 or 9, and the score the row printed was unreachable from
the control meant to set it.

The open entry asked for a decision on "which tick spacing satisfies every
format". None does, and that was the wrong question: `MediaList` has no score
format, but `Viewer.mediaListOptions.scoreFormat` is one field on the request the
editor already makes. Both halves are now derived from it — the ceiling from the
format's own maximum, the tick from its own step, with a tenth on
`POINT_10_DECIMAL` and a whole point on the integer formats. The old inference
survives only as the fallback for a payload with no `Viewer` (the save response),
where the step is halved so a printed `8.5` stays reachable. A loaded score is
never snapped until the slider is touched, so opening the form and saving
unchanged cannot rewrite an `8.3` to `8.5`. `EntryScoreScaleTest` pins all of
it, including the query asking for the enum.

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

### The detail store's published map defeated its own bound

`MediaDetailStore` bounds its namespaced fetch cache at 250 entries, while the
map the screens read was `_details.value + (id to node)` for the life of the
store. Past 250 opened titles the fetch cache evicted an entry and the node
itself stayed reachable from the published map, so the cap bounded nothing: a
long session held every synopsis, cast and studio tree it had ever opened.
Nothing visibly breaks when that map drops an entry, which is why it went
unnoticed.

Both maps now share the bound, and opening a title refreshes its recency in the
published one — so the sheet on screen is the last entry the bound would drop.
`MediaDetailNamespacingTest` pins both halves.

### There was no in-app way to see what changed between versions

The notes were a hardcoded `listOf` in the Info tab, stale since v1.0.21 and with
no version attached to check it against. `app/src/main/assets/changelog.json` is
now the single source, read by the "What's new" card and by the release workflow
when it writes the GitHub release body. `ChangelogTest` exists because the
workflow reads that file without any of this repo's tests running. See
[NOTES § Release notes](NOTES.md#release-notes).

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

Three of the four costs are gone — the instant motion style no longer runs the
clock at all, the morph progress is read while drawing rather than in composition,
and a stopped Activity no longer drives it at all (`rememberIsResumed`). What
remains is the transition ticking behind an orb that has been scrolled out of view
*while the app is resumed*; it is still tracked as
[§2.1](#21-expressivevisuals-orb-still-animates-while-it-is-not-visible).

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

### A character's page hid half the appearances AniList sent

`getCharacterProfileQuery` passed `type: ANIME` to `Character.media`, so a character's
appearances row listed only anime. The same query asked every node for its `type`, and
`ProfileAppearance` is documented around a character appearing in a manga — the field
the tap routes on exists precisely because both namespaces occur — so the filter was
contradicting the query's own payload. The staff query never filtered, which made it
visible: an actor's page listed manga credits their character's page hid. The filter is
gone; both person kinds draw the same list.

### `yearsActive` was a range printed as a list

AniList's Staff schema says `yearsActive: [Int]` — `[startYear, endYear]`, with a lone
`[startYear]` meaning the person is still working. The model's KDoc said "a person can
have a non-contiguous career" and `facts` joined the values with a comma, so a career
running 1963 to 2013 printed as "Active 1963, 2013": two years with a fifty-year hole
between them. The still-working form printed as a bare year. `facts` renders the span
now, and `PersonProfileTest` pins both forms.

### The person sheet's height cap was a crop

`PROFILE_SHEET_MAX_HEIGHT` caps the sheet so it opens on the portrait and the name.
The cap was `heightIn` alone, and `Column` measures children in order - so a biography
long enough to fill it left the appearances row and the "Open on AniList" button
measured into a zero-height remainder. They were unreachable, not merely below the
fold. The column is a scrolling viewport now, which keeps what the cap was for (the
face stays on screen when the sheet opens) and removes what it did wrong.

### An inline spoiler could never be opened

`~!...!~` on a line of its own becomes `MarkdownNode.SpoilerBlock`, which has a
chip: "Show spoiler". The same token *inside* a sentence becomes
`InlineToken.Spoiler`, and that branch drew the obscured run and stopped —
`append(SPOILER_PLACEHOLDER.repeat(...))`, with no annotation and no state. So it
was not merely hard to open, it could not be opened: the plot point written
mid-paragraph was covered for good, one line away from a block spoiler whose chip
revealed fine.

The comment on the branch recorded why, and the reasoning had expired: "an
`AnnotatedString` cannot be tapped, so it has nowhere to put a reveal".
`LinkAnnotation.Clickable` is exactly a tap on a span, and the same file was
already routing `LinkAnnotation.Url` taps through the app's own link handler two
functions below. The obscured run is the tap target now — a chip is not drawable
in an `AnnotatedString`, and a pill dropped mid-sentence would break the line it
lives on — numbered per paragraph by a shared counter so two spoilers in one
sentence open independently, with the reveal in `rememberSaveable` because a bio
paragraph is disposed with its `LazyColumn` item (the same trap
`RenderSpoilerBlock` documents). The run also wears the chip's own
`secondaryContainer` pair; it had been sharing `code`'s background, which made an
inline spoiler and a code span look like the same thing.

`InlineSpoilerRevealTest` pins the annotation, the listener, the placeholder being
what opens, and the saved state.

### Picking a palette under Dynamic colour changed nothing

`AniSequelTheme` resolves its scheme in the order anime colour → wallpaper
(Dynamic colour) → custom hue → palette, and the accent section's controls only
knew about the first of those. A palette or hue click cleared the anime layer and
left the wallpaper switch alone, and the palette card's `selected` state ignored
`useDynamicColor` outright — so under Dynamic colour a pick moved the check mark,
lit the border and changed nothing at all, and the reader had no way to tell that
apart from the theme applying.

Both controls now step the wallpaper off with the anime layer; turning Dynamic
colour *on* steps the anime layer off for the same reason (it outranks the
wallpaper, so the switch looked broken until the layer timed out); the hue caption
no longer names a colour the wallpaper is overriding; and `ThemePreviewCard`'s pill
lists the sources in the order the theme resolves them, rather than listing a
stored custom hue ahead of the wallpaper that is actually drawing.

### The hero swapped its artwork mid-open

`InfoHero`'s backdrop was `media.bannerImage ?: media.posterUrl`, while the same
file's KDoc argues at length that the hero leads with the key visual and *not* a
banner ("cropped into a hero it competes with the title sitting on top of it").
The `?:` made that true only until the detail response landed: a sheet opened from
the dashboard starts on the list node, which has no banner, so the first frames
drew the poster — and then the banner arrived and replaced the artwork under the
title mid-entrance, at the cost of a second full-size download, decode and GPU
upload during the animation. The hero draws the key visual again, and the banner
is simply not read here. It is still selected by the detail query and merged onto
the node, which is why `MediaNode.bannerImage` survives the change.

### A person's appearance rows asked for the small cover, and the merge kept it

Two halves of the same soft poster. `PROFILE_MEDIA_FIELDS` requested
`coverImage { large }` — 230px — and the appearance row draws at
`APPEARANCE_CARD_WIDTH = 112.dp`, about 336px on a 3x screen, so every poster under
a character or staff member was upscaled before it was drawn. The query asks for
`extraLarge` too, and the row prefers it.

The other half was `MediaNode.withDetail`, which copied `detail.coverImage.large`
and nothing else: a node whose cover had arrived by a thinner route kept its
smaller `large` in the detail hero even after the response had handed over the
460px `extraLarge`, and a tint only the detail response knew about was dropped, so
a node could end up with neither the bigger cover nor a colour. The merge is
field-wise now, detail winning only where it has a value; `RelationGapTest` pins
both directions — the upgrade, and the refusal to downgrade a cover the node
already had.

### The Edit form's Save button sat below the fold

"Save changes" and "Delete" were the last row of a form — Status, Progress, Score,
both dates, Private, then the message and the buttons — inside the *sheet's*
scroll. The form is around 640dp tall, so on a 640dp-tall screen the primary
action was roughly 170dp below the fold: a reader who had just changed a score had
to scroll to find the button that keeps it, and a form whose primary action is
only reachable by scrolling reads as a form with no action at all.

The fields scroll inside the tab now and the action row is pinned below them,
drawn as the same outlined card as the rows above it. The row also says which
state it is in: the entry AniList confirmed is held alongside the draft
(`EntryEditState.markSaved`), so "No changes to save yet" shows instead of a
button that would send a whole-entry snapshot to change nothing, and a save's
message is cleared by the next edit rather than surviving it — "Saved to your
AniList list" used to stay up while the score underneath it had already changed.

### Motion style was invisible to composition

`ExpressiveMotion.speed` was a plain `var`, written from `AniSequelTheme`'s
composition body and read by every token (`FastSpatial`, `FastColorEffects`,
`sectionReveal`, `bouncyPress`). A read of a plain variable subscribes to nothing,
so changing Motion style reached only whatever recomposed afterwards: the app kept
the springs it had been composed with, `SMOOTH` went on overshooting, and `INSTANT`
sped up the next rebuild rather than the app on screen.

It is snapshot state now, written from a `SideEffect` so the write is a
commit-phase fact rather than something a discarded composition can leave behind.
The navigation bar's selected item also springs to its width (`animatedItemWeight`,
`FastSpatial` — the same physics as the label's `FastSpatialSize` unfurl) instead of
taking it in one frame while its label grew into a slot that had already finished
growing.

### The Info page rebuilt its derived lists on every pass

`genreList`, `weightedTags.mapNotNull { … }`, `relatedMedia`, `characterList`,
`alternativeTitleRows`, `hasProgressCard` and `hasRankings` are all *getters* that
walk, filter or sort, and the page read them in its body — so every pass over the
body rebuilt all of them. The body recomposes several times inside one open: the
first frame, the `isDetailLoading` flip, the detail arriving, and the floating
bar's appearance as the hero collapses past its threshold. One `remember(media)`
holds the lot (`InfoSections`), which also collapses four `isNotEmpty()` questions
into flags.

### The anime colour layer re-themed the app twice per open

Opening a title with the anime colour layer on wrote the layer in two DataStore
transactions — `setAnimeThemeActive(true, hex)`, then `setCustomColorHex(hex)` for
Retain. Each transaction emits a new `ThemeSettings`, and a new `ThemeSettings`
rebuilds the scheme behind the whole app, so the open recoloured everything twice:
once when the anime colour reached the scheme and once when the retained accent
arrived a frame later, both inside the sheet's entrance. `setAnimeThemeActive`
takes the retained accent as a third key and writes one transaction, so the
recolour is one wave. The layer's lifetime is unchanged.
