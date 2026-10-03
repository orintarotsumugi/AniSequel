# AniSequel

AniSequel is a modern Android application built with Kotlin and Jetpack Compose that scans your AniList completed anime catalogue to detect missed sequels, next seasons, movies, and prequels across all your franchises.

---

## Key Features

- **Automated Sequel & Franchise Discovery**: Recursively traverses relation graphs via AniList's GraphQL API to pinpoint uncompleted seasons and spin-offs.
- **1-Tap Planning Sync**: Directly add missing entries to your AniList "Planning" list via OAuth authentication.
- **Public Profile Scanner**: Check sequels for any public AniList username without logging in.
- **Material 3 Expressive UI**: Featuring fluid spring animations, tactile bouncy press feedback, custom SVG vector iconography, and dynamic color theming.
- **Robust Rate-Limiting & Caching**: Exponential backoff request coalescing to respect AniList API quotas.
- **In-App & Automated Releases**: Integrated GitHub Actions workflow with signed universal APK generation, release notes synchronization, and SHA-256 integrity verification.

---

## Release Signing & Keystore Verification

Every release is signed with a single long-lived release key so that Android accepts it as an in-place update. A release signed with a *different* key than the installed copy fails with `INSTALL_FAILED_UPDATE_INCOMPATIBLE` and the user must uninstall, losing local data.

**The key is not in this repository.** It lives only in three repository secrets:

| Secret | Contents |
| --- | --- |
| `KEYSTORE_BASE64` | `base64` of the JKS release keystore |
| `STORE_PASSWORD` | Keystore password |
| `KEY_PASSWORD` | Key password (same value as the store password) |

The key was previously committed as `debug.keystore.base64` through v1.0.16. Because base64 is an encoding rather than encryption, anyone who cloned the repo could decode it and sign an arbitrary "update" APK — which the in-app updater would accept, since it only checks that the downloaded APK's certificate matches the installed one. It has now been removed from the working tree and added to `.gitignore` so it cannot be committed again.

**The key itself is unchanged.** Every published release, v1.0.0 through v1.0.16, is signed by the same certificate (SHA-256 `c33eceb9ccf48378c8ec8fc7c1d72b0340638d2d824a4aa3db96c8e8f1c0e4ab`), and every build made from the secret is signed by it too. Moving the key out of version control stops further leakage; it does not rotate the key. **Nobody needs to uninstall** — existing installs update in place exactly as before.

Because the key is unchanged it is still recoverable from this repository's own history, which is how the `KEYSTORE_BASE64` secret is populated:

```bash
git show e95b75c:debug.keystore.base64 | tr -d '\n' | gh secret set KEYSTORE_BASE64
```

History is deliberately left intact, precisely because that is where the key now lives. Still keep a private, off-git backup: history can be lost, and regenerating a key does not restore update compatibility — it permanently breaks every existing install.

### Release Verification Pipeline

1. **Keystore Integrity Checks**:
   - `KeystoreIntegrityTest` runs in the local and CI JVM unit test suite. It asserts the inverse of the old check: no key material is committed, `.gitignore` blocks keystore extensions, and the workflow sources both the key and its passwords from secrets rather than literals.
   - The workflow step `Restore the release signing key from secrets` decodes `KEYSTORE_BASE64`, fails with an actionable message if any secret is unset, and validates the `upload` alias via `keytool`.
2. **Post-Build APK Verification**:
   - The workflow executes `Verify APK Release Signing Integrity` using Android SDK's `apksigner` on `anisequel-universal.apk` to validate signature block integrity and certificate fingerprints before publishing.
3. **Release Asset Distribution**:
   - The workflow attaches the signed APK to GitHub Releases and updates `update.json` for automatic update notifications within the app.

### Building a signed release locally

```bash
export KEYSTORE_PATH=/path/to/your/anisequel-release.jks
export STORE_PASSWORD='<your store password>'
export KEY_PASSWORD='<your key password>'
./gradlew assembleRelease
```

---

## Building Locally

### Prerequisites
- JDK 21 (Temurin / OpenJDK)
- Android SDK (API 34 / Build-Tools 34.0.0+)
- Gradle 9.3+ (managed via Gradle Wrapper)

### Commands

```bash
# Run all JVM unit tests and integrity assertions
./gradlew testDebugUnitTest

# Build debug APK
./gradlew assembleDebug

# Build signed release APK
./gradlew assembleRelease
```

---

## Architecture

- **UI Layer**: Jetpack Compose, Material Design 3 Expressive, StateFlow & ViewModel architecture.
- **Domain Layer**: Clean Architecture UseCases (`FindMissedSequelsUseCase`, `SaveToPlanningUseCase`, `GetViewerProfileUseCase`).
- **Data Layer**: Ktor / OkHttp Network client, GraphQL queries with rate-limit coalescing, DataStore preferences.
- **Testing**: JUnit 4, Robolectric, and unit test suites covering GraphQL mapping, error handling, rate limiting, and version baseline integrity.
