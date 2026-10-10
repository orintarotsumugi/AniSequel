package com.example

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * The home-screen widget's contract with the platform.
 *
 * ## What went wrong
 *
 * v1.0.42 shipped a widget that could not be added. Three separate declaration
 * bugs, none of which the compiler, R8 or the unit tests could see - every one of
 * them only shows up on a device, at the moment a user drops the tile:
 *
 *  - The metadata declared `android:configure` pointing at
 *    `AniSequelWidgetProvider`, an `AppWidgetProvider` and not an Activity. The
 *    launcher launches that component when the tile is placed; there was nothing
 *    to launch, and the failure surfaces as the launcher claiming the app is not
 *    installed.
 *  - `android:initialLayout` was missing, so a placed tile had no layout to draw
 *    between being added and its first update.
 *  - The manifest registered a plain `BroadcastReceiver` that forwarded to the
 *    provider, and it read `EXTRA_APPWIDGET_ID` where the system sends
 *    `EXTRA_APPWIDGET_IDS` - so the update that fills a fresh tile in was dropped.
 *
 * ## Why this is a test and not a convention
 *
 * All three are *declaration* mistakes: they live in XML and a one-line
 * `android:name`, they cannot fail a build, and their only symptom is a user
 * reporting that the widget does not exist. There is no runtime signal to fix
 * from, so the only place to catch them is here.
 *
 * A fourth assertion pins something adjacent that fails just as quietly: the
 * view ids the provider writes into `RemoteViews`. `setTextViewText` on an id the
 * layout does not contain is not an error - it is a no-op - so a renamed id
 * leaves a permanently empty widget that compiles, packages and installs.
 */
class WidgetDeclarationTest {

    private val root: File = findProjectRoot()

    private val widgetMetadataPath = "app/src/main/res/xml/widget_anisequel.xml"
    private val buildScriptPath = "app/build.gradle.kts"
    private val manifestPath = "app/src/main/AndroidManifest.xml"
    private val providerPath = "app/src/main/java/com/example/ui/widget/AniSequelWidgetProvider.kt"

    /**
     * The Gradle test task runs with the project directory as the working
     * directory, but resolve upward anyway so the test does not depend on which
     * module is running it.
     */
    private fun findProjectRoot(): File {
        val start = File(System.getProperty("user.dir") ?: ".")
        var candidate: File? = start
        while (candidate != null) {
            if (File(candidate, "gradle.properties").isFile) return candidate
            candidate = candidate.parentFile
        }
        error("could not locate gradle.properties from $start")
    }

    /** Namespace-aware parse, so attributes can be read by their qualified name. */
    private fun parse(relativePath: String): Element =
        DocumentBuilderFactory.newInstance()
            .apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(File(root, relativePath))
            .documentElement

    /** Every descendant with [tag], which is how the receiver is found inside `<application>`. */
    private fun Element.descendants(tag: String): List<Element> {
        val nodes = getElementsByTagName(tag)
        return (0 until nodes.length).mapNotNull { nodes.item(it) as? Element }
    }

    /** The metadata the manifest points the widget picker at. */
    private fun widgetMetadata(): Element = parse(widgetMetadataPath)

    /** The `<receiver>` the platform treats as this app's widget provider. */
    private fun widgetReceiver(): Element {
        val receivers = parse(manifestPath).descendants("receiver")
        val declared = receivers.filter { receiver ->
            receiver.descendants("meta-data").any {
                it.getAttribute("android:name") == "android.appwidget.provider"
            }
        }

        assertTrue(
            "AndroidManifest.xml declares ${receivers.size} receivers and none of " +
                "them carries the android.appwidget.provider meta-data, so the " +
                "widget is not published at all.",
            declared.size == 1
        )

        return declared.single()
    }

    /**
     * The one that produced "this app is not installed".
     *
     * Only asserted when a configure target exists: this widget needs no
     * configuration, and the fix was to stop pointing the attribute at something
     * that cannot be launched rather than to invent an Activity for it. If one is
     * ever declared, it has to be an Activity the manifest actually declares.
     */
    @Test
    fun `a widget configuration target is an activity the manifest declares`() {
        val configured = widgetMetadata().getAttribute("android:configure")
        if (configured.isBlank()) return

        val className = configured.removePrefix(".")
        val activities = parse(manifestPath).descendants("activity")
            .map { it.getAttribute("android:name").removePrefix(".") }

        assertTrue(
            "res/xml/$widgetMetadataPath sets android:configure=\"$configured\", " +
                "but AndroidManifest.xml declares no matching <activity> " +
                "(declared: $activities). The launcher starts that component when " +
                "the user drops the widget, and a target that is not an Activity " +
                "makes adding the widget fail with a message about the app not " +
                "being installed.",
            activities.any { it == className }
        )
    }

    /**
     * Without this there is nothing for the host to inflate until the first
     * update arrives, which is a blank tile - or, on a picker that renders the
     * entry first, no entry.
     */
    @Test
    fun `the widget declares an initial layout that exists`() {
        val initialLayout = widgetMetadata().getAttribute("android:initialLayout")

        assertTrue(
            "res/xml/$widgetMetadataPath has no android:initialLayout. The host " +
                "inflates that layout before the widget's first update, so without " +
                "it a freshly placed tile has nothing to draw.",
            initialLayout.isNotBlank()
        )
        assertTrue(
            "android:initialLayout=\"$initialLayout\" is not a @layout/ reference",
            initialLayout.startsWith("@layout/")
        )

        val name = initialLayout.removePrefix("@layout/")
        assertTrue(
            "android:initialLayout points at $initialLayout, but " +
                "app/src/main/res/layout/$name.xml does not exist.",
            File(root, "app/src/main/res/layout/$name.xml").isFile
        )
    }

    /**
     * The platform refuses to reschedule an update period under 30 minutes, so a
     * value below it silently does nothing except read as a shorter interval than
     * the widget actually gets.
     */
    @Test
    fun `the update period is either disabled or at least thirty minutes`() {
        val period = widgetMetadata().getAttribute("android:updatePeriodMillis").toLongOrNull()

        assertNotNull(
            "res/xml/$widgetMetadataPath has no android:updatePeriodMillis",
            period
        )
        assertTrue(
            "android:updatePeriodMillis=$period. The platform does not support " +
                "periods below 1800000 (30 minutes) and ignores the value, so a " +
                "smaller number is a lie about how often the widget refreshes. Use " +
                "0 to disable periodic updates.",
            period == 0L || period!! >= 1_800_000L
        )
    }

    /**
     * The receiver must be the `AppWidgetProvider` itself.
     *
     * This is the bug that made a newly added tile stay blank: a forwarding
     * `BroadcastReceiver` in front of the provider unpacked the broadcast by hand
     * and got the extra name wrong. `AppWidgetProvider` already does that
     * unpacking correctly, so anything that is *not* one has to reimplement it.
     */
    @Test
    fun `the registered receiver is an AppWidgetProvider`() {
        val receiver = widgetReceiver()
        val declaredName = receiver.getAttribute("android:name")

        assertTrue(
            "the widget's <receiver> has no android:name",
            declaredName.isNotBlank()
        )

        val namespace = Regex("""namespace\s*=\s*"([^"]+)"""")
            .find(File(root, buildScriptPath).readText())
            ?.groupValues
            ?.get(1)

        assertNotNull("could not read `namespace` out of $buildScriptPath", namespace)

        val className = if (declaredName.startsWith(".")) {
            "$namespace${declaredName}"
        } else {
            declaredName
        }
        val source = File(root, "app/src/main/java/${className.replace('.', '/')}.kt")

        assertTrue(
            "the widget's receiver android:name is \"$declaredName\", which " +
                "resolves to $className - and no such source file exists under " +
                "app/src/main/java.",
            source.isFile
        )
        assertTrue(
            "${source.name} is registered as the widget's receiver but does not " +
                "extend AppWidgetProvider. A plain BroadcastReceiver has to unpack " +
                "the update broadcast itself, which is how the widget came to " +
                "never receive its first update - see this class's own note.",
            source.readText().contains(": AppWidgetProvider()")
        )
    }

    @Test
    fun `the receiver filters the app widget update broadcast`() {
        val receiver = widgetReceiver()
        val actions = receiver.descendants("intent-filter")
            .flatMap { it.descendants("action") }
            .map { it.getAttribute("android:name") }

        assertTrue(
            "the widget's receiver does not filter " +
                "android.appwidget.action.APPWIDGET_UPDATE (it filters $actions). " +
                "The platform only ever calls it through that action.",
            "android.appwidget.action.APPWIDGET_UPDATE" in actions
        )
    }

    /**
     * Every id the provider writes must exist in the layout the metadata declares.
     *
     * `setTextViewText` and `setOnClickPendingIntent` on an id that is not in the
     * layout are silently ignored. A rename on either side therefore ships a
     * widget that is present, empty and untappable, with nothing logged.
     */
    @Test
    fun `the ids the provider writes exist in the declared initial layout`() {
        val initialLayout = widgetMetadata().getAttribute("android:initialLayout")
        val layoutName = initialLayout.removePrefix("@layout/")
        val layout = File(root, "app/src/main/res/layout/$layoutName.xml").readText()

        val written = Regex("""R\.id\.(\w+)""")
            .findAll(File(root, providerPath).readText())
            .map { it.groupValues[1] }
            .toSet()

        assertTrue(
            "no R.id references found in $providerPath. This test exists to compare " +
                "them against the layout, so finding none means it can no longer do " +
                "that rather than that the widget stopped writing any.",
            written.isNotEmpty()
        )

        val missing = written.filterNot { layout.contains("@+id/$it") }
        assertTrue(
            "the provider writes to $missing, which $layoutName.xml does not " +
                "declare. RemoteViews ignores an update to a view that is not " +
                "there, so this is a widget that renders empty or does not respond " +
                "to taps - with no error anywhere.",
            missing.isEmpty()
        )
    }
}
