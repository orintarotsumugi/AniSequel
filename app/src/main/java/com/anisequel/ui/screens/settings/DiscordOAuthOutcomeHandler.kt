package com.anisequel.ui.screens.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import com.anisequel.data.discord.DiscordOAuthApi
import com.anisequel.data.discord.DiscordOAuthFlow
import com.anisequel.data.repository.DiscordPreferences
import com.anisequel.domain.discord.DiscordAuthKind
import com.anisequel.domain.discord.DiscordOAuth
import com.anisequel.domain.discord.DiscordSettings

/**
 * Finishes a Discord sign-in once Discord has sent the code back.
 *
 * ## Why this is not part of the dialog
 *
 * The reader authorizes in the device's browser, so the callback arrives while the
 * page - not the dialog - is what exists. A dialog dismissed behind the browser, or
 * a process that rebuilt its Activity around a fresh composition, would both lose
 * an exchange written into the dialog's own state: the account would never link and
 * nothing would say why. The outcome therefore lives in [DiscordOAuthFlow], and this
 * effect is mounted by the page.
 *
 * ## Why a failure is reported to the page
 *
 * [onError] runs on the page, not in the dialog, for the same reason: a refusal that
 * arrives after the dialog closed is still a refusal the reader has to see.
 */
@Composable
internal fun DiscordOAuthOutcomeHandler(
    preferences: DiscordPreferences,
    settings: DiscordSettings,
    onBusyChange: (Boolean) -> Unit,
    onError: (String?) -> Unit,
    onLinked: () -> Unit
) {
    // Fresh settings for a coroutine that is started once: it has to see the
    // reader's current switches, not the ones this composition was born with.
    val current by rememberUpdatedState(settings)

    LaunchedEffect(Unit) {
        DiscordOAuthFlow.outcome.collect { outcome ->
            if (outcome == null) return@collect
            when (outcome) {
                is DiscordOAuthFlow.Outcome.Stale -> onError(outcome.message)
                is DiscordOAuthFlow.Outcome.Ready -> when (val redirect = outcome.redirect) {
                    is DiscordOAuth.Redirect.Failure -> onError(redirect.message)
                    DiscordOAuth.Redirect.Ignored -> Unit
                    is DiscordOAuth.Redirect.Code -> {
                        onBusyChange(true)
                        onError(null)
                        DiscordOAuthApi.shared.exchange(
                            clientId = outcome.session.clientId,
                            code = redirect.code,
                            codeVerifier = outcome.session.verifier
                        ).fold(
                            onSuccess = { granted ->
                                // A grant without `identify` still returns a token, so
                                // the account is read before anything is stored: a
                                // credential that cannot name the account it belongs
                                // to is not a login, and storing it would paint
                                // "Connected to Discord" over nothing.
                                DiscordOAuthApi.shared.me(granted.accessToken).fold(
                                    onSuccess = { account ->
                                        preferences.link(
                                            token = granted.accessToken,
                                            username = account.username,
                                            userId = account.userId,
                                            avatarUrl = account.avatarUrl,
                                            authKind = DiscordAuthKind.OAUTH
                                        )
                                        if (!current.enabled) preferences.setEnabled(true)
                                        onBusyChange(false)
                                        onError(null)
                                        onLinked()
                                    },
                                    onFailure = { failure ->
                                        onBusyChange(false)
                                        onError(
                                            failure.message ?: "Discord did not send an account."
                                        )
                                    }
                                )
                            },
                            onFailure = { failure ->
                                onBusyChange(false)
                                onError(
                                    failure.message ?: "Discord did not complete the sign-in."
                                )
                            }
                        )
                    }
                }
            }
            DiscordOAuthFlow.consume()
        }
    }
}
