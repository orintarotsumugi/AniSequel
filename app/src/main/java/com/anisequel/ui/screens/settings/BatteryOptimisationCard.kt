package com.anisequel.ui.screens.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.anisequel.ui.components.AppVectorIcons

/**
 * The platform switch that lets a background check keep its schedule.
 *
 * Out of [NotificationSettingsPage] because that file was already the page's
 * three-card scaffolding plus its permission ladder, and this card is a fourth
 * platform state with its own copy - moving it keeps the page under the file
 * ceiling without splitting a job that belongs together.
 *
 * Not a [SwitchRow], deliberately: the app cannot set this, and a toggle that
 * opens a system dialog and then flips back would describe a state the app does
 * not own - the same reason the Android permission card is a status plus
 * buttons. The badge is read back from the platform on every resume.
 */
@Composable
internal fun BatteryOptimisationCard(
    exempt: Boolean,
    onRequest: () -> Unit,
    onOpenSettings: () -> Unit
) {
    SectionCard(
        title = "Battery optimisation",
        icon = AppVectorIcons.BatteryBolt,
        subtitle = "AniList has no push stream, so a check is a periodic wake-up. Android may delay it to save power; this is the one grant that stops it choosing to.",
        badge = { StatusBadge(active = exempt, onLabel = "Unrestricted", offLabel = "Optimised") }
    ) {
        Text(
            text = if (exempt) {
                "Android is leaving AniSequel's background checks alone. Delivery can still wait for a connection, and some phones apply limits of their own."
            } else {
                "While AniSequel is optimised, Android can hold a check back past its interval - sometimes for hours on a phone that has been off the charger. The exemption keeps the schedule the interval asks for."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (!exempt) {
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = onRequest,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("notification_battery_request")
            ) {
                Text("Turn off battery optimisation")
            }
        }
        // Offered in both states, unlike the request button. The platform exemption
        // is not the only manager on the phone, and a reader whose manufacturer's
        // battery screen keeps killing the check still needs a way in - with the
        // action hidden while exempt, the closing sentence below named a screen the
        // card would not open.
        TextButton(
            onClick = onOpenSettings,
            modifier = Modifier.testTag("notification_battery_settings")
        ) {
            Text("Open Android's battery settings")
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = "This is an Android dialog, not an app switch. A phone whose manufacturer ships its own battery manager may still need that screen.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
