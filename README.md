# AniSequel

An Android app that reads your AniList completed-anime history, walks each
franchise's relation graph, and tells you what came next that you never got
around to — then adds it to your Planning list in one tap.

Not on the Play Store. Distributed as a signed universal APK from
[GitHub Releases](https://github.com/LOST-4EVER/AniSequel/releases), with an
in-app updater.

---

## Table of contents

- [What it does](#what-it-does)
- [How the update flow works](#how-the-update-flow-works)
- [Architecture](#architecture)
- [Building it](#building-it)
- [Testing](#testing)
- [Release signing](#release-signing) — **read this before touching the keystore**
- [Continuous integration](#continuous-integration)
- [Project layout](#project-layout)
- [Contributing](#contributing)

---

## What it does

- **Franchise traversal.** Walks AniList's `relations` graph rather than
  trusting a "next season" field, so it catches renamed continuations, films,
  OVAs and spin-offs that a simple title match would miss.
- **Your profile, or somebody else's.** Scan a public AniList username without
  signing in. Read-only, and the UI says so before the mutation fails.
- **One-tap Planning sync.** OAuth against your own AniList account. There is no
  AniSequel account and no password to manage.
- **Filters and hiding.** Sort, filter by relation type and status, search, and
  hide entries you deliberately do not want to be reminded about — with undo.
- **Material 3 Expressive UI.** Spring physics throughout, `RoundedPolygon`
  artwork where a shape earns its keep, dynamic colour, and six theme palettes.
- **Self-contained updater.** No update library and no Play Services.

### Deliberate non-goals

- **No offline-first database.** Every scan is a fresh read. Caching AniList
  lists well is a larger project than this one, and a stale list here produces
  confidently wrong answers, which is worse than no answer.
- **No notifications.** The app offers an update when you open it. A background
  poller would be a battery and privacy cost for a feature a sideloaded app does
  not need.

---

## How the update flow works

This is the least obvious part of the app, so it is worth writing down.

### Where the manifest comes from

The app reads `update.json` from
`releases/latest/download/update.json` — a **release asset**, not a file on
`main`. That is not a stylistic choice:

- `main` is protected and requires status checks. GitHub does not run workflows
  for pushes made with `GITHUB_TOKEN`, so those checks can never report on such a
  commit, and the hook rejects the push.
- The push step used to end in `|| echo "Nothing to push."`, which turned that
  rejection into a *green* step.

The result was four releases (v1.0.15–v1.0.18) published while the manifest on
`main` still advertised v1.0.14. Because `version_code` is what gets compared,
an app already on 35 correctly decided "not newer" — so no user was ever offered
the releases containing the fixes. The updater was not broken so much as deaf.

A release asset has none of those problems: written by the same authenticated job
that publishes the APK, needs no branch write, and `releases/latest` tracks the
newest non-draft release so the URL is stable while its contents move forward.

### What happens when you press Install

```
press "Install"
      │
      ├─ permission already granted?  ──yes──► download ──► open installer
      │                                                  (reuse cached APK if
      ▼                                                   the version matches)
  download the APK anyway
      │
      ├─ permission granted? ──yes──► open installer
      ▼
  NeedsInstallPermission  ──"Open settings"──►  ACTION_MANAGE_UNKNOWN_APP_SOURCES
      │
      │  user grants it, comes back  ──►  MainActivity.onResume()
      ▼
  UpdateController.onAppResumed()  ──►  re-checks  ──►  opens the installer
                                        on the APK already on disk
```

Four decisions are load-bearing here:

1. **The download happens before the permission check.** The user has already
   asked to update. Refusing to start over a setting they may have granted since
   last launch is a worse outcome than downloading and then asking.
2. **The first permission prompt is never unprompted.** It is only ever raised
   in response to the user asking to update. `REQUEST_INSTALL_PACKAGES` is
   declared in the manifest, but declaring it does not grant it — it only puts
   the app on the list of packages allowed to ask.
3. **The grant is observed, not polled.** Nothing in this process is told when
   the user flips a switch in another app's Settings screen. The resume is the
   only signal, which is why `MainActivity.onResume()` hands it to
   `UpdateController.onAppResumed()`. Without that, the user grants the
   permission, returns, and is asked to press a third button for something they
   already requested.
4. **The APK is kept across the Settings round trip.** The cache is keyed by
   release version (`UpdateManager.downloadedApkFor`), so the trip costs one
   download rather than two. Every download run used to wipe the directory
   first, which meant going back to Settings could cost the whole transfer again.

Opening the installer unprompted on resume is safe in the sense that matters:
the system install dialog still requires the user's own confirmation, and no
version of this app can install itself silently.

### What the updater will not do

It cannot install without a user-visible confirmation — that has been true since
Android 8, and it is a platform guarantee rather than a choice made here.

Before a downloaded file is handed to the installer it must be a zip
(`PK\x03\x04`), must match this app's package name if readable, and must carry
either AniSequel's pinned release certificate
(`01924c4a…1f82`) or the running app's own signing certificate. A release build
refuses anything it cannot positively identify — "we could not read a signature"
is the absence of proof, not a substitute for it.

---

## Architecture

```
ui/          Compose screens + ViewModels          (presentation)
domain/      Use cases                             (pure logic, no Android)
data/
  network/   Retrofit + OkHttp + GraphQL + coalescing
  repository/ AniListRepository, AuthRepository, DataStore preferences
  update/    UpdateManager, UpdateManifest, manifest result types
ui/components/update/   controller, states, dialogs, permission handling
```

- **UI** — Jetpack Compose, Material 3 Expressive, `StateFlow` + ViewModel.
- **Domain** — `FindMissedSequelsUseCase`, `SaveToPlanningUseCase`,
  `GetViewerProfileUseCase`. Pure functions over data classes; this is where the
  sequel-detection rules live, and where the tests are densest because that logic
  is exactly what regresses silently.
- **Data** — Retrofit/Moshi/OkHttp, DataStore preferences, and a separate
  `OkHttpClient` for the update manifest so the AniList auth interceptor can
  never attach a bearer token to a public file.

### GraphQL over REST

AniList only speaks GraphQL, so `AniListApiService` is a Retrofit interface
whose "endpoints" are POSTs to `/graphql` with a query string body. Queries live
in `GraphQLQueries.kt`.

Two client interceptors matter:

- **`AuthInterceptor`** attaches the OAuth bearer token.
- **`RateLimitInterceptor`** absorbs the short spikes AniList's burst limiter
  produces, and `RequestCoalescer` collapses identical in-flight queries — a
  recompute triggered by both a filter change and a detail load must not become
  two round trips.

### Keeping the list fresh

The AniList list is the app's whole input, and it is expensive: one
`MediaListCollection` is the single most expensive request the app makes, against
an API that allows roughly 30 requests a minute. It is also the thing that goes
stale while you are not looking — you finish something in a browser, or an
episode airs, and the dashboard you come back to is wrong.

That collision used to be resolved badly, in the one direction that kept the
dashboard stale: the list was cached for a flat hour, and nothing observed the
app coming back. Android **resumes** a process rather than restarting it, so
closing and reopening AniSequel answered from the same in-memory entry, and the
ViewModel's one `init` load never ran again. Reopening was not a reload, and the
only route to fresh data was the refresh button.

Two mechanisms now share one setting, in **Settings → Edit**:

| | |
| --- | --- |
| `RefreshIntervalPreferences` | DataStore-backed choice: Always, 15 min, 30 min (default), 1 hour, Manual |
| `AniListRepositoryImpl` | reuses a completed list response for that long, then re-fetches |
| `ListFreshnessWatch` | decides *when* to ask, owned by `DashboardViewModel` |

The watch is driven by `LifecycleResumeEffect` in `DashboardScreen`, so it acts
on every `ON_RESUME` and on every return to the destination, and it runs a timer
only while the app is actually in the foreground. Two deliberate consequences:

- **A setting changed in Settings takes effect immediately.** Both the repository
  and the watch read the interval per lookup rather than capturing it, so
  picking "15 min" does not need a restart.
- **The timer is foreground-only, and there is no WorkManager job.** WorkManager
  cannot run periodic work more often than every 15 minutes, is deliberately
  inexact under doze, and would spend AniList's request budget refreshing a list
  nobody is looking at.

`Always` and `Manual` schedule no timer at all — `Always` has a zero window, so a
loop would re-fetch continuously rather than on resume, and `Manual` must not
re-fetch without the gesture. Both are decided entirely by the resume check.
After a *failed* automatic fetch the loop waits a five-minute floor before asking
again, so one dropped request cannot become a tight retry loop.

---

## Building it

### Prerequisites

| Tool | Version | Why that one |
| --- | --- | --- |
| JDK | **21** (Temurin) | Newest LTS that AGP 9.1.1, Gradle 9.3.1 and the Robolectric runner in this repo are all verified against. A newer JDK runs the build and then breaks the JVM test step — which is the one step that is a real gate rather than advisory. |
| Android SDK | compileSdk **36.1** | `minorApiLevel = 1` on the 36 line. |
| Gradle | **9.3.1** | Pinned in `gradle/wrapper/gradle-wrapper.properties` *and* the workflow. It was `'current'` in CI at one point, which meant CI resolved whatever Gradle shipped that week while every local build used 9.3.1. |

### Commands

```bash
./gradlew test              # JVM unit tests — the real gate
./gradlew lintRelease       # advisory; never blocks a release
./gradlew assembleDebug     # debug APK
./gradlew assembleRelease   # signed release APK
```

`assembleRelease` falls back to the debug keystore when no release key is
configured, so a local release build always produces something installable. See
[Release signing](#release-signing) before trusting that output.

### Dependency versions that are pinned deliberately

`androidx.compose.material3` is pinned to **1.5.0-alpha14**, above the Compose
BOM. This is not inertia:

- `1.4.0` stable ships the *theming* half of Expressive
  (`MaterialExpressiveTheme`, `MotionScheme`, `MaterialShapes`) but not the
  component set. `LoadingIndicator`, `ContainedLoadingIndicator` and the
  `Wavy*ProgressIndicators` are 1.5.0-alpha only.
- alpha14 specifically, not a later alpha: from alpha16 onward material3 depends
  on Compose UI 1.11+, which requires compileSdk 37. This app compiles against
  36.1, so every later alpha fails AAR metadata checking.

---

## Testing

```bash
./gradlew test
```

| Suite | Covers |
| --- | --- |
| `FindMissedSequelsUseCase*` | Sequel detection, relation kinds, filter interaction |
| `AniListJsonTest`, `AniListErrorMappingTest` | GraphQL response → model, error classification |
| `RequestCoalescerTest`, `RateLimitInterceptorTest` | Query dedup, burst handling |
| `MissedSequelCachingTest`, `RelationGapTest` | Cache correctness, relation graph gaps |
| `KeystoreIntegrityTest` | No key material committed; workflow reads secrets, not literals |
| `VersionBaselineTest` | The three version copies agree |
| `ApkPackagingTest` | `dex.useLegacyPackaging` is set, and in the dex block |
| `UpdateManifestTest` | Manifest parsing, version comparison |
| `ExpressiveShapesTest` | Capsule geometry is not a stretched polygon |
| `RefreshIntervalTest`, `ListFreshnessWatchTest` | Staleness boundary, foreground re-fetch, timer, retry floor |
| `RefreshIntervalPreferencesTest` | The interval survives storage and reaches the repository |
| `AuthRedirectTest`, `AniListOAuthTest` | OAuth redirect and token handling |

`test` is a **gate**; `lintRelease` is not (`abortOnError = false` in
`app/build.gradle.kts`, which is why the workflow treats it as advisory rather
than claiming otherwise).

### Tests that exist because something broke

- **`ApkPackagingTest`** — v1.0.17 shipped a 4.15 MB APK against v1.0.16's
  2.31 MB. `classes.dex` had actually *shrunk*; it was simply being stored
  uncompressed, because `minSdk ≥ 28` makes AGP skip dex compression. One
  flipped setting, ~1.9 MB, and nothing in review would have looked like a size
  regression.
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

## Continuous integration

One workflow, `.github/workflows/android-release.yml`, on push to `main`, on
tags, on pull requests, and on manual dispatch.

```
checkout → JDK 21 → Gradle 9.3.1 → actionlint (validates this file itself)
   ↓
ephemeral key (PR)  |  restore key from secrets (non-PR)
   ↓
validate the key with keytool → resolve version → lint (advisory)
   ↓
test  ← THE GATE
   ↓
assembleRelease → collect artifact
   ↓
verify apksigner digest (non-PR only)
   ↓
create/update release (non-PR) → publish update.json (non-PR, branch pushes only)
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
- **The manifest commit is reported, not swallowed.** `main` is protected and
  rejects `GITHUB_TOKEN` pushes, so `gradle.properties` and `update.json` still
  need bringing forward by hand after a release. A `|| echo "Nothing to push."`
  used to turn that rejection green, which is how a stale manifest stayed
  invisible.

`update.json`, `gradle.properties` and `app/build.gradle.kts` must all agree.
`VersionBaselineTest` enforces it.

---

## Project layout

```
AniSequel/
├── app/
│   ├── build.gradle.kts          version baseline, packaging, R8, dependencies
│   ├── proguard-rules.pro        Moshi/Retrofit reflection keep rules
│   └── src/
│       ├── main/java/com/example/
│       │   ├── data/             network, repository, update
│       │   ├── domain/usecase/   pure sequel-detection logic
│       │   └── ui/               screens, components, theme, viewmodel
│       ├── main/res/
│       │   ├── drawable/         adaptive-icon layers only
│       │   ├── mipmap-anydpi-v26/ adaptive icons (no raster fallbacks)
│       │   └── xml/              backup rules, FileProvider paths
│       ├── test/                 JVM unit tests
│       └── androidTest/          instrumented tests
├── gradle/libs.versions.toml     every version, with the reasoning kept inline
├── AGENTS.md                     the rules for working in this repo
├── TO-DO.md                      the improvement backlog
└── .github/workflows/
```

### APK size

R8 and `shrinkResources` are both on, with the *optimized* default proguard
file, and there are deliberately **no `-keep` rules on `androidx.compose`** —
adding any would be a large, avoidable regression. What that buys:

- `material-icons-extended` ships ~30,000 icon classes; R8 prunes to the ~49 the
  app actually references. Disabling shrinking would ship all of them.
- All iconography is Compose `ImageVector`. There is not one PNG, SVG or XML
  drawable in the app's own UI, no `res/anim`, no `res/font`.
- `dependenciesInfo.includeInApk = false` keeps dependency metadata out of the
  APK.
- `dex.useLegacyPackaging = true` keeps `classes.dex` DEFLATEd — see
  `ApkPackagingTest` for the 1.9 MB this is worth.
- The launcher is a pure adaptive icon (`mipmap-anydpi-v26`). The density-bucket
  PNGs that Android Studio generates by default were unreachable at
  `minSdk = 29` and were removed.

---

## Contributing

Read [AGENTS.md](AGENTS.md) first — it records the rules this repo has learned the
expensive way, and several of them exist because the obvious thing was wrong
once.

The short version:

- Keep files focused, ideally **250–400 lines**.
- No emoji as UI icons. Vectors only.
- Look up docs before adding a dependency, plugin or experimental API.
- Never commit key material.
- Bump the version in all three places and run `VersionBaselineTest`.
- `./gradlew test` must be green. Lint will not save you.

Known improvements that are deliberately *not* done are tracked in
[TO-DO.md](TO-DO.md).

## License

No license file has been added yet.