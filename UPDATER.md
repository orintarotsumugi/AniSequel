# The in-app updater

AniSequel does not ship through the Play Store, so it ships itself. This is the
least obvious system in the app: a manifest read from a release asset, a
resumable download, a permission that cannot be requested at launch, and a
hand-off to the system installer through a `FileProvider`.

Signing, CI and the version baseline are the maintainer's half and live in
[BUILDING.md](BUILDING.md#release-signing). This file is what the app does.

- [Where the manifest comes from](#where-the-manifest-comes-from)
- [What happens when you press Install](#what-happens-when-you-press-install)
- [Two directories, and why the FileProvider is part of this](#two-directories-and-why-the-fileprovider-is-part-of-this)
- [Interrupted downloads continue](#interrupted-downloads-continue)
- [A download that cannot be installed stays downloaded](#a-download-that-cannot-be-installed-stays-downloaded)
- [What the updater will not do](#what-the-updater-will-not-do)

---

## Where the manifest comes from

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

## What happens when you press Install

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

## Two directories, and why the FileProvider is part of this

An update is handed to the installer as a `content://` URI, because the installer
runs as a different app and cannot read this app's private storage by path. That
URI is minted by a `FileProvider`, and what it may publish is declared in
`app/src/main/res/xml/update_file_paths.xml`.

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

## Interrupted downloads continue

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
rather than averaged across the whole transfer — so it reports the connection
you have *now*. An average is dominated by the part of the transfer that already
happened, which is exactly the number that is no longer true.
`TransferRateTest` pins the window.

## A download that cannot be installed stays downloaded

When `openInstaller` failed, the app used to discard its record of the file it
had already fetched and close the pop-up, so the update looked gone and the only
way back to it was a fresh check. The APK is still on disk and still verified, so
the failure message now sits above the button that tries again rather than
replacing it. `UpdateDownloadStateTest` covers the same ground across a relaunch.

This is the same instinct as the cache keyed by release version: the expensive,
already-verified thing should survive a failure of something downstream of it.

## What the updater will not do

It cannot install without a user-visible confirmation — that has been true since
Android 8, and it is a platform guarantee rather than a choice made here.

Before a downloaded file is handed to the installer it must be a zip
(`PK\x03\x04`), must match this app's package name if readable, and must carry
either AniSequel's pinned release certificate (`01924c4a…1f82`) or the running
app's own signing certificate. A release build refuses anything it cannot
positively identify — "we could not read a signature" is the absence of proof,
not a substitute for it.
