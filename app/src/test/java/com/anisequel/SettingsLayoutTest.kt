package com.anisequel

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Settings redesign, read off the source.
 *
 * ## What this is for
 *
 * Three of the changes here are structural, and a structural change that is half
 * done looks exactly like a finished one on a screenshot of the wrong screen:
 *
 *  - **The shelf groups its rows.** Rows inside one surface with a rule between
 *    them is what makes a section label structural. Drawn as separate cards the
 *    grouping is carried by the label alone, and the gap between two rows in a group
 *    was the same size as the gap between two groups.
 *  - **The card preview draws the real card.** A preview written separately is an
 *    advert: it can be tuned, it can be left behind, and nothing fails when it does,
 *    because nobody taps it. This pins that the preview, the Library grid and the
 *    dashboard's poster all call the one cell.
 *  - **A moved control is not drawn twice.** The Interface page took motion off the
 *    Theme page. A copy left behind means two switches over one preference, and the
 *    reader turns the one they can see while the other keeps the value.
 *
 * There is no Compose test harness in the unit-test source set, so these are read
 * from the source - the same approach as `RelationDetailResolutionTest`.
 */
class SettingsLayoutTest {

    private fun source(path: String): String {
        val file = File(findProjectRoot(), path)
        assertTrue(
            "could not read ${file.path}; this guard silently passing on a missing " +
                "file is the same failure it exists to prevent",
            file.isFile
        )
        return withoutComments(file.readText())
    }

    private val settings = "app/src/main/java/com/anisequel/ui/screens/settings/"
    private val components = "app/src/main/java/com/anisequel/ui/components/"

    private fun withoutComments(src: String): String = Regex("""/[*][\s\S]*?[*]/""")
        .replace(src) { "" }
        .lines()
        .filterNot { it.trimStart().startsWith("//") }
        .joinToString("\n")

    @Test
    fun `the shelf draws one grouped surface per section`() {
        val hub = source(settings + "SettingsHub.kt")
        assertTrue(
            "the hub has no grouped surface, so every row is still its own card",
            hub.contains("fun SettingsHubGroup(")
        )
        assertTrue(
            "a group with no divider between its rows is the old separated cards " +
                "wearing a new name",
            hub.contains("HorizontalDivider(")
        )
        assertFalse(
            "SettingsHubRow is back: one surface per row undoes the grouping",
            hub.contains("fun SettingsHubRow(")
        )

        val screen = source(settings + "SettingsScreen.kt")
        listOf(
            "SettingsSectionLabel(\"Look & layout\")",
            "SettingsSectionLabel(\"What it shows\")",
            "SettingsSectionLabel(\"Device & app\")"
        ).forEach { label ->
            assertTrue("the shelf lost its $label heading", screen.contains(label))
        }
        // A heading over a single row is the hierarchy this rework removed: it made
        // the reader parse a section with no members. The shelf has three groups and
        // every one of them holds destinations.
        assertFalse(
            "a one-row section is back, so the shelf has a label that names nothing",
            screen.contains("SettingsSectionLabel(\"Library & dashboard\")") ||
                screen.contains("SettingsSectionLabel(\"This device\")") ||
                screen.contains("SettingsSectionLabel(\"Personalisation\")")
        )
        assertTrue(
            "the rows are no longer built from the page enum, so a row can drift " +
                "from the page it opens",
            screen.contains("SettingsPage.APPEARANCE.entry(")
        )
        // The account is a card, not a row, and the connection check is the chip on
        // it - the shelf's contract is "every row opens a page", and the old action
        // row was the one exception.
        assertTrue(
            "the account is no longer the card at the top of the shelf",
            screen.contains("SettingsAccountCard(")
        )
        // The chip is drawn by the card, which lives in SettingsHub.kt; the page
        // only places the card. Reading one file here is what made this assertion
        // fail on a build where the chip was drawn correctly.
        assertTrue(
            "the connection check lost its row tag",
            screen.contains("settings_row_connection") ||
                hub.contains("settings_row_connection")
        )
        assertFalse(
            "a Connection check action row is back, so the shelf is not all pages",
            screen.contains("title = \"Connection check\"")
        )
    }

    /**
     * The shelf answers its own rows.
     *
     * This is the payoff of the redesign: "is OLED on?", "how often does the list
     * refresh?" and "is the lock set?" are answered by looking, not by opening a
     * page and pressing Back. The failure this guards is a value that is drawn for
     * some rows and silently forgotten for the rest, because nothing errors when a
     * row omits it.
     */
    @Test
    fun `every destination row carries its current value`() {
        val hub = source(settings + "SettingsHub.kt")
        assertTrue(
            "the row model has no value field, so the shelf cannot state a setting",
            hub.contains("val value: String? = null")
        )
        assertTrue(
            "the row does not draw its value",
            hub.contains("entry.value") && hub.contains("stateDescription")
        )

        val screen = source(settings + "SettingsScreen.kt")
        val summaries = source(settings + "SettingsSummaries.kt")
        listOf(
            "summaries.appearance",
            "summaries.interfaceStyle",
            "summaries.type",
            "summaries.library",
            "summaries.jokes",
            "summaries.seasonal",
            "summaries.device",
            "summaries.about"
        ).forEach { value ->
            assertTrue("the shelf stopped reading $value", screen.contains(value))
        }
        // Assembled from the same preference instances the pages write through, or
        // the row and the page could describe two different stored values.
        listOf(
            "themePreferences.settings",
            "interfacePreferences.cardStyle",
            "refreshIntervalPreferences.interval",
            "jokePreferences.settings",
            "seasonalPreferences.settings",
            "appLockPreferences.enabled"
        ).forEach { store ->
            assertTrue("the summaries stopped reading $store", summaries.contains(store))
        }
        assertFalse(
            "the hub collects preferences itself, so two screens can disagree",
            hub.contains("SettingsSummaries(")
        )
    }

    /**
     * A setting whose effect is visual is chosen by looking.
     *
     * Every page that had a preview keeps it, and the pages that gained one in this
     * rework draw it on their own shelf: the joke pill is the real toast, the library
     * bar is the real navigation bar, and the type specimen is the type the reader is
     * already reading. A preview written separately is an advert - it can be left
     * behind, and nothing fails when it is.
     */
    @Test
    fun `each settings page keeps a preview of what it changes`() {
        val jokes = source(settings + "JokeSettingsTab.kt")
        assertTrue(
            "the joke page no longer shows the toast it configures",
            jokes.contains("JokeToastPill(")
        )
        val library = source(settings + "LibrarySettingsTab.kt")
        assertTrue(
            "the library page no longer draws the bar its switches change",
            library.contains("ExpressiveNavigationBar(")
        )
        assertTrue(
            "the library bar preview is not built from the reader's own tabs",
            library.contains("mainTabDestinations(")
        )
        val type = source(settings + "TypeDisplayTab.kt")
        assertTrue(
            "the type page lost its specimen",
            type.contains("TypeSpecimenCard(")
        )
        val appearance = source(settings + "ThemeTab.kt")
        assertTrue(
            "the appearance page lost its live theme sample",
            appearance.contains("ThemePreviewCard(")
        )
        val seasonal = source(settings + "SeasonalSettingsTab.kt")
        assertTrue(
            "the seasonal page lost the sky preview that makes its sliders judgeable",
            seasonal.contains("PreviewCard(")
        )
        val iface = source(settings + "InterfaceSections.kt")
        assertTrue(
            "the card group lost the preview that is the answer to a style choice",
            iface.contains("CardDesignPreview(")
        )
    }

    /**
     * A preview of something that *acts* is drawn with the acting off.
     *
     * Both previews in Settings are the real components - that is what makes them
     * worth drawing - and both were drawn with their live behaviour intact. The
     * joke pill rippled under a thumb and dismissed nothing, announced itself to a
     * screen reader as an arriving joke and carried the real toast's test tag (two
     * nodes, whenever a joke landed while Settings was open over it); the
     * navigation-bar sample ran the real hold-and-swipe, so the bar lifted,
     * previewed a tab and switched to nothing, because its `onSelect` is a no-op.
     *
     * The fix is a switch on the component rather than a second copy of it here, so
     * this pins both halves: the components have the switch, and the samples ask for
     * the quiet mode.
     */
    @Test
    fun `previews are drawn without the behaviour they cannot serve`() {
        val jokes = source(settings + "JokeSettingsTab.kt")
        assertTrue(
            "the toast sample is drawn live again: it will ripple, dismiss nothing " +
                "and announce itself to a screen reader",
            jokes.contains("interactive = false")
        )
        assertTrue(
            "the toast sample wears the real toast's tag again, so a UI check for " +
                "the toast finds two nodes while Settings is open over it",
            jokes.contains("testTag = \"joke_pill_sample_pill\"")
        )
        val library = source(settings + "LibrarySettingsTab.kt")
        assertTrue(
            "the bar sample runs the real hold-and-swipe again, so it lifts under " +
                "the finger and switches to nothing",
            library.contains("gesturesEnabled = false")
        )
        val pill = source(components + "JokeToastPill.kt")
        assertTrue(
            "the toast has no quiet mode for a preview to ask for",
            pill.contains("interactive: Boolean = true")
        )
        val bar = source(components + "expressive/ExpressiveNavigationBar.kt")
        assertTrue(
            "the navigation bar has no quiet mode for a preview to ask for",
            bar.contains("gesturesEnabled: Boolean = true")
        )
    }

    /**
     * Typing in the search field puts the answers where the reader is looking.
     *
     * The list is one scroll shared by both modes, so the offset outlived the shelf
     * it was scrolled through: a reader who scrolled to the bottom and then typed
     * got the first results drawn above the viewport. The fix is a hoisted scroll
     * state and a spring back to the top when the mode changes, which is also the
     * animation the shelf leaves on.
     */
    @Test
    fun `typing in search returns the list to the top`() {
        val screen = source(settings + "SettingsScreen.kt")
        assertTrue(
            "the hub's scroll state is created inside `verticalScroll` again, so " +
                "nothing can return it to the top when the mode changes",
            screen.contains("val hubScroll = rememberScrollState()")
        )
        assertTrue(
            "the mode change no longer scrolls the list back to the top, so results " +
                "can be drawn above the viewport they were typed into",
            screen.contains("hubScroll.animateScrollTo(0)")
        )
    }

    /**
     * The build stamp follows the shelf and stays out of search mode.
     *
     * It was the last child of a shelf branch that 2.5.4 also wrapped in a search
     * crossfade, and that shipped a frame with the version drawn across "Device
     * Manager". The crossfade is gone - it laid every card on the one behind it, see
     * the note in `AnimationWiringTest` - so what is left to guard here is the
     * ordering: the stamp follows the shelf's guard and is never drawn above it.
     */
    @Test
    fun `the build stamp follows the shelf and stays out of search mode`() {
        val screen = source(settings + "SettingsScreen.kt")
        val guard = screen.indexOf("if (!searching) {")
        val stamp = screen.indexOf("SettingsVersionFooter(")
        assertTrue(
            "the build stamp has no guard keeping it out of the result list, so it " +
                "is back inside the mode crossfade",
            guard >= 0
        )
        assertTrue(
            "the build stamp is drawn before the shelf's guard, which means it is " +
                "still a child of the animated branch the overlap came from",
            stamp > guard
        )
    }

    /**
     * A chip's selection cannot change the space it occupies.
     *
     * The border animated from 1dp to 2dp; a border is part of layout, so choosing
     * one pill measured every pill in the row again and pushed the rest sideways.
     * The appearance tiles learned this first and record it in their own comment.
     */
    @Test
    fun `a selected chip does not resize its row`() {
        val chip = source(settings + "SettingsChoiceChip.kt")
        assertFalse(
            "the chip animates its border width again, so selecting one re-flows the " +
                "whole row",
            chip.contains("animateDpAsState(")
        )
        assertTrue(
            "the chip has no constant border to carry the selection's colour",
            chip.contains("val borderWidth = 1.5.dp")
        )
    }

    @Test
    fun `the interface page exists and the theme page is only about colour`() {
        val screen = source(settings + "SettingsScreen.kt")
        assertTrue("the enum has no interface page", screen.contains("UI(\"Interface\""))
        assertTrue(
            "the hub has no door to the interface page",
            screen.contains("settings_row_ui")
        )
        assertTrue(
            "the page is not dispatched",
            screen.contains("SettingsPage.UI -> InterfaceTab(")
        )

        val theme = source(settings + "ThemeTab.kt")
        assertFalse(
            "motion is drawn on the Theme page again, so two pages write one " +
                "preference",
            theme.contains("MotionSection(")
        )
        assertTrue(
            "the appearance mode is a word rather than a picture of the scheme",
            theme.contains("ThemeModeTile(")
        )
        assertTrue("the OLED switch lost its name", theme.contains("title = \"OLED mode\""))

        // The page is two files: the shelf (`InterfaceTab`) and the sub-page bodies
        // (`InterfaceSections`). Both are read, because the requirement is that the
        // Interface page draws motion and the style chips - not that one file does.
        // A guard that read only the shelf would pass on a page whose bodies had
        // been emptied.
        val iface = source(settings + "InterfaceTab.kt") +
            source(settings + "InterfaceSections.kt")
        assertTrue("the interface page does not draw motion", iface.contains("MotionSection("))
        assertTrue("the interface page has no style chips", iface.contains("card_style_"))
    }

    /**
     * The preview, the Library grid and the dashboard's poster all call one cell.
     *
     * This is the assertion that makes the preview worth drawing at all.
     */
    @Test
    fun `every card surface draws the same cell`() {
        val surfaces = listOf(
            // The picker's preview moved out of `InterfaceSections` when the card
            // design page outgrew it; the assertion follows the drawing, not the file
            // name, so a page that handed its cards to a sample widget of its own
            // would still fail here.
            settings + "CardDesignPreview.kt" to "the picker's preview",
            "app/src/main/java/com/anisequel/ui/screens/profile/ProfileLibraryTab.kt" to
                "the Library grid",
            "app/src/main/java/com/anisequel/ui/components/cards/SequelPoster.kt" to
                "the dashboard's poster"
        )
        surfaces.forEach { (path, who) ->
            assertTrue(
                "$who does not go through MediaPosterCell, so it is a second " +
                    "implementation of the card style",
                source(path).contains("MediaPosterCell(")
            )
        }

        val details = source(
            "app/src/main/java/com/anisequel/ui/components/cards/SequelCardDetails.kt"
        )
        assertTrue(
            "the details column reads the style, so it cannot keep drawing a score " +
                "the poster already shows",
            details.contains("style.scoreOwner")
        )
    }

    @Test
    fun `the style is provided once at the app root`() {
        val nav = source("app/src/main/java/com/anisequel/ui/navigation/AppNavigation.kt")
        assertTrue(
            "nothing provides the card style, so every reader falls back to the " +
                "default and the picker changes nothing",
            nav.contains("LocalMediaCardStyle provides cardStyle")
        )
        assertTrue(
            "the Interface page writes a different store than the root reads",
            nav.contains("interfacePreferences = interfacePreferences")
        )
    }

    @Test
    fun `settings pages share expressive surfaces and animated controls`() {
        val components = source(settings + "SettingsComponents.kt")
        assertTrue("section cards should use the expressive shape scale", components.contains("ExpressiveShapes.extraLarge"))
        assertTrue("section icon tiles should use Material expressive silhouettes", components.contains("settingsDecorativeShape(title)"))
        // The switch row's selected surface is animated through one progress with
        // both endpoints read fresh from the scheme - not `animateColorAsState`, which
        // re-targets on every frame of a theme change because the scheme itself is
        // animated. Same requirement, stronger mechanism.
        assertTrue(
            "switch rows should animate their selected surface",
            components.contains("rememberStateColor(")
        )
        assertTrue("switch rows should use the expressive spatial size token", components.contains("ExpressiveMotion.FastSpatialSize"))
        assertTrue("checked rows should have a visible tonal state", components.contains("background(rowContainer)"))
        val hub = source(settings + "SettingsHub.kt")
        assertTrue("hub groups should use expressive large surfaces", hub.contains("ExpressiveShapes.extraLarge"))
        // The tile wears one colour and one silhouette rule: colour is the app's,
        // silhouette is the row's position. The shelf used to run a colour per
        // group (LOOK/CONTENT/DEVICE), so a blue Interface tile sat three shelves
        // from a pink Library one for no reason a reader could use - the heading
        // above the card already says which group a row is in.
        assertTrue(
            "hub entries no longer draw their tile in the app's one settings colour",
            hub.contains("settingsTilePalette()")
        )
        assertFalse(
            "a tile's colour is taken from its group again, which is what made the " +
                "shelf read as a colour chart",
            hub.contains("settingsGroupPalette(") || components.contains("SettingsGroupTone")
        )
        assertTrue(
            "hub entries no longer take their silhouette from their position",
            hub.contains("settingsTileShape(index)")
        )
    }

    /**
     * Every settings page draws the same chip.
     *
     * Five pages had each grown their own selected/unselected pill, and they had
     * drifted in every dimension a reader can see: three padding pairs, two colour
     * families, one 48dp minimum height and four without, two firing a haptic and
     * one not, and two different selection feedbacks. A settings page is a page of
     * chips, so "what does a chip look like" must have exactly one answer.
     *
     * The per-page copies are named here rather than counted, because the failure
     * this guards is not "one too many" - it is somebody adding a sixth for the
     * next page, which is how the first five happened.
     */
    @Test
    fun `settings pages share one choice chip`() {
        val directory = File(findProjectRoot(), "app/src/main/java/com/anisequel/ui/screens/settings")
        val files = directory.listFiles { file -> file.extension == "kt" }
            .orEmpty()
            .associateWith { withoutComments(it.readText()) }
            .toList()

        // `withoutComments`, and a real path: the shared chip's own comment names
        // every copy it replaced, so a name search over the raw text matches the
        // explanation of the bug instead of the bug.
        val declared = files.filter { (_, text) -> text.contains("internal fun SettingsChoiceChip(") }
        assertEquals(
            "the shared chip is declared nowhere or more than once: $declared",
            1,
            declared.size
        )

        listOf(
            "JokeChoiceChip",
            "RefreshIntervalChip",
            "MotionChip",
            "SeasonalEffectChip",
            "SeasonalPill"
        ).forEach { name ->
            val copies = files.filter { (_, text) -> text.contains("private fun $name(") }
                .map { (file, _) -> file.name }
            assertTrue(
                "a second chip called $name is back in $copies - a settings page " +
                    "must not answer \"what does a chip look like\" on its own",
                copies.isEmpty()
            )
        }
    }

    /**
     * The library view and the refresh/arriving cards are on their page.
     *
     * They were split into sub-pages to keep the shelf tidy, which made one of the
     * most common reasons to open Settings - "how often does the list refresh" -
     * a row, a page and a Back. Only Navigation is still a page.
     */
    @Test
    fun `library view and refresh are drawn on the page, not behind a row`() {
        val library = source(settings + "LibrarySettingsTab.kt")

        assertTrue(
            "the Library & dashboard page no longer opens with the bar it previews",
            library.contains("BottomBarSample(")
        )
        assertTrue(
            "the page no longer takes a `hub` block, so this check is looking at nothing",
            library.contains("hub = { onOpen ->")
        )
        val hub = library.substringAfter("hub = { onOpen ->").substringBefore("body = { section ->")
        listOf("LibraryViewCard(", "ListRefreshCard(", "ArrivingSectionCard(").forEach { card ->
            assertTrue(
                "$card is behind a sub-page again, so changing it costs a tap and a Back",
                hub.contains(card)
            )
        }
        assertFalse(
            "Library view is a sub-page again",
            library.contains("LibrarySection.VIEW")
        )
        assertFalse(
            "Refresh & arriving is a sub-page again",
            library.contains("LibrarySection.REFRESH")
        )
    }

    /**
     * Two pages that asked one question are one page.
     *
     * "Type & reading" and "Display size" sat next to each other on the shelf with
     * no way to tell which one made the app bigger, and the Type page carried a card
     * whose only job was a button to the other. One row replaced both.
     */
    @Test
    fun `type and display size are one destination`() {
        val screen = source(settings + "SettingsScreen.kt")
        assertTrue(
            "the merged page is not in the page enum",
            screen.contains("TYPE_AND_DISPLAY(")
        )
        assertFalse(
            "Display size is a destination of its own again",
            screen.contains("DISPLAY(\"Display size\"")
        )
        assertTrue(
            "the merged page is not dispatched",
            screen.contains("SettingsPage.TYPE_AND_DISPLAY -> TypeDisplayTab(")
        )
        val type = source(settings + "TypeDisplayTab.kt")
        assertTrue(
            "the merged page lost the text half",
            type.contains("TypeTextSectionBody(")
        )
        assertTrue(
            "the merged page lost the display-size half",
            type.contains("DisplaySizeSectionBody(")
        )
        assertFalse(
            "the pointer card between the two pages is back",
            source(settings + "DpiSettingsTab.kt").contains("DisplaySizePointerCard(")
        )
    }

    /**
     * The Interface page is one level deep.
     *
     * Card design sat between the page and the three screens that change a card, so
     * the stack was Interface -> Card design -> Card shape and three titles stacked
     * before a control. The parts are peers now, and the preview the middle level was
     * worth lives on the page's own shelf.
     */
    @Test
    fun `the card design shelf is gone and its parts are peers of the layout rows`() {
        // Two files: the page's stack and the group that draws the cards. The
        // requirement is about the page's shape, not about which file holds a line.
        val iface = source(settings + "InterfaceTab.kt") +
            source(settings + "InterfaceSections.kt")
        assertFalse(
            "a Card design section is back, so the page is four levels deep again",
            iface.contains("InterfaceSection.CARDS") || iface.contains("fun parent()")
        )
        assertTrue(
            "the three card parts are not on the Interface shelf",
            iface.contains("InterfaceSection.CARD_SHAPE.shelfEntry(onOpen)")
        )
        assertTrue(
            "the shelf no longer runs through the shared one-level scaffold",
            iface.contains("SettingsShelf<InterfaceSection>(")
        )
        assertTrue(
            "the card preview left the shelf with the level it used to sit on",
            iface.contains("fun CardDesignGroup(")
        )
    }

    /**
     * About states the build on its shelf, and says it in one place.
     *
     * The shelf briefly opened with a "build stamp" card above its rows, which drew
     * the version, the version code and the package - the same three facts the Build
     * page draws one tap away. A row can carry a fact at rest instead, so the reader
     * sees which build this is *before* choosing a row, and the page stays the only
     * place the details are written out.
     */
    @Test
    fun `the about shelf states the build once and the page does not draw it again`() {
        val about = source(settings + "InfoSettingsTab.kt")
        assertTrue(
            "the About shelf no longer states which build this is, so the one fact " +
                "the page exists for is a tap away",
            about.contains("AboutSection.BUILD to BuildConfig.VERSION_NAME")
        )
        assertFalse(
            "a build stamp card is back above the rows, drawing the version rows the " +
                "Build page draws one tap below it",
            about.contains("BuildStampCard(")
        )
        assertTrue(
            "the shelf's row values are not passed through the shared group, so a " +
                "fact cannot be stated on any shelf",
            source(settings + "SettingsShelf.kt").contains("values: Map<T, String> = emptyMap()")
        )
    }

    @Test
    fun `library and joke settings put primary choices before refinements`() {
        // The navigation half leads the Library page as a *row* now, because it is the
        // only part of that page still behind one: the bar, the saved-list view and
        // the refresh interval are drawn on the page itself. The intent is unchanged -
        // the reader meets "where do my tabs go" before the refinements below it - so
        // the assertion moved up a level rather than being dropped.
        val library = source(settings + "LibrarySettingsTab.kt")
        assertTrue("navigation choices should lead the Library page",
            library.indexOf("SettingsHubGroup(") < library.indexOf("LibraryViewCard(libraryPreferences)"))
        assertTrue("saved-list view should precede refresh interval",
            library.indexOf("LibraryViewCard(libraryPreferences)") < library.indexOf("ListRefreshCard(refreshIntervalPreferences)"))
        val jokes = source(settings + "JokeSettingsTab.kt")
        assertTrue("speaker identity should precede timing refinements",
            jokes.indexOf("PersonaCard(settings") < jokes.indexOf("PaceCard(settings"))
        // The order that matters is the shelf's now, not the file's: the parts are a
        // menu, and a reader meets "where she appears" before "what she talks about".
        assertTrue("placement should be offered before the topics she may use",
            jokes.indexOf("JokeSection.PLACEMENT") < jokes.indexOf("JokeSection.TONES"))
        assertTrue("the persona should be offered before placement",
            jokes.indexOf("JokeSection.PERSONA") < jokes.indexOf("JokeSection.PLACEMENT"))
    }

    private fun findProjectRoot(): File {
        val start = File(System.getProperty("user.dir") ?: ".")
        var candidate: File? = start
        while (candidate != null) {
            if (File(candidate, "gradle.properties").isFile) return candidate
            candidate = candidate.parentFile
        }
        error("could not locate gradle.properties from $start")
    }
}
