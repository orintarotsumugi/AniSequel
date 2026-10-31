# AniSequel

**Your AniList history, read backwards.** AniSequel walks the relation graph of
every anime and manga you have finished and finds the sequels, films, OVAs and
spin-offs you never got around to — then puts the good ones one tap from your
Planning list.

<br>

![Kotlin](https://img.shields.io/badge/Kotlin-2.4-7F52FF?logo=kotlin&logoColor=white)
![Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203%20Expressive-4285F4?logo=jetpackcompose&logoColor=white)
![Android](https://img.shields.io/badge/Android-12%2B-3DDC84?logo=android&logoColor=white)
![Tests](https://img.shields.io/badge/tests-JVM%20only-6E56CF)

Not on the Play Store. Every release is a signed universal APK on
[GitHub Releases](https://github.com/orintarotsumugi/AniSequel/releases), with a
self-contained updater in the app — no Play Services, no AniSequel account, no
middleman server.

---

## What it does

- **Franchise traversal, not title matching.** A show's `relations` graph is
  walked in both directions, so renamed continuations, films and side stories
  are found even when the title gives nothing away.
- **Your profile, or anybody's.** Scan a public AniList username without
  signing in. Read-only, and the UI says so before a write can fail.
- **Home, Library, Profile.** Three pages in a floating capsule at the bottom of
  the screen. Library is your own list as a poster grid, with counts and search.
- **One-tap Planning.** OAuth against your own AniList account, no AniSequel
  password. Tap to plan, hold or swipe to start watching, from a recommendation
  card or any title page.
- **Filters, hiding, undo.** Sort and filter by relation type and status, search,
  and hide the entries you would rather not be nagged about.
- **A title page that answers everything.** Synopsis, categories, themes, cast,
  crew, studios, openings and endings, the rest of the franchise, and an entry
  editor on a second capsule.
- **Studios and people are destinations too.** Every credit opens the studio's
  filmography; every face opens the character, staff or voice-actor sheet.
- **Material 3 Expressive throughout.** Spring physics, a blurred key visual
  behind every title, and a Material You theme seeded from the cover art itself.
- **A tier list, local and shareable.** Saved as projects, dragged positionally,
  filtered by status, genre, studio and a name search, with names under or on the
  cover and cut to a chosen length, locked into view-only browsing, and exported
  as one full-board PNG up to 4K that can be shared or saved to Pictures (with a
  lossless SVG renderer behind it).
- **Four home-screen widgets.** Recent activity, missed sequels, arriving
  sequels, and one featured recommendation.
- **A real updater.** Resumable download, verified signature, cached APK across
  the Settings round trip.

### Deliberate non-goals

No offline-first database (a stale list gives confidently wrong answers) and no
notifications (a sideloaded app does not need a background poller). More, with
reasoning, in [NOTES.md](NOTES.md#deliberate-non-goals).

---

## Install

1. Download `anisequel-universal.apk` from the
   [latest release](https://github.com/orintarotsumugi/AniSequel/releases/latest).
2. Open it. Android will ask you to allow installs from your browser or file
   manager — that grant is per-app and per-source, and it only ever has to be
   given once.
3. Keep the app installed. AniSequel offers the next update itself, and each
   release installs over the previous one without losing anything.

Android 12 (API 31) or newer. There are no density or ABI splits — the one APK
is the app.

---

## Build it

JDK 21, Android SDK 36.1, Gradle comes with the wrapper.

```bash
./gradlew test              # JVM unit tests — the real gate
./gradlew assembleDebug     # debug APK
./gradlew assembleRelease   # signed release APK
```

`assembleRelease` falls back to the debug keystore when no release key is
configured, so it always produces something installable — but read
[Release signing](#release-signing) before you trust that output.

Prerequisites, pinned dependency versions and the reason for each pin live in
[BUILDING.md § Prerequisites](BUILDING.md#prerequisites).

---

## How it works

```
ui/          Compose screens + ViewModels, widgets        (presentation)
domain/      use cases, tier list, motion, studio shelves (pure Kotlin, no Android)
data/        Retrofit + GraphQL, repositories, snapshots, updater
```

- **Three layers, one rule.** `ui/` is Compose screens plus `StateFlow` and
  ViewModels; the one place it is not Compose is `ui/widget/`, which the launcher
  inflates in its own process. `domain/` is pure Kotlin — the use cases
  (`FindMissedSequelsUseCase`, `SaveToPlanningUseCase`, `GetViewerProfileUseCase`,
  `FindArrivingEntriesUseCase`, `BuildListInsightsUseCase`), the tier list, the
  motion geometry — with no Android imports. `data/` is Retrofit, Moshi, OkHttp,
  `AniListApiService` with its query strings in `GraphQLQueries.kt`,
  `AuthRepository` and the OAuth token in DataStore, and `SnapshotStore`.
- **One backend, and it is AniList's.** No AniSequel server. AniList speaks only
  GraphQL, so every "endpoint" is a POST to `/graphql` with a query string body.
  `AuthInterceptor` adds the OAuth token; `RateLimitInterceptor` absorbs AniList's
  burst limiter; `RequestCoalescer` collapses identical in-flight queries. The
  update manifest gets its own `OkHttpClient`, so the AniList interceptor can
  never attach a bearer token to a public file.
- **The list is expensive and goes stale.** One `MediaListCollection` is the
  biggest request the app makes against an API of roughly thirty a minute. The
  repository reuses it for the interval chosen in Settings (30 minutes by
  default), and `ListFreshnessWatch` re-checks on every resume and on every
  return to the dashboard — never in the background, because WorkManager cannot
  run that often and would burn the request budget on a list nobody is reading.
- **Closing the app does not throw the list away.** `SnapshotStore` keeps one
  atomic JSON file per kind under the cache directory, with the window that kind
  deserves: thirty minutes for your profile, the refresh interval for lists, a
  day for a title's facts. A mutation or a sign-out takes the disk copy with it.
- **The updater is its own small system**: manifest from a release asset, a
  resumable `.part` download, and three checks — zip magic, package name,
  signature — before anything reaches the installer. See
  [UPDATER.md](UPDATER.md).

The long versions are [ARCHITECTURE.md](ARCHITECTURE.md) (the spec) and
[NOTES.md](NOTES.md) (the odd decisions, and the failures behind them).

---

## Documentation

| | |
| --- | --- |
| [ARCHITECTURE.md](ARCHITECTURE.md) | The exhaustive spec: layers, sequence diagrams, the multi-module plan |
| [NOTES.md](NOTES.md) | Why the weird parts are weird. Read a section before changing its screen |
| [BUILDING.md](BUILDING.md) | Prerequisites, tests, signing, CI, version numbers, APK size |
| [UPDATER.md](UPDATER.md) | The update system end to end, and the failures it grew out of |
| [AGENTS.md](AGENTS.md) | The rules. Every one cost something to learn |
| [TO-DO.md](TO-DO.md) | Known improvements, each with the reason it was left |
| [IMPROVEMENTS-NEEDS-TO-BE-DONE.md](IMPROVEMENTS-NEEDS-TO-BE-DONE.md) | A feature-by-feature comparison against AniSync, with a roadmap |

Each layer directory carries a short `README.md` of its own — `data/`,
`domain/`, `ui/`, `ui/screens/`, `ui/components/` — stating what belongs there
and which mistakes have already been made in it.

---

## Release notes

`app/src/main/assets/changelog.json` is the single source of the app's release
notes. The "What's new" block in Settings → About & support reads it from inside the APK;
the release workflow reads the same file to write the GitHub release body. Two
consumers, one source, so they cannot drift.

The card shows the newest block rather than the block matching
`BuildConfig.VERSION_NAME`, because the workflow derives the published version
at build time and no file can be written ahead of a number that does not exist
yet. Why that is the safer failure, and what `ChangelogTest` pins:
[NOTES § Release notes](NOTES.md#release-notes).

Adding notes and bumping the version are **one change**, not two —
[AGENTS.md §6](AGENTS.md) makes it mandatory, and
[VersionBaselineTest](BUILDING.md#version-numbers) enforces the three copies.

---

## Release signing

> **Rotating the release key permanently breaks updates for every existing
> install.** Android refuses to install over an app signed with a different key
> (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`) and there is no undo.

The key is **not in this repository**. It lives only in the `KEYSTORE_BASE64`,
`STORE_PASSWORD` and `KEY_PASSWORD` repository secrets, and CI verifies the
built APK with `apksigner verify --print-certs` against the pinned certificate:

```
SHA-256 01924c4a7503820489802deb6993e5e030103a1a1a36482ecb54688fa3311f82
```

The key rotated on 2026-10-05; installs from v1.0.16 and earlier cannot update
in place. Git history is deliberately not rewritten — the old key is already
compromised, and hiding it would not un-compromise it. The full history, the
three verification layers and the local signing recipe are in
[BUILDING.md § Release signing](BUILDING.md#release-signing).

---

## Continuous integration

One workflow, `.github/workflows/android-release.yml`, on push, tags, pull
requests and manual dispatch. Lint runs as its own advisory job while the
release job runs the test gate, builds the APK, verifies its signature and
publishes the release and `update.json`. Pull requests get a throwaway key and
a comment telling them where the APK is; nothing from a PR is ever published.

The diagram, the Robolectric cache and the seven properties that were each a bug
once are in [BUILDING.md § Continuous integration](BUILDING.md#continuous-integration).

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
│       │   │   ├── domain/        use cases, tier, motion, studio, color
│       │   │   └── ui/            screens, components, theme, viewmodel, widget
│       │   ├── assets/            changelog.json
│       │   └── res/               the widget XML, adaptive icon, strings
│       └── test/                  98 JVM test classes (there is no androidTest/)
├── gradle/libs.versions.toml      every version, with its reasoning inline
├── update.json                    what the updater reads from the release asset
└── .github/workflows/
```

`applicationId` is `com.aistudio.anisequel.app`, not `com.anisequel`. The Kotlin
package was renamed away from the Android Studio default, but the application ID
is the identity every installed copy answers to and **cannot** change: editing
it makes the next release a different app, and every existing install rejects
it. The same trap as rotating the key.

---

## Contributing

Read [AGENTS.md](AGENTS.md) first — it records the rules this repo learned the
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

**MIT** — see [LICENSE](LICENSE).

Use the code, fork it, ship it, or build the app into something of your own,
commercially or not. The one condition is **attribution**: keep the copyright
and licence notice with any copy or substantial portion of the code, and credit
the original author — **Rintarotsumugi**
([github.com/orintarotsumugi](https://github.com/orintarotsumugi)) — wherever
this code, or an app or service built from it, is distributed or published.
