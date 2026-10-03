# AGENTS.md

Developer & AI Agent Guidelines for AniSequel.

---

## 1. Modular Code & File Size Constraints

- **File Modularity**: Do not write monolithic Kotlin files. Keep files concise, focused, and ideally under 250–400 lines.
- **Component Separation**: Separate complex UI components into sub-packages and distinct files (e.g., `ui/components/cards/`, `ui/components/detail/`, `ui/screens/dashboard/`).
- **Single Responsibility**: Maintain clear separation between Presentation (`ui/`), Domain (`domain/usecase/`), and Data (`data/network/`, `data/repository/`).

---

## 2. Iconography & Visual Assets

- **Zero Emoji Dependencies**: Never use raw Unicode emoji characters as UI icons or status indicators.
- **Vector Icons Only**: Use pure Compose `ImageVector` definitions and SVG path representations (located in `com.example.ui.components.AppVectorIcons`, `AppCustomVectors`, and `AppExtraVectors`).
- **Semantic Naming**: Name vector assets with descriptive identifiers adhering to Android resource naming conventions.

---

## 3. Web Search Requirement

- **Verification First**: Always perform a web search or documentation query prior to introducing new third-party dependencies, Gradle plugins, or experimental APIs to ensure compatibility with Kotlin, AGP, and Android SDK versions.

---

## 4. Material 3 Expressive Motion & Animations

- **Spring Physics**: Use physics-based spring specifications from `ExpressiveMotion.kt` (`FastSpatial`, `BouncySpatial`, `SuperBouncy`, `DefaultSpatial`).
- **Draw Phase Optimizations**: Apply animations and interactive scale/translation transforms within `Modifier.graphicsLayer { ... }` or via `Modifier.bouncyPress(...)` to avoid unnecessary composition and layout passes.
- **Responsive Shapes**: Use Material 3 Expressive shapes with proper capsule geometries (`RoundedCornerShape(percent = 50)`) rather than stretched polygons.

---

## 5. Keystore & Release Signing Protocol

- **Never Commit Key Material**: The release signing key lives only in the `KEYSTORE_BASE64`, `STORE_PASSWORD` and `KEY_PASSWORD` repository secrets. Never add a `.jks`, `.p12`, `.pfx`, or base64-encoded key to the repository - a committed key lets anyone sign an "update" APK that the in-app updater accepts.
- **Never Rotate Without Meaning It**: Every published APK must share one signing key, or Android refuses the update (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`). Rotating means existing installs can never update and must uninstall. There is no undo, and git history does not help: the old key stays compromised forever, so only a new key fixes anything.
- **Never Rewrite History To Hide A Key**: Commit #9 exists because a history rewrite dropped the key. Purging history hides a key that is already compromised; it does not un-compromise it.
- **Keystore Tests**: Always ensure `KeystoreIntegrityTest` passes during builds. It guards both halves of the protocol - no key in the repo, and no hardcoded secret in the workflow.
- **CI Verification**: GitHub Actions workflow automatically verifies the APK signature and certificate fingerprints using `apksigner` prior to releasing.

---

## 6. Version Synchronization Checklist

When updating versions, ensure all three locations match:
1. `anisequelVersionName` & `anisequelVersionCode` in `gradle.properties`
2. `BASELINE_VERSION_NAME` & `BASELINE_VERSION_CODE` in `app/build.gradle.kts`
3. `version` & `version_code` in `update.json`

Verify alignment by executing `VersionBaselineTest`.
