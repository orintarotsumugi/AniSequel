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

    var joke by remember { mutableStateOf<String?>(null) }
    val latestSettings by rememberUpdatedState(settings)

    LaunchedEffect(Unit) {
        DiscordPresenceCommands.newJokeRequested.collect {
            joke = DiscordJokes.next(latestSettings, joke)
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
            return@LaunchedEffect
        }
        joke = DiscordJokes.next(latestSettings, joke)
        if (!settings.jokeInterval.schedules) return@LaunchedEffect
        while (isActive) {
            delay(settings.jokeInterval.minutes * 60_000L)
            // Re-read latest settings/tones/custom lines at tick time.
            joke = DiscordJokes.next(latestSettings, joke)
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

    val client = remember { DiscordRpcClient() }
    var lastLiveToken by remember { mutableStateOf<String?>(null) }
    var publishJob by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(settings.enabled, settings.token, settings.linked) {
        if (!settings.enabled) {
            publishJob?.cancel()
            DiscordPresenceRuntime.update(DiscordRuntimeState.Disabled)
            val token = lastLiveToken ?: settings.token
            if (token.isNotBlank()) {
                runCatching { client.clear(token) }
                runCatching { client.disconnect() }
            }
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
                        if (!latestSettings.enabled || latestSettings.token != token || !latestSettings.canPublish) {
                            runCatching { client.clear(token) }
                            runCatching { client.disconnect() }
                            lastLiveToken = null
                        } else {
                            lastLiveToken = token
                            DiscordPresenceRuntime.update(
                                DiscordRuntimeState.Live(result.getOrNull()?.username ?: "Discord")
                            )
                        }
                    } else if (latestSettings.enabled && latestSettings.canPublish && latestSettings.token == token) {
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
