# AniSequel — Advanced Architecture & System Design Specification

This specification provides an exhaustive, production-grade technical breakdown of the **AniSequel** codebase. It is authored for software engineers, systems architects, and automated AI coding agents tasked with analyzing, maintaining, extending, or refactoring the application.

---

## 1. System Purpose & Architectural Philosophy

**AniSequel** is a native, offline-resilient Android application built with Kotlin, Jetpack Compose, Coroutines, and Material 3 Expressive UI. The app solves a key problem for anime enthusiasts: identifying unwatched sequels, prequels, OVAs, films, and spin-offs across complex, multi-branch anime franchise relation graphs on [AniList](https://anilist.co).

### Core Philosophy & Operational Invariants

1. **Zero Proprietary Backend**: AniSequel directly interfaces with AniList's public GraphQL API via OAuth2 PKCE or public user queries. There is no middleman backend or database server.
2. **Freshness Over Offline Caching**: Anime lists change constantly as users complete shows or new seasons air. An offline-first database with stale entries produces confident, wrong answers. Consequently, AniSequel uses an in-memory dynamic-TTL cache for fast UI restores paired with foreground staleness detection (`ListFreshnessWatch`).
3. **Pure Clean Architecture & Layer Isolation**:
   - `ui/` — Jetpack Compose presentation, Material 3 Expressive UI, and ViewModels.
   - `domain/usecase/` — Pure Kotlin business logic. Zero Android dependencies (`android.*`).
   - `data/` — Network clients, GraphQL queries, repositories, DataStore preferences, and the standalone updater.
4. **Standalone Self-Updater**: Operating outside the Google Play Store, the app includes a secure, self-contained update engine that fetches release manifests from GitHub Releases, streams APKs, verifies ZIP magic bytes and SHA-256 certificate signatures, and safely hands off to the Android Package Installer via `FileProvider`.

---

## 2. End-to-End Sequence & Data Flow Diagrams

### Diagram A: OAuth2 PKCE Authentication & Session Restoration

```
User / UI                      AuthViewModel                  AuthRepositoryImpl              AniList API / Web
   │                                │                                 │                               │
   │─── Launch App ────────────────►│                                 │                               │
   │                                │─── restoreSession() ───────────►│                               │
   │                                │                                 ├── Read DataStore Token        │
   │                                │◄── Token Present? ──────────────┤                               │
   │                                │    (AuthUiState.Authenticated)  │                               │
   │                                │                                 │                               │
   │─── Tap "Sign in with AniList"─►│                                 │                               │
   │                                │─── getAuthorizationUrl() ──────►│                               │
   │                                │                                 ├── Generate Code Verifier/Challenge
   │                                │◄── Auth URL + Intent ───────────┤                               │
   │                                │                                 │                               │
   │─── Redirect to Web Browser ────┴─────────────────────────────────┼──────────────────────────────►│
   │                                                                  │                               │
   │◄── Redirect Uri with Auth Code (anisequel://oauth/callback) ─────┴──────────────────────────────┤
   │                                                                                                  │
   │─── Process Auth Code ─────────►│                                                                 │
   │                                │─── exchangeCodeForToken(code) ─────────────────────────────────►│
   │                                │                                                                 │
   │                                │◄── Return Access Token ─────────────────────────────────────────┤
   │                                │                                                                 │
   │                                │─── saveAccessToken(token) ─────►│                               │
   │                                │                                 ├── Persist in DataStore        │
   │                                │◄── Emission Confirmed ──────────┤                               │
   │                                │                                 │                               │
   │   UI updates to Authenticated ◄│                                 │                               │
```

---

### Diagram B: Sequel Traversal & Live Filter Pipeline (`FindMissedSequelsUseCase`)

```
MediaListCollection (Raw AniList User Data)
               │
               ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│ Phase 1: discover(collection, criteria, includeHidden)                         │
│                                                                                 │
│ 1. Extract activeOrCompletedMediaIds (COMPLETED, WATCHING, PAUSED, DROPPED)      │
│ 2. Extract plannedMediaIds (PLANNING)                                           │
│ 3. Filter for isWatched(status, episodes, progress)                             │
│ 4. Iterate over parentMedia.relations.edges:                                    │
│    ├── Match edge.relationType against filterCriteria.includedRelations         │
│    ├── Skip if sequelId is in activeOrCompletedMediaIds                         │
│    ├── Skip if sequelId is in hiddenMediaIds (unless includeHidden=true)       │
│    └── Map to MissedSequel(parentId, parentTitle, sequelMedia, relationType, ...)│
│ 5. Deduplicate: distinctBy { it.sequelId }                                      │
└────────────────────────────────────────┬────────────────────────────────────────┘
                                         │
                         Returns List<MissedSequel> Candidates
                                         │
                                         ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│ Phase 2: applyFilters(candidates, criteria, currentYear)                       │
│                                                                                 │
│ Runs in O(N) over candidate list on every search/filter keystroke:             │
│  ├── Text Search: candidate.searchableText.contains(query)                      │
│  ├── Release Status: ALL, FINISHED, RELEASING, NOT_YET_RELEASED                 │
│  ├── Format Filter: TV, MOVIE, OVA, ONA, SPECIAL, etc.                          │
│  └── Year Constraints: sequelReleasedThisYear, parentCompletedThisYear          │
│                                                                                 │
│ Sort Execution (RELEASE_DATE, TITLE_ASC, POPULARITY, SCORE)                     │
└────────────────────────────────────────┬────────────────────────────────────────┘
                                         │
                                         ▼
                        UI Screen Output (Dashboard State)
```

---

### Diagram C: In-App Self-Update & ZIP / Signature Security Flow

```
UpdateController / UI              UpdateManager                     GitHub Asset URL              Package Installer
         │                               │                                 │                               │
         │─── checkForUpdate() ─────────►│                                 │                               │
         │                               │─── Fetch update.json ──────────►│                               │
         │                               │◄── Returns UpdateManifest ──────┤                               │
         │                               │                                 │                               │
         │◄── Available(manifest) ───────┤                                 │                               │
         │                               │                                 │                               │
         │─── Tap "Install" ────────────►│                                 │                               │
         │                               │─── Stream APK to cache ────────►│                               │
         │                               │    (anisequel-<ver>.apk.part)   │                               │
         │                               │                                 │                               │
         │                               │─── 1. Size Guard Check          │                               │
         │                               │─── 2. ZIP Magic Check (PK\x03\x04)│                              │
         │                               │─── 3. SHA-256 Signature Check   │                               │
         │                               │       (vs Pinned 01924c4a...)   │                               │
         │                               │                                 │                               │
         │                               ├── Valid? Rename .part to .apk   │                               │
         │◄── Success(targetFile) ───────┤                                 │                               │
         │                               │                                 │                               │
         │─── Can Request Installs? ─────┼─────────────────────────────────┼───────────────────────────────┤
         │    ├── YES: Open Installer ───┴─────────────────────────────────┴──────────────────────────────►│
         │    └── NO: Open Settings ─────► ACTION_MANAGE_UNKNOWN_APP_SOURCES                               │
         │                                                                 │                               │
         │◄── User grants in Settings, returns to MainActivity (onResume) ─┘                               │
         │                               │                                                                 │
         │─── onAppResumed() ───────────►│                                                                 │
         │                               ├── Read cached downloadedApkFor(manifest)                        │
         │                               └── Launch Installer Intent ─────────────────────────────────────►│
```

---

## 3. Comprehensive Module & Package Structure

```
/ (Workspace Root)
├── ARCHITECTURE.md                          # This architecture specification
├── AGENTS.md                                # Repository rules and constraints for contributors & agents
├── README.md                                # Project overview and build instructions
├── TO-DO.md                                 # Technical backlog and pending extractions
├── build.gradle.kts                         # Root build file
├── settings.gradle.kts                      # Gradle project settings
├── gradle.properties                        # Environment & baseline version definitions
├── update.json                              # Published release update manifest
├── metadata.json                            # Platform metadata for AI Studio sync
├── gradle/
│   └── libs.versions.toml                   # Version catalog with inline architectural reasoning
└── app/
    ├── build.gradle.kts                     # App-level build configuration (compilation, packaging, R8)
    ├── proguard-rules.pro                   # Moshi / Retrofit keep rules
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml          # Permissions, FileProvider, OAuth intent filter
        │   ├── assets/changelog.json        # Release history and feature logs
        │   ├── res/                         # Adaptive vector launcher icon & XML configs
        │   └── java/com/example/
        │       ├── AniSequelApplication.kt  # Global Application class
        │       ├── MainActivity.kt          # Edge-to-edge Activity entry point
        │       │
        │       ├── data/                    # Data Layer
        │       │   ├── changelog/           # Local changelog repository
        │       │   ├── model/               # Data Transfer Objects (DTOs) & domain-adjacent data models
        │       │   ├── network/             # Retrofit, OkHttp, GraphQL documents, interceptors, coalescer
        │       │   ├── repository/          # AniList repository, Auth repo, DataStore preference handlers
        │       │   └── update/              # Self-updater engine and manifest models
        │       │
        │       ├── domain/                  # Domain Layer (Pure Kotlin)
        │       │   └── usecase/             # Business logic use cases (Sequel traversal, Insights, Calendar)
        │       │
        │       └── ui/                      # Presentation Layer
        │           ├── components/          # Reusable Compose components (cards, detail, expressive, markdown, update)
        │           ├── navigation/          # NavHost, transition animation specs, route graphs
        │           ├── screens/             # Top-level screen composables & sub-sections (dashboard, login, profile, settings)
        │           ├── theme/               # Color palettes, Expressive M3 theme setup, typography
        │           └── viewmodel/           # ViewModels, UI state definitions, lifecycle staleness watchers
        └── test/                            # Unit & Robolectric test suite
```

---

## 4. Layer Deep Dives

### 4.1 Data Layer Architecture (`data/`)

#### Network Engine (`data/network/`)
AniList exclusively uses GraphQL over HTTP POST. `AniListApiService` defines the Retrofit entry points.

- **`GraphQLQueries.kt`**: Houses raw GraphQL query string constants. Queries request exact media edge shapes (`relations`, `coverImage`, `startDate`, `nextAiringEpisode`, `mediaListEntry`).
- **`AuthInterceptor.kt`**: Dynamically fetches the user token from `AuthRepository` and attaches `Authorization: Bearer <token>`.
- **`RateLimitInterceptor.kt`**: Detects HTTP 429 status codes from AniList's burst limiter, reads the `Retry-After` header, and applies non-blocking Coroutine delay before retrying.
- **`RequestCoalescer.kt`**: A generic thread-safe query deduplicator (`RequestCoalescer<K>`). If 3 UI components trigger an identical read query concurrently, only 1 network request executes; all callers receive the shared `Deferred` result.
- **Operation Safety**: Read queries are coalesced, but mutations (`ADD_TO_PLANNING`) are **never** coalesced (`isReadOnly(request)` enforces `!MUTATION_PATTERN.containsMatchIn(query)`).

#### Caching & Repository Protocol (`data/repository/AniListRepositoryImpl.kt`)
 AniList limits requests to ~30 per minute. `AniListRepositoryImpl` uses a multi-tier caching strategy:

1. **Chunked List Merging (`fetchFullMediaListCollection`)**: AniList paginates large media collections. The repository automatically retrieves chunks (`chunk = 1..10`), merging entries across lists while deduplicating entries.
2. **Dynamic-TTL List Cache (`listCache`)**: Caches `MediaListCollection` per user (`user:<id>` or `userName:<name>`). The staleness window (`listCacheTtlMillis`) is evaluated per lookup from `RefreshIntervalPreferences`.
3. **Bounded Detail Cache (`detailCache`)**: Holds full `MediaNode` structures (synopses, studio information, trailers). Capped at `MAX_DETAIL_CACHE_NODES = 128` to prevent unbounded memory growth during continuous browsing.
4. **Social & Overview Caches**: Dedicated `ConcurrentHashMap` instances for `UserOverview`, `ListActivity`, `Followers`, and `Following` isolate feed items from media list invalidations.
5. **Session Isolation**: Calling `clearDetailCache()` purges all in-memory maps upon sign-out.

#### DataStore Preferences (`data/repository/`)
- `AuthRepositoryImpl`: Stores OAuth tokens (`access_token`, `refresh_token`) securely in Jetpack DataStore.
- `ThemePreferences`: Persists theme settings (Dark/Light/System) and active color palette selection (6 M3 palettes).
- `RefreshIntervalPreferences`: Stores auto-refresh interval settings (`Always`, `15 min`, `30 min`, `1 hour`, `Manual`).
- `HiddenSequelsPreferences`: Persists a set of media IDs explicitly hidden by the user.
- `ArrivingPreferences`: Persists user toggles for showing/hiding upcoming airing entries.

---

### 4.2 Domain Layer Architecture (`domain/usecase/`)

The domain layer contains zero Android dependencies (`android.*`).

#### Core Traversal Engine: `FindMissedSequelsUseCase.kt`
The traversal engine evaluates franchise graphs using a two-stage approach:

1. **`discover()` — $O(|V| + |E|)$ Graph Walk**:
   - Analyzes all entries across active (`COMPLETED`, `WATCHING`, `PAUSED`, `DROPPED`) and `PLANNING` lists.
   - For every completed entry, iterates over relationship edges (`SEQUEL`, `PREQUEL`, `SIDE_STORY`, `SPIN_OFF`, `ALTERNATIVE`, `PARENT`).
   - Skips media that the user already active/completed.
   - Evaluates hidden media preferences and constructs `MissedSequel` candidate models.
   - Optimized string matching: Relation types are checked case-insensitively using `firstOrNull` against target types to prevent string allocation churn.

2. **`applyFilters()` — $O(N)$ Filtering & Sorting**:
   - Executes live search query string matching over pre-calculated `searchQuery` searchable text.
   - Filters candidates by release status (`ALL`, `FINISHED`, `RELEASING`, `NOT_YET_RELEASED`), media format (TV, MOVIE, OVA, etc.), and release year bounds.
   - Applies sorting options: `RELEASE_DATE_DESC`, `RELEASE_DATE_ASC`, `TITLE_ASC`, `POPULARITY`, `SCORE`.

3. **`splitHidden()`**:
   - Partitioning function that splits candidates into visible and hidden lists, allowing users to view and un-hide previously dismissed sequels.

#### Auxiliary Use Cases:
- `FindArrivingEntriesUseCase`: Extracts upcoming airing episodes for sequels of completed shows.
- `BuildListInsightsUseCase` & `BuildListEntryInsightsUseCase`: Computes franchise completion percentages, genre distributions, and watched metrics.
- `BuildActivityCalendarUseCase`: Transforms user list updates into calendar heatmap representations.
- `SaveToPlanningUseCase`: Triggers the AniList GraphQL mutation to add a target sequel directly to the user's Planning list.

---

### 4.3 Presentation Layer & UI Architecture (`ui/`)

#### Jetpack Compose Navigation (`ui/navigation/AppNavigation.kt`)
`AppNavigation` manages application routes with spatial spring transitions:

```kotlin
// Expressive Spatial Spring Transition Tokens
private val RouteSlideSpring: FiniteAnimationSpec<IntOffset> get() =
    when (ExpressiveMotion.speed) {
        MotionStyle.INSTANT -> snap()
        MotionStyle.SMOOTH -> spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
        MotionStyle.CHILL -> spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)
    }
```

- **Activity-Scoped ViewModels**: The authenticated `DashboardViewModel` is scoped to the Activity using a token key (`"dashboard_${token}"`). This guarantees state survival during device rotation without re-fetching multi-megabyte media collections.

#### Screen & Component Breakdown
- **`DashboardScreen`**: Primary feed. Features header profile summary, search bar, quick filter chips, stats banner, arriving sequels section, missed sequels list/grid, and bottom sheets (`SequelDetailSheet`, `FilterSortSheet`).
- **`LoginScreen`**: Multi-auth entry point supporting OAuth2 PKCE login, public username scanning, and instant Demo mode.
- **`UserProfileScreen`**: Profile hub containing four tabs: **Home** (overview & favorites), **Activity** (timeline & heatmap), **Social** (followers/following), and **Stats** (charts & genre breakdown).
- **`SettingsScreen`**: Configuration hub for themes, palettes, refresh intervals, changelog view, and manual/auto app update management.

---

### 4.4 State Management & ViewModels (`ui/viewmodel/`)

#### `DashboardViewModel` Recompute Pipeline
The dashboard state is managed via a reactive recompute pipeline:

```kotlin
// Simplified Conceptual Flow inside DashboardViewModel
private fun recomputeDashboardState() {
    viewModelScope.launch(Dispatchers.Default) {
        val collection = currentCollection ?: return@launch
        val rawCandidates = findMissedSequelsUseCase.discover(
            collection = collection,
            filterCriteria = filterCriteria.value,
            includeHidden = true
        )
        val (visible, hidden) = findMissedSequelsUseCase.splitHidden(
            candidates = rawCandidates,
            hiddenMediaIds = filterCriteria.value.hiddenMediaIds
        )
        val filteredVisible = findMissedSequelsUseCase.applyFilters(
            candidates = visible,
            filterCriteria = filterCriteria.value
        )
        _uiState.value = DashboardUiState.Success(
            visibleSequels = filteredVisible,
            hiddenSequels = hidden,
            ...
        )
    }
}
```

#### Foreground Staleness Observer (`ListFreshnessWatch.kt`)
`ListFreshnessWatch` works with `LifecycleResumeEffect` in `DashboardScreen`:
- Evaluates staleness on every `ON_RESUME` event.
- If the current time exceeds `lastLoadedTimestamp + intervalMillis`, it triggers a silent background refresh.
- Enforces a **5-minute retry floor** after network failures to prevent rapid retry loops.

---

## 5. Material 3 Expressive System & Motion Mechanics

AniSequel adopts Material 3 Expressive UI standards (`ui/components/expressive/`):

1. **Spring Physics Tokens (`ExpressiveMotion.kt`)**:
   - `FastSpatial`, `FastSpatialInt`: Snappy UI feedback.
   - `BouncySpatial`, `SuperBouncy`: Interactive spring effects.
   - `FastColorEffects`: Color transitions.
   - `PressSpatial`: Touch press down/release scales (`bouncyPress`).

2. **Draw Phase Performance**: All scale, translation, and alpha modifications run inside draw phase lambdas (`Modifier.graphicsLayer { ... }`). This prevents re-running Compose layout and measure phases during active animations.

3. **Capsule Geometry**: Capsule UI elements strictly use `RoundedCornerShape(percent = 50)`. Using `RoundedPolygon.toShape()` on non-square bounds produces stretched ellipses; standard rounded corner shapes preserve clean capsule geometry.

4. **Zero-Raster Vector Icon Policy**:
   - All icons are pure Compose `ImageVector` instances (`AppVectorIcons`, `AppCustomVectors`, `AppExtraVectors`).
   - Zero PNG/WEBP/SVG drawables or emojis are used in the application UI, minimizing APK overhead.

5. **Dynamic Color & Theme Palettes (`ui/theme/`)**:
   - Supports system Material You dynamic colors on Android 12+ (`minSdk = 31`).
   - Offers six handcrafted Material 3 color palettes: `Sakura Blossom`, `Midnight Slate`, `Cyberpunk Neon`, `Forest Matcha`, `Sunset Amber`, and `Ocean Breeze`.

---

## 6. App Self-Updater & Security Architecture

### 6.1 Update Manifest Schema (`update.json`)
The updater reads `update.json` published as a GitHub Release asset:

```json
{
  "version": "1.0.19",
  "version_code": 39,
  "download_url": "https://github.com/.../anisequel-1.0.19.apk",
  "size_bytes": 2621440,
  "changelog": ["Feature updates..."]
}
```

### 6.2 Security & Download Validation Pipeline (`UpdateManager.kt`)

Before handing a downloaded APK to the package installer, `UpdateManager` executes four verification checks:

1. **Declared Size Guard**: Confirms response content length does not exceed `2 * size_bytes` to prevent disk filling attacks.
2. **ZIP Magic Byte Check**: Reads the first 4 bytes of the stream to confirm ZIP header signature (`0x50 0x4B 0x03 0x04`). HTML error pages or text redirects are discarded immediately.
3. **Package Name Verification**: Reads `PackageInfo` from the archive and verifies `packageName == context.packageName`.
4. **SHA-256 Signature Fingerprinting**:
   ```kotlin
   const val OFFICIAL_SIGNER_SHA256 = "01924c4a7503820489802deb6993e5e030103a1a1a36482ecb54688fa3311f82"
   ```
   Compares the APK's signer certificate SHA-256 digest against the pinned official release certificate fingerprint.

### 6.3 Android Installation Handoff (`UpdateInstallation.kt`)
- Generates a secure `content://` URI via `FileProvider` (`xml/update_file_paths.xml`).
- Requests `REQUEST_INSTALL_PACKAGES` permission if ungranted.
- Launches `Intent(Intent.ACTION_VIEW)` with `FLAG_GRANT_READ_URI_PERMISSION` and MIME type `application/vnd.android.package-archive`.

---

## 7. Build System, Versioning & CI/CD Pipeline

### 7.1 Triple Source of Truth Versioning
App versions must strictly match across three files:

```
1. gradle.properties          ──► anisequelVersionName / anisequelVersionCode
2. app/build.gradle.kts       ──► BASELINE_VERSION_NAME / BASELINE_VERSION_CODE
3. update.json                 ──► version / version_code
```

*Enforced by `VersionBaselineTest.kt`.*

### 7.2 Gradle & Dependency Pinning Rationale (`gradle/libs.versions.toml`)

- **`compileSdk = 36.1`** (`minorApiLevel = 1`)
- **`minSdk = 31`** (Android 12+): Eliminates legacy SDK branch checks.
- **`androidx.compose.material3 = 1.5.0-alpha14`**: Explicitly pinned above Compose BOM.
  - *Reasoning*: Standard `1.4.0` stable includes M3 Expressive theming but lacks components (`WavyProgressIndicator`, `LoadingIndicator`). `1.5.0-alpha16+` requires `compileSdk 37`, while this project compiles on `36.1`. Thus `1.5.0-alpha14` is required.
- **`dex.useLegacyPackaging = true`**: Enforces DEFLATE compression on `classes.dex`, saving ~1.9 MB in final APK size.

### 7.3 Continuous Integration (`.github/workflows/android-release.yml`)

```
Push to main / Tag / PR
  │
  ├── 1. JDK 21 (Temurin) & Gradle 9.3.1 setup
  ├── 2. actionlint workflow validation
  ├── 3. Restore keystore from secrets (KEYSTORE_BASE64, STORE_PASSWORD, KEY_PASSWORD)
  ├── 4. Validate key alias via keytool
  ├── 5. ./gradlew test (THE GATE - Must pass)
  ├── 6. ./gradlew assembleRelease
  ├── 7. Verify APK signature digest via apksigner verify
  └── 8. Publish Release & upload update.json (Branch pushes only)
```

---

## 8. Target Multi-Module Architecture Blueprint (Future Scalability)

To scale compilation speed and team modularity, AniSequel is designed to easily migrate from its single-module structure to the following multi-module layout:

```
                                  ┌───────────────────────┐
                                  │         :app          │
                                  └───────────┬───────────┘
                                              │
                      ┌───────────────────────┼───────────────────────┐
                      ▼                       ▼                       ▼
            ┌──────────────────┐    ┌──────────────────┐    ┌──────────────────┐
            │ :feature:dashboard│    │ :feature:profile │    │ :feature:settings│
            └─────────┬────────┘    └─────────┬────────┘    └─────────┬────────┘
                      │                       │                       │
                      └───────────────────────┼───────────────────────┘
                                              │
                                              ▼
                                    ┌──────────────────┐
                                    │   :core:domain   │
                                    └─────────┬────────┘
                                              │
                                              ▼
                                    ┌──────────────────┐
                                    │    :core:data    │
                                    └─────────┬────────┘
                                              │
                      ┌───────────────────────┴───────────────────────┐
                      ▼                                               ▼
            ┌──────────────────┐                            ┌──────────────────┐
            │   :core:network  │                            │  :core:model     │
            └──────────────────┘                            └──────────────────┘
```

### Module Responsibilities for Target Refactoring:
- **`:core:model`**: Pure data classes (`MediaNode`, `MissedSequel`, `ViewerProfile`). No dependencies.
- **`:core:network`**: Retrofit, OkHttp, GraphQL query definitions, Moshi adapters, interceptors, coalescer.
- **`:core:data`**: Repositories, DataStore preferences, update manager implementation.
- **`:core:domain`**: Pure Kotlin use cases (`FindMissedSequelsUseCase`, `SaveToPlanningUseCase`).
- **`:core:designsystem`**: M3 Expressive theme tokens, colors, shapes, spring motion constants, custom vector icons.
- **`:feature:*`**: Isolated feature modules containing Compose screens and ViewModels.

---

## 9. Test Suite Matrix & Quality Assurance

The codebase includes an extensive JVM unit test suite running on Gradle (`./gradlew test`):

| Test Suite | Coverage & Purpose |
| :--- | :--- |
| `FindMissedSequelsUseCaseTest` | Sequel discovery algorithm, relation edge filters, planning overrides. |
| `FindMissedSequelsUseCaseFilterTest` | Search filtering, format filters, year constraints, sorting algorithms. |
| `RelationGapTest` | Detection of gaps in multi-season franchise relation trees. |
| `MissedSequelCachingTest` | In-memory candidate caching correctness and invalidation logic. |
| `AniListJsonTest` | Moshi GraphQL response JSON deserialization correctness. |
| `AniListErrorMappingTest` | Network error classification (`AniListErrorKind`). |
| `RequestCoalescerTest` | Thread-safe concurrent query deduplication correctness. |
| `RateLimitInterceptorTest` | HTTP 429 backoff header parsing and retry logic. |
| `ListFreshnessWatchTest` | Foreground staleness window, timer execution, failure retry floor. |
| `RefreshIntervalPreferencesTest` | DataStore refresh interval persistence and repository TTL synchronization. |
| `AuthRedirectTest` & `AniListOAuthTest` | OAuth2 PKCE code exchange and callback parsing. |
| `UpdateManifestTest` | GitHub update manifest parsing and version comparison. |
| `VersionBaselineTest` | Tri-file version alignment enforcement (`gradle.properties`, `build.gradle.kts`, `update.json`). |
| `KeystoreIntegrityTest` | Ensures zero keystore secrets/binaries are committed to git repository. |
| `ApkPackagingTest` | Asserts `dex.useLegacyPackaging = true` setting in Gradle config. |
| `ExpressiveShapesTest` | Verifies capsule shape math avoiding stretched polygon distortions. |

---

## 10. Rules & Conventions for AI Agents & Developers

When making modifications or adding new features to AniSequel, you **MUST** adhere to the following rules:

1. **File Granularity**: Keep files focused (ideally **250–400 lines**). Split modular components by responsibility into appropriate sub-packages (`cards/`, `detail/`, `dashboard/`, `profile/`, `settings/`, `expressive/`).
2. **Layer Invariants**: Never add Android framework imports (`android.*`) to `domain/usecase/` or core data models.
3. **No Emoji Icons**: All UI icons must be Compose `ImageVector` definitions in `AppVectorIcons.kt`, `AppCustomVectors.kt`, or `AppExtraVectors.kt`.
4. **Spring Physics**: Use shared motion tokens in `ExpressiveMotion.kt`. Do not hardcode custom duration-based tween animations.
5. **Draw-Phase Transforms**: Animate UI scales and translations inside `Modifier.graphicsLayer { ... }` or `bouncyPress`.
6. **Triple-Version Sync**: If you bump the app version, update all three version baseline locations (`gradle.properties`, `app/build.gradle.kts`, `update.json`) and run `./gradlew test`.
7. **Verification Gate**: Always run `./gradlew test` (or `compile_applet`) to verify changes before completing a task.
