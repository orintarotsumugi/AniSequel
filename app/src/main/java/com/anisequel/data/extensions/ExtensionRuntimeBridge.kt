package com.anisequel.data.extensions

import android.content.Context
import java.io.File

/** How far an extension has travelled toward being executable. */
enum class ExtensionRuntimeStage(val label: String) {
    NATIVE("Built-in"),
    APK_INSTALLED("APK installed"),
    APK_STAGED("APK staged"),
    MODULE_STAGED("Module staged")
}

/** Aggregate counts the manager renders without knowing the records' details. */
data class ExtensionRuntimeReport(
    val nativeSources: Int,
    val installedApks: Int,
    val stagedModules: Int,
    val playableSources: Int
)

/**
 * The one seam between an installed record and something the player can call.
 *
 * This class does **not** execute third-party code. It is the host-side bridge:
 * it classifies a record, lists it beside the native sources and keeps the
 * "installed" and "playable" counts from disagreeing. The isolated Aniyomi and
 * CloudStream loaders from Phase 2 plug in behind [nativeSource] / [sources]
 * without the Watch page learning a second source API.
 *
 * Keeping this boundary in one class is deliberate. Before it, the manager built
 * the source list inline and the manager page hard-coded its own Phase 2 sentence;
 * two places described the same bridge, so a future loader would have had to be
 * wired into both.
 */
class ExtensionRuntimeBridge(context: Context) {

    private val inspector = ExtensionInspector(context.applicationContext)

    fun stageOf(record: InstalledExtension): ExtensionRuntimeStage {
        val extension = record.apkPath?.substringAfterLast('.', "").orEmpty().lowercase()
        return when {
            extension in MODULE_EXTENSIONS -> ExtensionRuntimeStage.MODULE_STAGED
            record.needsRestart -> ExtensionRuntimeStage.APK_STAGED
            else -> ExtensionRuntimeStage.APK_INSTALLED
        }
    }

    fun noteFor(record: InstalledExtension): String = when (stageOf(record)) {
        ExtensionRuntimeStage.NATIVE -> "Built into AniSequel. Playable now."
        ExtensionRuntimeStage.APK_STAGED ->
            "APK staged. Restart AniSequel to register it."
        ExtensionRuntimeStage.APK_INSTALLED ->
            "APK installed, but it is not a loadable anime source."
        ExtensionRuntimeStage.MODULE_STAGED ->
            "Module staged. The JS/DEX runtime is a later phase."
    }

    /** Every source the Watch page shows, native first, then installed records. */
    fun sources(
        nativeSources: List<NativeAnimeSource>,
        installed: Collection<InstalledExtension>,
        runtimeSources: List<NativeAnimeSource> = emptyList()
    ): List<PlayableSource> {
        val native = nativeSources.map { source ->
            PlayableSource(
                id = source.id,
                name = source.name,
                kind = source.kind,
                content = source.content,
                language = source.language,
                nsfw = source.nsfw,
                iconUrl = null,
                playable = true,
                note = source.note
            )
        }
        val runtime = runtimeSources.map { source ->
            PlayableSource(
                id = source.id,
                name = source.name,
                kind = source.kind,
                content = source.content,
                language = source.language,
                nsfw = source.nsfw,
                iconUrl = null,
                playable = true,
                note = source.note
            )
        }
        val runtimeIds = runtimeSources.map { it.id }.toSet()
        val staged = installed
            .filter { it.content == ExtensionContent.ANIME && it.key !in runtimeIds }
            .sortedBy { it.name.lowercase() }
            .map { record ->
                PlayableSource(
                    id = record.key,
                    name = record.name,
                    kind = record.kind,
                    content = record.content,
                    language = "en",
                    nsfw = false,
                    iconUrl = record.iconUrl,
                    playable = false,
                    note = noteFor(record)
                )
            }
        return native + runtime + staged
    }

    fun report(
        nativeSources: List<NativeAnimeSource>,
        installed: Collection<InstalledExtension>,
        runtimeSources: List<NativeAnimeSource> = emptyList()
    ): ExtensionRuntimeReport = ExtensionRuntimeReport(
        nativeSources = nativeSources.size,
        installedApks = installed.count { stageOf(it) != ExtensionRuntimeStage.MODULE_STAGED },
        stagedModules = installed.count { stageOf(it) == ExtensionRuntimeStage.MODULE_STAGED },
        playableSources = nativeSources.size + runtimeSources.size
    )

    /** Metadata for a downloaded file; used by install validation and the manager. */
    fun inspect(file: File): ExtensionFileInfo? = inspector.inspect(file)

    fun isPackageInstalled(packageName: String): Boolean =
        inspector.isPackageInstalled(packageName)

    private companion object {
        // .cs3 is a CloudStream DEX module. Missing it made the manager call a
        // staged DEX an installed APK and invent a restart step that could not help.
        val MODULE_EXTENSIONS = setOf("js", "jar", "cs3", "dex")
    }
}
