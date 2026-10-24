package com.anisequel.ui.screens.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.anisequel.data.network.ApiReachability
import com.anisequel.data.network.friendlyCopy
import com.anisequel.ui.components.ApiStatusCard
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.HeartRepairScene
import com.anisequel.ui.components.HeartbreakScene
import com.anisequel.ui.components.expressive.ExpressiveLoadingIndicator
import com.anisequel.ui.components.expressive.bouncyPress
import com.anisequel.ui.theme.AniSequelTheme
import com.anisequel.ui.viewmodel.ApiStatusProbeState
import com.anisequel.ui.viewmodel.ApiStatusUi
import kotlinx.coroutines.delay

/**
 * The Settings-side reachability check, with the same heart as the error screen.
 *
 * ## Why a dialog rather than a row that silently updates
 *
 * The check is a request and, when it fails, a *moment*: the heart breaks, the
 * status explains itself, and there are exactly two honest things to do - try
 * again or go outside. Hiding that behind a row whose text quietly changes would
 * waste the one place the app explains an outage that no other screen is showing
 * yet.
 *
 * ## Why the check has no random jokes
 *
 * It used to borrow the failure copy's quip pool, so opening Settings and asking
 * a direct diagnostic question could be answered with a joke about naps. The
 * ambient toast already owns the app's random voice; a check the reader started
 * on purpose is a moment for the verdict, not for a reroll. Every line here is
 * fixed copy that says what happened, and "go touch grass" stays because it is
 * an exit rather than a punchline.
 *
 * ## Why the repair animation only plays on a recovery
 *
 * Opening this dialog on a healthy API should not pretend there was a crisis.
 * [seenProblem] makes the glue a celebration of a transition: the first result is
 * shown as it is, and only a later green result *after a red one* gets the
 * animation. That is the "quick thing for when the server comes back" - it costs
 * no request of its own, because the reader's own retry is what triggers it.
 */
@Composable
fun ConnectionCheckDialog(
    probe: ApiStatusProbeState,
    onDismiss: () -> Unit
) {
    val status by probe.ui.collectAsState()
    val context = LocalContext.current
    var seenProblem by remember { mutableStateOf(false) }
    var celebrating by remember { mutableStateOf(false) }
    var touchingGrass by remember { mutableStateOf(false) }
    // The first frame has to read as "asking", not as "unknown". The probe's
    // initial UNKNOWN would otherwise flash the broken heart for one frame
    // before the check even starts, which reads as a failure that then fixes
    // itself - exactly the false alarm this dialog exists to avoid.
    var requested by remember { mutableStateOf(false) }

    // A check that threw is the one failure the dialog cannot draw as a verdict:
    // there is no latency and no reachability to report. It used to be
    // indistinguishable from "the question has not been answered
    // yet", so a broken probe looked exactly like a slow one and the reader
    // tapped Check again with no idea anything had gone wrong. The toast says
    // the check itself failed; the dialog stays up and stays usable, because the
    // retry is `probe()` and nothing here is one-shot.
    //
    // Seeded with the count already on the clock so reopening the dialog after
    // an old failure does not toast it a second time, and keyed on the *count*
    // rather than the message because retrying the same broken check produces
    // the same message - an effect keyed on an unchanged string would stay
    // silent for exactly the tap the reader just made.
    var toastedFailures by remember { mutableStateOf(status.failureCount) }
    LaunchedEffect(status.failureCount) {
        val failures = status.failureCount
        if (failures <= toastedFailures) return@LaunchedEffect
        toastedFailures = failures
        // Fixed copy rather than the raw exception: the message from a probe that
        // threw is a socket or a parser talking, not something a reader can act
        // on. No random quip here - this is a diagnostic the reader asked for,
        // not an ambient joke, and a reroll would obscure whether the check ran.
        Toast.makeText(
            context,
            "The check didn't finish. Try again in a moment.",
            Toast.LENGTH_LONG
        ).show()
    }

    // Opening the dialog is asking for a fresh answer, not a cached one.
    LaunchedEffect(Unit) {
        requested = true
        probe.probe()
    }

    LaunchedEffect(status.reachability, status.isChecking) {
        if (status.isChecking || status.reachability == ApiReachability.UNKNOWN) {
            return@LaunchedEffect
        }
        val recovered = seenProblem && status.reachability.isReachable
        // Recorded before the celebration starts, so the next result is judged
        // against *this* one rather than against whatever the state was before the
        // delay below. It used to be written after the delay, which made the order
        // depend on whether the effect survived to the end of it.
        seenProblem = !status.reachability.isReachable
        if (recovered) celebrating = true
    }

    // Timed separately on purpose. Keyed on the status, the effect above is
    // cancelled the moment another check starts, and a `celebrating` flag set
    // inside it stayed true forever when that happened - the dialog then drew the
    // repair scene with no Close button and no way out of it.
    LaunchedEffect(celebrating) {
        if (!celebrating) return@LaunchedEffect
        delay(2_100)
        celebrating = false
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
            modifier = Modifier
                .widthIn(max = 380.dp)
                .testTag("api_status_dialog")
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                when {
                    celebrating -> RepairContent()
                    !requested || status.isChecking -> CheckingContent()
                    touchingGrass -> GrassContent(
                        onBack = {
                            touchingGrass = false
                            probe.probe()
                        },
                        onClose = onDismiss
                    )

                    // A check that never produced a verdict is not a failure. It
                    // used to fall into the broken-heart branch below, so a probe
                    // that could not run announced "Ani-chan is confused" beside a
                    // heart breaking over a question nobody had answered yet.
                    status.reachability == ApiReachability.UNKNOWN -> UnknownContent(
                        onCheck = { probe.probe() }
                    )

                    status.reachability.isReachable -> AwakeContent(
                        reachability = status.reachability,
                        detail = status.reachability.detail(status.latencyMillis)
                    )

                    else -> BrokenContent(
                        probe = probe,
                        reachability = status.reachability,
                        latencyMillis = status.latencyMillis,
                        isChecking = status.isChecking,
                        onTouchGrass = { touchingGrass = true }
                    )
                }

                if (!celebrating) {
                    Spacer(modifier = Modifier.height(12.dp))
                    TextButton(onClick = onDismiss) { Text("Close") }
                }
            }
        }
    }
}

@Composable
private fun RepairContent() {
    HeartRepairScene()
    Text(
        text = "Ani-chan is back",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = "The heartbeat is answered. Good as new, slightly sticky.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )
}

@Composable
private fun CheckingContent() {
    Spacer(modifier = Modifier.height(8.dp))
    ExpressiveLoadingIndicator(
        modifier = Modifier.size(48.dp),
        color = MaterialTheme.colorScheme.primary
    )
    Spacer(modifier = Modifier.height(16.dp))
    Text(
        text = "Calling AniList...",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = "One tiny query. No list entries were harmed.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )
}

/**
 * The honest "no verdict" state.
 *
 * Not the same thing as "down": the request never produced an answer the probe
 * could classify, so the dialog says so, offers the check again, and leaves the
 * heart whole. Showing the break scene here was a claim about AniList that the
 * app had not earned.
 */
@Composable
private fun UnknownContent(onCheck: () -> Unit) {
    Icon(
        imageVector = AppVectorIcons.Info,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(52.dp)
    )
    Spacer(modifier = Modifier.height(14.dp))
    Text(
        text = ApiReachability.UNKNOWN.label,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center
    )
    Spacer(modifier = Modifier.height(6.dp))
    Text(
        text = ApiReachability.UNKNOWN.detail(),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )
    Spacer(modifier = Modifier.height(10.dp))
    Text(
        text = "Nothing is broken. The question just has not been answered yet.",
        style = MaterialTheme.typography.bodySmall,
        fontStyle = FontStyle.Italic,
        color = MaterialTheme.colorScheme.tertiary,
        textAlign = TextAlign.Center
    )
    Spacer(modifier = Modifier.height(16.dp))
    Button(
        onClick = onCheck,
        modifier = Modifier.bouncyPress(pressedScale = 0.96f)
    ) {
        Text("Check now")
    }
}

@Composable
private fun AwakeContent(
    reachability: ApiReachability,
    detail: String
) {
    Icon(
        imageVector = AppVectorIcons.HeartMended,
        contentDescription = null,
        tint = AniSequelTheme.statusColors.success,
        modifier = Modifier.size(64.dp)
    )
    Spacer(modifier = Modifier.height(14.dp))
    Text(
        text = reachability.label,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center
    )
    Spacer(modifier = Modifier.height(6.dp))
    Text(
        text = detail,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )
}

@Composable
private fun BrokenContent(
    probe: ApiStatusProbeState,
    reachability: ApiReachability,
    latencyMillis: Long?,
    isChecking: Boolean,
    onTouchGrass: () -> Unit
) {
    val copy = reachability.friendlyCopy()

    HeartbreakScene()
    Text(
        text = copy.headline,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center
    )
    Spacer(modifier = Modifier.height(6.dp))
    Text(
        text = copy.detail,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )
    Spacer(modifier = Modifier.height(16.dp))
    ApiStatusCard(
        status = ApiStatusUi(
            reachability = reachability,
            latencyMillis = latencyMillis,
            isChecking = isChecking
        ),
        onCheckAgain = { probe.probe() },
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(16.dp))
    Button(
        onClick = { probe.probe() },
        modifier = Modifier.bouncyPress(pressedScale = 0.96f)
    ) {
        Text("Try again")
    }
    TextButton(onClick = onTouchGrass) {
        Icon(
            imageVector = AppVectorIcons.Grass,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.size(8.dp))
        Text("Go touch grass")
    }
}

@Composable
private fun GrassContent(onBack: () -> Unit, onClose: () -> Unit) {
    Icon(
        imageVector = AppVectorIcons.Grass,
        contentDescription = null,
        tint = AniSequelTheme.statusColors.success,
        modifier = Modifier.size(60.dp)
    )
    Spacer(modifier = Modifier.height(12.dp))
    Text(
        text = "Go touch grass",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center
    )
    Spacer(modifier = Modifier.height(6.dp))
    Text(
        text = "Ani-chan will still be here. The grass will not touch itself.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )
    Spacer(modifier = Modifier.height(16.dp))
    Button(
        onClick = onBack,
        modifier = Modifier.bouncyPress(pressedScale = 0.96f)
    ) {
        Text("I'm back")
    }
    TextButton(onClick = onClose) { Text("Not now") }
}
