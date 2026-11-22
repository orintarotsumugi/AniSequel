package com.anisequel.data.repository

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The reads a cold start waits for, started together and before the window exists.
 *
 * ## The failure this fixes
 *
 * Opening AniSequel used to cost a *chain* of small reads, each one waiting for
 * the one above it:
 *
 *  1. `MainActivity` collects the theme, the interface settings and the app-lock
 *     choice, and draws the branded placeholder until they answer. Those three
 *     start together, so this is one read's worth of time.
 *  2. Only then does `AppNavigation` compose. It collects the stored session and
 *     the first-run answer behind a *second* placeholder, so the reader watched
 *     two different splash screens in sequence - and the session read is the
 *     expensive one, because it is a DataStore read *plus* a hardware-keystore
 *     decrypt of the bearer token.
 *  3. Only then is the dashboard's ViewModel built, and its first paint waits on
 *     two more disk reads: the saved viewer and the saved list, which is the one
 *     snapshot measured in megabytes.
 *
 * Each hop costs the same tens of milliseconds whether or not anything else is
 * happening, so the whole launch paid for them serially while the CPU sat idle
 * waiting on a flash read. Nothing in that chain *depends* on the hop above it -
 * the session, the first-run answer and the saved list are all independent of
 * the theme - so the serialisation was an artifact of when each flow was first
 * collected, not of the data.
 *
 * This reads all of them at process start, concurrently, and keeps the four
 * answers that a screen needs *before* its first frame in memory. By the time
 * the Activity composes, the stores are parsed and their flows answer from
 * memory, and the two splash screens resolve on the first frame instead of after
 * a file read each.
 *
 * ## Why it is safe
 *
 * Every read here is a read: nothing is written, nothing is invalidated, and the
 * same DataStore instance serves the warm read and every collector afterwards -
 * so a warm value cannot disagree with the first emission a screen sees, and a
 * value that changes while the app runs still arrives through the flow. The
 * stored values are exposed as the *initial* values of the flows a screen
 * collects, not as replacements for them.
 *
 * A failed read is not an error. [begin] is best-effort by construction: if a
 * warm read throws or never finishes, the property stays `null` and the screen
 * falls back to the placeholder it drew before this existed. That is also why
 * every launch read is wrapped rather than awaited - nothing may block the
 * process start on an optimisation.
 *
 * ## What is deliberately not warmed
 *
 * The detail snapshot ([AniListRepositoryImpl.SNAPSHOT_DETAIL]) is left alone.
 * It is only read when a title is opened, and holding it from launch would spend
 * the reader's disk and memory on a page they may never visit - while competing
 * with the two reads above for the store's lock, which is the one thing that
 * cannot be allowed to slow the launch path.
 */
object StartupWarmup {

    /**
     * Process-scoped and never cancelled.
     *
     * The work belongs to the process rather than to an Activity: a warm read
     * that outlives the Activity that could never have started it yet is the
     * whole point, and `SupervisorJob` keeps one failed store from cancelling the
     * rest of the batch.
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** The stored theme, or null until (or unless) it has been read. */
    @Volatile
    var themeSettings: ThemePreferences.ThemeSettings? = null
        private set

    /** The stored interface settings, or null. See [themeSettings]. */
    @Volatile
    var interfaceSettings: InterfacePreferences.InterfaceSettings? = null
        private set

    /** Whether the reader asked for the app lock, or null. See [themeSettings]. */
    @Volatile
    var appLockEnabled: Boolean? = null
        private set

    /** Whether the first-run setup is done, or null. See [themeSettings]. */
    @Volatile
    var onboardingCompleted: Boolean? = null
        private set

    /**
     * The stored bearer token, and whether the read has answered at all.
     *
     * Two properties rather than one nullable token, because "no session" and
     * "not known yet" are different answers and `MainActivity` may not guess
     * between them - see [com.anisequel.ui.viewmodel.seedAuthState].
     */
    @Volatile
    var authResolved: Boolean = false
        private set

    @Volatile
    var authToken: String? = null
        private set

    /**
     * Starts every launch read. Call once from `Application.onCreate`.
     *
     * The session is launched first: it is the slowest of the batch (a DataStore
     * read plus the keystore decrypt) and the one the second launch gate waits
     * for, so it gets the head start.
     */
    fun begin(context: Context, authRepository: AuthRepository) {
        val app = context.applicationContext

        scope.launch {
            runCatching { authRepository.accessTokenFlow.first() }
                .onSuccess { token ->
                    authToken = token
                    authResolved = true
                }
        }

        scope.launch {
            runCatching { ThemePreferences(app).settings.first() }
                .onSuccess { themeSettings = it }
        }

        scope.launch {
            runCatching { InterfacePreferences(app).settings.first() }
                .onSuccess { interfaceSettings = it }
        }

        scope.launch {
            runCatching { AppLockPreferences(app).enabled.first() }
                .onSuccess { appLockEnabled = it }
        }

        scope.launch {
            runCatching { OnboardingPreferences(app).completed.first() }
                .onSuccess { onboardingCompleted = it }
        }

        // The rest are read for the same reason but nothing seeds a screen from
        // them: `AppNavigation` collects all of these on its first composition,
        // and a warm store answers a collector without touching the disk. One
        // flow per file is enough - DataStore caches the parsed file, so the
        // other keys in the same file come back from memory.
        arrayOf<suspend () -> Unit>(
            { HiddenSequelsPreferences(app).hiddenIds.first() },
            { RefreshIntervalPreferences(app).interval.first() },
            { ArrivingPreferences(app).settings.first() },
            { QuickAddPreferences(app).settings.first() },
            { JokePreferences(app).settings.first() },
            { LibraryPreferences(app).lastType.first() },
            { MainTabPreferences(app).tierListTab.first() },
            { SeasonalPreferences(app).settings.first() },
            { MediaPreferences(app).settings.first() }
        ).forEach { read ->
            scope.launch { runCatching { read() } }
        }

        // The disk tier under the dashboard's first paint. Read in one coroutine,
        // viewer first: the viewer is what makes the list's key knowable, so a
        // launch that reaches the repository before this finishes still waits for
        // the same file it would have waited for anyway - it just waits here,
        // while the window is still being inflated, instead of after the first
        // frame.
        scope.launch {
            val snapshots = AniListSnapshots.of(app)
            runCatching { snapshots.prefetch(AniListRepositoryImpl.SNAPSHOT_VIEWER) }
            runCatching { snapshots.prefetch(AniListRepositoryImpl.SNAPSHOT_LIST) }
        }
    }
}
