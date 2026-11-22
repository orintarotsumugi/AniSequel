package com.anisequel.ui.screens.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag

/**
 * One part of a settings page that has outgrown a single scroll.
 *
 * Implemented on the page's own enum so the shelf row and the sub-page's header
 * come from the same two strings - the drift the old tabs shipped ("timing and
 * placement" opening a page that said "timing, tone and saved lines") is
 * impossible rather than unlikely when there is only one copy of the sentence.
 *
 * [testTag] is the shelf row's tag, and is also what the page's tests find the
 * sub-page back arrow by - one name per part, not one per drawing.
 */
internal interface SettingsShelfSection {
    val title: String
    val subtitle: String
    val icon: ImageVector
    val testTag: String
}

/**
 * A settings page as a shelf, one level deep.
 *
 * ## Why one implementation
 *
 * Interface and Appearance had each grown their own copy of this: a nullable
 * section state, a `BackHandler` enabled only while a part is open, a
 * `rememberSaveableStateHolder` so a part's scroll survives a trip back to the
 * shelf, an `AnimatedContent` with the shared push/pop, and a header that names
 * whatever level is on screen. Four pages were about to make that six copies,
 * and the copies had already drifted: one of them announced "Back to Card design"
 * from a level that was two steps out.
 *
 * ## What it does *not* do
 *
 * No nesting. Every part is exactly one level below the page, which is what made
 * this a single composable rather than a stack. The one page that had a third
 * level - Interface's "Card design", holding shape, name and poster - now lists
 * those three parts beside its other rows, and the level that existed only to
 * group them is gone. A reader who wants the card's shape is one tap from the
 * page's shelf, not two.
 *
 * ## Back
 *
 * A part's arrow returns to the shelf; the shelf's returns to Settings. Both are
 * generated from [hubTitle], so no page carries a hand-written "Back to …" that
 * can name a screen the arrow does not reach. The `BackHandler` is registered
 * only while a part is open, which is what lets Back on the shelf fall through to
 * `SettingsScreen` and close Settings.
 */
@Composable
internal fun <T> SettingsShelf(
    hubTitle: String,
    hubSubtitle: String,
    /** A part a Settings search result asked for; consumed once. */
    initialSection: T?,
    onInitialSectionConsumed: () -> Unit,
    /** Leaves the page for the Settings shelf. */
    onClosePage: () -> Unit,
    /** Test tag for the animated container, e.g. "jokes_tab_content". */
    contentTag: String,
    /**
     * The shelf body: a [SettingsScrollColumn] with the page's groups.
     *
     * A lambda taking the open callback rather than a composable per section,
     * because the shelf is the only place that knows which rows exist.
     */
    hub: @Composable (onOpen: (T) -> Unit) -> Unit,
    /** The body of one part. */
    body: @Composable (T) -> Unit,
    modifier: Modifier = Modifier
) where T : Enum<T>, T : SettingsShelfSection {
    var section by rememberSaveable { mutableStateOf<T?>(null) }

    LaunchedEffect(initialSection) {
        if (initialSection != null) {
            section = initialSection
            onInitialSectionConsumed()
        }
    }

    // The shelf and the parts are separate compositions and `AnimatedContent`
    // disposes the outgoing one, so without this a part's scroll started at the top
    // every time the reader came back to it - and so did the shelf's.
    val stateHolder = rememberSaveableStateHolder()

    BackHandler(enabled = section != null) { section = null }

    AnimatedContent(
        targetState = section,
        transitionSpec = { settingsLevelTransition() },
        label = "settings_shelf_transition",
        modifier = modifier
            .fillMaxSize()
            .testTag(contentTag)
    ) { destination ->
        stateHolder.SaveableStateProvider(key = destination?.name ?: "hub") {
            Column(modifier = Modifier.fillMaxSize()) {
                // Exactly one header: the page's on the shelf, the part's inside a
                // part. Drawing the page header around its own shelf is what used to
                // stack three titles before the first control.
                SettingsSubHeader(
                    title = destination?.title ?: hubTitle,
                    subtitle = destination?.subtitle ?: hubSubtitle,
                    onBack = {
                        if (destination == null) onClosePage() else section = null
                    },
                    backDescription = if (destination == null) {
                        "Back to settings"
                    } else {
                        "Back to $hubTitle"
                    },
                    backTestTag = if (destination == null) {
                        "settings_sub_back"
                    } else {
                        "settings_shelf_back"
                    }
                )
                // The body takes the rest of the screen. Without the weight a
                // `fillMaxSize` body measured against the whole page and pushed its
                // last row under the bar.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    if (destination == null) {
                        hub { section = it }
                    } else {
                        body(destination)
                    }
                }
            }
        }
    }
}

/**
 * One labelled group of parts.
 *
 * The label is `compact` because `SettingsScrollColumn` already supplies the
 * gutter and the gaps: a label there must not also indent itself and add its own
 * vertical room, or the heading lands 48dp from the edge while its card sits at
 * 20dp - the uneven rhythm the Theme page used to show.
 *
 * [values] is the value a row shows at rest, for the few parts whose whole point
 * is a fact rather than a control (the installed build). Empty by default: a part
 * whose current state is a *setting* says so in its subtitle, and a value there
 * would be a second, hand-maintained copy of the same sentence.
 */
@Composable
internal fun <T> SettingsShelfGroup(
    label: String,
    sections: List<T>,
    tone: SettingsGroupTone,
    onOpen: (T) -> Unit,
    values: Map<T, String> = emptyMap()
) where T : Enum<T>, T : SettingsShelfSection {
    SettingsSectionLabel(text = label, compact = true)
    SettingsHubGroup(
        entries = sections.map { section -> section.shelfEntry(onOpen, values[section]) },
        tone = tone
    )
}

/** The shelf row for one part - the same row design the Settings hub uses. */
internal fun <T> T.shelfEntry(
    onOpen: (T) -> Unit,
    value: String? = null
): SettingsHubEntry where T : Enum<T>, T : SettingsShelfSection = SettingsHubEntry(
    icon = icon,
    title = title,
    subtitle = subtitle,
    testTag = testTag,
    onClick = { onOpen(this) },
    value = value
)
