# AGENTS.md

Rules for working in **AniSequel**. Every one of these is here because the
obvious alternative was tried and cost something. The *why* is the point — if
you cannot reproduce the failure, do not remove the rule, and do add the missing
explanation.

Read [README.md](README.md) for the tour and [NOTES.md](NOTES.md) for why the
app is put together that way; this file is only about the constraints.

---

## 0. Never push to `main` — open a pull request

- **Every change lands through a branch and a pull request.** Branch off
  `main`, commit, push the branch, and open the PR. Do not commit to `main` and
  do not push to it, not even for a one-line fix and not even when the tests
  pass locally.
- **Why:** `main` is the release branch. The workflow treats a non-PR run as
  shippable — it builds, verifies the signature and publishes the release and
  `update.json` manifest — so a direct push is a release attempt that skipped
  review. A push made during this project failed to compile in CI, and
  force-pushing `main` to undo it was rejected by branch protection, so the
  broken commit could not be withdrawn. The PR is the only checkpoint between a
  commit and users.
- **If a commit already reached `main`**, do not try to rewrite `main`. Land the
  fix forward through a PR and say plainly in the PR what reached `main` and
  when.

---

## 1. Size and structure

- **Keep files focused, and under 700 lines.** The number is a ceiling, not a
  target — a file under it that is doing two jobs still needs splitting. Past 700
  the file is genuinely doing more than one thing.
- **Split by responsibility, not by line count.** Splitting a large file into two
  arbitrary halves is churn. The split is worth making when the two halves have
  genuinely different reasons to change, or different owners.
- **Keep layers separate**: `ui/` (presentation), `domain/usecase/` (pure logic,
  no Android imports), `data/` (network, repository, update).
- **Comments explain why, not what.** A comment restating the line below it is
  noise and rots. A comment recording that something looks wrong but is
  deliberate is the most valuable thing in the file — those are the traps.

### Why `DashboardViewModel` is not 700 lines of helpers by accident

Its recompute pipeline (`discoveredCandidates`, `discoveryKey`, the detail cache,
the watched-count memo, the debounce generation guard) shares about a dozen
private fields. Splitting it means either passing that state around or making it
`internal`, and both make the invariants *worse* — they are already hard to hold.
It is tracked in [TO-DO.md](TO-DO.md) with the specific extraction.

---

## 2. UI and assets

- **No emoji as UI icons.** Ever. Vectors only.
- **Pure Compose `ImageVector`**, no PNG/SVG/XML drawable for app UI, no
  `res/anim`, no `res/font`.
- Custom vectors live in `AppVectorIcons.kt`, `AppCustomVectors.kt`,
  `AppExtraVectors.kt`. Name them semantically, not by shape.
- **Reachability counts.** A resource that no configuration on `minSdk = 31` can
  load is dead weight in the APK. The five density-bucket launcher PNGs Android
  Studio generates were 30 KB of exactly that.
- **Raising `minSdk` is not a number change, it is a deletion.** Every
  `SDK_INT` branch below the new floor describes a device the app can no longer
  be installed on. Delete the branch and its fallback copy rather than leaving it
  to rot: a guard that reads as load-bearing and cannot fail is worse than no
  guard, because the next reader assumes there is a device class it excludes.
  The `minSdk` comment in `app/build.gradle.kts` has to say what it *buys*, not
  only which versions it drops.

---

## 3. Material 3 Expressive motion

- **Spring physics only.** Use the shared tokens in `ExpressiveMotion.kt`:
  `FastSpatial`, `FastSpatialInt`, `FastColorEffects`, `BouncySpatial`,
  `SuperBouncy`, `DefaultSpatial`, `PressSpatial`. If you need a new feel, add a
  named token there rather than inlining a literal spring — six copies of the
  same literal spring is how they drift apart.
- **Animate transforms in the draw phase.** `Modifier.graphicsLayer { }`,
  `Modifier.bouncyPress(...)`. Animating a value read by layout re-runs layout
  on every frame.
- **Animate toward a settled value.** `animateColorAsState` toward a constant,
  or an `AnimatedContent` whose target changes every frame, animates forever or
  restarts constantly. The dashboard counter was doing the latter.
- **Cap indefinite animations.** One `rememberInfiniteTransition` per screen,
  and gate it on visibility so a shimmer keeps ticking in the background.
- **Shapes are capsules, not stretched polygons.** `RoundedPolygon` is authored on
  a square perimeter and `toShape()` stretches it to the `Size` it is given, so a
  120×44 tab renders a 120×44 *ellipse* from `MaterialShapes.Pill`. Use
  `RoundedCornerShape(percent = 50)` for capsules.
  `ExpressiveShapesTest` guards the geometry.

---

## 4. Dependencies and versions

- **Look up the docs before adding a dependency, Gradle plugin, or experimental
  API.** Check it against *this* repo's Kotlin, AGP, and compileSdk — not against
  the newest release.
- **A version pin that looks stale is usually load-bearing.** `material3` is pinned
  *above* the Compose BOM because the Expressive component set (LoadingIndicator,
  the wavy progress indicators, SplitButton, the morphing ButtonGroup) only exists
  in the 1.5 line - the BOM still resolves the stable 1.4.0. Both the BOM and the
  pin now sit on the Compose 1.12 line and require compileSdk 37; the reasoning
  lives inline in `gradle/libs.versions.toml`. Read it before "tidying" it.
- **Scope every dependency.** `ui-tooling-preview` is `debugImplementation`, not
  `implementation`. A release APK has no business shipping preview tooling.
- **Do not add `-keep` rules on `androidx.compose`.** R8 and
  `shrinkResources` are both on precisely because there are none; one keep rule
  on Compose undoes the size work the app has done.

---

## 5. Release signing — read before touching the keystore

> **Rotating the release key permanently breaks updates for every existing
> install.** Android refuses to install over an app signed with a different key
> (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`) and there is no undo.

- **Never commit key material.** No `.jks`, `.p12`, `.pfx`, and no base64 key. The
  key lives only in the `KEYSTORE_BASE64`, `STORE_PASSWORD` and `KEY_PASSWORD`
  repository secrets. A committed key lets anybody sign an "update" APK that the
  in-app updater will accept.
- **Never rewrite history to hide a key.** The old key is in commit history and
  stays there. Purging it hides a key that is already compromised without
  un-compromising it, and it has already cost this project one release path.
- **Never add a keytool fallback in CI.** The workflow has no
  `keytool -genkeypair` fallback on purpose: those branches turned a repository
  with no secrets into a green build that signed every release with a fresh
  random key, and every user who installed one got
  `INSTALL_FAILED_UPDATE_INCOMPATIBLE`. Missing secrets must fail loudly.
- **`KeystoreIntegrityTest` must pass.** It guards both halves — no key in the
  repo, and no literal secret in the workflow.
- **The workflow verifies the signature for real**, with `apksigner verify
  --print-certs` against the pinned certificate digest, on non-PR runs only.

Full history and the current fingerprint: [BUILDING § Release signing](BUILDING.md#release-signing).

---

## 6. Version numbers and changelog

Version lives in **three** places and all three must agree:

1. `anisequelVersionName` / `anisequelVersionCode` in `gradle.properties`
2. `BASELINE_VERSION_NAME` / `BASELINE_VERSION_CODE` in `app/build.gradle.kts`
3. `version` / `version_code` in `update.json`

`VersionBaselineTest` enforces it — run it. After a release, bring 1 and 2
forward by hand: the workflow's commit is rejected by branch protection because
GitHub does not run workflows for `GITHUB_TOKEN` pushes.

### Which number to write: patch first, roll at 9

- **A release is the last digit plus one.** `1.8.2` ships, the next one is
  `1.8.3` — whatever the size of the change. Bug fixes, a redrawn icon, a new
  setting, a performance pass: they all take the same step. Do not read
  *major/minor/patch* off the version number and decide the bump from that;
  this app has no "breaking release" or "feature release" tier, and a rule that
  depends on classifying a change is a rule that gets argued every time.
- **The last digit is a single decimal place, so it rolls at 9.** After `1.8.9`
  comes `1.9.0`, and after `1.9.9` comes `1.10.0` — the *middle* digit steps up
  and the last resets to zero. Nothing skips: `1.8.9` is never followed by
  `1.9.1`, and a release is never labelled with a digit that has already been
  used.
- **`versionCode` ignores all of this and goes up by one every release.** It is
  the GitHub run number, monotonic by construction, and it is the only thing the
  updater compares — `1.0.9` sorts above `1.0.10` as a string, which is why
  nobody may compare names (see §7).

**Why the rule is written down:** the number is the release's name in the
changelog, in `update.json` and in the tag, and a bump that has to be reasoned
out each time produces a release that repeats or skips a name. `ChangelogTest`
pins "newest first" and "no version listed twice", which catches the repeat, and
nothing catches a skipped digit — that only shows up later as a gap nobody can
explain.

### Always update the changelog and app version together (MANDATORY)

- **Never ship changes or release a new version without updating `assets/changelog.json`**.
- When new features, visual redesigns, gesture additions, or bug fixes are introduced:
  1. Increment the version number (`anisequelVersionName` in `gradle.properties`, `BASELINE_VERSION_NAME` in `app/build.gradle.kts`, `version` in `update.json`) and version code (`anisequelVersionCode`, `BASELINE_VERSION_CODE`, `version_code`).
  2. Prepend a new release block to `app/src/main/assets/changelog.json` matching the new version number, with concise, descriptive entries explaining what changed.
  3. Run `ChangelogTest` and `VersionBaselineTest` to verify that the changelog format, version ordering, and version alignment pass all assertions.
  4. The in-app "What's new" card in Settings > Info and the release manifest read from `changelog.json`. Leaving it stale breaks user trust and fails automated test verification.

---

## 7. The updater

- **`REQUEST_INSTALL_PACKAGES` is not a runtime permission.** Declaring it in the
  manifest does not grant it; it only makes AniSequel allowed to *ask*. The grant
  is per-app, in Settings, from Android 8.
- **Never prompt for it at launch.** Only in response to the user asking to
  update.
- **Download before checking the permission.** The user already asked. Asking
  first means refusing to start over a setting they may have granted since the
  last launch.
- **The grant is observed via `onResume`, never polled.** Nothing in-process is
  told when the user flips a switch in another app's Settings screen.
- **Open Settings through every route the device might answer, in order.**
  `ACTION_MANAGE_UNKNOWN_APP_SOURCES` is one permission with several ways in, and a
  re-skinned build drops the ones you would expect: some phones register the
  package-scoped screen, some only the all-apps list, some only the app-info page,
  and some move it under a security page. `UpdateInstallation.permissionSettingsRoutes()`
  is that ladder — package-scoped, app details, the same action with no package,
  security, Settings home — and every rung carries `FLAG_ACTIVITY_NEW_TASK`, because
  these launches come from the *application* context and without the flag the throw
  is indistinguishable from a device with no Settings app. Adding one route and
  stopping is how the "your phone has no Settings" complaint stayed alive: on a
  re-skinned build the first two intents can both be unanswered while the switch sits
  two screens away. `UpdateInstallPermissionTest` pins the order and the flag.
- **Say what to tap before sending them there.** The route opens a screen this app
  cannot see, whose row is named differently by every manufacturer, so
  `InstallPermissionGuide` writes the steps per brand (Samsung/Xiaomi/oppo/Huawei
  first, generic otherwise) and closes with the search fallback that is true on every
  build. The sentences are a pure object over strings for the same reason the query
  documents are: nothing here can look at a phone, so pin what the user is told.
- **A refused Settings hand-off keeps the APK and says so.** It stays
  `NeedsInstallPermission` with a notice; `Failed` would drop the file the user
  already waited for and send them to re-download it, and the message is the one thing
  that must never blame the device for our own intent.
- **Cache APKs by release version** (`UpdateManager.downloadedApkFor`), so the
  Settings round trip costs one download instead of two.
- **The download directory and the `FileProvider` paths are one contract.**
  `res/xml/update_file_paths.xml` has to publish the directory `UpdateManager`
  writes into, or `getUriForFile` throws and every install ends on "This device
  has no app installer". Moving one without the other compiles, lints and
  ships - the only evidence is a message blaming the user's phone.
- **A `.part` file survives a failed transfer.** It is the valid prefix of the
  release and `ApkDownloader` continues from it; only a file that fails
  verification is thrown away, because those bytes are not a prefix of anything
  installable.
- **Compare `version_code`, never the version string.** "1.0.9" sorts above
  "1.0.10"; the code is the GitHub run number and is monotonic by construction.
- **Verify before installing**: zip magic, matching package name, and a signature
  that is either the pinned release certificate or the running app's own. A
  release build refuses anything it cannot positively identify.

---

## 8. Verification

- **`./gradlew test` is the gate.** `lintRelease` is advisory
  (`abortOnError = false`); do not describe it as blocking.
- **Write the regression test.** If you fix a bug that reached a build, the test
  that fails without the fix is the deliverable. `ApkPackagingTest`,
  `VersionBaselineTest`, `KeystoreIntegrityTest` and `ExpressiveShapesTest` all
  exist because of a specific shipped failure.
- **CI is the build.** There is no local Android SDK in the development
  environment; verify by pushing and reading Actions, not by guessing.
- **Do not add a test that asserts a behaviour you chose rather than one the
  codebase requires.** Pin requirements, not preferences.

---

## 9. Comments and documentation

- Explain the failure that made the code necessary, not the code.
- When fixing something that looks wrong, say in a comment that it *was* wrong,
  what it did, and what it does now — otherwise the next reader "fixes" it back.
- Keep `gradle/libs.versions.toml`'s inline version reasoning. It is the only
  record of why a pin exists.
- Significant architecture or protocol changes get a section in
  [NOTES.md](NOTES.md) — or in [ARCHITECTURE.md](ARCHITECTURE.md) if they change
  the layering — not just a code comment. [README.md](README.md) is the tour and
  stays short on purpose.