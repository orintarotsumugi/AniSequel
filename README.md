# AniSequel

An Android app that reads your AniList completed-anime history, walks each
franchise's relation graph, and tells you what came next that you never got
around to — then adds it to your Planning list in one tap.

Not on the Play Store. Distributed as a signed universal APK from
[GitHub Releases](https://github.com/LOST-4EVER/AniSequel/releases), with an
in-app updater.

This file is about **how the app works and why it is built that way**. Three
others share the job, and each is linked from here because knowing which one to
open is most of the work:

| Document | For |
| --- | --- |
| [ARCHITECTURE.md](ARCHITECTURE.md) | The exhaustive spec: every package, layer-by-layer deep dive, sequence diagrams and the multi-module blueprint |
| [AGENTS.md](AGENTS.md) | The rules. Every one exists because the obvious alternative cost something |
| [TO-DO.md](TO-DO.md) | Deliberately unimproved things, each with the reason it was left |

---

## Table of contents

- [What it does](#what-it-does)
  - [The profile screen](#the-profile-screen)
  - [The tier list](#the-tier-list)
  - [Home-screen widgets](#home-screen-widgets)
  - [Release notes](#release-notes)
  - [Deliberate non-goals](#deliberate-non-goals)
- [How the update flow works](#how-the-update-flow-works)
- [Architecture](#architecture)
- [Building it](#building-it)
- [Testing](#testing)
- [Release signing](#release-signing) — **read this before touching the keystore**
- [Continuous integration](#continuous-integration)
- [Project layout](#project-layout)
- [Contributing](#contributing)
- [License](#license)

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
  artwork where a shape earns its keep, dynamic colour — drawn from the
  wallpaper, or from an anime's cover art when you view it — and six theme
  palettes.
- **Edit your own entry.** The second destination of the title page's capsule
  moves a title between Watching, Reading, Rewatching, Completed, Planning,
  Paused and Dropped, sets progress, score and both dates, and marks the entry
  private — staged locally and sent as one write, so nothing reaches AniList
  until "Save changes".
- **Home-screen widgets.** Four tiles — your recent activity, missed sequels,
  arriving sequels, and one featured recommendation — drawn by the system
  launcher from the data the app already has.
- **Release notes in the app.** "What's new" in Settings → Info reads
  `assets/changelog.json`, the same file the release workflow writes the GitHub
  release body from.
- **Self-contained updater.** No update library and no Play Services.

### The title page

What a tap on a title opens, and the answer to "what is this show" in one scroll:
the key visual blurred behind its own title, the viewer's progress, the synopsis
through the app's AniList-markdown renderer, the production facts, genres, the
weighted thematic tags, the cast with their voice actors, the crew, the rest of the
franchise, and the things the app can do with the title. Editing the viewer's entry
is the page's second destination, on a floating capsule at the bottom, because
editing is a mode rather than a section of the page.

It replaces three tabs — Story, Specs, My Entry — which had split one title's
information across two of them. Four decisions in it are worth knowing before
changing it:

- **It is a `LazyColumn`, and the scroll drives the motion.** The hero's parallax
  and the floating bar read the list's scroll state: the parallax through a
  function called inside a `graphicsLayer` lambda (draw phase, so a 120Hz scroll
  touches no layout), and the bar through a `derivedStateOf` that produces a
  `Boolean` (so it recomposes on the crossing rather than on every frame). As the
  hero goes: the backdrop lags the page and dims, the title lockup lifts away
  against it, and the two hand over to the docked bar, which slides in on the
  spatial spring. That handover is timed by three constants that live together in
  `InfoMotion.kt`, because it is a relationship between two files rather than a
  rule either of them can break alone.
- **The staggered entrance is capped, one-shot, and skipped on `INSTANT`.** Seven
  sections at 55ms each is 385ms of stagger; the rest arrive together.
  `sectionReveal` returns no modifier at all when the motion style is `INSTANT`,
  because the delay *is* motion. Its "already played" flag is `rememberSaveable`,
  not `remember`: the `LazyColumn` keeps an item's saved state under its key after
  disposing the item, and the plain `remember` version replayed the whole stagger
  on the way back up the page.
- **The wavy progress bar is drawn, not a `LinearWavyProgressIndicator`.** The
  height of that wave is the progress, which the M3 component's parameters cannot
  express; its geometry lives in `domain/motion/WaveGeometry.kt` so the shape is
  asserted by `WaveGeometryTest` rather than eyeballed. Its travelling phase is one
  `rememberInfiniteTransition`, gated on there being progress to show, and its fill
  springs from zero once per title.
- **The data it needs was widened, not faked.** `GET_MEDIA_DETAIL` now also asks
  for `characters` (with `voiceActors`), `staff` and `relations` — one request per
  open, on a query that already fetched the synopsis, rankings and tags. The
  relation row is filtered to anime, because AniList also returns the novel a show
  was adapted from and its guidebook, which next to the sequels reads as a mistake.

### The profile screen

Five tabs: Home (identity, bio, pinned favourites), Activity (calendar and
paginated list history), Stats, Library, and Social. Each keeps its own scroll
position across a tab switch, because `AnimatedContent` disposes the tab it
animates away from and anything remembered inside it dies with it.

**A profile paints after one request.** The overview — identity, bio,
favourites, and AniList's own aggregates — is published the moment it arrives.
The media list is merged in behind it, because it is one request per 500 entries
and only two of the five tabs read it; the profile used to wait for the whole
download before drawing a bio that had already come back. Between the two
publishes `UserOverviewUiState.Success.isLibraryLoading` is true, and Stats and
Library render "still counting" rather than a tally of zero — an empty list is a
real answer for an account that tracks nothing, so the flag, not the emptiness, is
what the screen reads.

**Bios** are AniList-flavoured Markdown, rendered by
`ui/components/markdown/`: headings, lists and checklists, code, blockquotes,
spoilers (`~!like this!~`), inline GIFs, `~~~a centred line~~~`, AniList links
lifted into cover cards, and images. A bio's cover cards cost **one** request:
see `MediaCoversQueryTest` for why that document asks through a `Page`, and what
used to make AniList reject it. Two deliberate gaps: tables and
`(video)`-style embeds are only partly AniList-documented, so they are parsed
conservatively, and underline has no working AniList syntax (`<u>` is accepted
anyway rather than dropped, since dropping it would print the tag).

**Library** browses every anime and manga on the account as a poster grid with
per-status counts and search. It costs no extra requests: the Stats tab already
forces the whole anime list to be fetched and chunk-merged, because AniList
publishes no aggregate for country of origin, release year, watch year or episode
counts, and the screen used to discard all of it once it had tallied what it
needed. Manga is the one half nothing else needed, so it is fetched the first
time somebody filters to it and then cached.

### The tier list

`domain/tier/` holds the rules, the geometry, the storage format and the exporter
as pure Kotlin with no Android or Compose imports, so the whole arrangement is
testable without a device. The profile's top bar opens it from a small trophy
beside the name — and **only on your own profile**: the ranking is stored against
the signed-in account, so it means nothing on somebody else's, which is why the
control is hidden off your own rather than shown and re-explained.

#### Lists are saved projects, and the screen opens on them

The trophy opens a **library**: the lists you have, with the one you opened last
offered to carry on with, a button for a new empty one, and rename/delete on every
row. Picking one opens its board. There is one route and one piece of state behind
that choice (`opened == null` is the library), so a deep link, a restored back
stack and the system back gesture cannot disagree about where they are.

Each list is a `TierListDocument` — an id, a name, its tiers and its placements —
stored under its own key, with an index of ids beside it. That is deliberate:
`Preferences` is an unordered map, so enumerating its keys would hand the library
whatever order the file happened to be written in, and an interrupted delete would
leave a document behind that still looked like a list. Writes are per document, so
editing one list cannot rewrite another, and every edit is one `edit` transaction
— a removed tier and the placements it drops are no longer two writes that could
be separated by a crash. The list that existed before this change is migrated into
the library once, from the two legacy keys, which are then left in place as the
only copy of an upgrade that cannot be repeated.

#### Every edit is undoable, and the undo is the previous document

The ViewModel keeps a bounded stack of the documents as they were. A snapshot
rather than a delta, because a delta has to be inverted correctly for a move, for
a removal that dropped entries filed under the removed row, and for a seeding — and
the one that is inverted wrong corrupts the list silently instead of failing.

The snapshot is read *inside* the store's write transaction, which is what makes
it exactly the state the edit replaced: a snapshot taken by the caller can be one
write behind, and undo would then jump back past work the person wants to keep.

#### Dragging is positional, and reaches past the screen

A drop names a tier **and** a tile to go above, so dropping a poster between two
others means what it looks like. A caret marks the seam it will land on. Holding a
tile within 72dp of the top or bottom of the viewport scrolls the screen at up to
9dp a frame, scaled by how close the finger is, and the ghost is translated by how
far the board has moved since the tile was picked up — without that half, the tile
slides out from under the finger exactly when the drag is longest.

One layout is computed and intended for several renderers, rather than each
arranging the list for itself — three independent arrangements is how an export
ends up with the labels in a different order from the screen it came from.

Export is **SVG**, and it is lossless rather than a screenshot: every glyph is
still text and every rule is still a rectangle, so the file is sharp at any size.
Posters are embedded as base64 rather than linked, which costs roughly 40-60KB
each and buys a file that opens correctly in every viewer and every app it is
shared into — a linked poster is blocked or skipped often enough that a tier list
would otherwise arrive as a wall of empty tiles.

### Home-screen widgets

Four tiles, each answering one question without opening the app:

| Widget | Answers | Tapping it opens |
| --- | --- | --- |
| AniSequel | How much have I logged recently — avatar, handle, latest title | Profile → Activity |
| Missed Sequels | How many unwatched sequels are waiting, and the top one's poster | The dashboard |
| Arriving Sequels | How many are airing or announced soon | The dashboard, arriving section |
| Featured Sequel | One recommendation, given a whole poster — and what it is a sequel *to* | The detail sheet |

They are `RemoteViews`, not Compose, which is the one place in the app where
layout is XML: the launcher inflates them in its own process, so a `Composable`
would not be reachable. `res/layout/widget_*.xml` and the three `res/drawable/
widget_*.xml` shapes are therefore the **only** XML in the UI, and the
"vectors only" rule in [APK size](#apk-size) holds for the Compose surface, not
for these.

Three decisions are load-bearing:

- **The provider *is* the receiver.** There was once an `AniSequelWidgetReceiver`
  in front of each provider, and it read only `EXTRA_APPWIDGET_ID` — the system
  sends the update list as `EXTRA_APPWIDGET_IDS`, so the extra was almost always
  absent and a freshly placed tile never received its first update at all.
  `AppWidgetProvider` already unpacks both forms, so extending it is the whole
  fix and there is no forwarding class left to get wrong.
- **The shared update path is an `object`, not a base class.** The four
  providers differ in exactly one thing: which layout they inflate and which
  data they read. The placeholder render, the off-main-thread cover fetch and
  the "always finish the pending result" contract were copy-pasted between them
  verbatim, so a fix to the fetch had to be made four times and a mistake in one
  copy shipped quietly in the other three. `WidgetUpdater` holds it — as an
  object because `WidgetDeclarationTest` reads each provider's source and
  requires the literal `: AppWidgetProvider()`, which a base class would hide.
- **Every instance is painted immediately, without a cover.** The count and
  caption are data the app already has; drawing them first is what stops a new
  tile from sitting blank while a bitmap is fetched.

`exported="false"` with no permission: the system broadcast is delivered either
way and nothing outside the app needs to send one. The old
`android.permission.BIND_APPWIDGET` declaration was in no Google sample and
restricted the declaration for no gain.

### Release notes

`app/src/main/assets/changelog.json` is the single source of the app's release
notes, and it has two consumers: the "What's new" card in Settings → Info reads
it from inside the APK, and the release workflow reads the same file from the
checkout to write the GitHub release body. Two consumers, one source, so they
cannot drift — which is the whole point, because the notes used to be a
hardcoded `listOf` in the Info tab that still described v1.0.21 features at
v1.0.23, with no version attached to check it against.

**The card shows the newest block in the file, not the block for
`BuildConfig.VERSION_NAME`.** An exact lookup looks more correct and cannot work:
the workflow *derives* the version it publishes by advancing the patch number
past whatever release is newest, so a repo at 1.0.23 publishes as 1.0.29 and no
changelog file can be written ahead of a number that does not exist until the
build runs. A build one patch ahead of its notes then honestly says "what
changed through 1.2.5" instead of showing an empty card or silently borrowing
the wrong block. `ChangelogTest` pins the newest-first ordering, so "newest" means
new *notes* rather than "whatever is at the top of the file".

Every field is nullable and a malformed file degrades to an empty card, because
this is a hand-edited file read by code that must not throw over a missing comma
and take the Settings screen down with it.

Shipping changes without adding a block is a broken build, not an oversight —
[AGENTS.md §6](AGENTS.md) makes the changelog and the version bump mandatory
together, and `ChangelogTest` and `VersionBaselineTest` enforce it.

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

### Two directories, and why the FileProvider is part of this

An update is handed to the installer as a `content://` URI, because the
installer runs as a different app and cannot read this app's private storage by
path. That URI is minted by a `FileProvider`, and what it may publish is declared
in `app/src/main/res/xml/update_file_paths.xml`.

So the directory the updater downloads into and the directory that file declares
are a pair, and nothing in the build connects them. They came apart in #61, when
downloads moved out of `cacheDir` — deliberately, because the system may empty
the cache whenever it likes, and a download that vanished during the trip to
Settings had to be paid for twice — and the provider's paths stayed behind
pointing at the now-empty cache directory. `getUriForFile` then threw on every
downloaded APK, `openInstaller` contained the throw (correctly: the last step of
an install must not crash the app), and every user on every device was told
**"This device has no app installer."** A bug in the app, reported as a fact
about their hardware.

`UpdateFileProviderPathsTest` reads that XML and checks it against
`UPDATE_DOWNLOAD_DIR`, so the pair cannot drift apart again.

### Interrupted downloads continue

The transfer writes `anisequel-<version_code>.apk.part` and only renames it into
place once the bytes have been read back and vetted, so an interrupted transfer
can never leave a truncated APK for the installer to choke on.

It used to delete that `.part` on every failure and start again from zero. Now it
asks for the rest:

- a `206` continues from the bytes on disk, and the progress bar starts from
  where the transfer is rather than from zero;
- a `200` answering a ranged request means the server ignored the range and is
  sending the whole file again — the `.part` is discarded and the transfer
  restarts, because appending would produce a valid-looking zip of the wrong
  bytes;
- a `416` means the `.part` is longer than the file, so it cannot belong to this
  asset; it is deleted and the single retry starts clean.

`ApkDownloader` moves the bytes; `UpdateManager` judges them (zip magic, package
name, signature) and is the only thing allowed to rename the file.

The progress line also carries a speed, and it is measured over a rolling window
rather than averaged across the whole transfer — so it reports the connection you
have *now*. An average is dominated by the part of the transfer that already
happened, which is exactly the number that is no longer true.
`TransferRateTest` pins the window.

### A download that cannot be installed stays downloaded

When `openInstaller` failed, the app used to discard its record of the file it had
already fetched and close the pop-up, so the update looked gone and the only way
back to it was a fresh check. The APK is still on disk and still verified, so the
failure message now sits above the button that tries again rather than replacing
it. `UpdateDownloadStateTest` covers the same ground across a relaunch.

This is the same instinct as the cache keyed by release version: the expensive,
already-verified thing should survive a failure of something downstream of it.

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
  widget/    AppWidgetProviders + RemoteViews      (the only XML UI)
domain/      Use cases                             (pure logic, no Android)
  usecase/   sequel detection, arriving, insights
  tier/      tier-list rules, geometry, SVG export (no Android, no Compose)
data/
  network/   Retrofit + OkHttp + GraphQL + coalescing
  repository/ AniListRepository, AuthRepository, DataStore preferences
  update/    UpdateManager (check + verify), ApkDownloader (transfer), UpdateManifest
  changelog/ assets/changelog.json -> release notes
ui/components/update/   controller, states, dialogs, permission handling
```

- **UI** — Jetpack Compose, Material 3 Expressive, `StateFlow` + ViewModel. The
  one exception is `ui/widget/`, which cannot be Compose because the launcher
  inflates it in another process — see [Home-screen widgets](#home-screen-widgets).
- **Domain** — `FindMissedSequelsUseCase`, `SaveToPlanningUseCase`,
  `GetViewerProfileUseCase`, `FindArrivingEntriesUseCase`,
  `BuildListInsightsUseCase`. Pure functions over data classes; this is where the
  sequel-detection rules live, and where the tests are densest because that logic
  is exactly what regresses silently. `domain/tier/` holds the tier list for the
  same reason: rules, geometry, storage and the SVG exporter in one testable
  package with no Android imports at all.
- **Data** — Retrofit/Moshi/OkHttp, DataStore preferences, and a separate
  `OkHttpClient` for the update manifest so the AniList auth interceptor can
  never attach a bearer token to a public file.

[ARCHITECTURE.md](ARCHITECTURE.md) is the exhaustive version of this section:
every package, the layer deep dives, the OAuth and scan sequence diagrams, and
the multi-module blueprint this is planned to grow into.

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
| JDK | **21** (Temurin) | Newest LTS that AGP 9.4.1, Gradle 9.6.1 and the Robolectric runner in this repo are all verified against. A newer JDK runs the build and then breaks the JVM test step — which is the one step that is a real gate rather than advisory. |
| Android SDK | compileSdk **36.1**, targetSdk **36**, minSdk **31** | `minorApiLevel = 1` on the 36 line. See [Minimum Android version](#minimum-android-version). |
| Gradle | **9.6.1** | Pinned in `gradle/wrapper/gradle-wrapper.properties` *and* the workflow. It was `'current'` in CI at one point, which meant CI resolved whatever Gradle shipped that week while every local build used the pinned one. It is also the Gradle the committed AGP is green with; the two are one decision, because AGP refuses to start on an older Gradle. |
| AGP | **9.4.1** | The newest stable — and still is. Moving this pin to 9.5.0 on the belief that the line had gone stable is what CI caught with `Plugin [id: 'com.android.application', version: '9.5.0'] was not found`; Google's metadata still lists `9.5.0-alpha10` as the newest, so there is no stable 9.5.0 to move to. An alpha *build system* is a different decision from an alpha UI library — material3 below is pinned to an alpha deliberately and for a written reason, whereas this one gates every build the project makes. |

`settings.gradle.kts` applies the `foojay-resolver-convention` plugin, so a
non-21 JDK does not fail with a bare toolchain error — it provisions one. That is
convenience, not permission to drift: 21 is the version CI runs and the version
the Robolectric step is verified against.

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

There are 59 JVM unit suites and **no instrumented tests** — `androidTest/` does
not exist, so there is nothing to run on a device and no test that needs an
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
| | `LibraryFilterTest`, `MediaStatusLabelTest`, `AnimeInfoSpecsTest` | Library tab logic, status display, the title's spec derivations |
| Title page | `WaveGeometryTest` | The wavy progress bar's amplitude, period and travelling phase |
| | `MediaEntryEditorTest` | The entry editor's writes: the score scale untouched, volumes carried through, and no null for a numeric argument |
| | `ActivityEngagementJsonTest`, `ActivityLikesJsonTest`, `DemoActivityFixtureTest` | Comment threads, like toggles, likers; the demo-mode activity fixture |
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
  [Home-screen widgets](#home-screen-widgets). All three only surface when a user
  drops the tile, and the launcher reports them as *the app is not installed*.
- **`ChangelogTest`** — the release workflow reads `assets/changelog.json` without
  any of this repo's tests running, so the file has to be right on its own. It is
  hand-edited and prepend-only: a version with no written notes renders an empty
  card and a generic release page, and a newest entry that drifts to the bottom
  silently becomes the oldest. Malformed JSON is worse still, because
  `ChangelogRepository` deliberately swallows parse errors so Settings cannot
  crash — the safety net is exactly what would hide a broken file from a release.

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
checkout → JDK 21 → Gradle 9.6.1 → actionlint (validates this file itself)
   ↓
ephemeral key (PR)  |  restore key from secrets (non-PR)
   ↓
validate the key with keytool → resolve version → lint (advisory)
   ↓
cache Robolectric's framework jars → test  ← THE GATE
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
- **Robolectric's framework jars are cached.** They are fetched by Robolectric's
  own Maven resolver *during the test task*, not through Gradle, so they are in
  no Gradle cache and a cold runner downloads ~100 MB of them before the first
  test starts. That download — not the suite — is what makes the test step the
  slowest in the workflow. The cache is keyed on `gradle/libs.versions.toml`,
  which is where the Robolectric version lives, so a bump cannot silently keep
  testing against the previous release's jars.
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

## Project layout

```
AniSequel/
├── app/
│   ├── build.gradle.kts          version baseline, packaging, R8, dependencies
│   ├── proguard-rules.pro        Moshi/Retrofit reflection keep rules
│   └── src/
│       ├── main/
│       │   ├── java/com/anisequel/
│       │   │   ├── data/         network, repository, update, changelog
│       │   │   ├── domain/usecase/  pure sequel-detection logic
│       │   │   ├── domain/tier/     tier-list rules, layout, SVG export
│       │   │   ├── domain/motion/   the info page's wavy-progress geometry
│       │   │   └── ui/            screens, components, theme, viewmodel, widget
│       │   ├── assets/
│       │   │   └── changelog.json   release notes, one source for two readers
│       │   └── res/
│       │       ├── drawable/      adaptive-icon layers + the three widget shapes
│       │       ├── layout/        widget RemoteViews layouts (no Compose lives here)
│       │       ├── mipmap-anydpi-v26/ adaptive icons (no raster fallbacks)
│       │       ├── values/        strings, colours, the system splash theme
│       │       └── xml/           backup rules, FileProvider paths, widget configs
│       └── test/                 60 JVM unit suites (there is no androidTest/)
├── gradle/libs.versions.toml     every version, with the reasoning kept inline
├── AGENTS.md                     the rules for working in this repo
├── ARCHITECTURE.md               the exhaustive system specification
├── TO-DO.md                      the improvement backlog
├── metadata.json                 AI Studio template leftovers, kept minimal
├── update.json                   what the updater reads from the release asset
└── .github/workflows/
```

`applicationId` is `com.aistudio.anisequel.app`, not `com.anisequel`. The Kotlin
package was renamed away from the Android Studio default, but the application ID
is the identity every installed copy answers to and **cannot** be changed:
editing it makes the next release a different app, every existing install
rejects it as foreign, and there is no update path. The same trap as the key
rotation below.

### APK size

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

### Minimum Android version

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
| Coil `if (SDK_INT >= P) ImageDecoderDecoder else GifDecoder` | `ImageDecoderDecoder`; `coil-gif` dropped |

What 31 buys beyond tidiness: Material You works on every supported device, so
the wallpaper theming that was previously "unavailable on some phones" is
universal, and the system splash can be themed without a versioned resource.

Dropping Android 10 and 11 is a real loss of installs. It is accepted deliberately
— a per-API code path is a path that has to be reasoned about forever, and AGENTS.md
already asks whether a resource is reachable on `minSdk` at all.

`UpdateManager` still branches on `TIRAMISU` (33) for `PackageInfoFlags` and
notification permission: those arrived *after* 31 and are still real.

---

## Contributing

Read [AGENTS.md](AGENTS.md) first — it records the rules this repo has learned the
expensive way, and several of them exist because the obvious thing was wrong
once.

The short version:

- Keep files focused, ideally **250–400 lines**.
- No emoji as UI icons. Vectors only.
- Look up docs before adding a dependency, plugin or experimental API.
- Never commit key material, and never rewrite history to hide it.
- Bump the version in all three places and run `VersionBaselineTest`.
- **Add a `changelog.json` block in the same change as the version bump.** The
  release workflow publishes notes it did not write, and the in-app "What's new"
  card reads the same file.
- If you fixed a bug that reached a build, the regression test is the
  deliverable — not the fix.
- `./gradlew test` must be green. Lint will not save you.

Known improvements that are deliberately *not* done are tracked in
[TO-DO.md](TO-DO.md), each with the reason it was left. The exhaustive system
design is [ARCHITECTURE.md](ARCHITECTURE.md).

## License

No license file has been added yet.