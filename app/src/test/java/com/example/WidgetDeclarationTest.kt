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
    private fun widgetReceivers(): List<Element> {
        val receivers = parse(manifestPath).descendants("receiver")
        val declared = receivers.filter { receiver ->
            receiver.descendants("meta-data").any {
                it.getAttribute("android:name") == "android.appwidget.provider"
            }
        }

        assertTrue(
            "AndroidManifest.xml declares ${receivers.size} receivers and none of " +
                "them carries the android.appwidget.provider meta-data, so no " +
                "widget is published at all.",
            declared.isNotEmpty()
        )

        return declared
    }

    private fun widgetReceiver(): Element = widgetReceivers().first()

    @Test
    fun `a widget configuration target is an activity the manifest declares`() {
        for (receiver in widgetReceivers()) {
            val metaDataResource = receiver.descendants("meta-data")
                .firstOrNull { it.getAttribute("android:name") == "android.appwidget.provider" }
                ?.getAttribute("android:resource")
                ?.removePrefix("@xml/") ?: continue

            val metadata = parse("app/src/main/res/xml/$metaDataResource.xml")
            val configured = metadata.getAttribute("android:configure")
            if (configured.isBlank()) continue

            val className = configured.removePrefix(".")
            val activities = parse(manifestPath).descendants("activity")
                .map { it.getAttribute("android:name").removePrefix(".") }

            assertTrue(
                "res/xml/$metaDataResource.xml sets android:configure=\"$configured\", " +
                    "but AndroidManifest.xml declares no matching <activity> " +
                    "(declared: $activities).",
                activities.any { it == className }
            )
        }
    }

    @Test
    fun `the widget declares an initial layout that exists`() {
        for (receiver in widgetReceivers()) {
            val metaDataResource = receiver.descendants("meta-data")
                .firstOrNull { it.getAttribute("android:name") == "android.appwidget.provider" }
                ?.getAttribute("android:resource")
                ?.removePrefix("@xml/")

            assertNotNull("Widget receiver must specify android:resource in meta-data", metaDataResource)
            val metadata = parse("app/src/main/res/xml/$metaDataResource.xml")
            val initialLayout = metadata.getAttribute("android:initialLayout")

            assertTrue(
                "res/xml/$metaDataResource.xml has no android:initialLayout.",
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
    }

    @Test
    fun `the update period is either disabled or at least thirty minutes`() {
        for (receiver in widgetReceivers()) {
            val metaDataResource = receiver.descendants("meta-data")
                .firstOrNull { it.getAttribute("android:name") == "android.appwidget.provider" }
                ?.getAttribute("android:resource")
                ?.removePrefix("@xml/") ?: continue

            val metadata = parse("app/src/main/res/xml/$metaDataResource.xml")
            val period = metadata.getAttribute("android:updatePeriodMillis").toLongOrNull()

            assertNotNull(
                "res/xml/$metaDataResource.xml has no android:updatePeriodMillis",
                period
            )
            assertTrue(
                "android:updatePeriodMillis=$period in $metaDataResource.xml. The platform does not support " +
                    "periods below 1800000 (30 minutes) and ignores the value.",
                period == 0L || period!! >= 1_800_000L
            )
        }
    }

    @Test
    fun `the registered receiver is an AppWidgetProvider`() {
        val namespace = Regex("""namespace\s*=\s*"([^"]+)"""")
            .find(File(root, buildScriptPath).readText())
            ?.groupValues
            ?.get(1)

        assertNotNull("could not read `namespace` out of $buildScriptPath", namespace)

        for (receiver in widgetReceivers()) {
            val declaredName = receiver.getAttribute("android:name")
            assertTrue("the widget's <receiver> has no android:name", declaredName.isNotBlank())

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
                    "extend AppWidgetProvider.",
                source.readText().contains(": AppWidgetProvider()")
            )
        }
    }

    @Test
    fun `the receiver filters the app widget update broadcast`() {
        for (receiver in widgetReceivers()) {
            val actions = receiver.descendants("intent-filter")
                .flatMap { it.descendants("action") }
                .map { it.getAttribute("android:name") }

            assertTrue(
                "the widget receiver ${receiver.getAttribute("android:name")} does not filter " +
                    "android.appwidget.action.APPWIDGET_UPDATE (it filters $actions).",
                "android.appwidget.action.APPWIDGET_UPDATE" in actions
            )
        }
    }

    @Test
    fun `the ids the provider writes exist in the declared initial layout`() {
        val namespace = Regex("""namespace\s*=\s*"([^"]+)"""")
            .find(File(root, buildScriptPath).readText())
            ?.groupValues
            ?.get(1)

        for (receiver in widgetReceivers()) {
            val declaredName = receiver.getAttribute("android:name")
            val className = if (declaredName.startsWith(".")) "$namespace$declaredName" else declaredName
            val sourceFile = File(root, "app/src/main/java/${className.replace('.', '/')}.kt")

            val metaDataResource = receiver.descendants("meta-data")
                .firstOrNull { it.getAttribute("android:name") == "android.appwidget.provider" }
                ?.getAttribute("android:resource")
                ?.removePrefix("@xml/") ?: continue

            val metadata = parse("app/src/main/res/xml/$metaDataResource.xml")
            val initialLayout = metadata.getAttribute("android:initialLayout")
            val layoutName = initialLayout.removePrefix("@layout/")
            val layout = File(root, "app/src/main/res/layout/$layoutName.xml").readText()

            val written = Regex("""R\.id\.(\w+)""")
                .findAll(sourceFile.readText())
                .map { it.groupValues[1] }
                .toSet()

            assertTrue(
                "no R.id references found in ${sourceFile.name}.",
                written.isNotEmpty()
            )

            val missing = written.filterNot { layout.contains("@+id/$it") }
            assertTrue(
                "${sourceFile.name} writes to $missing, which $layoutName.xml does not declare.",
                missing.isEmpty()
            )
        }
    }
}
