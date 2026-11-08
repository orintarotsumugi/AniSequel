# Anime Watch and Extensions — phased implementation

This is the implementation trail for the `1.8.9` beta and the work that follows
it. Phase 1 is in pull request #121, labelled `beta`. Later phases are written so
each one can be picked up on its own, with a deliverable and a test that says
when it is done.

The beta deliberately splits **what can be installed** from **what can be
executed**. AniSequel can discover, download and record Aniyomi, CloudStream,
Sora and LNReader extensions now. It executes none of them yet, and the UI says
so instead of showing a source that would fail. The one playable source is the
built-in Internet Archive source, which is a real HTTP source: it searches,
returns episodes, exposes each Archive file as a server and plays through Media3.

## The licence boundary (read this before Phase 2)

- AniSequel is MIT. The referenced `AnymeXExtension-for-Anisequel-` fork is a
  fork of an Aniyomi/CloudStream runtime bridge whose upstream is GPL/UPL.
  Copying that runtime into this repository would relicense the application.
- No code from that fork is copied here. The repository formats are read from
  their public JSON shapes; the runtime is a clean-room design in Phase 2.
- The fork can still be used as a **reference for behaviour** and as a place to
  host a separately licensed runtime APK. It must not be linked statically into
  the MIT APK.
- A Firefox/WebView Cloudflare cookie bridge is a separate concern from an
  extension runtime; adding it does not grant permission to embed GPL code.

## Phase 1 — shipped in this beta

### 1.1 Extensions tab and repository catalogue

- [x] Fifth main tab (`Extensions`) between Library and Tiers.
- [x] Repository list with add, remove and seeded Aniyomi indexes.
- [x] Reads a repository root, a GitHub tree/blob URL, a Codeberg URL or a
      direct index URL; tries the conventional index tails in order.
- [x] Parses Aniyomi (`pkg`, `apk`, `lang`), CloudStream (`internalName`,
      `tvTypes`, `url`), and generic Sora/LNReader JSON arrays.
- [x] Anime / Manga / Novel content switch and ecosystem chips.
- [x] Language grouping, version badges, NSFW filter and per-repository error
      cards. One failed repository never hides the ones that answered.
- [x] Search across name, package name and language.

Done when: a fresh install shows the seeded catalogue, a bad URL produces a
visible repository failure rather than an empty page, and removing a repository
stays removed after a restart.

### 1.2 Install, permission and restart

- [x] APK downloads stream to `filesDir/extensions`, never the cache.
- [x] `.js` and `.jar` downloads are recorded as modules, not handed to the APK
      installer.
- [x] `REQUEST_INSTALL_PACKAGES` handoff through `ACTION_MANAGE_UNKNOWN_APP_SOURCES`,
      then the app-details screen, then the app list, then Settings.
- [x] The grant is observed on `onResume`, never assumed.
- [x] A restart banner and a real `makeRestartActivityTask` restart.
- [x] FileProvider publishes `files-path/extensions`, the directory the
      installer actually writes.
- [x] Install from a storage URI copies the file into that same directory.
- [x] The manager page reports permission state, repository/install counts,
      NSFW visibility and the Phase 2 boundary.

Done when: the package installer opens for an APK, a refusal keeps the file and
explains the permission, and an install is visible in Installed after a restart.

### 1.3 Watch page

- [x] Pinned Watch dock on an anime's Info page; hidden for non-anime and for a
      preview with no navigation host.
- [x] Full Watch page with blurred hero, list actions, source card, found-title
      card and episode list.
- [x] Source sheet lists every source, including installed third-party records
      with their Phase 2 note.
- [x] "Wrong Title?" runs a real source search and remembers the chosen mapping
      per AniList id.
- [x] All / 25-episode range chips, Minimal / Compact / Detailed layouts,
      artwork toggle and server preference.
- [x] Continue Watching bar writes and reads the resume point.
- [x] Downloaded tab is explicit about downloads not existing yet rather than
      showing a fake download.

Done when: opening an anime from the Library reaches a playable episode with no
manual source selection, and "Wrong Title?" changes the match.

### 1.4 Playback

- [x] Media3 ExoPlayer with a Compose control layer (no stock controller).
- [x] Server picker per episode; chosen server remembered per title.
- [x] Next episode advances the in-process queue.
- [x] Resume position written every five seconds, at end, on dispose and before
      entering Player Settings.
- [x] SurfaceView by default; TextureView when enhancement or the renderer
      setting requires it.
- [x] Decoder filter, renderer choice and a real `RenderEffect` colour grade.
- [x] Fullscreen, speed, seek and ten-second skip controls.
- [x] A disposed old player cannot overwrite the new episode's resume point.

Done when: an episode plays end to end, "next" advances, closing and reopening
resumes, and Player Settings returns to the same position.

### 1.5 Regression tests to add with this beta

These are the tests that should be written next; CI runs `./gradlew test` and
there is no Android SDK in the development environment.

- Repository parser fixture tests for one Aniyomi, one CloudStream and one
  generic array index (JSON strings only, no network).
- `WatchRoutes.forMedia` / `from` round-trip for a title with spaces, `&` and a
  slash in its name.
- `episodeRanges` boundaries: empty, one episode, 25, 26 and 50.
- A `PlaybackQueue` test for next-at-end and resume-at-server-switch.
- A `VersionBaselineTest`/`ChangelogTest` run after every version bump.

## Phase 2 — a sandboxed third-party runtime

The goal is that an installed Aniyomi APK or CloudStream JAR becomes a source
without ever running its code in the main process.

### 2.1 Process isolation

- [ ] A dedicated `:extension-runtime` process declared with
      `android:isolatedProcess="true"` and no access to the app's private data.
- [ ] AIDL surface with four calls: `search`, `details`, `episodes`, `resolve`.
      Keep it smaller than the Aniyomi API; the Watch page only needs those.
- [ ] A per-call timeout and a hard process kill; a misbehaving extension must
      not take the player down.
- [ ] Binder identity check on the host side; only this app's UID may bind.

### 2.2 Aniyomi APK loader

- [ ] Load the APK with a `PathClassLoader` in the isolated process only.
- [ ] Read the extension's `AndroidManifest` metadata (`tachiyomi.extension`,
      version, language, NSFW) and construct its source class through the
      published factory interface.
- [ ] Map the Aniyomi `CatalogueSource` / `AnimeSource` results into the host's
      `SourceAnime`, `SourceDetails`, `SourceEpisode` and `SourceServer`.
- [ ] Do not expose `Context`, files, or the main process's OkHttp client.
- [ ] Test with one known Aniyomi APK that returns a fixed search result and one
      video URL; assert the host receives it without loading the class itself.

### 2.3 CloudStream JAR loader

- [ ] Treat `.jar` as a plugin module, never as an APK (Phase 1 already stops
      handing it to the package installer).
- [ ] Load with a child-first class loader in the isolated process.
- [ ] Map `MainAPI` search/load/links results to the same host DTOs.
- [ ] Resolve subtitle and quality lists into the existing server list.
- [ ] Reject plugins that request the host's classes or reflection escapes.

### 2.4 Sora and LNReader modules

- [ ] Pick one JavaScript engine and pin it (no dynamic CDN download).
- [ ] Execute modules in a JS context with no filesystem, no raw network and a
      small host bridge for HTTP and JSON only.
- [ ] Map results to the same source DTOs; a module that cannot return a video
      URL is still listed with `playable = false` rather than hidden.
- [ ] Keep the JS runtime behind the same AIDL interface so the player never
      knows which ecosystem answered.

### 2.5 Runtime host management

- [ ] The manager's "Update plugin", "Force re-download" and "Rollback" rows
      become real only when there is a signed runtime APK to install.
- [ ] A runtime version handshake: the app and the isolated runtime refuse a
      mismatch instead of failing one request at a time.
- [ ] A source-level circuit breaker: three failures disable the source for the
      session and show the reason.

Done when: one Aniyomi APK and one CloudStream JAR each return a search result,
an episode list and a playable URL through the isolated process, and the main
process has loaded neither class.

## Phase 3 — real enhancement, not a colour filter

Phase 1 applies saturation/contrast through `RenderEffect`. That is real but it
is not upscaling. Phase 3 should either ship a real shader or remove the "4K"
language from the UI.

- [ ] Media3 `GlEffect` / `GlShaderProgram` pipeline on a `TextureView` or
      `SurfaceTexture`.
- [ ] One anime-tuned luma/chroma pass, with a measurable output (before/after
      frame and a device frame-time budget).
- [ ] Device capability probe: GPU model, max texture size, current frame time.
- [ ] High-End / Mid-End profiles become shader parameter sets, not colour
      matrices.
- [ ] Off switch must restore the untouched surface and release GL resources.
- [ ] A benchmark on the low-end device the app still supports; if it cannot
      hold the frame budget, the profile is not offered.

Done when: a 720p episode renders visibly sharper at 1080p/4K surface size,
frame time stays inside the budget on the reference device, and disabling the
effect returns the original pixels.

## Phase 4 — downloads and offline playback

The Downloaded tab currently says it is not available. That is intentional;
this phase makes it true.

- [ ] A download queue with pause/resume, one active download and a visible
      progress notification.
- [ ] HLS/DASH segment download or a progressive MP4 fallback; never store a
      signed URL as if it were the file.
- [ ] Store under `filesDir/downloads/<source>/<anime>/<episode>/` with a
      manifest and a `.part` prefix that can resume.
- [ ] Verify size and media type before marking complete.
- [ ] Playback resolves a downloaded file before asking the network.
- [ ] Remove-download and clear-completed actions; deleting an episode must not
      orphan its manifest.
- [ ] Storage ceiling and eviction policy in Device preferences.
- [ ] Downloaded tab lists real local episodes and an explicit empty state.

Done when: airplane mode plays a completed download, a killed download resumes,
and the tab's counts match the files on disk.

## Phase 5 — catalogue quality and source operations

- [ ] Repository health check with last-success time and a manual refresh.
- [ ] Extension update detection by package + version, not by display name.
- [ ] Update-all and per-extension update with signature/package checks.
- [ ] Category/search filters supplied by the source rather than the catalogue
      row only.
- [ ] Subtitle and audio-track selection in the player, persisted per title.
- [ ] Quality selector derived from the source's servers, not guessed from a
      file name.
- [ ] Source ordering and pinning on the Watch page.
- [ ] A "source reported no playable file" state that names the extension and
      the request that failed.

## Delivery rules for every phase

- Branch from `main`, open a PR, never push to `main`.
- Keep files under 700 lines, split by responsibility.
- Update `gradle.properties`, `app/build.gradle.kts` and `update.json` together,
  then prepend `assets/changelog.json`.
- Run `./gradlew test`; CI is the build. Do not claim a local Android build.
- Add the regression test that fails without the fix before shipping a bug fix.
- Keep `ANIME-WATCH-PHASES.md` current: tick a box only when its test exists.
