package com.anisequel.ui.components.update

/**
 * What to tell a user who has to allow AniSequel to install apps, on a phone whose
 * Settings screen this app cannot see.
 *
 * ## Why the app writes this out instead of just opening Settings
 *
 * `REQUEST_INSTALL_PACKAGES` is a per-app switch that only the user can flip, and
 * Android has never promised where it lives. The platform action
 * (`ACTION_MANAGE_UNKNOWN_APP_SOURCES`) is a request to whichever Settings app the
 * manufacturer shipped, and those disagree for real: a Pixel puts it on the app's own
 * row, One UI hides it behind Settings > Apps > Special app access, MIUI calls the same
 * thing "Install unknown apps" under a permissions list, and a Huawei or HONOR build
 * renames it again. So opening Settings is only half of helping; the other half is
 * saying what to look for once that screen is in front of them, in words that match
 * the phone in hand.
 *
 * ## Why this is a pure object over strings
 *
 * There is no Android SDK in this environment, so the only honest way to test any of
 * this is to test the sentences. [steps] takes the manufacturer rather than reading
 * `Build.MANUFACTURER` itself - that one read belongs to the caller - which is what
 * lets `UpdateInstallPermissionTest` assert that a Samsung user is told the
 * special-access route first and an unknown build is told the generic one. A
 * composable cannot be pinned that way, and a wrong instruction on this screen costs
 * an update the user cannot install.
 */
internal object InstallPermissionGuide {

    /** The line above the steps; [appName] is the app the row has to name. */
    fun title(appName: String): String =
        "Allow $appName to install apps - once, on this phone"

    /**
     * One or two sentences on why the app is asking at all.
     *
     * It has to say plainly that the app cannot do this by itself, because the most
     * common reading of a permission screen appearing mid-update is that the update is
     * doing something nobody asked for. It isn't: the user pressed Update.
     */
    fun summary(appName: String): String =
        "Android will not let $appName open the installer until you allow it. Nothing " +
            "installs itself - you still confirm the install on the next screen - and " +
            "this only has to be done once per phone."

    /**
     * The steps, in the order that fits [manufacturer].
     *
     * The phone-specific route comes first because that is the one the reader will
     * actually find. The plain app-row route is always given as well, because an OEM
     * re-skin moves a row rather than removing it, and a reader who cannot find the
     * special-access list still needs somewhere to look. The search hint is last and is
     * the only promise worth making about a screen this app cannot see: every Settings
     * app from Android 8 on can reach this from its own search field.
     */
    fun steps(appName: String, manufacturer: String?): List<String> {
        val appRow = "Or open the app row: Settings > Apps > See all apps > $appName."
        return when {
            manufacturer isBrand "samsung" -> listOf(
                "Open Settings > Apps.",
                "Tap the three-dot menu (or the info icon), then Special app access > " +
                    "Install unknown apps.",
                "Find $appName in the list and turn on Allow from this source.",
                appRow,
                SEARCH_STEP
            )

            manufacturer isBrand "xiaomi" || manufacturer isBrand "redmi" ||
                manufacturer isBrand "poco" -> listOf(
                "Open Settings > Apps > Manage apps.",
                "Tap $appName, then Other permissions - or Install unknown apps.",
                "Turn on Install unknown apps and choose Always allow.",
                appRow,
                SEARCH_STEP
            )

            manufacturer isBrand "oppo" || manufacturer isBrand "realme" ||
                manufacturer isBrand "oneplus" -> listOf(
                "Open Settings > Apps > App management (some builds say App list).",
                "Tap $appName, then Install unknown apps / Allow app to install apps.",
                appRow,
                SEARCH_STEP
            )

            manufacturer isBrand "huawei" || manufacturer isBrand "honor" -> listOf(
                "Open Settings > Apps.",
                "Tap $appName, then Install apps from unknown sources - on some " +
                    "builds it sits under More settings.",
                appRow,
                SEARCH_STEP
            )

            else -> listOf(
                appRow.removePrefix("Or "),
                "Turn on Allow from this source - some builds call it Install unknown apps.",
                SEARCH_STEP
            )
        }
    }

    /**
     * What happens after the switch is on.
     *
     * Its own line because the return trip is the part people get wrong: coming back
     * to the app continues on its own (the APK is already downloaded and the grant is
     * noticed when the app is foregrounded again), so nobody should be hunting for the
     * update button a second time.
     */
    fun afterGrant(appName: String): String =
        "Go back here when you have turned it on. The update is already downloaded and " +
            "AniSequel picks up where it left off."

    /** The fallback that works on every build, since the names above do not all. */
    private const val SEARCH_STEP =
        "Still lost? Open Settings and search for \"install unknown apps\" - that " +
            "screen exists under that name on every phone running Android 8 or newer."

    /**
     * Whether the manufacturer string [isBrand] names it.
     *
     * `Build.MANUFACTURER` is free text the OEM sets: it arrives as `samsung`,
     * `SAMSUNG`, `Xiaomi`, `HUAWEI`, and sub-brands bring their own (`realme` reports
     * `realme`, not `oppo`). Containment on the lower-cased value is the only thing
     * that survives that; an equality check would quietly hand a Samsung user the
     * generic steps forever, which is a failure no test would notice and every user
     * would.
     */
    private infix fun String?.isBrand(brand: String): Boolean =
        this?.lowercase()?.contains(brand) == true
}
