package com.anisequel.ui.screens.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import com.anisequel.ui.components.expressive.ExpressiveMotion
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.anisequel.data.repository.CustomFontStore
import com.anisequel.data.repository.DEFAULT_TITLE_SCROLL_DP_PER_SECOND
import com.anisequel.data.repository.MAX_FONT_SCALE
import com.anisequel.data.repository.MAX_TITLE_SCROLL_DP_PER_SECOND
import com.anisequel.data.repository.MIN_FONT_SCALE
import com.anisequel.data.repository.MIN_TITLE_SCROLL_DP_PER_SECOND
import com.anisequel.data.repository.ThemePreferences
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.LocalTitleMotion
import com.anisequel.ui.components.MarqueeText
import com.anisequel.ui.components.expressive.ExpressiveShapes
import com.anisequel.ui.components.expressive.expressiveShape
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Type & reading: the reader's own font, size and title motion.
 *
 * A page of its own rather than rows on Appearance, because these are the three
 * choices that change what the app is like to *read* rather than what it looks
 * like - and because the samples need room to play. Every control commits on
 * release, not on every drag tick: a DataStore write per frame would put a disk
 * round trip inside the animation the slider is driving.
 *
 * The samples are the point of the page. A size slider without text to look at,
 * or a scroll-speed slider without a long title, asks the reader to set a number
 * and then go find out what it did.
 */
@Composable
fun TypeSettingsTab(
    themePreferences: ThemePreferences,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val themeSettings by themePreferences.settings.collectAsState(
        initial = ThemePreferences.ThemeSettings(
            themeMode = com.anisequel.data.repository.ThemeMode.DEFAULT,
            useDynamicColor = false
        )
    )

    var importError by remember { mutableStateOf<String?>(null) }
    var importing by remember { mutableStateOf(false) }

    // `*/*` rather than `font/*`: plenty of document providers do not advertise a
    // font category at all, and a picker that hides every .ttf on the device is
    // worse than one that shows too much. `CustomFontStore` validates the bytes,
    // so the filter would only have been a convenience.
    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        importError = null
        importing = true
        scope.launch {
            CustomFontStore.import(context, uri).fold(
                onSuccess = { font ->
                    themePreferences.setCustomFont(path = font.path, name = font.name)
                },
                onFailure = { failure ->
                    importError = failure.message
                        ?: "That file could not be read as a font."
                }
            )
            importing = false
        }
    }

    SettingsScrollColumn(modifier) {
        TextSizeSection(
            themePreferences = themePreferences,
            committedScale = themeSettings.fontScale,
            scope = scope
        )

        SectionCard(
            title = "Font",
            icon = AppVectorIcons.TextBold,
            subtitle = "The face every screen is drawn in",
            iconShape = expressiveShape(ExpressiveShapes.diamond)
        ) {
            Text(
                text = themeSettings.customFontName
                    ?.takeIf { it.isNotBlank() && themeSettings.customFontPath != null }
                    ?: if (themeSettings.customFontPath != null) {
                        "Imported font"
                    } else {
                        "System font"
                    },
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = if (themeSettings.customFontPath != null) {
                    "Imported into the app, so it survives the file being moved or " +
                        "the app that supplied it being uninstalled."
                } else {
                    "Import a .ttf or .otf file. It is copied into the app, not " +
                        "linked, so the choice is permanent."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (importing) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Importing\u2026",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            importError?.let { message ->
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalButton(
                    onClick = { picker.launch(arrayOf("*/*")) },
                    enabled = !importing,
                    modifier = Modifier.testTag("import_font_button")
                ) {
                    Text("Choose a font file")
                }
                if (themeSettings.customFontPath != null) {
                    TextButton(
                        onClick = {
                            importError = null
                            scope.launch {
                                CustomFontStore.clear(context)
                                themePreferences.setCustomFont(path = null, name = null)
                            }
                        },
                        enabled = !importing
                    ) {
                        Text("Use the system font")
                    }
                }
            }
        }

        TitleMotionSection(
            themePreferences = themePreferences,
            committedSpeed = themeSettings.titleScrollDpPerSecond,
            scope = scope
        )
    }
}

@Composable
private fun TextSizeSection(
    themePreferences: ThemePreferences,
    committedScale: Float,
    scope: CoroutineScope
) {
    // Draft state so the drag is smooth and the sample follows the finger; the
    // DataStore write happens once, when the thumb is released.
    var draft by remember(committedScale) {
        mutableFloatStateOf(committedScale.coerceIn(MIN_FONT_SCALE, MAX_FONT_SCALE))
    }
    val ratio = if (committedScale > 0f) draft / committedScale else 1f
    // The sample scales in the draw phase rather than by re-styling every text
    // run: multiplying `fontSize` on each drag tick re-laid-out the paragraph on
    // every frame, which made the preview jump and reflow instead of read like a
    // zoom. One animated factor smooths it, and the committed write still updates
    // the real scale on release.
    val previewScale by animateFloatAsState(
        targetValue = ratio,
        animationSpec = ExpressiveMotion.FastSpatial,
        label = "text_size_preview"
    )

    SectionCard(
        title = "Text size",
        icon = AppVectorIcons.Tune,
        subtitle = "Scales every text style in the app",
        // A four-sided cookie on the tile: a fixed square is the one surface a
        // RoundedPolygon may outline - see ExpressiveShapes.
        iconShape = expressiveShape(ExpressiveShapes.cookie)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${(draft * 100).roundToInt()}%",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            if (draft != 1f) {
                TextButton(onClick = {
                    draft = 1f
                    scope.launch { themePreferences.setFontScale(1f) }
                }) {
                    Text("Reset")
                }
            }
        }

        // The sample is drawn at the *committed* scale already, so multiplying by
        // the draft/committed ratio shows the would-be size live without waiting
        // for the release write - and without rebuilding the whole type scale
        // every frame.
        Column(
            modifier = Modifier.graphicsLayer {
                scaleX = previewScale
                scaleY = previewScale
                transformOrigin = TransformOrigin(0f, 0f)
            }
        ) {
            SampleLine(
                text = "A long title scrolls here, so nothing is cut",
                base = MaterialTheme.typography.titleLarge
            )
            SampleLine(
                text = "Body text at the same size, because a bigger title without " +
                    "bigger reading text would be half a setting.",
                base = MaterialTheme.typography.bodyMedium
            )
        }

        Slider(
            value = draft,
            onValueChange = { draft = it },
            onValueChangeFinished = {
                val target = draft
                scope.launch { themePreferences.setFontScale(target) }
            },
            valueRange = MIN_FONT_SCALE..MAX_FONT_SCALE,
            // Nine stops (85% to 130% in fives): fine enough to matter, coarse
            // enough that every stop is a deliberate size rather than a pixel nudge.
            steps = 8,
            modifier = Modifier.testTag("text_size_slider")
        )
    }
}

@Composable
private fun SampleLine(text: String, base: androidx.compose.ui.text.TextStyle) {
    Text(
        text = text,
        style = base,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(vertical = 4.dp)
    )
}

@Composable
private fun TitleMotionSection(
    themePreferences: ThemePreferences,
    committedSpeed: Float,
    scope: CoroutineScope
) {
    var draft by remember(committedSpeed) {
        mutableFloatStateOf(
            committedSpeed.coerceIn(
                MIN_TITLE_SCROLL_DP_PER_SECOND,
                MAX_TITLE_SCROLL_DP_PER_SECOND
            )
        )
    }

    SectionCard(
        title = "Long titles",
        icon = AppVectorIcons.SectionMotion,
        subtitle = "Scroll a name that does not fit instead of cutting it"
    ) {
        Text(
            text = "A title too long for its row scrolls once it has rested, at the " +
                "speed below. Titles that fit stay still.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(10.dp))

        // The sample uses the draft speed directly, so the slider's effect is the
        // thing moving in front of the reader rather than a number. The motion style
        // still decides whether anything moves at all - a sample that scrolled under
        // Instant would promise a behaviour the app does not have.
        MarqueeText(
            text = "That Time I Got Reincarnated as a Slime Season 3 Part 2",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("title_scroll_sample"),
            motion = LocalTitleMotion.current.copy(velocity = draft.dp)
        )
        if (!LocalTitleMotion.current.animates) {
            Text(
                text = "Motion style is Instant, so titles stay still in the app.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${draft.roundToInt()} dp/s",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            if (draft != DEFAULT_TITLE_SCROLL_DP_PER_SECOND) {
                TextButton(onClick = {
                    draft = DEFAULT_TITLE_SCROLL_DP_PER_SECOND
                    scope.launch {
                        themePreferences.setTitleScrollSpeed(
                            DEFAULT_TITLE_SCROLL_DP_PER_SECOND
                        )
                    }
                }) {
                    Text("Reset")
                }
            }
        }

        Slider(
            value = draft,
            onValueChange = { draft = it },
            onValueChangeFinished = {
                val target = draft
                scope.launch { themePreferences.setTitleScrollSpeed(target) }
            },
            valueRange = MIN_TITLE_SCROLL_DP_PER_SECOND..MAX_TITLE_SCROLL_DP_PER_SECOND,
            modifier = Modifier.testTag("title_scroll_slider")
        )
    }
}
