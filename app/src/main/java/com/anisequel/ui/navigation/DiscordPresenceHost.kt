package com.anisequel.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.anisequel.data.discord.DiscordPresenceCommands
import com.anisequel.data.discord.DiscordPresenceRuntime
import com.anisequel.data.discord.DiscordRpcClient
import com.anisequel.data.discord.DiscordRuntimeState
import com.anisequel.data.repository.DiscordPreferences
import com.anisequel.domain.discord.DiscordPresence
import com.anisequel.domain.discord.DiscordPresenceComposer
import com.anisequel.domain.discord.DiscordSettings
import com.anisequel.domain.discord.DiscordStats
import com.anisequel.ui.components.discord.DiscordActivityBus
import com.anisequel.ui.components.discord.DiscordJokes
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Publishes Discord Rich Presence directly to Discord from the app root.
 */
@Composable
fun DiscordPresenceHost(
    navController: NavHostController,
    preferences: DiscordPreferences,
    stats: DiscordStats
) {
    val settings by preferences.settings.collectAsState(initial = DiscordSettings())
    val activity by DiscordActivityBus.activity.collectAsState()
    val entry by navController.currentBackStackEntryAsState()
    val route = entry?.destination?.route

    LaunchedEffect(route) {
        DiscordActivityBus.browse(discordPageStatus(route))
    }

    // Read here, not through a helper that returns the line: a state read inside
    // another composable invalidates only that scope, so the host would never
    // recompose the presence below when only the joke changed.
    var joke by remember { mutableStateOf<String?>(null) }
    // The last few lines, so a rotation does not redraw one it quoted two ticks
    // ago. Three is enough to stop the "it keeps saying the same thing" feeling
    // without making the pool predictable.
    var recentJokes by remember { mutableStateOf<List<String>>(emptyList()) }
    val latestSettings by rememberUpdatedState(settings)

    fun pickJoke(): String? {
        val next = DiscordJokes.next(latestSettings, recentJokes)
        if (next != null) recentJokes = (recentJokes + next).takeLast(3)
        return next
    }

    LaunchedEffect(Unit) {
        DiscordPresenceCommands.newJokeRequested.collect {
            joke = pickJoke()
        }
    }

    // Stabilize list/set keys so unrelated DataStore emissions don't restart rotation.
    val jokeTonesKey = remember(settings.activeJokeTones) {
        settings.activeJokeTones.map { it.storageValue }.sorted().joinToString("|")
    }
    val customJokesKey = remember(settings.customJokes) { settings.customJokes.joinToString("\u0000") }

    LaunchedEffect(
        settings.showJokes,
        settings.jokeInterval,
        jokeTonesKey,
        customJokesKey
    ) {
        if (!settings.jokesScheduled) {
            joke = null
            recentJokes = emptyList()
            return@LaunchedEffect
        }
        joke = pickJoke()
        if (!settings.jokeInterval.schedules) return@LaunchedEffect
        while (isActive) {
            delay(settings.jokeInterval.minutes * 60_000L)
            // Re-read latest settings/tones/custom lines at tick time.
            joke = pickJoke()
        }
    }

    val presence = remember(settings, activity, stats, joke) {
        DiscordPresenceComposer.compose(
            settings = settings,
            activity = activity,
            stats = stats,
            joke = joke
        )
    }

    LaunchedEffect(presence) {
        DiscordPresenceRuntime.updatePresence(presence)
    }

    DiscordPresencePublisher(settings = settings, presence = presence)
}

/**
 * Keeps the gateway socket and the published presence in step with the settings.
 *
 * Split from [DiscordPresenceHost] because it owns the three things a presence
 * change must not see - the client, the token the live presence went out with,
 * and the publish in flight - and the host owns none of them.
 */
@Composable
private fun DiscordPresencePublisher(
    settings: DiscordSettings,
    presence: DiscordPresence
) {
    val client = remember { DiscordRpcClient() }
    var lastLiveToken by remember { mutableStateOf<String?>(null) }
    var publishJob by remember { mutableStateOf<Job?>(null) }
    val latestSettings by rememberUpdatedState(settings)

    LaunchedEffect(settings.enabled, settings.token, settings.linked) {
        if (!settings.enabled) {
            publishJob?.cancel()
            DiscordPresenceRuntime.update(DiscordRuntimeState.Disabled)
            val token = lastLiveToken ?: settings.token
            if (token.isNotBlank()) {
                runCatching { client.clear(token) }
            }
            // Unconditionally, not only when a token is present: logging out
            // clears the token, and the old build guarded this on a non-blank one,
            // leaving the gateway socket open after a sign-out.
            runCatching { client.disconnect() }
            lastLiveToken = null
        }
    }

    val publishKey = remember(
        settings.canPublish,
        settings.token,
        settings.linked,
        settings.onlineStatus,
        settings.activityType,
        settings.reduceNetworkChatter,
        presence
    ) {
        arrayOf<Any?>(
            settings.canPublish,
            settings.token,
            settings.linked,
            settings.onlineStatus,
            settings.activityType,
            settings.reduceNetworkChatter,
            presence.details,
            presence.state,
            presence.largeImage,
            presence.largeText,
            presence.startTimestampSeconds,
            presence.activityName,
            presence.buttons.joinToString { "${it.label}|${it.url}" },
            presence.joke
        ).contentHashCode()
    }

    LaunchedEffect(publishKey) {
        when {
            !settings.enabled -> DiscordPresenceRuntime.update(DiscordRuntimeState.Disabled)
            !settings.canPublish -> {
                publishJob?.cancel()
                runCatching { client.disconnect() }
                lastLiveToken = null
                DiscordPresenceRuntime.update(DiscordRuntimeState.PreviewOnly)
            }
            else -> {
                publishJob?.cancel()
                DiscordPresenceRuntime.update(DiscordRuntimeState.Connecting)
                val token = settings.token
                // Small debounce to coalesce rapid edits and reduce network chatter.
                if (settings.reduceNetworkChatter) delay(350L)
                publishJob = launch {
                    val result = client.publish(
                        rawToken = token,
                        presence = presence,
                        status = latestSettings.onlineStatus,
                        activityType = latestSettings.activityType
                    )
                    if (!isActive) return@launch
                    if (result.isSuccess) {
                        if (!latestSettings.stillLiveWith(token)) {
                            runCatching { client.clear(token) }
                            runCatching { client.disconnect() }
                            lastLiveToken = null
                        } else {
                            lastLiveToken = token
                            DiscordPresenceRuntime.update(
                                DiscordRuntimeState.Live(result.getOrNull()?.username ?: "Discord")
                            )
                        }
                    } else if (latestSettings.stillLiveWith(token)) {
                        DiscordPresenceRuntime.update(
                            DiscordRuntimeState.Failed(
                                result.exceptionOrNull()?.message
                                    ?: "Discord did not accept the presence."
                            )
                        )
                    }
                }
            }
        }
    }
}

/**
 * Whether a presence published with [token] is still the one the reader wants.
 *
 * A publish can land after the settings moved on - disabled, logged out, or the
 * token replaced - and its result must be withdrawn rather than painted as live.
 */
private fun DiscordSettings.stillLiveWith(token: String): Boolean =
    enabled && canPublish && this.token == token

internal fun discordPageStatus(route: String?): String = when {
    route == AppRoutes.DASHBOARD ||
        route == AppRoutes.DASHBOARD_DEMO ||
        route == AppRoutes.DASHBOARD_USER -> "Home"
    route == AppRoutes.LIBRARY -> "the Library"
    route == AppRoutes.SEARCH -> "catalogue search"
    route == AppRoutes.TIER_LIST -> "the tier list"
    route == AppRoutes.OTHER -> "Other"
    route == AppRoutes.SCHEDULE -> "the schedule"
    route == AppRoutes.SETTINGS -> "Settings"
    route == AppRoutes.LOGIN -> "AniSequel"
    route == ProfileRoutes.OWN_ROUTE -> "your profile"
    route?.startsWith("profile?") == true -> "a profile"
    route?.startsWith("studio/") == true -> "a studio page"
    route?.startsWith("tier_list/") == true -> "the tier list"
    else -> "AniSequel"
}
