# Building, testing and shipping AniSequel

Everything a contributor needs to get from a checkout to a signed APK that an
installed copy will accept as its own update. The rules behind the steps are in
[AGENTS.md](AGENTS.md); why the app is shaped the way it is, in
[NOTES.md](NOTES.md) and [ARCHITECTURE.md](ARCHITECTURE.md).

- [Prerequisites](#prerequisites)
- [Commands](#commands)
- [Dependency versions that are pinned deliberately](#dependency-versions-that-are-pinned-deliberately)
- [Testing](#testing)
- [Tests that exist because something broke](#tests-that-exist-because-something-broke)
- [Release signing](#release-signing)
- [Version numbers](#version-numbers)
- [Continuous integration](#continuous-integration)
- [APK size](#apk-size)
- [Minimum Android version](#minimum-android-version)

---

## Prerequisites

| Tool | Version | Why that one |
| --- | --- | --- |
| JDK | **21** (Temurin) | Newest LTS that AGP 9.4.1, Gradle 9.6.1 and the Robolectric runner in this repo are all verified against. A newer JDK runs the build and then breaks the JVM test step — which is the one step that is a real gate rather than advisory. |
| Android SDK | compileSdk **36.1**, targetSdk **36**, minSdk **31** | `minorApiLevel = 1` on the 36 line. See [Minimum Android version](#minimum-android-version). |
| Gradle | **9.6.1** | Pinned in `gradle/wrapper/gradle-wrapper.properties` *and* the workflow. It was `'current'` in CI at one point, which meant CI resolved whatever Gradle shipped that week while every local build used the pinned one. It is also the Gradle the committed AGP is green with; the two are one decision, because AGP refuses to start on an older Gradle. |
| AGP | **9.4.1** | The newest stable — and still is. Moving this pin to 9.5.0 on the belief that the line had gone stable is what CI caught with `Plugin [id: 'com.android.application', version: '9.5.0'] was not found`; Google's metadata still lists `9.5.0-alpha10` as the newest, so there is no stable 9.5.0 to move to. An alpha *build system* is a different decision from an alpha UI library — material3 below is pinned to an alpha deliberately and for a written reason, whereas this one gates every build the project makes. |

`settings.gradle.kts` applies the `foojay-resolver-convention` plugin, so a
non-21 JDK does not fail with a bare toolchain error — it provisions one. That is
convenience, not permission to drift: 21 is the version CI runs and the version
the Robolectric step is verified against.

## Commands

```bash
./gradlew test              # JVM unit tests — the real gate
./gradlew lintRelease       # advisory; never blocks a release
./gradlew assembleDebug     # debug APK
./gradlew assembleRelease   # signed release APK
```

`assembleRelease` falls back to the debug keystore when no release key is
configured, so a local release build always produces something installable. See
[Release signing](#release-signing) before trusting that output.

## Dependency versions that are pinned deliberately

`androidx.compose.material3` is pinned to **1.5.0-alpha14**, above the Compose
BOM. This is not inertia:

- `1.4.0` stable ships the *theming* half of Expressive
  (`MaterialExpressiveTheme`, `MotionScheme`, `MaterialShapes`) but not the
  component set. `LoadingIndicator`, `ContainedLoadingIndicator` and the
  `Wavy*ProgressIndicators` are 1.5.0-alpha only.
- alpha14 specifically, not a later alpha: from alpha16 onward material3 depends
  on Compose UI 1.11+, which requires compileSdk 37. This app compiles against
  36.1, so every later alpha fails AAR metadata checking.

The second one that looks like a mistake is **KSP 2.3.12 next to Kotlin 2.4.21**.
Since KSP 2.3.0 its versioning is decoupled from the Kotlin version, so the two
move independently and the mismatched-looking pairing is the correct one. The
moment it becomes a red flag is the moment it starts reading like the old
`2.2.10-2.0.2` shape again, which would mean Kotlin and KSP have to be read
together. The Compose compiler plugin, by contrast, *is* versioned from the same
`kotlin` key and moves in lockstep with nothing to keep in step by hand.

Both pins carry their reasoning inline in `gradle/libs.versions.toml`, which is
the only record of why they exist.

---

## Testing

```bash
./gradlew test
```

There are 98 JVM test classes and **no instrumented tests** — `androidTest/`
does not exist, so there is nothing to run on a device and no test that needs an
emulator to pass. Everything below is `testImplementation` and Robolectric or
pure JUnit.

| Area | Suite | Covers |
| --- | --- | --- |
| Sequel detection | `FindMissedSequelsUseCaseTest`, `…FilterTest`, `…CurrentlyWatchingTest` | The core rules, relation kinds, and two filter bugs that produced empty or misleading lists |
| | `RelationGapTest` | Franchise gaps that are not "the next season" |
| | `MissedSequelCachingTest`, `MissedSequelDetailTest` | Memoised derived values; what the card says about progress and air date |
| | `FilterCriteriaNarrowingTest` | The one `isNarrowing` definition the badge, the filtered row and the chip all read |
| | `UnreleasedMediaTest` | What counts as unaired |
| | `FindArrivingEntriesUseCaseTest` | The "Currently arriving" section's derivation |
| Network | `AniListJsonTest`, `AniListErrorMappingTest` | GraphQL response → model; failure classification behind "Try again" vs "Sign in again" |
| | `RequestCoalescerTest`, `RateLimitInterceptorTest` | Identical in-flight queries cost one call; burst handling and `Retry-After` |
| Profile | `UserOverviewLoadOrderTest` | The profile is published before the media list lands, and says so while it waits |
| | `UserOverviewJsonTest`, `UserOverviewQueryTest` | Captured AniList responses, and the documents against what AniList actually serves |
| | `ProfileViewModelTest`, `ProfileBioCollapseTest`, `ProfileTextFormattingTest` | Profile state; the bio card's collapse rule and its non-Markdown fallback text |
| | `BuildActivityCalendarUseCaseTest`, `BuildListInsightsUseCaseTest` | The activity grid's layout; the Stats tab's reshaping of AniList's aggregates |
| | `LibraryFilterTest`, `MediaStatusLabelTest`, `AnimeInfoSpecsTest` | Library filtering logic, status display, the title's spec derivations |
| Title page | `WaveGeometryTest` | The wavy progress bar's amplitude, period and travelling phase |
| Studio pages | `StudioQueryTest` | The one document shape AniList requires: `Studio.media` rather than a filtered `Page`, the 25-title ceiling, and the fields a shelf card and a tap need |
| | `StudioYearShelfTest` | The shelves: year from the start date with a `seasonYear` fallback, newest first, `TBA` on top, in-year order preserved, duplicates dropped |
| | `MediaDetailQueryTest`, `MediaDetailNamespacingTest`, `MediaNodeDisplayTest` | The detail document itself — the `title` it has to ask for, the relation fields it shares with the list query — and what a node prints when the summary it was opened from is replaced |
| | `MediaEntryEditorTest` | The entry editor's writes: the score scale untouched, volumes carried through, and no null for a numeric argument |
| | `ActivityEngagementJsonTest`, `ActivityLikesJsonTest`, `ReplyManagementQueryTest`, `CommentManagementTest`, `DemoActivityFixtureTest` | Comment threads, like toggles, likers, and editing or deleting your own comment; the demo-mode activity fixture |
| Bio Markdown | `AniListMarkdownTest` | Which constructs parse, and which of them AniList writes differently from GFM |
| | `MarkdownMediaPreviewTest` | How long one bio parse may take before the watchdog calls it looping |
| | `MediaCoversQueryTest` | The bio cover batch: one request per profile, the variables it needs, and why every alias goes through a `Page` |
| Auth | `AuthRedirectTest`, `AniListOAuthTest` | Redirect handling, token storage, and the silent OAuth failures |
| Updater | `UpdateManifestTest` | Manifest parsing and the version comparison everything rests on |
| | `ApkDownloadResumeTest`, `TransferRateTest` | 206/200/416 range handling against a `.part` on disk; the speed shown under the bar |
| | `UpdateDownloadStateTest` | The downloaded APK survives a relaunch and a failed installer handoff |
| | `UpdateFileProviderPathsTest` | The declared `FileProvider` paths against the directory actually written to |
| Tier list | `TierListTest`, `TierListSerialisationTest` | The rules and geometry; the on-disk format, which holds somebody's afternoon |
| | `TierListDocumentTest`, `TierListPreferencesTest` | A list as a saved project; the library of documents written one at a time |
| | `TierListPlacementTest`, `TierTrayDropTest`, `TierScreenLogicTest` | Where a dropped title lands, including out of the tray; the screen's un-drawn decisions |
| Freshness | `RefreshIntervalTest`, `RefreshIntervalPreferencesTest`, `ListFreshnessWatchTest` | Staleness boundary, the interval surviving storage, foreground re-fetch, timer, retry floor |
| Preferences & UI | `ThemePreferencesTest` | Appearance settings, and what happens after the switch is flipped |
| | `ExpressiveShapesTest`, `VectorIconsTest`, `BackdropTransformTest` | Capsule geometry is not a stretched polygon; every hand-authored icon's name and 24×24 frame; hero image placement, and the gesture gate that decides whether it takes a drag at all |
| | `EntryEditStateTest` | The entry editor loads once per title, survives tab switches, and keeps a hundred-point score scale and a manga's volumes |
| Build & release | `KeystoreIntegrityTest` | No key material committed; the workflow reads secrets, not literals |
| | `VersionBaselineTest`, `ChangelogTest` | The three version copies agree; the bundled notes are ordered and match the version |
| | `ApkPackagingTest` | `dex.useLegacyPackaging` is set, and in the dex block |
| | `WidgetDeclarationTest` | Each provider's declaration, and the literal `: AppWidgetProvider()` the shared updater's shape depends on |
| | `MinimumSdkReachabilityTest` | No `Build.VERSION.SDK_INT` branch decided below the floor |
| | `ExampleRobolectricTest` | The Robolectric harness itself — if the runner breaks, it should break here first |

`test` is a **gate**; `lintRelease` is not (`abortOnError = false` *and*
`checkReleaseBuilds = false` in `app/build.gradle.kts`, which is why the workflow
treats it as advisory rather than claiming otherwise).

### Tests that exist because something broke

- **`ApkPackagingTest`** — v1.0.17 shipped a 4.15 MB APK against v1.0.16's
  2.31 MB. `classes.dex` had actually *shrunk*; it was simply being stored
  uncompressed, because `minSdk ≥ 28` makes AGP skip dex compression. One
  flipped setting, ~1.9 MB, and nothing in review would have looked like a size
  regression. The rule AGP applies is `minSdk >= 28`, so raising the floor to 31
  does not bring it back — but nothing about that is obvious, which is why the
  test reads `build.gradle.kts` rather than the build output.
- **`MediaCoversQueryTest`** — every AniList preview card in every bio showed the
  placeholder icon and no poster, and it did so from the day the feature shipped.
  The batched cover document declared its variables as `id0: Int` while using
  them as `$id0`, so AniList answered the whole thing with HTTP 400 - and the
  assertion that should have caught it asserted the broken spelling instead. The
  suite now also pins the reason each alias is resolved through a `Page`: the
  same request written against the `Media` root field answers **404** as soon as
  one linked id has no media, which blanked every other card in the bio.
- **`MediaDetailQueryTest`** — v1.3.0 shipped a title page that could open headed
  **Unknown Title**. `GET_MEDIA_DETAIL` did not ask for `title`, on the assumption
  that the summary the sheet was opened from already had one. That holds on the
  dashboard, which folds the response into the list node, and does not hold on a
  profile, where the fetched detail *replaces* the activity row's node — so there
  was nothing left to render. The suite pins the field in the document, the way
  `MediaCoversQueryTest` pins its variable declarations, because a query is the one
  kind of code here that no compiler checks.
- **`VersionBaselineTest`** — the version lives in three places, and two of them
  used to drift apart silently.
- **`KeystoreIntegrityTest`** — see below.
- **`ExpressiveShapesTest`** — `RoundedPolygon` is authored on a square
  perimeter, and `toShape()` stretches it to whatever `Size` it is handed. On a
  tab measured 120×44, `MaterialShapes.Pill` rendered as a 120×44 *ellipse*.
- **`ListFreshnessWatchTest`** — the list was cached for an hour and nothing
  watched for the app returning, so because Android *resumes* a process instead of
  restarting it, reopening AniSequel produced the identical dashboard every time.
  The suite pins the three things that fix had to get right: an overdue list is
  re-fetched on resume, a fresh one is not (twenty resumes inside an interval cost
  zero requests), and a failed fetch cannot become a tight retry loop.
- **`WidgetDeclarationTest`** — v1.0.42 shipped a widget that could not be added
  at all, from three declaration bugs no build step could see: `android:configure`
  pointing at a provider rather than an Activity, a missing `initialLayout`, and
  the `EXTRA_APPWIDGET_ID` / `EXTRA_APPWIDGET_IDS` mismatch described in
  [NOTES § Home-screen widgets](NOTES.md#home-screen-widgets). All three only
  surface when a user drops the tile, and the launcher reports them as *the app is
  not installed*.
- **`ChangelogTest`** — the release workflow reads `assets/changelog.json` without
  any of this repo's tests running, so the file has to be right on its own. It is
  hand-edited and prepend-only: a version with no written notes renders an empty
  card and a generic release page, and a newest entry that drifts to the bottom
  silently becomes the oldest. Malformed JSON is worse still, because
  `ChangelogRepository` deliberately swallows parse errors so Settings cannot
  crash — the safety net is exactly what would hide a broken file from a release.
- **`RelationDetailResolutionTest`** — a relation followed from a title page opened
  a visibly emptier page than the same title opened from the list: "no synopsis
  available", no credits, and the relations row gone after a single hop. The sheet
  did fetch the full record; the subject it drew was the thin relation node the
  card carried, so the answer was thrown away. The guard pins the resolution (at
  the source, because the regression is *which node is handed to the page* — there
  is no Compose harness in the unit-test source set) and pins the merge the
  resolution depends on.
- **`SnapshotStoreTest` / `RepositorySnapshotsTest`** — every cache the app kept
  lived for exactly one process, so each launch re-bought the whole list (one
  request per chunk of it), the viewer's own profile before it could name the
  account, and every title page whose synopsis, cast and studios had already been
  read. The first suite pins the disk tier's contract — a value is served with the
  moment it was *written*, a window is closed at both ends, anything unreadable is
  a miss and never an exception, the cache is bounded — and the second pins the
  policy: a launch inside the windows spends nothing, a mutation clears the disk
  copy of the list too, signing out deletes all of it, and a title page served
  from disk carries the list's progress rather than yesterday's.

---

## Release signing

> **Rotating the release key permanently breaks updates for every existing
> install.** Android refuses to install over an app signed with a different key
> (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`) and there is no undo. Read
> [AGENTS.md §5](AGENTS.md) before you go anywhere near the keystore.

Every release is signed with one long-lived key so Android accepts it as an
in-place update.

**The key is not in this repository.** It lives only in three repository
secrets:

| Secret | Contents |
| --- | --- |
| `KEYSTORE_BASE64` | `base64` of the JKS release keystore |
| `STORE_PASSWORD` | Keystore password |
| `KEY_PASSWORD` | Key password (same value as the store password) |

### History

The key was previously committed as `debug.keystore.base64` through v1.0.16.
Base64 is an encoding, not encryption, so anyone who cloned the repo could
decode it and sign an arbitrary "update" APK — which the in-app updater would
accept, because until recently it only checked that the certificate matched the
installed one.

**The key rotated on 2026-10-05.** Releases up to and including v1.0.16 are
signed by the original certificate
(SHA-256 `c33eceb9…e4ab`). Because that keystore's password cannot be recovered
from the repository, **no in-place update path exists from those installs** —
anyone still on them must install this APK as a fresh download, losing local
data.

Every release from the rotation onward is signed by the certificate pinned at
SHA-256 `01924c4a7503820489802deb6993e5e030103a1a1a36482ecb54688fa3311f82`, and
so is every build made from the secret. **Nobody needs to uninstall when
updating from the rotation onward.**

Git history is deliberately left intact — that is where the old key now lives,
and purging it hides a key that is already compromised without un-compromising
it. Still keep a private, off-git backup: history can be lost, and regenerating
a key does not restore update compatibility.

### Three layers of verification

1. **`KeystoreIntegrityTest`** — in the local and CI unit-test suite. Asserts
   that no key material is committed, that `.gitignore` blocks keystore
   extensions, and that the workflow sources both the key and its passwords from
   secrets rather than literals.
2. **`Restore the release signing key from secrets`** (workflow) — decodes
   `KEYSTORE_BASE64`, fails with an actionable message if any secret is unset,
   and validates the `upload` alias with `keytool`. There is deliberately **no**
   `keytool -genkeypair` fallback: those branches previously turned a repository
   with no secrets into a green build that signed every release with a fresh
   random key, and every user who tried to install one got
   `INSTALL_FAILED_UPDATE_INCOMPATIBLE`.
3. **`Verify APK Release Signing Integrity`** (workflow) — runs `apksigner
   verify --print-certs` on the built APK and compares the certificate digest to
   the pinned fingerprint. Skipped on pull requests, which sign with a throwaway
   key generated inside the runner.

### Building a signed release locally

```bash
export KEYSTORE_PATH=/path/to/anisequel-release.jks
export STORE_PASSWORD='<store password>'
export KEY_PASSWORD='<key password>'
./gradlew assembleRelease
```

---

## Version numbers

The version lives in **three** places and all three must agree:

1. `anisequelVersionName` / `anisequelVersionCode` in `gradle.properties`
2. `BASELINE_VERSION_NAME` / `BASELINE_VERSION_CODE` in `app/build.gradle.kts`
3. `version` / `version_code` in `update.json`

`VersionBaselineTest` enforces it. The workflow derives the version it
publishes by advancing past the newest release and passes it back in as `-P`
properties, which is why a checkout's own value is a *baseline* rather than the
release's name.

After a release, bring 1 and 2 forward by hand: the workflow's commit is
rejected by branch protection because GitHub does not run workflows for
`GITHUB_TOKEN` pushes. Always add the matching `changelog.json` block in the
same change — [AGENTS.md §6](AGENTS.md) makes it mandatory, and
[NOTES § Release notes](NOTES.md#release-notes) explains why the card reads the
newest block rather than the one matching `BuildConfig`.

## Continuous integration

One workflow, `.github/workflows/android-release.yml`, on push to `main`, on
tags, on pull requests, and on manual dispatch. Two jobs, started together: an
advisory lint job, and the release job that runs the gate and publishes.

```
lint (advisory, own runner)           build-and-release
checkout → JDK 21 → Gradle 9.6.1      checkout → JDK 21 → Gradle 9.6.1 → actionlint
   ↓                                     ↓
lintRelease → annotations              ephemeral key (PR) | restore key from secrets (non-PR)
                                         ↓
                                       validate the key with keytool → resolve version
                                         ↓
                                       cache Robolectric's framework jars → test  ← THE GATE
                                         ↓
                                       assembleRelease → collect artifact
                                         ↓
                                       verify apksigner digest (non-PR only)
                                         ↓
                                       create/update release (non-PR)
                                       → publish update.json (non-PR, branch pushes only)
```

Notable properties, each of which was a bug once:

- **A pull request gets a throwaway key**, never the real one — GitHub withholds
  secrets from forks, and a review build has no reason to hold the key that can
  update every installed copy. The two publishing steps are gated on
  `github.event_name != 'pull_request'`, so nothing signed in CI is ever shipped.
- **A PR is told where its APK is.** The APK exists but is unfindable without it,
  so the workflow links it on the PR and edits that comment in place rather than
  posting one per push. It never fails the run over a permissions quirk — a red X
  on an otherwise-good build trains people to ignore this workflow.
- **`actionlint` runs first.** A duplicate YAML key makes the whole file invalid
  and the only symptom is "this run likely failed because of a workflow file
  issue". The workflow validates itself.
- **Robolectric's framework jars are cached.** They are fetched by Robolectric's
  own Maven resolver *during the test task*, not through Gradle, so they are in
  no Gradle cache and a cold runner downloads ~100 MB of them before the first
  test starts. That download — not the suite, which is about twenty seconds — is
  what makes a cold test run slow. The cache is keyed on `gradle/libs.versions.toml`,
  which is where the Robolectric version lives, so a bump cannot silently keep
  testing against the previous release's jars.
- **Lint is its own job, not a step in front of the tests.** `lintRelease`
  compiles the release variant to build its model — about three minutes. In the
  release job those three minutes were serialised ahead of the gate, so a broken
  test waited for lint before the test step even started, and the APK step then
  compiled the release variant again: only that step is given the resolved
  version, and those `-P` flags change `BuildConfig`, an input to both KSP and
  the Kotlin compile. Split onto its own runner, lint runs while the tests do,
  and the gate is reached in about a minute instead of four. Neither job needs
  the other: lint cannot hold up a release, and a failed release does not cancel
  the annotations.
- **A superseded pull-request run is cancelled.** Pushing a fix three times left
  two full builds — R8 over the whole app, plus the tests — finishing behind the
  one that mattered. `cancel-in-progress` is an expression rather than `true`:
  it applies to pull requests only, because branch and tag runs are serialised
  on purpose (two pushes landing together would both mint a release tag and race
  to write `update.json`).
- **The manifest commit is reported, not swallowed.** `main` is protected and
  rejects `GITHUB_TOKEN` pushes, so `gradle.properties` and `update.json` still
  need bringing forward by hand after a release. A `|| echo "Nothing to push."`
  used to turn that rejection green, which is how a stale manifest stayed
  invisible.

`update.json`, `gradle.properties` and `app/build.gradle.kts` must all agree.
`VersionBaselineTest` enforces it.

---

## APK size

R8 and `shrinkResources` are both on, with the *optimized* default proguard
file, and there are deliberately **no `-keep` rules on `androidx.compose`** —
adding any would be a large, avoidable regression. What that buys:

- `material-icons-extended` ships ~30,000 icon classes; R8 prunes them to the
  **54** the app actually references, all of them listed by name in
  `ui/components/AppVectorIcons.kt` so the reachable set stays reviewable.
  Disabling shrinking would ship all 30,000.
- All iconography in the Compose UI is an `ImageVector`. There is not one PNG,
  SVG or XML drawable in it, and no `res/anim` and no `res/font` anywhere.
  The three `res/drawable/widget_*.xml` shapes are the exception, and not by
  preference — a widget is `RemoteViews` inflated by the launcher in its own
  process, so it cannot be a `Composable`.
- `dependenciesInfo.includeInApk = false` keeps dependency metadata out of the
  APK.
- `androidResources.localeFilters += listOf("en")` drops the library locales.
  Every string in this app is in the default `res/values/strings.xml`; there is
  not one translated string in the repository. The other ~100 locales in a
  release APK come from AndroidX, Material and friends, and nothing in this app
  can draw them. If the app is ever translated, the new locale has to be added
  there or its strings are filtered out of the build — which is exactly the
  constraint to remember about that block, and it fails visibly (English UI in a
  translated app) rather than silently. `resourceConfigurations` did this
  before and was deprecated in AGP 8.8.
- The animated-image decoder set is down to the one that can run. `NetworkClient`
  used to register `ImageDecoderDecoder` behind `if (SDK_INT >= P)` and
  `GifDecoder` below it; at `minSdk = 31` that guard is always true, so the
  fallback could not execute on any device this app installs on. The branch and
  the fallback are gone and R8 prunes the unused decoder's classes.
  **`coil-gif` is still a dependency, though** — both decoders live in that one
  artifact (`coil-gif-2.7.0.aar` contains `ImageDecoderDecoder.class`;
  `coil-base` contains neither), so removing it breaks the build rather than
  shrinking the APK. `MinimumSdkReachabilityTest` now fails the build if a
  `Build.VERSION.SDK_INT` branch is decided before the app starts, so the next
  raise of `minSdk` cannot quietly leave one behind.
- `dex.useLegacyPackaging = true` keeps `classes.dex` DEFLATEd — see
  `ApkPackagingTest` for the 1.9 MB this is worth.
- The launcher is a pure adaptive icon (`mipmap-anydpi-v26`). The density-bucket
  PNGs that Android Studio generates by default were unreachable on every
  supported configuration and were removed.

## Minimum Android version

`minSdk = 31` — **Android 12**. Raised from 29 (Android 10) because every API
branch below 31 in this codebase had stopped describing a device the app can be
installed on, and the ones that remained were carrying caveat text rather than
behaviour:

| Was | Now |
| --- | --- |
| `supportsDynamicColor = SDK_INT >= S` | removed; dynamic colour exists everywhere |
| Dynamic-colours switch `enabled = supportsDynamicColor` | removed; always live |
| `canInstallPackages = SDK_INT < O \|\| canRequestPackageInstalls()` | `canRequestPackageInstalls()` |
| `installPermissionSettings(): Intent?` | returns `Intent` |
| `GET_SIGNING_CERTIFICATES or GET_SIGNATURES` | `GET_SIGNING_CERTIFICATES` |
| deprecated `getPackageInfo(path, int)` on API 33+ | `PackageInfoFlags` |
| `PackageInfo.signatures` signer fallback | `signingInfo` only |
| Coil `if (SDK_INT >= P) ImageDecoderDecoder else GifDecoder` | `ImageDecoderDecoder`; `coil-gif` kept, see above |

What 31 buys beyond tidiness: Material You works on every supported device, so
the wallpaper theming that was previously "unavailable on some phones" is
universal, and the system splash can be themed without a versioned resource.

Dropping Android 10 and 11 is a real loss of installs. It is accepted
deliberately — a per-API code path is a path that has to be reasoned about
forever, and AGENTS.md already asks whether a resource is reachable on `minSdk`
at all.

`UpdateManager` still branches on `TIRAMISU` (33) for `PackageInfoFlags` and
notification permission: those arrived *after* 31 and are still real.
