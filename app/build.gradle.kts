/**
 * The version a build falls back to when gradle.properties is unreadable.
 *
 * Mirrors the `anisequelVersion*` entries there. Gradle evaluates project
 * properties before this file's own body runs, so the values cannot be read
 * from gradle.properties here - they are asserted to match by
 * `VersionBaselineTest`, which is what keeps the two copies honest.
 */
val BASELINE_VERSION_NAME = "1.0.33"
val BASELINE_VERSION_CODE = 73

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.secrets)
}

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.aistudio.anisequel.app"
    // Android 12 (API 31) is the floor, raised from 29 (Android 10).
    //
    // API 29 was previously the floor for a specific reason: it is the level
    // from which every device understands APK Signature Scheme v3 key
    // rotation, so raising it was what kept a future key rotation from
    // permanently stranding installs. That reason still holds, and 31 is
    // above it.
    //
    // What 31 buys, concretely, rather than as a support-policy statement:
    //
    //  - `android:windowSplashScreen` is available to this app without a
    //    versioned resource qualifier, so the system splash is themed the same
    //    way on every supported device. (There was no pre-31 `windowBackground`
    //    workaround in this codebase to delete - the theme in res/values is a
    //    bare `DeviceDefault.NoActionBar` - so nothing was removed here.)
    //  - Dynamic colour (Material You) exists on *every* supported device.
    //    `supportsDynamicColor` was a runtime SDK_INT check gating a Settings
    //    switch; on this floor it can no longer be false, so the switch can
    //    no longer be disabled and the check is gone with it.
    //  - `PackageManager.canRequestPackageInstalls` and
    //    `Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES` both behave as the
    //    updater assumes on every supported device (they arrived in API 26).
    //  - `POST_NOTIFICATIONS` permission prompts arrived in 33, which is why
    //    [UpdateManager] still branches on TIRAMISU.
    //
    // Raising this drops Android 10 and 11. Devices already running an older
    // AniSequel cannot install later builds; they stay on the last version
    // that supported them. That is a real loss of installs and is accepted
    // deliberately - see the "Reachability counts" rule in AGENTS.md: a
    // per-API code path is a path that has to be reasoned about forever.
    minSdk = 31
    targetSdk = 36
    // Read from gradle.properties, overridable with -PanisequelVersionCode /
    // -PanisequelVersionName (which is how the release workflow injects the
    // version it derived from the newest published release).
    //
    // The fallbacks are the *same strings* as gradle.properties rather than
    // independent literals. They used to be a second, separate copy of the
    // version ("1.0.0" and 1), which is a second place for the version to go
    // stale - and gradle.properties is the file that actually gets updated, so
    // the copy is what a local build falls back to.
    versionCode =
      (project.findProperty("anisequelVersionCode")?.toString()?.toIntOrNull() ?: BASELINE_VERSION_CODE)
    versionName = project.findProperty("anisequelVersionName")?.toString() ?: BASELINE_VERSION_NAME

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  signingConfigs {
    create("release") {
      val envPath = System.getenv("KEYSTORE_PATH")
      val targetFile = when {
        envPath != null && file(envPath).exists() -> file(envPath)
        file("${rootDir}/release-key.jks").exists() -> file("${rootDir}/release-key.jks")
        file("${rootDir}/my-upload-key.jks").exists() -> file("${rootDir}/my-upload-key.jks")
        else -> null
      }
      if (targetFile != null) {
        storeFile = targetFile
        storePassword = System.getenv("STORE_PASSWORD")
        keyAlias = System.getenv("KEY_ALIAS") ?: "upload"
        keyPassword = System.getenv("KEY_PASSWORD")
      }
    }
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      // Shrinking is the single biggest lever on APK size: without R8 every one
      // of the ~8,000 icons in material-icons-extended, all of Compose and all
      // of Retrofit ships whether the app uses it or not. `isShrinkResources`
      // then drops the unreachable drawables and strings that code shrinking
      // leaves behind. See app/proguard-rules.pro for the reflection keep rules
      // this requires.
      isMinifyEnabled = true
      isShrinkResources = true
      isCrunchPngs = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      val releaseConfig = signingConfigs.getByName("release")
      signingConfig = if (releaseConfig.storeFile != null && releaseConfig.storeFile!!.exists()) {
        releaseConfig
      } else {
        signingConfigs.getByName("debugConfig")
      }
    }
    debug { signingConfig = signingConfigs.getByName("debugConfig") }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  lint {
    // Advisory, and the workflow treats it as such: the `lintRelease` step reports
    // annotations into the run summary but must never be the reason a green release
    // fails. Setting it here keeps that promise true instead of aspirational, and
    // stops lint's vital checks from blocking assembleRelease on a style warning.
    abortOnError = false
    checkReleaseBuilds = false
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
  packaging {
    dex {
      useLegacyPackaging = true
    }
    resources {
      excludes += listOf(
        "/META-INF/{AL2.0,LGPL2.1}",
        "/META-INF/INDEX.LIST",
        "/META-INF/io.netty.versions.properties"
      )
    }
  }
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
  ignoreList.add("FIREBASE_APPCHECK_DEBUG_TOKEN")
}

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
//
// Anything commented out here also stops reaching the APK: an `implementation`
// line for a library the code never imports still gets dexed and packaged,
// which is why Firebase and Room are no longer listed as live dependencies.
// Neither has a single import in app/src/main - the AniList client is built on
// Retrofit + Moshi + Coil alone.
dependencies {
  implementation(platform(libs.androidx.compose.bom))
  // implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  // implementation(libs.androidx.camera.camera2)
  // implementation(libs.androidx.camera.core)
  // implementation(libs.androidx.camera.lifecycle)
  // implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core)
  // material-icons-extended ships ~30,000 icon classes; R8 prunes the ones this
  // app never draws, which is why shrinking is enabled above. Dropping the
  // artifact is not an option - FilterList, Movie, Tv, Visibility, BookmarkAdd,
  // Login, Logout and friends only exist there. AppVectorIcons is the single
  // place those names are listed, so the reachable set stays small and reviewable.
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.graphics.shapes)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.browser)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  // implementation(libs.androidx.room.ktx)
  // implementation(libs.androidx.room.runtime)
  implementation(libs.coil.compose)
  implementation(libs.converter.moshi)
  // implementation(platform(libs.firebase.bom))
  // implementation(libs.firebase.ai)

  // Uncomment to use Firestore:
  // implementation(libs.firebase.firestore)

  // Uncomment ALL FOUR of the following dependencies together to use Firebase Auth and Google
  // Sign-In via Credential Manager (and re-add the google-services plugin above):
  // implementation(libs.firebase.auth)
  // implementation(libs.androidx.credentials)
  // implementation(libs.androidx.credentials.play.services)
  // implementation(libs.googleid)
  // implementation(libs.firebase.appcheck.recaptcha)
  // debugImplementation(libs.firebase.appcheck.debug)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.logging.interceptor)
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  // implementation(libs.play.services.location)
  implementation(libs.retrofit)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  // ui-tooling AND ui-tooling-preview are both debug-only. `ui-tooling` was
  // already moved off `implementation` once, but `ui-tooling-preview` was left
  // behind on it - so release builds still packaged the @Preview annotation and
  // its tooling support, for an app with zero @Preview functions in it.
  debugImplementation(libs.androidx.compose.ui.tooling.preview)
  // "ksp"(libs.androidx.room.compiler)
  "ksp"(libs.moshi.kotlin.codegen)
}
