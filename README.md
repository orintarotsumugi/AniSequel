# AniSequel

**Your AniList history, read forwards.** AniSequel walks the relation graph of
every anime and manga you have finished and finds the sequels, films, OVAs and
spin-offs you never got around to - then puts the good ones one tap from your
Planning list.

<br>

![Kotlin](https://img.shields.io/badge/Kotlin-2.4.21-7F52FF?logo=kotlin&logoColor=white)
![Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203%20Expressive-4285F4?logo=jetpackcompose&logoColor=white)
![Android](https://img.shields.io/badge/Android-12%2B%20(API%2031)-3DDC84?logo=android&logoColor=white)
![Tests](https://img.shields.io/badge/tests-131%20JVM%20classes-6E56CF)

Not on the Play Store. Every release is a signed universal APK on
[GitHub Releases](https://github.com/orintarotsumugi/AniSequel/releases), with a
self-contained updater in the app - no Play Services, no AniSequel account, no
middleman server.

**Jump to:** [What's new](#whats-new---178) | [Features](#features) | [Install](#install) |
[Build](#build-it) | [How it works](#how-it-works) | [Docs](#documentation) |
[Contributing](#contributing) | [License](#license)

---

## What's new - 1.7.8

One sheet for messages and comments, a bar that stops navigating on a hold, and a GIF search that types the way you do.

- **A message opens in the comment thread.** Tapping a message, or the Message button on a friend, opens that comment thread with the message itself as the first row - so every line can be replied to and quoted, including the message. The old bubbles-and-reply-box sheet is only used to write the first message of a conversation.
- **A thread keeps the place you were reading.** Loading older comments no longer slides the conversation out from under you, quoting and cancelling no longer teleports you to the newest message, and the scroll after you post lands on your comment. The header has a Refresh button and says when the thread was last checked.
- **Holding the navigation bar goes nowhere.** A hold exists to start a swipe; letting go of Profile no longer jumps to Messages.
- **The GIF search types forwards.** The field rebuilt its value on every keystroke and put the caret at the start, so typing `cat` searched `tac`.
- **A GIF search finds the show, not just the tag.** A series name is asked three ways before it is called a miss - your words, your whole phrase as one tag, then your first word as a prefix - because the catalogue files a show under a tag nobody types, and an empty search hands over two rows of tags that are known to answer.
- **A cancelled photo upload actually stops.** The transfer is cancellable now, and the composer cannot be left stuck on Uploading by an upload that was thrown away.
- **Photos attach again while catbox is paused.** Its anonymous uploads are off, so the file falls back to imgbb.com first and pomf.lain.la after that - no account on any of them, and in that order because pomf's files are the ones AniList cannot load.
- **A photo can go in small, centred, or behind a spoiler.** The size and framing row that the GIF pane already had now covers photos picked from the device too, so a screenshot does not have to be a full-width block.
- **The controls under a comment fit.** Heart, Reply, Quote and manage wrap instead of hiding off the edge of a phone, Reply and Quote read as capsules, and the like count animates to its new number.
- **A quote keeps what it quoted** - blank lines survive and a spoiler no longer prints its own `~!...!~` tags, and quoting threads the reply under the person it answers instead of at the root.

The full block-by-block notes live in
[`changelog.json`](app/src/main/assets/changelog.json), which is also what the app
and the GitHub release body read.

---

## Features

### Find what you missed

  walked in both directions, so renamed continuations, films and side stories are
  found even when the title gives nothing away.
  default, or the **Airing now** and **Upcoming** groups in Settings), recent
  activity, lists and stats.
  score and episode floors, a name search, and hidden titles with undo.
  in. Read-only, and the UI says so before a write can fail.

### Read a title completely

  openings and endings, the rest of the franchise, friends' progress, and an entry
  editor on a second capsule.
  filmography; every face opens the character, staff or voice-actor sheet.

### Keep your list

  hold-to-swipe gesture; Tiers is opt-in for the bar and also reachable from the
  Library trophy and the profile.
  or swipe to start watching, from any recommendation or title page.
  add, remove and reorder - the S/A/B/C/D/F template is a default, not a cage -
  and each row carries an editable AniList score band, so one tap seeds the
  whole board from your own scores. Anime and manga rank on separate shelves,
  because AniList numbers them independently and id 1 is both *Cowboy Bebop*
  and *Monster*.
  dragging a poster names a row *and* a position, with a caret marking the seam
  it lands on and edge-scroll reaching past the screen - and dropping it back on
  the tray un-ranks it. Sheets do the same job one-handed, word search and
  status/genre/studio filters narrow the pool, every edit undoes and redoes, and
  a lock turns the board into read-only browsing that still scrolls and zooms.
  captions sit under or on the covers, and export one full-board PNG at up to
  4K - rendered from the same layout the screen drew, not a screenshot of the
  viewport, with the heading's own show/size/font controls and a lossless SVG
  renderer behind it. The lists save as local projects in a library that offers
  to resume the last one you opened.
  followers, and an inbox you can reply from - or dismiss a row out of, and come back to find
  what arrived since you last read marked as new.

### Make it yours

  blurred key visual behind every title, and a theme that can follow the cover you
  are looking at.
  palettes, Material You, true black, and background canvases.
  included), and optional typewriter / word-by-word / fade-up reveals - all off
  unless asked for.
  a seasonal sky you can pace: how often it moves is its own setting, and it only
  offers the rates this screen reports.
  off until you turn it on.

### Deliberate non-goals

No offline-first database (a stale list gives confidently wrong answers) and no
notifications (a sideloaded app does not need a background poller). More, with
reasoning, in [NOTES.md](NOTES.md#deliberate-non-goals).

---

## Install

1. Download `anisequel-universal.apk` from the
   [latest release](https://github.com/orintarotsumugi/AniSequel/releases/latest).
2. Open it. Android will ask you to allow installs from your browser or file
   manager - that grant is per-app and per-source, and it only ever has to be
   given once.
3. Keep the app installed. AniSequel offers the next update itself, and each
   release installs over the previous one without losing anything.

Android 12 (API 31) or newer. There are no density or ABI splits - the one APK is
the app.

---

## Build it

JDK 21, Android SDK 37, Gradle comes with the wrapper.

```bash
./gradlew test              # JVM unit tests - the real gate
./gradlew assembleDebug     # debug APK
./gradlew assembleRelease   # signed release APK
```

`assembleRelease` falls back to the debug keystore when no release key is
configured, so it always produces something installable - but read
[Release signing](#release-signing) before you trust that output.

Prerequisites, pinned dependency versions and the reason for each pin live in
[BUILDING.md Prerequisites](BUILDING.md#prerequisites).

---

## How it works

```
ui/          Compose screens + ViewModels, widgets         (presentation)
domain/      use cases, tier list, profile layout, motion  (pure Kotlin, no Android)
data/        Retrofit + GraphQL, repositories, snapshots, updater
```

  ViewModels; the one place it is not Compose is `ui/widget/`, which the launcher
  inflates in its own process. `domain/` is pure Kotlin - the use cases
  (`FindMissedSequelsUseCase`, `FindArrivingEntriesUseCase`,
  `BuildListInsightsUseCase`, ...), the tier list, the profile layout rules and
  the motion geometry - with no Android imports. `data/` is Retrofit, Moshi,
  OkHttp, `AniListApiService` with its query strings in `GraphQLQueries.kt`,
  `AuthRepository` and the OAuth token in DataStore, and `SnapshotStore`.
  GraphQL, so every "endpoint" is a POST to `/graphql` with a query string body.
  `AuthInterceptor` adds the OAuth token; `RateLimitInterceptor` absorbs AniList's
  burst limiter; `RequestCoalescer` collapses identical in-flight queries. The
  update manifest gets its own `OkHttpClient`, so the AniList interceptor can
  never attach a bearer token to a public file.
  biggest request the app makes against an API of roughly thirty a minute. The
  repository reuses it for the interval chosen in Settings, and
  `ListFreshnessWatch` re-checks on every resume and every return to the
  dashboard - never in the background, because WorkManager cannot run that often
  and would burn the request budget on a list nobody is reading.
  atomic JSON file per kind, with the window that kind deserves: thirty minutes
  for the viewer, the refresh interval for lists, a day for a title's facts. A
  mutation or a sign-out takes the disk copy with it.
  resumable `.part` download, and three checks - zip magic, package name,
  signature - before anything reaches the installer. See [UPDATER.md](UPDATER.md).

The long versions are [ARCHITECTURE.md](ARCHITECTURE.md) (the spec) and
[NOTES.md](NOTES.md) (the odd decisions, and the failures behind them).

---

## Documentation

The repository's docs are a small library; this README is the door.

| | |
| --- | --- |
| [ARCHITECTURE.md](ARCHITECTURE.md) | The exhaustive spec: layers, sequence diagrams, the multi-module plan |
| [NOTES.md](NOTES.md) | Why the weird parts are weird. Read a section before changing its screen |
| [BUILDING.md](BUILDING.md) | Prerequisites, tests, signing, CI, version numbers, APK size |
| [UPDATER.md](UPDATER.md) | The update system end to end, and the failures it grew out of |
| [AGENTS.md](AGENTS.md) | The rules. Every one cost something to learn |
| [TO-DO.md](TO-DO.md) | Known improvements, each with the reason it was left |
| [IMPROVEMENTS-NEEDS-TO-BE-DONE.md](IMPROVEMENTS-NEEDS-TO-BE-DONE.md) | A feature-by-feature comparison against AniSync, with a roadmap |

Each layer directory carries a short `README.md` of its own - `data/`, `domain/`,
`ui/`, `ui/screens/`, `ui/components/` - stating what belongs there and which
mistakes have already been made in it.

---

## Project layout

```
AniSequel/
├── app/
│   ├── build.gradle.kts           version baseline, R8, packaging
│   └── src/
│       ├── main/
│       │   ├── java/com/anisequel/
│       │   │   ├── data/          network, repository, update, changelog
│       │   │   ├── domain/        use cases, tier, profile layout, motion, color
│       │   │   └── ui/            screens, components, theme, viewmodel, widget
│       │   ├── assets/            changelog.json
│       │   └── res/               the widget XML, adaptive icon, strings
│       └── test/                  131 JVM test classes (there is no androidTest/)
├── gradle/libs.versions.toml      every version, with its reasoning inline
├── update.json                    what the updater reads from the release asset
├── LICENSE                        MIT - use it, keep the notice
└── .github/workflows/
```

`applicationId` is `com.aistudio.anisequel.app`, not `com.anisequel`. The Kotlin
package was renamed away from the Android Studio default, but the application ID
is the identity every installed copy answers to and **cannot** change: editing it
makes the next release a different app, and every existing install rejects it.
The same trap as rotating the key.

---

## Release notes

`app/src/main/assets/changelog.json` is the single source of the app's release
notes. The "What's new" card on **Settings > Updates** reads it from inside the
APK; the release workflow reads the same file to write the GitHub release body.
Two consumers, one source, so they cannot drift.

The card shows the newest block rather than the block matching
`BuildConfig.VERSION_NAME`, because the workflow derives the published version at
build time and no file can be written ahead of a number that does not exist yet.
Why that is the safer failure, and what `ChangelogTest` pins:
[NOTES - Release notes](NOTES.md#release-notes).

Adding notes and bumping the version are **one change**, not two -
[AGENTS.md 6](AGENTS.md) makes it mandatory, and
[VersionBaselineTest](BUILDING.md#version-numbers) enforces the three copies.

---

## Release signing

> **Rotating the release key permanently breaks updates for every existing
> install.** Android refuses to install over an app signed with a different key
> (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`) and there is no undo.

The key is **not in this repository**. It lives only in the `KEYSTORE_BASE64`,
`STORE_PASSWORD` and `KEY_PASSWORD` repository secrets, and CI verifies the built
APK with `apksigner verify --print-certs` against the pinned certificate:

```
SHA-256 01924c4a7503820489802deb6993e5e030103a1a1a36482ecb54688fa3311f82
```

The key rotated on 2026-10-05; installs from v1.0.16 and earlier cannot update in
place. Git history is deliberately not rewritten - the old key is already
compromised, and hiding it would not un-compromise it. The full history, the three
verification layers and the local signing recipe are in
[BUILDING.md Release signing](BUILDING.md#release-signing).

---

## Continuous integration

One workflow, `.github/workflows/android-release.yml`, on push, tags, pull
requests and manual dispatch. Lint runs as its own advisory job while the release
job runs the test gate, builds the APK, verifies its signature and publishes the
release and `update.json`. Pull requests get a throwaway key and a comment telling
them where the APK is; nothing from a PR is ever published.

The diagram, the Robolectric cache and the seven properties that were each a bug
once are in [BUILDING.md Continuous integration](BUILDING.md#continuous-integration).

---

## Contributing

Read [AGENTS.md](AGENTS.md) first - it records the rules this repo learned the
expensive way. The short version:

- Keep files focused, under 700 lines, split by responsibility.
- No emoji as UI icons. Pure Compose `ImageVector`; vectors only.
- Look up the docs before adding a dependency, plugin or experimental API, and
  check it against *this* repo's Kotlin, AGP and compileSdk.
- Never commit key material. Never rewrite history to hide it.
- Bump the version in all three places and add the `changelog.json` block in the
  same change.
- If a bug reached a build, the regression test is the deliverable.
- `./gradlew test` must be green. Lint will not save you.

---

## License

**MIT** - see [LICENSE](LICENSE).

Use the code, fork it, ship it, or build the app into something of your own,
commercially or not. The one condition is **attribution**: keep the copyright and
licence notice with any copy or substantial portion of the code, and credit the
original author - **Rintarotsumugi**
([github.com/orintarotsumugi](https://github.com/orintarotsumugi)) - wherever this
code, or an app or service built from it, is distributed or published.
