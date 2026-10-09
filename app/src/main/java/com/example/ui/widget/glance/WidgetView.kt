package com.example.ui.widget.glance

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Spacing
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.glance.unit.Dp
import androidx.glance.unit.Sp
import com.example.data.repository.DemoProfileProvider

data class WidgetState(
    val activityCount: Int,
    val subtitle: String
)

/**
 * The widget's visual surface.
 *
 * Kept intentionally small and glanceable: a title, a subtitle, and the count of
 * recent list activities from the demo fixture. The real release will replace the
 * demo fixture with the same per-user activity the app already shows on the profile
 * activity tab.
 */
@androidx.glance.compose.Composable
fun WidgetView(
    activityCount: Int,
    subtitle: String,
    onClick: () -> Unit
) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .padding(16.dp)
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = GlanceModifier.fillMaxSize(),
            verticalAlignment = androidx.glance.alignment.Alignment.Vertical.CenterVertically,
            horizontalAlignment = androidx.glance.alignment.Alignment.Horizontal.CenterHorizontally,
            spacing = Spacing.dp(8.dp)
        ) {
            Text(
                text = "AniSequel",
                style = TextStyle(
                    color = ColorProvider.DefaultText,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            )
            Text(
                text = subtitle,
                style = TextStyle(
                    color = ColorProvider.DefaultText.copy(alpha = 0.62f),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            )
            androidx.glance.layout.Spacing.dp(6.dp)
            Text(
                text = activityCount.toString(),
                style = TextStyle(
                    color = ColorProvider.DefaultContentColor,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold
                )
            )
            Text(
                text = "activities",
                style = TextStyle(
                    color = ColorProvider.DefaultText.copy(alpha = 0.7f),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            )
        }
    }
}

/**
 * Thin helper that returns a stable demo activity count for the widget.
 *
 * Exists so the widget updater does not depend on the profile ViewModel, which is
 * owned by the UI process and not available to the widget broadcast receiver.
 */
internal fun widgetActivityCount(): Int = DemoProfileProvider.getDemoActivity().size
