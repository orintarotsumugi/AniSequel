# TO-DO

A prioritised list of known improvements. Not a wishlist — each entry says what
is actually wrong, what it costs, and what the first step would be, so the next
person can start without re-investigating.

Ordered by *value per unit of risk*. Anything in §0 is a correctness or security
problem and should jump the queue.

---

## 0. Correctness

### 0.1 The dashboard detail cache has no size bound

`detailCache` is a `Map` in `DashboardViewModel`, cleared on a new list load but
not evicted on its own. A large account fills it with one entry per discovered
candidate and it stays that size for the life of the ViewModel.

Low severity — it is bounded by the account's own completed list, so it cannot
grow without limit from one session — but it is unbounded *by design* rather than
by measurement. An LRU with a sane cap, or a size assertion in a test, would
make the current behaviour deliberate.

---

## 1. Code structure

### 1.1 Split `UpdateManager` — medium effort, mechanical once started

Roughly 420 lines, of which the manifest fetch, the download loop, the
certificate verification and the cache management are four unrelated jobs. This
was left as-is because the signing block depends on Android APIs that need
careful test setup, and there is no local Android SDK in the development
environment to verify a split against.

Suggested split, in dependency order:

| File | Contents |
| --- | --- |
| `ApkSignatureVerifier.kt` | cert extraction, fingerprinting, the pin |
| `UpdateCache.kt` | `downloadedApkFor`, `targetFileFor`, `downloadDirectory`, `clearDownloadedApk` |
| `UpdateManager.kt` | manifest fetch + download streaming |

The cache half is pure file operations and is the easiest to test — a
`TemporaryFolder` and no Android framework at all. Start there.

### 1.2 Split `DashboardViewModel`'s recompute pipeline — medium effort, needs care

The file is ~780 lines including comments, and ~450 excluding them. The public
contract (`DashboardUiState`, `DashboardEvent`) is already extracted to
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

### 1.2 `bouncyPress` is a `composed { }` modifier — medium effort, decent win

`ExpressiveMotion.bouncyPress` uses `Modifier.composed`, which is deprecated in
favour of `Modifier.Node` / `ModifierNodeElement`. The cost is not just the
deprecation warning: `composed` cannot be skipped in recomposition and allocates
its lambda chain per use.

There is a testable stopping point well before a full node implementation — an
`Animatable` held in a `remember` alongside a `graphicsLayer` reads almost the
same and already removes the deprecation. The full `ModifierNodeElement` rewrite
should measure the frame time before and after with a macrobenchmark; there is no
benchmark module in the repo yet.

### 1.3 `UpdateManifest.kt` mixes the model, the comparison logic, the URL and a byte formatter

Small, but it means `formatBytes` (a UI helper) and `isNewerThan` (a policy
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
| `roomRuntime` | 2.7.0 | Migration touches the database layer and needs a real device test |
| `coilCompose` | 2.7.0 | Newer majors changed the API surface |

Upgrade one at a time, with `./gradlew test` green in between, and update the
version table in `README.md` in the same PR.

### 3.4 No benchmark or screenshot-testing module

There is no way to measure an animation change or to catch a UI regression
automatically. A `macrobenchmark` module with one scrolling benchmark plus
Compose screenshot tests on the dashboard and settings screens would turn §2.1
and §2.3 from opinions into measurements.

This is the highest-leverage item in the whole list for the quality of future
animation work, and the most expensive to start.

---

## 4. Product

### 4.1 No in-app way to see what changed between versions

The dialog links to the GitHub release page, which is correct. The manifest's
`notes` field is a build number and commit hash and is deliberately not shown —
see the comment in `UpdateDialogs.kt`.

Worth adding a real changelog asset, so "What's new" does not require a browser.

### 4.2 Scan progress is not reported

Scanning a large account walks every relation and the UI has no progress state
for it. `DashboardUiState.Loading` carries a static message. A real progress
model would need the scan to emit intermediate results, which is a domain-layer
change — related to §1.1.

### 4.3 `metadata.json` advertises a capability the app does not have

```json
"majorCapabilities": ["MAJOR_CAPABILITY_SERVER_SIDE_GEMINI_API"],
"requestFramePermissions": []
```

There is no Firebase or Gemini dependency on the classpath and no
`com.google.firebase` import anywhere in `app/src/main`. Every Firebase entry in
`app/build.gradle.kts` is commented out, and `gradle.properties` says outright
that "no Firebase dependency is live".

A marketplace listing that declares a server-side AI capability the app does not
implement is a bad look at best and a rejection at worst. Either wire the feature
or remove the declaration.

### 4.4 The Secrets Gradle plugin is configured for nothing

`com.google.android.libraries.mapsplatform.secrets-gradle-plugin` is applied in
`app/build.gradle.kts` and given a `secrets { propertiesFileName = ".env" }`
block — but no code reads any secret. There is no Maps API key in the manifest
and no `MAPS_API_KEY` reference anywhere.

The plugin is there for a Maps integration that does not exist. Removing it also
removes the `.env` / `.env.example` convention that a contributor has to be told
about and that currently guards nothing.