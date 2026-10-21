package com.anisequel.ui.screens.settings

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
 * waste the one place the app can be funny about an outage that no other screen
 * is showing yet.
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
    var seenProblem by remember { mutableStateOf(false) }
    var celebrating by remember { mutableStateOf(false) }
    var touchingGrass by remember { mutableStateOf(false) }
    // The first frame has to read as "asking", not as "unknown". The probe's
    // initial UNKNOWN would otherwise flash the broken heart for one frame
    // before the check even starts, which reads as a failure that then fixes
    // itself - exactly the false alarm this dialog exists to avoid.
    var requested by remember { mutableStateOf(false) }

    // Opening the dialog is asking for a fresh answer, not a cached one.
    LaunchedEffect(Unit) {
        requested = true
        probe.probe()
    }

    LaunchedEffect(status.reachability, status.isChecking) {
        if (status.isChecking || status.reachability == ApiReachability.UNKNOWN) {
            return@LaunchedEffect
        }
        if (seenProblem && status.reachability.isReachable) {
            celebrating = true
            delay(2_100)
            celebrating = false
        }
        seenProblem = !status.reachability.isReachable
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

                    status.reachability.isReachable -> AwakeContent(
                        reachability = status.reachability,
                        detail = status.reachability.detail(status.latencyMillis),
                        quip = status.reachability.friendlyCopy()
                            .quipFor(status.reachability.name)
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

@Composable
private fun AwakeContent(
    reachability: ApiReachability,
    detail: String,
    quip: String
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
    Spacer(modifier = Modifier.height(10.dp))
    Text(
        text = quip,
        style = MaterialTheme.typography.bodySmall,
        fontStyle = FontStyle.Italic,
        color = AniSequelTheme.statusColors.success,
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
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = copy.quipFor("${reachability.name}:$latencyMillis"),
        style = MaterialTheme.typography.bodySmall,
        fontStyle = FontStyle.Italic,
        color = MaterialTheme.colorScheme.tertiary,
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
