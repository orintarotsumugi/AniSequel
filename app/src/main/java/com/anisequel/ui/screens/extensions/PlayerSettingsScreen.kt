package com.anisequel.ui.screens.extensions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anisequel.data.extensions.AudioEngineMode
import com.anisequel.data.extensions.DecoderMode
import com.anisequel.data.extensions.PlayerPreferences
import com.anisequel.data.extensions.PlayerSettings
import com.anisequel.data.extensions.RendererMode
import com.anisequel.data.extensions.ShaderProfile
import com.anisequel.ui.components.AppVectorIcons

/** Which choice dialog is open. A sealed set because the four share one layout. */
private sealed interface PlayerDialog {
    data object Decoder : PlayerDialog
    data object Renderer : PlayerDialog
    data object Audio : PlayerDialog
    data object Cache : PlayerDialog
    data object Shader : PlayerDialog
}

/**
 * The Player Settings page.
 *
 * Decoder, renderer and audio are real Media3/Android choices where the player
 * can honour them, and a stored preference otherwise - the page does not claim a
 * decoder switch changed codec selection when it did not. The 4K section is
 * honest about what it is: a colour-grade filter on the video view, with a real
 * visible effect, while true spatial upscaling is Phase 3.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerSettingsScreen(
    playerPreferences: PlayerPreferences,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val settings by playerPreferences.settings.collectAsStateWithLifecycle()
    var dialog by remember { mutableStateOf<PlayerDialog?>(null) }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("player_settings_screen"),
        topBar = {
            TopAppBar(
                title = { Text("Player Settings", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(AppVectorIcons.Back, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding()),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item(key = "playback_header") { SectionHeader("PLAYBACK") }
            item(key = "decoder") {
                PlayerRow(
                    icon = AppVectorIcons.Memory,
                    title = "Decoder",
                    value = settings.decoder.label,
                    onClick = { dialog = PlayerDialog.Decoder }
                )
            }
            item(key = "renderer") {
                PlayerRow(
                    icon = AppVectorIcons.VideoSettings,
                    title = "Video Renderer",
                    value = settings.renderer.label,
                    onClick = { dialog = PlayerDialog.Renderer }
                )
            }
            item(key = "audio") {
                PlayerRow(
                    icon = AppVectorIcons.GraphicEq,
                    title = "Audio Engine",
                    value = settings.audio.label,
                    onClick = { dialog = PlayerDialog.Audio }
                )
            }
            item(key = "pitch") {
                PlayerToggleRow(
                    icon = AppVectorIcons.GraphicEq,
                    title = "Audio Pitch Correction",
                    subtitle = "Keep voice pitch stable at higher speeds",
                    checked = settings.audioPitchCorrection,
                    onCheckedChange = playerPreferences::setAudioPitchCorrection
                )
            }
            item(key = "cache") {
                PlayerRow(
                    icon = AppVectorIcons.CacheTimer,
                    title = "Cache Minutes",
                    value = "${settings.cacheMinutes} min read-ahead",
                    onClick = { dialog = PlayerDialog.Cache }
                )
            }

            item(key = "enhancement_header") {
                SectionHeader("ANIME 4K ENHANCEMENT")
            }
            item(key = "enhancement") {
                EnhancementCard(
                    settings = settings,
                    onToggle = { playerPreferences.setEnhance(it) },
                    onChooseProfile = { dialog = PlayerDialog.Shader }
                )
            }

            item(key = "note") {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Beta, and what it changes",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "The Enhancement switch applies a colour-grade filter to the video " +
                                "surface - saturation and contrast, per the selected profile. It is " +
                                "not a spatial upscaler; a real shader pass is Phase 3. Decoder " +
                                "and renderer choices are applied to the next playback. Audio pitch " +
                                "and cache minutes are applied to the next episode. An A/V offset " +
                                "and frame interpolation are deliberately not exposed: Media3 has no " +
                                "cross-device implementation, and a stored toggle would be a lie.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(10.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "High-End is the default profile. A mid-range phone can use Mid-End " +
                                "without losing playback - the filter is a single colour matrix, " +
                                "not a per-frame GPU shader chain.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    when (dialog) {
        PlayerDialog.Decoder -> ChoiceDialog(
            title = "Decoder",
            options = DecoderMode.entries.map { Choice(it.label, it.detail) },
            selected = settings.decoder.label,
            onSelect = { label ->
                DecoderMode.entries.firstOrNull { it.label == label }
                    ?.let(playerPreferences::setDecoder)
                dialog = null
            },
            onDismiss = { dialog = null }
        )
        PlayerDialog.Renderer -> ChoiceDialog(
            title = "Video Renderer",
            options = RendererMode.entries.map { Choice(it.label, it.detail) },
            selected = settings.renderer.label,
            onSelect = { label ->
                RendererMode.entries.firstOrNull { it.label == label }
                    ?.let(playerPreferences::setRenderer)
                dialog = null
            },
            onDismiss = { dialog = null }
        )
        PlayerDialog.Audio -> ChoiceDialog(
            title = "Audio Engine",
            options = AudioEngineMode.entries.map { Choice(it.label, it.detail) },
            selected = settings.audio.label,
            onSelect = { label ->
                AudioEngineMode.entries.firstOrNull { it.label == label }
                    ?.let(playerPreferences::setAudio)
                dialog = null
            },
            onDismiss = { dialog = null }
        )
        PlayerDialog.Cache -> ChoiceDialog(
            title = "Cache Minutes",
            options = listOf(1, 2, 3, 5, 10).map {
                Choice(
                    label = "$it min",
                    detail = "Read-ahead buffer before the file is paused to rebuffer"
                )
            },
            selected = "${settings.cacheMinutes} min",
            onSelect = { label ->
                label.substringBefore(' ').toIntOrNull()
                    ?.let(playerPreferences::setCacheMinutes)
                dialog = null
            },
            onDismiss = { dialog = null }
        )
        PlayerDialog.Shader -> ChoiceDialog(
            title = "Shader Profile",
            options = ShaderProfile.entries.map { Choice(it.label, it.blurb) },
            selected = settings.shaderProfile.label,
            onSelect = { label ->
                ShaderProfile.entries.firstOrNull { it.label == label }
                    ?.let(playerPreferences::setShaderProfile)
                dialog = null
            },
            onDismiss = { dialog = null }
        )
        null -> Unit
    }
}

@Composable
private fun EnhancementCard(
    settings: PlayerSettings,
    onToggle: (Boolean) -> Unit,
    onChooseProfile: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Icon(
                        AppVectorIcons.Visibility,
                        contentDescription = null,
                        modifier = Modifier.padding(12.dp).size(22.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Anime 4K Enhancement",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Real-time colour grade for anime content",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = settings.enhance, onCheckedChange = onToggle)
            }
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onChooseProfile)
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    AppVectorIcons.Play,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Choose shader profile",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "Mid End = e.g. GTX 980, GTX 1060, RX 570",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        settings.shaderProfile.label.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
                Spacer(Modifier.width(4.dp))
                Icon(
                    AppVectorIcons.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun PlayerRow(
    icon: ImageVector,
    title: String,
    value: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.padding(10.dp).size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    value,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                AppVectorIcons.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PlayerToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.padding(10.dp).size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 6.dp, top = 12.dp, bottom = 2.dp)
    )
}

private data class Choice(val label: String, val detail: String)

@Composable
private fun ChoiceDialog(
    title: String,
    options: List<Choice>,
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                options.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(option.label) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = option.label == selected,
                            onClick = { onSelect(option.label) }
                        )
                        Spacer(Modifier.width(4.dp))
                        Column {
                            Text(option.label, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                option.detail,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}
