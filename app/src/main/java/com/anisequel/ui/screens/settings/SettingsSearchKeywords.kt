package com.anisequel.ui.screens.settings

/**
 * The words each Settings destination is found by.
 *
 * Separate from the ranking engine and from the result list because these are
 * *data*: a keyword is added when a setting gains a name a reader might type, and
 * nothing about scoring changes when it does. "dpi" finding "Display size" is one
 * line here, not a branch in the matcher.
 *
 * Top-level `internal` values rather than an object's private members only because
 * the index that reads them is in another file - the names are deliberately
 * prefixed per page so a map lookup at a call site still says which page it is.
 */

internal val settingsPageKeywords = mapOf(
    SettingsPage.APPEARANCE to "theme appearance colour color dark light oled accent " +
        "background wallpaper dynamic material you backdrop banner cover retain anime tint",
    SettingsPage.UI to "interface layout cards motion gestures navigation bar " +
        "poster design shelf bar lift info page",
    SettingsPage.TYPE_AND_DISPLAY to "font text size reading accessibility scale " +
        "title scroll dpi density display size automatic manual ui zoom bigger " +
        "smaller device panel presets exact",
    SettingsPage.JOKES to "ani chan jokes pill humour humor top bottom edge " +
        "everywhere scope position placement",
    SettingsPage.SEASONAL to "snow petals leaves seasonal sky animation " +
        "density speed quality particles",
    SettingsPage.LIBRARY to "dashboard refresh arriving list grid search bar " +
        "interval staleness sync cache manual always",
    SettingsPage.DEVICE to "device phone performance cache lock full screen " +
        "frame rate diagnostics dpi",
    SettingsPage.ACCOUNT to "anilist account sign in login oauth client id " +
        "profile viewer list counts",
    SettingsPage.UPDATES to "updates version changelog release notes download " +
        "what's new apk manifest",
    SettingsPage.ABOUT to "about support privacy links build diagnostics " +
        "repository issue tracker"
)

internal val interfaceSectionKeywords = mapOf(
    InterfaceSection.LAYOUT to "info page title page classic nova bar lift continue rail " +
        "layout home bottom navigation float",
    InterfaceSection.SEQUELS to "home sequel feed rows cards banners continue watching " +
        "shelf tickets polaroids poster style preview",
    InterfaceSection.LIBRARY to "library view grid shelf covers list grouped table " +
        "columns captions browsing",
    InterfaceSection.CARD_SHAPE to "card shape corner ratio frame radius border",
    InterfaceSection.CARD_TEXT to "card name score title size bold rating caption",
    InterfaceSection.CARD_POSTER to "poster art source betterposters overlays " +
        "unique seasons rating genre trend",
    InterfaceSection.MOTION to "animation motion display system bars " +
        "fullscreen immersive edge swipe",
    InterfaceSection.GESTURES to "quick add hold swipe confirm planning button"
)

internal val appearanceSectionKeywords = mapOf(
    ThemeSection.BACKGROUND to "oled black canvas background blur " +
        "true black glow home wallpaper",
    ThemeSection.ACCENT to "accent colour color palette wallpaper dynamic " +
        "material you hue custom hex",
    ThemeSection.ARTWORK to "anime poster colour color dynamic theme cover " +
        "retain tint revert duration kept accent",
    ThemeSection.PAGES to "anime pages backdrop blur tint detail sheet " +
        "banner cover off key visual"
)

internal val typeSectionKeywords = mapOf(
    TypeSection.TEXT to "font typeface text size reading accessibility title " +
        "scroll marquee saved google fonts long",
    TypeSection.SCALE to "dpi density display size automatic manual scale " +
        "bigger smaller zoom panel device"
)

internal val jokeSectionKeywords = mapOf(
    JokeSection.PERSONA to "ani chan name stamp badge persona switch golden " +
        "speaker identity",
    JokeSection.TEXT_EFFECT to "line style effect reveal typewriter compact " +
        "sample words arrive",
    JokeSection.PACE to "frequency pace dwell how often how long stay seconds " +
        "calm chatty normal",
    JokeSection.PLACEMENT to "position top bottom edge scope where pages " +
        "everywhere placement",
    JokeSection.MOMENTS to "moments triggers when appears empty error loading " +
        "invite",
    JokeSection.TONES to "tones topics what she talks about allowed off " +
        "content filters",
    JokeSection.SAVED to "favourites favourites saved lines kept hearts " +
        "favourite clear"
)

internal val librarySectionKeywords = mapOf(
    LibrarySection.NAVIGATION to "top bar bottom bar tabs shortcuts trophy " +
        "magnifier search page navigation",
    LibrarySection.VIEW to "library view remembered filter sort forget reset " +
        "reopens",
    LibrarySection.REFRESH to "refresh interval arriving rows stale sync cache " +
        "manual always dashboard"
)

internal val aboutSectionKeywords = mapOf(
    AboutSection.BUILD to "version build package application id about",
    AboutSection.AUTHENTICITY to "authenticity signature certificate verified " +
        "signed build",
    AboutSection.DIAGNOSTICS to "crash report diagnostics logs stack trace",
    AboutSection.PRIVACY to "privacy data leaves device token analytics",
    AboutSection.LINKS to "github issues source help documentation links report",
    // The helper names are here because they are what a reader types: somebody
    // hears about Dantotsu over this app and searches for it, not for "credits".
    AboutSection.CREDITS to "credits thanks helpers developer author made by " +
        "orintarotsumugi itsmechinmoy dantotsu aayush dartotsu ryanyuuki anymex " +
        "titangen kusokohei contributors people"
)
