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

Every release is deterministically signed using the committed key material stored in `debug.keystore.base64` to prevent `INSTALL_FAILED_UPDATE_INCOMPATIBLE` errors on device updates.

### Release Verification Pipeline

1. **Keystore Integrity Checks**:
   - `KeystoreIntegrityTest` runs as part of the local and CI JVM unit test suite to ensure `debug.keystore.base64` is valid and present.
   - GitHub Actions workflow step `Restore the committed release signing key` decodes and tests alias availability via `keytool`.
2. **Post-Build APK Verification**:
   - The workflow executes `Verify APK Release Signing Integrity` using Android SDK's `apksigner` on `anisequel-universal.apk` to validate signature block integrity and certificate fingerprints before publishing.
3. **Release Asset Distribution**:
   - The workflow attaches the signed APK to GitHub Releases and updates `update.json` for automatic update notifications within the app.

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
