pluginManagement {
  repositories {
    google {
      content {
        includeGroupByRegex("com\\.android.*")
        includeGroupByRegex("com\\.google.*")
        includeGroupByRegex("androidx.*")
      }
    }
    mavenCentral()
    gradlePluginPortal()
  }
}

plugins {
  id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
  // Gives this file the `android { }` block below, which is where R8 is told to
  // run in its own JVM. The version has to be the same AGP the modules use -
  // it is the same product, published under a settings-plugin marker - and the
  // duplication is the reason `R8BuildConfigTest` asserts it against
  // `gradle/libs.versions.toml` rather than trusting the two to be bumped
  // together.
  id("com.android.settings") version "9.4.1"
}

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    google()
    mavenCentral()
  }
}

rootProject.name = "AniSequel"

include(":app")

/*
 * R8 runs in its own JVM, with its own heap, instead of inside the Gradle daemon.
 *
 * ## What went wrong
 *
 * `minifyReleaseWithR8` is 40-90% of a release build's APK step, and its wall time
 * swings by an order of magnitude for byte-identical input. Measured on `main`:
 *
 *     run 44706092338   R8 task     936s   (job 17m00s)
 *     run 44862685755   R8 task      80s   (job  3m01s)   same branch, 85 min later
 *     run 45066437072   R8 task     123s   (job  8m03s)
 *     run 45112123803   R8 task    1102s   (job 24m21s)
 *
 * Between those last two the diff was 620 lines in Settings and nothing in the
 * dependency graph, the APK grew 19 KB, and the two runs executed an *identical*
 * Gradle task graph - so the 18 minutes was not the change being built. The Kotlin
 * compile in the same pair took 38s and 64s, i.e. the machine was not simply
 * slower, and the R8 step is where every minute of the outlier went.
 *
 * R8 is the largest allocation source in this build and it ran in the daemon's
 * 6 GB heap, sharing that heap with an in-process Kotlin compiler (see
 * `kotlin.compiler.execution.strategy` in gradle.properties) that has just
 * allocated its way through 200k lines of source, and with Robolectric's forked
 * test JVM beside it. So how much room R8 had to work in depended on the compile
 * in front of it rather than on what it was asked to do - which is the only
 * mechanism that fits the numbers above. It is inference from the timings rather
 * than a log line: R8 does not report its heap, and no run printed an
 * OutOfMemoryError, a GC storm, or anything else to point at. What the change
 * below does is remove the sharing, so that the next 18-minute run - if one comes
 * back - is R8's own behaviour rather than a neighbour's, which is the distinction
 * this investigation could not draw.
 *
 * `-Xmx6g` here, matching the daemon, because the runner has 16 GB and the two
 * JVMs are never both busy: the daemon is idle while the R8 worker runs. Raise
 * this line if a run still spends minutes in R8; do not raise the daemon's
 * `org.gradle.jvmargs` to do the same job, because that heap is shared with the
 * Kotlin compiler and Robolectric's fork and does not belong to R8 alone.
 *
 * Parallel GC for the same reason the daemon uses it: this JVM is a batch
 * compiler with no frame to miss, so throughput beats pause targets.
 *
 * `R8BuildConfigTest` fails if this profile loses `runInSeparateProcess` or its
 * heap, because the symptom of losing it is not a broken build - it is a build
 * that takes twenty minutes again and nothing in the log saying why.
 */
android {
  execution {
    // Stated rather than inferred: AGP falls back to the only declared profile
    // today, but adding a second one later would make an unset default an error.
    defaultProfile = "r8OutOfProcess"
    profiles {
      create("r8OutOfProcess") {
        r8 {
          runInSeparateProcess = true
          jvmOptions.addAll(listOf("-Xmx6g", "-XX:+UseParallelGC"))
        }
      }
    }
  }
}
