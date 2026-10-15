# TO-DO

A prioritised list of known improvements. Not a wishlist — each entry says what
is actually wrong, what it costs, and what the first step would be, so the next
person can start without re-investigating.

Ordered by *value per unit of risk*. Anything in §0 is a correctness or security
problem and should jump the queue.

---

## 0. Correctness

### 0.1 The tier list's `seeded_for_v1` is written and never read

`TierListPreferences.markSeededFor` records which AniList account has been offered
the "rank from scores" seeding, and nothing reads it back. It was introduced so the
offer could be made once per account, and the UI never did that — the offer is on
the empty state of every list instead, which is arguably the better behaviour now
that lists are documents (a new list *is* a new offer).

Nothing is broken by it: it is one small string, and it has been that way since the
tier list shipped. The cost is that it reads as load-bearing state that a future
reader has to work out is dead. Either use it — the library could mark which of
the person's own lists was seeded for them — or delete the key and the write.

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

The file is ~1120 lines including comments, well past the 250–400 in
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

### 1.3 `bouncyPress` is a `composed { }` modifier, and 44 call sites skip its fix — medium effort

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
Of 56 call sites, only **11** pass a source. The other **44** get the fallback
path, which installs a second `pointerInput` node that sets "pressed" on every
pointer-down before anything knows whether the gesture is a tap or the start of a
fling — so a finger dragged across one of those elements springs it down and
then back: two springs per gesture. Passing the source costs one line per site
and removes the node entirely.

`AnimeTrailerBanner` was one of the 44 and is now fixed; it was the one inside a
scrolling sheet, which is where it is most visible. The rest are single-line
mechanical changes, but there are 44 of them and **no device in the development
environment to check any of them on** — so this is a good candidate for doing in
one pass with a real phone afterwards, rather than one at a time.

### 1.4 `UpdateManifest.kt` mixes the model, the comparison logic, the URL and a byte formatter

129 lines, but it means `formatBytes` (a UI helper) and `isNewerThan` (a policy
decision) sit in the same file as the DTO. Splitting them is cheap and would let
`isNewerThan` get a proper test without constructing a full manifest.

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

---

## 5. Profile UI follow-ups

The profile screen got a visual pass — hero header, summary strip, blurred
activity backdrops, a scan-friendly friends grid, and nested-markdown rendering.
What that pass deliberately did **not** do is listed here so the next person does
not mistake each one for an oversight.

### 5.1 `stripMarkdown` is UI-dead and only its test keeps it alive

`ProfileAboutCard.stripMarkdown` strips Markdown to plain text. No screen calls
it any more — `AboutCard` renders the parsed AST — but `StripMarkdownTest` (the
class inside `ProfileTextFormattingTest.kt`, which is a misleading filename)
still exercises it, so it cannot simply be deleted without removing that suite
too.

Either wire it back in where a single-line preview of a bio is wanted (a search
result, a future social feed row) or delete both the function and its tests in
one change. Leaving a tested-but-unused formatter is how the next reader assumes
some screen is using it.

### 5.2 Blur has no cost measurement on a long activity feed

`ActivityCard` blurs every cover it draws (`Modifier.blur(28.dp)`), and an
activity feed is unbounded — it pages. On a low-end device, a screenful of
blurred layers is the first thing likely to drop frames, and there is no
benchmark module to say whether it does (see §2.3 / §3.4).

First step: measure with `macrobenchmark` on one scrolling test, then either cap
the blur to the visible cards or replace it with a pre-blurred server-side
transform. Do not guess either way.

### 5.3 The hero header does not collapse on scroll

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

### 5.4 Markdown nesting stops at four levels and then degrades to raw text

`AniListMarkdownParser` re-parses an emphasis run's or link label's inner
expression, capped at `MAX_INLINE_DEPTH = 4`. Past the cap the renderer falls
back to the flat source text, so a pathological bio would still print asterisks.

Real bios never get close, and the cap is what prevents unbounded recursion. If
it ever needs raising, raise it with a test that pins the cap rather than
removing the guard.

### 5.5 No screenshot tests for the profile tabs

The five profile tabs — Home, Activity, Stats, Library, Social — are the most
shape-variable screens in the app (they change entirely between an account that
pinned nothing and one that pinned fifty), and nothing asserts what they look
like. Every visual claim in this pass was verified by reading code and comparing
against a reference image, not by a test.

A Compose screenshot test per tab, over the demo fixture `DemoProfileProvider`
already builds, would turn §5.2 and §5.3 into measurements and catch the next
regression in the header the way `ExpressiveShapesTest` catches capsule geometry.

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

This needs a device and a finger, not a JVM test — which is exactly the gap
[§2.2](#22-no-animation-regression-tests) and [§3.4](#34-no-benchmark-or-screenshot-testing-module)
describe. Do not "fix" it by guessing: try candidate 1 first, since it is one
line and reversible, and record which one it actually was here.

---

## Closed

Entries that used to live above and are no longer true. Kept rather than
deleted, because in each case the *reason* is the part worth not re-deriving —
see [AGENTS.md §1](AGENTS.md).

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