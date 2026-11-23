package com.anisequel.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anisequel.data.repository.CustomFontStore
import com.anisequel.data.repository.DEFAULT_TITLE_SCROLL_DP_PER_SECOND
import com.anisequel.data.repository.InterfacePreferences
import com.anisequel.data.repository.MAX_FONT_SCALE
import com.anisequel.data.repository.MAX_TITLE_SCROLL_DP_PER_SECOND
import com.anisequel.data.repository.MIN_FONT_SCALE
import com.anisequel.data.repository.MIN_TITLE_SCROLL_DP_PER_SECOND
import com.anisequel.data.repository.SystemFont
import com.anisequel.data.repository.ThemePreferences
import com.anisequel.domain.ui.DisplayDpi
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.LocalTitleMotion
import com.anisequel.ui.components.MarqueeText
import com.anisequel.ui.components.expressive.ExpressiveLoadingIndicator
import com.anisequel.ui.components.expressive.ExpressiveMotion
import com.anisequel.ui.components.expressive.ExpressivePolygonSegmentedBar
import com.anisequel.ui.components.expressive.ExpressiveShapes
import com.anisequel.ui.components.expressive.SegmentedOption
import com.anisequel.ui.components.expressive.expressiveShape
import com.anisequel.ui.components.rememberBoundSliderState
import com.anisequel.ui.theme.rememberDeviceDensityDpi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * The Text half of Type & display: the reader's own font, size and title motion.
 *
 * ## Why it is a section body rather than a page
 *
 * Text size and display density were two pages, and inline "Display size" on one
 * of them linked to the other - the code already knew the question was one
 * question. Both now live behind one row on the shelf, so "make everything
 * bigger" has one place to start and a visible choice at the top of it.
 *
 * ## Why every control commits on release
 *
 * A DataStore write per drag tick would put a disk round trip inside the animation
 * the slider is driving.
 *
 * The samples are the point of the section. A size slider without text to look
 * at, or a scroll-speed slider without a long title, asks the reader to set a
 * number and then go find out what it did.
 */
@Composable
internal fun TypeTextSectionBody(
    themePreferences: ThemePreferences,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val themeSettings by themePreferences.settings.collectAsStateWithLifecycle(
        initialValue = ThemePreferences.ThemeSettings(
            themeMode = com.anisequel.data.repository.ThemeMode.DEFAULT,
            useDynamicColor = true
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
            try {
                CustomFontStore.import(context, uri).fold(
                    onSuccess = { font ->
                        themePreferences.setCustomFont(path = font.path, name = font.name)
                    },
                    onFailure = { failure ->
                        importError = failure.message
                            ?: "That file could not be read as a font."
                    }
                )
            } finally {
                // Cancellation used to leave the button disabled forever if the
                // Settings screen left composition during a large copy.
                importing = false
            }
        }
    }

    SettingsScrollColumn(modifier) {
        TextSizeSection(
            themePreferences = themePreferences,
            committedScale = themeSettings.fontScale,
            scope = scope
        )

        Spacer(modifier = Modifier.height(16.dp))
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
                        "${themeSettings.systemFont.displayName} system font"
                    },
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(2.dp))
            CollapsibleText(
                text = when {
                    CustomFontStore.isDownloaded(themeSettings.customFontPath) ->
                        "Downloaded from Google Fonts and kept in the app's own storage, " +
                            "so switching away and back does not download it again."
                    themeSettings.customFontPath != null ->
                        "Imported into the app, so it survives the file being moved or " +
                            "the app that supplied it being uninstalled."
                    else ->
                        "Import a .ttf or .otf file, or download a family below. " +
                            "Either choice is copied into the app, not linked."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // The five faces Android ships for an app to ask for by name. This is
            // the choice that applies to every screen immediately; an imported
            // file replaces it until it is removed, and the system choice stays
            // underneath so removing the file returns to it rather than to
            // Default.
            Spacer(modifier = Modifier.height(12.dp))
            SettingsFieldLabel(text = "System font")
            Spacer(modifier = Modifier.height(8.dp))
            ExpressivePolygonSegmentedBar(
                options = SystemFont.entries.map { SegmentedOption(label = it.displayName) },
                selectedIndex = SystemFont.entries.indexOf(themeSettings.systemFont),
                onSelect = { index ->
                    scope.launch {
                        themePreferences.setSystemFont(SystemFont.entries[index])
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("system_font_row")
            )
            Spacer(modifier = Modifier.height(6.dp))
            CollapsibleText(
                text = "Default, Sans Serif, Serif, Monospace and Cursive are the " +
                    "faces the system already has, so every one renders on every " +
                    "device. An imported file replaces this choice until you remove it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            AnimatedVisibility(
                visible = importing,
                enter = fadeIn(animationSpec = ExpressiveMotion.FastEffects) +
                    scaleIn(animationSpec = ExpressiveMotion.FastSpatial),
                exit = fadeOut(animationSpec = ExpressiveMotion.FastEffects) +
                    scaleOut(animationSpec = ExpressiveMotion.FastSpatial)
            ) {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("font_import_loading")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ExpressiveLoadingIndicator(
                            modifier = Modifier.testTag("font_import_indicator"),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Column {
                            Text(
                                text = "Loading font",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = "Checking the file and applying it to the preview.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
            AnimatedVisibility(
                visible = importError != null,
                enter = fadeIn(animationSpec = ExpressiveMotion.FastEffects),
                exit = fadeOut(animationSpec = ExpressiveMotion.FastEffects)
            ) {
                importError?.let { message ->
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("font_import_error")
                    ) {
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }
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

        DownloadableFontSection(
            themePreferences = themePreferences,
            currentFontPath = themeSettings.customFontPath
        )

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
    // The sample's layout height at 100%. Scaling it in the draw phase leaves the
    // *layout* at 100%, so at 175% the last line of the sample drew across the
    // slider below the card - a preview that broke the card it was previewing.
    // Reserving the scaled overflow as real height is the fix; it is quantised to
    // 8dp so a thumb drag does not re-measure the card on every tick.
    var sampleHeightPx by remember { mutableIntStateOf(0) }
    val reservedSampleHeight = with(LocalDensity.current) {
        val overflow = (sampleHeightPx * (ratio - 1f)).coerceAtLeast(0f)
        (kotlin.math.ceil(overflow / (8f * density)) * 8f).dp
    }
    // The sample scales in the draw phase rather than by re-styling every text
    // run: multiplying `fontSize` on each drag tick re-laid-out the paragraph on
    // every frame, which made the preview jump and reflow instead of read like a
    // zoom. The committed write still happens once, on release.
    //
    // It reads the ratio directly, not through a spring. The version here animated
    // *toward* `ratio`, and `ratio` moves on every tick of the thumb - which is the
    // pattern `AGENTS.md` §3 forbids, the one that made the dashboard counter
    // restart constantly: a spring re-aimed each frame lags the finger it is supposed
    // to be following and never settles while the drag runs. A slider is the finger.
    // Only a value that changes discretely - a preset, the reset button, the
    // committed scale landing - is worth animating toward, and those arrive through
    // `draft`, which is already the source of this number.

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
            // Appearing and disappearing on a threshold, so it animates rather
            // than popping a button into a row the reader is already dragging.
            //
            // Compared as the *percentage the label shows*, not as a float. A
            // slider lands on `start + i * span / (steps + 1)`, and a five percent
            // step is not representable in binary: the tick that reads "100%" is
            // 1.0000000149, so `draft != 1f` stayed true at the default size and the
            // reset link sat there describing a modification that was not one - the
            // same fault `ThemeHub` records for "Reset appearance".
            AnimatedVisibility(
                visible = (draft * 100f).roundToInt() != 100,
                enter = fadeIn(animationSpec = ExpressiveMotion.FastEffects) +
                    scaleIn(
                        initialScale = 0.9f,
                        animationSpec = ExpressiveMotion.FastSpatial
                    ),
                exit = fadeOut(animationSpec = ExpressiveMotion.FastEffects) +
                    scaleOut(
                        targetScale = 0.9f,
                        animationSpec = ExpressiveMotion.FastSpatial
                    )
            ) {
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
            modifier = Modifier
                .onSizeChanged { sampleHeightPx = it.height }
                .graphicsLayer {
                    scaleX = ratio
                    scaleY = ratio
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
        Spacer(modifier = Modifier.height(reservedSampleHeight))

        Slider(
            state = rememberBoundSliderState(
                value = draft,
                // 75% to 175% in fives: 21 sizes, so 19 stops between the ends.
                steps = 19,
                trackRange = MIN_FONT_SCALE..MAX_FONT_SCALE
            ),
            onValueChange = { draft = it },
            onValueChangeFinished = {
                val target = draft
                scope.launch { themePreferences.setFontScale(target) }
            },
            // The stops are five percent apart, and the ends are what widened
            // (see MIN/MAX_FONT_SCALE): the span is a range a reader can actually
            // read half a dense list at and, at the top, a genuinely large size
            // rather than 140% and a shrug.
            //
            // `stateDescription` because the platform reads the raw 0.75-1.75
            // fraction, and "0.95" is not a text size a person can judge.
            modifier = Modifier
                .semantics {
                    stateDescription = "${(draft * 100).roundToInt()} percent"
                }
                .testTag("text_size_slider")
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
        CollapsibleText(
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
            AnimatedVisibility(
                // Rounded to the dp/s the label prints, for the reason the text-size
                // slider records: this track has no steps, so a drag that lands back
                // on 60 dp/s lands on 60.000004 and the reset link stayed on screen
                // over its own default.
                visible = draft.roundToInt() != DEFAULT_TITLE_SCROLL_DP_PER_SECOND.roundToInt(),
                enter = fadeIn(animationSpec = ExpressiveMotion.FastEffects) +
                    scaleIn(
                        initialScale = 0.9f,
                        animationSpec = ExpressiveMotion.FastSpatial
                    ),
                exit = fadeOut(animationSpec = ExpressiveMotion.FastEffects) +
                    scaleOut(
                        targetScale = 0.9f,
                        animationSpec = ExpressiveMotion.FastSpatial
                    )
            ) {
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
            state = rememberBoundSliderState(
                value = draft,
                trackRange = MIN_TITLE_SCROLL_DP_PER_SECOND..MAX_TITLE_SCROLL_DP_PER_SECOND
            ),
            onValueChange = { draft = it },
            onValueChangeFinished = {
                val target = draft
                scope.launch { themePreferences.setTitleScrollSpeed(target) }
            },
            // Same reason as the text-size slider: the raw value is dp/s, which
            // the platform would not read out at all helpfully.
            modifier = Modifier
                .semantics {
                    stateDescription = "${draft.roundToInt()} dp per second"
                }
                .testTag("title_scroll_slider")
        )
    }
}
