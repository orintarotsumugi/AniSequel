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
import com.anisequel.data.discord.DiscordPresenceRuntime
import com.anisequel.data.discord.DiscordPresenceCommands
import com.anisequel.data.discord.DiscordRpcClient
import com.anisequel.data.discord.DiscordRuntimeState
import com.anisequel.data.repository.DiscordPreferences
import com.anisequel.domain.discord.DiscordPresenceComposer
import com.anisequel.domain.discord.DiscordSettings
import com.anisequel.domain.discord.DiscordStats
import com.anisequel.ui.components.discord.DiscordActivityBus
import com.anisequel.ui.components.discord.DiscordJokes
import kotlinx.coroutines.delay

/**
 * Draws nothing, and is the only thing that publishes.
 *
 * Mounted once at the navigation root, beside the bar, the seasonal sky and the
 * joke toast: the presence describes the whole app, so it cannot live inside a
 * screen that is disposed when its tab changes. Being a sibling of `NavHost`
 * rather than a parameter also means a route change recomposes this small
 * composable, not the destination table.
 *
 * ## What it does not do
 *
 * It does not compose a new presence every second. The value changes when the
 * page, the list or the joke changes, and each of those is a real publish.
 * Discord's own timer runs from the start timestamp in the value.
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

    // The page name is the fallback activity's whole content, so it is updated
    // even when Discord is off: turning the switch on should not say "Home" for
    // as long as it takes to navigate somewhere.
    LaunchedEffect(route) {
        DiscordActivityBus.browse(discordPageStatus(route))
    }

    var joke by remember { mutableStateOf<String?>(null) }
    val latestSettings by rememberUpdatedState(settings)

    // A quick action on the Settings page, routed here so the preview and the
    // published line cannot quote two different jokes.
    LaunchedEffect(Unit) {
        DiscordPresenceCommands.newJokeRequested.collect {
            joke = DiscordJokes.next(latestSettings, joke)
        }
    }

    // Restarts on any switch that changes what the pool is or how often it
    // changes. The first line is drawn immediately rather than after one whole
    // interval: a ten-minute wait is indistinguishable from a broken feature.
    LaunchedEffect(
        settings.showJokes,
        settings.jokeInterval,
        settings.activeJokeTones,
        settings.customJokes
    ) {
        if (!settings.jokesScheduled) {
            joke = null
            return@LaunchedEffect
        }
        joke = DiscordJokes.next(settings, joke)
        if (!settings.jokeInterval.schedules) return@LaunchedEffect
        while (true) {
            delay(settings.jokeInterval.minutes * 60_000L)
            joke = DiscordJokes.next(settings, joke)
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

    // The Settings preview reads this rather than composing a second opinion
    // about the joke that is currently live.
    LaunchedEffect(presence) {
        DiscordPresenceRuntime.updatePresence(presence)
    }

    val client = remember { DiscordRpcClient() }
    // Remembered across the disabled emission so switching the feature off can
    // withdraw the activity from the relay it was actually sent to.
    var publishedRelay by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(settings.enabled) {
        if (settings.enabled) return@LaunchedEffect
        DiscordPresenceRuntime.update(DiscordRuntimeState.Disabled)
        publishedRelay?.let { relay -> client.clear(relay) }
        publishedRelay = null
    }

    // The relay URL and the application id are keys in their own right: linking a
    // *different* relay leaves `canPublish` true and the composed card unchanged,
    // so without them the old relay would keep the presence and the new one would
    // never see it - while Settings happily said "Connected".
    LaunchedEffect(
        settings.canPublish,
        settings.relayUrl,
        settings.applicationId,
        presence
    ) {
        when {
            !settings.enabled -> {
                DiscordPresenceRuntime.update(DiscordRuntimeState.Disabled)
            }
            !settings.canPublish -> {
                DiscordPresenceRuntime.update(DiscordRuntimeState.PreviewOnly)
            }
            else -> {
                DiscordPresenceRuntime.update(DiscordRuntimeState.Publishing)
                // A new relay is linked while the old one is still holding an
                // activity: withdraw it first, or the old relay shows a stale
                // presence until Discord times it out.
                publishedRelay
                    ?.takeIf { it != settings.relayUrl }
                    ?.let { previous -> client.clear(previous) }

                val result = client.publish(
                    rawBaseUrl = settings.relayUrl,
                    applicationId = settings.applicationId,
                    presence = presence
                )
                if (result.isSuccess) {
                    // The switch can be flipped while the POST is in flight;
                    // cancellation does not stop a blocking `execute()`. The
                    // latest value, not the one this effect started with,
                    // decides whether the relay is allowed to keep the card.
                    if (!latestSettings.enabled) {
                        client.clear(settings.relayUrl)
                        publishedRelay = null
                    } else {
                        publishedRelay = settings.relayUrl
                        DiscordPresenceRuntime.update(
                            DiscordRuntimeState.Live(settings.relayUrl)
                        )
                    }
                } else if (latestSettings.enabled) {
                    // A cancelled publish that fails because the reader turned the
                    // feature off must not paint an error over the off state; the
                    // clear path is the one that matters.
                    DiscordPresenceRuntime.update(
                        DiscordRuntimeState.Failed(
                            result.exceptionOrNull()?.message
                                ?: "The Discord relay did not answer."
                        )
                    )
                }
            }
        }
    }
}

/**
 * The presence's word for a route.
 *
 * A pure mapping rather than a `when` inside the host so a new destination is one
 * line here - and so the fallback is obvious. It deliberately returns the app's
 * name for anything unrecognised: a presence that says "Browsing AniSequel" is
 * true on every screen this app has, including the ones added later.
 */
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
