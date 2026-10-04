/**
 * The version a build falls back to when gradle.properties is unreadable.
 *
 * Mirrors the `anisequelVersion*` entries there. Gradle evaluates project
 * properties before this file's own body runs, so the values cannot be read
 * from gradle.properties here - they are asserted to match by
 * `VersionBaselineTest`, which is what keeps the two copies honest.
 */
val BASELINE_VERSION_NAME = "1.0.14"
val BASELINE_VERSION_CODE = 35

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
    // Android 10 (API 29) is the floor, raised from 24 (Android 7).
    //
    // API 29 is also the level from which every device understands APK
    // Signature Scheme v3 key rotation. Below it, a build signed with a
    // rotated key can never be installed over the previous one, so raising
    // the floor is what keeps a future key rotation from permanently
    // stranding installs on older devices.
    //
    // Raising this drops support for Android 7, 8 and 9. Devices already
    // running an older AniSequel cannot install later builds; they stay on
    // the last version that supported them.
    minSdk = 29
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
      // The release workflow decodes the keystore from the KEYSTORE_BASE64
      // secret and points these variables at it. The key is deliberately not in
      // the repository: it used to be committed as debug.keystore.base64, which
      // let anyone who cloned the repo sign an update APK.
      //
      // The passwords are read from the environment and nothing else. This used
      // to fall back to a literal, and that literal was *wrong* - it could not
      // open the real keystore - so the only thing it could ever produce was a
      // build that either failed obscurely or, worse, signed with whatever
      // keystore happened to match it. Guessing a password is never the right
      // fallback for signing material; failing is.
      //
      // Null rather than an exception on purpose. This block is evaluated for
      // every Gradle invocation, including `testDebugUnitTest` and the debug
      // build, neither of which needs the release key and neither of which has
      // the secrets. AGP only actually opens the keystore while signing a
      // release, so leaving these null keeps those tasks working and makes
      // `assembleRelease` fail with its own message when the secrets are absent.
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/release-key.jks"
      storeFile = file(keystorePath)
      storePassword = System.getenv("STORE_PASSWORD")
      keyAlias = "upload"
      keyPassword = System.getenv("KEY_PASSWORD")
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
      signingConfig = signingConfigs.getByName("release")
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
      // Compress `classes.dex` instead of storing it verbatim.
      //
      // When minSdk >= 28 the Android Gradle Plugin deliberately stores dex
      // *uncompressed* so the runtime can map it straight out of the APK without
      // extracting it. That optimisation is invisible until the numbers move:
      // raising minSdk from 24 to 29 in v1.0.17 switched it on, and the APK went
      // from 2.31 MB to 4.15 MB in one release. The code itself had got
      // *smaller* - `classes.dex` fell from 4,028,848 to 3,953,880 uncompressed
      // bytes - and was simply being shipped at 3.95 MB instead of the 2.0 MB it
      // deflates to. Every entry in the APK was checked; this one accounts for
      // the entire increase.
      //
      // The trade-off is real and deliberate: compressing means the installer
      // has to decompress the dex into a second copy on disk, so the *installed*
      // footprint grows and installing takes marginally longer. This app is
      // distributed as a sideloaded APK from a GitHub release, so the download
      // is what every user waits on and the device's own storage is not the
      // constraint - "APK small, installed size large" is the right way round
      // here. Set this to false to go back to the mapped-dex behaviour.
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
  implementation(libs.androidx.compose.ui.tooling.preview)
  // ui-tooling is a debug artifact. It was declared as `implementation` as well
  // as `debugImplementation` below, so the release APK shipped a second copy of
  // the layout inspector it never uses.
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
  // "ksp"(libs.androidx.room.compiler)
  "ksp"(libs.moshi.kotlin.codegen)
}
